"""
Request dependencies shared by routes.

VigilEx has no authentication yet, so there is no current user and every
assessment is visible. Routes still take `owner` from here and scope their
queries with it (services.analysis_assessments.owned), so enabling
authentication means implementing this one function.
"""

from app.models.user import User


def get_current_user() -> User | None:
    return None
