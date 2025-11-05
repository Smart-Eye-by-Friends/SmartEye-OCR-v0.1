"""
Project Batch Analysis Service
=============================

실제 데이터베이스(DB) 기반으로 프로젝트 내 페이지들을 순차적으로 분석하고
정렬(Question Grouping) 및 포맷팅(Text Version 생성)까지 수행합니다.

파이프라인 (페이지 단위)
1. 이미지 로드
2. AnalysisService로 레이아웃 → OCR → (선택) AI 설명 생성
3. sorter.py를 이용한 정렬 후 question_groups / question_elements 저장
4. TextFormatter로 자동 포맷팅 → text_versions에 최신 버전 기록

결과는 페이지별 요약 정보와 함께 프로젝트 상태를 갱신합니다.
"""

from __future__ import annotations

import asyncio
import os
import time
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional

import cv2
import numpy as np
from loguru import logger
from sqlalchemy.orm import Session, selectinload

from ..models import LayoutElement, Page, Project
from .analysis_service import AnalysisService
from .formatter import TextFormatter
from .mock_models import MockElement
from .sorter import save_sorting_results_to_db, sort_layout_elements
from .text_version_service import create_text_version


# -----------------------------------------------------------------------------
# 내부 상수 & 헬퍼
# -----------------------------------------------------------------------------

UPLOADS_ROOT = (Path(__file__).resolve().parents[2] / "uploads").resolve()
DEFAULT_AI_CONCURRENCY = int(os.getenv("OPENAI_MAX_CONCURRENCY", "5"))


@lru_cache(maxsize=1)
def _get_analysis_service(model_choice: str = "SmartEyeSsen") -> AnalysisService:
    """
    모델 로딩 비용을 줄이기 위해 AnalysisService 인스턴스를 캐시합니다.
    """
    logger.debug("AnalysisService 인스턴스 요청 (model_choice=%s)", model_choice)
    return AnalysisService(model_choice=model_choice, auto_load=False)


def _resolve_image_path(image_path: str) -> Path:
    """
    Page.image_path 값을 절대 경로로 변환합니다.
    """
    raw_path = Path(image_path)
    candidates = []

    if raw_path.is_absolute():
        candidates.append(raw_path)
    else:
        candidates.append((UPLOADS_ROOT / raw_path).resolve())
        candidates.append((Path.cwd() / "uploads" / raw_path).resolve())
        candidates.append((Path.cwd() / raw_path).resolve())

    for candidate in candidates:
        if candidate.exists():
            return candidate

    raise FileNotFoundError(
        "이미지 파일을 찾을 수 없습니다. "
        f"확인된 경로: {[str(path) for path in candidates]}"
    )


def _load_page_image(page: Page) -> np.ndarray:
    """
    페이지 객체에서 이미지를 로드하고, 해상도 정보를 갱신합니다.
    """
    resolved_path = _resolve_image_path(page.image_path)
    image = cv2.imread(str(resolved_path))
    if image is None:
        raise ValueError(f"이미지 파일을 읽을 수 없습니다: {resolved_path}")

    height, width = image.shape[:2]
    if page.image_width != width or page.image_height != height:
        page.image_width = width
        page.image_height = height
    return image


def _layout_to_mock(elements: List[LayoutElement]) -> List[MockElement]:
    """
    SQLAlchemy LayoutElement 객체를 sorter에서 사용하는 MockElement로 변환합니다.
    """
    mock_elements: List[MockElement] = []
    for element in elements:
        mock = MockElement(
            element_id=element.element_id,
            class_name=element.class_name,
            confidence=float(element.confidence or 0.0),
            bbox_x=int(element.bbox_x),
            bbox_y=int(element.bbox_y),
            bbox_width=int(element.bbox_width),
            bbox_height=int(element.bbox_height),
            page_id=element.page_id,
        )
        mock_elements.append(mock)
    return mock_elements


