import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class WorkerCreate(BaseModel):
    organization_id: uuid.UUID
    worker_ref: str = Field(min_length=1, max_length=100, description="Pseudonymous reference; no PII.")


class WorkerRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    organization_id: uuid.UUID
    worker_ref: str
    created_at: datetime
    updated_at: datetime
