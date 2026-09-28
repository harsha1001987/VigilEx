from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "VigilEx API"
    database_url: str
    cors_origins: str = "http://localhost:8000,http://127.0.0.1:8000"
    upload_dir: str = "uploads"
    max_upload_size_bytes: int = 100 * 1024 * 1024  # 100 MB

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
