import uuid
from datetime import datetime, timezone
from pathlib import Path

import logging

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from app.api.deps import get_current_user

from app.core.config import get_settings
from app.cv import batch_processor
from app.cv.track_state import TrackStateManager
from app.cv.worker_selector import PrimaryWorkerSelector
from app.db.database import get_db
from app.models.area import Area
from app.models.assessment import Assessment
from app.models.organization import Organization
from app.models.site import Site
from app.models.task import Task
from app.models.user import User
from app.models.worker import Worker
from app.schemas.assessment import (
    AssessmentBase,
    AssessmentCreate,
    AssessmentResponse,
    AssessmentUpdate,
    CVPersistPayload,
    ProcessVideoPayload,
)
from app.schemas.analysis import AssessmentDetail, AssessmentPage, ReportMetadata
from app.services import analysis_assessments as analyses
from app.services import reports
from app.services.assessment_persistence import persist_cv_assessment_results

router = APIRouter(prefix="/assessments", tags=["assessments"])
settings = get_settings()
log = logging.getLogger("vigilex.assessments")

MAX_PAGE = 100


def _get_assessment_or_404(db: Session, assessment_id: uuid.UUID, owner: User | None = None) -> Assessment:
    # Another owner's assessment is reported exactly like a missing one.
    assessment = analyses.get_owned(db, assessment_id, owner)
    if assessment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assessment not found")
    return assessment


def _validate_assessment_references(db: Session, payload: AssessmentBase) -> None:
    """
    Checks, in order:
    1. Every referenced id (organization/site/area/task/worker/assessor) exists -> 404.
    2. The site/area/task chain, and worker/assessor organization, actually
       match the supplied organization_id -> 400. IDs that individually exist
       but belong to a different parent are rejected rather than silently
       accepted.
    """
    organization = db.get(Organization, payload.organization_id)
    if organization is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Organization not found")

    site = db.get(Site, payload.site_id)
    if site is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Site not found")

    area = db.get(Area, payload.area_id)
    if area is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Area not found")

    task = db.get(Task, payload.task_id)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")

    worker = None
    if payload.worker_id is not None:
        worker = db.get(Worker, payload.worker_id)
        if worker is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Worker not found")

    assessor = None
    if payload.assessor_id is not None:
        assessor = db.get(User, payload.assessor_id)
        if assessor is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assessor (user) not found")

    if site.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Site does not belong to the supplied organization",
        )
    if area.site_id != payload.site_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Area does not belong to the supplied site",
        )
    if task.area_id != payload.area_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Task does not belong to the supplied area",
        )
    if worker is not None and worker.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Worker does not belong to the supplied organization",
        )
    if assessor is not None and assessor.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Assessor does not belong to the supplied organization",
        )


def _commit(db: Session) -> None:
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Assessment violates a database constraint (status, load_source, or consent fields).",
        )


@router.post("", response_model=AssessmentResponse, status_code=status.HTTP_201_CREATED)
def create_assessment(payload: AssessmentCreate, db: Session = Depends(get_db)) -> Assessment:
    _validate_assessment_references(db, payload)
    assessment = Assessment(**payload.model_dump())
    db.add(assessment)
    _commit(db)
    db.refresh(assessment)
    return assessment


