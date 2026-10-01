"""
Assessment persistence, history, detail, overview, reports and ownership.

Uses a genuine process_video() result from the real test video (computed once
per session). Where a test needs several assessments with different scores,
it copies that result and changes only the summary fields under test; the
expected aggregates are then computed from those inputs, never hardcoded.
"""

import copy
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from app.api.deps import get_current_user
from app.cv import batch_processor
from app.cv.posture import _reba_risk_level, _rula_risk_level
from app.main import app
from app.models.assessment import Assessment
from app.models.organization import Organization
from app.models.user import User
from app.services import analysis_assessments as analyses

VIDEO = Path(__file__).resolve().parent / "19832490-hd_1920_1080_25fps (1).mp4"
ORIGINAL_KEYS = {"methodology_version", "video", "worker", "quality", "derived_angles", "rula", "reba", "keyframes", "meta"}


@pytest.fixture(scope="session")
def real_result() -> dict:
    if not VIDEO.exists():
        pytest.skip(f"Real test video missing: {VIDEO}")
    return batch_processor.process_video(video_path=str(VIDEO))


def _save(db, result: dict, *, owner=None, created_at=None, name="clip.mp4") -> Assessment:
    record = analyses.start_analysis(db, file_name=name, content_type="video/mp4", size_bytes=1, owner=owner)
    failure = analyses.complete_analysis(db, record, copy.deepcopy(result))
    if created_at is not None:
        record.created_at = created_at
        db.commit()
    db.refresh(record)
    assert failure is None or record.status == "failed"
    return record


def _with_scores(result: dict, rula: int, reba: int) -> dict:
    r = copy.deepcopy(result)
    r["rula"]["max_score"] = rula
    r["reba"]["max_score"] = reba
    return r


# ====================================================== analysis -> persistence

def test_analyze_video_persists_and_returns_assessment_id(client, db, real_result):
    with open(VIDEO, "rb") as f:
        response = client.post("/api/v1/analyze-video", files={"file": (VIDEO.name, f, "video/mp4")})
    assert response.status_code == 200
    body = response.json()

    # Existing contract preserved; one field added.
    assert ORIGINAL_KEYS <= set(body)
    assert body["assessment_id"] is not None
    assert body["rula"]["max_score"] == real_result["rula"]["max_score"]
    assert body["reba"]["max_score"] == real_result["reba"]["max_score"]

    saved = db.get(Assessment, uuid.UUID(body["assessment_id"]))
    assert saved.status == "completed"
    assert saved.methodology_version == real_result["methodology_version"]
    assert analyses.video_name(saved) == VIDEO.name


def test_stored_assessment_returns_exactly_what_analysis_produced(client, db, real_result):
    record = _save(db, real_result)
    detail = client.get(f"/api/v1/assessments/{record.id}").json()
    analysis = detail["analysis"]

    for key in ("methodology_version", "video", "worker", "quality", "derived_angles", "keyframes"):
        assert analysis[key] == real_result[key], key
    for method in ("rula", "reba"):
        assert analysis[method]["max_score"] == real_result[method]["max_score"]
        assert analysis[method]["frames_scored"] == real_result[method]["frames_scored"]
        assert analysis[method]["risk_distribution"] == real_result[method]["risk_distribution"]

    summary = detail["summary"]
    assert summary["rula"]["risk"] == _rula_risk_level(real_result["rula"]["max_score"])
    assert summary["reba"]["risk"] == _reba_risk_level(real_result["reba"]["max_score"])
    # Existing AssessmentResponse fields are still present at the top level.
    assert detail["id"] == str(record.id)
    assert {s["method"] for s in detail["scores"]} == {"RULA", "REBA"}


def test_processing_failure_records_failed_assessment_without_scores(client, db, monkeypatch):
    def boom(**_):
        raise RuntimeError("decoder exploded at C:\\secret\\path")

    monkeypatch.setattr(batch_processor, "process_video", boom)
    response = client.post("/api/v1/analyze-video", files={"file": ("clip.mp4", b"bytes", "video/mp4")})

    assert response.status_code == 500
    assert "secret" not in response.text and "decoder" not in response.text
    rows = db.query(Assessment).all()
    assert [r.status for r in rows] == ["failed"]
    assert rows[0].scores == []


def test_invalid_pipeline_result_is_rejected_not_saved(client, db, real_result, monkeypatch):
    invalid = _with_scores(real_result, rula=9, reba=4)  # RULA tops out at 7.
    monkeypatch.setattr(batch_processor, "process_video", lambda **_: invalid)
    response = client.post("/api/v1/analyze-video", files={"file": ("clip.mp4", b"bytes", "video/mp4")})

    assert response.status_code == 500
    assert response.json()["detail"] == "The analysis produced an invalid result."
    row = db.query(Assessment).one()
    assert row.status == "failed" and row.scores == []


