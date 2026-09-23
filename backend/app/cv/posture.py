"""
VigilEx posture scoring.

RULA / REBA scoring from 2D pose landmarks.

Notes
-----
- This module is geometry/scoring only.
- Camera-view validation is handled upstream.
- YOLO11n-Pose / ByteTrack are handled upstream.
- The current VigilEx pose estimator may provide `ear`, `head`, or `neck`
  for the head/neck reference point.
- COCO-17 does not provide finger landmarks, so wrist twist is
  represented as a fixed lowest-risk value and marked as not measured.
- Force/load, muscle use, coupling, and activity are fixed at their
  lowest-risk values because those inputs are not available from the
  current pose model.
"""

from __future__ import annotations

import math
from typing import Any, Dict, Optional, Tuple


# ---------------------------------------------------------------------------
# VigilEx methodology version
# ---------------------------------------------------------------------------

METHODOLOGY_VERSION = "vigilex-rula-reba-v1"


# ---------------------------------------------------------------------------
# Basic geometry
# ---------------------------------------------------------------------------

Point = Tuple[float, float]


def _subtract(a: Point, b: Point) -> Point:
    return a[0] - b[0], a[1] - b[1]


def _dot(a: Point, b: Point) -> float:
    return a[0] * b[0] + a[1] * b[1]


def _norm(v: Point) -> float:
    return math.sqrt(v[0] ** 2 + v[1] ** 2)


def _angle_between_vectors(
    a: Point,
    b: Point,
) -> Optional[float]:
    na = _norm(a)
    nb = _norm(b)

    if na == 0 or nb == 0:
        return None

    cosine = _dot(a, b) / (na * nb)
    cosine = max(-1.0, min(1.0, cosine))

    return math.degrees(math.acos(cosine))


def angle_between(
    a: Optional[Point],
    vertex: Optional[Point],
    b: Optional[Point],
) -> Optional[float]:
    """
    Angle ABC, where B is the vertex.
    """
    if a is None or vertex is None or b is None:
        return None

    return _angle_between_vectors(
        _subtract(a, vertex),
        _subtract(b, vertex),
    )


def angle_from_vertical(
    a: Optional[Point],
    b: Optional[Point],
) -> Optional[float]:
    """
    Angle of vector a -> b from the vertical axis.

    0 degrees = vertical
    90 degrees = horizontal
    """
    if a is None or b is None:
        return None

    dx = b[0] - a[0]
    dy = b[1] - a[1]

    length = math.sqrt(dx * dx + dy * dy)

    if length == 0:
        return None

    # Image coordinates have y increasing downward, but the absolute
    # deviation from vertical is what matters here.
    cosine = abs(dy) / length
    cosine = max(-1.0, min(1.0, cosine))

    return math.degrees(math.acos(cosine))


# ---------------------------------------------------------------------------
# RULA / REBA component bands
# ---------------------------------------------------------------------------

def _upper_arm_band(angle_deg: Optional[float]) -> int:
    if angle_deg is None:
        return 1

    if angle_deg <= 20:
        return 1
    if angle_deg <= 45:
        return 2
    if angle_deg <= 90:
        return 3

    return 4


def _lower_arm_band(angle_deg: Optional[float]) -> int:
    if angle_deg is None:
        return 1

    if 60 <= angle_deg <= 100:
        return 1

    return 2


_WRIST_SCORE = 1
_WRIST_TWIST_SCORE = 1


def _rula_neck_band(angle_deg: Optional[float]) -> int:
    if angle_deg is None:
        return 1

    if angle_deg <= 10:
        return 1

    if angle_deg <= 20:
        return 2

    return 3


def _reba_neck_band(angle_deg: Optional[float]) -> int:
    if angle_deg is None:
        return 1

    if angle_deg <= 20:
        return 1

    return 2


def _trunk_band(angle_deg: Optional[float]) -> int:
    if angle_deg is None:
        return 1

    if angle_deg <= 10:
        return 1

    if angle_deg <= 20:
        return 2

    if angle_deg <= 60:
        return 3

    return 4


def _knee_flexion_deg(
    hip: Optional[Point],
    knee: Optional[Point],
    ankle: Optional[Point],
) -> Optional[float]:
    joint_angle = angle_between(hip, knee, ankle)

    if joint_angle is None:
        return None

    return 180.0 - joint_angle


