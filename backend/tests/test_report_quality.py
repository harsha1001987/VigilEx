"""
Report quality: Extrive product suggestions (BackEX first), measurement
reliability, peak-score-frame component tables, and their consistency across
the API, report content and PDF.

Synthetic postures are scored by the real posture.assess_posture(); the
real-video tests read every expected value from the process_video() result.
"""

import json
import re
import uuid
from datetime import datetime, timezone

import pytest

from app.models.assessment import Assessment
from app.services import analysis_assessments as analyses
from app.services import interventions as engine
from app.services import products
from app.services import reports
from test_interventions import FORBIDDEN, _assessment, _frame, _pdf_text, _pose, _save, real_result  # noqa: F401

CLAIMS = ("guarantee", "prevent", "productivity", "proven", "reduces", "cure", "treat")


def _plan(frames, overall="moderate"):
    a = _assessment(frames)
    back = engine.back_posture(frames, a.derived_angles)
    items = engine.build_interventions(analyses.region_measurements(a), overall, back)
    return back, items, products.product_recommendations(items, back)


def _rendered(frames, tmp_path):
    """Report content and PDF text for an unsaved assessment."""
    a = _assessment(frames)
    a.created_at = a.updated_at = datetime(2026, 10, 9, tzinfo=timezone.utc)
    a.methodology_version = "test"
    a.scores = []
    content = reports.build_report_content(a)
    target = tmp_path / f"{a.id}.pdf"
    reports.render_pdf(a, content, target)
    return content, _pdf_text(target.read_bytes())


def _product_texts(rec) -> list[str]:
    return [rec.headline, rec.rationale, rec.intended_use or "", rec.operating_principle or "", rec.evidence_note or "",
            rec.qualification, *rec.task_types, *rec.suitability, *rec.complementary_actions]


# ================================================================ BackEX trigger

def test_back_finding_makes_backex_the_primary_product():
    back, items, recs = _plan([_frame(_pose(trunk_lean=30, knee_bend=100), t=i) for i in range(5)])
    assert items[0].area == "back"
    assert [r.product_id for r in recs] == ["backex"]
    rec = recs[0]
    assert rec.name == "BackEX" and rec.role == "primary" and rec.linked_area == "back"
    assert rec.headline == "Evaluate BackEX for this task"
    assert rec.content_status == "verified" and rec.content_gaps == []
    # Tailored to the measured finding, not generic.
    assert re.search(r"observed (consistent|repeated|brief) forward trunk bending", rec.rationale), rec.rationale
    assert f"in {items[0].evidence.frames_flagged} of {items[0].evidence.frames_measured} measured frames" in rec.rationale
    # Task changes come first; the product is evaluated, not prescribed.
    assert rec.complementary_actions[0].startswith("Adjust the workstation")
    assert "does not establish that this worker needs BackEX" in rec.qualification
    assert "qualified person" in rec.qualification


@pytest.mark.parametrize("pose", [dict(knee_bend=100), dict(neck_bend=30), dict(arm_raise=70), dict()])
def test_without_a_back_finding_backex_is_not_inserted(pose):
    _, items, recs = _plan([_frame(_pose(**pose), t=i) for i in range(5)])
    assert not any(i.area == "back" for i in items)
    assert recs == []


def test_backward_lean_does_not_qualify_for_a_forward_bending_support():
    back, items, recs = _plan([_frame(_pose(trunk_lean=-25), t=i) for i in range(4)])
    assert back.direction == "backward" and items[0].area == "back"
    assert recs == []


def test_priority_policy_is_unchanged_by_product_suggestions(monkeypatch):
    frames = [_frame(_pose(trunk_lean=15, knee_bend=100), t=i) for i in range(5)]
    _, items, recs = _plan(frames)
    assert [i.area for i in items][:2] == ["back", "knees"]
    assert recs[0].role == "primary"
    # Without the back-first policy the back is not the priority, so BackEX is not presented as primary.
    monkeypatch.setattr(engine, "BACK_FIRST", False)
    _, items, recs = _plan(frames)
    assert [i.area for i in items][:2] == ["knees", "back"]
    assert recs[0].role == "supporting"


# ================================================================ product content

def test_product_text_is_the_verified_source_content_without_benefit_claims():
    _, _, recs = _plan([_frame(_pose(trunk_lean=30), t=i) for i in range(5)])
    rec = recs[0]
    source = json.loads(products.CATALOGUE_PATH.read_text(encoding="utf-8"))["products"]["backex"]
    assert rec.intended_use == source["intended_use"]
    assert rec.operating_principle == source["operating_principle"]
    assert rec.task_types == source["task_types"]
    assert rec.suitability == source["suitability"]
    assert rec.sources == source["sources"] and rec.sources
    for text in _product_texts(rec):
        low = text.lower()
        assert not re.search(r"\d+\s*%", text), text
        assert not any(w in low for w in FORBIDDEN + CLAIMS), text
    assert "work efficiency in your operating environment" in rec.evidence_note


