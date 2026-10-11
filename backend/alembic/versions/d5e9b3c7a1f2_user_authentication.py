"""user authentication

Adds what sign-in needs to `users`: an Argon2id password hash (nullable, so
existing users stay valid and simply cannot sign in until a password is set)
and a session version that revokes issued tokens on sign-out. Emails become
unique case-insensitively.

Ownership policy for existing assessments: nothing is backfilled. Assessments
with no assessor_id stay unowned and are visible to no account through the
API. An operator assigns one explicitly with
`python -m app.scripts.assign_assessments` (see backend/README.md).

Revision ID: d5e9b3c7a1f2
Revises: c4d8a2e6f1b3
Create Date: 2026-10-09 12:00:00

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "d5e9b3c7a1f2"
down_revision: Union[str, None] = "c4d8a2e6f1b3"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    bind = op.get_bind()
    clashes = bind.execute(
        sa.text("SELECT lower(email) FROM users GROUP BY lower(email) HAVING count(*) > 1")
    ).fetchall()
    if clashes:
        raise RuntimeError(
            "Cannot add case-insensitive email uniqueness: these emails exist in several cases: "
            + ", ".join(row[0] for row in clashes)
        )
    op.add_column("users", sa.Column("password_hash", sa.String(length=255), nullable=True))
    op.add_column("users", sa.Column("session_version", sa.Integer(), nullable=False, server_default="0"))
    op.create_index("uq_users_email_lower", "users", [sa.text("lower(email)")], unique=True)


def downgrade() -> None:
    op.drop_index("uq_users_email_lower", table_name="users")
    op.drop_column("users", "session_version")
    op.drop_column("users", "password_hash")
