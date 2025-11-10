# -*- coding: utf-8 -*-
"""
SmartEyeSsen 통합 테스트 (다중 이미지, v2.1 스키마)
===================================================

프로젝트 생성부터 여러 페이지 추가, 배치 분석(정렬 포함)까지
전체 파이프라인을 테스트하고, 각 페이지의 정렬 결과를 상세히 출력합니다.

Phase 3.1, 3.2 구현 및 v2.1 스키마 마이그레이션 검증용.
포맷팅 단계는 검증하지 않습니다.

실행 방법:
    cd /home/jongyoung3/Smart_Demo/Project/backend
    python test_full_pipeline_multi_image.py
"""

import sys
import os
import io # BytesIO 사용을 위해 추가
import cv2
from pathlib import Path
from typing import List, Dict, Optional
from loguru import logger
import time
import asyncio # 비동기 함수 실행을 위해 추가

# --- FastAPI UploadFile 임포트 ---
# fastapi.datastructures.UploadFile 대신 fastapi.UploadFile 사용 (FastAPI 최신 버전 기준)
from fastapi import UploadFile

# 프로젝트 루트를 Python 경로에 추가
project_root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(project_root / 'backend'))

# --- 서비스 모듈 임포트 ---
# Phase 3.1 서비스
from backend.app.services.project_service import (
    create_new_project,
    add_new_page,
    list_pages_for_project
)
# Phase 3.2 서비스 및 Mock DB 초기화/조회
from backend.app.services.batch_analysis import (
    analyze_project_batch,
    get_project_mock,
    get_page_mock,
    get_latest_version_mock,
)
# DB Saver (v2.1 스키마 조회용)
from backend.app.services.db_saver import (
    get_question_groups_by_page,
    get_question_elements_by_group,
    print_mock_db_summary
)
# Mock 모델 (타입 힌팅 및 시각화 헬퍼용)
from backend.app.services.mock_models import MockElement, MockTextContent

# Sorter 결과 출력 헬퍼 (test_sorter.py 에서 가져옴)
try:
    from Project.tests.backend.test_sorter import print_detailed_results, visualize_and_save_results
except ImportError:
    logger.error("test_sorter.py를 찾을 수 없습니다. Project 폴더에 있는지 확인하세요.")
    def print_detailed_results(sorted_elements, ocr_map, ai_map): logger.warning("print_detailed_results 임시 함수 사용됨.")
    def visualize_and_save_results(image, sorted_elements, output_filename_prefix): logger.warning("visualize_and_save_results 임시 함수 사용됨.")

# ============================================================================
# 테스트 설정
# ============================================================================
TEST_IMAGE_FILES = [
    "test_images/쎈 수학1-1_페이지_014.jpg",
    "test_images/쎈 수학1-1_페이지_016.jpg",
    "test_images/쎈 수학1-1_페이지_018.jpg",
    "test_images/낱개 문제지_페이지_01.jpg",
    "test_images/낱개 문제지_페이지_02.jpg",
]
OUTPUT_DIR = "test_pipeline_outputs"
os.makedirs(OUTPUT_DIR, exist_ok=True)
logger.remove()
logger.add(sys.stderr, level="INFO")
logger.add(os.path.join(OUTPUT_DIR, "test_pipeline.log"), level="DEBUG", encoding='utf-8')

