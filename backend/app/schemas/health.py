from typing import Literal

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str


class DBHealthResponse(BaseModel):
    status: str
    database: str
    # "behind" means migrations are pending and analyses are not being saved.
    migrations: Literal["current", "behind"]
