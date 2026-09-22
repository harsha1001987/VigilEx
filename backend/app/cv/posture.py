"""RULA (Rapid Upper Limb Assessment) and REBA (Rapid Entire Body
Assessment) ergonomic risk scoring from 2D pose keypoints. No camera, no ML
model dependency -- pure geometry, (x, y) pixel tuples in.

Replaces the earlier 4-check ok/slouching/forward_head/leaning/bending/
over_extension system (see git history) with the two standard published
ergonomic scoring methods (McAtamney & Corlett 1993 for RULA; Hignett &
McAtamney 2000 for REBA), at the user's request. Camera positioning
requirement is UNCHANGED and just as hard a constraint as before: a
SIDE-VIEW / profile camera, for the same reason as the old system (the
neck/trunk/upper-arm angle-from-vertical measurements are sagittal-plane
geometry; see pipeline/pose_estimator.py's module docstring for the
camera-orientation gate that enforces this in main.py).

*** THIS IS A POSTURE-ONLY APPROXIMATION, NOT THE FULL PUBLISHED METHOD ***
Both RULA and REBA officially require several inputs that cannot be
observed from a camera at all:
  - Force/load score: the weight of whatever's being handled. Fixed at 0
    (lowest risk) -- there's no way to see load weight from pose alone.
  - Muscle-use score: whether a posture is held static >1min or repeated
    >4x/min. Fixed at 0 -- this is a task/temporal judgment, not a
    single-frame geometric one, and this pipeline doesn't track posture
    duration/repetition history per person.
  - Coupling score (REBA only): quality of hand grip on a load. Fixed at 0
    (good) -- unmeasurable without knowing what's being held.
  - Activity score (REBA only): task-characteristic judgment. Fixed at 0.
  - Wrist score/twist: RULA/REBA's wrist score measures flexion/extension
    of the wrist joint itself (hand angle relative to forearm) and, for
    RULA, forearm pronation/supination (twist). COCO's 17 keypoints (this
    project's pose backend, see pipeline/pose_estimator.py) include a
    wrist POINT but no hand/finger landmarks -- there is no way to measure
    the hand's own angle. Both fixed at their lowest-risk band (RULA wrist
    1, twist 1; REBA wrist 1).
  - Legs "well supported and evenly balanced" (RULA) / "bilateral weight
    bearing" (REBA): approximated via knee flexion angle only (see
    _leg_score) -- there's no way to assess actual weight distribution or
    foot support from pose alone, but knee bend is a reasonable, directly
    measurable proxy that both published methods explicitly use as part of
    their own official leg-scoring criteria (REBA especially: its leg
    adjustment IS defined by knee flexion degree).

Every other component (upper arm, lower arm/elbow, neck, trunk) IS
genuinely measured from pose angles below.

The lookup tables in this module (RULA Table A/B/C, REBA Table A/B/C) are
large, hand-transcribed reproductions of the standard published worksheets --
transcribing ~100+ numeric cells by hand is a real error-risk class that
doesn't apply to a formula. They were spot-checked 2026-07-09 against
authoritative references (see each table's own dated citation comment below):
RULA Table A against two independent open-source implementations that agree
byte-for-byte plus the canonical ergo-plus worksheet -- which corrected five
originally-mistranscribed rows -- and the remaining five tables matched their
references exactly. Every table is now pinned by exact-cell regression tests
in tests/test_posture.py, so a future re-transcription error fails loudly.

Resolves a previously-open limitation: the old system's lower_back check
had an unresolved "reads as continuously bending while seated" limitation,
patched with an ad-hoc seated gate. REBA's legs score is explicitly
knee-flexion-based in the official method, so knee bend is now a
first-class, legitimately-scored signal rather than a workaround -- no
special seated-detection gate is needed anymore.
"""

import math

_UP_REF = (0, -1)
_DOWN_REF = (0, 1)


