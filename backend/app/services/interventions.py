"""
Posture interventions: measured posture -> ergonomic finding -> recommended action.

    region measurements (stored component severities and derived angles)
      -> region status (low / attention / elevated / high)
      -> intervention for each region that needs action, most important first
      -> API (assessment summary, report content), PDF and app

Everything is derived deterministically from the stored assessment, so the
same assessment always yields the same interventions wherever they are shown.
No score or angle is produced here.

VigilEx observes posture. The wording describes observed posture and
workplace changes; it never refers to injury, pain, fatigue or health.
"""

from __future__ import annotations

import math
from typing import Any

from app.cv.pose_estimator import MIN_LANDMARK_CONFIDENCE, NOSE, landmarks_to_posture_dict
from app.cv.posture import _trunk_band
from app.schemas.analysis import (
    BackPosture,
    Intervention,
    InterventionEvidence,
    RegionMeasurement,
)

# ------------------------------------------------------------------ wording

CONSISTENT_SHARE = 0.66
REPEATED_SHARE = 0.2


def frequency_word(flagged: int, measured: int) -> str:
    share = flagged / measured if measured else 0.0
    return "consistently" if share >= CONSISTENT_SHARE else "repeatedly" if share >= REPEATED_SHARE else "briefly"


# ------------------------------------------------------------------ region status

STATUS_RANK = {"not_measured": 0, "low": 1, "attention": 2, "elevated": 3, "high": 4}


def region_status(region: RegionMeasurement, overall: str | None) -> str:
    """How strongly a region is presented, from its peak backend component severity.

    A region at the backend's high component severity reads "high" only when
    the overall risk is high; otherwise "elevated".
    """
    if region.peak_severity is None:
        return "not_measured"
    if region.peak_severity == "ok":
        return "low"
    if region.peak_severity == "moderate":
        return "attention"
    return "high" if overall in ("high", "very_high") else "elevated"


# ------------------------------------------------------------------ back / trunk metric

# VigilEx camera yaw: 0 = side-on (profile), 90 = frontal. Trunk inclination is
# measured in the image plane, so it is most reliable at or below this yaw.
PROFILE_YAW_DEG = 45.0
# Below this share of side-on frames, forward bending may be under-measured.
MIN_SIDE_ON_SHARE = 0.5
# Facing direction is read from the nose's horizontal offset from the shoulder;
# smaller offsets (relative to torso length) are too ambiguous to use.
MIN_FACING_OFFSET = 0.1
# Share of inclined frames that must agree for a single lean direction.
DIRECTION_AGREEMENT = 2 / 3


def _lean(landmarks: list[dict[str, Any]]) -> int | None:
    """+1 trunk leaning toward the side the worker faces, -1 away from it, None if unknown."""
    if len(landmarks) < 17:
        return None
    triples = [(p.get("x", 0.0), p.get("y", 0.0), p.get("confidence", 0.0)) for p in landmarks]
    # The same body side and points the posture scoring used.
    points = landmarks_to_posture_dict(triples)
    shoulder, hip = points.get("shoulder"), points.get("hip")
    nose = triples[NOSE]
    if shoulder is None or hip is None or nose[2] < MIN_LANDMARK_CONFIDENCE:
        return None
    torso = math.hypot(shoulder[0] - hip[0], shoulder[1] - hip[1])
    facing = nose[0] - shoulder[0]
    if torso == 0 or abs(facing) < MIN_FACING_OFFSET * torso:
        return None
    lean = (shoulder[0] - hip[0]) * (1 if facing > 0 else -1)
    return 1 if lean > 0 else -1 if lean < 0 else None


def back_posture(keyframes: list[dict[str, Any]], derived_angles: dict[str, Any] | None) -> BackPosture | None:
    """The back/trunk metric for an assessment, or None when the trunk was never measured."""
    measured = inclined = side_on = forward = backward = 0
    for frame in keyframes:
        trunk = ((frame.get("rula") or {}).get("angles") or {}).get("trunk_deg")
        if trunk is None:
            continue
        measured += 1
        yaw = frame.get("camera_yaw_deg")
        if yaw is not None and yaw <= PROFILE_YAW_DEG:
            side_on += 1
        # Beyond the upright band of the RULA/REBA trunk score (same thresholds).
        if _trunk_band(trunk) >= 2:
            inclined += 1
            lean = _lean(frame.get("landmarks") or [])
            forward += lean == 1
            backward += lean == -1
    if measured == 0:
        return None

    known = forward + backward
    direction = None
    if known:
        if forward >= DIRECTION_AGREEMENT * known:
            direction = "forward"
        elif backward >= DIRECTION_AGREEMENT * known:
            direction = "backward"
        else:
            direction = "mixed"

    angles = derived_angles or {}
    side_on_fraction = round(side_on / measured, 3)
    return BackPosture(
        average_deg=angles.get("trunk_deg_avg"),
        peak_deg=angles.get("trunk_deg_max"),
        frames_measured=measured,
        frames_inclined=inclined,
        direction=direction,
        side_on_fraction=side_on_fraction,
        view_limited=side_on_fraction < MIN_SIDE_ON_SHARE,
    )


# ------------------------------------------------------------------ interventions

# A region needs action when it reached at least this status in at least this many frames.
MIN_STATUS = "attention"
MIN_FLAGGED_FRAMES = 2

# Product policy: the back is the primary concern for manual work, so a back
# finding at "attention" or above is always the priority action. Other regions
# are ordered by status, then by how often they were flagged.
BACK_FIRST = True

AREA_ORDER = ("back", "knees", "neck", "upper_arm")
_AREA_FOR_METRIC = {"trunk": "back", "knee_flexion": "knees", "neck": "neck", "upper_arm": "upper_arm"}


