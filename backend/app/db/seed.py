"""
Idempotent seed script for VigilEx development database.
Ensures default Organization, Site, Area, and Task exist for V1 testing.
"""

from __future__ import annotations

import uuid
from sqlalchemy.orm import Session

from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.db.database import SessionLocal


def seed_development_data(db: Session) -> None:
    """Idempotently seeds V1 development data."""
    # Organization: Extrive Innovations
    org = db.query(Organization).filter(Organization.name == "Extrive Innovations").first()
    if not org:
        org = Organization(id=uuid.uuid4(), name="Extrive Innovations")
        db.add(org)
        db.commit()
        db.refresh(org)

    # Site: Assembly Plant
    site = db.query(Site).filter(Site.organization_id == org.id, Site.name == "Assembly Plant").first()
    if not site:
        site = Site(
            id=uuid.uuid4(),
            organization_id=org.id,
            name="Assembly Plant",
            location="Chennai",
        )
        db.add(site)
        db.commit()
        db.refresh(site)

    # Area: Line 03
    area = db.query(Area).filter(Area.site_id == site.id, Area.name == "Line 03").first()
    if not area:
        area = Area(
            id=uuid.uuid4(),
            site_id=site.id,
            name="Line 03",
        )
        db.add(area)
        db.commit()
        db.refresh(area)

    # Task: Box Lifting
    task = db.query(Task).filter(Task.area_id == area.id, Task.name == "Box Lifting").first()
    if not task:
        task = Task(
            id=uuid.uuid4(),
            area_id=area.id,
            name="Box Lifting",
            description="Manual lifting from pallet to conveyor",
        )
        db.add(task)
        db.commit()
        db.refresh(task)


if __name__ == "__main__":
    db = SessionLocal()
    try:
        seed_development_data(db)
        print("Development data seeded successfully.")
    finally:
        db.close()
