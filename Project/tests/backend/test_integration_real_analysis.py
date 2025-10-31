# test_integration_real_analysis.py

import sys
import os
import cv2
import pytest
import pytest_asyncio  # 비동기 테스트 지원
from pathlib import Path
from typing import List, Dict, Optional
from loguru import logger
import time
from dotenv import load_dotenv  # .env 파일 로드용
import fitz  # PyMuPDF

# --- 프로젝트 루트 설정 및 모듈 임포트 ---
project_root = Path(__file__).resolve().parent.parent.parent # Project/ 경로
sys.path.insert(0, str(project_root)) # <--- 이 줄은 그대로 유지합니다.

# 서비스 임포트
from backend.app.services.analysis_service import AnalysisService
from backend.app.services.sorter import sort_layout_elements
from backend.app.services.db_saver import save_sorted_elements_to_mock_db, print_mock_db_summary
from backend.app.services.batch_analysis import initialize_mock_db_for_test as initialize_db_saver_mock
from backend.app.services.pdf_processor import PDFProcessor

# Mock 모델 (데이터 구조용)
from backend.app.services.mock_models import MockElement, MockTextContent

# 테스트 유틸리티
try:
    from tests.backend.test_utils import save_intermediate_results, load_intermediate_results, save_visual_artifacts
except ImportError:
    logger.error("test_utils.py 임포트 실패. Project/tests/backend/ 경로를 확인하세요.")
    def save_intermediate_results(*args, **kwargs): raise ImportError("save_intermediate_results not found")
    def load_intermediate_results(*args, **kwargs): raise ImportError("load_intermediate_results not found")
    def save_visual_artifacts(*args, **kwargs): raise ImportError("save_visual_artifacts not found")

# --- .env 로드 및 API 키 설정 ---
dotenv_path = project_root / ".env"
load_dotenv(dotenv_path)
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    logger.warning("⚠️ OPENAI_API_KEY가 .env 파일에 설정되지 않았습니다. AI 설명 생성이 건너뛰어집니다.")
else:
    logger.info(f"✅ .env 파일에서 OPENAI_API_KEY 로드 완료.")

# --- 테스트 설정 ---
CACHE_DIR = project_root / "tests" / ".cache"
BASE_OUTPUT_DIR = project_root / "tests" / "test_pipeline_outputs"
OUTPUT_SUBDIR = "real_analysis_test"
FINAL_OUTPUT_DIR = BASE_OUTPUT_DIR / OUTPUT_SUBDIR
DOC_TYPE_NAME = "question_based"

# --- 이미지 테스트 파일 목록 ---
TEST_IMAGE_FILES = [
    project_root / "tests" / "test_images" / "쎈 수학1-1_페이지_014.jpg",
    project_root / "tests" / "test_images" / "쎈 수학1-1_페이지_016.jpg",
]

# --- PDF 테스트 파일 경로 ---
# 👇 여기에 테스트할 PDF 파일 경로를 지정하세요.
# 예: project_root / "tests" / "test_images" / "my_test.pdf"
TEST_PDF_FILE = project_root / "tests" / "test_images" / "쎈 수학 낱개 문제지.pdf" 

# ===================================================================
# Pytest Fixtures
# ===================================================================

@pytest.fixture(scope="module")
def analysis_service_instance():
    """테스트 전체에서 사용할 AnalysisService 인스턴스 생성"""
    try:
        service = AnalysisService(model_choice="SmartEyeSsen", auto_load=True)
        return service
    except Exception as e:
        pytest.fail(f"AnalysisService 초기화 실패: {e}")

@pytest.fixture(scope="module")
def pdf_processor():
    """PDF 처리기 인스턴스 생성"""
    pdf_output_dir = project_root / "tests" / "test_outputs" / "pdf_to_img_for_analysis"
    pdf_output_dir.mkdir(parents=True, exist_ok=True)
    logger.info(f"PDF 변환 이미지 저장 폴더: {pdf_output_dir}")
    return PDFProcessor(upload_directory=str(pdf_output_dir), dpi=150)

# ===================================================================
# 테스트 헬퍼 함수
# ===================================================================