def _subtract(a, b):
    return tuple(ai - bi for ai, bi in zip(a, b))


def _dot(a, b):
    return sum(ai * bi for ai, bi in zip(a, b))


def _norm(v):
    return math.sqrt(sum(c * c for c in v))


def _angle_between_vectors(v1, v2):
    norm1 = _norm(v1)
    norm2 = _norm(v2)
    if norm1 == 0 or norm2 == 0:
        raise ValueError("_angle_between_vectors: degenerate (zero-length) vector")
    cos_theta = max(-1.0, min(1.0, _dot(v1, v2) / (norm1 * norm2)))
    return math.degrees(math.acos(cos_theta))


def angle_between(p1, p2, p3):
    """Angle in degrees at p2, formed by the rays p2->p1 and p2->p3."""
    return _angle_between_vectors(_subtract(p1, p2), _subtract(p3, p2))


def angle_from_vertical(v):
    """Angle in degrees between 2D vector v and "straight up"."""
    return _angle_between_vectors(v, _UP_REF)


# ---------------------------------------------------------------------------
# Individual body-part band scores.
#
# All angle-from-vertical/from-hanging-down measurements below are UNSIGNED
# magnitudes, not signed flexion-vs-extension -- distinguishing forward
# flexion from backward extension needs to know which way the body is
# facing, which a single 2D side-view angle can't give without extra
# directional logic this module doesn't have. This mirrors exactly what the
# old 4-check system already did (see git history's _check_shoulder /
# _check_angle_from_vertical) and is a reasonable simplification: RULA's own
# upper-arm bands 1-2 are symmetric between flexion/extension anyway, and
# anatomically-limited backward extension rarely reaches the high-angle
# bands where the direction would otherwise matter.
# ---------------------------------------------------------------------------


def _upper_arm_band(angle_deg):
    """RULA/REBA upper arm score (1-4), from the angle of shoulder->elbow
    away from straight-down (arm hanging = 0deg). Bands identical between
    RULA and REBA. Adjustments (+1 shoulder raised, +1 abducted, -1
    supported/leaning) are not applied -- unmeasurable from pose alone,
    default 0.
    """
    if angle_deg <= 20:
        return 1
    if angle_deg <= 45:
        return 2
    if angle_deg <= 90:
        return 3
    return 4


def _lower_arm_band(flexion_deg):
    """RULA/REBA lower arm (elbow) score (1-2), from elbow flexion angle
    (180 - the raw shoulder-elbow-wrist joint angle; 0 = straight arm).
    Identical bands between RULA and REBA. The +1 "working across midline
    or outside the body" adjustment (RULA only) is not applied --
    unmeasurable without depth/facing information, default 0.
    """
    return 1 if 60 <= flexion_deg <= 100 else 2


# Neither wrist flexion nor wrist twist/deviation is measurable (see module
# docstring) -- both fixed at their lowest-risk band.
_WRIST_SCORE = 1
_WRIST_TWIST_SCORE = 1


def _rula_neck_band(angle_deg):
    """RULA neck score (1-3 here; official table also has a 4th "extension"
    row that this module folds into band 3, see module-level note on
    unsigned angles). +1 twisted / +1 side-bending adjustments not applied
    -- unmeasurable from a single side-view 2D angle, default 0.
    """
    if angle_deg <= 10:
        return 1
    if angle_deg <= 20:
        return 2
    return 3


def _reba_neck_band(angle_deg):
    """REBA neck score (1-2; official 3rd "twisted/side-flexed" state is an
    adjustment this module can't apply, see above)."""
    return 1 if angle_deg <= 20 else 2


def _trunk_band(angle_deg):
    """Trunk flexion score (1-4), shared by RULA and REBA -- both use the
    same angle ranges (upright / 0-20 / 20-60 / >60deg). +1 twisted / +1
    side-bending adjustments not applied, default 0. The official "upright
    (0deg)" vs "0-20deg" boundary is genuinely ambiguous in the published
    wording (both effectively describe near-upright); this module draws
    the line at 10deg.
    """
    if angle_deg <= 10:
        return 1
    if angle_deg <= 20:
        return 2
    if angle_deg <= 60:
        return 3
    return 4


