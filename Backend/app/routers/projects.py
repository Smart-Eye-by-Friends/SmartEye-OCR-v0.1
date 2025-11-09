from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy.orm import Session

from .. import crud, schemas
from ..database import get_db
from ..models import Page, Project

router = APIRouter(
    prefix="/api/projects",
    tags=["Projects"],
)


class ProjectCreateRequest(schemas.ProjectCreate):
    """프로젝트 생성 요청 스키마 (user_id 포함)"""

    user_id: Optional[int] = 1  # 기본값 1 (테스트 사용자)


def _project_to_response(project: Project) -> schemas.ProjectResponse:
    return schemas.ProjectResponse.model_validate(project)


def _page_to_response(page: Page) -> schemas.PageResponse:
    return schemas.PageResponse.model_validate(page)


@router.post(
    "",
    response_model=schemas.ProjectResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_project_endpoint(
    payload: ProjectCreateRequest,
    db: Session = Depends(get_db),
) -> schemas.ProjectResponse:
    """
    프로젝트 생성 API
    
    - **project_name**: 프로젝트 이름
    - **doc_type_id**: 문서 타입 ID (1: worksheet, 2: document)
    - **analysis_mode**: 분석 모드 (auto/manual/hybrid, 기본값: auto)
    - **user_id**: 사용자 ID (선택, 기본값: 1)
    """
    project = crud.create_project(
        db=db,
        project=schemas.ProjectCreate(
            project_name=payload.project_name,
            doc_type_id=payload.doc_type_id,
            analysis_mode=payload.analysis_mode,
        ),
        user_id=payload.user_id or 1,  # user_id가 None이면 1 사용
    )
    return _project_to_response(project)


@router.get("", response_model=List[schemas.ProjectResponse])
def list_projects(
    db: Session = Depends(get_db),
    user_id: Optional[int] = Query(default=None, description="특정 사용자 ID로 필터링"),
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=100, ge=1, le=1000),
) -> List[schemas.ProjectResponse]:
    query = db.query(Project).order_by(Project.created_at.desc())
    if user_id is not None:
        query = query.filter(Project.user_id == user_id)
    projects = query.offset(skip).limit(limit).all()
    return [_project_to_response(project) for project in projects]


@router.get(
    "/{project_id}",
    response_model=schemas.ProjectWithPagesResponse,
)
def get_project_detail(
    project_id: int,
    db: Session = Depends(get_db),
) -> schemas.ProjectWithPagesResponse:
    project = crud.get_project_with_pages(db, project_id)
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="프로젝트를 찾을 수 없습니다.")
    project_response = _project_to_response(project)
    page_responses = [_page_to_response(page) for page in project.pages]
    return schemas.ProjectWithPagesResponse(
        **project_response.model_dump(),
        pages=page_responses,
    )


@router.patch(
    "/{project_id}",
    response_model=schemas.ProjectResponse,
)
def update_project_endpoint(
    project_id: int,
    payload: schemas.ProjectUpdate,
    db: Session = Depends(get_db),
) -> schemas.ProjectResponse:
    project = crud.update_project(db, project_id, payload)
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="프로젝트를 찾을 수 없습니다.")
    return _project_to_response(project)


@router.delete(
    "/{project_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def delete_project_endpoint(
    project_id: int,
    db: Session = Depends(get_db),
) -> Response:
    success = crud.delete_project(db, project_id)
    if not success:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="프로젝트를 찾을 수 없습니다.")
    return Response(status_code=status.HTTP_204_NO_CONTENT)
