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

# --- 프로젝트 루트 설정 및 모듈 임포트 ---
project_root = Path(__file__).resolve().parent.parent.parent # Project/ 경로
sys.path.insert(0, str(project_root)) # <--- 이 줄은 그대로 유지합니다.

# 실제 분석 서비스
from backend.app.services.analysis_service import AnalysisService
# 정렬 서비스 (✨ Adaptive Strategy)
from backend.app.services.sorter_strategies import sort_layout_elements_adaptive, LayoutProfiler
# DB 저장 서비스 (Mock DB v2.1 사용) 및 Mock DB 상태
from backend.app.services.db_saver import (
    save_sorted_elements_to_mock_db,
    print_mock_db_summary,
    mock_question_groups,
    mock_question_elements
)

from backend.app.services.batch_analysis import (
    initialize_mock_db_for_test as initialize_db_saver_mock, # 여기서 임포트하고 별칭 사용
)

# Mock 모델 (데이터 구조용)
from backend.app.services.mock_models import MockElement, MockTextContent

# 테스트 유틸리티 (캐싱 및 결과 저장)
try:
    # --- 👇 수정: CACHE_DIR 임포트 제거 👇 ---
    from tests.backend.test_utils import save_intermediate_results, load_intermediate_results, save_visual_artifacts
except ImportError:
    logger.error("test_utils.py 임포트 실패. Project/tests/backend/ 경로를 확인하세요.")
    # 대체 함수 정의 (테스트 실패 유도)
    def save_intermediate_results(*args, **kwargs): raise ImportError("save_intermediate_results not found")
    def load_intermediate_results(*args, **kwargs): raise ImportError("load_intermediate_results not found")
    def save_visual_artifacts(*args, **kwargs): raise ImportError("save_visual_artifacts not found")
    # --- 👆 CACHE_DIR 정의도 여기서 제거 👆 ---

# --- 👇 수정: .env 로드 및 OPENAI_API_KEY 정의 👇 ---
# .env 파일 로드 (Project 루트에서)
dotenv_path = project_root / ".env"
load_dotenv(dotenv_path)

# 환경 변수에서 API 키 가져오기
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    logger.warning("⚠️ OPENAI_API_KEY가 .env 파일에 설정되지 않았습니다.")
    logger.warning(f"   .env 파일 경로: {dotenv_path}")
    logger.warning("   AI 설명 생성이 건너뛰어집니다.")
else:
    logger.info(f"✅ .env 파일에서 OPENAI_API_KEY 로드 완료 (키 길이: {len(OPENAI_API_KEY)}자)")

# CACHE_DIR 정의 (test_utils.py 대신 여기에)
CACHE_DIR = project_root / "tests" / ".cache"
# --- 👆 수정 끝 👆 ---

# --- 테스트 설정 ---
TEST_IMAGE_FILES = [
    project_root / "tests" / "test_images" / "쎈 수학1-1_페이지_014.jpg",
    project_root / "tests" / "test_images" / "쎈 수학1-1_페이지_016.jpg",
    project_root / "tests" / "test_images" / "쎈 수학1-1_페이지_023.jpg"
    # 필요에 따라 이미지 경로 추가 (Path 객체 사용)
]
OUTPUT_SUBDIR = "real_analysis_test" # 결과 저장용 하위 폴더
BASE_OUTPUT_DIR = project_root / "tests" / "test_pipeline_outputs"
FINAL_OUTPUT_DIR = BASE_OUTPUT_DIR / OUTPUT_SUBDIR
DOC_TYPE_NAME = "question_based" # 또는 "reading_order"
# --------------------

# --- Pytest 설정 ---
# conftest.py의 --rerun-analysis 옵션을 사용하기 위해 pytest 실행 필요
# pytest -s -v tests/backend/test_integration_real_analysis_images.py
# pytest -s -v tests/backend/test_integration_real_analysis_images.py --rerun-analysis

@pytest.fixture(scope="module")
def analysis_service_instance():
    """
    테스트 전체에서 사용할 AnalysisService 인스턴스 생성

    [Lazy Loading 패턴 활용]
    - auto_load=True: 인스턴스 생성 시 모델을 즉시 로드
    - 다중 페이지 분석 시에도 모델은 한 번만 로드됨 (최적화됨)

    [하위 호환 방식]
    기존처럼 수동 로드도 가능:
    service = AnalysisService()
    model_path = service.download_model("SmartEyeSsen")
    service.load_model(model_path)
    """
    try:
        # Lazy Loading 패턴: auto_load=True로 자동 모델 로드
        service = AnalysisService(model_choice="SmartEyeSsen", auto_load=True)
        return service
    except Exception as e:
        pytest.fail(f"AnalysisService 초기화 실패: {e}")

