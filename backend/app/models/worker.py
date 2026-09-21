import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class Worker(Base):
    """
    A pseudonymous worker reference. Deliberately stores no direct identity
    (name, phone, national ID, email). `worker_ref` is an opaque code chosen by
    the organization and is unique within that organization.
    """

    __tablename__ = "workers"
    __table_args__ = (
        UniqueConstraint("organization_id", "worker_ref", name="uq_workers_organization_worker_ref"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    organization_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("organizations.id"), nullable=False, index=True
    )
    worker_ref: Mapped[str] = mapped_column(String(100), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    organization: Mapped["Organization"] = relationship(back_populates="workers")
    assessments: Mapped[list["Assessment"]] = relationship(back_populates="worker")
