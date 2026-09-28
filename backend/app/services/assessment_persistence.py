"""
Persistence service layer for storing CV pipeline assessments in PostgreSQL.

Connects VigilEx CV model outputs (YOLO11n-Pose -> ByteTrack -> RULA/REBA)
to PostgreSQL database records without modifying the underlying CV scoring algorithms.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any, Dict, Optional

from sqlalchemy.orm import Session

from app.models.assessment import Assessment
from app.models.assessment_score import AssessmentScore
from app.models.assessment_intervention import AssessmentIntervention


def persist_cv_assessment_results(
    db: Session,
    assessment: Assessment,
    cv_result: Dict[str, Any],
    primary_assessment: Optional[Dict[str, Any]] = None,
) -> Assessment:
    """
    Persists CV batch processing and primary worker assessment into PostgreSQL.

    Updates Assessment:
    - status = 'completed' if scores are present or processing succeeded
    - capture_metadata = video metadata, quality, camera_view_ok, max_camera_yaw_deg, etc.
    - keypoint_series = keyframes, track_id, worker selection info
    - derived_angles = average and max posture angles
    - methodology_version = methodology version string from pipeline (e.g. 'vigilex-rula-reba-v1')

    Updates AssessmentScore:
    - Creates or updates RULA and REBA rows in assessment_scores table with exact scores,
      risk bands, frames observed, valid frames, and coverage percentage.
    """
    methodology_version = cv_result.get("methodology_version", "vigilex-rula-reba-v1")
    # String(20) constraint safeguard
    if len(methodology_version) > 20:
        methodology_version = methodology_version[:20]

    assessment.status = "completed"
    assessment.methodology_version = methodology_version

    video_meta = cv_result.get("video", {})
    quality_meta = cv_result.get("quality", {})
    extra_meta = cv_result.get("meta", {})

    assessment.capture_metadata = {
        "video": video_meta,
        "quality": quality_meta,
        "meta": extra_meta,
    }

    worker_meta = cv_result.get("worker", {})
    keyframes_meta = cv_result.get("keyframes", [])
    assessment.keypoint_series = {
        "worker": worker_meta,
        "keyframes_count": len(keyframes_meta),
        "primary_track": primary_assessment if primary_assessment else {},
    }

    derived_angles = cv_result.get("derived_angles")
    if not derived_angles and primary_assessment:
        derived_angles = primary_assessment.get("angles", {})
    assessment.derived_angles = derived_angles

    # Extract score details
    frames_observed = 0
    primary_track_id = None

    if primary_assessment:
        primary_track_id = primary_assessment.get("track_id")
        frames_observed = primary_assessment.get(
            "frames_observed",
            video_meta.get("frames_sampled", 0),
        )
    else:
        primary_track_id = worker_meta.get("track_id")
        frames_observed = video_meta.get("frames_sampled", 0)

    # -------------------------------------------------------------
    # RULA Persistence
    # -------------------------------------------------------------
    rula_data = (
        primary_assessment.get("rula", {})
        if primary_assessment
        else cv_result.get("rula", {})
    )
    rula_score_val = rula_data.get("score")
    if rula_score_val is None:
        rula_score_val = cv_result.get("rula", {}).get("max_score")

    rula_risk = rula_data.get("risk")
    if not rula_risk or rula_risk == "unknown":
        rula_risk = cv_result.get("rula", {}).get("risk") or "unknown"

    valid_rula_frames = rula_data.get(
        "valid_frames",
        cv_result.get("rula", {}).get("frames_scored", 0),
    )

    rula_coverage = rula_data.get("coverage")
    if rula_coverage is None:
        rula_coverage = (
            (valid_rula_frames / frames_observed)
            if frames_observed > 0
            else 0.0
        )

    if rula_score_val is not None:
        _save_or_update_score(
            db=db,
            assessment_id=assessment.id,
            method="RULA",
            methodology_version=methodology_version,
            score=Decimal(str(rula_score_val)),
            risk_band=str(rula_risk),
            action_level=None,
            inputs={
                "primary_track_id": primary_track_id,
                "frames_observed": frames_observed,
                "valid_frames": valid_rula_frames,
                "coverage": float(rula_coverage),
                "coverage_pct": f"{round(float(rula_coverage) * 100, 2)}%",
            },
            details={
                "risk_distribution": cv_result.get("rula", {}).get("risk_distribution", {}),
                "angles": (
                    primary_assessment.get("angles", {})
                    if primary_assessment
                    else cv_result.get("derived_angles", {})
                ),
            },
        )

    # -------------------------------------------------------------
    # REBA Persistence
    # -------------------------------------------------------------
    reba_data = (
        primary_assessment.get("reba", {})
        if primary_assessment
        else cv_result.get("reba", {})
    )
    reba_score_val = reba_data.get("score")
    if reba_score_val is None:
        reba_score_val = cv_result.get("reba", {}).get("max_score")

    reba_risk = reba_data.get("risk")
    if not reba_risk or reba_risk == "unknown":
        reba_risk = cv_result.get("reba", {}).get("risk") or "unknown"

    valid_reba_frames = reba_data.get(
        "valid_frames",
        cv_result.get("reba", {}).get("frames_scored", 0),
    )

    reba_coverage = reba_data.get("coverage")
    if reba_coverage is None:
        reba_coverage = (
            (valid_reba_frames / frames_observed)
            if frames_observed > 0
            else 0.0
        )

    if reba_score_val is not None:
        _save_or_update_score(
            db=db,
            assessment_id=assessment.id,
            method="REBA",
            methodology_version=methodology_version,
            score=Decimal(str(reba_score_val)),
            risk_band=str(reba_risk),
            action_level=None,
            inputs={
                "primary_track_id": primary_track_id,
                "frames_observed": frames_observed,
                "valid_frames": valid_reba_frames,
                "coverage": float(reba_coverage),
                "coverage_pct": f"{round(float(reba_coverage) * 100, 2)}%",
            },
            details={
                "risk_distribution": cv_result.get("reba", {}).get("risk_distribution", {}),
                "angles": (
                    primary_assessment.get("angles", {})
                    if primary_assessment
                    else cv_result.get("derived_angles", {})
                ),
            },
        )

    # Generate interventions if high risk
    _generate_interventions_if_needed(db, assessment, rula_score_val, reba_score_val)

    db.commit()
    db.refresh(assessment)
    return assessment


def _save_or_update_score(
    db: Session,
    assessment_id: uuid.UUID,
    method: str,
    methodology_version: str,
    score: Decimal,
    risk_band: str,
    action_level: Optional[int],
    inputs: Dict[str, Any],
    details: Dict[str, Any],
) -> AssessmentScore:
    """Creates or updates an AssessmentScore record transactionally."""
    existing_score = (
        db.query(AssessmentScore)
        .filter(
            AssessmentScore.assessment_id == assessment_id,
            AssessmentScore.method == method,
            AssessmentScore.methodology_version == methodology_version,
        )
        .first()
    )

    if existing_score:
        existing_score.score = score
        existing_score.risk_band = risk_band
        existing_score.action_level = action_level
        existing_score.inputs = inputs
        existing_score.details = details
        return existing_score

    score_record = AssessmentScore(
        assessment_id=assessment_id,
        method=method,
        methodology_version=methodology_version,
        score=score,
        risk_band=risk_band,
        action_level=action_level,
        inputs=inputs,
        details=details,
    )
    db.add(score_record)
    return score_record


def _generate_interventions_if_needed(
    db: Session,
    assessment: Assessment,
    rula_score: Optional[int],
    reba_score: Optional[int],
) -> None:
    """Generates recommendations if risk levels are high/very_high."""
    if rula_score is not None and rula_score >= 4:
        db.add(
            AssessmentIntervention(
                assessment_id=assessment.id,
                risk_driver="upper_body_posture",
                recommendation="Investigate workplace layout to reduce sustained shoulder/arm elevation.",
                priority="high" if rula_score >= 6 else "medium",
                extrive_product="ShoulderEX",
            )
        )
    if reba_score is not None and reba_score >= 4:
        db.add(
            AssessmentIntervention(
                assessment_id=assessment.id,
                risk_driver="full_body_posture",
                recommendation="Adjust work surface height and evaluate lumbar support during task execution.",
                priority="high" if reba_score >= 8 else "medium",
                extrive_product="BackEX",
            )
        )
