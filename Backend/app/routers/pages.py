from __future__ import annotations

import asyncio
import os
from pathlib import Path
from typing import List, Optional, Union
from uuid import uuid4

import cv2
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from loguru import logger
from sqlalchemy import func
from sqlalchemy.orm import Session

from .. import crud, schemas
from ..database import get_db
from ..models import AnalysisStatusEnum, AnalysisModeEnum, Page, Project, ProjectStatusEnum
from ..services.pdf_processor import pdf_processor
from ..services.text_version_service import (
    get_current_page_text,
    save_user_edited_version,
)

router = APIRouter(
    prefix="/api/pages",
    tags=["Pages"],
)

UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "uploads")).resolve()
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)


def _page_to_response(page: Page) -> schemas.PageResponse:
    return schemas.PageResponse.model_validate(page)


def _get_or_create_default_project(db: Session) -> Project:
    """
    기본 프로젝트 조회 또는 생성
    
    TODO: 향후 사용자 인증 구현 시 실제 user_id 사용
    """
    # 첫 번째 프로젝트 조회
    project = db.query(Project).first()
    
    if not project:
        # 기본 프로젝트 생성
        project = Project(
            user_id=1,  # TODO: 인증 구현 시 실제 user_id 사용
            doc_type_id=1,  # worksheet 타입 (기본값)
            project_name="기본 프로젝트",
            analysis_mode=AnalysisModeEnum.AUTO,
            status=ProjectStatusEnum.IN_PROGRESS,
            total_pages=0
        )
        db.add(project)
        db.commit()
        db.refresh(project)
        logger.info(f"기본 프로젝트 생성 - ProjectID: {project.project_id}")
    
    return project


def _calculate_next_page_number(db: Session, project_id: int) -> int:
    """
    다음 페이지 번호 자동 계산
    
    같은 프로젝트 내에서 가장 큰 page_number를 찾아서 +1 반환
    """
    max_page = db.query(func.max(Page.page_number))\
                 .filter(Page.project_id == project_id)\
                 .scalar()
    
    next_page_number = (max_page or 0) + 1
    logger.debug(f"다음 페이지 번호 계산 - ProjectID: {project_id}, NextPage: {next_page_number}")
    
    return next_page_number


@router.post(
    "/upload",
    response_model=Union[schemas.PageResponse, schemas.MultiPageCreateResponse],
    status_code=status.HTTP_201_CREATED,
)
async def upload_page(
    file: UploadFile = File(..., description="페이지 이미지 또는 PDF 파일"),
    db: Session = Depends(get_db),
) -> Union[schemas.PageResponse, schemas.MultiPageCreateResponse]:
    """
    페이지 업로드 (이미지 또는 PDF)
    
    프론트엔드는 file만 전송하면 됨:
    - project_id: 백엔드에서 기본 프로젝트 사용 (자동)
    - page_number: 백엔드에서 자동 계산 (순차 증가)
    
    처리 방식:
    - 이미지 업로드: 단일 페이지 생성
    - PDF 업로드: 다중 페이지 자동 생성
    """
    
    # 1. 기본 프로젝트 조회 또는 생성
    project = _get_or_create_default_project(db)
    project_id = project.project_id
    
    # 2. 다음 페이지 번호 자동 계산
    page_number = _calculate_next_page_number(db, project_id)

    # 2. 다음 페이지 번호 자동 계산
    page_number = _calculate_next_page_number(db, project_id)

    # 3. PDF 업로드 분기 처리
    if file.content_type == "application/pdf":
        logger.info(f"PDF 업로드 시작 - ProjectID: {project_id}, StartPage: {page_number}")

        # PDF 바이트 읽기
        pdf_bytes = await file.read()

        try:
            # PDF → 이미지 변환 (page_number부터 시작)
            converted_pages = pdf_processor.convert_pdf_to_images(
                pdf_bytes=pdf_bytes,
                project_id=project_id,
                start_page_number=page_number
            )
            logger.info(f"PDF 변환 완료 - {len(converted_pages)}개 페이지")

            # 비동기 병렬 페이지 생성 (Semaphore로 동시성 제한)
            semaphore = asyncio.Semaphore(5)
            created_pages = []

            async def save_page(page_info: dict):
                async with semaphore:
                    page_create = schemas.PageCreate(
                        project_id=project_id,
                        page_number=page_info['page_number'],
                        image_path=page_info['image_path'],
                        image_width=page_info['width'],
                        image_height=page_info['height']
                    )
                    page = crud.create_page(db, page_create)
                    created_pages.append(page)
                    logger.debug(f"페이지 {page_info['page_number']} 저장 완료")

            # 병렬 저장 실행
            await asyncio.gather(*(save_page(info) for info in converted_pages))

            logger.info(f"PDF 업로드 완료 - ProjectID: {project_id}, {len(created_pages)}개 페이지 생성")

            return schemas.MultiPageCreateResponse(
                project_id=project_id,
                total_created=len(created_pages),
                source_type="pdf",
                pages=[_page_to_response(p) for p in created_pages],
            )

        except ValueError as ve:
            logger.error(f"PDF 처리 실패: {str(ve)}")
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"PDF 처리 실패: {str(ve)}"
            ) from ve
        except Exception as exc:
            logger.error(f"PDF 업로드 중 오류: {str(exc)}")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"PDF 업로드 실패: {str(exc)}"
            ) from exc

    # 4. 단일 이미지 업로드 처리
    else:
        logger.info(f"이미지 업로드 - ProjectID: {project_id}, PageNumber: {page_number}")

        suffix = Path(file.filename).suffix or ".png"
        filename = f"project_{project_id}_page_{page_number}_{uuid4().hex}{suffix}"
        file_path = UPLOAD_DIR / filename

        content = await file.read()
        file_path.write_bytes(content)

        try:
            image = cv2.imread(str(file_path))
            if image is None:
                raise ValueError("이미지를 읽을 수 없습니다.")
            height, width = image.shape[:2]
        except Exception as exc:  # pylint: disable=broad-except
            file_path.unlink(missing_ok=True)
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"이미지 처리 실패: {exc}") from exc

        try:
            relative_path = file_path.relative_to(Path.cwd())
            stored_path = str(relative_path)
        except ValueError:
            stored_path = str(file_path)

        page_create = schemas.PageCreate(
            project_id=project_id,
            page_number=page_number,
            image_path=stored_path,
            image_width=width,
            image_height=height,
        )
        page = crud.create_page(db, page_create)

        logger.info(f"이미지 업로드 완료 - PageID: {page.page_id}")
        return _page_to_response(page)


