import cv2
import os
import sys
import json
import argparse  # 인자 파싱을 위해 추가
from datetime import datetime
from glob import glob  # 파일 검색을 위해 추가
from loguru import logger

# ------------------------------------------------------------------
# Phase 2 서비스 모듈 임포트
# ------------------------------------------------------------------
try:
    from backend.app.services.analysis_service import AnalysisService
    from backend.app.services.sorter import sort_layout_elements
    from backend.app.services.formatter import TextFormatter
    # MockElement 외 다른 모델도 필요할 수 있으므로 유연하게 대처
    from backend.app.services.mock_models import MockElement, MockTextContent, USE_PYDANTIC
except ImportError:
    print("오류: 'backend' 폴더 구조를 찾을 수 없습니다.")
    print("이 스크립트를 'Project/' 폴더의 최상위에서 실행해주세요.")
    sys.exit(1)

# ------------------------------------------------------------------
# ⚠️ 여기에 테스트할 정보 입력
# ------------------------------------------------------------------
IMAGE_PATH = "./test_images/낱개 문제지_페이지_01.jpg"
OPENAI_API_KEY = "sk-..." # 실제 키로 변경
DOC_TYPE_ID = 1
DOC_TYPE_NAME = "question_based" if DOC_TYPE_ID == 1 else "reading_order"
OUTPUT_DIR = "test_outputs"  # 출력 디렉토리 변수화
# ------------------------------------------------------------------

# === 중간 결과 저장/로드 함수 ===