def test_video_without_scoreable_worker_is_not_a_completed_assessment(client, db, real_result, monkeypatch):
    empty = copy.deepcopy(real_result)
    empty["rula"] = {"max_score": None, "frames_scored": 0, "risk_distribution": {}}
    empty["reba"] = {"max_score": None, "frames_scored": 0, "risk_distribution": {}}
    monkeypatch.setattr(batch_processor, "process_video", lambda **_: empty)
    response = client.post("/api/v1/analyze-video", files={"file": ("clip.mp4", b"bytes", "video/mp4")})

    # The analysis is still returned (the app shows "no worker assessed"), but nothing is completed.
    assert response.status_code == 200
    assert response.json()["assessment_id"] is None
    assert db.query(Assessment).one().status == "failed"
    assert client.get("/api/v1/assessments").json()["total"] == 0


def test_stale_in_progress_analysis_is_expired(client, db):
    record = analyses.start_analysis(db, file_name="x.mp4", content_type=None, size_bytes=1, owner=None)
    record.created_at = datetime.now(timezone.utc) - analyses.PROCESSING_TIMEOUT - timedelta(minutes=1)
    db.commit()

    client.get("/api/v1/assessments")
    db.expire_all()
    assert db.get(Assessment, record.id).status == "failed"


# ============================================================== retrieval

