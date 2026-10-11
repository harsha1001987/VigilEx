import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api import scope
from app.api.deps import get_current_user
from app.db.database import get_db
from app.models.area import Area
from app.models.user import User
from app.schemas.area import AreaCreate, AreaRead, AreaUpdate

router = APIRouter(prefix="/areas", tags=["areas"])


@router.post("", response_model=AreaRead, status_code=status.HTTP_201_CREATED)
def create_area(payload: AreaCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Area:
    scope.site(db, payload.site_id, user)
    area = Area(**payload.model_dump())
    db.add(area)
    db.commit()
    db.refresh(area)
    return area


@router.get("", response_model=list[AreaRead])
def list_areas(
    site_id: uuid.UUID | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[Area]:
    query = scope.areas(user)
    if site_id is not None:
        query = query.where(Area.site_id == site_id)
    return list(db.scalars(query.order_by(Area.created_at)).all())


@router.get("/{area_id}", response_model=AreaRead)
def get_area(area_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Area:
    return scope.area(db, area_id, user)


@router.put("/{area_id}", response_model=AreaRead)
def update_area(
    area_id: uuid.UUID,
    payload: AreaUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Area:
    area = scope.area(db, area_id, user)
    scope.site(db, payload.site_id, user)
    for field, value in payload.model_dump().items():
        setattr(area, field, value)
    db.commit()
    db.refresh(area)
    return area


@router.delete("/{area_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_area(area_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> None:
    db.delete(scope.area(db, area_id, user))
    db.commit()
