import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class AreaBase(BaseModel):
    name: str


class AreaCreate(AreaBase):
    site_id: uuid.UUID


class AreaUpdate(AreaBase):
    site_id: uuid.UUID


class AreaRead(AreaBase):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    site_id: uuid.UUID
    created_at: datetime
