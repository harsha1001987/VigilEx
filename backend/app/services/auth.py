"""
Accounts: registration, sign-in and sign-out.

Each new account gets its own organization (users.organization_id is
required, and the mobile app has no organization picker). The account's
assessments and hierarchy belong to that organization.
"""

from __future__ import annotations

import uuid

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.security import hash_password, needs_rehash, verify_password
from app.models.organization import Organization
from app.models.user import User
from app.schemas.auth import normalize_email


class EmailTaken(Exception):
    pass


def find_by_email(db: Session, email: str) -> User | None:
    return db.scalars(select(User).where(func.lower(User.email) == normalize_email(email))).first()


def get_user(db: Session, user_id: uuid.UUID) -> User | None:
    return db.get(User, user_id)


def register(db: Session, *, name: str, email: str, password: str) -> User:
    email = normalize_email(email)
    if find_by_email(db, email) is not None:
        raise EmailTaken()
    organization = Organization(name=f"{name}'s workspace")
    user = User(organization=organization, name=name, email=email, password_hash=hash_password(password))
    db.add_all([organization, user])
    try:
        db.commit()
    except IntegrityError:
        # A concurrent registration won the unique email index.
        db.rollback()
        raise EmailTaken() from None
    db.refresh(user)
    return user


def authenticate(db: Session, email: str, password: str) -> User | None:
    """The user for these credentials, or None. Takes the same time whether or not the email exists."""
    user = find_by_email(db, email)
    if not verify_password(password, user.password_hash if user else None):
        return None
    if needs_rehash(user.password_hash):
        user.password_hash = hash_password(password)
        db.commit()
    return user


def sign_out_everywhere(db: Session, user: User) -> None:
    """Revokes every access token issued to [user] so far."""
    user.session_version += 1
    db.commit()
