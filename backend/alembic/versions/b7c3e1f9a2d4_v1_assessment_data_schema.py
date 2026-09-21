"""v1 assessment data schema

Adds pseudonymous workers, expands assessments with capture/CV/load/consent
fields, and normalizes scores and interventions into child tables.

Revision ID: b7c3e1f9a2d4
Revises: adda9a20dc4e
Create Date: 2026-09-21 16:10:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = "b7c3e1f9a2d4"
down_revision: Union[str, None] = "adda9a20dc4e"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # ------------------------------------------------------------- workers
    op.create_table(
        "workers",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("organization_id", sa.UUID(), nullable=False),
        sa.Column("worker_ref", sa.String(length=100), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["organization_id"], ["organizations.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("organization_id", "worker_ref", name="uq_workers_organization_worker_ref"),
    )
    op.create_index("ix_workers_organization_id", "workers", ["organization_id"])

    # --------------------------------------------------------- assessments
    # user_id already represented the assessor; rename instead of duplicating.
    op.drop_constraint("assessments_user_id_fkey", "assessments", type_="foreignkey")
    op.alter_column("assessments", "user_id", new_column_name="assessor_id", nullable=True)
    op.create_foreign_key(
        "assessments_assessor_id_fkey", "assessments", "users",
        ["assessor_id"], ["id"], ondelete="SET NULL",
    )

    op.add_column("assessments", sa.Column("worker_id", sa.UUID(), nullable=True))
    op.create_foreign_key(
        "assessments_worker_id_fkey", "assessments", "workers",
        ["worker_id"], ["id"], ondelete="SET NULL",
    )

    op.add_column("assessments", sa.Column("captured_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("assessments", sa.Column("capture_metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=True))
    op.add_column("assessments", sa.Column("keypoint_series", postgresql.JSONB(astext_type=sa.Text()), nullable=True))
    op.add_column("assessments", sa.Column("derived_angles", postgresql.JSONB(astext_type=sa.Text()), nullable=True))
    op.add_column("assessments", sa.Column("load_value", sa.Numeric(precision=8, scale=2), nullable=True))
    op.add_column("assessments", sa.Column("load_unit", sa.String(length=10), nullable=True))
    op.add_column("assessments", sa.Column("load_source", sa.String(length=20), nullable=True))
    op.add_column("assessments", sa.Column("intervention_flags", postgresql.JSONB(astext_type=sa.Text()), nullable=True))
    op.add_column(
        "assessments",
        sa.Column("methodology_version", sa.String(length=20), server_default="v1.0", nullable=False),
    )
    op.add_column(
        "assessments",
        sa.Column("consent_given", sa.Boolean(), server_default=sa.text("false"), nullable=False),
    )
    op.add_column("assessments", sa.Column("consent_timestamp", sa.DateTime(timezone=True), nullable=True))

    op.create_check_constraint(
        "ck_assessments_status", "assessments",
        "status IN ('draft', 'in_progress', 'completed')",
    )
    op.create_check_constraint(
        "ck_assessments_load_source", "assessments",
        "load_source IS NULL OR load_source IN ('measured', 'prompted', 'default')",
    )
    op.create_check_constraint(
        "ck_assessments_load_requires_source", "assessments",
        "load_value IS NULL OR load_source IS NOT NULL",
    )
    op.create_check_constraint(
        "ck_assessments_consent_requires_timestamp", "assessments",
        "consent_given = false OR consent_timestamp IS NOT NULL",
    )

    op.create_index("ix_assessments_organization_id", "assessments", ["organization_id"])
    op.create_index("ix_assessments_site_id", "assessments", ["site_id"])
    op.create_index("ix_assessments_area_id", "assessments", ["area_id"])
    op.create_index("ix_assessments_task_id", "assessments", ["task_id"])
    op.create_index("ix_assessments_worker_id", "assessments", ["worker_id"])
    op.create_index("ix_assessments_assessor_id", "assessments", ["assessor_id"])
    op.create_index("ix_assessments_captured_at", "assessments", ["captured_at"])

    # --------------------------------------------------- assessment_scores
    op.create_table(
        "assessment_scores",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("assessment_id", sa.UUID(), nullable=False),
        sa.Column("method", sa.String(length=20), nullable=False),
        sa.Column("methodology_version", sa.String(length=20), nullable=False),
        sa.Column("score", sa.Numeric(precision=10, scale=3), nullable=False),
        sa.Column("risk_band", sa.String(length=20), nullable=True),
        sa.Column("action_level", sa.Integer(), nullable=True),
        sa.Column("inputs", postgresql.JSONB(astext_type=sa.Text()), server_default="{}", nullable=False),
        sa.Column("details", postgresql.JSONB(astext_type=sa.Text()), server_default="{}", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("method IN ('REBA', 'RULA', 'NIOSH')", name="ck_assessment_scores_method"),
        sa.ForeignKeyConstraint(["assessment_id"], ["assessments.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "assessment_id", "method", "methodology_version",
            name="uq_assessment_scores_assessment_method_version",
        ),
    )
    op.create_index("ix_assessment_scores_assessment_id", "assessment_scores", ["assessment_id"])
    op.create_index("ix_assessment_scores_method", "assessment_scores", ["method"])

    # -------------------------------------------- assessment_interventions
    op.create_table(
        "assessment_interventions",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("assessment_id", sa.UUID(), nullable=False),
        sa.Column("risk_driver", sa.String(length=50), nullable=False),
        sa.Column("recommendation", sa.Text(), nullable=False),
        sa.Column("priority", sa.String(length=10), nullable=False),
        sa.Column("extrive_product", sa.String(length=50), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("priority IN ('low', 'medium', 'high')", name="ck_assessment_interventions_priority"),
        sa.ForeignKeyConstraint(["assessment_id"], ["assessments.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_assessment_interventions_assessment_id", "assessment_interventions", ["assessment_id"])


def downgrade() -> None:
    op.drop_index("ix_assessment_interventions_assessment_id", table_name="assessment_interventions")
    op.drop_table("assessment_interventions")

    op.drop_index("ix_assessment_scores_method", table_name="assessment_scores")
    op.drop_index("ix_assessment_scores_assessment_id", table_name="assessment_scores")
    op.drop_table("assessment_scores")

    for name in (
        "ix_assessments_captured_at",
        "ix_assessments_assessor_id",
        "ix_assessments_worker_id",
        "ix_assessments_task_id",
        "ix_assessments_area_id",
        "ix_assessments_site_id",
        "ix_assessments_organization_id",
    ):
        op.drop_index(name, table_name="assessments")

    op.drop_constraint("ck_assessments_consent_requires_timestamp", "assessments", type_="check")
    op.drop_constraint("ck_assessments_load_requires_source", "assessments", type_="check")
    op.drop_constraint("ck_assessments_load_source", "assessments", type_="check")
    op.drop_constraint("ck_assessments_status", "assessments", type_="check")

    for name in (
        "consent_timestamp", "consent_given", "methodology_version", "intervention_flags",
        "load_source", "load_unit", "load_value", "derived_angles", "keypoint_series",
        "capture_metadata", "captured_at",
    ):
        op.drop_column("assessments", name)

    op.drop_constraint("assessments_worker_id_fkey", "assessments", type_="foreignkey")
    op.drop_column("assessments", "worker_id")

    op.drop_constraint("assessments_assessor_id_fkey", "assessments", type_="foreignkey")
    op.alter_column("assessments", "assessor_id", new_column_name="user_id", nullable=False)
    op.create_foreign_key("assessments_user_id_fkey", "assessments", "users", ["user_id"], ["id"])

    op.drop_index("ix_workers_organization_id", table_name="workers")
    op.drop_table("workers")
