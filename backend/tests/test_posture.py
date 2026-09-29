from backend.app.cv.posture import assess_posture


RULA_COMPONENT_KEYS = (
    "upper_arm",
    "lower_arm",
    "wrist",
    "wrist_twist",
    "neck",
    "trunk",
    "legs",
)

REBA_COMPONENT_KEYS = (
    "upper_arm",
    "lower_arm",
    "wrist",
    "neck",
    "trunk",
    "legs",
)


def _neutral_landmarks():
    """Upright, side-view stance: ear/shoulder/hip/knee/ankle roughly
    vertically stacked, arm relaxed close to the trunk."""
    return {
        "ear": (100, 40),
        "shoulder": (100, 90),
        "elbow": (105, 150),
        "wrist": (108, 210),
        "hip": (100, 260),
        "knee": (100, 340),
        "ankle": (100, 420),
    }


def _bent_squatted_landmarks():
    """Bent trunk, raised upper arm, and heavily flexed knee (squat-like
    posture) to exercise a different part of the scoring tables."""
    return {
        "ear": (100, 60),
        "shoulder": (110, 100),
        "elbow": (150, 90),
        "wrist": (190, 80),
        "hip": (140, 220),
        "knee": (110, 260),
        "ankle": (140, 330),
    }


def test_assess_posture_runs_with_valid_synthetic_landmarks():
    result = assess_posture(_neutral_landmarks())

    assert result["label"] == "scored"


def test_rula_score_is_within_valid_range():
    result = assess_posture(_neutral_landmarks())

    rula_score = result["rula"]["score"]

    assert rula_score is not None
    assert isinstance(rula_score, int)
    assert 1 <= rula_score <= 7


def test_reba_score_is_within_valid_range():
    result = assess_posture(_neutral_landmarks())

    reba_score = result["reba"]["score"]

    assert reba_score is not None
    assert isinstance(reba_score, int)
    assert 1 <= reba_score <= 12


def test_rula_risk_is_returned():
    result = assess_posture(_neutral_landmarks())

    assert result["rula"]["risk"] in (
        "low",
        "moderate",
        "high",
        "very_high",
    )


def test_reba_risk_is_returned():
    result = assess_posture(_neutral_landmarks())

    assert result["reba"]["risk"] in (
        "negligible",
        "low",
        "medium",
        "high",
        "very_high",
    )


def test_rula_components_exist():
    result = assess_posture(_neutral_landmarks())

    rula_components = result["rula"]["components"]

    for key in RULA_COMPONENT_KEYS:
        assert key in rula_components


def test_reba_components_exist():
    result = assess_posture(_neutral_landmarks())

    reba_components = result["reba"]["components"]

    for key in REBA_COMPONENT_KEYS:
        assert key in reba_components


def test_angles_contains_knee_flexion_deg():
    result = assess_posture(_neutral_landmarks())

    assert "knee_flexion_deg" in result["rula"]["angles"]
    assert "knee_flexion_deg" in result["reba"]["angles"]


def test_knee_flexion_deg_is_numeric_for_valid_landmarks():
    result = assess_posture(_neutral_landmarks())

    knee_flexion_deg = result["rula"]["angles"]["knee_flexion_deg"]

    assert knee_flexion_deg is not None
    assert isinstance(knee_flexion_deg, (int, float))


def test_bent_squatted_posture_is_also_scored():
    """A different geometry (bent trunk, raised arm, flexed knee) should
    still produce a valid, fully-populated assessment."""
    result = assess_posture(_bent_squatted_landmarks())

    assert result["label"] == "scored"

    rula_score = result["rula"]["score"]
    reba_score = result["reba"]["score"]

    assert rula_score is not None
    assert 1 <= rula_score <= 7

    assert reba_score is not None
    assert 1 <= reba_score <= 12

    knee_flexion_deg = result["reba"]["angles"]["knee_flexion_deg"]
    assert knee_flexion_deg is not None


def test_neutral_and_bent_postures_produce_different_angles():
    """Sanity check that the two synthetic postures actually exercise
    different geometry, rather than accidentally being equivalent."""
    neutral = assess_posture(_neutral_landmarks())
    bent = assess_posture(_bent_squatted_landmarks())

    neutral_trunk = neutral["rula"]["angles"]["trunk_deg"]
    bent_trunk = bent["rula"]["angles"]["trunk_deg"]

    assert neutral_trunk != bent_trunk
