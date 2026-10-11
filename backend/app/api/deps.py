"""
Request dependencies shared by routes.

`get_current_user` authenticates every protected route from the
`Authorization: Bearer <access token>` header. The account it returns is the
only source of ownership: routes scope their queries with it
(services.analysis_assessments.owned) and never accept a user id from the
client. Every failure is the same 401, so a caller learns nothing about why.
"""

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.security import InvalidToken, decode_access_token
from app.db.database import get_db
from app.models.user import User
from app.services import auth

_bearer = HTTPBearer(auto_error=False)

NOT_AUTHENTICATED = "Not authenticated"
SESSION_INVALID = "Your session is invalid or has expired. Sign in again."


def unauthorized(detail: str) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None or credentials.scheme.lower() != "bearer" or not credentials.credentials:
        raise unauthorized(NOT_AUTHENTICATED)
    try:
        claims = decode_access_token(credentials.credentials)
    except InvalidToken:
        raise unauthorized(SESSION_INVALID) from None
    user = auth.get_user(db, claims.user_id)
    # A deleted account, or a token issued before the last sign-out.
    if user is None or user.session_version != claims.session_version:
        raise unauthorized(SESSION_INVALID)
    return user