# ============================================================================
# 정렬 결과 상세 출력 함수 (변경 없음)
# ============================================================================
def print_sorted_results_from_db(page_id: int) -> Optional[List[MockElement]]:
    """
    Mock DB에서 특정 페이지의 정렬 결과를 조회하여 상세하게 출력하고,
    시각화를 위한 MockElement 리스트를 반환합니다.
    """
    logger.info(f"📄 페이지 {page_id} 정렬 결과 (DB 조회):")
    groups = get_question_groups_by_page(page_id)
    if not groups:
        logger.warning("   -> 해당 페이지에 저장된 그룹 정보가 없습니다.")
        return None

    print("\n" + "="*80)
    print(f"    [ 페이지 {page_id} Sorter 정렬 결과 상세 (DB 기준) ]")
    print("="*80)

    element_map: Dict[int, Dict] = {}
    all_elements_sorted_data = []

    groups.sort(key=lambda g: g['start_y'])
    global_order_counter = 0

    for group in groups:
        group_id = group['question_group_id']
        anchor_id = group.get('anchor_element_id')
        group_type = group.get('group_type', 'N/A')
        print(f"--- Group ID: {group_id} (Type: {group_type}, Anchor: {anchor_id}) ---")

        elements_in_group = get_question_elements_by_group(group_id)
        elements_in_group.sort(key=lambda qe: qe['order_in_group'])

        for qe in elements_in_group:
            elem_id = qe['element_id']
            order_in_grp = qe['order_in_group']
            order_in_q = qe.get('order_in_question', global_order_counter)

            print(f"  [Elem {elem_id} | GrpOrder {order_in_grp} | GlbOrder {order_in_q}]")

            elem_data = {
                'element_id': elem_id, 'page_id': page_id,
                'class_name': f'class_{elem_id % 9}', 'confidence': 0.9,
                'bbox_x': 100 + (elem_id % 10) * 50, 'bbox_y': 100 + order_in_q * 40,
                'bbox_width': 200 + (elem_id % 5) * 20, 'bbox_height': 30 + (elem_id % 3) * 5,
                'order_in_question': order_in_q, 'group_id': group_id, 'order_in_group': order_in_grp,
                'bbox': [100 + (elem_id % 10) * 50, 100 + order_in_q * 40,
                         200 + (elem_id % 5) * 20, 30 + (elem_id % 3) * 5]
            }
            element_map[elem_id] = elem_data
            all_elements_sorted_data.append(elem_data)
            global_order_counter +=1

    print("="*80 + "\n")
    all_elements_sorted_data.sort(key=lambda x: x.get('order_in_question', float('inf')))

    mock_elements_for_vis: List[MockElement] = []
    try:
        for elem_data in all_elements_sorted_data:
            required_fields = ['element_id', 'class_name', 'confidence', 'bbox_x', 'bbox_y', 'bbox_width', 'bbox_height']
            if all(field in elem_data for field in required_fields):
                 mock_elements_for_vis.append(MockElement(**elem_data))
            else: logger.warning(f"   -> Element ID {elem_data.get('element_id', 'N/A')} 데이터 불완전.")
    except Exception as model_e:
        logger.error(f"   -> MockElement 변환 오류: {model_e}", exc_info=True); return None
    return mock_elements_for_vis

