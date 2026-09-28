"""
Tests for CV assessment persistence layer and database integration.
"""

import uuid
from decimal import Decimal
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.dialects.sqlite import base as sqlite_base

from app.db.base import Base
from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.models.assessment import Assessment
from app.models.assessment_score import AssessmentScore
from app.services.assessment_persistence import persist_cv_assessment_results

# Allow SQLite to compile PostgreSQL JSONB columns during unit tests
sqlite_base.SQLiteTypeCompiler.visit_JSONB = lambda self, type_, **kw: "JSON"


@pytest.fixture
def db_session():
    """Create an in-memory SQLite database for fast unit testing."""
    engine = create_engine("sqlite:///:memory:", echo=False)
    Base.metadata.create_all(engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = TestingSessionLocal()

    # Seed required foreign key hierarchy
    org = Organization(id=uuid.uuid4(), name="Test Org")
    site = Site(id=uuid.uuid4(), organization_id=org.id, name="Test Site")
    area = Area(id=uuid.uuid4(), site_id=site.id, name="Test Area")
    task = Task(id=uuid.uuid4(), area_id=area.id, name="Test Task")

    session.add_all([org, site, area, task])
    session.commit()

    session.test_hierarchy = {
        "org_id": org.id,
        "site_id": site.id,
        "area_id": area.id,
        "task_id": task.id,
    }

    yield session
    session.close()


def test_persist_cv_assessment_results(db_session):
    """Verify persisting CV assessment results writes accurate RULA/REBA scores and coverage."""
    hierarchy = db_session.test_hierarchy

    # Create parent Assessment
    assessment = Assessment(
        id=uuid.uuid4(),
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    # Simulated CV batch processor result
    cv_result = {
        "methodology_version": "vigilex-rula-reba-v1",
        "video": {
            "duration_sec": 16.0,
            "fps": 25.0,
            "frames_total": 400,
            "frames_sampled": 80,
        },
        "quality": {"detection_fraction": 1.0, "camera_view_ok": True},
        "derived_angles": {
            "trunk_deg_avg": 15.0,
            "upper_arm_deg_avg": 32.5,
            "knee_deg_avg": 10.0,
            "neck_deg_avg": 12.0,
        },
        "rula": {"max_score": 3, "frames_scored": 65, "risk_distribution": {"moderate": 65}},
        "reba": {"max_score": 4, "frames_scored": 65, "risk_distribution": {"medium": 65}},
        "worker": {"track_id": 1, "detected_frames": 80},
    }

    primary_assessment = {
        "track_id": 1,
        "frames_observed": 80,
        "rula": {
            "score": 3,
            "risk": "moderate",
            "valid_frames": 65,
            "coverage": 0.8125,
        },
        "reba": {
            "score": 4,
            "risk": "medium",
            "valid_frames": 65,
            "coverage": 0.8125,
        },
        "angles": {
            "upper_arm_deg": 32.5,
            "lower_arm_flexion_deg": 85.0,
            "neck_deg": 12.0,
            "trunk_deg": 15.0,
            "knee_flexion_deg": 10.0,
        },
        "status": "available",
    }

    persisted = persist_cv_assessment_results(
        db_session,
        assessment,
        cv_result,
        primary_assessment,
    )

    assert persisted.status == "completed"
    assert len(persisted.scores) == 2

    # Verify RULA Score
    rula_score = next(s for s in persisted.scores if s.method == "RULA")
    assert rula_score.score == Decimal("3")
    assert rula_score.risk_band == "moderate"
    assert rula_score.inputs["primary_track_id"] == 1
    assert rula_score.inputs["frames_observed"] == 80
    assert rula_score.inputs["valid_frames"] == 65
    assert rula_score.inputs["coverage"] == 0.8125
    assert rula_score.inputs["coverage_pct"] == "81.25%"

    # Verify REBA Score
    reba_score = next(s for s in persisted.scores if s.method == "REBA")
    assert reba_score.score == Decimal("4")
    assert reba_score.risk_band == "medium"
    assert reba_score.inputs["primary_track_id"] == 1
    assert reba_score.inputs["frames_observed"] == 80
    assert reba_score.inputs["valid_frames"] == 65
    assert reba_score.inputs["coverage"] == 0.8125
    assert reba_score.inputs["coverage_pct"] == "81.25%"


def test_missing_scores_not_padded(db_session):
    """Verify that when scores are missing, fake scores are NOT stored."""
    hierarchy = db_session.test_hierarchy

    assessment = Assessment(
        id=uuid.uuid4(),
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    cv_result = {
        "methodology_version": "vigilex-rula-reba-v1",
        "video": {"frames_sampled": 50},
        "rula": {"max_score": None, "frames_scored": 0},
        "reba": {"max_score": None, "frames_scored": 0},
    }

    primary_assessment = {
        "track_id": 2,
        "frames_observed": 50,
        "rula": {"score": None, "risk": "unknown", "valid_frames": 0, "coverage": 0.0},
        "reba": {"score": None, "risk": "unknown", "valid_frames": 0, "coverage": 0.0},
        "status": "insufficient_data",
    }

    persisted = persist_cv_assessment_results(
        db_session,
        assessment,
        cv_result,
        primary_assessment,
    )

    # No fake score rows should be added when score is None
    assert len(persisted.scores) == 0
    assert persisted.keypoint_series["primary_track"]["rula"]["score"] is None


def test_idempotent_retry_assessment_update(db_session):
    """Verify re-persisting an assessment updates existing scores without duplicates."""
    hierarchy = db_session.test_hierarchy

    assessment = Assessment(
        id=uuid.uuid4(),
        organization_id=hierarchy["org_id"],
        site_id=hierarchy["site_id"],
        area_id=hierarchy["area_id"],
        task_id=hierarchy["task_id"],
        status="draft",
        consent_given=False,
    )
    db_session.add(assessment)
    db_session.commit()

    cv_result1 = {
        "methodology_version": "vigilex-rula-reba-v1",
        "video": {"frames_sampled": 100},
        "rula": {"max_score": 2, "frames_scored": 80},
        "reba": {"max_score": 3, "frames_scored": 80},
    }
    primary_assessment1 = {
        "track_id": 1,
        "frames_observed": 100,
        "rula": {"score": 2, "risk": "low", "valid_frames": 80, "coverage": 0.8},
        "reba": {"score": 3, "risk": "low", "valid_frames": 80, "coverage": 0.8},
    }

    persist_cv_assessment_results(db_session, assessment, cv_result1, primary_assessment1)
    assert len(assessment.scores) == 2

    # Retry/Update with new scores
    cv_result2 = {
        "methodology_version": "vigilex-rula-reba-v1",
        "video": {"frames_sampled": 100},
        "rula": {"max_score": 4, "frames_scored": 90},
        "reba": {"max_score": 5, "frames_scored": 90},
    }
    primary_assessment2 = {
        "track_id": 1,
        "frames_observed": 100,
        "rula": {"score": 4, "risk": "moderate", "valid_frames": 90, "coverage": 0.9},
        "reba": {"score": 5, "risk": "medium", "valid_frames": 90, "coverage": 0.9},
    }

    updated = persist_cv_assessment_results(db_session, assessment, cv_result2, primary_assessment2)

    # Score records updated in-place; count remains 2
    assert len(updated.scores) == 2
    rula_score = next(s for s in updated.scores if s.method == "RULA")
    assert rula_score.score == Decimal("4")
    assert rula_score.inputs["coverage"] == 0.9
