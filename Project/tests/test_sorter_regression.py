# tests/backend/test_sorter_regression.py

import pytest
import json
from pathlib import Path
from typing import List, Dict, Any

# 테스트 대상 임포트
from backend.app.services.sorter import sort_layout_elements
from backend.app.services.mock_models import MockElement

# 1. 테스트 데이터 위치 정의
BASE_DIR = Path("tests/test_data/sorter")
INPUT_DIR = BASE_DIR / "inputs"
GOLDEN_DIR = BASE_DIR / "golden"

# 2. 'inputs' 디렉토리의 모든 하위 폴더에서 .json 파일을 재귀적으로 탐색
try:
    # glob("**/*.json")을 사용하여 하위 폴더까지 모두 검색
    test_cases = list(INPUT_DIR.glob("**/*.json"))
    
    # 테스트 ID를 '폴더명/파일명' (예: '이미지(*.json)/쎈 수학1-1_페이지_014')으로 만듦
    test_case_ids = [
        str(case.relative_to(INPUT_DIR).with_suffix('')) for case in test_cases
    ]
    if not test_cases:
        print(f"Warning: Sorter 'inputs' 디렉토리가 비어있습니다: {INPUT_DIR}")
except FileNotFoundError:
    print(f"Error: Sorter 테스트 입력 디렉토리를 찾을 수 없습니다: {INPUT_DIR}")
    test_cases = []
    test_case_ids = []


@pytest.mark.parametrize("input_file_path", test_cases, ids=test_case_ids)
def test_sorter_regression(input_file_path: Path, request): # pytest 'request' fixture
    """
    'input' 파일을 Sorter로 실행한 결과가 'golden' 파일과 1:1 매칭되는지 검증
    """
    
    # 1. ARRANGE (준비)
    
    # input_file_path 예: .../inputs/이미지(*.json)/쎈 수학1-1_페이지_014.json
    
    # 'inputs' 기준 상대 경로 계산 (예: '이미지(*.json)/쎈 수학1-1_페이지_014.json')
    relative_path = input_file_path.relative_to(INPUT_DIR)
    
    # Golden 파일 경로 계산 (예: .../golden/이미지(*.json)/쎈 수학1-1_페이지_014.json)
    golden_file_path = GOLDEN_DIR / relative_path
    
    # 입력(Input) 파일 로드
    with open(input_file_path, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)
    elements_in: List[MockElement] = [MockElement(**data) for data in elements_data]
    
    # 정답(Golden) 파일 로드
    golden_snapshot: List[Dict[str, Any]] = []
    if golden_file_path.exists():
        with open(golden_file_path, 'r', encoding='utf-8') as f:
            golden_snapshot = json.load(f)
            
    # Sorter에 필요한 페이지 너비/높이 계산
    page_width = max((e.bbox_x + e.bbox_width) for e in elements_in) if elements_in else 0
    page_height = max((e.bbox_y + e.bbox_height) for e in elements_in) if elements_in else 0

    # 2. ACT (실행)
    # 실제 sorter 로직 실행
    sorted_elements: List[MockElement] = sort_layout_elements(
        elements=elements_in,
        document_type="question_based", # TODO: PDF의 경우 'reading_order'로 변경 필요
        page_width=page_width,
        page_height=page_height
    )

    # 3. ASSERT / UPDATE (검증 또는 업데이트)
    result_snapshot = _convert_to_snapshot(sorted_elements)
    
    if request.config.getoption("--update-golden"):
        # Golden 파일 경로에 폴더가 없으면 생성
        golden_file_path.parent.mkdir(parents=True, exist_ok=True)
        with open(golden_file_path, 'w', encoding='utf-8') as f:
            json.dump(result_snapshot, f, ensure_ascii=False, indent=2)
        pytest.skip(f"Golden File 업데이트 완료: {golden_file_path.name}")
    
    else:
        if not golden_snapshot:
            pytest.fail(f"Golden File이 없습니다. 생성하려면 --update-golden 실행: {golden_file_path}")

        assert result_snapshot == golden_snapshot, \
            f"Sorter 결과가 {golden_file_path.name}과(와) 일치하지 않습니다."

# --- 헬퍼 함수 ---
def _convert_to_snapshot(elements: List[MockElement]) -> List[Dict[str, Any]]:
    """정렬된 결과를 '정답' 형식의 dict 리스트로 변환"""
    snapshot = []
    for elem in elements:
        snapshot.append({
            "element_id": elem.element_id,
            "group_id": getattr(elem, 'group_id', -99),
            "order_in_group": getattr(elem, 'order_in_group', -99)
        })
    return snapshot