from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from loguru import logger
from sqlalchemy.orm import Session

from .. import schemas
from ..database import get_db
from ..models import Project
from ..services.download_service import (
    generate_combined_text,
    generate_word_document,
)

router = APIRouter(
    prefix="/api/projects",
    tags=["Downloads"],
)


@router.get(
    "/{project_id}/combined-text",
    response_model=schemas.CombinedTextResponse,
    status_code=status.HTTP_200_OK,
    summary="프로젝트 통합 텍스트 조회",
)
def get_combined_text(
    project_id: int,
    db: Session = Depends(get_db),
) -> schemas.CombinedTextResponse:
    """
    프로젝트의 최신 텍스트 버전을 통합하여 반환합니다.
    CombinedResult 캐시가 최신이면 캐시를 사용합니다.
    """
    project_exists = (
        db.query(Project.project_id)
        .filter(Project.project_id == project_id)
        .scalar()
    )
    if not project_exists:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="프로젝트를 찾을 수 없습니다.")

    try:
        combined_data = generate_combined_text(db, project_id, use_cache=True)
        return schemas.CombinedTextResponse.model_validate(combined_data)
    except ValueError as value_error:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(value_error)) from value_error
    except Exception as error:  # pylint: disable=broad-except
        logger.error("통합 텍스트 생성 실패: project_id=%s / error=%s", project_id, error, exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="통합 텍스트 생성 중 오류가 발생했습니다.") from error


@router.post(
    "/{project_id}/download",
    response_class=StreamingResponse,
    status_code=status.HTTP_200_OK,
    summary="프로젝트 Word 문서 다운로드",
)
def download_document(
    project_id: int,
    db: Session = Depends(get_db),
) -> StreamingResponse:
    """
    프로젝트의 통합 텍스트를 Word(.docx) 문서로 생성하여 스트리밍 응답으로 반환합니다.
    """
    project_exists = (
        db.query(Project.project_id)
        .filter(Project.project_id == project_id)
        .scalar()
    )
    if not project_exists:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="프로젝트를 찾을 수 없습니다.")

    try:
        filename, file_stream = generate_word_document(db, project_id, use_cache=True)
        headers = {"Content-Disposition": f'attachment; filename="{filename}"'}
        return StreamingResponse(
            file_stream,
            media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            headers=headers,
        )
    except ImportError as import_error:
        raise HTTPException(
            status_code=status.HTTP_501_NOT_IMPLEMENTED,
            detail=str(import_error),
        ) from import_error
    except ValueError as value_error:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(value_error)) from value_error
    except Exception as error:  # pylint: disable=broad-except
        logger.error("Word 문서 생성 실패: project_id=%s / error=%s", project_id, error, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Word 문서 생성 중 오류가 발생했습니다.",
        ) from error
