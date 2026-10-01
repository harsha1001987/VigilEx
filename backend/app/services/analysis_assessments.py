"""
Video-analysis assessments: lifecycle, retrieval and aggregation.

Lifecycle of an analyze-video request:

    in_progress (row created when the upload is received)
      -> completed   the CV result validated and was persisted
      -> failed      processing raised, the result was invalid, or no worker
                     could be scored; no scores are stored

Every score and risk label returned from here is read back from what
persist_cv_assessment_results() stored from process_video(); nothing is
recomputed. `rebuild_analysis()` returns the stored result in exactly the
shape POST /analyze-video returns, so the app reads one contract.
"""

from __future__ import annotations

import logging
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone
from typing import Any

from pydantic import ValidationError
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.models.assessment import Assessment
from app.models.assessment_score import AssessmentScore
from app.models.user import User
from app.schemas.assessment import public_metadata
from app.schemas.analysis import (
    REBA_MAX,
    RULA_MAX,
    AnalyzeFailure,
    AssessmentSummary,
    CVAnalysisResult,
    ElevatedMeasurement,
    MethodResult,
    OverviewMethodStats,
    OverviewResponse,
    RegionMeasurement,
)
from app.services.assessment_persistence import persist_cv_assessment_results

log = logging.getLogger("vigilex.assessments")

# An analysis still in_progress after this long belongs to a request that died.
PROCESSING_TIMEOUT = timedelta(hours=1)
RECENT_WINDOW_DAYS = 30
OVERVIEW_LATEST = 5

METHODS = {"RULA": RULA_MAX, "REBA": REBA_MAX}

# Backend risk labels on one shared scale (the same mapping the app uses).
_LEVEL = {
    "RULA": {"low": 0, "moderate": 1, "high": 2, "very_high": 3},
    "REBA": {"negligible": 0, "low": 0, "medium": 1, "high": 2, "very_high": 3},
}
_LEVEL_NAMES = ("low", "moderate", "high", "very_high")

# Body regions: derived-angle keys and the RULA/REBA component scored from each.
REGIONS = (
    ("trunk", "Trunk", "trunk_deg", "trunk"),
    ("upper_arm", "Upper arm", "upper_arm_deg", "upper_arm"),
    ("knee_flexion", "Knee flexion", "knee_deg", "legs"),
    ("neck", "Neck", "neck_deg", "neck"),
)
_SEVERITY_RANK = {"ok": 0, "moderate": 1, "high": 2}



# ------------------------------------------------------------------ lifecycle

def start_analysis(
    db: Session,
    *,
    file_name: str,
    content_type: str | None,
    size_bytes: int,
    owner: User | None,
) -> Assessment:
    assessment = Assessment(
        status="in_progress",
        assessor_id=owner.id if owner else None,
        organization_id=owner.organization_id if owner else None,
        methodology_version="pending",
        consent_given=False,
        capture_metadata={
            "upload": {
                "original_filename": file_name,
                "content_type": content_type,
                "file_size_bytes": size_bytes,
                "uploaded_at": datetime.now(timezone.utc).isoformat(),
            }
        },
    )
    db.add(assessment)
    db.commit()
    db.refresh(assessment)
    return assessment


def complete_analysis(db: Session, assessment: Assessment, cv_result: dict[str, Any]) -> AnalyzeFailure | None:
    """Validates and persists a CV result. Returns the failure instead when it cannot be completed."""
    try:
        validated = CVAnalysisResult.model_validate(cv_result)
    except ValidationError as e:
        log.error("CV result for %s failed validation: %s", assessment.id, e)
        failure = AnalyzeFailure(stage="validation", message="The analysis produced an invalid result.")
        fail_analysis(db, assessment, failure)
        return failure

    if not validated.is_scoreable:
        failure = AnalyzeFailure(stage="no_worker", message="No worker could be scored in any sampled frame.")
        fail_analysis(db, assessment, failure)
        return failure

    upload = (assessment.capture_metadata or {}).get("upload", {})
    stored = dict(cv_result)
    stored["meta"] = {**(cv_result.get("meta") or {}), "upload": upload}
    persist_cv_assessment_results(db, assessment, cv_result=stored, primary_assessment=None)
    return None


