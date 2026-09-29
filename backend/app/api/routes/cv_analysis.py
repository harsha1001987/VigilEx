"""
Stateless CV analysis endpoint.

Accepts a raw video upload and runs it straight through the existing
batch_processor.process_video() pipeline (YOLO11n-Pose -> ByteTrack ->
RULA/REBA), returning the resulting analysis as JSON. Unlike
assessments.upload_and_process_video, this endpoint does not touch the
database or require an existing assessment record - it is a standalone
"analyze this video" utility used to try the CV pipeline directly.
"""

import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, UploadFile, status

from app.core.config import get_settings
from app.cv import batch_processor

router = APIRouter(tags=["cv-analysis"])
settings = get_settings()

ALLOWED_VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".3gp", ".mkv", ".webm"}


@router.post("/analyze-video")
def analyze_video(file: UploadFile = File(...)) -> dict:
    """
    Runs the CV pipeline (YOLO11n-Pose + ByteTrack -> RULA/REBA) on an
    uploaded video and returns the process_video() result as JSON.

    The uploaded video is written to a temporary file for the duration of
    processing and deleted afterwards; no video is persisted.
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

        try:
            return batch_processor.process_video(video_path=str(tmp_path))
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Video processing failed: {str(e)}",
            )
    finally:
        if tmp_path is not None and tmp_path.exists():
            tmp_path.unlink()