def _rula_legs_score(
    knee_flexion: Optional[float],
) -> int:
    if knee_flexion is None:
        return 1

    if knee_flexion < 30:
        return 1

    return 2


def _reba_legs_score(
    knee_flexion: Optional[float],
) -> int:
    if knee_flexion is None:
        return 1

    if knee_flexion > 60:
        return 3

    if knee_flexion >= 30:
        return 2

    return 1


# ---------------------------------------------------------------------------
# RULA lookup tables
# ---------------------------------------------------------------------------

RULA_TABLE_A = [
    [1, 2, 2, 2, 3, 3, 4, 5],
    [2, 2, 2, 3, 3, 4, 5, 5],
    [2, 3, 3, 3, 4, 5, 5, 5],
    [2, 3, 3, 3, 4, 5, 5, 5],
    [3, 3, 4, 4, 4, 5, 6, 7],
    [3, 4, 4, 4, 5, 6, 7, 7],
    [3, 4, 4, 5, 6, 6, 7, 7],
]


RULA_TABLE_B = [
    [1, 2, 3, 3, 4, 5, 5],
    [2, 2, 3, 4, 4, 5, 5],
    [3, 3, 3, 4, 4, 5, 6],
    [3, 3, 3, 4, 5, 6, 6],
    [4, 4, 4, 5, 6, 7, 7],
    [5, 5, 5, 6, 7, 7, 7],
    [5, 5, 6, 6, 7, 7, 7],
    [5, 6, 6, 7, 7, 7, 7],
]


RULA_TABLE_C = [
    [1, 2, 3, 3, 4, 5, 5, 5],
    [2, 2, 3, 4, 4, 5, 6, 6],
    [3, 3, 3, 4, 4, 5, 6, 6],
    [3, 3, 4, 5, 5, 6, 7, 7],
    [4, 4, 5, 6, 6, 7, 7, 7],
    [4, 5, 6, 6, 7, 7, 7, 7],
    [5, 6, 6, 7, 7, 7, 7, 7],
]


def _lookup_rula_a(
    upper_arm: int,
    lower_arm: int,
    wrist: int,
    wrist_twist: int,
) -> int:
    upper_arm = max(1, min(4, upper_arm))
    lower_arm = max(1, min(2, lower_arm))
    wrist = max(1, min(3, wrist))
    wrist_twist = max(1, min(2, wrist_twist))

    wrist_index = (wrist - 1) * 2 + (wrist_twist - 1)

    rows = [
        [1, 2, 2, 2, 3, 3, 4, 5],
        [2, 2, 2, 3, 3, 4, 5, 5],
        [2, 3, 3, 3, 4, 5, 5, 5],
        [2, 3, 3, 3, 4, 5, 5, 5],
        [3, 3, 4, 4, 4, 5, 6, 7],
        [3, 4, 4, 4, 5, 6, 7, 7],
        [3, 4, 4, 5, 6, 6, 7, 7],
        [4, 4, 5, 5, 6, 7, 7, 7],
    ]

    row = (upper_arm - 1) * 2 + (lower_arm - 1)

    return rows[min(row, len(rows) - 1)][
        min(wrist_index, len(rows[0]) - 1)
    ]


def _lookup_rula_b(
    neck: int,
    trunk: int,
    legs: int,
) -> int:
    neck = max(1, min(3, neck))
    trunk = max(1, min(4, trunk))
    legs = max(1, min(2, legs))

    table = [
        [1, 2, 3, 3, 4, 5, 5],
        [2, 2, 3, 4, 4, 5, 5],
        [3, 3, 3, 4, 4, 5, 6],
        [3, 3, 3, 4, 5, 6, 6],
        [4, 4, 4, 5, 6, 7, 7],
        [5, 5, 5, 6, 7, 7, 7],
        [5, 5, 6, 6, 7, 7, 7],
        [5, 6, 6, 7, 7, 7, 7],
    ]

    row = (neck - 1) * 2 + (legs - 1)
    col = trunk - 1

    return table[min(row, len(table) - 1)][
        min(col, len(table[0]) - 1)
    ]


def _rula_final_score(
    score_a: int,
    score_b: int,
) -> int:
    a = max(1, min(7, score_a))
    b = max(1, min(7, score_b))

    return RULA_TABLE_C[a - 1][b - 1]