@router.get("", response_model=AssessmentPage)
def list_assessments(
    organization_id: uuid.UUID | None = None,
    site_id: uuid.UUID | None = None,
    area_id: uuid.UUID | None = None,
    task_id: uuid.UUID | None = None,
    worker_id: uuid.UUID | None = None,
    assessor_id: uuid.UUID | None = None,
    status: str = Query("completed", description="Lifecycle status; history shows completed assessments."),
    limit: int = Query(20, ge=1, le=MAX_PAGE),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> AssessmentPage:
    """Assessment history, newest first."""
    analyses.expire_stale_analyses(db)
    query = analyses.owned(owner).where(Assessment.status == status)
    for column, value in (
        (Assessment.organization_id, organization_id),
        (Assessment.site_id, site_id),
        (Assessment.area_id, area_id),
        (Assessment.task_id, task_id),
        (Assessment.worker_id, worker_id),
        (Assessment.assessor_id, assessor_id),
    ):
        if value is not None:
            query = query.where(column == value)

    total = analyses.count_owned(db, query)
    rows = db.scalars(
        query.order_by(Assessment.created_at.desc(), Assessment.id)
        .limit(limit)
        .offset(offset)
        .options(selectinload(Assessment.scores))
    ).all()
    return AssessmentPage(items=[analyses.summarize(a) for a in rows], total=total, limit=limit, offset=offset)


@router.get("/{assessment_id}", response_model=AssessmentDetail)
def get_assessment(
    assessment_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> AssessmentDetail:
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    return AssessmentDetail(
        **AssessmentResponse.model_validate(assessment).model_dump(),
        summary=analyses.summarize(assessment),
        analysis=analyses.rebuild_analysis(assessment),
        report_available=reports.stored_report(assessment) is not None,
    )


@router.put("/{assessment_id}", response_model=AssessmentResponse)
def update_assessment(
    assessment_id: uuid.UUID,
    payload: AssessmentUpdate,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> Assessment:
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    _validate_assessment_references(db, payload)
    for field, value in payload.model_dump().items():
        setattr(assessment, field, value)
    _commit(db)
    db.refresh(assessment)
    return assessment


@router.delete("/{assessment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_assessment(
    assessment_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> None:
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    # assessment_scores / assessment_interventions are ON DELETE CASCADE at the
    # database level (see migration b7c3e1f9a2d4), so a single delete here is
    # sufficient - no app-level cascade logic needed.
    db.delete(assessment)
    db.commit()
    reports.delete_report(assessment_id)


@router.post("/{assessment_id}/results", response_model=AssessmentResponse)
def submit_cv_results(
    assessment_id: uuid.UUID,
    payload: CVPersistPayload,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> Assessment:
    """
    Persists CV batch processing results into PostgreSQL for an existing assessment.
    Updates assessment scores (RULA/REBA) and capture metadata.
    """
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    return persist_cv_assessment_results(
        db,
        assessment,
        cv_result=payload.cv_result,
        primary_assessment=payload.primary_assessment,
    )


@router.post("/{assessment_id}/process-video", response_model=AssessmentResponse)
def process_and_persist_video(
    assessment_id: uuid.UUID,
    payload: ProcessVideoPayload,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> Assessment:
    """
    Runs the CV video processing pipeline (YOLO11n-Pose + ByteTrack -> RULA/REBA)
    on a video file and persists the resulting assessment into PostgreSQL.
    """
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    cv_result = batch_processor.process_video(
        video_path=payload.video_path,
        stride_hz=payload.stride_hz,
    )
    return persist_cv_assessment_results(
        db,
        assessment,
        cv_result=cv_result,
        primary_assessment=None,
    )


@router.post("/{assessment_id}/upload-video", response_model=AssessmentResponse)
def upload_and_process_video(
    assessment_id: uuid.UUID,
    file: UploadFile = File(...),
    stride_hz: float = Form(25.0),
    requested_track_id: int | None = Form(None),
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> Assessment:
    """
    Accepts a multipart/form-data video upload from Android client or web client,
    saves the file safely to server-side storage, runs the CV pipeline (YOLO11n-Pose
    -> ByteTrack -> posture -> RULA/REBA -> PrimaryWorkerSelector), and persists
    the final assessment into PostgreSQL.
    """
    assessment = _get_assessment_or_404(db, assessment_id, owner)

    if not file or not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No video file uploaded.",
        )

    filename_lower = file.filename.lower()
    allowed_extensions = {".mp4", ".mov", ".avi", ".3gp", ".mkv", ".webm"}
    ext = Path(filename_lower).suffix
    if ext not in allowed_extensions and not (file.content_type and file.content_type.startswith("video/")):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported file type '{ext or file.content_type}'. Please upload an MP4, MOV, or 3GP video.",
        )

    safe_ext = ext if ext in allowed_extensions else ".mp4"
    unique_filename = f"{assessment_id}_{uuid.uuid4().hex[:8]}{safe_ext}"
    saved_path = settings.upload_path / unique_filename

    file_size = 0
    max_size = settings.max_upload_size_bytes

    try:
        with open(saved_path, "wb") as out_file:
            while chunk := file.file.read(1024 * 1024):  # 1MB chunks
                file_size += len(chunk)
                if file_size > max_size:
                    out_file.close()
                    if saved_path.exists():
                        saved_path.unlink()
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File size exceeds maximum allowed limit ({max_size // (1024 * 1024)} MB).",
                    )
                out_file.write(chunk)
    except HTTPException:
        raise
    except Exception:
        log.exception("Could not store upload for %s", assessment_id)
        if saved_path.exists():
            saved_path.unlink()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to save uploaded video file.",
        )

    if file_size == 0:
        if saved_path.exists():
            saved_path.unlink()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded video file is empty.",
        )

    try:
        cv_result = batch_processor.process_video(
            video_path=str(saved_path),
            stride_hz=stride_hz,
        )
    except Exception:
        log.exception("process_video failed for %s", assessment_id)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Video processing failed.",
        )

    upload_meta = {
        "original_filename": Path(file.filename).name,
        "stored_filename": unique_filename,
        "stored_path": str(saved_path),
        "file_size_bytes": file_size,
        "content_type": file.content_type,
        "uploaded_at": datetime.now(timezone.utc).isoformat(),
    }
    if "meta" not in cv_result:
        cv_result["meta"] = {}
    cv_result["meta"]["upload"] = upload_meta

    track_manager = TrackStateManager()
    for frame in cv_result.get("keyframes", []):
        track_id = frame["track_id"]
        frame_idx = int(frame["t"] * cv_result["video"]["fps"])
        frame_assessment = {
            "rula": frame["rula"],
            "reba": frame["reba"],
        }
        track_manager.update(track_id, frame_assessment, frame_idx)

    selector = PrimaryWorkerSelector()
    selection = selector.select_primary_track(track_manager, requested_track_id=requested_track_id)

    primary_assessment = None
    if selection.get("selected_track_id") is not None:
        primary_track = track_manager.get(selection["selected_track_id"])
        if primary_track:
            primary_assessment = primary_track.to_assessment()

    return persist_cv_assessment_results(
        db,
        assessment,
        cv_result=cv_result,
        primary_assessment=primary_assessment,
    )


# ------------------------------------------------------------------ reports

def _report_metadata(assessment: Assessment, content, path: Path) -> ReportMetadata:
    stat = path.stat()
    return ReportMetadata(
        assessment_id=assessment.id,
        status="ready",
        generated_at=datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc),
        file_name=reports.report_file_name(assessment),
        size_bytes=stat.st_size,
        download_url=f"/api/v1/assessments/{assessment.id}/report/pdf",
        content=content,
    )


def _completed_or_400(assessment: Assessment) -> None:
    if assessment.status != "completed":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Reports can only be generated for completed assessments.",
        )


@router.post("/{assessment_id}/report", response_model=ReportMetadata, status_code=status.HTTP_201_CREATED)
def generate_report(
    assessment_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> ReportMetadata:
    """Generates (or regenerates) the PDF report from the stored assessment."""
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    _completed_or_400(assessment)
    try:
        content, path = reports.generate_report(assessment)
    except Exception:
        log.exception("Report generation failed for %s", assessment_id)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="The report could not be generated.",
        )
    return _report_metadata(assessment, content, path)


@router.get("/{assessment_id}/report", response_model=ReportMetadata)
def get_report(
    assessment_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> ReportMetadata:
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    path = reports.stored_report(assessment)
    if path is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Report has not been generated.")
    return _report_metadata(assessment, reports.build_report_content(assessment), path)


@router.get("/{assessment_id}/report/pdf", response_class=FileResponse)
def download_report(
    assessment_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> FileResponse:
    """The generated PDF, for export or the platform share sheet."""
    assessment = _get_assessment_or_404(db, assessment_id, owner)
    path = reports.stored_report(assessment)
    if path is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Report has not been generated.")
    return FileResponse(path, media_type="application/pdf", filename=reports.report_file_name(assessment))
