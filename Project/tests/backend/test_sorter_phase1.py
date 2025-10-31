# tests/backend/test_sorter_phase1.py
"""
Sorter Phase 1 테스트: 강제 전략 검증

Phase 1 목표:
- GlobalFirstStrategy로 PDF 테스트 통과
- LocalFirstStrategy로 특정 이미지 테스트 통과
"""

import pytest
import json
from pathlib import Path
from typing import List, Dict, Any

from backend.app.services.sorter_strategies import (
    sort_layout_elements_adaptive,
    LayoutProfiler,
    SortingStrategyType
)
from backend.app.services.mock_models import MockElement


# 테스트 데이터 위치
BASE_DIR = Path("tests/test_data/sorter")
INPUT_DIR = BASE_DIR / "inputs"
GOLDEN_DIR = BASE_DIR / "golden"


# ============================================================================
# Phase 1: 강제 전략 테스트
# ============================================================================

@pytest.mark.phase1
@pytest.mark.parametrize("input_file, forced_strategy, expected_pass", [
    # PDF는 GLOBAL_FIRST로 통과해야 함
    ("pdf(*.json)/page_1.json", "GLOBAL_FIRST", True),

    # 이미지는 LOCAL_FIRST로 통과해야 함
    ("이미지(*.json)/쎈 수학1-1_페이지_014.json", "LOCAL_FIRST", True),
    ("이미지(*.json)/쎈 수학1-1_페이지_016.json", "LOCAL_FIRST", True),
    ("이미지(*.json)/쎈 수학1-1_페이지_023.json", "LOCAL_FIRST", True),

    # (선택적) 교차 검증: 반대 전략으로는 실패할 수 있음
    # ("pdf(*.json)/page_1.json", "LOCAL_FIRST", False),
    # ("이미지(*.json)/쎈 수학1-1_페이지_014.json", "GLOBAL_FIRST", False),
])
def test_forced_strategy_execution(input_file, forced_strategy, expected_pass, request):
    """
    [Phase 1] 강제 전략 실행 테스트

    각 시나리오에 맞는 전략을 강제로 적용했을 때
    Golden Test를 통과/실패하는지 검증
    """

    # 1. ARRANGE (준비)
    input_file_path = INPUT_DIR / input_file
    golden_file_path = GOLDEN_DIR / input_file

    # 입력 데이터 로드
    with open(input_file_path, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)
    elements_in: List[MockElement] = [MockElement(**data) for data in elements_data]

    # Golden 데이터 로드 (--update-golden 모드가 아닐 때만 필수)
    golden_snapshot = []
    if golden_file_path.exists():
        with open(golden_file_path, 'r', encoding='utf-8') as f:
            golden_data = json.load(f)
        # Golden 데이터에서 비교용 스냅샷 추출 (element_id, group_id, order_in_group만)
        golden_snapshot = [
            {
                "element_id": elem["element_id"],
                "group_id": elem["group_id"],
                "order_in_group": elem["order_in_group"]
            }
            for elem in golden_data
        ]

    # 페이지 너비/높이 계산
    page_width = max((e.bbox_x + e.bbox_width) for e in elements_in) if elements_in else 0
    page_height = max((e.bbox_y + e.bbox_height) for e in elements_in) if elements_in else 0

    # 2. ACT (실행)
    sorted_elements: List[MockElement] = sort_layout_elements_adaptive(
        elements=elements_in,
        document_type="question_based",
        page_width=page_width,
        page_height=page_height,
        force_strategy=forced_strategy  # 🔥 강제 전략 사용
    )

    # 3. ASSERT / UPDATE (검증 또는 업데이트)
    result_snapshot = _convert_to_snapshot(sorted_elements)

    # --update-golden 플래그 확인
    if request.config.getoption("--update-golden"):
        # Golden 파일 업데이트 모드
        # 전체 데이터를 읽어와서 result_snapshot의 정렬 순서대로 재배열
        input_elements_dict = {elem.element_id: elem for elem in elements_in}

        # 정렬된 순서대로 전체 데이터 재구성
        updated_golden = []
        for snapshot_elem in result_snapshot:
            elem_id = snapshot_elem["element_id"]
            if elem_id in input_elements_dict:
                original_elem = input_elements_dict[elem_id]
                # 원본 데이터에 정렬 결과 추가
                elem_dict = {
                    "element_id": original_elem.element_id,
                    "page_id": None,
                    "class_name": original_elem.class_name,
                    "confidence": original_elem.confidence,
                    "bbox_x": original_elem.bbox_x,
                    "bbox_y": original_elem.bbox_y,
                    "bbox_width": original_elem.bbox_width,
                    "bbox_height": original_elem.bbox_height,
                    "created_at": original_elem.created_at.isoformat() if hasattr(original_elem.created_at, 'isoformat') else str(original_elem.created_at),
                    "order_in_question": snapshot_elem["order_in_group"],
                    "group_id": snapshot_elem["group_id"],
                    "order_in_group": snapshot_elem["order_in_group"],
                    "bbox": None,
                    "area": original_elem.area,
                    "y_position": original_elem.y_position,
                    "x_position": original_elem.x_position
                }
                updated_golden.append(elem_dict)

        # Golden 파일 저장
        golden_file_path.parent.mkdir(parents=True, exist_ok=True)
        with open(golden_file_path, 'w', encoding='utf-8') as f:
            json.dump(updated_golden, f, ensure_ascii=False, indent=2)

        pytest.skip(f"✅ Golden File 업데이트 완료 [{forced_strategy}]: {golden_file_path.name}")

    else:
        # 검증 모드
        if not golden_snapshot:
            pytest.fail(f"Golden File이 없습니다. 생성하려면 --update-golden 실행: {golden_file_path}")

        if expected_pass:
            # 통과 예상: Golden과 일치해야 함
            assert result_snapshot == golden_snapshot, \
                f"[{forced_strategy}] 전략으로 {input_file}를 정렬한 결과가 Golden과 일치하지 않습니다."
            print(f"✅ [{forced_strategy}] {input_file}: Golden Test 통과")
        else:
            # 실패 예상: Golden과 다를 수 있음 (교차 검증)
            if result_snapshot != golden_snapshot:
                print(f"⚠️ [{forced_strategy}] {input_file}: 예상대로 Golden과 다름 (교차 검증)")
            else:
                print(f"ℹ️ [{forced_strategy}] {input_file}: 의외로 Golden과 일치함")


