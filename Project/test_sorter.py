import cv2
import os
import sys
import json
import argparse
from datetime import datetime
from glob import glob
from loguru import logger
from typing import List, Dict, Union, Optional, Any # Typing 추가

# ------------------------------------------------------------------
# Phase 2 서비스 모듈 임포트
# ------------------------------------------------------------------
try:
    # backend 폴더가 sys.path에 있다고 가정
    from backend.app.services.analysis_service import AnalysisService
    from backend.app.services.sorter import sort_layout_elements
    # Formatter는 현재 테스트에서 직접 사용하지 않음
    # from backend.app.services.formatter import TextFormatter
    # --- 수정: USE_PYDANTIC 임포트 추가 ---
    from backend.app.services.mock_models import MockElement, MockTextContent, USE_PYDANTIC
except ImportError:
    print("오류: 'backend.app.services' 모듈을 찾을 수 없습니다.")
    print("이 스크립트를 'Project/' 폴더의 최상위에서 실행하거나 sys.path를 확인하세요.")
    sys.exit(1)

# ------------------------------------------------------------------
# ⚠️ 여기에 테스트할 정보 입력 (변경 없음)
# ------------------------------------------------------------------
IMAGE_PATH = "./test_images/낱개 문제지_페이지_01.jpg"
OPENAI_API_KEY = "sk-..." # 실제 키로 변경 시 AI 설명 생성
DOC_TYPE_ID = 1
DOC_TYPE_NAME = "question_based" if DOC_TYPE_ID == 1 else "reading_order"
OUTPUT_DIR = "test_outputs"
# ------------------------------------------------------------------

# === 중간 결과 저장/로드 함수 (변경 없음) ===
def save_intermediate_results(data: Union[List[Any], Dict[Any, Any]], filename_prefix: str) -> Optional[str]:
    # ... (이전 답변과 동일) ...
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{filename_prefix}_{timestamp}.json"
    filepath = os.path.join(OUTPUT_DIR, filename)
    serializable_data: Union[List[Any], Dict[Any, Any]]
    try:
        if isinstance(data, list):
            serializable_data = []
            for item in data:
                if hasattr(item, 'model_dump'): serializable_data.append(item.model_dump(mode='json'))
                elif hasattr(item, 'dict'): serializable_data.append(item.dict())
                elif hasattr(item, 'to_dict'): serializable_data.append(item.to_dict())
                else: serializable_data.append(item)
        elif isinstance(data, dict):
            serializable_data = {}
            for k, v in data.items():
                if hasattr(v, 'model_dump'): serializable_data[k] = v.model_dump(mode='json')
                elif hasattr(v, 'to_dict'): serializable_data[k] = v.to_dict()
                else: serializable_data[k] = v
        else:
             logger.warning(f"{filename_prefix} 데이터 직렬화 불가 타입: {type(data)}"); return None
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(serializable_data, f, ensure_ascii=False, indent=2)
        logger.info(f"💾 중간 결과 저장 완료: {filepath}"); return filepath
    except Exception as e: logger.error(f"💾 중간 결과 저장 실패 ({filename}): {e}"); return None

def load_intermediate_results(filename_prefix: str) -> Optional[Union[List[Any], Dict[Any, Any]]]:
    # ... (이전 답변과 동일) ...
    try:
        list_of_files = glob(os.path.join(OUTPUT_DIR, f'{filename_prefix}_*.json'))
        if not list_of_files: logger.error(f"'{OUTPUT_DIR}' 폴더에 '{filename_prefix}_*.json' 파일 없음."); return None
        latest_file = max(list_of_files, key=os.path.getctime)
        logger.info(f"💾 중간 결과 로드: {latest_file}")
        with open(latest_file, 'r', encoding='utf-8') as f: data = json.load(f)
        if filename_prefix == "layout_elements" and isinstance(data, list):
            return [MockElement(**item) for item in data if all(k in item for k in ['element_id', 'class_name', 'confidence', 'bbox_x', 'bbox_y', 'bbox_width', 'bbox_height'])]
        if filename_prefix == "ocr_results" and isinstance(data, list):
            return [MockTextContent(**item) for item in data if all(k in item for k in ['text_id', 'element_id', 'ocr_text'])]
        return data
    except Exception as e: logger.error(f"💾 중간 결과 로드 실패: {e}", exc_info=True); return None

# === 시각화 함수 (변경 없음) ===
COLOR_PALETTE = [ (255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0), (0, 255, 255),
                  (255, 0, 255), (192, 192, 192), (128, 128, 128), (128, 0, 0),
                  (128, 128, 0), (0, 128, 0), (128, 0, 128), (0, 128, 128), (0, 0, 128) ]

