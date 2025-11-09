"""
텍스트 버전 관리 서비스
=======================

Project/의 Mock 기반 텍스트 버전 로직을 Backend/ ORM 환경에 맞게 재구성한 모듈입니다.
페이지별 텍스트 버전을 조회·생성·갱신하며, `batch_analysis` 및 FastAPI 라우터에서 재사용합니다.
"""

from __future__ import annotations

from typing import Any, Dict, Optional

from loguru import logger
from sqlalchemy.orm import Session

from ..models import CombinedResult, Page, TextVersion


def _deactivate_existing_versions(db: Session, page_id: int) -> None:
    """
    지정한 페이지의 기존 text_versions 중 is_current=True 항목을 False로 설정합니다.
    """
    updated = (
        db.query(TextVersion)
        .filter(
            TextVersion.page_id == page_id,
            TextVersion.is_current.is_(True),
        )
        .update({"is_current": False}, synchronize_session=False)
    )
    if updated:
        logger.debug(
            "페이지 {}의 기존 텍스트 버전 {}건을 비활성화했습니다.",
            page_id,
            updated,
        )


def _get_next_version_number(db: Session, page_id: int) -> int:
    """
    다음 버전 번호를 계산합니다.
    """
    latest_number = (
        db.query(TextVersion.version_number)
        .filter(TextVersion.page_id == page_id)
        .order_by(TextVersion.version_number.desc())
        .limit(1)
        .scalar()
    )
    return (latest_number or 0) + 1


def create_text_version(
    db: Session,
    page: Page,
    content: str,
    *,
    version_type: str = "auto_formatted",
    user_id: Optional[int] = None,
    commit: bool = False,
) -> TextVersion:
    """
    텍스트 버전을 생성하고 is_current 플래그를 관리합니다.
    """
    _deactivate_existing_versions(db, page.page_id)
    next_number = _get_next_version_number(db, page.page_id)

    version = TextVersion(
        page_id=page.page_id,
        user_id=user_id,
        content=content,
        version_number=next_number,
        version_type=version_type,
        is_current=True,
    )
    db.add(version)
    db.flush()
    db.refresh(version)

    if commit:
        db.commit()

    logger.info(
        "텍스트 버전 생성 완료: page_id={}, version_id={}, number={}, type={}",
        page.page_id,
        version.version_id,
        next_number,
        version_type,
    )
    return version


def _serialize_version(version: TextVersion) -> Dict[str, Any]:
    return {
        "page_id": version.page_id,
        "version_id": version.version_id,
        "version_type": version.version_type,
        "is_current": version.is_current,
        "content": version.content,
        "created_at": version.created_at,
    }


def get_current_page_text(db: Session, page_id: int) -> Optional[Dict[str, Any]]:
    """
    현재(is_current=True) 텍스트 버전을 조회합니다.
    """
    page = (
        db.query(Page)
        .filter(Page.page_id == page_id)
        .first()
    )
    if not page:
        raise ValueError(f"페이지 ID {page_id}를 찾을 수 없습니다.")

    version = (
        db.query(TextVersion)
        .filter(
            TextVersion.page_id == page_id,
            TextVersion.is_current.is_(True),
        )
        .order_by(TextVersion.version_number.desc())
        .first()
    )
    if not version:
        logger.warning(
            "페이지 {}의 현재 텍스트 버전을 찾을 수 없습니다. status={}",
            page_id,
            page.analysis_status,
        )
        return None
    return _serialize_version(version)


def save_user_edited_version(
    db: Session,
    page_id: int,
    content: str,
    *,
    user_id: Optional[int],
) -> Dict[str, Any]:
    """
    사용자 편집 텍스트를 새 버전으로 저장하고 해당 버전을 현재 버전으로 설정합니다.
    """
    page = (
        db.query(Page)
        .filter(Page.page_id == page_id)
        .first()
    )
    if not page:
        raise ValueError(f"페이지 ID {page_id}를 찾을 수 없습니다.")

    version = create_text_version(
        db,
        page,
        content,
        version_type="user_edited",
        user_id=user_id,
        commit=False,
    )

    deleted = (
        db.query(CombinedResult)
        .filter(CombinedResult.project_id == page.project_id)
        .delete(synchronize_session=False)
    )
    if deleted:
        logger.info(
            "CombinedResult 캐시 무효화: project_id={}, 삭제된 레코드={}",
            page.project_id,
            deleted,
        )

    db.commit()
    db.refresh(version)
    return _serialize_version(version)


__all__ = [
    "create_text_version",
    "get_current_page_text",
    "save_user_edited_version",
]
