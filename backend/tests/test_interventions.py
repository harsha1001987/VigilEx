"""
Back/trunk posture metric, the intervention engine, and interventions through
the API, report and PDF.

Synthetic postures are scored by the real posture.assess_posture(), so every
angle, band and severity in them comes from the production scoring code.
The real-video tests use a genuine process_video() result; expected values
are read from that result, never hardcoded.
"""

import base64
import copy
import math
import re
import uuid
import zlib
from pathlib import Path

import pytest

from app.cv import batch_processor
from app.cv.pose_estimator import landmarks_to_posture_dict
from app.cv.posture import angle_from_vertical, assess_posture
from app.models.assessment import Assessment
from app.schemas.analysis import BackPosture, RegionMeasurement
from app.services import analysis_assessments as analyses
from app.services import interventions as engine
from app.services import reports
from conftest import make_user

VIDEO = Path(__file__).resolve().parent / "19832490-hd_1920_1080_25fps (1).mp4"
FORBIDDEN = ("injur", "diagnos", "pain", "medical", "health", "disease", "condition")


@pytest.fixture(scope="session")
def real_result() -> dict:
    if not VIDEO.exists():
        pytest.skip(f"Real test video missing: {VIDEO}")
    return batch_processor.process_video(video_path=str(VIDEO))


# ------------------------------------------------------------- synthetic poses

def _coco(points: dict[str, tuple[float, float]]) -> list[dict]:
    """COCO-17 landmarks with the left side visible (right side unreliable), as stored in keyframes."""
    index = {"nose": 0, "ear": 3, "shoulder": 5, "elbow": 7, "wrist": 9, "hip": 11, "knee": 13, "ankle": 15}
    out = [{"x": 0.0, "y": 0.0, "confidence": 0.05} for _ in range(17)]
    for name, (x, y) in points.items():
        out[index[name]] = {"x": float(x), "y": float(y), "confidence": 0.95}
    return out


def _pose(trunk_lean=0.0, facing=1, knee_bend=0.0, arm_raise=10.0, neck_bend=0.0):
    """A side-on worker. Angles in degrees from vertical; positive trunk lean is toward the facing side."""
    hip = (500.0, 500.0)
    torso, thigh, shin, upper, lower, head = 160.0, 120.0, 120.0, 90.0, 80.0, 45.0
    t = math.radians(trunk_lean)
    shoulder = (hip[0] + facing * torso * math.sin(t), hip[1] - torso * math.cos(t))
    n = math.radians(neck_bend)
    ear = (shoulder[0] + facing * head * math.sin(n), shoulder[1] - head * math.cos(n))
    nose = (ear[0] + facing * 25, ear[1] + 5)
    a = math.radians(arm_raise)
    elbow = (shoulder[0] + facing * upper * math.sin(a), shoulder[1] + upper * math.cos(a))
    wrist = (elbow[0] + facing * lower, elbow[1])
    k = math.radians(knee_bend)
    knee = (hip[0] + facing * thigh * math.sin(k), hip[1] + thigh * math.cos(k))
    ankle = (knee[0], knee[1] + shin)
    return _coco({"nose": nose, "ear": ear, "shoulder": shoulder, "elbow": elbow, "wrist": wrist,
                  "hip": hip, "knee": knee, "ankle": ankle})


def _frame(landmarks: list[dict], t=0.0, yaw=10.0) -> dict:
    scored = assess_posture(landmarks_to_posture_dict([(p["x"], p["y"], p["confidence"]) for p in landmarks]))
    return {"t": t, "track_id": 1, "camera_yaw_deg": yaw, "side_view_ok": True,
            "landmarks": landmarks, "rula": scored["rula"], "reba": scored["reba"]}


def _assessment(frames: list[dict]) -> Assessment:
    """An unsaved completed assessment holding [frames], with derived angles computed from them."""
    def values(key):
        return [f["rula"]["angles"][key] for f in frames if f["rula"]["angles"].get(key) is not None]

    derived = {}
    for name, key in (("trunk", "trunk_deg"), ("upper_arm", "upper_arm_deg"), ("knee", "knee_flexion_deg"), ("neck", "neck_deg")):
        v = values(key)
        derived[f"{name}_deg_avg"] = sum(v) / len(v) if v else None
        derived[f"{name}_deg_max"] = max(v) if v else None
    return Assessment(id=uuid.uuid4(), status="completed", keypoint_series={"keyframes": frames}, derived_angles=derived)


