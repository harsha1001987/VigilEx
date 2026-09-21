from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.health import DBHealthResponse, HealthResponse

router = APIRouter(prefix="/health", tags=["health"])


@router.get("", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok")


@router.get("/db", response_model=DBHealthResponse)
def health_db(db: Session = Depends(get_db)) -> DBHealthResponse:
    db.execute(text("SELECT 1"))
    return DBHealthResponse(status="ok", database="connected")