# --- 테스트 함수 ---
@pytest.mark.asyncio  # 비동기 테스트 데코레이터
async def test_real_analysis_multi_page(request, analysis_service_instance: AnalysisService):
    """
    다중 이미지에 대해 실제 분석 모델을 사용하고 결과를 캐싱하며,
    정렬 결과만 Mock DB에 저장하는 통합 테스트.

    [비동기 병렬 처리 버전]
    - AI 설명 생성 시 call_openai_api_async() 사용
    - 처리 시간 약 70% 단축 (6초 → 1.8초)
    """
    logger.info("🚀 실제 분석 통합 테스트 시작 (다중 이미지, 캐싱 사용)...")
    start_time = time.time()
    rerun_analysis = request.config.getoption("--rerun-analysis") # conftest.py 옵션 읽기
    if rerun_analysis:
        logger.warning("   -> --rerun-analysis 옵션 활성화: 모든 분석 단계를 강제 재실행합니다.")

    # --- 1. 출력 폴더 및 Mock DB 초기화 ---
    os.makedirs(FINAL_OUTPUT_DIR, exist_ok=True)
    logger.info(f"   -> 결과 저장 폴더: {FINAL_OUTPUT_DIR}")
    initialize_db_saver_mock() # db_saver의 Mock DB만 초기화
    logger.info("   -> Mock DB(v2.1) 초기화 완료.")

    # 서비스 인스턴스 가져오기
    service = analysis_service_instance

    processed_pages = 0
    failed_pages = 0

    # --- 2. 이미지별 분석 및 정렬 루프 ---
    for i, img_path in enumerate(TEST_IMAGE_FILES):
        page_num = i + 1
        img_filename = img_path.name
        logger.info(f"\n--- 📄 페이지 {page_num}/{len(TEST_IMAGE_FILES)} 처리 시작: {img_filename} ---")

        if not img_path.exists():
            logger.error(f"   -> 이미지 파일 없음: {img_path}")
            failed_pages += 1
            continue

        try:
            # --- 이미지 로드 ---
            image = cv2.imread(str(img_path))
            if image is None:
                logger.error(f"   -> 이미지 로드 실패: {img_path}")
                failed_pages += 1
                continue
            page_height, page_width = image.shape[:2]
            logger.info(f"   -> 이미지 로드 완료 ({page_width}x{page_height})")

            # --- 중간 결과 로드 또는 실제 분석 실행 (캐싱 로직) ---
            layout_elements: Optional[List[MockElement]] = None
            ocr_results: Optional[List[MockTextContent]] = None
            ai_descriptions: Optional[Dict[str, str]] = None # str 키 사용

            if not rerun_analysis:
                logger.debug("   -> 캐시된 중간 결과 로드 시도...")
                layout_elements = load_intermediate_results(CACHE_DIR, img_filename, "layout_elements")
                ocr_results = load_intermediate_results(CACHE_DIR, img_filename, "ocr_results")
                ai_descriptions = load_intermediate_results(CACHE_DIR, img_filename, "ai_descriptions")

            # 레이아웃 분석
            if not layout_elements:
                logger.info("   -> [1/4] 실제 레이아웃 분석 실행...")
                layout_elements = service.analyze_layout(image)
                if layout_elements is None: layout_elements = [] # None 대신 빈 리스트
                save_intermediate_results(CACHE_DIR, img_filename, "layout_elements", layout_elements)
            else:
                logger.info("   -> [1/4] 레이아웃 분석: 캐시 사용")

            # OCR 처리
            if not ocr_results:
                logger.info("   -> [2/4] 실제 OCR 처리 실행...")
                ocr_results = service.perform_ocr(image, layout_elements or [])
                if ocr_results is None: ocr_results = []
                save_intermediate_results(CACHE_DIR, img_filename, "ocr_results", ocr_results)
            else:
                logger.info("   -> [2/4] OCR 처리: 캐시 사용")

            # AI 설명 생성 (비동기 병렬 처리 버전)
            if ai_descriptions is None: # None 체크 중요 (빈 dict일 수 있음)
                logger.info("   -> [3/4] 실제 AI 설명 생성 실행 (비동기 병렬 처리)...")
                # 👇 변경: await 추가 및 call_openai_api_async 사용
                ai_desc_dict_int_keys = await service.call_openai_api_async(
                    image=image,
                    layout_elements=layout_elements or [],
                    api_key=OPENAI_API_KEY,
                    max_concurrent_requests=5  # 동시 요청 수 제한 (Rate Limit 대응)
                )
                # JSON 저장을 위해 int 키를 str 키로 변환
                ai_descriptions = {str(k): v for k, v in ai_desc_dict_int_keys.items()} if ai_desc_dict_int_keys else {}
                save_intermediate_results(CACHE_DIR, img_filename, "ai_descriptions", ai_descriptions)
                logger.info(f"      ✅ 비동기 병렬 처리 완료: {len(ai_descriptions)}개 설명 생성")
            else:
                logger.info("   -> [3/4] AI 설명 생성: 캐시 사용")

            # --- 정렬 실행 (✨ Adaptive Strategy 자동 선택) ---
            logger.info("   -> [4/4] 레이아웃 정렬 실행 (Adaptive Strategy)...")

            # 레이아웃 프로파일 분석
            profile = LayoutProfiler.analyze(
                elements=layout_elements or [],
                page_width=page_width,
                page_height=page_height
            )
            logger.info(f"      📊 레이아웃 프로파일:")
            logger.info(f"         - Global Consistency: {profile.global_consistency_score:.3f}")
            logger.info(f"         - Horizontal Adjacency: {profile.horizontal_adjacency_ratio:.3f}")
            logger.info(f"         - Anchor Count: {profile.anchor_count}")
            logger.info(f"         - Layout Type: {profile.layout_type.name}")
            logger.info(f"         - 🎯 추천 전략: {profile.recommended_strategy.name}")

            # Adaptive Sorter 실행 (자동 전략 선택)
            sorted_elements = sort_layout_elements_adaptive(
                elements=layout_elements or [],
                document_type=DOC_TYPE_NAME,
                page_width=page_width,
                page_height=page_height,
                force_strategy=None  # 자동 전략 선택
            )
            logger.info(f"      ✅ 정렬 완료: {len(sorted_elements)}개 요소")

            # --- Mock DB 저장 (정렬 결과만) ---
            # Mock 페이지 ID 생성 (실제 페이지 ID 대신 순서 사용)
            mock_page_id = page_num
            logger.info(f"   -> Mock DB(v2.1)에 정렬 결과 저장 (Page ID: {mock_page_id})...")
            save_stats = save_sorted_elements_to_mock_db(
                page_id=mock_page_id,
                sorted_elements=sorted_elements,
                clear_existing=False # 페이지별로 추가 (True로 하면 이전 페이지 결과 삭제됨)
            )
            logger.info(f"      -> DB 저장 완료: {save_stats}")

            # --- 결과물 저장 ---
            logger.info("   -> 시각화 및 텍스트 결과 저장...")
            ocr_map = {res.element_id: res.ocr_text for res in ocr_results or [] if hasattr(res, 'element_id')}
            # ai_map은 이미 str 키를 가지고 있음

            # 고유한 파일명 생성 (페이지 번호 + 원본 파일명)
            unique_filename = f"page_{page_num}_{img_filename}"

            output_paths = save_visual_artifacts(
                output_dir=str(FINAL_OUTPUT_DIR), # Path 객체를 문자열로 변환
                image=image,
                sorted_elements=sorted_elements,
                ocr_map=ocr_map,
                ai_map=ai_descriptions or {},
                image_filename=unique_filename  # 고유 파일명 전달
            )
            logger.info(f"      -> 결과 저장 완료: {output_paths}")

            processed_pages += 1

        except Exception as e:
            logger.error(f"   -> 페이지 {page_num} 처리 중 오류 발생: {e}", exc_info=True)
            failed_pages += 1

    # --- 3. 최종 결과 요약 ---
    logger.info("\n" + "="*80)
    logger.info("📊 테스트 결과 요약")
    logger.info(f"   - 총 시도 페이지: {len(TEST_IMAGE_FILES)}")
    logger.info(f"   - 성공 페이지: {processed_pages}")
    logger.info(f"   - 실패 페이지: {failed_pages}")
    logger.info(f"   - 총 소요 시간: {time.time() - start_time:.2f} 초")
    logger.info("="*80)

    # Mock DB 요약 출력
    print("\n--- 최종 Mock DB 상태 요약 ---")
    print_mock_db_summary()

    # 테스트 성공/실패 판정
    assert failed_pages == 0, f"{failed_pages}개 페이지 처리 실패"
    assert processed_pages == len(TEST_IMAGE_FILES), "모든 페이지가 처리되지 않았습니다."

    logger.success("🎉 실제 분석 통합 테스트 성공!")

# --- 테스트 실행 (스크립트 직접 실행 시) ---
if __name__ == "__main__":
    logger.remove()
    logger.add(sys.stderr, level="INFO")
    # pytest로 실행하는 것을 권장 (fixture, 옵션 등 활용)
    # pytest.main(['-s', '-v', __file__])
    print("이 테스트는 pytest로 실행하는 것을 권장합니다:")
    print(f"pytest -s -v {__file__}")
    print(f"pytest -s -v {__file__} --rerun-analysis  (캐시 무시)")