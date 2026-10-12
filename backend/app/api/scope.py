"""
Organization scoping for the site / area / task hierarchy.

A user sees and changes the hierarchy of their own organization. When an
organization has no custom sites yet, default seeded sites are visible so
the user can immediately run posture assessments.
"""

import uuid

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.area import Area
from app.models.site import Site
from app.models.task import Task
from app.models.user import User


def not_found(what: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"{what} not found")


def own_organization(organization_id: uuid.UUID, user: User) -> None:
    if user.organization_id is not None and organization_id != user.organization_id:
        raise not_found("Organization")


def sites(user: User):
    if user.organization_id is None:
        return select(Site)
    has_own_sites = select(Site).where(Site.organization_id == user.organization_id).exists()
    return select(Site).where((Site.organization_id == user.organization_id) | (~has_own_sites))


def areas(user: User):
    if user.organization_id is None:
        return select(Area)
    has_own_sites = select(Site).where(Site.organization_id == user.organization_id).exists()
    return select(Area).join(Site, Area.site_id == Site.id).where(
        (Site.organization_id == user.organization_id) | (~has_own_sites)
    )


def tasks(user: User):
    if user.organization_id is None:
        return select(Task)
    has_own_sites = select(Site).where(Site.organization_id == user.organization_id).exists()
    return (
        select(Task)
        .join(Area, Task.area_id == Area.id)
        .join(Site, Area.site_id == Site.id)
        .where((Site.organization_id == user.organization_id) | (~has_own_sites))
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
