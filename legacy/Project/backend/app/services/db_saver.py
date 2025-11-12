# -*- coding: utf-8 -*-
"""
SmartEyeSsen DB Saver 모듈 (v2.1 스키마 대상)
==============================================

sorter.py의 정렬 결과를 v2.1 Mock DB 구조에 저장하는 모듈.

v2.1 스키마 핵심 개념:
- question_groups: 그룹 메타데이터 저장 (앵커 기반 또는 고아 그룹)
- question_elements: 그룹과 요소 간의 N:M 매핑 및 정렬 순서 저장
- layout_elements: YOLO 탐지 결과만 저장 (정렬 관련 정보는 불변)

주요 함수:
- save_sorted_elements_to_mock_db: sorter.py 결과를 v2.1 Mock DB에 저장.
"""

from typing import List, Dict, Set, Optional, Tuple
from loguru import logger
from .mock_models import MockElement, MockQuestionGroup, MockQuestionElement # v2.1 모델 임포트
import sys

# ============================================================================
# Mock DB 구조 (v2.1 스키마)
# ============================================================================

# Mock question_groups 테이블
mock_question_groups: List[Dict] = []
"""
Mock question_groups 테이블 (v2.1 스키마)
스키마 참조: E-R_다이어그램_v2.1_스키마.md (lines 199-234)
- question_group_id: int (PK, auto_increment 시뮬레이션)
- page_id: int (FK)
- anchor_element_id: int | None (FK to layout_elements, UNIQUE)
- group_type: str ('anchor' | 'orphan')
- start_y: int
- end_y: int
- element_count: int
- created_at: datetime | None (Mock: None)
- updated_at: datetime | None (Mock: None)
"""

# Mock question_elements 테이블
mock_question_elements: List[Dict] = []
"""
Mock question_elements 테이블 (v2.1 스키마 - N:M 매핑)
스키마 참조: E-R_다이어그램_v2.1_스키마.md (lines 236-261)
- qe_id: int (PK, auto_increment 시뮬레이션)
- question_group_id: int (FK to question_groups)
- element_id: int (FK to layout_elements)
- order_in_question: int (sorter.py의 전역 정렬 순서)
- order_in_group: int (sorter.py의 그룹 내 정렬 순서)
- created_at: datetime | None (Mock: None)
"""

# Mock PK를 위한 Auto-increment 카운터
_next_question_group_id = 1
_next_qe_id = 1

# ============================================================================
# 1. sorter.py 결과 -> v2.1 Mock DB 저장 함수
# ============================================================================