def fail_analysis(db: Session, assessment: Assessment, failure: AnalyzeFailure) -> None:
    db.rollback()
    assessment.status = "failed"
    assessment.capture_metadata = {
        **(assessment.capture_metadata or {}),
        "failure": failure.model_dump(),
    }
    db.commit()


def expire_stale_analyses(db: Session, now: datetime | None = None) -> int:
    """Marks analyses left in_progress by a request that never finished as failed."""
    cutoff = (now or datetime.now(timezone.utc)) - PROCESSING_TIMEOUT
    stale = db.scalars(
        select(Assessment).where(Assessment.status == "in_progress", Assessment.created_at < cutoff)
    ).all()
    for assessment in stale:
        assessment.status = "failed"
        assessment.capture_metadata = {
            **(assessment.capture_metadata or {}),
            "failure": {"stage": "analysis", "message": "The analysis did not finish."},
        }
    if stale:
        db.commit()
    return len(stale)


# ------------------------------------------------------------------ ownership

def owned(owner: User | None):
    """Base query for assessments visible to `owner`. Without authentication, all of them."""
    query = select(Assessment)
    if owner is not None:
        query = query.where(Assessment.assessor_id == owner.id)
    return query


def get_owned(db: Session, assessment_id: uuid.UUID, owner: User | None) -> Assessment | None:
    return db.scalars(
        owned(owner).where(Assessment.id == assessment_id).options(selectinload(Assessment.scores))
    ).first()


# ------------------------------------------------------------------ reading

def _score_row(assessment: Assessment, method: str) -> AssessmentScore | None:
    rows = [s for s in assessment.scores if s.method == method]
    # Latest methodology version wins if an assessment was ever re-scored.
    return max(rows, key=lambda s: s.created_at or datetime.min.replace(tzinfo=timezone.utc)) if rows else None


def method_result(assessment: Assessment, method: str) -> MethodResult | None:
    row = _score_row(assessment, method)
    if row is None:
        return None
    return MethodResult(
        score=int(row.score),
        scale_max=METHODS[method],
        risk=row.risk_band,
        frames_scored=int((row.inputs or {}).get("valid_frames") or 0),
        risk_distribution=dict((row.details or {}).get("risk_distribution") or {}),
    )


def overall_risk(rula: MethodResult | None, reba: MethodResult | None) -> str | None:
    levels = [
        _LEVEL[m][r.risk]
        for m, r in (("RULA", rula), ("REBA", reba))
        if r is not None and r.risk in _LEVEL[m]
    ]
    return _LEVEL_NAMES[max(levels)] if levels else None


def video_name(assessment: Assessment) -> str | None:
    cm = assessment.capture_metadata or {}
    upload = cm.get("upload") or (cm.get("meta") or {}).get("upload") or {}
    return upload.get("original_filename")


def summarize(assessment: Assessment) -> AssessmentSummary:
    rula = method_result(assessment, "RULA")
    reba = method_result(assessment, "REBA")
    video = (assessment.capture_metadata or {}).get("video") or {}
    return AssessmentSummary(
        id=assessment.id,
        status=assessment.status,
        created_at=assessment.created_at,
        updated_at=assessment.updated_at,
        video_name=video_name(assessment),
        duration_sec=video.get("duration_sec"),
        rula=rula,
        reba=reba,
        overall_risk=overall_risk(rula, reba),
        methodology_version=assessment.methodology_version,
    )


def public_capture_metadata(metadata: dict[str, Any] | None) -> dict[str, Any] | None:
    """Capture metadata with server storage details removed."""
    return None if metadata is None else public_metadata(metadata)