def _sync_layout_runtime_fields(
    layout_elements: List[LayoutElement],
    mock_elements: List[MockElement],
) -> List[LayoutElement]:
    """
    sorter가 계산한 order_in_question, group_id 등을 실제 LayoutElement에 반영합니다.
    """
    element_map: Dict[int, LayoutElement] = {
        elem.element_id: elem for elem in layout_elements
    }
    synced_elements: List[LayoutElement] = []

    for mock in mock_elements:
        target = element_map.get(mock.element_id)
        if not target:
            logger.warning(
                "정렬 결과에 존재하지만 DB에 없는 element_id=%s", mock.element_id
            )
            continue

        setattr(target, "order_in_question", getattr(mock, "order_in_question", None))
        setattr(target, "group_id", getattr(mock, "group_id", None))
        setattr(target, "order_in_group", getattr(mock, "order_in_group", None))
        setattr(target, "y_position", getattr(mock, "y_position", target.bbox_y))
        setattr(target, "x_position", getattr(mock, "x_position", target.bbox_x))
        setattr(
            target,
            "area",
            getattr(mock, "area", target.bbox_width * target.bbox_height),
        )
        synced_elements.append(target)

    return synced_elements


def _update_page_status(
    page: Page,
    *,
    status: str,
    processing_time: float,
) -> None:
    """
    페이지의 상태/처리시간/분석 완료 시간을 갱신합니다.
    """
    page.analysis_status = status
    page.processing_time = processing_time
    page.analyzed_at = datetime.utcnow()


def _update_project_status(project: Project, status: str) -> None:
    """
    프로젝트 상태를 갱신합니다.
    """
    project.status = status
    project.updated_at = datetime.utcnow()


async def _process_single_page_async(
    *,
    db: Session,
    project: Project,
    page: Page,
    formatter: TextFormatter,
    analysis_service: AnalysisService,
    use_ai_descriptions: bool,
    api_key: Optional[str],
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
) -> Dict[str, Any]:
    """
    개별 페이지에 대한 전체 파이프라인을 실행하고 결과 요약을 반환합니다.
    """
    logger.info(
        "페이지 분석 시작: project_id=%s / page_id=%s", project.project_id, page.page_id
    )
    page_start = time.time()

    summary: Dict[str, Any] = {
        "page_id": page.page_id,
        "page_number": page.page_number,
        "status": "error",
        "message": "",
        "layout_count": 0,
        "ocr_count": 0,
        "ai_description_count": 0,
        "processing_time": 0.0,
    }

    try:
        image = _load_page_image(page)

        layout_elements = analysis_service.analyze_layout(
            image=image,
            page_id=page.page_id,
            db=db,
            model_choice=analysis_service.model_choice,
        )
        if not layout_elements:
            raise ValueError("레이아웃 분석 결과가 비어 있습니다.")
        summary["layout_count"] = len(layout_elements)

        text_contents = analysis_service.perform_ocr(
            image=image,
            layout_elements=layout_elements,
            db=db,
        )
        summary["ocr_count"] = len(text_contents)

        ai_descriptions: Dict[int, str] = {}
        if use_ai_descriptions:
            # API 키: 요청 파라미터 우선, 없으면 환경변수에서 로드
            effective_api_key = api_key or os.getenv("OPENAI_API_KEY")
            if effective_api_key:
                logger.info(f"AI 설명 생성 시작: page_id={page.page_id}")
                try:
                    ai_descriptions = await analysis_service.call_openai_api_async(
                        image=image,
                        layout_elements=layout_elements,
                        api_key=effective_api_key,
                        db=db,
                        max_concurrent_requests=ai_max_concurrency,
                    )
                    summary["ai_description_count"] = len(ai_descriptions)
                    logger.info(
                        f"AI 설명 생성 완료: {len(ai_descriptions)}개 요소 처리"
                    )
                except Exception as ai_error:
                    logger.error(
                        "AI 설명 생성 비동기 처리 실패: page_id=%s / error=%s",
                        page.page_id,
                        ai_error,
                    )
            else:
                logger.warning(
                    f"AI 설명 생성 요청되었으나 API 키가 없습니다 (page_id={page.page_id})"
                )

        mock_elements = _layout_to_mock(layout_elements)
        sorted_mock = sort_layout_elements(
            mock_elements,
            document_type=formatter.document_type,
            page_width=page.image_width or 0,
            page_height=page.image_height or 0,
        )
        synced_layouts = _sync_layout_runtime_fields(layout_elements, sorted_mock)

        save_sorting_results_to_db(db, page.page_id, synced_layouts)

        formatted_text = formatter.format_page(
            synced_layouts,
            text_contents,
            ai_descriptions=ai_descriptions,
        )
        create_text_version(db, page, formatted_text or "")

        processing_time = time.time() - page_start
        _update_page_status(page, status="completed", processing_time=processing_time)
        summary["status"] = "completed"
        summary["processing_time"] = processing_time
        summary["message"] = "success"

        db.commit()
        return summary

    except Exception as error:  # pylint: disable=broad-except
        logger.error(f"페이지 분석 실패: page_id={page.page_id} / error={str(error)}")
        logger.exception("상세 스택 트레이스:")  # 전체 스택 출력
        db.rollback()
        processing_time = time.time() - page_start
        _update_page_status(page, status="error", processing_time=processing_time)
        summary["processing_time"] = processing_time
        summary["message"] = str(error)
        db.commit()
        return summary