# ---------------------------------------------------------------------------
# REBA lookup tables
# ---------------------------------------------------------------------------

REBA_TABLE_A = [
    [1, 2, 3, 4, 1, 2, 3, 4],
    [1, 2, 3, 4, 1, 2, 3, 4],
    [2, 3, 4, 5, 2, 3, 4, 5],
    [3, 4, 5, 6, 3, 4, 5, 6],
    [4, 5, 6, 7, 4, 5, 6, 7],
    [5, 6, 7, 8, 5, 6, 7, 8],
]


REBA_TABLE_B = [
    [1, 2, 2, 3, 3, 4],
    [1, 2, 2, 3, 4, 5],
    [3, 4, 4, 5, 5, 6],
    [4, 5, 5, 6, 7, 8],
    [6, 7, 7, 8, 8, 9],
    [7, 8, 8, 9, 9, 9],
]


REBA_TABLE_C = [
    [1, 1, 1, 2, 3, 3, 4, 5, 6, 7, 7, 7],
    [1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 7, 8],
    [2, 3, 3, 3, 4, 5, 5, 6, 7, 7, 8, 8],
    [3, 4, 4, 4, 5, 6, 6, 7, 8, 8, 9, 9],
    [4, 4, 4, 5, 6, 7, 7, 8, 8, 9, 9, 9],
    [6, 6, 6, 7, 8, 8, 9, 9, 10, 10, 10, 10],
    [7, 7, 7, 8, 9, 9, 9, 10, 10, 11, 11, 11],
    [8, 8, 8, 9, 10, 10, 10, 11, 11, 11, 12, 12],
    [9, 9, 9, 10, 10, 10, 11, 11, 12, 12, 12, 12],
    [10, 10, 10, 11, 11, 11, 12, 12, 12, 12, 12, 12],
]


def _lookup_reba_a(
    trunk: int,
    neck: int,
    legs: int,
) -> int:
    trunk = max(1, min(5, trunk))
    neck = max(1, min(2, neck))
    legs = max(1, min(3, legs))

    row = min(
        trunk + neck - 2,
        len(REBA_TABLE_A) - 1,
    )

    col = min(
        (legs - 1) * 2,
        len(REBA_TABLE_A[0]) - 1,
    )

    return REBA_TABLE_A[row][col]


def _lookup_reba_b(
    upper_arm: int,
    lower_arm: int,
    wrist: int,
) -> int:
    upper_arm = max(1, min(4, upper_arm))
    lower_arm = max(1, min(2, lower_arm))
    wrist = max(1, min(3, wrist))

    row = min(
        (upper_arm - 1) * 2 + (lower_arm - 1),
        len(REBA_TABLE_B) - 1,
    )

    col = min(
        wrist - 1,
        len(REBA_TABLE_B[0]) - 1,
    )

    return REBA_TABLE_B[row][col]


def _lookup_reba_c(
    score_a: int,
    score_b: int,
) -> int:
    score_a = max(1, min(12, score_a))
    score_b = max(1, min(12, score_b))

    return REBA_TABLE_C[score_a - 1][score_b - 1]


# ---------------------------------------------------------------------------
# Risk levels
# ---------------------------------------------------------------------------

def _rula_risk_level(
    score: Optional[int],
) -> str:
    if score is None:
        return "not_measured"

    if score <= 2:
        return "low"

    if score <= 4:
        return "moderate"

    if score <= 6:
        return "high"

    return "very_high"


def _reba_risk_level(
    score: Optional[int],
) -> str:
    if score is None:
        return "not_measured"

    if score <= 1:
        return "negligible"

    if score <= 3:
        return "low"

    if score <= 7:
        return "medium"

    if score <= 10:
        return "high"

    return "very_high"


# ---------------------------------------------------------------------------
# Component reporting
# ---------------------------------------------------------------------------

def _severity(
    band: Optional[int],
    max_band: int,
    measured: bool = True,
) -> str:
    if not measured or band is None:
        return "not_measured"

    ratio = band / max_band

    if ratio <= 0.33:
        return "ok"

    if ratio <= 0.66:
        return "moderate"

    return "high"