def _deg(value: float | None) -> str:
    return "—" if value is None else f"{value:.0f}°"


def _frames(region: RegionMeasurement) -> str:
    return f"{region.frames_flagged} of {region.frames_measured} measured frames"


def _back(region: RegionMeasurement, status: str, back: BackPosture | None) -> tuple[str, str, str]:
    direction = back.direction if back else None
    lead = {
        "forward": "Forward trunk positioning",
        "backward": "Backward trunk lean",
    }.get(direction, "Trunk inclination away from upright")
    observation = (
        f"{lead} was observed {frequency_word(region.frames_flagged, region.frames_measured)} during the assessment, "
        f"beyond the upright range in {_frames(region)} (peak {_deg(region.maximum_deg)})."
    )
    if back is not None and back.view_limited:
        observation += " The camera was not side-on for most of the video, so forward bending may be larger than measured."
    if direction == "backward":
        recommendation = (
            "Keep the back closer to a neutral position where practical. Avoid leaning back to reach or view "
            "the work by placing it in front of the body."
        )
    else:
        recommendation = (
            "Keep the back closer to a neutral position where practical. Reduce repeated forward bending by "
            "bringing the task closer or adjusting the work surface."
        )
    if status in ("elevated", "high"):
        recommendation += " Avoid holding the trunk bent for long periods."
    return "Protect the back", observation, recommendation


def _knees(region: RegionMeasurement, status: str) -> tuple[str, str, str]:
    deep = status in ("elevated", "high")
    word = frequency_word(region.frames_flagged, region.frames_measured)
    return (
        "Reduce deep knee bending" if deep else "Reduce knee bending",
        f"{'Deep knee bending' if deep else 'Knee bending'} was observed {word} during the assessment, "
        f"in {_frames(region)} (peak {_deg(region.maximum_deg)} of knee flexion).",
        "Where practical, raise or reposition the work area to reduce prolonged or repeated "
        f"{'deep ' if deep else ''}knee flexion.",
    )


def _neck(region: RegionMeasurement, status: str) -> tuple[str, str, str]:
    word = frequency_word(region.frames_flagged, region.frames_measured)
    return (
        "Reduce neck bending",
        f"Neck bending away from upright was observed {word} during the assessment, "
        f"in {_frames(region)} (peak {_deg(region.maximum_deg)}).",
        "Bring the task closer to eye level where practical and avoid prolonged downward viewing.",
    )


def _upper_arm(region: RegionMeasurement, status: str) -> tuple[str, str, str]:
    word = frequency_word(region.frames_flagged, region.frames_measured)
    return (
        "Keep work within a comfortable reach",
        f"Raised upper-arm positions were observed {word} during the assessment, "
        f"in {_frames(region)} (peak {_deg(region.maximum_deg)} from the body).",
        "Position frequently used work within a comfortable reach to reduce prolonged arm elevation.",
    )


def build_interventions(
    regions: list[RegionMeasurement],
    overall: str | None,
    back: BackPosture | None,
) -> list[Intervention]:
    """One intervention per region that needs action, most important first."""
    found: list[tuple[tuple, Intervention]] = []
    for region in regions:
        area = _AREA_FOR_METRIC[region.metric]
        status = region_status(region, overall)
        if STATUS_RANK[status] < STATUS_RANK[MIN_STATUS] or region.frames_flagged < MIN_FLAGGED_FRAMES:
            continue
        if area == "back":
            title, observation, recommendation = _back(region, status, back)
        elif area == "knees":
            title, observation, recommendation = _knees(region, status)
        elif area == "neck":
            title, observation, recommendation = _neck(region, status)
        else:
            title, observation, recommendation = _upper_arm(region, status)

        back_first = BACK_FIRST and area == "back"
        priority = "high" if back_first or status in ("elevated", "high") else "medium"
        share = region.frames_flagged / region.frames_measured if region.frames_measured else 0.0
        key = (0 if back_first else 1, -STATUS_RANK[status], -share, AREA_ORDER.index(area))
        found.append((
            key,
            Intervention(
                area=area,
                priority=priority,
                status=status,
                title=title,
                observation=observation,
                recommendation=recommendation,
                evidence=InterventionEvidence(
                    average_deg=region.average_deg,
                    peak_deg=region.maximum_deg,
                    frames_flagged=region.frames_flagged,
                    frames_measured=region.frames_measured,
                ),
            ),
        ))
    return [intervention for _, intervention in sorted(found, key=lambda item: item[0])]


_OPPORTUNITY = {
    "back": "reduce forward trunk bending",
    "knees": "reduce deep knee flexion",
    "neck": "reduce neck bending",
    "upper_arm": "keep work within a comfortable reach",
}


def _opportunity(intervention: Intervention, back: BackPosture | None) -> str:
    if intervention.area == "back" and (back is None or back.direction != "forward"):
        return "keep the back closer to neutral"
    if intervention.area == "knees" and intervention.status == "attention":
        return "reduce knee bending"
    return _OPPORTUNITY[intervention.area]


def action_summary(
    overall: str | None,
    interventions: list[Intervention],
    back: BackPosture | None,
) -> str | None:
    """One plain-language sentence pair: how risky the posture was and what matters most."""
    if overall is None:
        return None
    risk = overall.replace("_", " ")
    first = f"Posture risk was {risk} during the analyzed movement."
    if not interventions:
        return f"{first} No body region was flagged often enough to need a specific action."
    actions = [_opportunity(i, back) for i in interventions[:2]]
    if len(actions) == 1:
        return f"{first} The main opportunity is to {actions[0]}."
    return f"{first} The main opportunities are to {actions[0]} and {actions[1]}."
