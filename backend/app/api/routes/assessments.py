import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.models.area import Area
from app.models.assessment import Assessment
from app.models.organization import Organization
from app.models.site import Site
from app.models.task import Task
from app.models.user import User
from app.models.worker import Worker
from app.schemas.assessment import AssessmentBase, AssessmentCreate, AssessmentResponse, AssessmentUpdate

router = APIRouter(prefix="/assessments", tags=["assessments"])


def _get_assessment_or_404(db: Session, assessment_id: uuid.UUID) -> Assessment:
    assessment = db.get(Assessment, assessment_id)
    if assessment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assessment not found")
    return assessment


def _validate_assessment_references(db: Session, payload: AssessmentBase) -> None:
    """
    Checks, in order:
    1. Every referenced id (organization/site/area/task/worker/assessor) exists -> 404.
    2. The site/area/task chain, and worker/assessor organization, actually
       match the supplied organization_id -> 400. IDs that individually exist
       but belong to a different parent are rejected rather than silently
       accepted.
    """
    organization = db.get(Organization, payload.organization_id)
    if organization is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Organization not found")

    site = db.get(Site, payload.site_id)
    if site is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Site not found")

    area = db.get(Area, payload.area_id)
    if area is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Area not found")

    task = db.get(Task, payload.task_id)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")

    worker = None
    if payload.worker_id is not None:
        worker = db.get(Worker, payload.worker_id)
        if worker is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Worker not found")

    assessor = None
    if payload.assessor_id is not None:
        assessor = db.get(User, payload.assessor_id)
        if assessor is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assessor (user) not found")

    if site.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Site does not belong to the supplied organization",
        )
    if area.site_id != payload.site_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Area does not belong to the supplied site",
        )
    if task.area_id != payload.area_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Task does not belong to the supplied area",
        )
    if worker is not None and worker.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Worker does not belong to the supplied organization",
        )
    if assessor is not None and assessor.organization_id != payload.organization_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Assessor does not belong to the supplied organization",
        )


def _commit(db: Session) -> None:
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Assessment violates a database constraint (status, load_source, or consent fields).",
        )


@router.post("", response_model=AssessmentResponse, status_code=status.HTTP_201_CREATED)
def create_assessment(payload: AssessmentCreate, db: Session = Depends(get_db)) -> Assessment:
    _validate_assessment_references(db, payload)
    assessment = Assessment(**payload.model_dump())
    db.add(assessment)
    _commit(db)
    db.refresh(assessment)
    return assessment


@router.get("", response_model=list[AssessmentResponse])
def list_assessments(
    organization_id: uuid.UUID | None = None,
    site_id: uuid.UUID | None = None,
    area_id: uuid.UUID | None = None,
    task_id: uuid.UUID | None = None,
    worker_id: uuid.UUID | None = None,
    assessor_id: uuid.UUID | None = None,
    status: str | None = None,
    db: Session = Depends(get_db),
) -> list[Assessment]:
    query = db.query(Assessment)
    if organization_id is not None:
        query = query.filter(Assessment.organization_id == organization_id)
    if site_id is not None:
        query = query.filter(Assessment.site_id == site_id)
    if area_id is not None:
        query = query.filter(Assessment.area_id == area_id)
    if task_id is not None:
        query = query.filter(Assessment.task_id == task_id)
    if worker_id is not None:
        query = query.filter(Assessment.worker_id == worker_id)
    if assessor_id is not None:
        query = query.filter(Assessment.assessor_id == assessor_id)
    if status is not None:
        query = query.filter(Assessment.status == status)
    return list(query.order_by(Assessment.created_at).all())


@router.get("/{assessment_id}", response_model=AssessmentResponse)
def get_assessment(assessment_id: uuid.UUID, db: Session = Depends(get_db)) -> Assessment:
    return _get_assessment_or_404(db, assessment_id)


@router.put("/{assessment_id}", response_model=AssessmentResponse)
def update_assessment(
    assessment_id: uuid.UUID, payload: AssessmentUpdate, db: Session = Depends(get_db)
) -> Assessment:
    assessment = _get_assessment_or_404(db, assessment_id)
    _validate_assessment_references(db, payload)
    for field, value in payload.model_dump().items():
        setattr(assessment, field, value)
    _commit(db)
    db.refresh(assessment)
    return assessment


@router.delete("/{assessment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_assessment(assessment_id: uuid.UUID, db: Session = Depends(get_db)) -> None:
    assessment = _get_assessment_or_404(db, assessment_id)
    # assessment_scores / assessment_interventions are ON DELETE CASCADE at the
    # database level (see migration b7c3e1f9a2d4), so a single delete here is
    # sufficient - no app-level cascade logic needed.
    db.delete(assessment)
    db.commit()
