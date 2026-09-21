import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class SiteBase(BaseModel):
    name: str
    location: str | None = None


class SiteCreate(SiteBase):
    organization_id: uuid.UUID


class SiteUpdate(SiteBase):
    organization_id: uuid.UUID


class SiteRead(SiteBase):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    organization_id: uuid.UUID
    created_at: datetime
