"""
Organization scoping for the site / area / task hierarchy.

A user sees and changes only the hierarchy of their own organization. There
is no cross-organization fallback: an organization without sites sees none
(previously it saw every organization's sites, and could edit or delete them).
"""

import uuid

from fastapi import HTTPException, status
from sqlalchemy import false, select
from sqlalchemy.orm import Session

from app.models.area import Area
from app.models.site import Site
from app.models.task import Task
from app.models.user import User


def not_found(what: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"{what} not found")


def own_organization(organization_id: uuid.UUID, user: User) -> None:
    if organization_id != user.organization_id:
        raise not_found("Organization")


def _in_own_organization(user: User):
    # organization_id is NOT NULL; an account without one is denied rather than unscoped.
    return Site.organization_id == user.organization_id if user.organization_id is not None else false()


def sites(user: User):
    return select(Site).where(_in_own_organization(user))


def areas(user: User):
    return select(Area).join(Site, Area.site_id == Site.id).where(_in_own_organization(user))


def tasks(user: User):
    return (
        select(Task)
        .join(Area, Task.area_id == Area.id)
        .join(Site, Area.site_id == Site.id)
        .where(_in_own_organization(user))
    )


def site(db: Session, site_id: uuid.UUID, user: User) -> Site:
    found = db.scalars(sites(user).where(Site.id == site_id)).first()
    if found is None:
        raise not_found("Site")
    return found


def area(db: Session, area_id: uuid.UUID, user: User) -> Area:
    found = db.scalars(areas(user).where(Area.id == area_id)).first()
    if found is None:
        raise not_found("Area")
    return found


def task(db: Session, task_id: uuid.UUID, user: User) -> Task:
    found = db.scalars(tasks(user).where(Task.id == task_id)).first()
    if found is None:
        raise not_found("Task")
    return found