def _knee_flexion_deg(hip, knee, ankle):
    """Knee joint flexion (0 = straight leg, larger = more bent), from the
    hip-knee-ankle angle. Returns None if any point is missing (caller
    defaults to the lowest-risk band in that case, matching this module's
    general "can't measure -> assume lowest risk" treatment for
    unmeasurable inputs)."""
    try:
        return 180.0 - angle_between(hip, knee, ankle)
    except ValueError:
        return None


def _rula_legs_score(knee_flexion_deg):
    """RULA legs score (1-2). RULA's own criterion ("legs/feet supported
    and evenly balanced" vs not) isn't directly observable from pose, so
    this approximates it via knee bend: a near-straight standing leg is
    treated as "supported" (1); a bent knee (crouching, kneeling, one-legged
    stances, etc.) is treated as the less-stable "not supported" (2)."""
    if knee_flexion_deg is None or knee_flexion_deg < 30:
        return 1
    return 2


def _reba_legs_score(knee_flexion_deg):
    """REBA legs score (1-3): base 1 (bilateral standing, this module's
    default when knee data is missing), +1 if knee flexion is 30-60deg,
    +2 if >60deg -- this is REBA's own official knee-flexion-based
    criterion, directly measurable, no approximation needed beyond the
    "missing data -> lowest risk" default."""
    if knee_flexion_deg is None:
        return 1
    if knee_flexion_deg > 60:
        return 3
    if knee_flexion_deg >= 30:
        return 2
    return 1


# ---------------------------------------------------------------------------
# RULA lookup tables (McAtamney & Corlett 1993 "RULA: a survey method for
# the investigation of work-related upper limb disorders").
# ---------------------------------------------------------------------------

# Table A: index [upper_arm(1-6)][lower_arm(1-3)][wrist(1-4)][wrist_twist(1-2)] -> posture score A (1-9)
# Spot-checked 2026-07-09 against two independent open-source reference
# implementations that agree byte-for-byte on this grid (and match the
# canonical ergo-plus RULA worksheet): benellinger/RULA-Rapid-Upper-Limb-
# Assessment- `tableA.csv` and ahmadataka/kcl_ergonomics `src/rula.cpp`'s
# `rulatable[144]`. That cross-check corrected five originally-mistranscribed
# rows -- (1,3), (3,3), (4,1), (4,2), (4,3) -- see tests/test_posture.py's
# exact-cell RULA Table A assertions for the pinned values.
RULA_TABLE_A = {
    (1, 1): {1: (1, 2), 2: (2, 2), 3: (2, 3), 4: (3, 3)},
    (1, 2): {1: (2, 2), 2: (2, 2), 3: (3, 3), 4: (3, 3)},
    (1, 3): {1: (2, 3), 2: (3, 3), 3: (3, 3), 4: (4, 4)},
    (2, 1): {1: (2, 3), 2: (3, 3), 3: (3, 4), 4: (4, 4)},
    (2, 2): {1: (3, 3), 2: (3, 3), 3: (3, 4), 4: (4, 4)},
    (2, 3): {1: (3, 4), 2: (4, 4), 3: (4, 4), 4: (5, 5)},
    (3, 1): {1: (3, 3), 2: (4, 4), 3: (4, 4), 4: (5, 5)},
    (3, 2): {1: (3, 4), 2: (4, 4), 3: (4, 4), 4: (5, 5)},
    (3, 3): {1: (4, 4), 2: (4, 4), 3: (4, 5), 4: (5, 5)},
    (4, 1): {1: (4, 4), 2: (4, 4), 3: (4, 5), 4: (5, 5)},
    (4, 2): {1: (4, 4), 2: (4, 4), 3: (4, 5), 4: (5, 5)},
    (4, 3): {1: (4, 4), 2: (4, 5), 3: (5, 5), 4: (6, 6)},
    (5, 1): {1: (5, 5), 2: (5, 5), 3: (5, 6), 4: (6, 7)},
    (5, 2): {1: (5, 6), 2: (6, 6), 3: (6, 7), 4: (7, 7)},
    (5, 3): {1: (6, 6), 2: (6, 7), 3: (7, 7), 4: (7, 8)},
    (6, 1): {1: (7, 7), 2: (7, 7), 3: (7, 8), 4: (8, 9)},
    (6, 2): {1: (8, 8), 2: (8, 8), 3: (8, 9), 4: (9, 9)},
    (6, 3): {1: (9, 9), 2: (9, 9), 3: (9, 9), 4: (9, 9)},
}


