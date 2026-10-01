"""
Test setup.

1. Import paths: the suite mixes `from app...` and `from backend.app...`
   imports, so the backend directory and the repository root are importable.
2. Database: tests never touch the development database. Before any app
   module is imported, DATABASE_URL is pointed at "<dev database>_test" on the
   same PostgreSQL server. The session fixture recreates that database and
   builds it by running the Alembic migrations (so the migrations themselves
   are exercised on a fresh database); every table is emptied after each test.
3. Reports are written to a temporary directory.
"""

import os
import sys
import tempfile
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = BACKEND_DIR.parent

for path in (BACKEND_DIR, REPO_ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from dotenv import dotenv_values  # noqa: E402
from sqlalchemy.engine import make_url  # noqa: E402

_dev_url = make_url(os.environ.get("DATABASE_URL") or dotenv_values(BACKEND_DIR / ".env")["DATABASE_URL"])
TEST_DATABASE = f"{_dev_url.database}_test"
TEST_DATABASE_URL = _dev_url.set(database=TEST_DATABASE)

# Must happen before app.core.config is imported anywhere (settings are cached).
os.environ["DATABASE_URL"] = TEST_DATABASE_URL.render_as_string(hide_password=False)
os.environ["REPORT_DIR"] = tempfile.mkdtemp(prefix="vigilex-reports-")

import pytest  # noqa: E402
from sqlalchemy import create_engine, text  # noqa: E402

TABLES = (
    "assessment_interventions", "assessment_scores", "assessments",
    "workers", "users", "tasks", "areas", "sites", "organizations",
)


def _recreate_test_database() -> None:
    assert TEST_DATABASE.endswith("_test"), "refusing to drop a database that is not a test database"
    admin = create_engine(_dev_url.set(database="postgres"), isolation_level="AUTOCOMMIT")
    with admin.connect() as conn:
        conn.execute(text(f'DROP DATABASE IF EXISTS "{TEST_DATABASE}" WITH (FORCE)'))
        conn.execute(text(f'CREATE DATABASE "{TEST_DATABASE}"'))
    admin.dispose()


def _alembic(command_name: str, revision: str) -> None:
    from alembic import command
    from alembic.config import Config

    config = Config(str(BACKEND_DIR / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    config.attributes["database_url"] = os.environ["DATABASE_URL"]
    getattr(command, command_name)(config, revision)


@pytest.fixture(scope="session", autouse=True)
def migrated_database():
    _recreate_test_database()
    _alembic("upgrade", "head")
    yield TEST_DATABASE_URL


@pytest.fixture(autouse=True)
def clean_tables(migrated_database):
    yield
    from app.db.database import engine

    with engine.begin() as conn:
        conn.execute(text(f"TRUNCATE {', '.join(TABLES)} CASCADE"))


@pytest.fixture
def alembic_run():
    """Runs an Alembic command against the test database."""
    return _alembic


@pytest.fixture
def db():
    from app.db.database import SessionLocal

    session = SessionLocal()
    yield session
    session.close()


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    return TestClient(app)
