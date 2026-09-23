"""
VigilEx per-track temporal state and smoothing.

This module does NOT compute RULA/REBA. It only consumes the output of
backend.app.cv.posture.assess_posture() and turns per-frame scores into
stable, per-track assessments via bounded history + mode smoothing.
"""

from __future__ import annotations

from collections import deque
from statistics import multimode
from typing import Any, Deque, Dict, List, Optional


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SMOOTHING_WINDOW = 15
MAX_MISSED_FRAMES = 30

ANGLE_KEYS = (
    "upper_arm_deg",
    "lower_arm_flexion_deg",
    "neck_deg",
    "trunk_deg",
    "knee_flexion_deg",
)


def _mode_smooth(values: List[int]) -> Optional[int]:
    """
    Return the smoothed (mode) value of a list of discrete scores.

    Ties are broken by preferring the most recent value among the tied
    modes, so smoothing reacts to a sustained posture change instead of
    always freezing on whichever tied value came first.
    """
    if not values:
        return None

    modes = multimode(values)

    if len(modes) == 1:
        return modes[0]

    for value in reversed(values):
        if value in modes:
            return value

    return modes[0]


class TrackState:
    """Temporal state for a single ByteTrack track ID."""

    def __init__(
        self,
        track_id: int,
        first_seen_frame: int,
        window: int = SMOOTHING_WINDOW,
    ) -> None:
        self.track_id = track_id
        self.window = window

        self.first_seen_frame = first_seen_frame
        self.last_seen_frame = first_seen_frame
        self.frames_seen = 0

        self.valid_rula_frames = 0
        self.valid_reba_frames = 0
        self.missing_rula_count = 0
        self.missing_reba_count = 0

        self.rula_score_history: Deque[int] = deque(maxlen=window)
        self.reba_score_history: Deque[int] = deque(maxlen=window)
        self.rula_risk_history: Deque[str] = deque(maxlen=window)
        self.reba_risk_history: Deque[str] = deque(maxlen=window)
        self.angle_history: Deque[Dict[str, Any]] = deque(maxlen=window)

    # -----------------------------------------------------------------
    # Update
    # -----------------------------------------------------------------

    def update(self, result: Dict[str, Any], frame_index: int) -> None:
        """Feed one frame's assess_posture() result into this track."""
        self.frames_seen += 1
        self.last_seen_frame = frame_index

        rula = result.get("rula", {}) or {}
        reba = result.get("reba", {}) or {}

        rula_score = rula.get("score")
        reba_score = reba.get("score")

        if rula_score is not None:
            self.valid_rula_frames += 1
            self.rula_score_history.append(rula_score)
            self.rula_risk_history.append(rula.get("risk"))
        else:
            self.missing_rula_count += 1

        if reba_score is not None:
            self.valid_reba_frames += 1
            self.reba_score_history.append(reba_score)
            self.reba_risk_history.append(reba.get("risk"))
        else:
            self.missing_reba_count += 1

        angles = rula.get("angles") or reba.get("angles")

        if angles:
            self.angle_history.append(angles)

    # -----------------------------------------------------------------
    # Smoothing
    # -----------------------------------------------------------------

    def smoothed_rula(self) -> Optional[int]:
        return _mode_smooth(list(self.rula_score_history))

    def smoothed_reba(self) -> Optional[int]:
        return _mode_smooth(list(self.reba_score_history))

    def smoothed_rula_risk(self) -> Optional[str]:
        score = self.smoothed_rula()

        if score is None:
            return None

        from backend.app.cv.posture import _rula_risk_level

        return _rula_risk_level(score)

    def smoothed_reba_risk(self) -> Optional[str]:
        score = self.smoothed_reba()

        if score is None:
            return None

        from backend.app.cv.posture import _reba_risk_level

        return _reba_risk_level(score)

    # -----------------------------------------------------------------
    # Coverage
    #
    # Coverage is purely: valid frames / observed frames. It is NOT a
    # prediction-accuracy or model-confidence metric -- it only says how
    # much of the track's lifetime produced a usable RULA/REBA score.
    # -----------------------------------------------------------------

    def rula_coverage(self) -> float:
        if self.frames_seen == 0:
            return 0.0

        return self.valid_rula_frames / self.frames_seen

    def reba_coverage(self) -> float:
        if self.frames_seen == 0:
            return 0.0

        return self.valid_reba_frames / self.frames_seen

    # -----------------------------------------------------------------
    # Angles
    # -----------------------------------------------------------------

    def last_valid_angles(self) -> Dict[str, Optional[float]]:
        """
        Most recent posture angle snapshot, with the fixed set of angle
        keys. Missing angles stay None -- never fabricated.
        """
        if not self.angle_history:
            return {key: None for key in ANGLE_KEYS}

        latest = self.angle_history[-1]

        return {key: latest.get(key) for key in ANGLE_KEYS}

    # -----------------------------------------------------------------
    # Status
    #
    # This is a plain data-availability flag, not a quality/confidence
    # judgement. Quality thresholds are a separate future step.
    # -----------------------------------------------------------------

    def assessment_status(self) -> str:
        if self.valid_rula_frames == 0 and self.valid_reba_frames == 0:
            return "insufficient_data"

        return "available"

    # -----------------------------------------------------------------
    # Lifecycle
    # -----------------------------------------------------------------

    def frames_missing(self, frame_index: int) -> int:
        return frame_index - self.last_seen_frame

    def is_stale(self, frame_index: int, max_missed_frames: int = MAX_MISSED_FRAMES) -> bool:
        return self.frames_missing(frame_index) > max_missed_frames

    # -----------------------------------------------------------------
    # Final per-track assessment object
    # -----------------------------------------------------------------

    def to_assessment(self) -> Dict[str, Any]:
        """
        Build the final, stable per-track assessment.

        Uses the EXISTING smoothed RULA/REBA values and the EXISTING
        risk-level lookups from posture.py -- no new scoring or risk
        rules are introduced here.
        """
        return {
            "track_id": self.track_id,
            "frames_observed": self.frames_seen,
            "rula": {
                "score": self.smoothed_rula(),
                "risk": self.smoothed_rula_risk(),
                "valid_frames": self.valid_rula_frames,
                "coverage": self.rula_coverage(),
            },
            "reba": {
                "score": self.smoothed_reba(),
                "risk": self.smoothed_reba_risk(),
                "valid_frames": self.valid_reba_frames,
                "coverage": self.reba_coverage(),
            },
            "angles": self.last_valid_angles(),
            "status": self.assessment_status(),
        }

    def summary(self) -> Dict[str, Any]:
        """Raw diagnostic dump of this track's state (history included)."""
        return {
            "track_id": self.track_id,
            "first_seen_frame": self.first_seen_frame,
            "last_seen_frame": self.last_seen_frame,
            "frames_seen": self.frames_seen,
            "valid_rula_frames": self.valid_rula_frames,
            "valid_reba_frames": self.valid_reba_frames,
            "missing_rula_count": self.missing_rula_count,
            "missing_reba_count": self.missing_reba_count,
            "rula_score_history": list(self.rula_score_history),
            "reba_score_history": list(self.reba_score_history),
            "smoothed_rula": self.smoothed_rula(),
            "smoothed_reba": self.smoothed_reba(),
            "smoothed_rula_risk": self.smoothed_rula_risk(),
            "smoothed_reba_risk": self.smoothed_reba_risk(),
            "rula_coverage": self.rula_coverage(),
            "reba_coverage": self.reba_coverage(),
        }