def save_sorted_elements_to_mock_db(
    page_id: int,
    sorted_elements: List[MockElement],
    clear_existing: bool = True
) -> Dict[str, int]:
    """
    sorter.py에서 정렬된 요소들을 v2.1 Mock DB에 저장합니다.
    mock_question_groups와 mock_question_elements에 레코드를 생성합니다.
    mock_layout_elements 테이블은 수정하지 않습니다.

    Args:
        page_id: 처리 중인 페이지의 ID.
        sorted_elements: sorter.py가 반환한 MockElement 객체 리스트.
                         동적으로 추가된 정렬 속성(order_in_question, group_id, order_in_group)을 포함합니다.
        clear_existing: True이면, 이 page_id에 대한 기존 데이터를
                        mock_question_groups와 mock_question_elements에서 제거합니다.

    Returns:
        저장 통계 딕셔너리:
        {
            'groups_created': int,      # question_groups에 생성된 그룹 수
            'elements_saved': int,      # question_elements에 저장된 매핑 수
            'anchor_groups': int,       # 앵커 그룹 수
            'orphan_groups': int        # 고아 그룹 수
        }

    처리 흐름:
        1. 요청 시 기존 페이지 데이터 삭제.
        2. sorter.py가 할당한 고유 group_id 추출.
        3. 각 고유 group_id에 대해:
           a. 그룹 멤버 식별.
           b. 앵커 존재 여부에 따라 group_type ('anchor' 또는 'orphan') 결정.
           c. anchor_element_id (있는 경우) 찾기.
           d. start_y, end_y, element_count 계산.
           e. mock_question_groups에 레코드 생성.
           f. 그룹 내 각 요소에 대해, mock_question_elements에 레코드 생성하여
              매핑 및 정렬 순서(order_in_question, order_in_group) 저장.
        4. 통계 반환.
    """
    global _next_question_group_id, _next_qe_id

    logger.info(f"📊 DB 저장 시작 (v2.1 스키마): page_id={page_id}, 요소={len(sorted_elements)}")

    stats = {
        'groups_created': 0,
        'elements_saved': 0, # v3의 'elements_updated'에서 이름 변경
        'anchor_groups': 0,
        'orphan_groups': 0
    }

    if clear_existing:
        deleted_counts = _clear_page_data_v2_1(page_id)
        logger.debug(f"   기존 v2.1 데이터 삭제: {deleted_counts['groups']} 그룹, {deleted_counts['elements']} 요소 매핑")

    if not sorted_elements:
        logger.warning("   정렬된 요소 없음. 저장 작업 중단.")
        return stats

    # Step 1: sorter.py 결과에서 고유 group_id 추출
    # sorter.py는 0부터 시작하는 임시 그룹 ID를 할당합니다.
    unique_sorter_group_ids: Set[int] = set()
    for e in sorted_elements:
        sorter_group_id = getattr(e, 'group_id', None)
        if sorter_group_id is not None:
            unique_sorter_group_ids.add(sorter_group_id)
        else:
            logger.warning(f"   요소 ID {e.element_id}에 group_id가 없습니다 (sorter.py 오류 가능성).")

    logger.debug(f"   Sorter가 할당한 유니크 그룹 ID 수: {len(unique_sorter_group_ids)}개")

    ALLOWED_ANCHORS = ["question type", "question number", "second_question_number"]

    # sorter의 임시 group_id를 새로운 question_group_id(PK)로 매핑
    sorter_gid_to_db_gid_map: Dict[int, int] = {}

    # Step 2: sorter.py가 식별한 각 그룹 처리
    for sorter_gid in sorted(list(unique_sorter_group_ids)):
        group_members = [
            e for e in sorted_elements
            if getattr(e, 'group_id', None) == sorter_gid
        ]

        if not group_members:
            logger.warning(f"   Sorter 그룹 ID {sorter_gid}: 요소가 없습니다. 건너<0xEB><0x9B><0x84>뜁니다.")
            continue

        # Step 2a: question_groups를 위한 그룹 타입 및 앵커 결정
        anchor_elem: Optional[MockElement] = None
        # 그룹 내에서 앵커로 표시된 첫 번째 요소 찾기 (그룹 내 순서 기준)
        for member in sorted(group_members, key=lambda x: getattr(x, 'order_in_group', 0)):
             if member.class_name in ALLOWED_ANCHORS:
                 anchor_elem = member
                 break # 찾은 첫 번째 앵커를 그룹의 앵커로 사용

        group_type = 'anchor' if anchor_elem else 'orphan'
        anchor_element_id = anchor_elem.element_id if anchor_elem else None

        # Step 2b: question_groups를 위한 공간 범위 및 개수 계산
        # y_position 속성 대신 bbox_y 사용 (더 정확함)
        y_starts = [e.bbox_y for e in group_members]
        y_ends = [e.bbox_y + e.bbox_height for e in group_members]
        start_y = min(y_starts) if y_starts else 0
        end_y = max(y_ends) if y_ends else 0
        element_count = len(group_members)

        # Step 2c: mock_question_groups 레코드 생성
        current_db_question_group_id = _next_question_group_id
        group_record = {
            'question_group_id': current_db_question_group_id,
            'page_id': page_id,
            'anchor_element_id': anchor_element_id,
            'group_type': group_type,
            'start_y': start_y,
            'end_y': end_y,
            'element_count': element_count,
            'created_at': None, # Mock
            'updated_at': None  # Mock
        }
        mock_question_groups.append(group_record)
        sorter_gid_to_db_gid_map[sorter_gid] = current_db_question_group_id # 매핑 저장
        _next_question_group_id += 1

        stats['groups_created'] += 1
        if group_type == 'anchor': stats['anchor_groups'] += 1
        else: stats['orphan_groups'] += 1
        logger.trace(f"   Question Group 생성됨: DB ID={current_db_question_group_id}, Type={group_type}, Anchor={anchor_element_id}, 요소={element_count}")


        # Step 2d: mock_question_elements 레코드 생성 (N:M 매핑)
        for member in group_members:
            order_in_question = getattr(member, 'order_in_question', -1)
            order_in_group = getattr(member, 'order_in_group', -1)

            if order_in_question == -1 or order_in_group == -1:
                 logger.warning(f"   요소 ID {member.element_id}에 정렬 순서 정보가 없습니다. 저장 건너<0xEB><0x9B><0x84>뜀.")
                 continue

            qe_record = {
                'qe_id': _next_qe_id,
                'question_group_id': current_db_question_group_id, # DB PK 사용
                'element_id': member.element_id,
                'order_in_question': order_in_question,
                'order_in_group': order_in_group,
                'created_at': None # Mock
            }
            mock_question_elements.append(qe_record)
            _next_qe_id += 1
            stats['elements_saved'] += 1

    # Step 3: 요약 로깅
    logger.info(f"✅ DB 저장 완료 (v2.1 스키마): {stats}")
    return stats

