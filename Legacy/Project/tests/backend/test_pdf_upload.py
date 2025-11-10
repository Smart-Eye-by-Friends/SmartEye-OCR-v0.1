# -*- coding: utf-8 -*-
"""
PDF 업로드 및 변환 통합 테스트
================================

PDF 파일 업로드, 페이지별 이미지 변환, DB 저장, 분석 파이프라인 연동을 검증합니다.
"""

import sys
import os
import pytest
import pytest_asyncio
from pathlib import Path
from typing import List, Dict
from loguru import logger
import io
from PIL import Image, ImageDraw, ImageFont
import fitz  # PyMuPDF

# 프로젝트 루트 설정
project_root = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(project_root))

# 서비스 임포트
from backend.app.services.pdf_processor import PDFProcessor
from backend.app.services.project_service import add_new_page, get_pages_for_project_mock
from backend.app.services.batch_analysis import (
    create_project_mock,
    mock_projects,
    mock_pages,
    initialize_mock_db_for_test
)

# 테스트 설정
TEST_OUTPUT_DIR = project_root / "tests" / "test_outputs" / "pdf_upload"
TEST_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ============================================================================
# 테스트 픽스처
# ============================================================================

@pytest.fixture(scope="function")
def setup_mock_db():
    """각 테스트 전에 Mock DB 초기화"""
    initialize_mock_db_for_test()
    yield
    # 테스트 후 정리
    mock_projects.clear()
    mock_pages.clear()


@pytest.fixture
def pdf_processor():
    """PDF 처리기 인스턴스 생성"""
    return PDFProcessor(upload_directory=str(TEST_OUTPUT_DIR), dpi=300)


def create_test_pdf(num_pages: int = 3, content_text: str = "테스트 페이지") -> bytes:
    """
    테스트용 PDF 생성

    Args:
        num_pages: 생성할 페이지 수
        content_text: 각 페이지에 표시할 텍스트

    Returns:
        PDF 바이트 데이터
    """
    # PyMuPDF로 PDF 생성
    pdf_document = fitz.open()

    for page_num in range(num_pages):
        # A4 크기 페이지 추가
        page = pdf_document.new_page(width=595, height=842)  # A4: 595 x 842 포인트

        # 텍스트 추가
        text = f"{content_text} {page_num + 1}"
        point = fitz.Point(50, 100)
        page.insert_text(point, text, fontsize=20, color=(0, 0, 0))

        # 간단한 도형 추가 (시각적 요소)
        rect = fitz.Rect(50, 150, 200, 300)
        page.draw_rect(rect, color=(0, 0, 1), width=2)

    # PDF를 바이트로 변환
    pdf_bytes = pdf_document.tobytes()
    pdf_document.close()

    return pdf_bytes


# ============================================================================
# 단위 테스트: PDF 변환 기능
# ============================================================================

def test_pdf_processor_initialization(pdf_processor):
    """PDF 처리기 초기화 테스트"""
    assert pdf_processor.dpi == 300
    assert pdf_processor.jpeg_quality == 95
    assert Path(pdf_processor.upload_directory).exists()


def test_pdf_to_images_conversion_single_page(pdf_processor):
    """단일 페이지 PDF 변환 테스트"""
    # 1페이지 PDF 생성
    pdf_bytes = create_test_pdf(num_pages=1, content_text="단일 페이지")

    # 변환 실행
    project_id = 999
    start_page_number = 1

    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )

    # 검증
    assert len(converted_pages) == 1
    assert converted_pages[0]['page_number'] == 1
    assert 'image_path' in converted_pages[0]
    assert 'width' in converted_pages[0]
    assert 'height' in converted_pages[0]
    assert converted_pages[0]['width'] > 0
    assert converted_pages[0]['height'] > 0

    # 파일 존재 확인
    full_path = Path(pdf_processor.upload_directory) / converted_pages[0]['image_path']
    assert full_path.exists()

    logger.info(f"✅ 단일 페이지 PDF 변환 성공: {full_path}")


