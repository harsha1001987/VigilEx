import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.models.organization import Organization
from app.schemas.organization import OrganizationCreate, OrganizationRead, OrganizationUpdate

router = APIRouter(prefix="/organizations", tags=["organizations"])


def _get_organization_or_404(db: Session, organization_id: uuid.UUID) -> Organization:
    organization = db.get(Organization, organization_id)
    if organization is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Organization not found")
    return organization


@router.post("", response_model=OrganizationRead, status_code=status.HTTP_201_CREATED)
def create_organization(payload: OrganizationCreate, db: Session = Depends(get_db)) -> Organization:
    organization = Organization(**payload.model_dump())
    db.add(organization)
    db.commit()
    db.refresh(organization)
    return organization


@router.get("", response_model=list[OrganizationRead])
def list_organizations(db: Session = Depends(get_db)) -> list[Organization]:
    return list(db.query(Organization).order_by(Organization.created_at).all())


@router.get("/{organization_id}", response_model=OrganizationRead)
def get_organization(organization_id: uuid.UUID, db: Session = Depends(get_db)) -> Organization:
    return _get_organization_or_404(db, organization_id)


@router.put("/{organization_id}", response_model=OrganizationRead)
def update_organization(
    organization_id: uuid.UUID, payload: OrganizationUpdate, db: Session = Depends(get_db)
) -> Organization:
    organization = _get_organization_or_404(db, organization_id)
    for field, value in payload.model_dump().items():
        setattr(organization, field, value)
    db.commit()
    db.refresh(organization)
    return organization


@router.delete("/{organization_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_organization(organization_id: uuid.UUID, db: Session = Depends(get_db)) -> None:
    organization = _get_organization_or_404(db, organization_id)
    db.delete(organization)
    db.commit()