# ============================================================================
# 2. 헬퍼 함수: v2.1용 페이지 데이터 삭제
# ============================================================================

def _clear_page_data_v2_1(page_id: int) -> Dict[str, int]:
    """
    특정 page_id에 대한 데이터를 v2.1 mock 테이블에서 제거합니다.
    (mock_question_groups 및 mock_question_elements).

    Args:
        page_id: 삭제할 페이지의 ID.

    Returns:
        삭제된 항목 수를 나타내는 딕셔너리: {'groups': int, 'elements': int}
    """
    global mock_question_groups, mock_question_elements

    deleted_counts = {'groups': 0, 'elements': 0}

    # Step 1: 해당 page_id와 관련된 question_group_id 식별
    page_group_ids: Set[int] = {
        g['question_group_id']
        for g in mock_question_groups
        if g['page_id'] == page_id
    }

    if not page_group_ids:
        logger.trace(f"   페이지 ID {page_id}에 대한 기존 그룹 데이터 없음.")
        return deleted_counts # 삭제할 내용 없음

    # Step 2: page_id와 관련된 그룹 제거
    initial_groups_count = len(mock_question_groups)
    mock_question_groups = [g for g in mock_question_groups if g['page_id'] != page_id]
    deleted_counts['groups'] = initial_groups_count - len(mock_question_groups)

    # Step 3: 식별된 group_id와 관련된 요소 매핑 제거
    initial_elements_count = len(mock_question_elements)
    mock_question_elements = [
        qe for qe in mock_question_elements
        if qe['question_group_id'] not in page_group_ids
    ]
    deleted_counts['elements'] = initial_elements_count - len(mock_question_elements)

    logger.trace(f"   페이지 ID {page_id} 데이터 삭제: {deleted_counts['groups']} 그룹, {deleted_counts['elements']} 요소 매핑")
    return deleted_counts

# ============================================================================
# 3. 조회 함수: v2.1 Mock DB에서 데이터 조회
# ============================================================================

def get_question_groups_by_page(page_id: int) -> List[Dict]:
    """
    특정 page_id에 대한 모든 question_groups를 mock DB에서 조회합니다.
    (v3의 get_element_groups_by_page 대체).
    """
    # 생성된 순서(question_group_id) 또는 공간적 순서(start_y)로 정렬하여 반환
    groups = [g for g in mock_question_groups if g['page_id'] == page_id]
    groups.sort(key=lambda x: x['start_y']) # 공간적 순서로 정렬
    return groups

def get_question_elements_by_group(question_group_id: int) -> List[Dict]:
    """
    특정 question_group_id에 대한 모든 question_elements 매핑을 조회합니다.
    (v2.1 신규 함수).
    """
    # 그룹 내 순서(order_in_group)로 정렬하여 반환
    elements = [
        qe for qe in mock_question_elements
        if qe['question_group_id'] == question_group_id
    ]
    elements.sort(key=lambda x: x['order_in_group']) # 그룹 내 순서로 정렬
    return elements

def get_all_groups_stats() -> Dict:
    """
    mock v2.1 DB에 대한 전체 통계를 조회합니다.
    (v3 버전 대체, 필드명 변경).
    """
    anchor_count = sum(1 for g in mock_question_groups if g['group_type'] == 'anchor')
    orphan_count = sum(1 for g in mock_question_groups if g['group_type'] == 'orphan')

    return {
        'total_groups': len(mock_question_groups),
        'anchor_groups': anchor_count,
        'orphan_groups': orphan_count,
        'total_elements_mapped': len(mock_question_elements) # 'total_elements_updated' 대체
    }