def visualize_and_save_results(image: Optional[cv2.typing.MatLike], sorted_elements: List[MockElement], output_filename_prefix: str):
    # ... (이전 답변과 동일) ...
    if image is None: logger.error("시각화 이미지 유효하지 않음."); return
    vis_image = image.copy(); overlay = vis_image.copy(); alpha = 0.2
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    all_class_names = sorted(list(set(elem.class_name for elem in sorted_elements)))
    class_color_map = {name: COLOR_PALETTE[i % len(COLOR_PALETTE)] for i, name in enumerate(all_class_names)}
    for elem in sorted_elements:
        try:
            color = class_color_map.get(elem.class_name, (100, 100, 100))
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            if w <= 0 or h <= 0: continue
            cv2.rectangle(overlay, (x, y), (x + w, y + h), color, -1)
        except Exception as e: logger.error(f"Element {getattr(elem, 'element_id', 'N/A')} 오버레이 오류: {e}")
    vis_image = cv2.addWeighted(overlay, alpha, vis_image, 1 - alpha, 0)
    for elem in sorted_elements:
        try:
            color = class_color_map.get(elem.class_name, (100, 100, 100))
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            if w <= 0 or h <= 0: continue
            cv2.rectangle(vis_image, (x, y), (x + w, y + h), color, 2)
            group_id = getattr(elem, 'group_id', -1); order_in_grp = getattr(elem, 'order_in_group', -1)
            label = f"G:{group_id} O:{order_in_grp} C:{elem.class_name}"
            (text_width, text_height), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 1)
            text_y = max(y, text_height + baseline)
            cv2.rectangle(vis_image, (x, text_y - text_height - baseline), (x + text_width, text_y), color, -1)
            cv2.putText(vis_image, label, (x, text_y - baseline), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1)
        except Exception as e: logger.error(f"Element {getattr(elem, 'element_id', 'N/A')} 시각화 오류: {e}")
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{os.path.basename(output_filename_prefix)}_visualization_{timestamp}.jpg"
    filepath = os.path.join(OUTPUT_DIR, filename)
    try: cv2.imwrite(filepath, vis_image); logger.info(f"🖼️ 시각화 저장 완료: {filepath}")
    except Exception as e: logger.error(f"🖼️ 시각화 저장 실패: {e}")

# === 가독성 높은 결과 출력 함수 (변경 없음) ===
def print_detailed_results(sorted_elements: List[MockElement],
                           ocr_map: Dict[int, str],
                           ai_map: Dict[str, str]):
    # ... (이전 답변과 동일) ...
    print("\n" + "="*100); print("    [ Sorter 정렬 결과 상세 ]"); print("="*100)
    if not sorted_elements: print("정렬 결과 없음."); print("="*100 + "\n"); return
    grouped_elements: Dict[int, List[MockElement]] = {}
    for elem in sorted_elements:
        group_id = getattr(elem, 'group_id', -1)
        if group_id not in grouped_elements: grouped_elements[group_id] = []
        grouped_elements[group_id].append(elem)
    for group_id in sorted(grouped_elements.keys()):
        print(f"--- Group ID: {group_id} ---")
        elements_in_group = sorted(grouped_elements[group_id], key=lambda x: getattr(x, 'order_in_group', -1))
        for elem in elements_in_group:
            order_in_grp = getattr(elem, 'order_in_group', -1); elem_id = elem.element_id
            ocr_text_raw = ocr_map.get(elem_id, ''); ocr_text_short = ocr_text_raw.replace('\n', ' ')
            ocr_text_display = ocr_text_short[:80] + '...' if len(ocr_text_short) > 80 else ocr_text_short
            ai_desc_raw = ai_map.get(str(elem_id), ''); ai_desc_short = ai_desc_raw.replace('\n', ' ')
            ai_desc_display = ai_desc_short[:80] + '...' if len(ai_desc_short) > 80 else ai_desc_short
            print(f"  [Elem {elem_id} | GrpOrder {order_in_grp}] Class: {elem.class_name:<20} BBox: ({elem.bbox_y}, {elem.bbox_x})")
            if ocr_text_raw: print(f"    - OCR: {ocr_text_display}")
            if ai_desc_raw: print(f"    - AI Desc: {ai_desc_display}")
        print("-" * 50)
    print("="*100 + "\n")

