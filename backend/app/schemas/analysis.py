"""
Contracts for video-analysis assessments: validation of the CV pipeline's
result before it is persisted, and the history / detail / overview / report
responses built from stored assessments.

Scores and risk labels are never computed here; they are the values
batch_processor.process_video() produced, validated and passed through.
"""

import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.schemas.assessment import AssessmentResponse

RULA_MAX = 7
REBA_MAX = 15

RulaRisk = Literal["low", "moderate", "high", "very_high"]
RebaRisk = Literal["negligible", "low", "medium", "high", "very_high"]
OverallRisk = Literal["low", "moderate", "high", "very_high"]


# ------------------------------------------------------------ CV result input

class _Open(BaseModel):
    # Unknown keys are kept so nothing the pipeline adds later is dropped.
    model_config = ConfigDict(extra="allow")


class CVVideo(_Open):
    duration_sec: float | None = Field(default=None, ge=0)
    fps: float | None = Field(default=None, ge=0)
    width: int | None = Field(default=None, ge=0)
    height: int | None = Field(default=None, ge=0)
    frames_total: int | None = Field(default=None, ge=0)
    frames_sampled: int | None = Field(default=None, ge=0)


class CVMethodSummary(_Open):
    max_score: int | None = None
    frames_scored: int = Field(default=0, ge=0)
    risk_distribution: dict[str, int] = Field(default_factory=dict)


class CVDerivedAngles(_Open):
    trunk_deg_avg: float | None = None
    trunk_deg_max: float | None = None
    upper_arm_deg_avg: float | None = None
    upper_arm_deg_max: float | None = None
    knee_deg_avg: float | None = None
    knee_deg_max: float | None = None
    neck_deg_avg: float | None = None
    neck_deg_max: float | None = None


class CVAnalysisResult(_Open):
    """The dict returned by batch_processor.process_video(), validated."""

    methodology_version: str = Field(min_length=1, max_length=20)
    video: CVVideo | None = None
    worker: dict[str, Any] | None = None
    quality: dict[str, Any] | None = None
    derived_angles: CVDerivedAngles | None = None
    rula: CVMethodSummary | None = None
    reba: CVMethodSummary | None = None
    keyframes: list[dict[str, Any]] = Field(default_factory=list)
    meta: dict[str, Any] | None = None

    @model_validator(mode="after")
    def _scores_in_range(self) -> "CVAnalysisResult":
        for name, summary, top in (("rula", self.rula, RULA_MAX), ("reba", self.reba, REBA_MAX)):
            if summary is None or summary.max_score is None:
                continue
            if not 1 <= summary.max_score <= top:
                raise ValueError(f"{name}.max_score {summary.max_score} is outside 1-{top}")
            if summary.frames_scored == 0:
                raise ValueError(f"{name}.max_score is set but no frames were scored")
        return self

    @property
    def is_scoreable(self) -> bool:
        return any(m is not None and m.max_score is not None for m in (self.rula, self.reba))


# ------------------------------------------------------------------ responses

# ------------------------------------------------------- posture interventions

InterventionArea = Literal["back", "knees", "neck", "upper_arm"]
RegionStatus = Literal["not_measured", "low", "attention", "elevated", "high"]


class BackPosture(BaseModel):
    """The VigilEx back/trunk posture metric: trunk inclination from upright.

    The angle is the stored trunk_deg (hip -> shoulder line from vertical,
    the same value RULA and REBA score). Direction and view reliability are
    derived from the stored landmarks. Observed posture only.
    """

    average_deg: float | None
    peak_deg: float | None
    frames_measured: int
    # Frames where the trunk was inclined beyond the upright band (> 10 degrees).
    frames_inclined: int
    # Which way the inclined frames leaned, when the landmarks show it.
    direction: Literal["forward", "backward", "mixed"] | None
    # Share of measured frames filmed close to side-on, where 2D trunk angles are most reliable.
    side_on_fraction: float
    # True when most frames were not side-on, so forward bending may be under-measured.
    view_limited: bool


class InterventionEvidence(BaseModel):
    average_deg: float | None
    peak_deg: float | None
    frames_flagged: int
    frames_measured: int


class Intervention(BaseModel):
    """A workplace posture recommendation derived from measured posture. Not medical advice."""

    area: InterventionArea
    priority: Literal["high", "medium"]
    status: RegionStatus
    title: str
    observation: str
    recommendation: str
    evidence: InterventionEvidence


class ProductRecommendation(BaseModel):
    """An Extrive product suggested for evaluation because of a specific finding.

    Built from approved product content (app/content/extrive_products.json)
    and the linked intervention's measured evidence. A suggestion to
    evaluate, never a statement that the worker needs the product.
    """

    product_id: str
    name: str
    maker: str
    category: str | None
    # The intervention area whose finding triggered the suggestion.
    linked_area: InterventionArea
    # "primary" when the linked finding is the priority action.
    role: Literal["primary", "supporting"]
    headline: str
    # Why it is suggested for this assessment, from the measured finding.
    rationale: str
    intended_use: str | None
    operating_principle: str | None
    task_types: list[str]
    suitability: list[str]
    # Task-level changes the product complements, never replaces.
    complementary_actions: list[str]
    evidence_note: str | None
    qualification: str
    sources: list[str]
    # "incomplete" when approved product content is missing; see content_gaps.
    content_status: Literal["verified", "incomplete"]
    content_gaps: list[str] = []