def test_pdf_to_images_conversion_multi_page(pdf_processor):
    """다중 페이지 PDF 변환 테스트"""
    # 10페이지 PDF 생성
    num_pages = 10
    pdf_bytes = create_test_pdf(num_pages=num_pages, content_text="다중 페이지")

    # 변환 실행
    project_id = 998
    start_page_number = 1

    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )

    # 검증
    assert len(converted_pages) == num_pages

    for i, page_info in enumerate(converted_pages):
        assert page_info['page_number'] == start_page_number + i
        full_path = Path(pdf_processor.upload_directory) / page_info['image_path']
        assert full_path.exists()

    logger.info(f"✅ {num_pages}페이지 PDF 변환 성공")


def test_pdf_conversion_with_start_page_offset(pdf_processor):
    """페이지 번호 오프셋 테스트"""
    # 5페이지 PDF 생성
    pdf_bytes = create_test_pdf(num_pages=5, content_text="오프셋 테스트")

    # 10번부터 시작
    project_id = 997
    start_page_number = 10

    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )

    # 검증: 페이지 번호가 10, 11, 12, 13, 14여야 함
    assert len(converted_pages) == 5
    for i, page_info in enumerate(converted_pages):
        assert page_info['page_number'] == 10 + i

    logger.info(f"✅ 페이지 번호 오프셋 테스트 성공")


def test_pdf_conversion_invalid_pdf(pdf_processor):
    """손상된 PDF 파일 에러 처리 테스트"""
    # 잘못된 바이트 데이터
    invalid_pdf_bytes = b"This is not a PDF file"

    project_id = 996
    start_page_number = 1

    # ValueError 발생 확인
    with pytest.raises(ValueError, match="PDF 파일이 손상"):
        pdf_processor.convert_pdf_to_images(
            pdf_bytes=invalid_pdf_bytes,
            project_id=project_id,
            start_page_number=start_page_number
        )

    logger.info(f"✅ 손상된 PDF 에러 처리 테스트 성공")


def test_pdf_metadata_extraction(pdf_processor):
    """PDF 메타데이터 추출 테스트"""
    pdf_bytes = create_test_pdf(num_pages=3, content_text="메타데이터 테스트")

    metadata = pdf_processor.get_pdf_info(pdf_bytes)

    # 검증
    assert 'total_pages' in metadata
    assert metadata['total_pages'] == 3
    assert 'title' in metadata
    assert 'author' in metadata

    logger.info(f"✅ PDF 메타데이터 추출 성공: {metadata}")


# ============================================================================
# 통합 테스트: project_service와 연동
# ============================================================================

@pytest.mark.asyncio
async def test_add_page_with_presaved_image(setup_mock_db):
    """기존 이미지 경로를 사용한 페이지 추가 테스트"""
    # Mock 프로젝트 생성
    project_id = create_project_mock(user_id=1, doc_type_id=1, project_name="PDF 테스트 프로젝트")

    # 테스트 이미지 생성 (실제 파일)
    test_image_dir = TEST_OUTPUT_DIR / str(project_id)
    test_image_dir.mkdir(parents=True, exist_ok=True)

    test_image_path = test_image_dir / "page_1.jpg"
    img = Image.new('RGB', (2480, 3508), color='white')
    draw = ImageDraw.Draw(img)
    draw.text((100, 100), "테스트 이미지", fill='black')
    img.save(test_image_path)

    # 페이지 추가 (기존 이미지 사용)
    relative_path = f"{project_id}/page_1.jpg"
    page = await add_new_page(
        project_id=project_id,
        page_number=1,
        image_file=None,
        pre_saved_image_path=relative_path,
        pre_saved_image_width=2480,
        pre_saved_image_height=3508
    )

    # 검증
    assert page['page_id'] is not None
    assert page['project_id'] == project_id
    assert page['page_number'] == 1
    assert page['image_path'] == relative_path

    # Mock DB 확인
    pages = get_pages_for_project_mock(project_id)
    assert len(pages) == 1

    logger.info(f"✅ 기존 이미지 경로 사용 페이지 추가 테스트 성공")


