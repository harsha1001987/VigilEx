import uuid
from datetime import datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Integer, Numeric, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

SCORE_METHODS = ("REBA", "RULA", "NIOSH")


class AssessmentScore(Base):
    """
    One methodology's result for one assessment. An assessment can hold several
    rows (REBA, RULA, NIOSH), and re-scoring under a new methodology_version adds
    a new row instead of overwriting history.
    """

    __tablename__ = "assessment_scores"
    __table_args__ = (
        CheckConstraint("method IN ('REBA', 'RULA', 'NIOSH')", name="ck_assessment_scores_method"),
        UniqueConstraint(
            "assessment_id", "method", "methodology_version",
            name="uq_assessment_scores_assessment_method_version",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    assessment_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("assessments.id", ondelete="CASCADE"), nullable=False, index=True
    )
    method: Mapped[str] = mapped_column(String(20), nullable=False, index=True)
    methodology_version: Mapped[str] = mapped_column(String(20), nullable=False)

    # REBA/RULA: integer grand score. NIOSH: lifting index (LI). Stored as numeric to cover both.
    score: Mapped[Decimal] = mapped_column(Numeric(10, 3), nullable=False)
    # Free text until scoring bands are finalized (e.g. low / moderate / high / very_high).
    risk_band: Mapped[str | None] = mapped_column(String(20), nullable=True)
    # REBA action level 0-4, RULA 1-4; NIOSH has no equivalent.
    action_level: Mapped[int | None] = mapped_column(Integer, nullable=True)

    # Snapshot of what went into the calculation (load, posture summaries, etc.)
    inputs: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False, server_default="{}")
    # Method-specific outputs (REBA table scores, NIOSH RWL and multipliers, etc.)
    details: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False, server_default="{}")

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    assessment: Mapped["Assessment"] = relationship(back_populates="scores")