class MeasurementReliability(BaseModel):
    """How far the camera view supports the 2D angle measurements, from this assessment's own values."""

    # "no_limitation_detected" is not a confidence claim; it means no check below failed.
    status: Literal["limited", "no_limitation_detected"]
    # Share of measured frames filmed close to side-on (camera yaw <= 45 degrees); None when the trunk was never measured.
    side_on_fraction: float | None
    # Share of sampled frames in which the worker was detected.
    detection_fraction: float | None
    explanation: str
    title: str | None = None
    reasons: list[str] = []
    recommendation: str | None = None


class ComponentRow(BaseModel):
    name: str
    measured: bool
    angle_deg: float | None
    band: int | None
    max_band: int | None


class ComponentTable(BaseModel):
    """One method's components at the first frame where that method reached its peak score."""

    method: Literal["RULA", "REBA"]
    title: str
    score: int
    scale_max: int
    frame_number: int
    frame_time_sec: float | None
    rows: list[ComponentRow]


class MethodResult(BaseModel):
    score: int | None
    scale_max: int
    risk: str | None
    frames_scored: int
    risk_distribution: dict[str, int]


class AssessmentSummary(BaseModel):
    """One history row."""

    id: uuid.UUID
    status: str
    created_at: datetime
    updated_at: datetime
    video_name: str | None
    duration_sec: float | None
    rula: MethodResult | None
    reba: MethodResult | None
    overall_risk: OverallRisk | None
    methodology_version: str
    # The stored derived_angles, so history-wide aggregates need no per-assessment fetch.
    derived_angles: dict[str, Any] | None = None
    # One plain-language sentence: overall risk and the main opportunities.
    action_summary: str | None = None
    # Most important first; empty when no region needs action.
    interventions: list[Intervention] = []
    # Extrive products to evaluate, each linked to a qualifying intervention; usually empty.
    product_recommendations: list[ProductRecommendation] = []
    measurement_reliability: MeasurementReliability | None = None


class AssessmentPage(BaseModel):
    items: list[AssessmentSummary]
    total: int
    limit: int
    offset: int


class AnalyzeFailure(BaseModel):
    stage: Literal["analysis", "validation", "no_worker"]
    message: str


class RegionMeasurement(BaseModel):
    metric: Literal["trunk", "upper_arm", "knee_flexion", "neck"]
    label: str
    average_deg: float | None
    maximum_deg: float | None
    # Highest backend component severity for the region in any keyframe.
    peak_severity: Literal["ok", "moderate", "high"] | None
    frames_measured: int
    frames_at_peak_severity: int
    # Frames at moderate severity or above.
    frames_flagged: int = 0


class OverviewMethodStats(BaseModel):
    assessments_scored: int
    average: float | None
    highest: int | None
    scale_max: int
    # Assessments counted once each, by the risk band of their peak score.
    distribution: dict[str, int]


class ElevatedMeasurement(BaseModel):
    metric: str
    label: str
    assessments_measured: int
    # Assessments in which the region reached the backend's "high" component severity.
    assessments_elevated: int


class OverviewResponse(BaseModel):
    total_assessments: int
    recent_window_days: int
    recent_assessments: int
    overall_risk_distribution: dict[str, int]
    rula: OverviewMethodStats
    reba: OverviewMethodStats
    elevated_measurements: list[ElevatedMeasurement]
    latest: list[AssessmentSummary]


class ReportFinding(BaseModel):
    title: str
    detail: str
    status: str


class ReportContent(BaseModel):
    """Everything the PDF shows, built only from the stored assessment."""

    assessment_id: uuid.UUID
    title: str
    video_name: str | None
    analyzed_at: datetime
    duration_sec: float | None
    overall_risk: OverallRisk | None
    rula: MethodResult | None
    reba: MethodResult | None
    observation: str | None
    action_summary: str | None = None
    interventions: list[Intervention] = []
    product_recommendations: list[ProductRecommendation] = []
    measurement_reliability: MeasurementReliability | None = None
    back_posture: BackPosture | None = None
    findings: list[ReportFinding]
    measurements: list[RegionMeasurement]
    # How "Not measured" in the measurements must be read.
    measurement_note: str = ""
    evidence_frames: int
    # Main report sections in reading order, then the technical appendix.
    sections: list[str] = []
    technical_section: str = ""
    component_note: str = ""
    component_tables: list[ComponentTable] = []
    methodology: list[str]
    limitations: list[str]
    methodology_version: str


class ReportMetadata(BaseModel):
    assessment_id: uuid.UUID
    status: Literal["ready"]
    generated_at: datetime
    file_name: str
    size_bytes: int
    # Relative API path; never a filesystem path.
    download_url: str
    content: ReportContent


class AssessmentDetail(AssessmentResponse):
    """GET /assessments/{id}: every existing AssessmentResponse field, plus the
    history summary and the full stored analysis."""

    summary: AssessmentSummary
    # Same shape as the POST /analyze-video response; null unless completed.
    analysis: dict[str, Any] | None
    report_available: bool