# ============================================================================
# 4. 디버깅 함수
# ============================================================================

def print_mock_db_summary() -> None:
    """현재 v2.1 Mock DB 상태 요약 출력."""
    print("=" * 70)
    print("Mock DB v2.1 상태 요약")
    print("=" * 70)

    stats = get_all_groups_stats()
    print(f"총 Question Groups 수: {stats['total_groups']}")
    print(f"  - 앵커 그룹: {stats['anchor_groups']}")
    print(f"  - 고아 그룹: {stats['orphan_groups']}")
    print(f"총 Question Elements 매핑 수: {stats['total_elements_mapped']}")
    print()

    if mock_question_groups:
        print("최근 5개 Question Groups (ID 순):")
        sorted_groups = sorted(mock_question_groups, key=lambda x: x['question_group_id'])
        for group in sorted_groups[:5]:
            print(f"  Group {group['question_group_id']} (Type: {group['group_type']}): "
                  f"{group['element_count']}개 요소, Anchor={group['anchor_element_id']}, "
                  f"Y={group['start_y']}-{group['end_y']}")
    if mock_question_elements:
        print("\n최근 5개 Question Elements (ID 순):")
        sorted_qes = sorted(mock_question_elements, key=lambda x: x['qe_id'])
        for qe in sorted_qes[:5]:
            print(f"  QE {qe['qe_id']}: Group={qe['question_group_id']}, Elem={qe['element_id']}, "
                  f"OrderQ={qe['order_in_question']}, OrderG={qe['order_in_group']}")

    print("=" * 70)

# ============================================================================
# 5. 테스트 코드 (v2.1 검증 로직 포함)
# ============================================================================