def _plan(frames: list[dict], overall="moderate"):
    a = _assessment(frames)
    back = engine.back_posture(frames, a.derived_angles)
    items = engine.build_interventions(analyses.region_measurements(a), overall, back)
    return back, items


# ================================================================ back / trunk metric

def test_back_metric_is_the_scored_trunk_angle_on_the_real_video(real_result):
    """The stored trunk_deg is exactly hip -> shoulder from vertical on the scored side."""
    for frame in real_result["keyframes"]:
        stored = frame["rula"]["angles"]["trunk_deg"]
        if stored is None:
            continue
        points = landmarks_to_posture_dict([(p["x"], p["y"], p["confidence"]) for p in frame["landmarks"]])
        assert round(angle_from_vertical(points["hip"], points["shoulder"]), 2) == stored
        assert frame["reba"]["angles"]["trunk_deg"] == stored  # One trunk angle for both methods.

    back = engine.back_posture(real_result["keyframes"], real_result["derived_angles"])
    assert back.average_deg == real_result["derived_angles"]["trunk_deg_avg"]
    assert back.peak_deg == real_result["derived_angles"]["trunk_deg_max"]
    assert back.frames_measured == sum(1 for f in real_result["keyframes"] if f["rula"]["angles"]["trunk_deg"] is not None)
    assert back.frames_inclined == sum(1 for f in real_result["keyframes"] if (f["rula"]["angles"]["trunk_deg"] or 0) > 10)
    yaws = [f["camera_yaw_deg"] for f in real_result["keyframes"] if f["rula"]["angles"]["trunk_deg"] is not None]
    assert back.side_on_fraction == round(sum(1 for y in yaws if y <= engine.PROFILE_YAW_DEG) / len(yaws), 3)


@pytest.mark.parametrize("facing", [1, -1])
def test_forward_lean_is_forward_whichever_way_the_worker_faces(facing):
    back, _ = _plan([_frame(_pose(trunk_lean=35, facing=facing), t=i) for i in range(4)])
    assert back.direction == "forward"
    assert back.frames_inclined == 4
    assert back.peak_deg == pytest.approx(35, abs=0.1)
    assert not back.view_limited


def test_backward_lean_is_backward():
    back, items = _plan([_frame(_pose(trunk_lean=-25), t=i) for i in range(4)])
    assert back.direction == "backward"
    item = next(i for i in items if i.area == "back")
    assert item.observation.startswith("Backward trunk lean")
    assert "leaning back" in item.recommendation


def test_upright_trunk_is_not_inclined():
    back, items = _plan([_frame(_pose(trunk_lean=3), t=i) for i in range(4)])
    assert back.frames_inclined == 0
    assert back.direction is None
    assert not any(i.area == "back" for i in items)


def test_oblique_view_is_flagged_as_view_limited():
    back, items = _plan([_frame(_pose(trunk_lean=15), t=i, yaw=70) for i in range(4)])
    assert back.view_limited
    assert "may be larger than measured" in next(i for i in items if i.area == "back").observation


# ================================================================ intervention rules

def test_normal_posture_gets_no_interventions():
    back, items = _plan([_frame(_pose(), t=i) for i in range(5)], overall="low")
    assert items == []
    summary = engine.action_summary("low", items, back)
    assert summary.startswith("Posture risk was low")
    assert "No body region" in summary


def test_upright_neck_is_not_flagged_despite_lowest_band_labels():
    """posture.py labels RULA neck 1/3 and REBA neck 1/2 'moderate'; the lowest band is neutral."""
    frames = [_frame(_pose(neck_bend=2), t=i) for i in range(5)]
    assert frames[0]["reba"]["components"]["neck"]["severity"] == "moderate"  # The raw label.
    neck = next(r for r in analyses.region_measurements(_assessment(frames)) if r.metric == "neck")
    assert neck.peak_severity == "ok"
    assert neck.frames_flagged == 0


def test_back_intervention_appears_and_leads():
    frames = [_frame(_pose(trunk_lean=30, knee_bend=100, arm_raise=60), t=i) for i in range(5)]
    _, items = _plan(frames)
    assert items[0].area == "back"
    assert items[0].title == "Protect the back"
    assert items[0].priority == "high"
    assert items[0].observation.startswith("Forward trunk positioning")
    assert "neutral position" in items[0].recommendation


