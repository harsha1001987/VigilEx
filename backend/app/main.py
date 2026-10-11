from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import areas, assessments, auth, cv_analysis, health, organizations, overview, sites, tasks
from app.core.config import get_settings
from app.db.database import engine
from app.db.schema_check import warn_if_behind

settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings.signing_key  # Refuse to start without a usable token signing key.
    warn_if_behind(engine)
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

# Allowed origins are configured via CORS_ORIGINS in .env
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api/v1")
app.include_router(auth.router, prefix="/api/v1")
app.include_router(organizations.router, prefix="/api/v1")
app.include_router(sites.router, prefix="/api/v1")
app.include_router(areas.router, prefix="/api/v1")
app.include_router(tasks.router, prefix="/api/v1")
app.include_router(assessments.router, prefix="/api/v1")
app.include_router(cv_analysis.router, prefix="/api/v1")
app.include_router(overview.router, prefix="/api/v1")


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "VigilEx API"}
