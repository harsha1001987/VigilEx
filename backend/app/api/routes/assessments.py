import uuid
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

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
from app.services.assessment_persistence import persist_cv_assessment_results

router = APIRouter(prefix="/assessments", tags=["assessments"])
settings = get_settings()


def _get_assessment_or_404(db: Session, assessment_id: uuid.UUID) -> Assessment:
    assessment = db.get(Assessment, assessment_id)
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


@router.get("", response_model=list[AssessmentResponse])
def list_assessments(
    organization_id: uuid.UUID | None = None,
    site_id: uuid.UUID | None = None,
    area_id: uuid.UUID | None = None,
    task_id: uuid.UUID | None = None,
    worker_id: uuid.UUID | None = None,
    assessor_id: uuid.UUID | None = None,
    status: str | None = None,
    db: Session = Depends(get_db),
) -> list[Assessment]:
    query = db.query(Assessment)
    if organization_id is not None:
        query = query.filter(Assessment.organization_id == organization_id)
    if site_id is not None:
        query = query.filter(Assessment.site_id == site_id)
    if area_id is not None:
        query = query.filter(Assessment.area_id == area_id)
    if task_id is not None:
        query = query.filter(Assessment.task_id == task_id)
    if worker_id is not None:
        query = query.filter(Assessment.worker_id == worker_id)
    if assessor_id is not None:
        query = query.filter(Assessment.assessor_id == assessor_id)
    if status is not None:
        query = query.filter(Assessment.status == status)
    return list(query.order_by(Assessment.created_at).all())


@router.get("/{assessment_id}", response_model=AssessmentResponse)
def get_assessment(assessment_id: uuid.UUID, db: Session = Depends(get_db)) -> Assessment:
    return _get_assessment_or_404(db, assessment_id)


@router.put("/{assessment_id}", response_model=AssessmentResponse)
def update_assessment(
    assessment_id: uuid.UUID, payload: AssessmentUpdate, db: Session = Depends(get_db)
) -> Assessment:
    assessment = _get_assessment_or_404(db, assessment_id)
    _validate_assessment_references(db, payload)
    for field, value in payload.model_dump().items():
        setattr(assessment, field, value)
    _commit(db)
    db.refresh(assessment)
    return assessment


@router.delete("/{assessment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_assessment(assessment_id: uuid.UUID, db: Session = Depends(get_db)) -> None:
    assessment = _get_assessment_or_404(db, assessment_id)
    # assessment_scores / assessment_interventions are ON DELETE CASCADE at the
    # database level (see migration b7c3e1f9a2d4), so a single delete here is
    # sufficient - no app-level cascade logic needed.
    db.delete(assessment)
    db.commit()


@router.post("/{assessment_id}/results", response_model=AssessmentResponse)
def submit_cv_results(
    assessment_id: uuid.UUID, payload: CVPersistPayload, db: Session = Depends(get_db)
) -> Assessment:
    """
    Persists CV batch processing results into PostgreSQL for an existing assessment.
    Updates assessment scores (RULA/REBA) and capture metadata.
    """
    assessment = _get_assessment_or_404(db, assessment_id)
    return persist_cv_assessment_results(
        db,
        assessment,
        cv_result=payload.cv_result,
        primary_assessment=payload.primary_assessment,
    )


@router.post("/{assessment_id}/process-video", response_model=AssessmentResponse)
def process_and_persist_video(
    assessment_id: uuid.UUID, payload: ProcessVideoPayload, db: Session = Depends(get_db)
) -> Assessment:
    """
    Runs the CV video processing pipeline (YOLO11n-Pose + ByteTrack -> RULA/REBA)
    on a video file and persists the resulting assessment into PostgreSQL.
    """
    assessment = _get_assessment_or_404(db, assessment_id)
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
) -> Assessment:
    """
    Accepts a multipart/form-data video upload from Android client or web client,
    saves the file safely to server-side storage, runs the CV pipeline (YOLO11n-Pose
    -> ByteTrack -> posture -> RULA/REBA -> PrimaryWorkerSelector), and persists
    the final assessment into PostgreSQL.
    """
    assessment = _get_assessment_or_404(db, assessment_id)

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
    except Exception as e:
        if saved_path.exists():
            saved_path.unlink()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to save uploaded video file: {str(e)}",
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
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Video processing failed: {str(e)}",
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