def _process_single_page(
    *,
    db: Session,
    project: Project,
    page: Page,
    formatter: TextFormatter,
    analysis_service: AnalysisService,
    use_ai_descriptions: bool,
    api_key: Optional[str],
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
) -> Dict[str, Any]:
    """
    동기 컨텍스트 호환용 래퍼.
    """
    return asyncio.run(
        _process_single_page_async(
            db=db,
            project=project,
            page=page,
            formatter=formatter,
            analysis_service=analysis_service,
            use_ai_descriptions=use_ai_descriptions,
            api_key=api_key,
            ai_max_concurrency=ai_max_concurrency,
        )
    )


# -----------------------------------------------------------------------------
# 공개 API
# -----------------------------------------------------------------------------


async def analyze_project_batch_async(
    db: Session,
    project_id: int,
    *,
    use_ai_descriptions: bool = True,
    api_key: Optional[str] = None,
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
) -> Dict[str, Any]:
    """
    프로젝트 내 'pending' 상태 페이지를 순차적으로 분석하고 결과 요약을 반환합니다.
    """
    logger.info("프로젝트 배치 분석 시작: project_id=%s", project_id)
    started_at = time.time()

    project = (
        db.query(Project)
        .options(selectinload(Project.pages))
        .filter(Project.project_id == project_id)
        .one_or_none()
    )
    if not project:
        raise ValueError(f"프로젝트 ID {project_id}를 찾을 수 없습니다.")

    pending_pages = [
        page for page in project.pages if page.analysis_status in {"pending", "error"}
    ]
    pending_pages.sort(key=lambda p: p.page_number)

    result_summary: Dict[str, Any] = {
        "project_id": project.project_id,
        "project_status_before": project.status,
        "processed_pages": 0,
        "successful_pages": 0,
        "failed_pages": 0,
        "total_pages": len(pending_pages),
        "status": "completed" if pending_pages else "no_pending_pages",
        "page_results": [],
        "total_time": 0.0,
    }

    if not pending_pages:
        logger.warning("분석할 페이지가 없습니다. project_id=%s", project.project_id)
        return result_summary

    _update_project_status(project, "in_progress")
    db.commit()

    analysis_service = _get_analysis_service()
    formatter = TextFormatter(
        doc_type_id=project.doc_type_id,
        db=db,
        use_db_rules=True,
    )

    for page in pending_pages:
        page_summary = await _process_single_page_async(
            db=db,
            project=project,
            page=page,
            formatter=formatter,
            analysis_service=analysis_service,
            use_ai_descriptions=use_ai_descriptions,
            api_key=api_key,
            ai_max_concurrency=ai_max_concurrency,
        )
        result_summary["page_results"].append(page_summary)
        result_summary["processed_pages"] += 1
        if page_summary["status"] == "completed":
            result_summary["successful_pages"] += 1
        else:
            result_summary["failed_pages"] += 1

    if result_summary["failed_pages"] == 0:
        final_status = "completed"
    elif result_summary["successful_pages"] == 0:
        final_status = "error"
    else:
        final_status = "partial"

    _update_project_status(project, final_status)
    db.commit()

    result_summary["status"] = final_status
    result_summary["project_status_after"] = project.status
    result_summary["total_time"] = time.time() - started_at
    logger.info(
        "프로젝트 배치 분석 종료: project_id=%s / status=%s / success=%s / fail=%s / %.2fs",
        project.project_id,
        final_status,
        result_summary["successful_pages"],
        result_summary["failed_pages"],
        result_summary["total_time"],
    )
    return result_summary


