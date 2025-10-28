# -*- coding: utf-8 -*-
"""
레이아웃 정렬 결과 시각적 검증 테스트 (캐싱 기능 추가)
=================================================================
이 테스트는 전체 분석 파이프라인을 실행하고, 최종 정렬 결과를
사람이 직접 확인할 수 있는 시각적 결과물(이미지, JSON, TXT)로 저장합니다.

- 실행 방법:
  - `pytest tests/backend/test_visual_layout_alignment.py`
  - 캐시 무시하고 전체 재실행: `pytest tests/backend/test_visual_layout_alignment.py --rerun-analysis`

- 캐시 위치: `Project/tests/.cache/`
- 결과물 위치: `Project/tests/test_pipeline_outputs/`
"""

import cv2
import os
import sys
import pytest
from loguru import logger
from typing import List, Dict, Any

# ------------------------------------------------------------------
# 시스템 경로 설정 및 서비스/유틸리티 모듈 임포트
# ------------------------------------------------------------------
try:
    from backend.app.services.analysis_service import AnalysisService
    from backend.app.services.sorter import sort_layout_elements
    from backend.app.services.mock_models import MockElement, MockTextContent
    # 공통 유틸리티 함수 임포트
    from .test_utils import save_intermediate_results, load_intermediate_results, save_visual_artifacts
except (ImportError, ModuleNotFoundError) as e:
    print(f"오류: 모듈 임포트 실패 - {e}")
    print("이 스크립트를 'Project/' 폴더의 최상위에서 실행하거나 sys.path를 확인하세요.")
    sys.exit(1)

# ------------------------------------------------------------------
# 테스트 설정
# ------------------------------------------------------------------
TEST_IMAGE_NAME = "쎈 수학1-1_페이지_014.jpg"
TEST_IMAGE_PATH = os.path.join(os.path.dirname(__file__), '..', 'test_images', TEST_IMAGE_NAME)

# 최종 결과물 저장 디렉토리
BASE_OUTPUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'test_pipeline_outputs')
# 중간 결과(캐시) 저장 디렉토리
CACHE_DIR = os.path.join(os.path.dirname(__file__), '..', '.cache')

# 커밋 전에 무조건 api 키를 지워야함.
OPENAI_API_KEY = "sk-..."  # 실제 키 입력 또는 None
DOC_TYPE_NAME = "question_based"

