import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api import scope
from app.api.deps import get_current_user
from app.db.database import get_db
from app.models.organization import Organization
from app.models.user import User
from app.schemas.organization import OrganizationCreate, OrganizationRead, OrganizationUpdate

router = APIRouter(prefix="/organizations", tags=["organizations"])

# Each account belongs to exactly one organization, created with the account.
NO_NEW_ORGANIZATIONS = "Organizations are created with accounts."
NO_DELETE = "Your organization cannot be deleted while it has accounts."


def _own_organization(db: Session, organization_id: uuid.UUID, user: User) -> Organization:
    scope.own_organization(organization_id, user)
    organization = db.get(Organization, organization_id)
    if organization is None:
        raise scope.not_found("Organization")
    return organization


@router.post("", response_model=OrganizationRead, status_code=status.HTTP_201_CREATED)
def create_organization(payload: OrganizationCreate, user: User = Depends(get_current_user)) -> Organization:
    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=NO_NEW_ORGANIZATIONS)


@router.get("", response_model=list[OrganizationRead])
def list_organizations(db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> list[Organization]:
    return [_own_organization(db, user.organization_id, user)]


@router.get("/{organization_id}", response_model=OrganizationRead)
def get_organization(
    organization_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)
) -> Organization:
    return _own_organization(db, organization_id, user)


@router.put("/{organization_id}", response_model=OrganizationRead)
def update_organization(
    organization_id: uuid.UUID,
    payload: OrganizationUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Organization:
    organization = _own_organization(db, organization_id, user)
    for field, value in payload.model_dump().items():
        setattr(organization, field, value)
    db.commit()
    db.refresh(organization)
    return organization


@router.delete("/{organization_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_organization(
    organization_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)
) -> None:
    _own_organization(db, organization_id, user)
    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=NO_DELETE)