def rebuild_analysis(assessment: Assessment) -> dict[str, Any] | None:
    """The stored result in the exact shape POST /analyze-video returned. None unless completed."""
    if assessment.status != "completed":
        return None
    cm = public_capture_metadata(assessment.capture_metadata) or {}
    ks = assessment.keypoint_series or {}

    def summary(method: str) -> dict[str, Any]:
        r = method_result(assessment, method)
        if r is None:
            return {"max_score": None, "frames_scored": 0, "risk_distribution": {}}
        return {"max_score": r.score, "frames_scored": r.frames_scored, "risk_distribution": r.risk_distribution}

    return {
        "methodology_version": assessment.methodology_version,
        "video": cm.get("video"),
        "worker": ks.get("worker"),
        "quality": cm.get("quality"),
        "derived_angles": assessment.derived_angles,
        "rula": summary("RULA"),
        "reba": summary("REBA"),
        "keyframes": ks.get("keyframes", []),
        "meta": cm.get("meta"),
        "assessment_id": str(assessment.id),
    }


def region_measurements(assessment: Assessment) -> list[RegionMeasurement]:
    angles = assessment.derived_angles or {}
    keyframes = (assessment.keypoint_series or {}).get("keyframes", [])
    out = []
    for metric, label, angle_key, component in REGIONS:
        severities = []
        for frame in keyframes:
            found = [
                (frame.get(m) or {}).get("components", {}).get(component)
                for m in ("rula", "reba")
            ]
            ranks = [
                _SEVERITY_RANK[c["severity"]]
                for c in found
                if c and c.get("measured") and c.get("severity") in _SEVERITY_RANK
            ]
            if ranks:
                severities.append(max(ranks))
        peak = max(severities) if severities else None
        out.append(
            RegionMeasurement(
                metric=metric,
                label=label,
                average_deg=angles.get(f"{angle_key}_avg"),
                maximum_deg=angles.get(f"{angle_key}_max"),
                peak_severity=(("ok", "moderate", "high")[peak] if peak is not None else None),
                frames_measured=len(severities),
                frames_at_peak_severity=(sum(1 for s in severities if s == peak) if peak else 0),
            )
        )
    return out


# ------------------------------------------------------------------ overview

def overview(db: Session, owner: User | None, now: datetime | None = None) -> OverviewResponse:
    now = now or datetime.now(timezone.utc)
    completed = db.scalars(
        owned(owner)
        .where(Assessment.status == "completed")
        .order_by(Assessment.created_at.desc())
        .options(selectinload(Assessment.scores))
    ).all()

    summaries = [summarize(a) for a in completed]
    since = now - timedelta(days=RECENT_WINDOW_DAYS)

    def method_stats(method: str) -> OverviewMethodStats:
        results = [s.rula if method == "RULA" else s.reba for s in summaries]
        scored = [r for r in results if r is not None and r.score is not None]
        counts = Counter(r.risk for r in scored if r.risk)
        return OverviewMethodStats(
            assessments_scored=len(scored),
            average=round(sum(r.score for r in scored) / len(scored), 2) if scored else None,
            highest=max((r.score for r in scored), default=None),
            scale_max=METHODS[method],
            distribution=dict(counts),
        )

    elevated: list[ElevatedMeasurement] = []
    per_region = [region_measurements(a) for a in completed]
    for index, (metric, label, _, _) in enumerate(REGIONS):
        column = [regions[index] for regions in per_region]
        elevated.append(
            ElevatedMeasurement(
                metric=metric,
                label=label,
                assessments_measured=sum(1 for r in column if r.peak_severity is not None),
                assessments_elevated=sum(1 for r in column if r.peak_severity == "high"),
            )
        )
    elevated.sort(key=lambda e: (-e.assessments_elevated, e.metric))

    return OverviewResponse(
        total_assessments=len(summaries),
        recent_window_days=RECENT_WINDOW_DAYS,
        recent_assessments=sum(1 for s in summaries if s.created_at >= since),
        overall_risk_distribution=dict(Counter(s.overall_risk for s in summaries if s.overall_risk)),
        rula=method_stats("RULA"),
        reba=method_stats("REBA"),
        elevated_measurements=elevated if summaries else [],
        latest=summaries[:OVERVIEW_LATEST],
    )


def count_owned(db: Session, query) -> int:
    return db.scalar(select(func.count()).select_from(query.subquery())) or 0
