import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class TaskBase(BaseModel):
    name: str
    description: str | None = None


class TaskCreate(TaskBase):
    area_id: uuid.UUID


class TaskUpdate(TaskBase):
    area_id: uuid.UUID


class TaskRead(TaskBase):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    area_id: uuid.UUID
    created_at: datetime
