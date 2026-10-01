from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.database import get_db
from app.models.user import User
from app.schemas.analysis import OverviewResponse
from app.services import analysis_assessments as analyses

router = APIRouter(tags=["overview"])


@router.get("/overview", response_model=OverviewResponse)
def get_overview(
    db: Session = Depends(get_db),
    owner: User | None = Depends(get_current_user),
) -> OverviewResponse:
    """Aggregates over completed assessments only. Empty database -> zero counts, empty lists."""
    analyses.expire_stale_analyses(db)
    return analyses.overview(db, owner)