def test_assessment_not_found(client):
    response = client.get(f"/api/v1/assessments/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.json() == {"detail": "Assessment not found"}


def test_malformed_assessment_id_is_422(client):
    assert client.get("/api/v1/assessments/not-a-uuid").status_code == 422


def test_delete_removes_assessment_and_report(client, db, real_result):
    record = _save(db, real_result)
    assert client.post(f"/api/v1/assessments/{record.id}/report").status_code == 201
    assert client.delete(f"/api/v1/assessments/{record.id}").status_code == 204
    assert client.get(f"/api/v1/assessments/{record.id}").status_code == 404
    assert client.get(f"/api/v1/assessments/{record.id}/report/pdf").status_code == 404


# ================================================================ history

def test_empty_history(client):
    assert client.get("/api/v1/assessments").json() == {"items": [], "total": 0, "limit": 20, "offset": 0}


def test_history_lists_completed_newest_first_with_pagination(client, db, real_result):
    now = datetime.now(timezone.utc)
    ids = [_save(db, real_result, created_at=now - timedelta(days=d), name=f"v{d}.mp4").id for d in (3, 1, 2)]
    failed = analyses.start_analysis(db, file_name="bad.mp4", content_type=None, size_bytes=1, owner=None)
    analyses.fail_analysis(db, failed, analyses.AnalyzeFailure(stage="analysis", message="x"))

    page = client.get("/api/v1/assessments?limit=2").json()
    assert page["total"] == 3
    assert [i["video_name"] for i in page["items"]] == ["v1.mp4", "v2.mp4"]
    assert client.get("/api/v1/assessments?limit=2&offset=2").json()["items"][0]["id"] == str(ids[0])

    item = page["items"][0]
    assert item["rula"]["score"] == real_result["rula"]["max_score"]
    assert item["reba"]["score"] == real_result["reba"]["max_score"]
    assert item["overall_risk"] in ("low", "moderate", "high", "very_high")
    assert item["duration_sec"] == real_result["video"]["duration_sec"]

    # Failed analyses are not history, but can be asked for explicitly.
    assert client.get("/api/v1/assessments?status=failed").json()["total"] == 1


# =============================================================== overview

def test_overview_empty_database(client):
    body = client.get("/api/v1/overview").json()
    assert body["total_assessments"] == 0
    assert body["recent_assessments"] == 0
    assert body["rula"] == {"assessments_scored": 0, "average": None, "highest": None, "scale_max": 7, "distribution": {}}
    assert body["reba"]["average"] is None and body["reba"]["distribution"] == {}
    assert body["overall_risk_distribution"] == {}
    assert body["elevated_measurements"] == []
    assert body["latest"] == []


def test_overview_aggregates_saved_assessments(client, db, real_result):
    scores = [(2, 3), (4, 5), (6, 9)]
    now = datetime.now(timezone.utc)
    for i, (rula, reba) in enumerate(scores):
        _save(db, _with_scores(real_result, rula, reba), created_at=now - timedelta(days=i))
    _save(db, _with_scores(real_result, 3, 4), created_at=now - timedelta(days=90))
    scores.append((3, 4))

    body = client.get("/api/v1/overview").json()
    rulas = [r for r, _ in scores]
    rebas = [b for _, b in scores]
    assert body["total_assessments"] == 4
    assert body["recent_assessments"] == 3
    assert body["rula"]["average"] == round(sum(rulas) / 4, 2)
    assert body["reba"]["average"] == round(sum(rebas) / 4, 2)
    assert body["rula"]["highest"] == max(rulas)
    assert body["reba"]["highest"] == max(rebas)

    expected_rula = {}
    for r in rulas:
        expected_rula[_rula_risk_level(r)] = expected_rula.get(_rula_risk_level(r), 0) + 1
    expected_reba = {}
    for b in rebas:
        expected_reba[_reba_risk_level(b)] = expected_reba.get(_reba_risk_level(b), 0) + 1
    assert body["rula"]["distribution"] == expected_rula
    assert body["reba"]["distribution"] == expected_reba
    assert sum(body["overall_risk_distribution"].values()) == 4
    assert [a["rula"]["score"] for a in body["latest"]] == [2, 4, 6, 3]
    assert {m["metric"] for m in body["elevated_measurements"]} == {"trunk", "upper_arm", "knee_flexion", "neck"}
    for m in body["elevated_measurements"]:
        assert 0 <= m["assessments_elevated"] <= m["assessments_measured"] <= 4


# ================================================================ reports

def test_report_uses_stored_values_and_pdf_downloads(client, db, real_result):
    record = _save(db, real_result, name="lifting.mp4")
    assert client.get(f"/api/v1/assessments/{record.id}/report").status_code == 404

    created = client.post(f"/api/v1/assessments/{record.id}/report")
    assert created.status_code == 201
    meta = created.json()
    content = meta["content"]

    assert content["rula"]["score"] == real_result["rula"]["max_score"]
    assert content["reba"]["score"] == real_result["reba"]["max_score"]
    assert content["rula"]["risk_distribution"] == real_result["rula"]["risk_distribution"]
    assert content["video_name"] == "lifting.mp4"
    by_metric = {m["metric"]: m for m in content["measurements"]}
    assert by_metric["knee_flexion"]["maximum_deg"] == real_result["derived_angles"]["knee_deg_max"]
    assert by_metric["trunk"]["average_deg"] == real_result["derived_angles"]["trunk_deg_avg"]
    assert 1 <= len(content["findings"]) <= 3
    assert any("does not assess injury" in text for text in content["limitations"])

    # No filesystem paths leave the server.
    assert meta["download_url"] == f"/api/v1/assessments/{record.id}/report/pdf"
    assert ":\\" not in created.text and "/tmp" not in created.text

    pdf = client.get(meta["download_url"])
    assert pdf.status_code == 200
    assert pdf.headers["content-type"] == "application/pdf"
    assert pdf.content.startswith(b"%PDF")
    assert meta["file_name"] in pdf.headers["content-disposition"]
    assert client.get(f"/api/v1/assessments/{record.id}").json()["report_available"] is True


def test_report_is_deterministic(client, db, real_result):
    record = _save(db, real_result)
    first = client.post(f"/api/v1/assessments/{record.id}/report")
    a = client.get(first.json()["download_url"]).content
    second = client.post(f"/api/v1/assessments/{record.id}/report")
    b = client.get(second.json()["download_url"]).content
    assert a == b
    assert first.json()["content"] == second.json()["content"]


def test_report_for_missing_or_incomplete_assessment(client, db):
    assert client.post(f"/api/v1/assessments/{uuid.uuid4()}/report").status_code == 404
    pending = analyses.start_analysis(db, file_name="x.mp4", content_type=None, size_bytes=1, owner=None)
    response = client.post(f"/api/v1/assessments/{pending.id}/report")
    assert response.status_code == 400
    assert response.json()["detail"] == "Reports can only be generated for completed assessments."


# ============================================================ ownership

def test_users_only_see_their_own_assessments(client, db, real_result):
    org = Organization(name="Org")
    db.add(org)
    db.commit()
    alice = User(organization_id=org.id, name="A", email="a@example.com")
    bob = User(organization_id=org.id, name="B", email="b@example.com")
    db.add_all([alice, bob])
    db.commit()

    mine = _save(db, real_result, owner=alice)
    theirs = _save(db, real_result, owner=bob)

    app.dependency_overrides[get_current_user] = lambda: alice
    try:
        assert client.get(f"/api/v1/assessments/{mine.id}").status_code == 200
        assert client.get(f"/api/v1/assessments/{theirs.id}").status_code == 404
        assert client.post(f"/api/v1/assessments/{theirs.id}/report").status_code == 404
        assert client.delete(f"/api/v1/assessments/{theirs.id}").status_code == 404
        listed = client.get("/api/v1/assessments").json()
        assert [i["id"] for i in listed["items"]] == [str(mine.id)]
        assert client.get("/api/v1/overview").json()["total_assessments"] == 1
    finally:
        app.dependency_overrides.pop(get_current_user, None)


# ============================================================ migrations

def test_migration_downgrades_and_upgrades_cleanly(alembic_run):
    alembic_run("downgrade", "b7c3e1f9a2d4")
    alembic_run("upgrade", "head")


def test_downgrade_refuses_to_invent_a_hierarchy(alembic_run, db, real_result):
    _save(db, real_result)  # No organization/site/area/task.
    db.close()
    with pytest.raises(RuntimeError, match="Cannot downgrade"):
        alembic_run("downgrade", "b7c3e1f9a2d4")
