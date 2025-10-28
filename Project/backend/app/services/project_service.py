# -*- coding: utf-8 -*-
"""
SmartEyeSsen 프로젝트 관리 서비스 (Phase 3.1)
===========================================

Mock DB를 사용하여 프로젝트 및 페이지 CRUD 로직을 처리합니다.
batch_analysis.py에 정의된 Mock DB 함수들을 호출합니다.
"""

from typing import List, Dict, Optional, Any
from loguru import logger
import os # 파일 저장을 위해 추가
import shutil # 파일 저장을 위해 추가
from fastapi import UploadFile
from pathlib import Path

# Mock DB 함수 임포트 (batch_analysis.py로부터)
try:
    from .batch_analysis import (
        create_project_mock,
        get_project_mock,
        get_all_projects_mock,
        add_page_mock,
        get_page_mock,
        get_pages_for_project_mock,
        mock_projects
    )
    # 필요한 다른 Mock DB 함수도 임포트 가능
except ImportError:
    logger.error("Mock DB 함수를 batch_analysis.py에서 임포트할 수 없습니다.")
    # 임시 함수 정의 (오류 방지용)
    def create_project_mock(user_id: int, doc_type_id: int, project_name: str) -> int: return 1
    def get_project_mock(project_id: int) -> Optional[Dict]: return None
    def get_all_projects_mock() -> List[Dict]: return []
    def add_page_mock(project_id: int, page_number: Optional[int], image_path: str, image_width: int = 0, image_height: int = 0) -> int: return 1
    def get_page_mock(page_id: int) -> Optional[Dict]: return None
    def get_pages_for_project_mock(project_id: int) -> List[Dict]: return []

# 이미지 업로드 설정 (실제 환경에서는 설정 파일에서 읽어옴)
UPLOAD_DIRECTORY = "uploads"
os.makedirs(UPLOAD_DIRECTORY, exist_ok=True)

# ============================================================================
# 프로젝트 관련 서비스 함수
# ============================================================================

def create_new_project(user_id: int, doc_type_id: int, project_name: str) -> Dict[str, Any]:
    """새 프로젝트 생성"""
    logger.info(f"서비스: 새 프로젝트 생성 요청 - UserID={user_id}, Name='{project_name}'")
    try:
        project_id = create_project_mock(user_id, doc_type_id, project_name)
        project = get_project_mock(project_id)
        if not project:
            raise ValueError("프로젝트 생성 후 조회 실패")
        logger.info(f"서비스: 프로젝트 생성 성공 - ID={project_id}")
        return project
    except ValueError as ve:
        logger.error(f"서비스: 잘못된 프로젝트 데이터 - {ve}")
        raise
    except KeyError as ke:
        logger.error(f"서비스: 필수 데이터 누락 - {ke}", exc_info=True)
        raise ValueError(f"필수 데이터 누락: {ke}")
    except Exception as e:
        logger.error(f"서비스: 예기치 않은 프로젝트 생성 오류 - {e}", exc_info=True)
        raise

def get_project_details(project_id: int) -> Optional[Dict[str, Any]]:
    """프로젝트 상세 정보 조회"""
    logger.debug(f"서비스: 프로젝트 상세 조회 요청 - ID={project_id}")
    project = get_project_mock(project_id)
    if project:
        # 필요시 페이지 목록도 함께 반환 가능
        # project['pages'] = get_pages_for_project_mock(project_id)
        pass
    return project

def list_all_projects() -> List[Dict[str, Any]]:
    """모든 프로젝트 목록 조회"""
    logger.debug("서비스: 모든 프로젝트 목록 조회 요청")
    return get_all_projects_mock()

# ============================================================================
# 페이지 관련 서비스 함수
# ============================================================================

