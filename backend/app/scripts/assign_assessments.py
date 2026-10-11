"""
Assigns unowned assessments (created before authentication existed) to an account.

Policy: unowned assessments are visible to no account. Nothing assigns them
automatically, because the database does not record who made them. An
operator who knows whose they are assigns each one explicitly:

    python -m app.scripts.assign_assessments --list
    python -m app.scripts.assign_assessments --email person@company.com --id <uuid> [--id <uuid> ...]
    python -m app.scripts.assign_assessments --email person@company.com --id <uuid> --dry-run

Only unowned assessments can be assigned; an assessment that already has an
owner is never moved. The assessment also joins the account's organization
when it has none.
"""

from __future__ import annotations

import argparse
import sys
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.database import SessionLocal
from app.models.assessment import Assessment
from app.services.auth import find_by_email


def unowned(db: Session) -> list[Assessment]:
    return list(db.scalars(
        select(Assessment).where(Assessment.assessor_id.is_(None)).order_by(Assessment.created_at)
    ).all())


def assign(db: Session, email: str, ids: list[uuid.UUID], dry_run: bool = False) -> list[str]:
    """Assigns each id to the account. Returns a line per id; raises ValueError before changing anything if any id cannot be assigned."""
    user = find_by_email(db, email)
    if user is None:
        raise ValueError(f"No account with email {email}.")
    rows = []
    for assessment_id in ids:
        assessment = db.get(Assessment, assessment_id)
        if assessment is None:
            raise ValueError(f"Assessment {assessment_id} does not exist.")
        if assessment.assessor_id is not None:
            raise ValueError(f"Assessment {assessment_id} already has an owner; it is not moved.")
        if assessment.organization_id not in (None, user.organization_id):
            raise ValueError(f"Assessment {assessment_id} belongs to another organization; it is not moved.")
        rows.append(assessment)
    lines = []
    for assessment in rows:
        assessment.assessor_id = user.id
        assessment.organization_id = user.organization_id
        lines.append(f"{assessment.id} -> {user.email}")
    if dry_run:
        db.rollback()
    else:
        db.commit()
    return lines


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--list", action="store_true", help="List unowned assessments.")
    parser.add_argument("--email", help="Account to assign the assessments to.")
    parser.add_argument("--id", dest="ids", action="append", type=uuid.UUID, default=[], help="Assessment id (repeatable).")
    parser.add_argument("--dry-run", action="store_true", help="Show what would change without saving.")
    args = parser.parse_args(argv)

    with SessionLocal() as db:
        if args.list:
            for a in unowned(db):
                name = ((a.capture_metadata or {}).get("upload") or {}).get("original_filename") or "-"
                print(f"{a.id}  {a.status:<11}  {a.created_at:%Y-%m-%d %H:%M}  {name}")
            return 0
        if not args.email or not args.ids:
            parser.error("--email and at least one --id are required (or use --list).")
        try:
            for line in assign(db, args.email, args.ids, args.dry_run):
                print(("would assign " if args.dry_run else "assigned ") + line)
        except ValueError as e:
            print(f"Nothing changed: {e}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
