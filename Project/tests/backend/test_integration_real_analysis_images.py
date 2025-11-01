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
import difflib

# --- 프로젝트 루트 설정 및 모듈 임포트 ---
project_root = Path(__file__).resolve().parent.parent.parent # Project/ 경로
sys.path.insert(0, str(project_root)) # <--- 이 줄은 그대로 유지합니다.

# 실제 분석 서비스
from backend.app.services.analysis_service import AnalysisService
# 정렬 서비스 (✨ Adaptive Strategy)
from backend.app.services.sorter_strategies import sort_layout_elements_adaptive, LayoutProfiler
from backend.app.services.formatter import TextFormatter
from backend.app.services.formatter_utils import clean_output
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
    from tests.backend.test_utils import (
        save_intermediate_results,
        load_intermediate_results,
        save_visual_artifacts,
        save_formatted_text,
    )
except ImportError:
    logger.error("test_utils.py 임포트 실패. Project/tests/backend/ 경로를 확인하세요.")
    # 대체 함수 정의 (테스트 실패 유도)
    def save_intermediate_results(*args, **kwargs): raise ImportError("save_intermediate_results not found")
    def load_intermediate_results(*args, **kwargs): raise ImportError("load_intermediate_results not found")
    def save_visual_artifacts(*args, **kwargs): raise ImportError("save_visual_artifacts not found")
    def save_formatted_text(*args, **kwargs): raise ImportError("save_formatted_text not found")
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

# TEST_IMAGE_FILES = [
#     project_root / "tests" / "test_images" / "2025 Korean History-1_1 1.png",
#     project_root / "tests" / "test_images" / "2025 Korean History-1_2 1.png",
#     project_root / "tests" / "test_images" / "2025 Korean History-1_3 1.png",
#     project_root / "tests" / "test_images" / "2025 Korean History-1_4 1.png"
#     # 필요에 따라 이미지 경로 추가 (Path 객체 사용)
# ]

OUTPUT_SUBDIR = "real_analysis_test" # 결과 저장용 하위 폴더
BASE_OUTPUT_DIR = project_root / "tests" / "test_pipeline_outputs"
FINAL_OUTPUT_DIR = BASE_OUTPUT_DIR / OUTPUT_SUBDIR
DOC_TYPE_NAME = "question_based" # 또는 "reading_order"
FORMATTED_GOLDEN_DIR = project_root / "tests" / "test_outputs" / "formatted_text"
FORMATTED_OUTPUT_DIR = FINAL_OUTPUT_DIR / "formatted_text"
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