def test_back_first_policy_puts_an_attention_back_ahead_of_elevated_regions():
    # Trunk 15 degrees: attention. Knees deeply bent: elevated.
    frames = [_frame(_pose(trunk_lean=15, knee_bend=100), t=i) for i in range(5)]
    _, items = _plan(frames)
    assert [i.area for i in items][:2] == ["back", "knees"]
    assert items[0].status == "attention" and items[0].priority == "high"
    assert items[1].status == "elevated"


def test_without_back_first_regions_are_ordered_by_status(monkeypatch):
    monkeypatch.setattr(engine, "BACK_FIRST", False)
    frames = [_frame(_pose(trunk_lean=15, knee_bend=100), t=i) for i in range(5)]
    _, items = _plan(frames)
    assert [i.area for i in items][:2] == ["knees", "back"]
    assert next(i for i in items if i.area == "back").priority == "medium"


def test_knee_intervention():
    _, items = _plan([_frame(_pose(knee_bend=100), t=i) for i in range(4)])
    knees = next(i for i in items if i.area == "knees")
    assert knees.title == "Reduce deep knee bending"
    assert knees.status == "elevated"
    assert "raise or reposition the work area" in knees.recommendation
    assert knees.evidence.peak_deg == pytest.approx(100, abs=0.1)


def test_neck_intervention():
    _, items = _plan([_frame(_pose(neck_bend=30), t=i) for i in range(4)])
    neck = next(i for i in items if i.area == "neck")
    assert neck.title == "Reduce neck bending"
    assert "eye level" in neck.recommendation


def test_upper_arm_intervention():
    _, items = _plan([_frame(_pose(arm_raise=70), t=i) for i in range(4)])
    arm = next(i for i in items if i.area == "upper_arm")
    assert arm.title == "Keep work within a comfortable reach"
    assert "comfortable reach" in arm.recommendation
    assert arm.evidence.frames_flagged == 4


def test_a_single_flagged_frame_is_not_enough():
    frames = [_frame(_pose(knee_bend=100), t=0)] + [_frame(_pose(), t=i) for i in range(1, 6)]
    _, items = _plan(frames)
    assert not any(i.area == "knees" for i in items)


def test_secondary_actions_are_ordered_by_status_then_frequency():
    regions = [
        RegionMeasurement(metric="neck", label="Neck", average_deg=15, maximum_deg=25, peak_severity="high",
                          frames_measured=10, frames_at_peak_severity=3, frames_flagged=3),
        RegionMeasurement(metric="upper_arm", label="Upper arm", average_deg=50, maximum_deg=60, peak_severity="high",
                          frames_measured=10, frames_at_peak_severity=9, frames_flagged=9),
        RegionMeasurement(metric="knee_flexion", label="Knee flexion", average_deg=40, maximum_deg=50,
                          peak_severity="moderate", frames_measured=10, frames_at_peak_severity=10, frames_flagged=10),
    ]
    items = engine.build_interventions(regions, "moderate", None)
    assert [i.area for i in items] == ["upper_arm", "neck", "knees"]
    assert [i.priority for i in items] == ["high", "high", "medium"]
    assert items[2].title == "Reduce knee bending"


def test_intervention_wording_never_makes_medical_claims(real_result):
    frames = [_frame(_pose(trunk_lean=lean, knee_bend=100, arm_raise=70, neck_bend=30), t=i)
              for i, lean in enumerate((-25, 30, 30, 30))]
    _, synthetic = _plan(frames, overall="high")
    record = _assessment(real_result["keyframes"])
    record.derived_angles = real_result["derived_angles"]
    _, real, summary = analyses.intervention_plan(record, None, None)
    texts = [summary or ""] + [t for i in synthetic + real for t in (i.title, i.observation, i.recommendation)]
    for text in texts:
        assert not any(word in text.lower() for word in FORBIDDEN), text


def test_action_summary_names_the_two_main_opportunities():
    frames = [_frame(_pose(trunk_lean=30, knee_bend=100), t=i) for i in range(4)]
    back, items = _plan(frames)
    summary = engine.action_summary("high", items, back)
    assert summary == (
        "Posture risk was high during the analyzed movement. The main opportunities are to "
        "reduce forward trunk bending and reduce deep knee flexion."
    )
    assert engine.action_summary(None, items, back) is None