async def add_new_page(project_id: int, page_number: Optional[int], image_file: UploadFile) -> Dict[str, Any]:
    """프로젝트에 새 페이지 추가 (이미지 파일 업로드 포함)"""
    logger.info(f"서비스: 페이지 추가 요청 - ProjectID={project_id}, FileName='{image_file.filename}'")

    project = get_project_mock(project_id)
    if not project:
        raise ValueError(f"프로젝트 ID {project_id} 없음")

    # 이미지 저장 경로 결정 (프로젝트별 폴더)
    project_upload_dir = os.path.join(UPLOAD_DIRECTORY, str(project_id))
    os.makedirs(project_upload_dir, exist_ok=True)

    # 페이지 번호 결정
    if page_number is None:
        existing_pages = get_pages_for_project_mock(project_id)
        page_number = len(existing_pages) + 1
    else:
        # 페이지 번호 중복 확인
        if any(p['page_number'] == page_number for p in get_pages_for_project_mock(project_id)):
             raise ValueError(f"페이지 번호 {page_number}는 이미 존재합니다.")

    # 파일 저장 (실제 저장 대신 Mock 경로 사용 가능)
    # 여기서는 실제 파일 저장을 시뮬레이션 해봅니다.
    # 보안: 실제 환경에서는 파일 이름 정제 필요
    safe_filename = f"page_{page_number}{Path(image_file.filename).suffix if image_file.filename else '.jpg'}"
    image_path = os.path.join(project_upload_dir, safe_filename)
    relative_image_path = os.path.join(str(project_id), safe_filename) # DB 저장용 상대 경로

    try:
        # 파일 저장
        with open(image_path, "wb") as buffer:
            shutil.copyfileobj(image_file.file, buffer)
        logger.info(f"서비스: 이미지 파일 저장 완료 - '{image_path}'")

        # 이미지 크기 정보 (선택적)
        image_width, image_height = 0, 0
        try:
            from PIL import Image
            with Image.open(image_path) as img:
                image_width, image_height = img.size
        except ImportError:
            logger.warning("서비스: PIL 라이브러리 없음 - 이미지 크기 측정 건너뜀")
        except (OSError, IOError) as img_e:
            logger.warning(f"서비스: 이미지 파일 읽기 실패 - {img_e}")

        # Mock DB에 페이지 정보 추가
        page_id = add_page_mock(project_id, page_number, relative_image_path, image_width, image_height)
        page = get_page_mock(page_id)
        if not page:
            raise ValueError("페이지 생성 후 조회 실패")

        logger.info(f"서비스: 페이지 추가 성공 - ID={page_id}")
        return page

    except ValueError as ve:
        logger.error(f"서비스: 잘못된 페이지 데이터 - {ve}")
        if os.path.exists(image_path):
            os.remove(image_path)
        raise
    except OSError as ose:
        logger.error(f"서비스: 파일 시스템 오류 - {ose}", exc_info=True)
        if os.path.exists(image_path):
            os.remove(image_path)
        raise
    except Exception as e:
        logger.error(f"서비스: 예기치 않은 페이지 추가 오류 - {e}", exc_info=True)
        # 실패 시 저장된 파일 삭제 (선택적)
        if os.path.exists(image_path):
            os.remove(image_path)
        raise
    finally:
        # UploadFile 객체 닫기
        await image_file.close()


def get_page_details(page_id: int) -> Optional[Dict[str, Any]]:
    """페이지 상세 정보 조회"""
    logger.debug(f"서비스: 페이지 상세 조회 요청 - ID={page_id}")
    return get_page_mock(page_id)

def list_pages_for_project(project_id: int) -> List[Dict[str, Any]]:
    """특정 프로젝트의 모든 페이지 목록 조회 (페이지 번호 순)"""
    logger.debug(f"서비스: 프로젝트 페이지 목록 조회 요청 - ProjectID={project_id}")
    if project_id not in mock_projects:
         raise ValueError(f"프로젝트 ID {project_id} 없음")
    return get_pages_for_project_mock(project_id)

# 페이지 순서 변경, 페이지 삭제 등의 함수 추가 가능
