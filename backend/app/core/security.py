"""
Password hashing and access tokens.

Passwords are hashed with Argon2id (argon2-cffi, OWASP's first choice); the
plaintext is never stored or logged. Access tokens are JWTs (PyJWT) signed
with HMAC-SHA256 using AUTH_SECRET_KEY. Each token carries:

    sub  user id            typ  "access" (rejects any other kind of token)
    iat  issued at          exp  expiry (ACCESS_TOKEN_EXPIRE_MINUTES)
    iss  this API           aud  the VigilEx app
    ver  the user's session_version when the token was issued

Tokens are otherwise stateless. Signing out increments the user's
session_version, which invalidates every token issued before it, on every
device; there is no per-token denylist and no refresh token.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError

from app.core.config import get_settings

ALGORITHM = "HS256"
TOKEN_TYPE = "access"

_hasher = PasswordHasher()
# Verified against when an email has no account, so a failed sign-in takes the
# same time whether or not the email exists.
_DUMMY_HASH = _hasher.hash("vigilex-timing-equalizer")


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password: str, password_hash: str | None) -> bool:
    """True only for the right password. With no hash (unknown email), spends the same time and fails."""
    try:
        return _hasher.verify(password_hash or _DUMMY_HASH, password) and password_hash is not None
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def needs_rehash(password_hash: str) -> bool:
    return _hasher.check_needs_rehash(password_hash)


@dataclass(frozen=True)
class IssuedToken:
    token: str
    expires_at: datetime


@dataclass(frozen=True)
class TokenClaims:
    user_id: uuid.UUID
    session_version: int


class InvalidToken(Exception):
    """The token is malformed, expired, wrongly signed or not an access token."""


def create_access_token(user_id: uuid.UUID, session_version: int, now: datetime | None = None) -> IssuedToken:
    settings = get_settings()
    issued = now or datetime.now(timezone.utc)
    expires = issued + timedelta(minutes=settings.access_token_expire_minutes)
    claims = {
        "sub": str(user_id),
        "typ": TOKEN_TYPE,
        "ver": session_version,
        "iat": issued,
        "exp": expires,
        "iss": settings.auth_token_issuer,
        "aud": settings.auth_token_audience,
    }
    return IssuedToken(jwt.encode(claims, settings.signing_key, algorithm=ALGORITHM), expires)


def decode_access_token(token: str) -> TokenClaims:
    settings = get_settings()
    try:
        claims = jwt.decode(
            token,
            settings.signing_key,
            algorithms=[ALGORITHM],  # Never trust the token's own "alg".
            audience=settings.auth_token_audience,
            issuer=settings.auth_token_issuer,
            options={"require": ["sub", "typ", "ver", "iat", "exp", "iss", "aud"]},
        )
    except jwt.PyJWTError as e:
        raise InvalidToken(type(e).__name__) from None
    if claims.get("typ") != TOKEN_TYPE or not isinstance(claims.get("ver"), int):
        raise InvalidToken("wrong token type")
    try:
        return TokenClaims(uuid.UUID(claims["sub"]), claims["ver"])
    except (ValueError, TypeError):
        raise InvalidToken("bad subject") from None