@router.get(
    "/{page_id}",
    response_model=schemas.PageResponse,
)
def get_page_detail(
    page_id: int,
    db: Session = Depends(get_db),
) -> schemas.PageResponse:
    page = crud.get_page(db, page_id)
    if not page:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="페이지를 찾을 수 없습니다.")
    return _page_to_response(page)


@router.get(
    "/project/{project_id}",
    response_model=List[schemas.PageResponse],
)
def list_project_pages(
    project_id: int,
    db: Session = Depends(get_db),
    include_error: bool = False,
) -> List[schemas.PageResponse]:
    pages = crud.get_pages_by_project(db, project_id)
    if not include_error:
        pages = [page for page in pages if page.analysis_status != AnalysisStatusEnum.ERROR]
    return [_page_to_response(page) for page in pages]


@router.get(
    "/{page_id}/text",
    response_model=schemas.PageTextResponse,
    summary="현재 페이지 텍스트 조회",
)
def get_page_text(
    page_id: int,
    db: Session = Depends(get_db),
) -> schemas.PageTextResponse:
    """
    is_current=True인 최신 텍스트 버전을 반환합니다.
    """
    version_data = get_current_page_text(db, page_id)
    if not version_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 페이지의 텍스트 버전을 찾을 수 없습니다.",
        )
    return schemas.PageTextResponse.model_validate(version_data)


@router.post(
    "/{page_id}/text",
    response_model=schemas.PageTextResponse,
    summary="사용자 수정 텍스트 저장",
)
def save_page_text(
    page_id: int,
    payload: schemas.PageTextUpdate,
    db: Session = Depends(get_db),
) -> schemas.PageTextResponse:
    """
    사용자 편집 내용을 새 텍스트 버전으로 저장합니다.
    """
    try:
        version_data = save_user_edited_version(
            db,
            page_id,
            payload.content,
            user_id=payload.user_id,
        )
        return schemas.PageTextResponse.model_validate(version_data)
    except ValueError as value_error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(value_error),
        ) from value_error
    except Exception as error:  # pylint: disable=broad-except
        logger.error("페이지 텍스트 저장 실패: page_id=%s / error=%s", page_id, error, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="페이지 텍스트 저장 중 오류가 발생했습니다.",
        ) from error