def test_missing_product_content_is_flagged_and_restrained():
    bare = {"backex": {"name": "BackEX", "trigger": {"area": "back"}}}
    back, items, _ = _plan([_frame(_pose(trunk_lean=30), t=i) for i in range(5)])
    rec = products.product_recommendations(items, back, bare)[0]
    assert rec.content_status == "incomplete"
    assert rec.content_gaps == ["intended use", "operating principle", "relevant task types", "fit and suitability guidance"]
    assert rec.intended_use is None and rec.operating_principle is None
    assert "pending from Extrive" in rec.evidence_note
    assert rec.suitability  # Still tells the reader to assess fit before use.


def test_report_still_generates_when_product_content_is_missing(tmp_path, monkeypatch):
    frames = [_frame(_pose(trunk_lean=30), t=i) for i in range(5)]
    # No content file at all: no product section, rest of the report intact.
    monkeypatch.setattr(products, "CATALOGUE_PATH", tmp_path / "absent.json")
    content, text = _rendered(frames, tmp_path)
    assert content.product_recommendations == []
    assert reports.PRODUCT_SECTION not in content.sections
    assert "Protect the back" in text and reports.PRODUCT_SECTION not in text
    # A product entry without approved text: shown as pending, never invented.
    partial = tmp_path / "partial.json"
    partial.write_text(json.dumps({"products": {"backex": {"name": "BackEX", "trigger": {"area": "back"}}}}))
    monkeypatch.setattr(products, "CATALOGUE_PATH", partial)
    content, text = _rendered(frames, tmp_path)
    assert content.product_recommendations[0].content_status == "incomplete"
    assert "Product information pending" in text
    assert "Why it may help" not in text


# ================================================================ reliability / not measured

def test_oblique_view_produces_a_limited_reliability_notice(tmp_path):
    frames = [_frame(_pose(trunk_lean=15), t=i, yaw=70) for i in range(4)]
    content, text = _rendered(frames, tmp_path)
    rel = content.measurement_reliability
    assert rel.status == "limited"
    assert rel.title == "Limited measurement reliability"
    assert rel.side_on_fraction == 0.0
    assert any("side-on in only 0%" in r for r in rel.reasons)
    assert "record a new video" in rel.recommendation
    assert "Limited measurement reliability" in text
    # Repeated in the technical limitations.
    assert any(l.startswith("Limited measurement reliability:") for l in content.limitations)


def test_side_on_view_reports_no_limitation_without_claiming_confidence(tmp_path):
    content, text = _rendered([_frame(_pose(trunk_lean=30), t=i) for i in range(4)], tmp_path)
    rel = content.measurement_reliability
    assert rel.status == "no_limitation_detected" and rel.title is None and rel.reasons == []
    assert "reliable" not in (rel.title or "")
    assert "This is not a confidence rating" in text
    assert "Limited measurement reliability" not in text


def test_unobservable_region_is_never_reported_as_low_risk(tmp_path):
    frames = []
    for i in range(4):
        landmarks = _pose(trunk_lean=30)
        for index in (13, 15):  # Knee and ankle hidden.
            landmarks[index]["confidence"] = 0.05
        frames.append(_frame(landmarks, t=i))
    content, text = _rendered(frames, tmp_path)
    knee = next(m for m in content.measurements if m.metric == "knee_flexion")
    assert knee.peak_severity is None
    assert reports._status(knee, content.overall_risk) == "not_measured"
    assert "not evidence that the posture of that region was low risk" in content.measurement_note
    assert content.measurement_reliability.status == "limited"
    assert any("knee flexion" in r and "not evidence" in r for r in content.measurement_reliability.reasons)
    assert "Not measured" in text


# ================================================================ peak vs peak-score frame

def test_overall_peak_and_peak_score_frame_components_are_distinguished(db, real_result):
    record = _save(db, real_result)
    content = reports.build_report_content(record)
    assert content.component_note.startswith("The overall peak shown in Measurements is the highest observed value")
    assert [t.method for t in content.component_tables] == ["RULA", "REBA"]
    keyframes = real_result["keyframes"]
    trunk_peak = next(m for m in content.measurements if m.metric == "trunk").maximum_deg
    assert trunk_peak == real_result["derived_angles"]["trunk_deg_max"]  # Overall peak is untouched.
    for table in content.component_tables:
        assert table.title == f"{table.method} — component measurements at the peak-score frame"
        frame = keyframes[table.frame_number - 1]
        method = table.method.lower()
        assert frame[method]["score"] == table.score == real_result[method]["max_score"]
        # The first frame that reached the peak score.
        assert all(f[method]["score"] != table.score for f in keyframes[:table.frame_number - 1])
        trunk = next(r for r in table.rows if r.name.lower().startswith("trunk"))
        assert trunk.angle_deg == frame[method]["components"]["trunk"]["angle_deg"]
        assert trunk.angle_deg <= trunk_peak


# ================================================================ end to end