def analyze_project_batch(
    db: Session,
    project_id: int,
    *,
    use_ai_descriptions: bool = True,
    api_key: Optional[str] = None,
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
) -> Dict[str, Any]:
    """
    동기 컨텍스트 호환용 래퍼.
    """
    return asyncio.run(
        analyze_project_batch_async(
            db=db,
            project_id=project_id,
            use_ai_descriptions=use_ai_descriptions,
            api_key=api_key,
            ai_max_concurrency=ai_max_concurrency,
        )
    )


async def analyze_project_batch_async_parallel(
    db: Session,
    project_id: int,
    *,
    use_ai_descriptions: bool = True,
    api_key: Optional[str] = None,
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
    max_concurrent_pages: int = 4,
) -> Dict[str, Any]:
    """
    프로젝트 내 'pending' 상태 페이지를 병렬로 분석하고 결과 요약을 반환합니다.
    
    Args:
        db: 데이터베이스 세션
        project_id: 프로젝트 ID
        use_ai_descriptions: AI 설명 생성 여부
        api_key: OpenAI API 키
        ai_max_concurrency: AI API 최대 동시 요청 수
        max_concurrent_pages: 최대 동시 처리 페이지 수 (기본값: 4)
        
    Returns:
        분석 결과 요약
        
    Note:
        기존 analyze_project_batch_async와 동일한 기능이지만,
        여러 페이지를 동시에 병렬로 처리하여 속도를 향상시킵니다.
        max_concurrent_pages 값을 조정하여 시스템 리소스에 맞게 최적화할 수 있습니다.
    """
    logger.info(
        "프로젝트 병렬 배치 분석 시작: project_id=%s, max_concurrent=%s",
        project_id,
        max_concurrent_pages,
    )
    started_at = time.time()

    project = (
        db.query(Project)
        .options(selectinload(Project.pages))
        .filter(Project.project_id == project_id)
        .one_or_none()
    )
    if not project:
        raise ValueError(f"프로젝트 ID {project_id}를 찾을 수 없습니다.")

    pending_pages = [
        page for page in project.pages if page.analysis_status in {"pending", "error"}
    ]
    pending_pages.sort(key=lambda p: p.page_number)

    result_summary: Dict[str, Any] = {
        "project_id": project.project_id,
        "project_status_before": project.status,
        "processed_pages": 0,
        "successful_pages": 0,
        "failed_pages": 0,
        "total_pages": len(pending_pages),
        "status": "completed" if pending_pages else "no_pending_pages",
        "page_results": [],
        "total_time": 0.0,
        "processing_mode": "parallel",
    }

    if not pending_pages:
        logger.warning("분석할 페이지가 없습니다. project_id=%s", project.project_id)
        return result_summary

    _update_project_status(project, "in_progress")
    db.commit()

    analysis_service = _get_analysis_service()
    formatter = TextFormatter(
        doc_type_id=project.doc_type_id,
        db=db,
        use_db_rules=True,
    )

    # Semaphore로 동시 실행 제어
    semaphore = asyncio.Semaphore(max_concurrent_pages)

    async def process_with_semaphore(page: Page) -> Dict[str, Any]:
        """
        Semaphore를 사용하여 동시 실행 수를 제한하면서 페이지 처리
        
        각 페이지 분석 작업마다 독립적인 DB 세션을 생성하여
        병렬 처리 시 세션 충돌을 방지합니다.
        """
        async with semaphore:
            # 각 작업마다 독립적인 세션 생성
            from ..database import SessionLocal
            task_db = SessionLocal()
            try:
                # 세션에서 페이지 재로드 (다른 세션에서 가져온 객체이므로)
                task_page = task_db.query(Page).filter(Page.page_id == page.page_id).first()
                task_project = task_db.query(Project).filter(Project.project_id == project.project_id).first()
                
                if not task_page or not task_project:
                    raise ValueError(f"페이지 또는 프로젝트를 찾을 수 없습니다: page_id={page.page_id}")
                
                return await _process_single_page_async(
                    db=task_db,
                    project=task_project,
                    page=task_page,
                    formatter=formatter,
                    analysis_service=analysis_service,
                    use_ai_descriptions=use_ai_descriptions,
                    api_key=api_key,
                    ai_max_concurrency=ai_max_concurrency,
                )
            finally:
                task_db.close()

    # 모든 페이지를 병렬로 처리
    logger.info(f"총 {len(pending_pages)}개 페이지를 최대 {max_concurrent_pages}개씩 병렬 처리 시작")
    tasks = [process_with_semaphore(page) for page in pending_pages]
    page_results = await asyncio.gather(*tasks, return_exceptions=True)

    # 결과 집계
    for page_result in page_results:
        if isinstance(page_result, Exception):
            logger.error(f"페이지 처리 중 예외 발생: {page_result}")
            result_summary["page_results"].append({
                "status": "error",
                "message": str(page_result),
            })
            result_summary["failed_pages"] += 1
        else:
            result_summary["page_results"].append(page_result)
            if page_result["status"] == "completed":
                result_summary["successful_pages"] += 1
            else:
                result_summary["failed_pages"] += 1
        result_summary["processed_pages"] += 1

    # 최종 상태 결정
    if result_summary["failed_pages"] == 0:
        final_status = "completed"
    elif result_summary["successful_pages"] == 0:
        final_status = "error"
    else:
        final_status = "partial"

    _update_project_status(project, final_status)
    db.commit()

    result_summary["status"] = final_status
    result_summary["project_status_after"] = project.status
    result_summary["total_time"] = time.time() - started_at
    
    logger.info(
        "프로젝트 병렬 배치 분석 종료: project_id=%s / status=%s / success=%s / fail=%s / %.2fs",
        project.project_id,
        final_status,
        result_summary["successful_pages"],
        result_summary["failed_pages"],
        result_summary["total_time"],
    )
    return result_summary


def analyze_project_batch_parallel(
    db: Session,
    project_id: int,
    *,
    use_ai_descriptions: bool = True,
    api_key: Optional[str] = None,
    ai_max_concurrency: int = DEFAULT_AI_CONCURRENCY,
    max_concurrent_pages: int = 4,
) -> Dict[str, Any]:
    """
    동기 컨텍스트 호환용 래퍼 (병렬 처리 버전).
    """
    return asyncio.run(
        analyze_project_batch_async_parallel(
            db=db,
            project_id=project_id,
            use_ai_descriptions=use_ai_descriptions,
            api_key=api_key,
            ai_max_concurrency=ai_max_concurrency,
            max_concurrent_pages=max_concurrent_pages,
        )
    )


__all__ = [
    "analyze_project_batch",
    "analyze_project_batch_async",
    "analyze_project_batch_parallel",
    "analyze_project_batch_async_parallel",
    "_get_analysis_service",
    "_process_single_page",
    "_process_single_page_async",
    "DEFAULT_AI_CONCURRENCY",
]