def _rula_table_a(upper_arm, lower_arm, wrist, wrist_twist):
    upper_arm = min(upper_arm, 6)
    lower_arm = min(lower_arm, 3)
    wrist = min(wrist, 4)
    twist_index = 0 if wrist_twist <= 1 else 1
    return RULA_TABLE_A[(upper_arm, lower_arm)][wrist][twist_index]


# Table B: index [neck(1-6)][trunk(1-6)][legs(1-2)] -> posture score B (1-9)
# Spot-checked 2026-07-09 against benellinger `tableB.csv` /
# ahmadataka/kcl_ergonomics -- matched exactly, no corrections needed.
RULA_TABLE_B = {
    1: {1: (1, 3), 2: (2, 3), 3: (3, 4), 4: (5, 5), 5: (6, 6), 6: (7, 7)},
    2: {1: (2, 3), 2: (2, 3), 3: (4, 5), 4: (5, 5), 5: (6, 7), 6: (7, 7)},
    3: {1: (3, 3), 2: (3, 4), 3: (4, 5), 4: (5, 6), 5: (6, 7), 6: (7, 7)},
    4: {1: (5, 5), 2: (5, 6), 3: (6, 7), 4: (7, 7), 5: (7, 7), 6: (8, 8)},
    5: {1: (7, 7), 2: (7, 7), 3: (7, 8), 4: (8, 8), 5: (8, 8), 6: (8, 8)},
    6: {1: (8, 8), 2: (8, 8), 3: (8, 8), 4: (8, 9), 5: (9, 9), 6: (9, 9)},
}


def _rula_table_b(neck, trunk, legs):
    neck = min(neck, 6)
    trunk = min(trunk, 6)
    legs_index = 0 if legs <= 1 else 1
    return RULA_TABLE_B[neck][trunk][legs_index]


# Table C: index [score_a(1-8, capped)][score_b(1-7, capped)] -> grand score (1-7)
# Spot-checked 2026-07-09 against benellinger `tableC.csv` -- matched exactly.
RULA_TABLE_C = {
    1: {1: 1, 2: 2, 3: 3, 4: 3, 5: 4, 6: 5, 7: 5},
    2: {1: 2, 2: 2, 3: 3, 4: 4, 5: 4, 6: 5, 7: 5},
    3: {1: 3, 2: 3, 3: 3, 4: 4, 5: 4, 6: 5, 7: 6},
    4: {1: 3, 2: 3, 3: 3, 4: 4, 5: 5, 6: 6, 7: 6},
    5: {1: 4, 2: 4, 3: 4, 4: 5, 5: 6, 6: 7, 7: 7},
    6: {1: 4, 2: 4, 3: 5, 4: 6, 5: 6, 6: 7, 7: 7},
    7: {1: 5, 2: 5, 3: 6, 4: 6, 5: 7, 6: 7, 7: 7},
    8: {1: 5, 2: 5, 3: 6, 4: 7, 5: 7, 6: 7, 7: 7},
}


