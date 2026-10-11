"""
/api/v1/auth: create an account, sign in, who am I, sign out.

Sign-in failures are one message for every cause (unknown email, wrong
password, account without a password). Registration necessarily reveals that
an email is taken; both endpoints are rate limited to slow enumeration and
password guessing. Request bodies are never logged.
"""

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import get_settings
from app.core.rate_limit import limiter
from app.core.security import create_access_token
from app.db.database import get_db
from app.models.user import User
from app.schemas.auth import LoginRequest, RegisterRequest, TokenResponse, UserRead
from app.services import auth

router = APIRouter(prefix="/auth", tags=["auth"])

LOGIN_FAILED = "Incorrect email or password."
EMAIL_TAKEN = "An account with this email already exists."
TOO_MANY = "Too many attempts. Try again later."


def _client(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _throttle(key: str, limit: int, window: int) -> None:
    retry_after = limiter.hit(key, limit, window)
    if retry_after is not None:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=TOO_MANY,
            headers={"Retry-After": str(retry_after)},
        )


def _token(user: User) -> TokenResponse:
    issued = create_access_token(user.id, user.session_version)
    return TokenResponse(
        access_token=issued.token,
        expires_at=issued.expires_at,
        expires_in=get_settings().access_token_expire_minutes * 60,
        user=UserRead.model_validate(user),
    )


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, request: Request, db: Session = Depends(get_db)) -> TokenResponse:
    settings = get_settings()
    _throttle(f"register:{_client(request)}", settings.register_rate_limit, settings.register_rate_window_seconds)
    try:
        user = auth.register(db, name=payload.name, email=payload.email, password=payload.password)
    except auth.EmailTaken:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=EMAIL_TAKEN) from None
    return _token(user)


@router.post("/login", response_model=TokenResponse)
def login(payload: LoginRequest, request: Request, db: Session = Depends(get_db)) -> TokenResponse:
    settings = get_settings()
    limit, window = settings.login_rate_limit, settings.login_rate_window_seconds
    # Per address (one client guessing many accounts) and per account (many clients guessing one).
    _throttle(f"login-ip:{_client(request)}", limit, window)
    _throttle(f"login-email:{payload.email}", limit, window)
    user = auth.authenticate(db, payload.email, payload.password)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=LOGIN_FAILED,
            headers={"WWW-Authenticate": "Bearer"},
        )
    # A successful sign-in clears the account's counter so its owner is not locked out by earlier typos.
    limiter.clear(f"login-email:{payload.email}")
    return _token(user)


@router.get("/me", response_model=UserRead)
def me(user: User = Depends(get_current_user)) -> User:
    return user


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> Response:
    """Revokes this account's access tokens on every device."""
    auth.sign_out_everywhere(db, user)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
