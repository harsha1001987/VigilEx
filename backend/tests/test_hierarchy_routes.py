"""
Tests for sites, areas, tasks hierarchy API endpoints and assessment creation.
"""

import uuid
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.db.database import SessionLocal
from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.models.assessment import Assessment


@pytest.fixture
def test_client():
    client = TestClient(app)
    db = SessionLocal()

    # Seed required hierarchy
    org = db.query(Organization).filter(Organization.name == "Hierarchy Test Org").first()
    if not org:
        org = Organization(id=uuid.uuid4(), name="Hierarchy Test Org")
        db.add(org)
        db.commit()

    site = db.query(Site).filter(Site.organization_id == org.id, Site.name == "Hierarchy Test Site").first()
    if not site:
        site = Site(id=uuid.uuid4(), organization_id=org.id, name="Hierarchy Test Site", location="Building A")
        db.add(site)
        db.commit()

    area = db.query(Area).filter(Area.site_id == site.id, Area.name == "Hierarchy Test Area").first()
    if not area:
        area = Area(id=uuid.uuid4(), site_id=site.id, name="Hierarchy Test Area")
        db.add(area)
        db.commit()

    task = db.query(Task).filter(Task.area_id == area.id, Task.name == "Hierarchy Test Task").first()
    if not task:
        task = Task(id=uuid.uuid4(), area_id=area.id, name="Hierarchy Test Task", description="Lifting box")
        db.add(task)
        db.commit()

    client.test_ids = {
        "org_id": str(org.id),
        "site_id": str(site.id),
        "area_id": str(area.id),
        "task_id": str(task.id),
    }

    yield client
    db.close()


def test_get_sites(test_client):
    """Verify GET /api/v1/sites returns HTTP 200 and list of sites."""
    res = test_client.get("/api/v1/sites")
    assert res.status_code == 200
    data = res.json()
    assert isinstance(data, list)
    assert len(data) >= 1
    found = next((s for s in data if s["id"] == test_client.test_ids["site_id"]), None)
    assert found is not None
    assert found["name"] == "Hierarchy Test Site"


def test_get_areas_by_site(test_client):
    """Verify GET /api/v1/areas?site_id={site_id} returns areas for that site."""
    site_id = test_client.test_ids["site_id"]
    res = test_client.get(f"/api/v1/areas?site_id={site_id}")
    assert res.status_code == 200
    data = res.json()
    assert isinstance(data, list)
    assert len(data) >= 1
    assert data[0]["site_id"] == site_id
    assert data[0]["name"] == "Hierarchy Test Area"


def test_get_tasks_by_area(test_client):
    """Verify GET /api/v1/tasks?area_id={area_id} returns tasks for that area."""
    area_id = test_client.test_ids["area_id"]
    res = test_client.get(f"/api/v1/tasks?area_id={area_id}")
    assert res.status_code == 200
    data = res.json()
    assert isinstance(data, list)
    assert len(data) >= 1
    assert data[0]["area_id"] == area_id
    assert data[0]["name"] == "Hierarchy Test Task"


def test_invalid_site_or_area_returns_empty_or_404(test_client):
    """Verify invalid UUID returns empty list for filter queries or 404 for item queries."""
    fake_id = str(uuid.uuid4())
    res_areas = test_client.get(f"/api/v1/areas?site_id={fake_id}")
    assert res_areas.status_code == 200
    assert res_areas.json() == []

    res_site_item = test_client.get(f"/api/v1/sites/{fake_id}")
    assert res_site_item.status_code == 404


def test_create_assessment_with_hierarchy_ids(test_client):
    """Verify POST /api/v1/assessments creates a draft assessment with selected hierarchy IDs."""
    ids = test_client.test_ids
    payload = {
        "organization_id": ids["org_id"],
        "site_id": ids["site_id"],
        "area_id": ids["area_id"],
        "task_id": ids["task_id"],
        "status": "draft",
        "load_value": 15.0,
        "load_unit": "kg",
        "load_source": "prompted",
        "consent_given": False,
    }
    res = test_client.post("/api/v1/assessments", json=payload)
    assert res.status_code == 201
    data = res.json()
    assert data["organization_id"] == ids["org_id"]
    assert data["site_id"] == ids["site_id"]
    assert data["area_id"] == ids["area_id"]
    assert data["task_id"] == ids["task_id"]
    assert data["status"] == "draft"
