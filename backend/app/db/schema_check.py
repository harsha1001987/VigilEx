"""
Detects a database whose Alembic revision is behind the code.

POST /analyze-video tolerates database errors so a user never loses an
analysis, which means an unmigrated database fails silently: every analysis is
returned with assessment_id null and nothing is saved. This check makes that
state visible at startup and on GET /health/db.
"""

import logging
from pathlib import Path

from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from alembic.script import ScriptDirectory
from sqlalchemy.engine import Engine

log = logging.getLogger("vigilex.schema")

BACKEND_DIR = Path(__file__).resolve().parent.parent.parent


def expected_revisions() -> set[str]:
    config = Config(str(BACKEND_DIR / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    return set(ScriptDirectory.from_config(config).get_heads())


def schema_status(engine: Engine) -> str:
    """'current' when the database is at the code's migration head, otherwise 'behind'."""
    with engine.connect() as conn:
        applied = set(MigrationContext.configure(conn).get_current_heads())
    return "current" if applied == expected_revisions() else "behind"


def warn_if_behind(engine: Engine) -> None:
    try:
        if schema_status(engine) == "behind":
            log.error(
                "Database schema is behind the code; analyses will not be saved. "
                "Run `alembic upgrade head` in backend/."
            )
    except Exception:
        log.exception("Could not check the database schema revision")
