from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

MIN_SECRET_LENGTH = 32


BACKEND_DIR = Path(__file__).resolve().parent.parent.parent


class Settings(BaseSettings):
    # Absolute path so the settings load whatever the working directory is.
    model_config = SettingsConfigDict(env_file=BACKEND_DIR / ".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "VigilEx API"
    database_url: str
    cors_origins: str = "http://localhost:8000,http://127.0.0.1:8000"
    upload_dir: str = "uploads"
    # Generated PDF reports, one per assessment, served only through the API.
    report_dir: str = "reports"
    max_upload_size_bytes: int = 100 * 1024 * 1024  # 100 MB

    # ---- Authentication
    # HMAC key that signs access tokens. Set AUTH_SECRET_KEY in the environment
    # (at least 32 random bytes, e.g. `python -c "import secrets; print(secrets.token_urlsafe(48))"`);
    # the API refuses to start without one. Never commit a real value.
    auth_secret_key: SecretStr | None = None
    auth_token_issuer: str = "vigilex-api"
    auth_token_audience: str = "vigilex-app"
    # One working shift. There are no refresh tokens: the app signs in again after this.
    access_token_expire_minutes: int = Field(default=12 * 60, ge=1, le=7 * 24 * 60)
    # Failed or attempted sign-ins per client address and per email within the window.
    login_rate_limit: int = Field(default=10, ge=1)
    login_rate_window_seconds: int = Field(default=15 * 60, ge=1)
    # Account creations per client address within the window.
    register_rate_limit: int = Field(default=5, ge=1)
    register_rate_window_seconds: int = Field(default=60 * 60, ge=1)

    @property
    def signing_key(self) -> str:
        """The token signing key; raises when it is missing or too short to be safe."""
        key = self.auth_secret_key.get_secret_value() if self.auth_secret_key else ""
        if len(key) < MIN_SECRET_LENGTH:
            raise RuntimeError(
                f"AUTH_SECRET_KEY must be set to a random value of at least {MIN_SECRET_LENGTH} characters."
            )
        return key

    @property
    def cors_origins_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def upload_path(self) -> Path:
        path = Path(self.upload_dir)
        if not path.is_absolute():
            # Place uploads relative to backend root directory
            base_dir = Path(__file__).resolve().parent.parent.parent
            path = base_dir / self.upload_dir
        path.mkdir(parents=True, exist_ok=True)
        return path


@lru_cache
def get_settings() -> Settings:
    return Settings()
