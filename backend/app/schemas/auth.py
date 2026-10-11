"""Request and response bodies for /api/v1/auth. Passwords are accepted, never returned."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

MIN_PASSWORD_LENGTH = 8
# Argon2 accepts any length; the cap only stops absurd request bodies.
MAX_PASSWORD_LENGTH = 128


def normalize_email(value: str) -> str:
    return value.strip().lower()


class RegisterRequest(BaseModel):
    name: str = Field(min_length=1, max_length=255)
    email: EmailStr = Field(max_length=255)
    password: str = Field(min_length=MIN_PASSWORD_LENGTH, max_length=MAX_PASSWORD_LENGTH)

    @field_validator("name")
    @classmethod
    def _name(cls, value: str) -> str:
        value = " ".join(value.split())
        if not value:
            raise ValueError("Enter your name.")
        return value

    @field_validator("email", mode="before")
    @classmethod
    def _email(cls, value: object) -> object:
        return normalize_email(value) if isinstance(value, str) else value

    @field_validator("password")
    @classmethod
    def _password(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("The password cannot be only spaces.")
        return value


class LoginRequest(BaseModel):
    # Not validated as an email: any malformed address simply fails like a wrong password.
    email: str = Field(min_length=1, max_length=255)
    password: str = Field(min_length=1, max_length=MAX_PASSWORD_LENGTH)

    @field_validator("email")
    @classmethod
    def _email(cls, value: str) -> str:
        return normalize_email(value)


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    email: str
    organization_id: uuid.UUID
    created_at: datetime


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: datetime
    expires_in: int
    user: UserRead