# ============================================================================
# Phase 2: 자동 전략 선택 테스트
# ============================================================================

@pytest.mark.phase2
@pytest.mark.parametrize("input_file, expected_strategy", [
    ("pdf(*.json)/page_1.json", SortingStrategyType.GLOBAL_FIRST),
    ("이미지(*.json)/쎈 수학1-1_페이지_014.json", SortingStrategyType.LOCAL_FIRST),
    ("이미지(*.json)/쎈 수학1-1_페이지_016.json", SortingStrategyType.LOCAL_FIRST),
    ("이미지(*.json)/쎈 수학1-1_페이지_023.json", SortingStrategyType.LOCAL_FIRST),
])
def test_auto_strategy_selection(input_file, expected_strategy):
    """LayoutProfiler가 입력을 분석해 올바른 전략을 추천하는지 검증"""
    input_file_path = INPUT_DIR / input_file
    with open(input_file_path, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)
    elements: List[MockElement] = [MockElement(**data) for data in elements_data]

    page_width = max((e.bbox_x + e.bbox_width) for e in elements) if elements else 0
    page_height = max((e.bbox_y + e.bbox_height) for e in elements) if elements else 0

    profile = LayoutProfiler.analyze(elements, page_width, page_height)
    assert profile.recommended_strategy == expected_strategy, \
        f"{input_file}: 기대 전략 {expected_strategy.name} vs 실제 {profile.recommended_strategy.name}"


# ============================================================================
# Phase 2: 프로파일 일관성 테스트
# ============================================================================

