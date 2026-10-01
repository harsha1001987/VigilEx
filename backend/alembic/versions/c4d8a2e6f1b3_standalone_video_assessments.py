"""standalone video assessments

Assessments created from POST /api/v1/analyze-video come from the mobile app,
which does not collect an organization/site/area/task hierarchy. The hierarchy
columns become nullable (still validated when supplied), a 'failed' status is
added so failed analyses are recorded without looking completed, and the
history ordering columns are indexed.

Revision ID: c4d8a2e6f1b3
Revises: b7c3e1f9a2d4
Create Date: 2026-09-30 16:00:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "c4d8a2e6f1b3"
down_revision: Union[str, None] = "b7c3e1f9a2d4"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

HIERARCHY = ("organization_id", "site_id", "area_id", "task_id")


def upgrade() -> None:
    for column in HIERARCHY:
        op.alter_column("assessments", column, existing_type=sa.UUID(), nullable=True)

    op.drop_constraint("ck_assessments_status", "assessments", type_="check")
    op.create_check_constraint(
        "ck_assessments_status", "assessments",
        "status IN ('draft', 'in_progress', 'completed', 'failed')",
    )

    op.create_index("ix_assessments_status", "assessments", ["status"])
    op.create_index("ix_assessments_created_at", "assessments", ["created_at"])


def downgrade() -> None:
    bind = op.get_bind()
    orphaned = bind.execute(
        sa.text(
            "SELECT count(*) FROM assessments WHERE "
            + " OR ".join(f"{c} IS NULL" for c in HIERARCHY)
        )
    ).scalar()
    if orphaned:
        # Restoring NOT NULL would require inventing a hierarchy for these rows.
        raise RuntimeError(
            f"Cannot downgrade: {orphaned} assessment(s) have no organization/site/area/task. "
            "Delete or re-parent them first."
        )

    op.drop_index("ix_assessments_created_at", table_name="assessments")
    op.drop_index("ix_assessments_status", table_name="assessments")

    # 'failed' has no equivalent in the previous schema; those rows return to draft.
    op.execute("UPDATE assessments SET status = 'draft' WHERE status = 'failed'")
    op.drop_constraint("ck_assessments_status", "assessments", type_="check")
    op.create_check_constraint(
        "ck_assessments_status", "assessments",
        "status IN ('draft', 'in_progress', 'completed')",
    )

    for column in HIERARCHY:
        op.alter_column("assessments", column, existing_type=sa.UUID(), nullable=False)