def _rula_table_c(score_a, score_b):
    score_a = min(score_a, 8)
    score_b = min(score_b, 7)
    return RULA_TABLE_C[score_a][score_b]


def _rula_risk_level(grand_score):
    if grand_score <= 2:
        return "acceptable"
    if grand_score <= 4:
        return "investigate"
    if grand_score <= 6:
        return "change_soon"
    return "change_now"


# ---------------------------------------------------------------------------
# REBA lookup tables (Hignett & McAtamney 2000 "Rapid Entire Body
# Assessment (REBA)").
# ---------------------------------------------------------------------------

# Table A: index [trunk(1-5)][neck(1-3)][legs(1-4)] -> base score
# Spot-checked 2026-07-09 against the canonical Hignett & McAtamney (2000)
# REBA Table A / ergo-plus worksheet -- matched exactly, no corrections.
REBA_TABLE_A = {
    1: {1: (1, 2, 3, 4), 2: (1, 2, 3, 4), 3: (3, 3, 5, 6)},
    2: {1: (2, 3, 4, 5), 2: (3, 4, 5, 6), 3: (4, 5, 6, 7)},
    3: {1: (2, 4, 5, 6), 2: (4, 5, 6, 7), 3: (5, 6, 7, 8)},
    4: {1: (3, 5, 6, 7), 2: (5, 6, 7, 8), 3: (6, 7, 8, 9)},
    5: {1: (4, 6, 7, 8), 2: (6, 7, 8, 9), 3: (7, 8, 9, 9)},
}


def _reba_table_a(trunk, neck, legs):
    trunk = min(trunk, 5)
    neck = min(neck, 3)
    legs = min(legs, 4)
    return REBA_TABLE_A[trunk][neck][legs - 1]


# Table B: index [upper_arm(1-6)][lower_arm(1-2)][wrist(1-3)] -> base score
# Spot-checked 2026-07-09 against the canonical REBA Table B -- matched exactly.
REBA_TABLE_B = {
    1: {1: (1, 2, 2), 2: (1, 2, 3)},
    2: {1: (1, 2, 3), 2: (2, 3, 4)},
    3: {1: (3, 4, 5), 2: (4, 5, 5)},
    4: {1: (4, 5, 5), 2: (5, 6, 7)},
    5: {1: (6, 7, 8), 2: (7, 8, 8)},
    6: {1: (7, 8, 8), 2: (8, 9, 9)},
}


def _reba_table_b(upper_arm, lower_arm, wrist):
    upper_arm = min(upper_arm, 6)
    lower_arm = min(lower_arm, 2)
    wrist = min(wrist, 3)
    return REBA_TABLE_B[upper_arm][lower_arm][wrist - 1]


# Table C: index [score_a(1-12)][score_b(1-12)] -> score C (1-12)
# Spot-checked 2026-07-09 against the canonical REBA Table C 12x12 grid -- matched exactly.
REBA_TABLE_C = {
    1: (1, 1, 1, 2, 3, 3, 4, 5, 6, 7, 7, 7),
    2: (1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 7, 8),
    3: (2, 3, 3, 3, 4, 5, 6, 7, 7, 8, 8, 8),
    4: (3, 4, 4, 4, 5, 6, 7, 8, 8, 9, 9, 9),
    5: (4, 4, 4, 5, 6, 7, 8, 8, 9, 9, 9, 9),
    6: (6, 6, 6, 7, 8, 8, 9, 9, 10, 10, 10, 10),
    7: (7, 7, 7, 8, 9, 9, 9, 10, 10, 11, 11, 11),
    8: (8, 8, 8, 9, 10, 10, 10, 10, 10, 11, 11, 11),
    9: (9, 9, 9, 10, 10, 10, 11, 11, 11, 12, 12, 12),
    10: (10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 12, 12),
    11: (11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12, 12),
    12: (12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12),
}


def _reba_table_c(score_a, score_b):
    score_a = min(max(score_a, 1), 12)
    score_b = min(max(score_b, 1), 12)
    return REBA_TABLE_C[score_a][score_b - 1]