class TrackStateManager:
    """Owns TrackState instances for every ByteTrack track ID seen so far."""

    def __init__(
        self,
        window: int = SMOOTHING_WINDOW,
        max_missed_frames: int = MAX_MISSED_FRAMES,
    ) -> None:
        self.window = window
        self.max_missed_frames = max_missed_frames
        self._tracks: Dict[int, TrackState] = {}
        self._finalized: Dict[int, Dict[str, Any]] = {}

    def update(
        self,
        track_id: int,
        result: Dict[str, Any],
        frame_index: int,
    ) -> TrackState:
        track = self._tracks.get(track_id)

        if track is None:
            track = TrackState(
                track_id=track_id,
                first_seen_frame=frame_index,
                window=self.window,
            )
            self._tracks[track_id] = track

        track.update(result, frame_index)

        return track

    def get(self, track_id: int) -> Optional[TrackState]:
        return self._tracks.get(track_id)

    def get_active_tracks(self) -> List[TrackState]:
        return list(self._tracks.values())

    def remove_stale_tracks(self, frame_index: int) -> List[int]:
        """Finalize and drop tracks that have been missing too long."""
        stale_ids = [
            track_id
            for track_id, track in self._tracks.items()
            if track.is_stale(frame_index, self.max_missed_frames)
        ]

        for track_id in stale_ids:
            self.finalize_track(track_id)

        return stale_ids

    def finalize_track(self, track_id: int) -> Optional[Dict[str, Any]]:
        """Remove a track from the active set and store its final assessment."""
        track = self._tracks.pop(track_id, None)

        if track is None:
            return None

        assessment = track.to_assessment()
        self._finalized[track_id] = assessment

        return assessment

    def finalize_all(self) -> Dict[int, Dict[str, Any]]:
        for track_id in list(self._tracks.keys()):
            self.finalize_track(track_id)

        return self._finalized

    def get_finalized(self) -> Dict[int, Dict[str, Any]]:
        return self._finalized
