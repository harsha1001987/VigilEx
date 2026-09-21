import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.models.area import Area
from app.models.site import Site
from app.schemas.area import AreaCreate, AreaRead, AreaUpdate

router = APIRouter(prefix="/areas", tags=["areas"])


def _get_area_or_404(db: Session, area_id: uuid.UUID) -> Area:
    area = db.get(Area, area_id)
    if area is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Area not found")
    return area


def _ensure_site_exists(db: Session, site_id: uuid.UUID) -> None:
    if db.get(Site, site_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Site not found")


@router.post("", response_model=AreaRead, status_code=status.HTTP_201_CREATED)
def create_area(payload: AreaCreate, db: Session = Depends(get_db)) -> Area:
    _ensure_site_exists(db, payload.site_id)
    area = Area(**payload.model_dump())
    db.add(area)
    db.commit()
    db.refresh(area)
    return area


@router.get("", response_model=list[AreaRead])
def list_areas(site_id: uuid.UUID | None = None, db: Session = Depends(get_db)) -> list[Area]:
    query = db.query(Area)
    if site_id is not None:
        query = query.filter(Area.site_id == site_id)
    return list(query.order_by(Area.created_at).all())


@router.get("/{area_id}", response_model=AreaRead)
def get_area(area_id: uuid.UUID, db: Session = Depends(get_db)) -> Area:
    return _get_area_or_404(db, area_id)


@router.put("/{area_id}", response_model=AreaRead)
def update_area(area_id: uuid.UUID, payload: AreaUpdate, db: Session = Depends(get_db)) -> Area:
    area = _get_area_or_404(db, area_id)
    _ensure_site_exists(db, payload.site_id)
    for field, value in payload.model_dump().items():
        setattr(area, field, value)
    db.commit()
    db.refresh(area)
    return area


@router.delete("/{area_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_area(area_id: uuid.UUID, db: Session = Depends(get_db)) -> None:
    area = _get_area_or_404(db, area_id)
    db.delete(area)
    db.commit()