def save_intermediate_results(data, filename_prefix):
    """분석 결과를 JSON 파일로 저장합니다."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{filename_prefix}_{timestamp}.json"
    filepath = os.path.join(OUTPUT_DIR, filename)

    serializable_data = []
    if data:
        # Pydantic 모델이나 to_dict 메소드가 있는 객체 처리
        if isinstance(data, list):
            for item in data:
                if hasattr(item, 'model_dump'):
                    serializable_data.append(item.model_dump(mode='json'))
                elif hasattr(item, 'dict'):
                    serializable_data.append(item.dict())
                elif hasattr(item, 'to_dict'):
                     serializable_data.append(item.to_dict())
                else:
                    serializable_data.append(item)
        elif isinstance(data, dict):
             serializable_data = {k: (v.model_dump(mode='json') if hasattr(v, 'model_dump') else v) for k, v in data.items()}
        else:
            logger.warning(f"{filename_prefix} 데이터를 직렬화할 수 없습니다.")
            return

    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(serializable_data, f, ensure_ascii=False, indent=2)
        logger.info(f"💾 중간 결과 저장 완료: {filepath}")
        return filepath
    except Exception as e:
        logger.error(f"💾 중간 결과 저장 실패 ({filename}): {e}")
        return None

def load_intermediate_results(filename_prefix):
    """가장 최근에 저장된 중간 결과 JSON 파일을 로드합니다."""
    try:
        list_of_files = glob(os.path.join(OUTPUT_DIR, f'{filename_prefix}_*.json'))
        if not list_of_files:
            logger.error(f"'{OUTPUT_DIR}' 폴더에 '{filename_prefix}_*.json' 패턴의 파일이 없습니다.")
            return None
        latest_file = max(list_of_files, key=os.path.getctime)
        logger.info(f"💾 중간 결과 로드: {latest_file}")
        with open(latest_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
            
        # Pydantic 모델로 다시 변환 (여기서는 MockElement, MockTextContent만 고려)
        if filename_prefix == "layout_elements":
            return [MockElement(**item) for item in data]
        if filename_prefix == "ocr_results":
            return [MockTextContent(**item) for item in data]
        
        return data # ai_descriptions는 dict이므로 그대로 반환
    except Exception as e:
        logger.error(f"💾 중간 결과 로드 실패: {e}")
        return None

# === 가독성 높은 결과 출력 함수 ===

# === 시각화 함수 추가 ===

# 그룹별로 다른 색상을 사용하기 위한 컬러 팔레트 (BGR 형식)
COLOR_PALETTE = [
    (255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0), (0, 255, 255),
    (255, 0, 255), (192, 192, 192), (128, 128, 128), (128, 0, 0),
    (128, 128, 0), (0, 128, 0), (128, 0, 128), (0, 128, 128), (0, 0, 128)
]

def visualize_and_save_results(image, sorted_elements, output_filename_prefix):
    """정렬된 결과를 이미지에 시각화하고 저장합니다."""
    # 이미지 로드에 실패했을 경우를 대비
    if image is None:
        logger.error("시각화를 위한 이미지가 유효하지 않습니다.")
        return

    vis_image = image.copy()
    overlay = vis_image.copy()
    alpha = 0.2  # 투명도 설정

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # 1. 모든 클래스 이름을 수집하고 각 클래스에 고유한 색상을 할당합니다.
    all_class_names = sorted(list(set(elem.class_name for elem in sorted_elements)))
    class_color_map = {name: COLOR_PALETTE[i % len(COLOR_PALETTE)] for i, name in enumerate(all_class_names)}

    # 2. 오버레이에 불투명한 박스들을 먼저 그립니다.
    for elem in sorted_elements:
        try:
            color = class_color_map.get(elem.class_name, (100, 100, 100))  # 클래스 이름으로 색상 조회
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            cv2.rectangle(overlay, (x, y), (x + w, y + h), color, -1)
        except Exception as e:
            logger.error(f"Element {getattr(elem, 'element_id', 'N/A')}의 불투명 박스 생성 중 오류: {e}")

    # 3. 원본 이미지와 오버레이를 합성합니다.
    vis_image = cv2.addWeighted(overlay, alpha, vis_image, 1 - alpha, 0)

    # 4. 합성된 이미지 위에 테두리와 텍스트를 그립니다.
    for elem in sorted_elements:
        try:
            color = class_color_map.get(elem.class_name, (100, 100, 100))  # 클래스 이름으로 색상 조회
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            
            # 바운딩 박스 테두리
            cv2.rectangle(vis_image, (x, y), (x + w, y + h), color, 2)

            # 정보 텍스트 추가 (group_id는 여전히 표시)
            group_id = getattr(elem, 'group_id', -1)
            order_in_grp = getattr(elem, 'order_in_group', -1)
            label = f"G:{group_id} O:{order_in_grp} C:{elem.class_name}"
            
            # 텍스트 배경 추가
            (text_width, text_height), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 1)
            cv2.rectangle(vis_image, (x, y - text_height - baseline), (x + text_width, y), color, -1)
            cv2.putText(vis_image, label, (x, y - baseline), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1)
        except Exception as e:
            logger.error(f"Element {getattr(elem, 'element_id', 'N/A')} 시각화 중 오류: {e}")


    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{output_filename_prefix}_visualization_{timestamp}.jpg"
    filepath = os.path.join(OUTPUT_DIR, filename)
    
    try:
        cv2.imwrite(filepath, vis_image)
        logger.info(f"🖼️  시각화 결과 저장 완료: {filepath}")
    except Exception as e:
        logger.error(f"🖼️  시각화 결과 저장 실패: {e}")


def print_detailed_results(sorted_elements, ocr_map, ai_map):
    """정렬된 결과를 그룹별로 묶어 상세 정보와 함께 출력합니다."""
    print("\n" + "="*100)
    print("    [ Sorter 정렬 결과 상세 ]")
    print("="*100)

    if not sorted_elements:
        print("정렬된 결과가 없습니다.")
        print("="*100 + "\n")
        return

    # GroupID 별로 요소들을 묶음
    grouped_elements = {}
    for elem in sorted_elements:
        group_id = getattr(elem, 'group_id', -1)
        if group_id not in grouped_elements:
            grouped_elements[group_id] = []
        grouped_elements[group_id].append(elem)

    # 그룹 ID 순서대로 정렬하여 출력
    for group_id in sorted(grouped_elements.keys()):
        print(f"--- Group ID: {group_id} ---")
        elements_in_group = sorted(grouped_elements[group_id], key=lambda x: getattr(x, 'order_in_group', -1))
        
        for elem in elements_in_group:
            order_in_grp = getattr(elem, 'order_in_group', -1)
            elem_id = elem.element_id
            
            print(f"  [Elem {elem_id} | GrpOrder {order_in_grp}] Class: {elem.class_name:<20} BBox: ({elem.bbox_y}, {elem.bbox_x})")
            
            # OCR 텍스트 출력
            if elem_id in ocr_map:
                ocr_text = ocr_map[elem_id].replace('\n', ' ')
                print(f"    - OCR: {ocr_text[:80] + '...' if len(ocr_text) > 80 else ocr_text}")
            
            # AI 설명 출력
            if str(elem_id) in ai_map: # JSON key는 문자열
                ai_desc = ai_map[str(elem_id)].replace('\n', ' ')
                print(f"    - AI Desc: {ai_desc[:80] + '...' if len(ai_desc) > 80 else ai_desc}")
        print("-" * 50)
        
    print("="*100 + "\n")


# === 파이프라인 실행 함수 ===

def run_full_pipeline(image_path, api_key, doc_type_id, doc_type_name):
    """분석, 정렬, 포맷팅 파이프라인 전체를 실행하고 결과를 저장합니다."""
    # 로그 레벨 설정 (기존 INFO 대신 DEBUG 사용)
    # logger.remove() # 필요시 기존 핸들러 제거
    logger.add(sys.stderr, level="DEBUG", format="{time:YYYY-MM-DD HH:mm:ss} | {level} | {name}:{function}:{line} - {message}")

    logger.info("Phase 2 'full' 파이프라인 시작...")
    service = AnalysisService()

    try:
        model_path = service.download_model("SmartEyeSsen")
        if not service.load_model(model_path):
            return
    except Exception as e:
        logger.error(f"모델 다운로드/로드 중 오류: {e}")
        return

    image = cv2.imread(image_path)
    if image is None:
        logger.error(f"이미지를 찾을 수 없습니다: {image_path}")
        return

    page_height, page_width = image.shape[:2]
    logger.info(f"이미지 로드 완료: {image_path} ({page_width}x{page_height})")

    # 분석
    layout_elements = service.analyze_layout(image, model_choice='SmartEyeSsen')
    ocr_results = service.perform_ocr(image, layout_elements)
    ai_descriptions = {}
    if api_key and api_key != "sk-...":
        ai_descriptions = service.call_openai_api(image, layout_elements, api_key)
    else:
        logger.warning("AI 설명: API 키가 없어 건너뜁니다.")

    # 결과 저장
    save_intermediate_results(layout_elements, "layout_elements")
    save_intermediate_results(ocr_results, "ocr_results")
    save_intermediate_results(ai_descriptions, "ai_descriptions")

    # 정렬
    sorted_elements = sort_layout_elements(
        layout_elements,
        document_type=doc_type_name,
        page_width=page_width,
        page_height=page_height
    )

    # 상세 결과 출력
    ocr_map = {res.element_id: res.ocr_text for res in ocr_results}
    print_detailed_results(sorted_elements, ocr_map, ai_descriptions or {})
    
    # 시각화 결과 저장
    visualize_and_save_results(image, sorted_elements, "full_pipeline")

    logger.info("테스트 완료.")

def run_sort_only_from_json(doc_type_name):
    """저장된 JSON 파일에서 데이터를 로드하여 정렬만 테스트합니다."""
    # logger.remove() # 제거
    # logger.add(sys.stderr, level="INFO") # 제거
    logger.info("Phase 2 'sort_only' 파이프라인 시작...")

    # 데이터 로드
    layout_elements = load_intermediate_results("layout_elements")
    ocr_results = load_intermediate_results("ocr_results")
    ai_descriptions = load_intermediate_results("ai_descriptions")

    if not layout_elements or not ocr_results:
        logger.error("정렬 테스트에 필요한 layout 또는 ocr 데이터를 로드하지 못했습니다.")
        return

    # 정렬 (page_width, page_height는 bbox 최대값으로 근사)
    # elem.bbox_w -> elem.bbox_width 로 수정
    page_width = max(elem.bbox_x + elem.bbox_width for elem in layout_elements)
    # elem.bbox_h -> elem.bbox_height 로 수정
    page_height = max(elem.bbox_y + elem.bbox_height for elem in layout_elements)
    
    sorted_elements = sort_layout_elements(
        layout_elements,
        document_type=doc_type_name,
        page_width=page_width,
        page_height=page_height
    )

    # 상세 결과 출력
    ocr_map = {res.element_id: res.ocr_text for res in ocr_results}
    print_detailed_results(sorted_elements, ocr_map, ai_descriptions or {})

    # 시각화 결과 저장 (상단에 정의된 IMAGE_PATH 사용)
    try:
        image_for_vis = cv2.imread(IMAGE_PATH)
        if image_for_vis is not None:
            visualize_and_save_results(image_for_vis, sorted_elements, "sort_only")
        else:
            logger.warning(f"시각화를 위한 원본 이미지를 로드할 수 없습니다: {IMAGE_PATH}")
    except Exception as e:
        logger.error(f"시각화 중 오류 발생: {e}")

    logger.info("테스트 완료.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sorter 로직 테스트 스크립트")
    parser.add_argument(
        "--mode",
        type=str,
        choices=["full", "sort_only"],
        default="full",
        help="실행 모드를 선택합니다. 'full': 전체 파이프라인 실행, 'sort_only': 저장된 JSON으로 정렬만 테스트"
    )
    args = parser.parse_args()

    if args.mode == "full":
        run_full_pipeline(IMAGE_PATH, OPENAI_API_KEY, DOC_TYPE_ID, DOC_TYPE_NAME)
    elif args.mode == "sort_only":
        run_sort_only_from_json(DOC_TYPE_NAME)