# === 파이프라인 실행 함수 (변경 없음) ===
def run_full_pipeline(image_path: str, api_key: Optional[str], doc_type_id: int, doc_type_name: str):
    # ... (이전 답변과 동일) ...
    logger.remove(); logger.add(sys.stderr, level="DEBUG")
    logger.info("Phase 2 'full' 파이프라인 시작...")
    service = AnalysisService()
    try:
        model_path = service.download_model("SmartEyeSsen")
        if not model_path or not service.load_model(model_path): return
    except Exception as e: logger.error(f"모델 로드 오류: {e}"); return
    image = cv2.imread(image_path)
    if image is None: logger.error(f"이미지 로드 실패: {image_path}"); return
    page_height, page_width = image.shape[:2]
    logger.info(f"이미지 로드 완료: {image_path} ({page_width}x{page_height})")
    layout_elements = service.analyze_layout(image, model_choice='SmartEyeSsen')
    ocr_results: List[MockTextContent] = service.perform_ocr(image, layout_elements)
    ai_descriptions: Dict[int, str] = {}
    if api_key and api_key != "sk-...": ai_descriptions = service.call_openai_api(image, layout_elements, api_key)
    else: logger.warning("AI 설명: API 키 없어 건너<0xEB><0x9B><0x84>뜀.")
    save_intermediate_results(layout_elements, "layout_elements")
    save_intermediate_results(ocr_results, "ocr_results")
    save_intermediate_results(ai_descriptions, "ai_descriptions")
    sorted_elements = sort_layout_elements(layout_elements, doc_type_name, page_width, page_height)
    ocr_map = {res.element_id: res.ocr_text for res in ocr_results if hasattr(res, 'element_id')}
    ai_map_str_keys = {str(k): v for k, v in ai_descriptions.items()} if ai_descriptions else {}
    print_detailed_results(sorted_elements, ocr_map, ai_map_str_keys)
    visualize_and_save_results(image, sorted_elements, "full_pipeline")
    logger.info("테스트 완료.")

def run_sort_only_from_json(doc_type_name: str):
    # ... (이전 답변과 동일) ...
    logger.remove(); logger.add(sys.stderr, level="INFO")
    logger.info("Phase 2 'sort_only' 파이프라인 시작...")
    loaded_layout_elements = load_intermediate_results("layout_elements")
    if not isinstance(loaded_layout_elements, list) or not all(isinstance(e, MockElement) for e in loaded_layout_elements):
        logger.error("layout_elements 로드 실패."); return
    layout_elements: List[MockElement] = loaded_layout_elements
    loaded_ocr_results = load_intermediate_results("ocr_results")
    if not isinstance(loaded_ocr_results, list) or not all(isinstance(t, MockTextContent) for t in loaded_ocr_results):
        logger.warning("ocr_results 로드 실패 (OCR 정보 없이 진행)."); ocr_results: List[MockTextContent] = []
    else: ocr_results: List[MockTextContent] = loaded_ocr_results
    ai_descriptions = load_intermediate_results("ai_descriptions")
    if not isinstance(ai_descriptions, dict): logger.warning("ai_descriptions 로드 실패."); ai_descriptions = {}
    if not layout_elements: logger.error("layout 데이터 로드 실패."); return
    page_width = max(elem.bbox_x + elem.bbox_width for elem in layout_elements) if layout_elements else 0
    page_height = max(elem.bbox_y + elem.bbox_height for elem in layout_elements) if layout_elements else 0
    sorted_elements = sort_layout_elements(layout_elements, doc_type_name, page_width, page_height)
    ocr_map = {res.element_id: res.ocr_text for res in ocr_results if hasattr(res, 'element_id')}
    ai_map_str_keys = {str(k): v for k, v in ai_descriptions.items()} if ai_descriptions else {}
    print_detailed_results(sorted_elements, ocr_map, ai_map_str_keys)
    try:
        image_for_vis = cv2.imread(IMAGE_PATH)
        if image_for_vis is not None: visualize_and_save_results(image_for_vis, sorted_elements, "sort_only")
        else: logger.warning(f"시각화 원본 이미지 로드 실패: {IMAGE_PATH}")
    except Exception as e: logger.error(f"시각화 중 오류: {e}")
    logger.info("테스트 완료.")


# === 메인 실행 블록 (변경 없음) ===
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sorter 로직 테스트 스크립트")
    parser.add_argument("--mode", type=str, choices=["full", "sort_only"], default="full", help="실행 모드 선택")
    args = parser.parse_args()
    if args.mode == "full": run_full_pipeline(IMAGE_PATH, OPENAI_API_KEY, DOC_TYPE_ID, DOC_TYPE_NAME)
    elif args.mode == "sort_only": run_sort_only_from_json(DOC_TYPE_NAME)