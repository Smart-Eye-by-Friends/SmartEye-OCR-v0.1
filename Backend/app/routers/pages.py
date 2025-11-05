from __future__ import annotations

import asyncio
import os
from pathlib import Path
from typing import Dict, List, Optional, Union
from uuid import uuid4

import cv2
from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile, status
from loguru import logger
from sqlalchemy import func
from sqlalchemy.orm import Session

from .. import crud, schemas
from ..database import get_db
from ..models import AnalysisStatusEnum, AnalysisModeEnum, Page, Project
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
PUBLIC_UPLOAD_ROOT = Path("uploads")

DEFAULT_PROJECT_NAME = "temp"
DEFAULT_DOC_TYPE_ID = 1
DEFAULT_ANALYSIS_MODE = AnalysisModeEnum.AUTO
DEFAULT_USER_ID = 1
ANCHOR_CLASS_NAMES = {"question number", "question type"}


def _page_to_response(page: Page) -> schemas.PageResponse:
    return schemas.PageResponse.model_validate(page)


def _create_project_for_upload(db: Session) -> Project:
    """업로드 세션용 신규 프로젝트 생성"""
    project = crud.create_project(
        db=db,
        project=schemas.ProjectCreate(
            project_name=DEFAULT_PROJECT_NAME,
            doc_type_id=DEFAULT_DOC_TYPE_ID,
            analysis_mode=DEFAULT_ANALYSIS_MODE,
        ),
        user_id=DEFAULT_USER_ID,
    )
    logger.info("업로드용 프로젝트 생성 - ProjectID: %s", project.project_id)
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
    project_id: Optional[int] = Form(None, description="업로드 대상 프로젝트 ID (생략 시 신규 생성)"),
    db: Session = Depends(get_db),
) -> Union[schemas.PageResponse, schemas.MultiPageCreateResponse]:
    """
    페이지 업로드 (이미지 또는 PDF)

    동작 방식:
    - 최초 업로드: project_id 없이 호출하면 서버가 temp 프로젝트를 생성
    - 이후 업로드: 동일한 project_id를 전달하면 해당 프로젝트에 페이지를 추가
    - page_number는 프로젝트 내에서 자동 증가

    처리 방식:
    - 이미지 업로드: 단일 페이지 생성
    - PDF 업로드: 다중 페이지 자동 생성
    """
    if project_id is not None:
        project = crud.get_project(db, project_id)
        if not project:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="프로젝트를 찾을 수 없습니다.",
            )
    else:
        project = _create_project_for_upload(db)
        project_id = project.project_id

    # 1. 다음 페이지 번호 자동 계산
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

        suffix = Path(file.filename or "").suffix or ".png"
        filename = f"page_{page_number}_{uuid4().hex}{suffix}"
        project_dir = UPLOAD_DIR / str(project_id)
        project_dir.mkdir(parents=True, exist_ok=True)
        file_path = project_dir / filename

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

        public_path = PUBLIC_UPLOAD_ROOT / str(project_id) / filename
        stored_path = str(public_path).replace("\\", "/")

        page_create = schemas.PageCreate(
            project_id=project_id,
            page_number=page_number,
            image_path=stored_path,
            image_width=width,
            image_height=height,
        )
        page = crud.create_page(db, page_create)

        logger.info(f"이미지 업로드 완료 - ProjectID: {project_id}, PageID: {page.page_id}")
        return _page_to_response(page)


@router.get(
    "/{page_id}",
    response_model=schemas.PageResponse,
)
def get_page_detail(
    page_id: int,
    include_layout: bool = Query(False, description="레이아웃 요소 포함 여부"),  # noqa: ARG001
    include_text: bool = Query(False, description="텍스트 콘텐츠 포함 여부"),  # noqa: ARG001
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


@router.get(
    "/{page_id}/stats",
    response_model=schemas.PageStatsResponse,
    summary="페이지 통계 조회",
)
def get_page_stats(
    page_id: int,
    db: Session = Depends(get_db),
) -> schemas.PageStatsResponse:
    """
    페이지별 레이아웃 통계를 계산하여 반환합니다.

    - 총 레이아웃 요소 수
    - 앵커 요소(question_number, question_type) 수
    - 클래스별 요소 분포
    - 클래스별 평균 신뢰도
    - 처리 시간
    """
    page = crud.get_page_with_elements(db, page_id)
    if not page:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="페이지를 찾을 수 없습니다.",
        )

    distribution: Dict[str, int] = {}
    confidence_sums: Dict[str, float] = {}
    confidence_counts: Dict[str, int] = {}

    for element in page.layout_elements:
        class_name = element.class_name or "unknown"
        distribution[class_name] = distribution.get(class_name, 0) + 1

        if element.confidence is not None:
            confidence_sums[class_name] = confidence_sums.get(class_name, 0.0) + float(element.confidence)
            confidence_counts[class_name] = confidence_counts.get(class_name, 0) + 1

    confidence_scores: Dict[str, float] = {}
    for class_name, total in confidence_sums.items():
        count = confidence_counts.get(class_name, 0)
        if count:
            confidence_scores[class_name] = total / count

    anchor_count = sum(
        1 for element in page.layout_elements if element.class_name in ANCHOR_CLASS_NAMES
    )

    return schemas.PageStatsResponse(
        page_id=page.page_id,
        project_id=page.project_id,
        total_elements=len(page.layout_elements),
        anchor_element_count=anchor_count,
        processing_time=page.processing_time,
        class_distribution=distribution,
        confidence_scores=confidence_scores,
    )
