"""
CV analysis endpoint.

Accepts a raw video upload, runs it through the existing
batch_processor.process_video() pipeline (YOLO11n-Pose -> ByteTrack ->
RULA/REBA) and returns the result as JSON, unchanged, plus the id of the
assessment it was saved as.

The assessment row is created as in_progress when the upload arrives and
becomes completed only after the result validates and is persisted; any
failure marks it failed and stores no scores. The uploaded video is written
to a temporary file for processing and deleted afterwards.
"""

import logging
import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import get_settings
from app.cv import batch_processor
from app.db.database import get_db
from app.models.user import User
from app.schemas.analysis import AnalyzeFailure
from app.services import analysis_assessments as assessments

router = APIRouter(tags=["cv-analysis"])
settings = get_settings()
log = logging.getLogger("vigilex.analyze")

ALLOWED_VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".3gp", ".mkv", ".webm"}


@router.post("/analyze-video")
def analyze_video(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> dict:
    """
    Runs the CV pipeline on an uploaded video and returns the process_video()
    result, with `assessment_id` added once the assessment has been saved.
    `assessment_id` is null when the result could not be saved.
    """
    if not file or not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No video file uploaded.",
        )

    ext = Path(file.filename.lower()).suffix
    if ext not in ALLOWED_VIDEO_EXTENSIONS and not (
        file.content_type and file.content_type.startswith("video/")
    ):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported file type '{ext or file.content_type}'. Please upload an MP4, MOV, or 3GP video.",
        )

    safe_ext = ext if ext in ALLOWED_VIDEO_EXTENSIONS else ".mp4"
    max_size = settings.max_upload_size_bytes

    tmp_path: Path | None = None
    try:
        file_size = 0

        with tempfile.NamedTemporaryFile(suffix=safe_ext, delete=False) as tmp_file:
            tmp_path = Path(tmp_file.name)

            while chunk := file.file.read(1024 * 1024):  # 1MB chunks
                file_size += len(chunk)
                if file_size > max_size:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File size exceeds maximum allowed limit ({max_size // (1024 * 1024)} MB).",
                    )
                tmp_file.write(chunk)

        if file_size == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Uploaded video file is empty.",
            )

        # The record exists before processing so a failure is recorded, never lost.
        # A database outage must not cost the user their analysis, so it is tolerated.
        record = None
        try:
            record = assessments.start_analysis(
                db,
                file_name=Path(file.filename).name,
                content_type=file.content_type,
                size_bytes=file_size,
                owner=owner,
            )
        except SQLAlchemyError:
            log.exception("Could not create assessment record; analysis continues unsaved")
            db.rollback()

        try:
            result = batch_processor.process_video(video_path=str(tmp_path))
        except Exception:
            log.exception("process_video failed for %s", file.filename)
            if record is not None:
                assessments.fail_analysis(
                    db, record, AnalyzeFailure(stage="analysis", message="Video processing failed.")
                )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Video processing failed.",
            )

        assessment_id = None
        if record is not None:
            try:
                failure = assessments.complete_analysis(db, record, result)
                if failure is None:
                    assessment_id = str(record.id)
                elif failure.stage == "validation":
                    raise HTTPException(
                        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                        detail=failure.message,
                    )
            except SQLAlchemyError:
                log.exception("Could not save assessment %s", record.id)
                db.rollback()

        # Unchanged analysis contract, plus the saved assessment's id.
        return {**result, "assessment_id": assessment_id}
    finally:
        if tmp_path is not None and tmp_path.exists():
            tmp_path.unlink()