def _format_and_assert(
    *,
    sorted_elements: List[MockElement],
    ocr_results: Optional[List[MockTextContent]],
    ai_descriptions: Optional[Dict[str, str]],
    page_tag: str,
    request: pytest.FixtureRequest,
    test_type: str,
    ocr_map_override: Optional[Dict[int, str]] = None,  # ✅ 새 파라미터
    ai_map_override: Optional[Dict[int, str]] = None,   # ✅ 새 파라미터
) -> str:
    """포맷팅 결과를 생성하고 골든과 비교한다.
    
    Args:
        sorted_elements: 정렬된 요소 리스트 (이미 오프셋 적용됨)
        ocr_results: OCR 결과 (레거시, ocr_map_override 우선)
        ai_descriptions: AI 설명 (레거시, ai_map_override 우선)
        page_tag: 페이지 태그
        request: pytest request fixture
        test_type: 테스트 타입 ("images" 등)
        ocr_map_override: 이미 오프셋 적용된 OCR 매핑 (우선 사용)
        ai_map_override: 이미 오프셋 적용된 AI 매핑 (우선 사용)
    """
    doc_type_id = 1 if DOC_TYPE_NAME == "question_based" else 2
    formatter = TextFormatter(doc_type_id=doc_type_id)

    # ✅ 오버라이드 매핑이 있으면 우선 사용 (레거시 호환)
    if ocr_map_override is not None:
        ocr_dict = ocr_map_override
        logger.debug(f"[{page_tag}] OCR 매핑: 오버라이드 사용")
    else:
        # 기존 방식 (호환성)
        ocr_dict = {
            res.element_id: res.ocr_text
            for res in (ocr_results or [])
            if getattr(res, "element_id", None) is not None
        }
        logger.debug(f"[{page_tag}] OCR 매핑: 레거시 방식")

    if ai_map_override is not None:
        ai_dict = ai_map_override
        logger.debug(f"[{page_tag}] AI 매핑: 오버라이드 사용")
    else:
        # 기존 방식 (str → int 변환)
        ai_dict: Dict[int, str] = {}
        for key, value in (ai_descriptions or {}).items():
            try:
                ai_dict[int(key)] = value
            except (TypeError, ValueError):
                logger.debug(f"AI 설명 키를 정수로 변환 실패: key={key}")
        logger.debug(f"[{page_tag}] AI 매핑: 레거시 방식")

    # ✅ 디버깅: 매핑 상태 확인
    logger.debug(f"[{page_tag}] 포맷팅 입력:")
    logger.debug(f"   - sorted_elements: {len(sorted_elements)}개")
    logger.debug(f"   - ocr_dict: {len(ocr_dict)}개")
    logger.debug(f"   - ai_dict: {len(ai_dict)}개")
    if sorted_elements:
        logger.debug(f"   - 첫 element_id: {sorted_elements[0].element_id}")
        logger.debug(f"   - OCR 키 샘플: {list(ocr_dict.keys())[:3]}")
        logger.debug(f"   - AI 키 샘플: {list(ai_dict.keys())[:3]}")

    formatted_text = formatter.format_page(sorted_elements, ocr_dict, ai_descriptions=ai_dict)
    cleaned_text = clean_output(formatted_text)
    
    if not cleaned_text:
        logger.error(f"❌ 포맷팅 결과가 비어 있습니다: {page_tag}")
        logger.error(f"   상세 정보는 위의 디버그 로그 참조")
    
    assert cleaned_text, f"포맷팅 결과가 비어 있습니다: {page_tag}"

    save_formatted_text(FORMATTED_OUTPUT_DIR, f"{page_tag}.txt", cleaned_text)

    golden_dir = FORMATTED_GOLDEN_DIR / test_type
    golden_path = golden_dir / f"{page_tag}.txt"
    update_golden = request.config.getoption("--update-formatted-golden")

    if update_golden:
        golden_dir.mkdir(parents=True, exist_ok=True)
        golden_path.write_text(cleaned_text, encoding="utf-8")
        logger.info(f"📝 포맷팅 골든 갱신: {golden_path}")
        return cleaned_text

    if not golden_path.exists():
        golden_dir.mkdir(parents=True, exist_ok=True)
        golden_path.write_text(cleaned_text, encoding="utf-8")
        pytest.fail(
            f"포맷팅 골든 파일이 없어 새로 생성했습니다. 검토 후 --update-formatted-golden 옵션으로 승인하세요: {golden_path}"
        )

    expected_text = clean_output(golden_path.read_text(encoding="utf-8"))
    if cleaned_text != expected_text:
        diff = "\n".join(
            difflib.unified_diff(
                expected_text.splitlines(),
                cleaned_text.splitlines(),
                fromfile="expected",
                tofile="actual",
                lineterm=""
            )
        )
        logger.error(f"⚠️ 포맷팅 골든 불일치 ({page_tag})\n{diff}")
        pytest.fail(f"포맷팅 출력이 골든과 다릅니다: {page_tag}")

    logger.info(f"   -> 포맷팅 골든 일치 확인 ({page_tag})")
    return cleaned_text

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
            
            # ✅ STEP 1: 오프셋 적용 전에 원본 ID 기반 매핑 생성
            logger.debug(f"   -> 원본 ID 기반 매핑 생성 중...")
            ocr_map_original = {
                res.element_id: res.ocr_text 
                for res in ocr_results or [] 
                if hasattr(res, 'element_id')
            }
            
            ai_map_original: Dict[int, str] = {}
            for key, value in (ai_descriptions or {}).items():
                try:
                    ai_map_original[int(key)] = value
                except (TypeError, ValueError):
                    logger.debug(f"AI 설명 키를 정수로 변환 실패: key={key}")
            
            # ✅ STEP 2: 페이지별 오프셋 정의
            element_id_offset = page_num * 1000
            logger.debug(f"   -> 페이지 {page_num} 오프셋: {element_id_offset}")
            
            # ✅ STEP 3: sorted_elements의 element_id에 오프셋 적용
            for elem in sorted_elements:
                elem.element_id = element_id_offset + elem.element_id
            
            # ✅ STEP 4: OCR/AI 매핑도 오프셋 적용 (새 ID로 재매핑)
            ocr_map_with_offset = {
                element_id_offset + orig_id: text 
                for orig_id, text in ocr_map_original.items()
            }
            
            ai_map_with_offset = {
                element_id_offset + orig_id: desc 
                for orig_id, desc in ai_map_original.items()
            }
            
            logger.debug(f"   -> 오프셋 적용 완료: OCR {len(ocr_map_with_offset)}개, AI {len(ai_map_with_offset)}개")
            
            # ✅ STEP 5: Mock DB 저장
            save_stats = save_sorted_elements_to_mock_db(
                page_id=mock_page_id,
                sorted_elements=sorted_elements,
                clear_existing=False
            )
            logger.info(f"      -> DB 저장 완료: {save_stats}")

            # --- 결과물 저장 ---
            logger.info("   -> 시각화 및 텍스트 결과 저장...")
            
            unique_filename = f"page_{page_num}_{img_path.stem}"

            output_paths = save_visual_artifacts(
                output_dir=str(FINAL_OUTPUT_DIR),
                image=image,
                sorted_elements=sorted_elements,
                ocr_map=ocr_map_with_offset,  # ✅ 오프셋 적용된 매핑
                ai_map=ai_map_with_offset,     # ✅ 오프셋 적용된 매핑 (int 키)
                image_filename=unique_filename
            )
            logger.info(f"      -> 결과 저장 완료: {output_paths}")

            # --- 포맷팅 및 검증 ---
            page_tag = f"img_{page_num:02d}_{img_path.stem}"
            
            _format_and_assert(
                sorted_elements=sorted_elements,  # 이미 오프셋 적용됨
                ocr_results=None,  # ⚠️ 사용 안 함 (직접 매핑 전달)
                ai_descriptions=None,  # ⚠️ 사용 안 함 (직접 매핑 전달)
                page_tag=page_tag,
                request=request,
                test_type="images",
                # ✅ 새 파라미터로 오프셋 적용된 매핑 전달
                ocr_map_override=ocr_map_with_offset,
                ai_map_override=ai_map_with_offset,
            )

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
