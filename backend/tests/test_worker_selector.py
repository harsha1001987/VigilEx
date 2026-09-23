from backend.app.cv.track_state import TrackStateManager
from backend.app.cv.worker_selector import (
    MIN_PRIMARY_TRACK_FRAMES,
    PrimaryWorkerSelector,
)


def _feed(manager, track_id, frame_count, rula_score=3, reba_score=2):
    """Feed `frame_count` valid RULA/REBA frames into a track."""
    for frame in range(frame_count):
        manager.update(
            track_id,
            {
                "rula": {"score": rula_score, "risk": "moderate", "angles": {}},
                "reba": {"score": reba_score, "risk": "low", "angles": {}},
            },
            frame,
        )


def _feed_partial(manager, track_id, frame_count, valid_rula, valid_reba):
    """Feed `frame_count` frames where only the first N of each score are valid."""
    for frame in range(frame_count):
        rula_score = 3 if frame < valid_rula else None
        reba_score = 2 if frame < valid_reba else None
        manager.update(
            track_id,
            {
                "rula": {
                    "score": rula_score,
                    "risk": "moderate" if rula_score is not None else "not_measured",
                    "angles": {},
                },
                "reba": {
                    "score": reba_score,
                    "risk": "low" if reba_score is not None else "not_measured",
                    "angles": {},
                },
            },
            frame,
        )


def test_case1_more_observed_frames_wins():
    manager = TrackStateManager()
    _feed(manager, 1, 100)
    _feed(manager, 2, 80)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] == 1
    assert result["selection_method"] == "most_observed_frames"


def test_case2_tie_on_frames_broken_by_valid_rula():
    manager = TrackStateManager()
    _feed_partial(manager, 1, 100, valid_rula=95, valid_reba=100)
    _feed_partial(manager, 2, 100, valid_rula=90, valid_reba=100)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] == 1


def test_case3_tie_on_frames_and_rula_broken_by_valid_reba():
    manager = TrackStateManager()
    _feed_partial(manager, 1, 100, valid_rula=100, valid_reba=80)
    _feed_partial(manager, 2, 100, valid_rula=100, valid_reba=90)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] == 2


def test_case4_full_tie_broken_by_lowest_track_id():
    manager = TrackStateManager()
    _feed(manager, 3, 50)
    _feed(manager, 10, 50)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] == 3


def test_case5_explicit_selection_overrides_frame_counts():
    manager = TrackStateManager()
    _feed(manager, 1, 200)
    _feed(manager, 10, 20)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager, requested_track_id=10)

    assert result["selected_track_id"] == 10
    assert result["selection_method"] == "explicit"


def test_case6_explicit_selection_of_missing_track_is_not_found():
    manager = TrackStateManager()
    _feed(manager, 1, 200)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager, requested_track_id=99)

    assert result["selected_track_id"] is None
    assert result["selection_method"] == "explicit_not_found"
    assert result["status"] == "requested_track_not_found"


def test_case7a_automatic_selection_excludes_tracks_below_minimum():
    manager = TrackStateManager()
    _feed(manager, 1, MIN_PRIMARY_TRACK_FRAMES - 1)
    _feed(manager, 2, MIN_PRIMARY_TRACK_FRAMES + 5)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] == 2


def test_case7b_explicit_selection_below_minimum_returns_insufficient_status():
    manager = TrackStateManager()
    _feed(manager, 1, MIN_PRIMARY_TRACK_FRAMES - 1)
    _feed(manager, 2, 200)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager, requested_track_id=1)

    assert result["selected_track_id"] is None
    assert result["selection_method"] == "explicit_insufficient_observations"
    assert result["status"] == "selected_track_insufficient_observations"


def test_no_tracks_returns_none_selection():
    manager = TrackStateManager()

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    assert result["selected_track_id"] is None
    assert result["selection_method"] == "none"
    assert result["candidates"] == []


def test_selection_ignores_rula_reba_score_magnitude():
    manager = TrackStateManager()
    # Track 1: fewer frames but much higher (worse) RULA/REBA scores.
    _feed(manager, 1, 50, rula_score=7, reba_score=11)
    # Track 2: more frames, lower (better) scores.
    _feed(manager, 2, 90, rula_score=1, reba_score=1)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    # Track 2 wins purely on frame count, despite "safer" scores.
    assert result["selected_track_id"] == 2


def test_multiple_tracks_remain_independent():
    manager = TrackStateManager()
    _feed(manager, 1, 144)
    _feed(manager, 2, 120)
    _feed(manager, 6, 95)
    _feed(manager, 10, 144)
    _feed(manager, 3, 92)

    selector = PrimaryWorkerSelector()
    result = selector.select_primary_track(manager)

    candidate_ids = {c["track_id"] for c in result["candidates"]}
    assert candidate_ids == {1, 2, 3, 6, 10}

    # Track 1 and Track 10 tie on 144 frames and 144 valid RULA/REBA;
    # lowest track ID wins.
    assert result["selected_track_id"] == 1

    # Untouched tracks still hold their own independent state.
    assert manager.get(2).frames_seen == 120
    assert manager.get(6).frames_seen == 95
