"""
Sets a new password for an account (operator account recovery).

VigilEx cannot send password-reset emails yet. An operator with server
access resets a password, then gives it to the user through a trusted
channel:

    python -m app.scripts.set_password --email person@company.com

The password is read from the terminal without echo (or from the
VIGILEX_NEW_PASSWORD environment variable for automation) and never printed.
Every existing session of the account is signed out.
"""

from __future__ import annotations

import argparse
import getpass
import os
import sys

from app.core.security import hash_password
from app.db.database import SessionLocal
from app.schemas.auth import MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH
from app.services.auth import find_by_email


def set_password(db, email: str, password: str) -> None:
    if not MIN_PASSWORD_LENGTH <= len(password) <= MAX_PASSWORD_LENGTH or not password.strip():
        raise ValueError(f"The password must be {MIN_PASSWORD_LENGTH}-{MAX_PASSWORD_LENGTH} characters.")
    user = find_by_email(db, email)
    if user is None:
        raise ValueError(f"No account with email {email}.")
    user.password_hash = hash_password(password)
    user.session_version += 1  # Old sessions end with the old password.
    db.commit()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--email", required=True)
    args = parser.parse_args(argv)
    password = os.environ.get("VIGILEX_NEW_PASSWORD")
    if password is None:
        password = getpass.getpass("New password: ")
        if getpass.getpass("Repeat: ") != password:
            print("Passwords do not match; nothing changed.", file=sys.stderr)
            return 1
    with SessionLocal() as db:
        try:
            set_password(db, args.email, password)
        except ValueError as e:
            print(f"Nothing changed: {e}", file=sys.stderr)
            return 1
    print(f"Password updated for {args.email}; existing sessions were signed out.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