@pytest.mark.asyncio
async def test_pdf_upload_full_workflow(setup_mock_db, pdf_processor):
    """PDF 업로드 전체 워크플로우 테스트 (변환 + DB 저장)"""
    # Mock 프로젝트 생성
    project_id = create_project_mock(user_id=1, doc_type_id=1, project_name="PDF 전체 워크플로우")

    # 5페이지 PDF 생성
    num_pages = 5
    pdf_bytes = create_test_pdf(num_pages=num_pages, content_text="전체 워크플로우")

    # 1. PDF 변환
    start_page_number = 1
    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )

    assert len(converted_pages) == num_pages

    # 2. 각 페이지를 DB에 추가
    created_pages = []
    for page_info in converted_pages:
        page = await add_new_page(
            project_id=project_id,
            page_number=page_info['page_number'],
            image_file=None,
            pre_saved_image_path=page_info['image_path'],
            pre_saved_image_width=page_info['width'],
            pre_saved_image_height=page_info['height']
        )
        created_pages.append(page)

    # 3. 검증
    assert len(created_pages) == num_pages

    # Mock DB 확인
    pages = get_pages_for_project_mock(project_id)
    assert len(pages) == num_pages

    for i, page in enumerate(pages):
        assert page['page_number'] == i + 1
        assert page['analysis_status'] == 'pending'

    logger.info(f"✅ PDF 업로드 전체 워크플로우 테스트 성공 ({num_pages}개 페이지)")


@pytest.mark.asyncio
async def test_mixed_image_and_pdf_upload(setup_mock_db, pdf_processor):
    """이미지와 PDF 혼합 업로드 테스트"""
    # Mock 프로젝트 생성
    project_id = create_project_mock(user_id=1, doc_type_id=1, project_name="혼합 업로드")

    # 1. 이미지 파일 업로드 (가상)
    test_image_dir = TEST_OUTPUT_DIR / str(project_id)
    test_image_dir.mkdir(parents=True, exist_ok=True)

    test_image_path = test_image_dir / "page_1.jpg"
    img = Image.new('RGB', (2000, 3000), color='white')
    img.save(test_image_path)

    # 이미지 페이지 추가
    from fastapi import UploadFile
    with open(test_image_path, "rb") as f:
        image_file = UploadFile(filename="page_1.jpg", file=f)
        await add_new_page(project_id=project_id, page_number=None, image_file=image_file)

    # 2. PDF 파일 업로드 (3페이지)
    pdf_bytes = create_test_pdf(num_pages=3, content_text="PDF 부분")

    # 현재 페이지 수 확인 (1개)
    existing_pages = get_pages_for_project_mock(project_id)
    start_page_number = len(existing_pages) + 1

    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )

    for page_info in converted_pages:
        await add_new_page(
            project_id=project_id,
            page_number=page_info['page_number'],
            image_file=None,
            pre_saved_image_path=page_info['image_path'],
            pre_saved_image_width=page_info['width'],
            pre_saved_image_height=page_info['height']
        )

    # 3. 검증: 총 4페이지 (이미지 1 + PDF 3)
    all_pages = get_pages_for_project_mock(project_id)
    assert len(all_pages) == 4

    logger.info(f"✅ 이미지 + PDF 혼합 업로드 테스트 성공 (총 {len(all_pages)}페이지)")


# ============================================================================
# 성능 테스트
# ============================================================================

def test_pdf_conversion_performance(pdf_processor):
    """PDF 변환 성능 테스트 (20페이지)"""
    import time

    # 20페이지 PDF 생성
    num_pages = 20
    pdf_bytes = create_test_pdf(num_pages=num_pages, content_text="성능 테스트")

    project_id = 995
    start_page_number = 1

    # 변환 시간 측정
    start_time = time.time()
    converted_pages = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=project_id,
        start_page_number=start_page_number
    )
    elapsed_time = time.time() - start_time

    # 검증
    assert len(converted_pages) == num_pages

    logger.info(f"✅ 20페이지 PDF 변환 성능: {elapsed_time:.2f}초 (평균 {elapsed_time/num_pages:.2f}초/페이지)")

    # 성능 기준: 30초 이내
    assert elapsed_time < 30, f"변환 시간이 너무 깁니다: {elapsed_time:.2f}초"


# ============================================================================
# 실행
# ============================================================================

if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