# ============================================================================
# 메인 테스트 함수
# ============================================================================
async def run_pipeline_test():
    """ 전체 파이프라인 테스트 실행 """
    logger.info("🚀 전체 파이프라인 통합 테스트 시작 (다중 이미지)")
    start_time = time.time(); test_passed = True

    # --- Step 1: Mock DB 초기화 ---
    logger.info("[1/5] Mock DB 초기화 중...")
    try:
        from app.services.batch_analysis import mock_projects, mock_pages, mock_text_versions
        from app.services.db_saver import mock_question_groups, mock_question_elements
        mock_projects.clear(); mock_pages.clear(); mock_text_versions.clear()
        mock_question_groups.clear(); mock_question_elements.clear()
        from app.services import batch_analysis as ba_service, db_saver as db_service
        ba_service._next_project_id = 1; ba_service._next_page_id = 1; ba_service._next_version_id = 1
        db_service._next_question_group_id = 1; db_service._next_qe_id = 1
        logger.info("   -> Mock DB 및 카운터 초기화 완료.")
    except Exception as init_e: logger.error(f"   -> Mock DB 초기화 실패: {init_e}", exc_info=True); return

    project_id = -1

    # --- Step 2: 프로젝트 생성 (Phase 3.1) ---
    logger.info("[2/5] 새 프로젝트 생성 중...")
    try:
        project_data = create_new_project(user_id=1, doc_type_id=1, project_name="다중 이미지 테스트 프로젝트")
        project_id = project_data['project_id']; logger.success(f"   -> 프로젝트 생성 성공! ID: {project_id}")
    except Exception as e: logger.error(f"   -> 프로젝트 생성 실패: {e}", exc_info=True); test_passed = False; return

    # --- Step 3: 페이지 추가 (Phase 3.1) ---
    logger.info(f"[3/5] {len(TEST_IMAGE_FILES)}개 페이지 추가 중...")
    page_ids = []; image_paths_map = {}
    for i, img_rel_path in enumerate(TEST_IMAGE_FILES):
        img_full_path = str(project_root / img_rel_path)
        if not os.path.exists(img_full_path):
            logger.error(f"   -> 이미지 파일 없음: {img_full_path}"); test_passed = False; continue

        filename = os.path.basename(img_full_path)
        mime_type = "image/jpeg" if filename.lower().endswith((".jpg", ".jpeg")) else "image/png"

        try:
            with open(img_full_path, "rb") as f:
                file_content = f.read(); file_like_object = io.BytesIO(file_content)

                # --- 수정: 실제 FastAPI UploadFile 객체 생성 (content_type 제거) ---
                # UploadFile 생성자는 filename과 file 인자만 받음 (headers는 선택)
                upload_file = UploadFile(filename=filename, file=file_like_object)
                # ----------------------------------------------------------------

                page_data = await add_new_page(project_id=project_id, page_number=i + 1, image_file=upload_file)
                page_ids.append(page_data['page_id']); image_paths_map[page_data['page_id']] = img_full_path
                logger.info(f"   -> 페이지 {i+1} 추가 성공. ID: {page_data['page_id']}, Path: {page_data.get('image_path', 'N/A')}")
        except Exception as e: logger.error(f"   -> 페이지 {i+1} ('{filename}') 추가 실패: {e}", exc_info=True); test_passed = False

    if not page_ids: logger.error("추가된 페이지 없어 테스트 중단."); test_passed = False; return

    # --- Step 4: 배치 분석 실행 (Phase 3.2) ---
    logger.info("[4/5] 프로젝트 배치 분석 실행 중...")
    analysis_result = None
    try:
        analysis_result = analyze_project_batch(project_id=project_id, document_type="question_based", use_ai_descriptions=False, api_key=None)
        if analysis_result and analysis_result.get('failed_pages', 0) > 0:
            logger.warning(f"   -> 배치 분석 완료 (부분 실패): {analysis_result['successful_pages']}/{analysis_result['processed_pages']} 성공"); test_passed = False
        elif analysis_result: logger.success(f"   -> 배치 분석 완료! 결과: {analysis_result['successful_pages']}/{analysis_result['processed_pages']} 성공")
        else: logger.error("   -> 배치 분석 결과 없음."); test_passed = False
    except Exception as e: logger.error(f"   -> 배치 분석 중 오류: {e}", exc_info=True); test_passed = False

    # --- Step 5: 정렬 결과 확인 및 시각화 ---
    logger.info("[5/5] 각 페이지 정렬 결과 확인 및 시각화...")
    project_pages = list_pages_for_project(project_id)
    for page_data in project_pages:
        page_id = page_data['page_id']; page_num = page_data['page_number']
        logger.info(f"\n--- 페이지 {page_num} (ID: {page_id}) 결과 ---")

        page_info = get_page_mock(page_id)
        if not page_info: logger.warning("   -> 페이지 정보 없음."); continue
        analysis_status = page_info.get('analysis_status', 'N/A'); logger.info(f"   분석 상태: {analysis_status}")
        if analysis_status != 'completed':
            logger.warning("   -> 분석 미완료, 정렬 결과 확인 불가.")
            if analysis_status == 'error': test_passed = False
            continue

        sorted_elements_for_vis = print_sorted_results_from_db(page_id)

        if sorted_elements_for_vis:
            img_path = image_paths_map.get(page_id)
            if img_path:
                try:
                    image = cv2.imread(img_path)
                    if image is not None:
                        vis_filename_prefix = f"page_{page_num}_sorted"
                        output_vis_path_prefix = os.path.join(OUTPUT_DIR, vis_filename_prefix)
                        visualize_and_save_results(image, sorted_elements_for_vis, output_vis_path_prefix)
                    else: logger.warning(f"   -> 이미지 로드 실패 (시각화): {img_path}")
                except Exception as vis_e: logger.error(f"   -> 페이지 {page_num} 시각화 오류: {vis_e}")
            else: logger.warning(f"   -> 페이지 {page_id} 원본 이미지 경로 없어 시각화 불가.")
        else: logger.warning("   -> DB 정렬 요소 없어 시각화 불가.")

    print("\n--- 최종 Mock DB 상태 요약 ---"); print_mock_db_summary()

    total_time = time.time() - start_time
    if test_passed: logger.success(f"🎉 전체 파이프라인 통합 테스트 성공! (총 {total_time:.2f}초 소요)")
    else: logger.error(f"❌ 전체 파이프라인 통합 테스트 실패. 로그 확인. (총 {total_time:.2f}초 소요)")
    logger.info(f"결과 로그 및 이미지는 '{OUTPUT_DIR}' 폴더 확인.")


if __name__ == "__main__":
    asyncio.run(run_pipeline_test())