@pytest.mark.phase2
def test_profile_consistency():
    """동일 입력에 대해 프로파일링 결과가 안정적인지 검증"""
    input_file_path = INPUT_DIR / "pdf(*.json)/page_1.json"
    with open(input_file_path, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)
    elements: List[MockElement] = [MockElement(**data) for data in elements_data]

    page_width = max((e.bbox_x + e.bbox_width) for e in elements) if elements else 0
    page_height = max((e.bbox_y + e.bbox_height) for e in elements) if elements else 0

    profiles = [LayoutProfiler.analyze(elements, page_width, page_height) for _ in range(3)]

    for i in range(1, 3):
        assert profiles[i].global_consistency_score == profiles[0].global_consistency_score
        assert profiles[i].anchor_x_std == profiles[0].anchor_x_std
        assert profiles[i].horizontal_adjacency_ratio == profiles[0].horizontal_adjacency_ratio
        assert profiles[i].recommended_strategy == profiles[0].recommended_strategy


# ============================================================================
# Phase 2: 자동 전략 실행 결과 검증
# ============================================================================

@pytest.mark.phase2
@pytest.mark.parametrize("input_file", [
    "pdf(*.json)/page_1.json",
    "이미지(*.json)/쎈 수학1-1_페이지_014.json",
    "이미지(*.json)/쎈 수학1-1_페이지_016.json",
    "이미지(*.json)/쎈 수학1-1_페이지_023.json",
])
def test_auto_strategy_execution_matches_golden(input_file, request):
    """자동 전략 선택 결과가 Golden 스냅샷과 일치하는지 검증"""
    input_file_path = INPUT_DIR / input_file
    golden_file_path = GOLDEN_DIR / input_file

    with open(input_file_path, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)
    elements_in: List[MockElement] = [MockElement(**data) for data in elements_data]

    golden_snapshot = []
    if golden_file_path.exists():
        with open(golden_file_path, 'r', encoding='utf-8') as f:
            golden_data = json.load(f)
        # Golden 데이터가 전체 요소 정보를 담고 있을 수도 있고, 스냅샷 형태일 수도 있음
        if golden_data and "element_id" in golden_data[0] and "group_id" in golden_data[0] and "order_in_group" in golden_data[0] and len(golden_data[0]) > 3:
            golden_snapshot = [
                {
                    "element_id": entry["element_id"],
                    "group_id": entry["group_id"],
                    "order_in_group": entry["order_in_group"]
                }
                for entry in golden_data
            ]
        else:
            golden_snapshot = golden_data

    page_width = max((e.bbox_x + e.bbox_width) for e in elements_in) if elements_in else 0
    page_height = max((e.bbox_y + e.bbox_height) for e in elements_in) if elements_in else 0

    sorted_elements: List[MockElement] = sort_layout_elements_adaptive(
        elements=elements_in,
        document_type="question_based",
        page_width=page_width,
        page_height=page_height,
        force_strategy=None
    )

    result_snapshot = _convert_to_snapshot(sorted_elements)

    if request.config.getoption("--update-golden"):
        golden_file_path.parent.mkdir(parents=True, exist_ok=True)
        with open(golden_file_path, 'w', encoding='utf-8') as f:
            json.dump(result_snapshot, f, ensure_ascii=False, indent=2)
        pytest.skip(f"Golden File 업데이트 완료(자동 전략): {golden_file_path.name}")

    if not golden_snapshot:
        pytest.fail(f"Golden File이 없습니다. 생성하려면 --update-golden 실행: {golden_file_path}")

    assert result_snapshot == golden_snapshot, \
        f"자동 전략으로 {input_file} 정렬한 결과가 Golden과 일치하지 않습니다."


# ============================================================================
# 헬퍼 함수
# ============================================================================

def _convert_to_snapshot(elements: List[MockElement]) -> List[Dict[str, Any]]:
    """정렬된 결과를 Golden 형식의 dict 리스트로 변환"""
    snapshot = []
    for elem in elements:
        snapshot.append({
            "element_id": elem.element_id,
            "group_id": getattr(elem, 'group_id', -99),
            "order_in_group": getattr(elem, 'order_in_group', -99)
        })
    return snapshot
