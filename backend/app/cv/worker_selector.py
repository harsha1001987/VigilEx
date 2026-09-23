"""
VigilEx primary worker selection.

Given a set of ByteTrack track IDs seen in a video, decide which tracked
person should be treated as the PRIMARY WORKER for the ergonomic
assessment.

IMPORTANT: This module is deliberately blind to RULA/REBA scores and risk
levels. Worker identity (who is being assessed) and ergonomic severity
(how risky their posture is) are separate concerns. Selection is based
only on how consistently a track was observed.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from backend.app.cv.track_state import TrackState, TrackStateManager


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

MIN_PRIMARY_TRACK_FRAMES = 10


def _candidate_dict(track: TrackState) -> Dict[str, Any]:
    return {
        "track_id": track.track_id,
        "frames_observed": track.frames_seen,
        "valid_rula_frames": track.valid_rula_frames,
        "valid_reba_frames": track.valid_reba_frames,
    }


def _priority_key(track: TrackState):
    """
    Deterministic ranking key for automatic selection.

    Higher frames_observed wins, then higher valid_rula_frames, then
    higher valid_reba_frames, then lower track_id. None of these are
    ergonomic-score based.
    """
    return (
        -track.frames_seen,
        -track.valid_rula_frames,
        -track.valid_reba_frames,
        track.track_id,
    )


class PrimaryWorkerSelector:
    """Selects which tracked person is the primary worker for a video."""

    def __init__(
        self,
        min_primary_track_frames: int = MIN_PRIMARY_TRACK_FRAMES,
    ) -> None:
        self.min_primary_track_frames = min_primary_track_frames

    def select_primary_track(
        self,
        track_manager: TrackStateManager,
        requested_track_id: Optional[int] = None,
    ) -> Dict[str, Any]:
        tracks: List[TrackState] = track_manager.get_active_tracks()

        candidates = [
            _candidate_dict(track)
            for track in sorted(tracks, key=lambda t: t.track_id)
        ]

        # -------------------------------------------------------------
        # Explicit selection.
        # -------------------------------------------------------------
        if requested_track_id is not None:
            track = track_manager.get(requested_track_id)

            if track is None:
                return {
                    "selected_track_id": None,
                    "selection_method": "explicit_not_found",
                    "status": "requested_track_not_found",
                    "requested_track_id": requested_track_id,
                    "candidates": candidates,
                }

            if track.frames_seen < self.min_primary_track_frames:
                return {
                    "selected_track_id": None,
                    "selection_method": "explicit_insufficient_observations",
                    "status": "selected_track_insufficient_observations",
                    "requested_track_id": requested_track_id,
                    "candidates": candidates,
                }

            return {
                "selected_track_id": requested_track_id,
                "selection_method": "explicit",
                "status": "selected",
                "requested_track_id": requested_track_id,
                "candidates": candidates,
            }

        # -------------------------------------------------------------
        # Automatic selection.
        # -------------------------------------------------------------
        if not tracks:
            return {
                "selected_track_id": None,
                "selection_method": "none",
                "status": "no_tracks",
                "candidates": candidates,
            }

        eligible = [
            track
            for track in tracks
            if track.frames_seen >= self.min_primary_track_frames
        ]

        if not eligible:
            return {
                "selected_track_id": None,
                "selection_method": "none",
                "status": "no_eligible_tracks",
                "candidates": candidates,
            }

        selected = min(eligible, key=_priority_key)

        return {
            "selected_track_id": selected.track_id,
            "selection_method": "most_observed_frames",
            "status": "selected",
            "candidates": candidates,
        }