# ------------------------------------------------------------------
# Pytest 테스트 함수 (캐싱 로직 추가)
# ------------------------------------------------------------------
@pytest.mark.visual
def test_single_page_layout_alignment_and_visual_output(request):
    """
    단일 페이지 분석 및 정렬을 테스트하고 시각적 결과물을 생성합니다.
    `--rerun-analysis` 옵션으로 캐시 사용을 제어할 수 있습니다.
    """
    # --- 0. 로거, 캐시 옵션, 디렉토리 설정 ---
    logger.remove()
    logger.add(sys.stderr, level="INFO")
    logger.info("🚀 레이아웃 정렬 시각적 검증 테스트 시작...")

    rerun_analysis = request.config.getoption("--rerun-analysis")
    if rerun_analysis:
        logger.warning("캐시 무시 옵션(--rerun-analysis) 활성화됨. 전체 분석을 재실행합니다.")

    test_name = "test_single_page_layout_cached"
    output_dir = os.path.join(BASE_OUTPUT_DIR, test_name)

    # --- 1. 이미지 및 서비스 준비 ---
    assert os.path.exists(TEST_IMAGE_PATH), f"테스트 이미지 파일을 찾을 수 없습니다: {TEST_IMAGE_PATH}"
    image = cv2.imread(TEST_IMAGE_PATH)
    assert image is not None, f"이미지 로드 실패: {TEST_IMAGE_PATH}"
    page_height, page_width = image.shape[:2]
    logger.info(f"이미지 로드 완료: {TEST_IMAGE_PATH} ({page_width}x{page_height})")

    service = AnalysisService()

    # --- 2. 분석 파이프라인 실행 (캐싱 적용) ---
    
    # 2.1 레이아웃 분석
    layout_elements: List[MockElement] | None = None
    if not rerun_analysis:
        layout_elements = load_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "layout_elements")
    
    if not layout_elements:
        logger.info("레이아웃 분석 실행 (캐시 없음 또는 재실행 요청)...")
        try:
            model_path = service.download_model("SmartEyeSsen")
            assert model_path and service.load_model(model_path), "YOLO 모델 로드 실패"
        except Exception as e:
            pytest.fail(f"모델 로드 중 예외 발생: {e}")
        layout_elements = service.analyze_layout(image, model_choice='SmartEyeSsen')
        assert layout_elements, "레이아웃 분석 결과가 비어있습니다."
        save_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "layout_elements", layout_elements)
    
    logger.info(f"레이아웃 분석 완료: {len(layout_elements)}개 요소.")

    # 2.2 OCR 처리
    ocr_results: List[MockTextContent] | None = None
    if not rerun_analysis:
        ocr_results = load_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "ocr_results")

    if not ocr_results:
        logger.info("OCR 처리 실행 (캐시 없음 또는 재실행 요청)...")
        ocr_results = service.perform_ocr(image, layout_elements)
        assert ocr_results, "OCR 결과가 비어있습니다."
        save_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "ocr_results", ocr_results)

    logger.info(f"OCR 처리 완료: {len(ocr_results)}개 텍스트 추출.")

    # 2.3 AI 설명 생성
    ai_descriptions: Dict[int, str] | None = None
    if not rerun_analysis:
        ai_descriptions = load_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "ai_descriptions")

    if ai_descriptions is None: # ai_descriptions는 빈 dict일 수 있으므로 None으로 체크
        logger.info("AI 설명 생성 실행 (캐시 없음 또는 재실행 요청)...")
        ai_descriptions = {}
        if OPENAI_API_KEY and OPENAI_API_KEY != "sk-...":
            ai_descriptions = service.call_openai_api(image, layout_elements, OPENAI_API_KEY)
            logger.info(f"{len(ai_descriptions)}개 AI 설명 생성 완료.")
        else:
            logger.warning("AI 설명: API 키가 없어 건너뜁니다.")
        save_intermediate_results(CACHE_DIR, TEST_IMAGE_NAME, "ai_descriptions", ai_descriptions)

    # --- 3. 핵심 로직: 레이아웃 정렬 ---
    logger.info(f"레이아웃 정렬 시작 (문서 타입: {DOC_TYPE_NAME})...")
    sorted_elements = sort_layout_elements(layout_elements, DOC_TYPE_NAME, page_width, page_height)
    assert sorted_elements, "레이아웃 정렬 결과가 비어있습니다."
    logger.info(f"{len(sorted_elements)}개 요소 정렬 완료.")

    # --- 4. 결과물 저장 및 검증 ---
    logger.info("시각적 결과물 저장 시작...")
    ocr_map = {res.element_id: res.ocr_text for res in ocr_results}
    ai_map_str_keys = {str(k): v for k, v in ai_descriptions.items()}

    output_files = save_visual_artifacts(
        output_dir=output_dir,
        image=image,
        sorted_elements=sorted_elements,
        ocr_map=ocr_map,
        ai_map=ai_map_str_keys
    )

    assert os.path.exists(output_files['image']), "시각화 이미지 파일이 생성되지 않았습니다."
    assert os.path.exists(output_files['json']), "JSON 결과 파일이 생성되지 않았습니다."
    assert os.path.exists(output_files['text']), "텍스트 결과 파일이 생성되지 않았습니다."

    logger.info("✅ 레이아웃 정렬 시각적 검증 테스트 성공적으로 완료.")
    logger.info(f"결과물은 다음 위치에서 확인하세요: {os.path.abspath(output_dir)}")