import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api import scope
from app.api.deps import get_current_user
from app.db.database import get_db
from app.models.task import Task
from app.models.user import User
from app.schemas.task import TaskCreate, TaskRead, TaskUpdate

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
def create_task(payload: TaskCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Task:
    scope.area(db, payload.area_id, user)
    task = Task(**payload.model_dump())
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


@router.get("", response_model=list[TaskRead])
def list_tasks(
    area_id: uuid.UUID | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[Task]:
    query = scope.tasks(user)
    if area_id is not None:
        query = query.where(Task.area_id == area_id)
    return list(db.scalars(query.order_by(Task.created_at)).all())


@router.get("/{task_id}", response_model=TaskRead)
def get_task(task_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> Task:
    return scope.task(db, task_id, user)


@router.put("/{task_id}", response_model=TaskRead)
def update_task(
    task_id: uuid.UUID,
    payload: TaskUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Task:
    task = scope.task(db, task_id, user)
    scope.area(db, payload.area_id, user)
    for field, value in payload.model_dump().items():
        setattr(task, field, value)
    db.commit()
    db.refresh(task)
    return task


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_task(task_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)) -> None:
    db.delete(scope.task(db, task_id, user))
    db.commit()
