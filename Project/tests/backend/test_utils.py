# -*- coding: utf-8 -*-
"""
테스트용 공통 유틸리티 함수
===================================
- 시각화, 파일 저장/로드 등 여러 테스트 파일에서
  공통으로 사용되는 헬퍼 함수들을 포함합니다.
"""

import cv2
import os
import json
from datetime import datetime
from loguru import logger
from typing import List, Dict, Any, Optional, Union, overload, Literal
from pathlib import Path

# backend 서비스 모듈 임포트
from backend.app.services.mock_models import MockElement, MockTextContent

# --- 상수 정의 ---
COLOR_PALETTE = [
    (255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0), (0, 255, 255),
    (255, 0, 255), (192, 192, 192), (128, 128, 128), (128, 0, 0),
    (128, 128, 0), (0, 128, 0), (128, 0, 128), (0, 128, 128), (0, 0, 128)
]

# --- 중간 결과(캐시) 저장/로드 함수 ---

def get_cache_path(cache_dir: str, image_filename: str, data_type: str) -> str:
    """이미지 파일명과 데이터 타입에 기반한 캐시 파일 경로 생성"""
    base_filename = os.path.splitext(image_filename)[0]
    return os.path.join(cache_dir, f"{base_filename}_{data_type}.json")

def save_intermediate_results(
    cache_dir: str,
    image_filename: str,
    data_type: str,
    data: Union[List[Any], Dict[Any, Any]]
) -> None:
    """직렬화 가능한 데이터를 JSON 파일로 저장 (이미지별 캐시)"""
    os.makedirs(cache_dir, exist_ok=True)
    filepath = get_cache_path(cache_dir, image_filename, data_type)

    try:
        serializable_data: Union[List[Any], Dict[Any, Any]]
        if isinstance(data, list):
            serializable_data = [
                item.model_dump(mode='json') if hasattr(item, 'model_dump') else item.__dict__
                for item in data
            ]
        elif isinstance(data, dict):
            # --- 👇 수정된 부분 시작 👇 ---
            serializable_data = {}
            for k, v in data.items():
                if hasattr(v, 'model_dump'):
                    serializable_data[k] = v.model_dump(mode='json')
                # 기본 타입 (str, int, float 등)인지 확인하는 조건 추가
                elif isinstance(v, (str, int, float, bool)) or v is None:
                    serializable_data[k] = v  # 기본 타입은 그대로 사용
                elif hasattr(v, '__dict__'):
                    serializable_data[k] = v.__dict__ # 객체는 __dict__ 사용
                else:
                    # 처리할 수 없는 타입은 경고 후 문자열로 변환 (또는 제외)
                    logger.warning(f"직렬화할 수 없는 타입 발견: key={k}, type={type(v)}")
                    serializable_data[k] = str(v)
            # --- 👆 수정된 부분 끝 👆 ---
        else:
            logger.warning(f"{data_type} 데이터는 직렬화 불가능한 타입입니다: {type(data)}")
            return

        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(serializable_data, f, ensure_ascii=False, indent=2)
        logger.info(f"💾 중간 결과 저장 완료: {filepath}")

    except Exception as e:
        logger.error(f"💾 중간 결과 저장 실패 ({filepath}): {e}")

@overload
def load_intermediate_results(cache_dir: str, image_filename: str, data_type: Literal["layout_elements"]) -> Optional[List[MockElement]]: ...

@overload
def load_intermediate_results(cache_dir: str, image_filename: str, data_type: Literal["ocr_results"]) -> Optional[List[MockTextContent]]: ...

@overload
def load_intermediate_results(cache_dir: str, image_filename: str, data_type: Literal["ai_descriptions"]) -> Optional[Dict[str, str]]: ...

def load_intermediate_results(
    cache_dir: str,
    image_filename: str,
    data_type: str
) -> Optional[Union[List[Any], Dict[Any, Any]]]:
    """JSON 파일에서 중간 결과를 로드하여 데이터 클래스 객체로 변환"""
    filepath = get_cache_path(cache_dir, image_filename, data_type)
    if not os.path.exists(filepath):
        logger.debug(f"캐시 파일 없음: {filepath}")
        return None

    try:
        logger.info(f"💾 중간 결과 로드: {filepath}")
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)

        if data_type == "layout_elements" and isinstance(data, list):
            return [MockElement(**item) for item in data]
        if data_type == "ocr_results" and isinstance(data, list):
            return [MockTextContent(**item) for item in data]
        if data_type == "ai_descriptions" and isinstance(data, dict):
            return data
        
        return data
    except Exception as e:
        logger.error(f"💾 중간 결과 로드 실패 ({filepath}): {e}", exc_info=True)
        return None

# --- 최종 결과물 저장 함수 ---