def test_api_report_and_pdf_carry_the_same_recommendation_and_explanations(client, db, real_result):
    record = _save(db, real_result, name="trench.mp4")
    detail = client.get(f"/api/v1/assessments/{record.id}").json()["summary"]
    history = client.get("/api/v1/assessments").json()["items"][0]
    latest = client.get("/api/v1/overview").json()["latest"][0]
    report = client.post(f"/api/v1/assessments/{record.id}/report").json()["content"]
    text = _pdf_text(client.get(f"/api/v1/assessments/{record.id}/report/pdf").content)

    for other in (history, latest, report):
        assert other["product_recommendations"] == detail["product_recommendations"]
        assert other["measurement_reliability"] == detail["measurement_reliability"]

    # The real clip leads with a back finding, so BackEX follows the priority action.
    assert detail["interventions"][0]["area"] == "back"
    rec = detail["product_recommendations"][0]
    assert rec["name"] == "BackEX" and rec["role"] == "primary"

    order = report["sections"]
    assert order[:4] == ["Overall assessment", "Priority action", "Recommended product to evaluate", "Other actions"]
    assert order[-1] == "Technical details and assessment limitations"
    positions = [text.index(h) for h in order]
    assert positions == sorted(positions)
    assert "EVALUATE BACKEX FOR THIS TASK" in text.upper()
    assert "Why it may help" in text
    assert "Best results come from combining approaches" in text
    assert "Component measurements at the peak-score frame" in text
    # Implementation detail lives only in the technical appendix.
    appendix = text.index("Technical details and assessment limitations")
    assert text.index("YOLO11n-Pose") > appendix and text.index("ByteTrack") > appendix
    if report["measurement_reliability"]["status"] == "limited":
        assert "Limited measurement reliability" in text


# ================================================================ PDF layout

def test_pdf_numbers_every_page_and_puts_key_findings_before_the_priority_action(tmp_path):
    content, text = _rendered([_frame(_pose(trunk_lean=30, knee_bend=100), t=i) for i in range(5)], tmp_path)
    pages = int(re.search(r"Page 1 of (\d+)", text).group(1))
    assert pages >= 2
    for n in range(1, pages + 1):
        assert f"Page {n} of {pages}" in text
    assert content.findings
    findings = text.index("Key findings")
    for finding in content.findings:
        assert finding.title in text
        assert findings < text.index(finding.title) < text.index("Priority action")


def test_pdf_wraps_a_long_name_and_leaves_out_missing_metadata(tmp_path):
    frames = [_frame(_pose(trunk_lean=30), t=i) for i in range(4)]
    a = _assessment(frames)
    a.created_at = a.updated_at = datetime(2026, 10, 9, tzinfo=timezone.utc)
    a.methodology_version = "test"
    a.scores = []
    long_name = "Warehouse_line_3_pallet_restacking_side_view_" * 4 + ".mp4"
    a.capture_metadata = {"upload": {"original_filename": long_name}, "video": {}}
    content = reports.build_report_content(a)
    assert content.video_name == long_name and content.duration_sec is None
    target = tmp_path / "long.pdf"
    reports.render_pdf(a, content, target)
    text = _pdf_text(target.read_bytes())
    # The whole name is printed (wrapped over lines, not clipped); no duration is invented.
    assert long_name.replace(" ", "") in text.replace(" ", "")
    assert "Video duration" not in text


def test_pdf_summary_page_names_every_recommended_action_in_priority_order(tmp_path):
    content, text = _rendered([_frame(_pose(trunk_lean=30, knee_bend=100, neck_bend=40), t=i) for i in range(5)], tmp_path)
    assert content.interventions
    summary = text.index("Recommended actions")
    assert text.index("Key findings") < summary < text.index("Priority action")
    # First mention of each action is on the summary list, in the backend's order.
    positions = [text.index(item.title) for item in content.interventions]
    assert positions == sorted(positions) and positions[0] > summary
    assert positions[-1] < text.index("Priority action")


def test_pdf_states_a_camera_view_limitation_once_in_full(tmp_path):
    content, text = _rendered([_frame(_pose(trunk_lean=15), t=i, yaw=70) for i in range(4)], tmp_path)
    rel = content.measurement_reliability
    assert rel.status == "limited"
    # The notice on the summary page carries the reasons; later sections refer back to it.
    for reason in rel.reasons:
        assert text.count(reason[:40]) == 1
    assert "given under Overall assessment" in text


def test_pdf_has_no_blank_trailing_page(tmp_path):
    a = _assessment([_frame(_pose(trunk_lean=30, knee_bend=100, neck_bend=40), t=i) for i in range(5)])
    a.created_at = a.updated_at = datetime(2026, 10, 9, tzinfo=timezone.utc)
    a.methodology_version = "test"
    a.scores = []
    target = tmp_path / "pages.pdf"
    reports.render_pdf(a, reports.build_report_content(a), target)
    data = target.read_bytes()
    pages = int(re.search(rb"/Count (\d+)", data).group(1))
    text = _pdf_text(data)
    # The last thing printed (the methodology version) is on the last page.
    assert f"Page {pages} of {pages}" in text
    assert text.index("Methodology version") > text.index(f"Page {pages - 1} of {pages}")
