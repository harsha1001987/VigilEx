"""
Tests for POST /api/v1/analyze-video.

This endpoint wraps the existing batch_processor.process_video() pipeline
(YOLO11n-Pose -> ByteTrack -> RULA/REBA) in a stateless FastAPI route: no
database access, no persisted assessment, just "upload a video, get back
the CV analysis JSON".

Requires numpy/torch/ultralytics/cv2 (for the real-video test) and
fastapi/sqlalchemy (to import app.main) to be importable in the same
environment.
"""

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.api.routes import cv_analysis
from app.main import app

client = TestClient(app)

TEST_VIDEO_PATH = (
    Path(__file__).resolve().parent
    / "19832490-hd_1920_1080_25fps (1).mp4"
)

ANALYZE_URL = "/api/v1/analyze-video"


def test_analyze_video_endpoint_exists():
    """A request with no file should be routed (422), not 404."""
    response = client.post(ANALYZE_URL)

    assert response.status_code != 404


def test_missing_file_returns_4xx():
    response = client.post(ANALYZE_URL)

    assert 400 <= response.status_code < 500


def test_invalid_file_type_is_rejected():
    response = client.post(
        ANALYZE_URL,
        files={"file": ("notes.txt", b"this is not a video", "text/plain")},
    )

    assert response.status_code == 415
    assert "detail" in response.json()


def test_empty_upload_is_rejected():
    response = client.post(
        ANALYZE_URL,
        files={"file": ("clip.mp4", b"", "video/mp4")},
    )

    assert response.status_code == 400


def test_processing_failure_is_handled_cleanly(monkeypatch):
    def _boom(*args, **kwargs):
        raise RuntimeError("simulated pipeline failure")

    monkeypatch.setattr(
        cv_analysis.batch_processor,
        "process_video",
        _boom,
    )

    response = client.post(
        ANALYZE_URL,
        files={"file": ("clip.mp4", b"not a real video but non-empty", "video/mp4")},
    )

    assert response.status_code == 500
    body = response.json()
    assert "detail" in body
    assert "simulated pipeline failure" in body["detail"]


@pytest.mark.skipif(
    not TEST_VIDEO_PATH.exists(),
    reason=f"Test video not found: {TEST_VIDEO_PATH}",
)
class TestAnalyzeVideoWithRealVideo:
    """One real upload, reused across assertions to avoid re-running the
    (slow) YOLO/ByteTrack pipeline once per test."""

    @pytest.fixture(scope="class")
    def result(self):
        with open(TEST_VIDEO_PATH, "rb") as video_file:
            response = client.post(
                ANALYZE_URL,
                files={
                    "file": (
                        TEST_VIDEO_PATH.name,
                        video_file,
                        "video/mp4",
                    )
                },
            )

        return response

    def test_successful_processing_returns_200(self, result):
        assert result.status_code == 200

    def test_response_contains_video_info(self, result):
        body = result.json()

        assert "video" in body
        assert body["video"]["duration_sec"] > 0
        assert body["video"]["fps"] > 0
        assert body["video"]["width"] > 0
        assert body["video"]["height"] > 0
        assert body["video"]["frames_sampled"] >= 1

    def test_response_contains_worker_and_quality_info(self, result):
        body = result.json()

        assert "worker" in body
        assert body["worker"]["track_id"] is not None

        assert "quality" in body
        assert body["quality"]["camera_view_ok"] is True
        assert 0 <= body["quality"]["scoreable_fraction"] <= 1

    def test_response_contains_rula_data(self, result):
        rula = result.json()["rula"]

        assert rula["frames_scored"] > 0
        assert isinstance(rula["max_score"], int)
        assert 1 <= rula["max_score"] <= 7
        assert "risk_distribution" in rula

    def test_response_contains_reba_data(self, result):
        reba = result.json()["reba"]

        assert reba["frames_scored"] > 0
        assert isinstance(reba["max_score"], int)
        assert 1 <= reba["max_score"] <= 12
        assert "risk_distribution" in reba

    def test_response_contains_posture_angle_data(self, result):
        angles = result.json()["derived_angles"]

        for key in (
            "trunk_deg_avg",
            "trunk_deg_max",
            "upper_arm_deg_avg",
            "upper_arm_deg_max",
            "knee_deg_avg",
            "knee_deg_max",
            "neck_deg_avg",
            "neck_deg_max",
        ):
            assert key in angles
            assert angles[key] is not None

    def test_response_does_not_expose_internal_filesystem_paths(self, result):
        body = result.json()

        assert body["meta"]["input_video"] != str(TEST_VIDEO_PATH)
        assert "\\" not in body["meta"]["input_video"]
        assert "/" not in body["meta"]["input_video"]
