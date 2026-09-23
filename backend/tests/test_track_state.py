from backend.app.cv.track_state import (
    MAX_MISSED_FRAMES,
    SMOOTHING_WINDOW,
    TrackStateManager,
)


def _rula_result(score, risk="moderate"):
    return {
        "rula": {"score": score, "risk": risk if score is not None else "not_measured", "angles": {}},
        "reba": {"score": None, "risk": "not_measured", "angles": {}},
    }


def test_repeated_score_smooths_to_same_value():
    manager = TrackStateManager()

    for frame in range(5):
        manager.update(1, _rula_result(3), frame)

    track = manager.get(1)

    assert track.smoothed_rula() == 3
    assert isinstance(track.smoothed_rula(), int)


def test_mode_smoothing_over_mixed_history():
    manager = TrackStateManager()

    scores = [3, 3, 4, 3, 3]

    for frame, score in enumerate(scores):
        manager.update(1, _rula_result(score), frame)

    track = manager.get(1)

    assert track.smoothed_rula() == 3


def test_missing_scores_are_ignored_not_zero():
    manager = TrackStateManager()

    scores = [3, None, 3, None, 4]

    for frame, score in enumerate(scores):
        manager.update(1, _rula_result(score), frame)

    track = manager.get(1)

    assert list(track.rula_score_history) == [3, 3, 4]
    assert track.missing_rula_count == 2
    assert 0 not in track.rula_score_history


def test_independent_tracks_do_not_share_history():
    manager = TrackStateManager()

    for frame, score in enumerate([3, 3, 3]):
        manager.update(3, _rula_result(score), frame)

    for frame, score in enumerate([5, 5, 4]):
        manager.update(10, _rula_result(score), frame)

    assert list(manager.get(3).rula_score_history) == [3, 3, 3]
    assert list(manager.get(10).rula_score_history) == [5, 5, 4]

    assert manager.get(3).smoothed_rula() == 3
    assert manager.get(10).smoothed_rula() == 5


def test_history_is_bounded_by_smoothing_window():
    manager = TrackStateManager()

    for frame in range(SMOOTHING_WINDOW + 10):
        manager.update(1, _rula_result(3), frame)

    track = manager.get(1)

    assert len(track.rula_score_history) == SMOOTHING_WINDOW


def test_stale_tracks_are_removed_only_after_max_missed_frames():
    manager = TrackStateManager()

    manager.update(1, _rula_result(3), frame_index=0)

    # Not yet stale.
    just_before_threshold = MAX_MISSED_FRAMES
    removed = manager.remove_stale_tracks(just_before_threshold)
    assert removed == []
    assert manager.get(1) is not None

    # Now past the threshold.
    past_threshold = MAX_MISSED_FRAMES + 1
    removed = manager.remove_stale_tracks(past_threshold)
    assert removed == [1]
    assert manager.get(1) is None


def test_smoothed_scores_are_never_fractional():
    manager = TrackStateManager()

    scores = [1, 2, 2, 3, 7, 4, 4, 4, 5]

    for frame, score in enumerate(scores):
        manager.update(1, _rula_result(score), frame)

    smoothed = manager.get(1).smoothed_rula()

    assert isinstance(smoothed, int)
    assert smoothed == int(smoothed)


def test_coverage_is_valid_frames_over_observed_frames():
    manager = TrackStateManager()

    scores = [3, None, 3, 3, None]

    for frame, score in enumerate(scores):
        manager.update(1, _rula_result(score), frame)

    track = manager.get(1)

    assert track.frames_seen == 5
    assert track.valid_rula_frames == 3
    assert track.rula_coverage() == 3 / 5
    # No REBA scores were ever fed in.
    assert track.reba_coverage() == 0.0


def test_full_coverage_when_every_frame_is_valid():
    manager = TrackStateManager()

    for frame in range(4):
        manager.update(1, _rula_result(3), frame)

    track = manager.get(1)

    assert track.rula_coverage() == 1.0


def test_to_assessment_uses_existing_smoothed_values_and_coverage():
    manager = TrackStateManager()

    scores = [3, 3, 4, 3, None]

    for frame, score in enumerate(scores):
        manager.update(7, _rula_result(score), frame)

    track = manager.get(7)
    assessment = track.to_assessment()

    assert assessment["track_id"] == 7
    assert assessment["frames_observed"] == 5
    assert assessment["rula"]["score"] == track.smoothed_rula()
    assert assessment["rula"]["risk"] == track.smoothed_rula_risk()
    assert assessment["rula"]["valid_frames"] == 4
    assert assessment["rula"]["coverage"] == 4 / 5
    assert assessment["reba"]["score"] is None
    assert assessment["reba"]["valid_frames"] == 0
    assert assessment["status"] == "available"


def test_status_is_insufficient_data_when_no_valid_scores():
    manager = TrackStateManager()

    manager.update(1, _rula_result(None), frame_index=0)

    assessment = manager.get(1).to_assessment()

    assert assessment["status"] == "insufficient_data"
    assert assessment["rula"]["score"] is None
    assert assessment["reba"]["score"] is None


def test_angles_are_none_when_missing_not_fabricated():
    manager = TrackStateManager()

    manager.update(1, _rula_result(3), frame_index=0)

    assessment = manager.get(1).to_assessment()

    assert assessment["angles"] == {
        "upper_arm_deg": None,
        "lower_arm_flexion_deg": None,
        "neck_deg": None,
        "trunk_deg": None,
        "knee_flexion_deg": None,
    }


def test_last_valid_angles_reflects_most_recent_snapshot():
    manager = TrackStateManager()

    def result_with_angles(rula_score, upper_arm_deg):
        return {
            "rula": {
                "score": rula_score,
                "risk": "moderate",
                "angles": {
                    "upper_arm_deg": upper_arm_deg,
                    "lower_arm_flexion_deg": 30.0,
                    "neck_deg": 5.0,
                    "trunk_deg": 4.0,
                    "knee_flexion_deg": 70.0,
                },
            },
            "reba": {"score": None, "risk": "not_measured", "angles": {}},
        }

    manager.update(1, result_with_angles(3, 10.0), frame_index=0)
    manager.update(1, result_with_angles(3, 20.0), frame_index=1)

    assessment = manager.get(1).to_assessment()

    assert assessment["angles"]["upper_arm_deg"] == 20.0


def test_coverage_is_independent_per_track():
    manager = TrackStateManager()

    for frame, score in enumerate([3, 3, 3, 3]):
        manager.update(1, _rula_result(score), frame)

    for frame, score in enumerate([3, None, None, None]):
        manager.update(2, _rula_result(score), frame)

    assert manager.get(1).rula_coverage() == 1.0
    assert manager.get(2).rula_coverage() == 1 / 4