# ================================================================ API / report / PDF

def _save(db, result, name="clip.mp4") -> Assessment:
    """Saves [result] as an assessment of the default test account (the one `client` is signed in as)."""
    record = analyses.start_analysis(db, file_name=name, content_type="video/mp4", size_bytes=1, owner=make_user(db))
    assert analyses.complete_analysis(db, record, copy.deepcopy(result)) is None
    db.refresh(record)
    return record


def _pdf_text(data: bytes) -> str:
    """Text drawn in a reportlab PDF: inflate each content stream, collect its string operands."""
    text = []
    for stream in re.findall(rb"stream\r?\n(.*?)endstream", data, re.S):
        try:
            # reportlab writes /Filter [/ASCII85Decode /FlateDecode].
            content = zlib.decompress(base64.a85decode(re.sub(rb"\s", b"", stream).removesuffix(b"~>")))
        except (ValueError, zlib.error):
            continue
        text += [m.decode("latin-1") for m in re.findall(rb"\((.*?)(?<!\\)\)\s*Tj", content)]
    return " ".join(text)


def test_api_detail_history_and_report_share_the_same_interventions(client, db, real_result):
    record = _save(db, real_result, name="trench.mp4")

    detail = client.get(f"/api/v1/assessments/{record.id}").json()
    interventions = detail["summary"]["interventions"]
    assert interventions, "the real video should produce interventions"
    assert detail["summary"]["action_summary"]

    history = client.get("/api/v1/assessments").json()["items"][0]
    assert history["interventions"] == interventions
    assert client.get("/api/v1/overview").json()["latest"][0]["interventions"] == interventions

    report = client.post(f"/api/v1/assessments/{record.id}/report").json()["content"]
    assert report["interventions"] == interventions
    assert report["action_summary"] == detail["summary"]["action_summary"]
    assert report["back_posture"]["peak_deg"] == real_result["derived_angles"]["trunk_deg_max"]


def test_real_video_leads_with_a_back_intervention(client, db, real_result):
    record = _save(db, real_result)
    interventions = client.get(f"/api/v1/assessments/{record.id}").json()["summary"]["interventions"]
    back = interventions[0]
    assert back["area"] == "back"
    assert back["title"] == "Protect the back"
    assert back["priority"] == "high"
    assert back["evidence"]["peak_deg"] == real_result["derived_angles"]["trunk_deg_max"]
    assert back["evidence"]["average_deg"] == real_result["derived_angles"]["trunk_deg_avg"]
    # Every other area is backed by its measured evidence.
    for item in interventions:
        assert item["evidence"]["frames_flagged"] >= engine.MIN_FLAGGED_FRAMES


def test_pdf_contains_the_intervention_text_in_action_order(client, db, real_result):
    record = _save(db, real_result)
    content = client.post(f"/api/v1/assessments/{record.id}/report").json()["content"]
    text = _pdf_text(client.get(f"/api/v1/assessments/{record.id}/report/pdf").content)

    primary = content["interventions"][0]
    assert primary["title"].upper() in text
    for item in content["interventions"][1:]:
        assert item["title"].upper() in text
    headings = ["OVERALL ASSESSMENT", "PRIORITY ACTION", "OTHER ACTIONS", "MEASUREMENTS",
                "POSTURE EVIDENCE", "CAMERA VIEW AND RELIABILITY", "TECHNICAL DETAILS AND ASSESSMENT LIMITATIONS"]
    positions = [text.index(h) for h in headings]
    assert positions == sorted(positions)
    assert "does not assess injury, pain, fatigue or health" in text
    assert "not medical advice" in text


def test_report_content_matches_the_stored_measurements(db, real_result):
    record = _save(db, real_result)
    content = reports.build_report_content(record)
    by_metric = {m.metric: m for m in content.measurements}
    angles = real_result["derived_angles"]
    for metric, key in (("trunk", "trunk_deg"), ("upper_arm", "upper_arm_deg"), ("knee_flexion", "knee_deg"), ("neck", "neck_deg")):
        assert by_metric[metric].average_deg == angles[f"{key}_avg"]
        assert by_metric[metric].maximum_deg == angles[f"{key}_max"]
    assert content.rula.score == real_result["rula"]["max_score"]
    assert content.reba.score == real_result["reba"]["max_score"]
    assert isinstance(content.back_posture, BackPosture)