def _component(
    name: str,
    angle_deg: Optional[float],
    band: Optional[int],
    max_band: int,
    measured: bool = True,
) -> Dict[str, Any]:
    return {
        "name": name,
        "angle_deg": (
            round(angle_deg, 2)
            if angle_deg is not None
            else None
        ),
        "band": band,
        "max_band": max_band,
        "measured": measured,
        "severity": _severity(
            band,
            max_band,
            measured=measured,
        ),
    }


# ---------------------------------------------------------------------------
# Main posture assessment
# ---------------------------------------------------------------------------

def assess_posture(
    landmarks: Dict[str, Point],
) -> Dict[str, Any]:
    """
    Calculate RULA and REBA from VigilEx pose landmarks.

    Expected landmark keys:

        head / neck / ear
        shoulder
        elbow
        wrist
        hip
        knee
        ankle

    Each landmark is an (x, y) pair.

    The current VigilEx pose estimator provides `head` and `neck`.
    `ear` is also supported for backwards compatibility.
    """

    measurement_empty = {
        "upper_arm": False,
        "lower_arm": False,
        "neck": False,
        "trunk": False,
        "legs": False,
        "wrist": False,
        "wrist_twist": False,
    }

    if not landmarks or "shoulder" not in landmarks:
        return {
            "methodology_version": METHODOLOGY_VERSION,
            "measurement": measurement_empty,
            "rula": {
                "score": None,
                "risk": "not_measured",
                "angles": {},
                "components": {},
            },
            "reba": {
                "score": None,
                "risk": "not_measured",
                "angles": {},
                "components": {},
            },
            "label": "unknown",
        }

    shoulder = landmarks.get("shoulder")

    # -----------------------------------------------------------------------
    # Head / neck compatibility
    # -----------------------------------------------------------------------
    #
    # New VigilEx pose_estimator.py:
    #
    #     head
    #     neck
    #
    # Older posture pipeline:
    #
    #     ear
    #
    # Prefer ear when available, then head, then neck.
    # -----------------------------------------------------------------------

    ear = landmarks.get("ear")

    if ear is None:
        ear = landmarks.get("head")

    if ear is None:
        ear = landmarks.get("neck")

    elbow = landmarks.get("elbow")
    wrist = landmarks.get("wrist")
    hip = landmarks.get("hip")
    knee = landmarks.get("knee")
    ankle = landmarks.get("ankle")

    # -----------------------------------------------------------------------
    # Geometry
    # -----------------------------------------------------------------------

    # Shoulder -> elbow relative to vertical.
    upper_arm_angle = angle_from_vertical(
        shoulder,
        elbow,
    )

    # Elbow angle.
    elbow_joint_angle = angle_between(
        shoulder,
        elbow,
        wrist,
    )

    lower_arm_flexion = (
        180.0 - elbow_joint_angle
        if elbow_joint_angle is not None
        else None
    )

    # Shoulder -> head/ear relative to vertical.
    neck_angle = angle_from_vertical(
        shoulder,
        ear,
    )

    # Hip -> shoulder relative to vertical.
    trunk_angle = angle_from_vertical(
        hip,
        shoulder,
    )

    # Hip -> knee -> ankle.
    knee_flexion = _knee_flexion_deg(
        hip,
        knee,
        ankle,
    )

    # -----------------------------------------------------------------------
    # Measurement state
    # -----------------------------------------------------------------------

    upper_arm_measured = upper_arm_angle is not None
    lower_arm_measured = lower_arm_flexion is not None
    neck_measured = neck_angle is not None
    trunk_measured = trunk_angle is not None
    legs_measured = knee_flexion is not None

    measurement = {
        "upper_arm": upper_arm_measured,
        "lower_arm": lower_arm_measured,
        "neck": neck_measured,
        "trunk": trunk_measured,
        "legs": legs_measured,

        # COCO-17 cannot properly measure these.
        "wrist": False,
        "wrist_twist": False,
    }

    # -----------------------------------------------------------------------
    # Component bands
    # -----------------------------------------------------------------------

    upper_arm = _upper_arm_band(
        upper_arm_angle,
    )

    lower_arm = _lower_arm_band(
        lower_arm_flexion,
    )

    rula_neck = _rula_neck_band(
        neck_angle,
    )

    reba_neck = _reba_neck_band(
        neck_angle,
    )

    trunk = _trunk_band(
        trunk_angle,
    )

    rula_legs = _rula_legs_score(
        knee_flexion,
    )

    reba_legs = _reba_legs_score(
        knee_flexion,
    )

    # -----------------------------------------------------------------------
    # Angles
    # -----------------------------------------------------------------------

    angles = {
        "upper_arm_deg": (
            round(upper_arm_angle, 2)
            if upper_arm_angle is not None
            else None
        ),
        "lower_arm_flexion_deg": (
            round(lower_arm_flexion, 2)
            if lower_arm_flexion is not None
            else None
        ),
        "neck_deg": (
            round(neck_angle, 2)
            if neck_angle is not None
            else None
        ),
        "trunk_deg": (
            round(trunk_angle, 2)
            if trunk_angle is not None
            else None
        ),
        "knee_flexion_deg": (
            round(knee_flexion, 2)
            if knee_flexion is not None
            else None
        ),
    }

    # -----------------------------------------------------------------------
    # RULA
    # -----------------------------------------------------------------------

    score_a = _lookup_rula_a(
        upper_arm,
        lower_arm,
        _WRIST_SCORE,
        _WRIST_TWIST_SCORE,
    )

    score_b = _lookup_rula_b(
        rula_neck,
        trunk,
        rula_legs,
    )

    rula_grand_score = _rula_final_score(
        score_a,
        score_b,
    )

    # -----------------------------------------------------------------------
    # REBA
    # -----------------------------------------------------------------------

    reba_score_a = _lookup_reba_a(
        trunk,
        reba_neck,
        reba_legs,
    )

    reba_score_b = _lookup_reba_b(
        upper_arm,
        lower_arm,
        _WRIST_SCORE,
    )

    reba_score_c = _lookup_reba_c(
        reba_score_a,
        reba_score_b,
    )

    # -----------------------------------------------------------------------
    # RULA component reporting
    # -----------------------------------------------------------------------

    rula_components = {
        "upper_arm": _component(
            "Upper arm",
            upper_arm_angle,
            upper_arm,
            4,
            measured=upper_arm_measured,
        ),
        "lower_arm": _component(
            "Lower arm (elbow)",
            lower_arm_flexion,
            lower_arm,
            2,
            measured=lower_arm_measured,
        ),
        "wrist": _component(
            "Wrist",
            None,
            _WRIST_SCORE,
            4,
            measured=False,
        ),
        "wrist_twist": _component(
            "Wrist twist",
            None,
            _WRIST_TWIST_SCORE,
            2,
            measured=False,
        ),
        "neck": _component(
            "Neck",
            neck_angle,
            rula_neck,
            3,
            measured=neck_measured,
        ),
        "trunk": _component(
            "Trunk",
            trunk_angle,
            trunk,
            4,
            measured=trunk_measured,
        ),
        "legs": _component(
            "Legs",
            knee_flexion,
            rula_legs,
            2,
            measured=legs_measured,
        ),
    }

    # -----------------------------------------------------------------------
    # REBA component reporting
    # -----------------------------------------------------------------------

    reba_components = {
        "upper_arm": _component(
            "Upper arm",
            upper_arm_angle,
            upper_arm,
            4,
            measured=upper_arm_measured,
        ),
        "lower_arm": _component(
            "Lower arm (elbow)",
            lower_arm_flexion,
            lower_arm,
            2,
            measured=lower_arm_measured,
        ),
        "wrist": _component(
            "Wrist",
            None,
            _WRIST_SCORE,
            3,
            measured=False,
        ),
        "neck": _component(
            "Neck",
            neck_angle,
            reba_neck,
            2,
            measured=neck_measured,
        ),
        "trunk": _component(
            "Trunk",
            trunk_angle,
            trunk,
            4,
            measured=trunk_measured,
        ),
        "legs": _component(
            "Legs",
            knee_flexion,
            reba_legs,
            3,
            measured=legs_measured,
        ),
    }

    # -----------------------------------------------------------------------
    # Final result
    # -----------------------------------------------------------------------

    return {
        "methodology_version": METHODOLOGY_VERSION,

        "measurement": measurement,

        "rula": {
            "score": rula_grand_score,
            "risk": _rula_risk_level(
                rula_grand_score,
            ),
            "angles": angles,
            "components": rula_components,
        },

        "reba": {
            "score": reba_score_c,
            "risk": _reba_risk_level(
                reba_score_c,
            ),
            "angles": angles,
            "components": reba_components,
        },

        "label": "scored",
    }