import uuid
from datetime import datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, Numeric, String, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

ASSESSMENT_STATUSES = ("draft", "in_progress", "completed")
LOAD_SOURCES = ("measured", "prompted", "default")


class Assessment(Base):
    """
    One ergonomic assessment of a task performed by a (pseudonymous) worker.

    Evolving computer-vision output (capture metadata, keypoint series, derived
    angles) is stored as JSONB on this row. Scores and interventions are
    normalized into child tables because they are queried independently.
    """

    __tablename__ = "assessments"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'in_progress', 'completed')",
            name="ck_assessments_status",
        ),
        CheckConstraint(
            "load_source IS NULL OR load_source IN ('measured', 'prompted', 'default')",
            name="ck_assessments_load_source",
        ),
        # A load value is meaningless without knowing where it came from.
        CheckConstraint(
            "load_value IS NULL OR load_source IS NOT NULL",
            name="ck_assessments_load_requires_source",
        ),
        CheckConstraint(
            "consent_given = false OR consent_timestamp IS NOT NULL",
            name="ck_assessments_consent_requires_timestamp",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # Context (derived hierarchy; not duplicated as text)
    organization_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("organizations.id"), nullable=False, index=True
    )
    site_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("sites.id"), nullable=False, index=True
    )
    area_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("areas.id"), nullable=False, index=True
    )
    task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("tasks.id"), nullable=False, index=True
    )

    # People
    worker_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("workers.id", ondelete="SET NULL"), nullable=True, index=True
    )
    # Nullable until authentication exists; will become required later.
    assessor_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )

    # Lifecycle
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="draft")
    captured_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, index=True)

    # Computer-vision payloads (see app/schemas/assessment.py for the JSON contracts)
    capture_metadata: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    keypoint_series: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    derived_angles: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)

    # Load input (cannot be inferred from video; source must be explicit)
    load_value: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    load_unit: Mapped[str | None] = mapped_column(String(10), nullable=True)
    load_source: Mapped[str | None] = mapped_column(String(20), nullable=True)

    # Quick boolean risk-driver summary; full recommendations live in assessment_interventions.
    intervention_flags: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)

    # Version of the scoring methodology that produced this assessment's scores.
    methodology_version: Mapped[str] = mapped_column(String(20), nullable=False, server_default="v1.0")

    # Worker consent (logged, no personal data)
    consent_given: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="false")
    consent_timestamp: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    organization: Mapped["Organization"] = relationship(back_populates="assessments")
    site: Mapped["Site"] = relationship(back_populates="assessments")
    area: Mapped["Area"] = relationship(back_populates="assessments")
    task: Mapped["Task"] = relationship(back_populates="assessments")
    worker: Mapped["Worker | None"] = relationship(back_populates="assessments")
    assessor: Mapped["User | None"] = relationship(back_populates="assessments")
    scores: Mapped[list["AssessmentScore"]] = relationship(
        back_populates="assessment", cascade="all, delete-orphan"
    )
    interventions: Mapped[list["AssessmentIntervention"]] = relationship(
        back_populates="assessment", cascade="all, delete-orphan"
    )
