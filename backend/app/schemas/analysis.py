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
    findings: list[ReportFinding]
    measurements: list[RegionMeasurement]
    evidence_frames: int
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