if __name__ == "__main__":
    logger.remove()
    logger.add(sys.stderr, level="DEBUG") # 상세 로깅 활성화

    print("=" * 70)
    print("DB Saver 모듈 테스트 (v2.1 스키마)")
    print("=" * 70 + "\n")

    # 테스트 데이터 생성 (sorter.py 출력 시뮬레이션)
    test_elements: List[MockElement] = [
        MockElement(element_id=1, class_name="question number", confidence=0.95, bbox_x=100, bbox_y=100, bbox_width=50, bbox_height=30),
        MockElement(element_id=2, class_name="question text", confidence=0.92, bbox_x=150, bbox_y=150, bbox_width=400, bbox_height=60),
        MockElement(element_id=3, class_name="figure", confidence=0.88, bbox_x=100, bbox_y=250, bbox_width=300, bbox_height=200),
        MockElement(element_id=4, class_name="table", confidence=0.87, bbox_x=100, bbox_y=500, bbox_width=500, bbox_height=150), # 고아 요소
    ]
    # Sorter 속성 시뮬레이션
    setattr(test_elements[0], 'order_in_question', 0); setattr(test_elements[0], 'group_id', 0); setattr(test_elements[0], 'order_in_group', 0)
    setattr(test_elements[1], 'order_in_question', 1); setattr(test_elements[1], 'group_id', 0); setattr(test_elements[1], 'order_in_group', 1)
    setattr(test_elements[2], 'order_in_question', 2); setattr(test_elements[2], 'group_id', 0); setattr(test_elements[2], 'order_in_group', 2)
    setattr(test_elements[3], 'order_in_question', 3); setattr(test_elements[3], 'group_id', 1); setattr(test_elements[3], 'order_in_group', 0) # 고아 그룹

    page_id_to_test = 1

    # --- Test 1: Save sorted elements (v2.1) ---
    print("Test 1: sorter.py 결과 저장 (v2.1)")
    print("-" * 70)
    save_stats = save_sorted_elements_to_mock_db(page_id=page_id_to_test, sorted_elements=test_elements)
    print(f"Save Stats: {save_stats}")
    assert save_stats['groups_created'] == 2, f"Expected 2 groups, got {save_stats['groups_created']}"
    assert save_stats['elements_saved'] == 4, f"Expected 4 elements saved, got {save_stats['elements_saved']}"
    assert save_stats['anchor_groups'] == 1, f"Expected 1 anchor group, got {save_stats['anchor_groups']}"
    assert save_stats['orphan_groups'] == 1, f"Expected 1 orphan group, got {save_stats['orphan_groups']}"
    print("✅ Test 1 Passed\n")

    # --- Test 2: Retrieve groups by page (v2.1) ---
    print("Test 2: 페이지별 그룹 조회 (v2.1)")
    print("-" * 70)
    groups = get_question_groups_by_page(page_id=page_id_to_test)
    print(f"Groups found for page {page_id_to_test}: {len(groups)}")
    assert len(groups) == 2, f"Expected 2 groups, found {len(groups)}"
    anchor_group = None
    orphan_group = None
    for group in groups:
        print(f"  Group {group['question_group_id']} (Type: {group['group_type']}): "
              f"Anchor={group['anchor_element_id']}, Count={group['element_count']}")
        if group['group_type'] == 'anchor': anchor_group = group
        else: orphan_group = group
    assert anchor_group is not None and anchor_group['anchor_element_id'] == 1, "Anchor group check failed"
    assert orphan_group is not None and orphan_group['anchor_element_id'] is None, "Orphan group check failed"
    print("✅ Test 2 Passed\n")

    # --- Test 3: Retrieve elements by group (v2.1) ---
    print("Test 3: 그룹별 요소 매핑 조회 (v2.1)")
    print("-" * 70)
    if anchor_group:
        anchor_group_id = anchor_group['question_group_id']
        anchor_elements = get_question_elements_by_group(anchor_group_id)
        print(f"Anchor Group (ID={anchor_group_id}) Elements: {len(anchor_elements)}")
        assert len(anchor_elements) == 3, "Anchor group element count mismatch"
        assert anchor_elements[0]['element_id'] == 1 and anchor_elements[0]['order_in_group'] == 0, "Anchor group order 0 failed"
        assert anchor_elements[1]['element_id'] == 2 and anchor_elements[1]['order_in_group'] == 1, "Anchor group order 1 failed"
        assert anchor_elements[2]['element_id'] == 3 and anchor_elements[2]['order_in_group'] == 2, "Anchor group order 2 failed"
        print("  Anchor Elements:")
        for qe in anchor_elements: print(f"    QE:{qe['qe_id']}, Elem:{qe['element_id']}, OQ:{qe['order_in_question']}, OG:{qe['order_in_group']}")
    if orphan_group:
        orphan_group_id = orphan_group['question_group_id']
        orphan_elements = get_question_elements_by_group(orphan_group_id)
        print(f"\nOrphan Group (ID={orphan_group_id}) Elements: {len(orphan_elements)}")
        assert len(orphan_elements) == 1, "Orphan group element count mismatch"
        assert orphan_elements[0]['element_id'] == 4 and orphan_elements[0]['order_in_group'] == 0, "Orphan group order 0 failed"
        print("  Orphan Elements:")
        for qe in orphan_elements: print(f"    QE:{qe['qe_id']}, Elem:{qe['element_id']}, OQ:{qe['order_in_question']}, OG:{qe['order_in_group']}")
    print("✅ Test 3 Passed\n")

    # --- Test 4: Check overall stats (v2.1) ---
    print("Test 4: 전체 통계 확인 (v2.1)")
    print("-" * 70)
    final_stats = get_all_groups_stats()
    print(f"Final Stats: {final_stats}")
    assert final_stats['total_groups'] == 2, "Total groups mismatch"
    assert final_stats['anchor_groups'] == 1, "Anchor groups mismatch"
    assert final_stats['orphan_groups'] == 1, "Orphan groups mismatch"
    assert final_stats['total_elements_mapped'] == 4, "Total elements mapped mismatch"
    print("✅ Test 4 Passed\n")

    # --- Test 5: Clear data (v2.1) ---
    print("Test 5: 데이터 삭제 (v2.1)")
    print("-" * 70)
    deleted = _clear_page_data_v2_1(page_id_to_test)
    print(f"Deletion Result: {deleted}")
    assert deleted['groups'] == 2, "Deleted groups count mismatch"
    assert deleted['elements'] == 4, "Deleted elements count mismatch"
    assert len(get_question_groups_by_page(page_id_to_test)) == 0, "Groups not cleared"
    assert len(mock_question_elements) == 0, "Elements not cleared"
    print("✅ Test 5 Passed\n")

    print_mock_db_summary()