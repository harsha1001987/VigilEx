import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api import scope
from app.api.deps import get_current_user
from app.db.database import get_db
from app.models.site import Site
from app.models.user import User
from app.schemas.site import SiteCreate, SiteRead, SiteUpdate

router = APIRouter(prefix="/sites", tags=["sites"])


@router.post("", response_model=SiteRead, status_code=status.HTTP_201_CREATED)
def create_site(payload: SiteCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Site:
    scope.own_organization(payload.organization_id, user)
    site = Site(**payload.model_dump())
    db.add(site)
    db.commit()
    db.refresh(site)
    return site


@router.get("", response_model=list[SiteRead])
def list_sites(
    organization_id: uuid.UUID | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[Site]:
    query = scope.sites(user)
    if organization_id is not None:
        query = query.where(Site.organization_id == organization_id)
    return list(db.scalars(query.order_by(Site.created_at)).all())


@router.get("/{site_id}", response_model=SiteRead)
def get_site(site_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Site:
    return scope.site(db, site_id, user)


@router.put("/{site_id}", response_model=SiteRead)
def update_site(
    site_id: uuid.UUID,
    payload: SiteUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Site:
    site = scope.site(db, site_id, user)
    scope.own_organization(payload.organization_id, user)
    for field, value in payload.model_dump().items():
        setattr(site, field, value)
    db.commit()
    db.refresh(site)
    return site


@router.delete("/{site_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_site(site_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> None:
    db.delete(scope.site(db, site_id, user))
    db.commit()