async def run_analysis_on_images(image_paths: List[Path], service: AnalysisService, rerun_analysis: bool, test_type: str):
    """지정된 이미지 목록에 대해 전체 분석 파이프라인을 실행하는 헬퍼 함수"""
    processed_pages = 0
    failed_pages = 0

    for i, img_path in enumerate(image_paths):
        page_num = i + 1
        img_filename = img_path.name
        logger.info(f"\n--- 📄 {test_type} 페이지 {page_num}/{len(image_paths)} 처리 시작: {img_filename} ---")

        if not img_path.exists():
            logger.error(f"   -> 이미지 파일 없음: {img_path}")
            failed_pages += 1
            continue

        try:
            image = cv2.imread(str(img_path))
            if image is None:
                logger.error(f"   -> 이미지 로드 실패: {img_path}")
                failed_pages += 1
                continue
            page_height, page_width = image.shape[:2]

            # 캐싱 로직
            layout_elements, ocr_results, ai_descriptions = None, None, None
            if not rerun_analysis:
                layout_elements = load_intermediate_results(CACHE_DIR, img_filename, "layout_elements")
                ocr_results = load_intermediate_results(CACHE_DIR, img_filename, "ocr_results")
                ai_descriptions = load_intermediate_results(CACHE_DIR, img_filename, "ai_descriptions")

            # 분석 파이프라인
            if not layout_elements:
                logger.info("   -> [1/4] 실제 레이아웃 분석 실행...")
                layout_elements = service.analyze_layout(image) or []
                save_intermediate_results(CACHE_DIR, img_filename, "layout_elements", layout_elements)
            else: logger.info("   -> [1/4] 레이아웃 분석: 캐시 사용")

            if not ocr_results:
                logger.info("   -> [2/4] 실제 OCR 처리 실행...")
                ocr_results = service.perform_ocr(image, layout_elements) or []
                save_intermediate_results(CACHE_DIR, img_filename, "ocr_results", ocr_results)
            else: logger.info("   -> [2/4] OCR 처리: 캐시 사용")

            if ai_descriptions is None:
                logger.info("   -> [3/4] 실제 AI 설명 생성 실행...")
                ai_desc_dict_int_keys = await service.call_openai_api_async(image, layout_elements, OPENAI_API_KEY)
                ai_descriptions = {str(k): v for k, v in ai_desc_dict_int_keys.items()} if ai_desc_dict_int_keys else {}
                save_intermediate_results(CACHE_DIR, img_filename, "ai_descriptions", ai_descriptions)
            else: logger.info("   -> [3/4] AI 설명 생성: 캐시 사용")

            logger.info("   -> [4/4] 실제 레이아웃 정렬 실행...")
            sorted_elements = sort_layout_elements(layout_elements, DOC_TYPE_NAME, page_width, page_height)

            # Mock DB 저장
            save_sorted_elements_to_mock_db(page_num, sorted_elements, clear_existing=False)

            # 결과물 저장
            ocr_map = {res.element_id: res.ocr_text for res in ocr_results if hasattr(res, 'element_id')}
            save_visual_artifacts(
                output_dir=str(FINAL_OUTPUT_DIR),
                image=image,
                sorted_elements=sorted_elements,
                ocr_map=ocr_map,
                ai_map=ai_descriptions or {},
                image_filename=img_filename
            )
            
            processed_pages += 1

        except Exception as e:
            logger.error(f"   -> 페이지 {page_num} 처리 중 오류 발생: {e}", exc_info=True)
            failed_pages += 1
    
    return processed_pages, failed_pages

# ===================================================================
# 테스트 함수
# ===================================================================

@pytest.mark.image_test
@pytest.mark.asyncio
async def test_real_analysis_multi_page(request, analysis_service_instance: AnalysisService):
    """다중 이미지에 대해 실제 분석 모델을 사용하는 통합 테스트"""
    logger.info("🚀 이미지 기반 실제 분석 통합 테스트 시작...")
    start_time = time.time()
    rerun_analysis = request.config.getoption("--rerun-analysis")

    os.makedirs(FINAL_OUTPUT_DIR, exist_ok=True)
    initialize_db_saver_mock()

    processed_pages, failed_pages = await run_analysis_on_images(
        image_paths=TEST_IMAGE_FILES,
        service=analysis_service_instance,
        rerun_analysis=rerun_analysis,
        test_type="이미지"
    )

    logger.info("\n" + "="*80)
    logger.info("📊 이미지 분석 테스트 결과 요약")
    logger.info(f"   - 총 시도 페이지: {len(TEST_IMAGE_FILES)}")
    logger.info(f"   - 성공 페이지: {processed_pages}")
    logger.info(f"   - 실패 페이지: {failed_pages}")
    logger.info(f"   - 총 소요 시간: {time.time() - start_time:.2f} 초")
    logger.info("="*80)

    print("\n--- 최종 Mock DB 상태 요약 (이미지 Test) ---")
    print_mock_db_summary()

    assert failed_pages == 0, f"{failed_pages}개 이미지 페이지 처리 실패"
    assert processed_pages == len(TEST_IMAGE_FILES), "모든 이미지 페이지가 처리되지 않았습니다."
    logger.success("🎉 이미지 기반 실제 분석 통합 테스트 성공!")