def _reba_risk_level(final_score):
    if final_score <= 1:
        return "negligible"
    if final_score <= 3:
        return "low"
    if final_score <= 7:
        return "medium"
    if final_score <= 10:
        return "high"
    return "very_high"


def _severity(band, max_band, measured=True):
    """Maps a single component's band (1..max_band) to a coarse severity
    the dashboard can color/sort by, without needing to know each body
    part's own band scale. "not_measured" is distinct from "ok" -- it means
    this component is fixed at its lowest-risk default because the camera
    can't observe it (see module docstring), not that it was measured and
    found fine."""
    if not measured:
        return "not_measured"
    if band <= 1:
        return "ok"
    if band >= max_band:
        return "high"
    return "moderate"


def _component(name, angle_deg, band, max_band, measured=True):
    return {
        "name": name,
        "angle_deg": angle_deg,
        "band": band,
        "max_band": max_band,
        "measured": measured,
        "severity": _severity(band, max_band, measured),
    }


# ---------------------------------------------------------------------------
# Top-level entry point.
# ---------------------------------------------------------------------------


def assess_posture(landmarks_dict):
    """Compute RULA and REBA posture-only risk scores from one person's
    side-view 2D landmarks.

    landmarks_dict: dict that may contain any of "ear", "shoulder", "hip",
        "knee", "ankle", "elbow", "wrist" -> (x, y) tuples (see
        pipeline/pose_estimator.landmarks_to_posture_dict, which now
        includes wrist/ankle alongside the original ear/shoulder/hip/
        knee/elbow). "shoulder" is required (everything else degrades to a
        lowest-risk default if missing, per this module's docstring); if
        "shoulder" itself is absent, returns an all-"unknown" result since
        neither upper-arm, neck, nor trunk angles have any usable anchor.

    Returns {
        "rula": {"score": int (1-7), "risk": str, "angles": {...}, "components": {...}},
        "reba": {"score": int (1-15), "risk": str, "angles": {...}, "components": {...}},
        "label": "unknown" if landmarks_dict has no "shoulder", else "scored",
    }

    "components" (added for the dashboard's per-body-part breakdown, so it
    can show *what* is driving a risky score rather than just the number):
    one entry per body part this method scores, name -> {"name", "angle_deg",
    "band", "max_band", "measured", "severity"}. "severity" is "not_measured"
    (fixed at lowest-risk default, camera can't observe this -- see module
    docstring), "ok" (band 1), "moderate" (between), or "high" (at this
    part's own max band). RULA and REBA have their own component dicts since
    their band scales differ per part (e.g. REBA's neck is 1-2, RULA's is
    1-3) even though several underlying angles are shared.

    Raw per-frame result -- smooth the "risk" strings with
    pipeline.debounce before displaying/logging live, same as the old
    per-check labels were debounced.
    """
    if "shoulder" not in landmarks_dict:
        return {
            "rula": {"score": None, "risk": "unknown", "angles": {}, "components": {}},
            "reba": {"score": None, "risk": "unknown", "angles": {}, "components": {}},
            "label": "unknown",
        }

    shoulder = landmarks_dict["shoulder"]
    ear = landmarks_dict.get("ear")
    hip = landmarks_dict.get("hip")
    elbow = landmarks_dict.get("elbow")
    wrist = landmarks_dict.get("wrist")
    knee = landmarks_dict.get("knee")
    ankle = landmarks_dict.get("ankle")

    angles = {}

    # Upper arm: angle of shoulder->elbow from hanging straight down.
    upper_arm_angle = None
    if elbow is not None:
        arm_vec = _subtract(elbow, shoulder)
        if _norm(arm_vec) > 0:
            upper_arm_angle = _angle_between_vectors(arm_vec, _DOWN_REF)
    upper_arm = _upper_arm_band(upper_arm_angle) if upper_arm_angle is not None else 1
    angles["upper_arm_deg"] = upper_arm_angle

    # Lower arm: elbow joint flexion (needs elbow + wrist).
    lower_arm_flexion = None
    if elbow is not None and wrist is not None:
        try:
            lower_arm_flexion = 180.0 - angle_between(shoulder, elbow, wrist)
        except ValueError:
            lower_arm_flexion = None
    lower_arm = _lower_arm_band(lower_arm_flexion) if lower_arm_flexion is not None else 1
    angles["lower_arm_deg"] = lower_arm_flexion

    # Neck: angle of shoulder->ear from vertical.
    neck_angle = None
    if ear is not None:
        neck_vec = _subtract(ear, shoulder)
        if _norm(neck_vec) > 0:
            neck_angle = angle_from_vertical(neck_vec)
    angles["neck_deg"] = neck_angle
    rula_neck = _rula_neck_band(neck_angle) if neck_angle is not None else 1
    reba_neck = _reba_neck_band(neck_angle) if neck_angle is not None else 1

    # Trunk: angle of hip->shoulder from vertical.
    trunk_angle = None
    if hip is not None:
        trunk_vec = _subtract(shoulder, hip)
        if _norm(trunk_vec) > 0:
            trunk_angle = angle_from_vertical(trunk_vec)
    angles["trunk_deg"] = trunk_angle
    trunk = _trunk_band(trunk_angle) if trunk_angle is not None else 1

    # Legs: knee flexion (needs hip + knee + ankle).
    knee_flexion = _knee_flexion_deg(hip, knee, ankle) if (hip is not None and knee is not None and ankle is not None) else None
    angles["knee_deg"] = knee_flexion
    rula_legs = _rula_legs_score(knee_flexion)
    reba_legs = _reba_legs_score(knee_flexion)

    # --- RULA ---
    score_a = _rula_table_a(upper_arm, lower_arm, _WRIST_SCORE, _WRIST_TWIST_SCORE)
    score_b = _rula_table_b(rula_neck, trunk, rula_legs)
    rula_grand_score = _rula_table_c(score_a, score_b)

    rula_components = {
        "upper_arm": _component("Upper arm", upper_arm_angle, upper_arm, 4),
        "lower_arm": _component("Lower arm (elbow)", lower_arm_flexion, lower_arm, 2),
        "wrist": _component("Wrist", None, _WRIST_SCORE, 4, measured=False),
        "wrist_twist": _component("Wrist twist", None, _WRIST_TWIST_SCORE, 2, measured=False),
        "neck": _component("Neck", neck_angle, rula_neck, 3),
        "trunk": _component("Trunk", trunk_angle, trunk, 4),
        "legs": _component("Legs", knee_flexion, rula_legs, 2),
    }

    # --- REBA ---
    reba_score_a = _reba_table_a(trunk, reba_neck, reba_legs)
    reba_score_b = _reba_table_b(upper_arm, lower_arm, _WRIST_SCORE)
    reba_score_c = _reba_table_c(reba_score_a, reba_score_b)

    reba_components = {
        "upper_arm": _component("Upper arm", upper_arm_angle, upper_arm, 4),
        "lower_arm": _component("Lower arm (elbow)", lower_arm_flexion, lower_arm, 2),
        "wrist": _component("Wrist", None, _WRIST_SCORE, 3, measured=False),
        "neck": _component("Neck", neck_angle, reba_neck, 2),
        "trunk": _component("Trunk", trunk_angle, trunk, 4),
        "legs": _component("Legs", knee_flexion, reba_legs, 3),
    }

    return {
        "rula": {"score": rula_grand_score, "risk": _rula_risk_level(rula_grand_score), "angles": angles, "components": rula_components},
        "reba": {"score": reba_score_c, "risk": _reba_risk_level(reba_score_c), "angles": angles, "components": reba_components},
        "label": "scored",
    }
