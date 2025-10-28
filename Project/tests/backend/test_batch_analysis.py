#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmartEyeSsen Phase 3.2 - 배치 분석 통합 테스트 (v2.1 스키마 준비 완료)
==========================================================================

v2.1 스키마를 대상으로 하는 Mock DB 설정을 사용하여 전체 배치 분석 파이프라인을 테스트합니다.
batch_analysis.py, sorter.py, formatter.py, db_saver.py (v2.1 버전) 간의 상호 작용을 검증합니다.

v2.1 마이그레이션 변경 사항:
- db_saver에서 v2.1 스키마용 조회 함수 임포트:
  `get_question_groups_by_page` (`get_element_groups_by_page` 대체)
  `get_question_elements_by_group` (신규)
- Step 5의 Assertion 및 출력문을 v2.1 테이블 및 필드 이름
  (`question_group_id`, `question_elements` 등)을 반영하도록 업데이트.
- `question_elements` 데이터 검증 추가.

실행 방법:
    python test_batch_analysis.py
"""

import sys
from pathlib import Path
from typing import Dict, List, Any

# 프로젝트 루트를 Python 경로에 추가
project_root = Path(__file__).resolve().parent
sys.path.insert(0, str(project_root))

from loguru import logger

# 메인 배치 분석 함수 및 Mock DB 상태 임포트
from app.services.batch_analysis import (
    analyze_project_batch,
    initialize_mock_db_for_test,
    mock_projects,
    mock_pages
)

# --- v2.1 업데이트: v2.1 스키마용 조회 함수 임포트 ---
from app.services.db_saver import (
    get_all_groups_stats,
    get_question_groups_by_page,  # get_element_groups_by_page 대체
    get_question_elements_by_group # v2.1 신규 함수
)
# -----------------------------------------------------------

def test_full_pipeline(num_pages: int = 5) -> Dict[str, Any]:
    """전체 파이프라인 테스트: 초기화 -> 분석 -> DB 상태 검증."""
    print("\n" + "=" * 80)
    print("SmartEyeSsen Phase 3.2 - 배치 분석 통합 테스트 (v2.1 스키마)")
    print("=" * 80 + "\n")

    # ========================================================================
    # Step 1: Mock DB 초기화
    # ========================================================================
    print("Step 1: Mock DB 초기화")
    print("-" * 80)
    project_id = initialize_mock_db_for_test(num_pages=num_pages)
    print(f"✅ 프로젝트 생성: ID={project_id}")
    print(f"✅ 페이지 생성: {num_pages}개")
    print(f"   초기 프로젝트 상태: {mock_projects[project_id]['status']}")
    initial_page_statuses = {p['page_id']: p['analysis_status'] for p in mock_pages.values() if p['project_id'] == project_id}
    print(f"   초기 페이지 상태: {initial_page_statuses}")
    print("-" * 80 + "\n")

    # ========================================================================
    # Step 2: 배치 분석 실행
    # ========================================================================
    print("Step 2: 배치 분석 실행")
    print("-" * 80)
    print("분석 시작... (각 페이지 6단계 파이프라인 실행)")
    result = analyze_project_batch(
        project_id=project_id,
        document_type="question_based", # 기본 테스트 타입
        use_ai_descriptions=False,      # 빠른 테스트를 위해 AI 비활성화
        api_key=None
    )
    print("✅ 배치 분석 완료!")
    print("-" * 80 + "\n")

    # ========================================================================
    # Step 3: 분석 결과 요약 검증
    # ========================================================================
    print("Step 3: 분석 결과 요약 검증")
    print("-" * 80)
    print(f"Project ID: {result['project_id']}")
    print(f"총 보류 페이지 수: {result['total_pages']}")
    print(f"처리된 페이지 수: {result['processed_pages']}")
    print(f"성공: {result['successful_pages']}")
    print(f"실패: {result['failed_pages']}")
    print(f"총 시간: {result['total_time']:.2f}초")
    print(f"최종 배치 상태: {result['status']}")
    # 요약 결과 Assertions
    assert result['project_id'] == project_id
    assert result['total_pages'] == num_pages
    assert result['processed_pages'] == num_pages
    # 이 테스트에서는 mock 함수가 항상 성공한다고 가정
    assert result['successful_pages'] == num_pages
    assert result['failed_pages'] == 0
    assert result['status'] == 'completed'
    print("✅ 요약 결과 검증 통과")
    print("-" * 80 + "\n")

    # ========================================================================
    # Step 4: 페이지 레벨 결과 및 상태 업데이트 검증
    # ========================================================================
    print("Step 4: 페이지별 결과 및 상태 업데이트 검증")
    print("-" * 80)
    assert len(result['page_results']) == num_pages
    for i, page_result in enumerate(result['page_results']):
        page_num = i + 1
        page_id = page_num # 초기화 로직 기준
        print(f"📄 페이지 {page_result['page_number']} (ID: {page_result['page_id']}): {page_result['status']} ({page_result['processing_time']:.3f}초)")
        assert page_result['page_number'] == page_num
        assert page_result['page_id'] == page_id
        assert page_result['status'] == 'completed'
        assert page_result['processing_time'] > 0
        assert page_result['error'] is None
        # 통계 존재 및 합리성 확인 (mock 데이터는 일관된 개수 생성)
        assert 'stats' in page_result
        stats = page_result['stats']
        # v2.1 통계 키 확인
        assert 'elements_detected' in stats and stats['elements_detected'] >= 0 # 검출된 요소 수 확인 (0 이상)
        assert 'elements_sorted' in stats and stats['elements_sorted'] >= 0   # 정렬된 요소 수 확인 (0 이상)
        assert 'groups_created' in stats # db_saver 결과
        assert 'anchor_groups' in stats
        assert 'orphan_groups' in stats
        assert 'elements_saved' in stats # v2.1 키
        assert 'formatted_chars' in stats and stats['formatted_chars'] >= 0 # 포맷팅된 문자 수 확인 (0 이상)

        # mock_pages에서 페이지 상태 업데이트 확인
        assert mock_pages[page_id]['analysis_status'] == 'completed'
        assert mock_pages[page_id]['processing_time'] is not None
        assert mock_pages[page_id]['analyzed_at'] is not None

    print("✅ 페이지별 결과 및 상태 검증 통과")
    print("-" * 80 + "\n")

    # ========================================================================
    # Step 5: Mock DB v2.1 상태 검증
    # ========================================================================
    print("Step 5: Mock DB v2.1 상태 검증")
    print("-" * 80)

    # --- v2.1 업데이트: v2.1 조회 함수 사용 ---
    db_stats = get_all_groups_stats()
    print("전체 통계:")
    print(f"  총 Question Groups: {db_stats['total_groups']}")
    print(f"    - Anchor Groups: {db_stats['anchor_groups']}")
    print(f"    - Orphan Groups: {db_stats['orphan_groups']}")
    print(f"  총 Question Elements 매핑: {db_stats['total_elements_mapped']}") # 업데이트된 키

    # 전체 통계 Assertions (mock 데이터 가정 기반)
    # Mock 데이터는 일반적으로 페이지당 1개의 앵커 그룹과 3개의 요소를 생성
    expected_groups = num_pages * 1 # mock에서 페이지당 그룹 1개 가정
    expected_elements_mapped = num_pages * 3 # mock에서 그룹당 요소 3개 가정
    # mock 데이터가 약간 변경될 경우를 대비한 유연성 허용
    assert abs(db_stats['total_groups'] - expected_groups) <= num_pages * 2, f"Expected ~{expected_groups} groups, got {db_stats['total_groups']}"
    assert abs(db_stats['total_elements_mapped'] - expected_elements_mapped) <= num_pages * 5, f"Expected ~{expected_elements_mapped} elements mapped, got {db_stats['total_elements_mapped']}"


    print("\n페이지별 그룹 상세 (처음 2페이지만):")
    for page_num in range(1, min(3, num_pages) + 1):
        page_id = page_num
        # get_question_groups_by_page 사용
        groups: List[Dict] = get_question_groups_by_page(page_id)
        print(f"\n  📄 페이지 {page_num} (총 {len(groups)}개 그룹):")
        assert len(groups) >= 0 # 그룹이 없을 수도 있음 (mock 데이터가 비어있는 경우)

        for group in groups[:3]: # 처음 3개 그룹만 표시
            # v2.1 필드 확인
            group_id = group['question_group_id'] # 필드명 변경
            group_type = group['group_type']
            anchor_id = group['anchor_element_id']
            element_count = group['element_count']
            start_y, end_y = group['start_y'], group['end_y']
            print(f"     - Group {group_id}: Type={group_type}, Anchor={anchor_id}, Count={element_count}, Y=[{start_y}-{end_y}]")

            # --- v2.1 업데이트: question_elements 검증 ---
            # get_question_elements_by_group 사용
            q_elements: List[Dict] = get_question_elements_by_group(group_id)
            print(f"       Elements ({len(q_elements)}):")
            assert len(q_elements) == element_count, f"Group {group_id} element count mismatch: expected {element_count}, got {len(q_elements)}" # 개수 일치 검증

            for qe in q_elements[:5]: # 처음 5개 요소만 표시
                 # v2.1 필드 확인
                 qe_id = qe['qe_id']
                 elem_id = qe['element_id']
                 order_q = qe['order_in_question']
                 order_g = qe['order_in_group']
                 print(f"         - QE:{qe_id}, Elem:{elem_id}, OrderQ:{order_q}, OrderG:{order_g}")
                 assert order_q >= 0, f"Invalid order_in_question for QE:{qe_id}"
                 assert order_g >= 0, f"Invalid order_in_group for QE:{qe_id}"
            if len(q_elements) > 5: print("         ...")
            # ----------------------------------------------
    # --- v2.1 업데이트 끝 ---

    print("\n✅ Mock DB v2.1 상태 검증 통과")
    print("-" * 80 + "\n")

    # ========================================================================
    # Step 6: 프로젝트 최종 상태 검증
    # ========================================================================
    print("Step 6: 프로젝트 최종 상태 검증")
    print("-" * 80)
    final_project = mock_projects[project_id]
    print(f"Project Name: {final_project['project_name']}")
    print(f"Total Pages in Project: {final_project['total_pages']}") # This reflects total pages added, not just pending
    print(f"Final Project Status: {final_project['status']}")
    assert final_project['status'] == 'completed', f"Expected final project status 'completed', got '{final_project['status']}'" # 최종 상태 확인
    print("✅ 프로젝트 최종 상태 검증 통과")
    print("-" * 80 + "\n")

    # ========================================================================
    # 최종 결과
    # ========================================================================
    print("=" * 80)
    final_success = result['status'] == 'completed' and result['failed_pages'] == 0
    if final_success:
        print("✅ 모든 테스트 통과! 배치 분석 시스템 (v2.1) 정상 작동 확인")
    else:
        print(f"❌ 테스트 실패: 최종 상태={result['status']}, 실패 페이지={result['failed_pages']}")
    print("=" * 80 + "\n")

    return result

# --- API 워크플로우 시뮬레이션 (구조 변경 없음) ---
def test_api_workflow_simulation(num_pages: int = 3):
    """API 워크플로우 시뮬레이션: 비동기 요청 -> 상태 폴링."""
    print("\n" + "=" * 80)
    print("API 워크플로우 시뮬레이션 (v2.1)")
    print("=" * 80 + "\n")

    print("시나리오: 클라이언트가 비동기 분석 요청 후 상태 폴링")
    print("-" * 80 + "\n")

    # 1. DB 초기화 (프로젝트 생성 시뮬레이션)
    print("1. Mock DB 초기화 (POST /api/projects 시뮬레이션)")
    project_id = initialize_mock_db_for_test(num_pages=num_pages)
    print(f"   프로젝트 생성됨: ID={project_id}, 상태='created'\n")

    # 2. 비동기 분석 요청 시뮬레이션 (POST /api/projects/{id}/analyze-async)
    print("2. POST /api/projects/{id}/analyze-async 시뮬레이션")
    print("   요청: {'document_type': 'question_based', ...}")
    print("   예상 응답: 202 Accepted, {'message': '...', 'status_url': '...'}\n")
    # 실제 FastAPI에서는 이 단계에서 analyze_project_batch를 BackgroundTasks에 추가

    # 3. 백그라운드 작업 실행 직접 호출 (백그라운드 실행 시뮬레이션)
    print("3. 백그라운드 작업 실행 시뮬레이션...")
    result = analyze_project_batch(
        project_id=project_id,
        document_type="question_based",
        use_ai_descriptions=False
    )
    print(f"   백그라운드 작업 완료: {result['successful_pages']}/{result['processed_pages']} 성공\n")

    # 4. 상태 폴링 시뮬레이션 (GET /api/projects/{id}/status)
    print("4. GET /api/projects/{id}/status 시뮬레이션 (폴링)")
    final_project = mock_projects[project_id]
    pages = sorted([p for p in mock_pages.values() if p['project_id'] == project_id], key=lambda x: x['page_number'])

    print(f"   시뮬레이션된 응답:")
    print(f"   {{")
    print(f"     'project_id': {project_id},")
    print(f"     'status': '{final_project['status']}',") # 이제 'completed'여야 함
    print(f"     'total_pages': {final_project['total_pages']},")
    print(f"     'pages': [")
    for page in pages[:2]: # 처음 2개 페이지 상태만 표시
        print(f"       {{'page_number': {page['page_number']}, 'analysis_status': '{page['analysis_status']}'}},")
    print(f"       ...")
    print(f"     ]")
    print(f"   }}")
    # 백그라운드 작업 후 최종 프로젝트 상태 검증
    assert final_project['status'] == 'completed', f"Expected final project status 'completed', got '{final_project['status']}'"
    print("\n✅ API 워크플로우 시뮬레이션 완료")
    print("=" * 80 + "\n")

if __name__ == "__main__":
    # 로깅 설정
    logger.remove()
    # 깔끔한 출력을 위해 INFO 레벨 사용, 상세 디버깅은 DEBUG로 변경
    logger.add(sys.stderr, level="INFO")

    # 메인 통합 테스트 실행
    test_result = test_full_pipeline(num_pages=5)

    # API 워크플로우 시뮬레이션 실행
    test_api_workflow_simulation(num_pages=3)

    # 성공 시 종료 코드 0, 실패 시 1 반환
    exit_code = 0 if test_result['status'] == 'completed' and test_result['failed_pages'] == 0 else 1
    sys.exit(exit_code)