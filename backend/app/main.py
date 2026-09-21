from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import areas, assessments, health, organizations, sites, tasks
from app.core.config import get_settings

settings = get_settings()

app = FastAPI(title=settings.app_name)

# Allowed origins are configured via CORS_ORIGINS in .env
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api/v1")
app.include_router(organizations.router, prefix="/api/v1")
app.include_router(sites.router, prefix="/api/v1")
app.include_router(areas.router, prefix="/api/v1")
app.include_router(tasks.router, prefix="/api/v1")
app.include_router(assessments.router, prefix="/api/v1")


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "VigilEx API"}
