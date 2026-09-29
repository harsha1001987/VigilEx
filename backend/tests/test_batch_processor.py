"""
Integration test for backend.app.cv.batch_processor.process_video().

This exercises the full VigilEx CV pipeline (YOLO11n-Pose -> ByteTrack ->
camera-yaw gate -> RULA/REBA) against a real video file. It requires
numpy, torch, ultralytics, and cv2 to be importable, and downloads no
model -- it uses the checkpoint already committed under
backend/app/cv/models/yolo11n-pose.pt.

Run with the Python environment that has the CV dependencies installed,
e.g.:

    python -m pytest backend/tests/test_batch_processor.py -q
"""

from pathlib import Path

import pytest

from backend.app.cv import batch_processor


TEST_VIDEO_PATH = (
    Path(__file__).resolve().parent
    / "19832490-hd_1920_1080_25fps (1).mp4"
)

pytestmark = pytest.mark.skipif(
    not TEST_VIDEO_PATH.exists(),
    reason=f"Test video not found: {TEST_VIDEO_PATH}",
)


@pytest.fixture(scope="module")
def video_result():
    """Run process_video() once and share the result across assertions."""
    return batch_processor.process_video(
        str(TEST_VIDEO_PATH),
        stride_hz=5.0,
    )


def test_process_video_completes_without_crashing(video_result):
    assert video_result is not None
    assert video_result["methodology_version"] == "vigilex-rula-reba-v1"


def test_worker_is_detected(video_result):
    assert video_result["worker"]["track_id"] is not None
    assert video_result["worker"]["detected_frames"] > 0


def test_at_least_one_frame_is_sampled(video_result):
    assert video_result["video"]["frames_sampled"] >= 1


def test_at_least_one_frame_is_scoreable(video_result):
    assert video_result["quality"]["camera_view_ok"] is True
    assert video_result["quality"]["scoreable_fraction"] > 0


def test_rula_results_are_present(video_result):
    rula = video_result["rula"]

    assert rula["frames_scored"] > 0
    assert rula["max_score"] is not None
    assert isinstance(rula["max_score"], int)


def test_reba_results_are_present(video_result):
    reba = video_result["reba"]

    assert reba["frames_scored"] > 0
    assert reba["max_score"] is not None
    assert isinstance(reba["max_score"], int)


def test_knee_flexion_is_aggregated(video_result):
    """Regression test for the batch_processor angle-key mismatch: the
    posture engine reports "knee_flexion_deg", and process_video() must
    read that exact key when building derived_angles.knee_deg_avg/max."""
    angles = video_result["derived_angles"]

    assert angles["knee_deg_avg"] is not None
    assert angles["knee_deg_max"] is not None
    assert isinstance(angles["knee_deg_avg"], float)
    assert isinstance(angles["knee_deg_max"], float)


def test_rula_maximum_score_is_valid(video_result):
    rula_max = video_result["rula"]["max_score"]

    assert 1 <= rula_max <= 7


def test_reba_maximum_score_is_valid(video_result):
    reba_max = video_result["reba"]["max_score"]

    assert 1 <= reba_max <= 12
