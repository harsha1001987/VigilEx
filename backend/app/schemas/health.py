from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str


class DBHealthResponse(BaseModel):
    status: str
    database: str
