"""
Tests for FastAPI multipart video upload endpoint and PostgreSQL integration.
"""

import io
import uuid
from pathlib import Path
from decimal import Decimal
import pytest
from fastapi.testclient import TestClient
from app.db.database import SessionLocal

from app.main import app
from app.db.base import Base
from app.db.database import get_db
from app.core.config import get_settings
from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.models.assessment import Assessment
from app.models.assessment_score import AssessmentScore

settings = get_settings()


@pytest.fixture
def db_session():
    """Connects to real PostgreSQL database for integration tests."""
    # The test database configured by conftest.py.
    session = SessionLocal()

    org = session.query(Organization).first()
    if not org:
        org = Organization(id=uuid.uuid4(), name="Upload Test Org")
        session.add(org)
        session.commit()

    site = session.query(Site).filter(Site.organization_id == org.id).first()
    if not site:
        site = Site(id=uuid.uuid4(), organization_id=org.id, name="Upload Test Site")
        session.add(site)
        session.commit()

    area = session.query(Area).filter(Area.site_id == site.id).first()
    if not area:
        area = Area(id=uuid.uuid4(), site_id=site.id, name="Upload Test Area")
        session.add(area)
        session.commit()

    task = session.query(Task).filter(Task.area_id == area.id).first()
    if not task:
        task = Task(id=uuid.uuid4(), area_id=area.id, name="Upload Test Task")
        session.add(task)
        session.commit()

    session.test_hierarchy = {
        "org_id": org.id,
        "site_id": site.id,
        "area_id": area.id,
        "task_id": task.id,
    }

    yield session
    session.close()


def test_upload_video_nonexistent_assessment(db_session):
    """Verify uploading to a non-existent assessment returns 404."""
    client = TestClient(app)
    fake_id = uuid.uuid4()
    files = {"file": ("test.mp4", b"dummy video content", "video/mp4")}
    response = client.post(f"/api/v1/assessments/{fake_id}/upload-video", files=files)
    assert response.status_code == 404
    assert "Assessment not found" in response.json()["detail"]


def test_upload_empty_video(db_session):
    """Verify uploading an empty video file returns 400."""
    hierarchy = db_session.test_hierarchy
    assessment_id = uuid.uuid4()
    assessment = Assessment(
        id=assessment_id,
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    client = TestClient(app)
    files = {"file": ("empty.mp4", b"", "video/mp4")}
    response = client.post(f"/api/v1/assessments/{assessment_id}/upload-video", files=files)
    assert response.status_code == 400
    assert "empty" in response.json()["detail"].lower()


def test_upload_unsupported_media_type(db_session):
    """Verify uploading an unsupported file format returns 415."""
    hierarchy = db_session.test_hierarchy
    assessment_id = uuid.uuid4()
    assessment = Assessment(
        id=assessment_id,
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    client = TestClient(app)
    files = {"file": ("script.sh", b"echo hello", "text/x-shellscript")}
    response = client.post(f"/api/v1/assessments/{assessment_id}/upload-video", files=files)
    assert response.status_code == 415
    assert "Unsupported file type" in response.json()["detail"]


def test_real_video_multipart_upload_e2e(db_session):
    """
    End-to-end integration test:
    Simulates Android multipart upload of real video -> FastAPI endpoint ->
    saves file to backend/uploads/ -> runs YOLO11n-Pose + ByteTrack ->
    persists RULA/REBA results to PostgreSQL -> verifies response.
    """
    hierarchy = db_session.test_hierarchy
    assessment_id = uuid.uuid4()
    assessment = Assessment(
        id=assessment_id,
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    test_dir = Path(__file__).resolve().parent
    sample_video_path = test_dir / "19832490-hd_1920_1080_25fps (1).mp4"
    if not sample_video_path.exists():
        pytest.skip("Sample video file not found")

    client = TestClient(app)
    with open(sample_video_path, "rb") as video_file:
        files = {"file": ("camera_recording.mp4", video_file, "video/mp4")}
        data = {"stride_hz": "25.0"}
        response = client.post(
            f"/api/v1/assessments/{assessment_id}/upload-video",
            files=files,
            data=data,
        )

    assert response.status_code == 200
    res_data = response.json()
    assert res_data["id"] == str(assessment_id)
    assert res_data["status"] == "completed"
    assert res_data["methodology_version"] == "vigilex-rula-reba-v1"

    # Verify scores returned in response
    scores = res_data["scores"]
    assert len(scores) >= 2

    rula = next(s for s in scores if s["method"] == "RULA")
    reba = next(s for s in scores if s["method"] == "REBA")

    assert float(rula["score"]) == 3.0
    assert rula["risk_band"] == "moderate"
    assert rula["inputs"]["valid_frames"] == 144
    assert rula["inputs"]["frames_observed"] == 144
    assert rula["inputs"]["coverage_pct"] == "100.0%"

    assert float(reba["score"]) == 3.0
    assert reba["risk_band"] == "low"
    assert reba["inputs"]["valid_frames"] == 144
    assert reba["inputs"]["frames_observed"] == 144
    assert reba["inputs"]["coverage_pct"] == "100.0%"

    # The response never reveals where the server stored the file...
    upload_info = res_data["capture_metadata"]["meta"]["upload"]
    assert "stored_path" not in upload_info
    assert "stored_filename" not in upload_info
    assert upload_info["original_filename"] == "camera_recording.mp4"

    # ...but the file was stored, as recorded in the database.
    db_session.expire_all()
    stored = db_session.get(Assessment, assessment_id).capture_metadata["meta"]["upload"]["stored_path"]
    assert Path(stored).exists()
    assert Path(stored).stat().st_size > 0