def save_visual_artifacts(
    output_dir: str,
    image: Optional[cv2.typing.MatLike],
    sorted_elements: List[MockElement],
    ocr_map: Dict[int, str],
    ai_map: Dict[str, str],
    image_filename: Optional[str] = None
) -> Dict[str, str]:
    """분석 결과를 이미지, JSON, TXT 파일로 저장하는 헬퍼 함수"""
    os.makedirs(output_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    if image_filename:
        base_filename = f"{os.path.splitext(image_filename)[0]}_{timestamp}"
    else:
        base_filename = f"output_{timestamp}"
        
    output_paths = {}

    # 1. 시각화 이미지 저장
    if image is not None:
        vis_image = image.copy()
        overlay = vis_image.copy()
        alpha = 0.2
        all_class_names = sorted(list(set(elem.class_name for elem in sorted_elements)))
        class_color_map = {name: COLOR_PALETTE[i % len(COLOR_PALETTE)] for i, name in enumerate(all_class_names)}

        for elem in sorted_elements:
            color = class_color_map.get(elem.class_name, (100, 100, 100))
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            if w > 0 and h > 0:
                cv2.rectangle(overlay, (x, y), (x + w, y + h), color, -1)

        vis_image = cv2.addWeighted(overlay, alpha, vis_image, 1 - alpha, 0)

        for elem in sorted_elements:
            color = class_color_map.get(elem.class_name, (100, 100, 100))
            x, y, w, h = int(elem.bbox_x), int(elem.bbox_y), int(elem.bbox_width), int(elem.bbox_height)
            if w > 0 and h > 0:
                cv2.rectangle(vis_image, (x, y), (x + w, y + h), color, 2)
                group_id = getattr(elem, 'group_id', -1)
                order_in_grp = getattr(elem, 'order_in_group', -1)
                label = f"G:{group_id} O:{order_in_grp} C:{elem.class_name}"
                (text_width, text_height), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
                text_y = max(y, text_height + baseline)
                cv2.rectangle(vis_image, (x, text_y - text_height - baseline), (x + text_width, text_y), color, -1)
                cv2.putText(vis_image, label, (x, text_y - baseline), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,0,0), 1)

        img_path = os.path.join(output_dir, f"visualization_{base_filename}.jpg")
        cv2.imwrite(img_path, vis_image)
        output_paths['image'] = img_path
        logger.info(f"🖼️  시각화 저장 완료: {img_path}")

    # 2. 정렬된 요소 JSON 저장
    json_path = os.path.join(output_dir, f"sorted_elements_{base_filename}.json")
    serializable_elements = [elem.model_dump(mode='json') if hasattr(elem, 'model_dump') else elem.__dict__ for elem in sorted_elements]
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(serializable_elements, f, ensure_ascii=False, indent=2)
    output_paths['json'] = json_path
    logger.info(f"💾 JSON 결과 저장 완료: {json_path}")

    # 3. OCR 및 AI 텍스트 저장
    txt_path = os.path.join(output_dir, f"extracted_text_{base_filename}.txt")
    with open(txt_path, 'w', encoding='utf-8') as f:
        f.write("="*80 + "\n")
        f.write(" OCR 및 AI 설명 텍스트 (정렬된 순서)\n")
        f.write("="*80 + "\n\n")
        grouped_elements: Dict[int, List[MockElement]] = {}
        for elem in sorted_elements:
            group_id = getattr(elem, 'group_id', -1)
            if group_id not in grouped_elements: grouped_elements[group_id] = []
            grouped_elements[group_id].append(elem)

        for group_id in sorted(grouped_elements.keys()):
            f.write(f"--- Group ID: {group_id} ---\n")
            elements_in_group = sorted(grouped_elements[group_id], key=lambda x: getattr(x, 'order_in_group', -1))
            for elem in elements_in_group:
                ocr_text = ocr_map.get(elem.element_id, "")
                ai_text = ai_map.get(str(elem.element_id), "")
                f.write(f"  [Elem {elem.element_id} | {elem.class_name}]\n")
                if ocr_text:
                    f.write(f"    - OCR: {ocr_text.strip()}\n")
                if ai_text:
                    f.write(f"    - AI Desc: {ai_text.strip()}\n")
            f.write("\n")
    output_paths['text'] = txt_path
    logger.info(f"📝 텍스트 결과 저장 완료: {txt_path}")

    return output_paths


def save_formatted_text(output_dir: Union[str, Path], filename: str, text: str) -> Path:
    """
    포맷팅된 텍스트를 지정된 디렉터리에 저장한다.
    """
    target_dir = Path(output_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    target_path = target_dir / filename
    target_path.write_text(text, encoding="utf-8")
    logger.info(f"📝 포맷팅 텍스트 저장: {target_path}")
    return target_path
