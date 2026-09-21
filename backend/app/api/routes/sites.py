import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.models.organization import Organization
from app.models.site import Site
from app.schemas.site import SiteCreate, SiteRead, SiteUpdate

router = APIRouter(prefix="/sites", tags=["sites"])


def _get_site_or_404(db: Session, site_id: uuid.UUID) -> Site:
    site = db.get(Site, site_id)
    if site is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Site not found")
    return site


def _ensure_organization_exists(db: Session, organization_id: uuid.UUID) -> None:
    if db.get(Organization, organization_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Organization not found")


@router.post("", response_model=SiteRead, status_code=status.HTTP_201_CREATED)
def create_site(payload: SiteCreate, db: Session = Depends(get_db)) -> Site:
    _ensure_organization_exists(db, payload.organization_id)
    site = Site(**payload.model_dump())
    db.add(site)
    db.commit()
    db.refresh(site)
    return site


@router.get("", response_model=list[SiteRead])
def list_sites(organization_id: uuid.UUID | None = None, db: Session = Depends(get_db)) -> list[Site]:
    query = db.query(Site)
    if organization_id is not None:
        query = query.filter(Site.organization_id == organization_id)
    return list(query.order_by(Site.created_at).all())


@router.get("/{site_id}", response_model=SiteRead)
def get_site(site_id: uuid.UUID, db: Session = Depends(get_db)) -> Site:
    return _get_site_or_404(db, site_id)


@router.put("/{site_id}", response_model=SiteRead)
def update_site(site_id: uuid.UUID, payload: SiteUpdate, db: Session = Depends(get_db)) -> Site:
    site = _get_site_or_404(db, site_id)
    _ensure_organization_exists(db, payload.organization_id)
    for field, value in payload.model_dump().items():
        setattr(site, field, value)
    db.commit()
    db.refresh(site)
    return site


@router.delete("/{site_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_site(site_id: uuid.UUID, db: Session = Depends(get_db)) -> None:
    site = _get_site_or_404(db, site_id)
    db.delete(site)
    db.commit()