@pytest.mark.pdf_test
@pytest.mark.asyncio
async def test_real_analysis_from_pdf(request, analysis_service_instance: AnalysisService, pdf_processor: PDFProcessor):
    """PDF 파일을 이미지로 변환 후, 전체 분석 파이프라인을 실행하는 통합 테스트."""
    if not TEST_PDF_FILE:
        pytest.skip("테스트할 PDF 파일이 지정되지 않았습니다. (TEST_PDF_FILE is None)")

    logger.info("🚀 PDF 기반 실제 분석 통합 테스트 시작...")
    start_time = time.time()
    rerun_analysis = request.config.getoption("--rerun-analysis")

    # --- 1. 제공된 PDF 로드 및 이미지 변환 ---
    if not TEST_PDF_FILE.exists():
        pytest.fail(f"테스트 PDF 파일을 찾을 수 없습니다: {TEST_PDF_FILE}\n" 
                    f"파일 상단의 TEST_PDF_FILE 변수에 올바른 경로를 지정하세요.")

    logger.info(f"   -> 테스트 PDF 로드: {TEST_PDF_FILE.name}")
    pdf_bytes = TEST_PDF_FILE.read_bytes()
    
    converted_images_info = pdf_processor.convert_pdf_to_images(
        pdf_bytes=pdf_bytes,
        project_id=999, # 임시 프로젝트 ID
        start_page_number=1
    )
    
    pdf_image_files = [Path(pdf_processor.upload_directory) / info['image_path'] for info in converted_images_info]
    logger.info(f"   -> {len(pdf_image_files)} 페이지 PDF를 이미지로 변환 완료.")

    # --- 2. 분석 실행 ---
    os.makedirs(FINAL_OUTPUT_DIR, exist_ok=True)
    initialize_db_saver_mock()

    processed_pages, failed_pages = await run_analysis_on_images(
        image_paths=pdf_image_files,
        service=analysis_service_instance,
        rerun_analysis=rerun_analysis,
        test_type="PDF"
    )

    # --- 3. 최종 결과 요약 ---
    logger.info("\n" + "="*80)
    logger.info("📊 PDF 분석 테스트 결과 요약")
    logger.info(f"   - 총 시도 페이지: {len(pdf_image_files)}")
    logger.info(f"   - 성공 페이지: {processed_pages}")
    logger.info(f"   - 실패 페이지: {failed_pages}")
    logger.info(f"   - 총 소요 시간: {time.time() - start_time:.2f} 초")
    logger.info("="*80)

    print("\n--- 최종 Mock DB 상태 요약 (PDF Test) ---")
    print_mock_db_summary()

    assert failed_pages == 0, f"{failed_pages}개 PDF 페이지 처리 실패"
    assert processed_pages == len(pdf_image_files), "모든 PDF 페이지가 처리되지 않았습니다."
    logger.success("🎉 PDF 기반 실제 분석 통합 테스트 성공!")


# --- 테스트 실행 (스크립트 직접 실행 시) ---
if __name__ == "__main__":
    logger.remove()
    logger.add(sys.stderr, level="INFO")
    print("이 테스트는 pytest로 실행하는 것을 권장합니다:")
    print(f"pytest -s -v {__file__}")
    print("pytest로 특정 테스트만 실행하려면 -k 옵션을 사용하세요:")
    print(f"  pytest -s -v -k test_real_analysis_multi_page {__file__}")
    print(f"  pytest -s -v -k test_real_analysis_from_pdf {__file__}")