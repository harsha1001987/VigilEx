"""
Pydantic models for the assessment schema.

The three JSONB payloads on `assessments` (capture_metadata, keypoint_series,
derived_angles) are defined here so their structure is documented and
validatable. Every payload carries `source` and a model/algorithm version so
video-derived (VigilEx) and sensor-derived (ErgoEx, future) data can share the
same Assessment parent.
"""

import uuid
from datetime import datetime
from decimal import Decimal
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

MeasurementSource = Literal["vigilex_android", "ergoex_sensor"]
AssessmentStatus = Literal["draft", "in_progress", "completed"]
LoadSource = Literal["measured", "prompted", "default"]
ScoreMethod = Literal["REBA", "RULA", "NIOSH"]
InterventionPriority = Literal["low", "medium", "high"]


# ----------------------------------------------------------------- capture_metadata

class Resolution(BaseModel):
    width: int
    height: int


class CaptureMetadata(BaseModel):
    source: MeasurementSource = "vigilex_android"
    device_model: str | None = None
    os: str | None = "android"
    os_version: str | None = None
    app_version: str | None = None
    fps: float | None = None
    camera_angle: Literal["side", "front", "oblique", "unknown"] | None = None
    resolution: Resolution | None = None
    duration_seconds: float | None = None
    frame_count: int | None = None
    quality_score: float | None = Field(default=None, ge=0, le=1)


# ------------------------------------------------------------------ keypoint_series

class KeypointFrame(BaseModel):
    """
    One frame of pose output. `landmarks` is a list of numeric rows ordered by
    landmark index; the meaning of each column is given by
    KeypointSeries.landmark_fields. Rows instead of objects keep a 900-frame
    capture roughly 4x smaller.
    """

    frame_index: int
    t_ms: int
    landmarks: list[list[float]]


class KeypointSeries(BaseModel):
    source: MeasurementSource = "vigilex_android"
    model: str = "mediapipe_pose"
    model_version: str
    landmark_count: int = 33
    coordinate_space: Literal["normalized_2d", "image_px", "world_3d"] = "normalized_2d"
    landmark_fields: list[str] = ["x", "y", "z", "visibility"]
    frames: list[KeypointFrame]


# ------------------------------------------------------------------- derived_angles

class JointAngleSeries(BaseModel):
    """
    Time series for one joint. `samples` rows follow DerivedAngles.sample_fields
    (e.g. [t_ms, angle]); future columns such as angular_velocity are added by
    extending sample_fields. `summary` holds per-joint aggregates and is open
    for hold_duration, time_in_risk_zone, asymmetry, etc.
    """

    side: Literal["left", "right"] | None = None
    samples: list[list[float]]
    summary: dict[str, Any] = Field(default_factory=dict)


class DerivedAngles(BaseModel):
    source: MeasurementSource = "vigilex_android"
    algorithm_version: str
    unit: Literal["degrees"] = "degrees"
    sample_rate_hz: float | None = None
    sample_fields: list[str] = ["t_ms", "angle"]
    # Keys: trunk, neck, left_shoulder, right_shoulder, left_elbow, right_elbow,
    # left_wrist, right_wrist, left_knee, right_knee (extensible).
    joints: dict[str, JointAngleSeries]


# ------------------------------------------------------------------------- rows

class LoadInput(BaseModel):
    load_value: Decimal | None = None
    load_unit: str | None = "kg"
    load_source: LoadSource | None = None


class AssessmentScoreRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    assessment_id: uuid.UUID
    method: ScoreMethod
    methodology_version: str
    score: Decimal
    risk_band: str | None
    action_level: int | None
    inputs: dict[str, Any]
    details: dict[str, Any]
    created_at: datetime


class AssessmentInterventionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    assessment_id: uuid.UUID
    risk_driver: str
    recommendation: str
    priority: InterventionPriority
    extrive_product: str | None
    created_at: datetime


# --------------------------------------------------------------- CRUD schemas
#
# capture_metadata / keypoint_series / derived_angles / intervention_flags are
# accepted and returned as plain dicts at this stage. The strict contracts
# above (CaptureMetadata, KeypointSeries, DerivedAngles) document the intended
# shape for when the capture/scoring pipeline is implemented, but validating
# against them here would risk silently dropping fields from a payload that
# doesn't perfectly match yet. JSON in, JSON stored, JSON out.

def _validate_load_and_consent(values: dict[str, Any]) -> dict[str, Any]:
    if values.get("load_value") is not None and not values.get("load_source"):
        raise ValueError("load_source is required when load_value is provided")
    if values.get("consent_given") and values.get("consent_timestamp") is None:
        raise ValueError("consent_timestamp is required when consent_given is true")
    return values


class AssessmentBase(BaseModel):
    organization_id: uuid.UUID
    site_id: uuid.UUID
    area_id: uuid.UUID
    task_id: uuid.UUID
    worker_id: uuid.UUID | None = None
    assessor_id: uuid.UUID | None = None

    status: AssessmentStatus = "draft"
    captured_at: datetime | None = None

    capture_metadata: dict[str, Any] | None = None
    keypoint_series: dict[str, Any] | None = None
    derived_angles: dict[str, Any] | None = None

    load_value: Decimal | None = None
    load_unit: str | None = None
    load_source: LoadSource | None = None

    intervention_flags: dict[str, bool] | None = None
    methodology_version: str = "v1.0"

    consent_given: bool = False
    consent_timestamp: datetime | None = None

    @model_validator(mode="after")
    def _check_load_and_consent(self) -> "AssessmentBase":
        _validate_load_and_consent(self.__dict__)
        return self


class AssessmentCreate(AssessmentBase):
    pass


class AssessmentUpdate(AssessmentBase):
    """Full-replacement update, matching this project's existing PUT convention
    (see SiteUpdate/AreaUpdate/TaskUpdate): fields omitted by the client are
    reset to their default, so a client updating one field should resend the
    others it wants to keep."""

    pass


class AssessmentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    organization_id: uuid.UUID
    site_id: uuid.UUID
    area_id: uuid.UUID
    task_id: uuid.UUID
    worker_id: uuid.UUID | None
    assessor_id: uuid.UUID | None
    status: AssessmentStatus
    captured_at: datetime | None
    capture_metadata: dict[str, Any] | None
    keypoint_series: dict[str, Any] | None
    derived_angles: dict[str, Any] | None
    load_value: Decimal | None
    load_unit: str | None
    load_source: LoadSource | None
    intervention_flags: dict[str, Any] | None
    methodology_version: str
    consent_given: bool
    consent_timestamp: datetime | None
    created_at: datetime
    updated_at: datetime
    scores: list[AssessmentScoreRead] = []
    interventions: list[AssessmentInterventionRead] = []
