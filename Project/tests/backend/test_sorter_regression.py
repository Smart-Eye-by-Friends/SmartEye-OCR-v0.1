# -*- coding: utf-8 -*-
"""
Sorter 회귀 테스트 (Regression Test)
====================================

Adaptive Strategy Pattern 기반 Sorter의 회귀 테스트입니다.
Golden Test 방식으로 정렬 결과의 일관성을 검증합니다.

실행 방법:
---------
# 1. Golden 파일 생성 (최초 1회 또는 업데이트 시)
pytest tests/backend/test_sorter_regression.py --update-golden -v

# 2. 회귀 테스트 실행
pytest tests/backend/test_sorter_regression.py -v -m regression

# 3. 특정 테스트만 실행
pytest tests/backend/test_sorter_regression.py -k "page_1" -v
"""

import pytest
import json
from pathlib import Path
from typing import List, Dict, Any

from backend.app.services.sorter_strategies import sort_layout_elements_adaptive
from backend.app.services.mock_models import MockElement


# =============================================================================
# 테스트 데이터 설정
# =============================================================================

TEST_DATA_DIR = Path("tests/test_data/sorter")
INPUT_DIR = TEST_DATA_DIR / "inputs"
GOLDEN_DIR = TEST_DATA_DIR / "golden"


def collect_test_files():
    """
    inputs 디렉토리에서 모든 JSON 파일을 수집합니다.

    Returns:
        List[Path]: 테스트 입력 파일 경로 리스트
    """
    if not INPUT_DIR.exists():
        return []

    # 재귀적으로 모든 .json 파일 수집
    test_files = sorted(INPUT_DIR.glob("**/*.json"))
    return test_files


def get_test_id(file_path: Path) -> str:
    """
    테스트 ID 생성 (pytest 출력용)

    예: 'pdf(*.json)/page_1' 또는 '이미지(*.json)/쎈 수학1-1_페이지_014'
    """
    relative = file_path.relative_to(INPUT_DIR)
    return str(relative.with_suffix(''))


# 테스트 케이스 수집
TEST_FILES = collect_test_files()
TEST_IDS = [get_test_id(f) for f in TEST_FILES]


# =============================================================================
# 회귀 테스트
# =============================================================================

@pytest.mark.regression
@pytest.mark.parametrize("input_file", TEST_FILES, ids=TEST_IDS)
def test_sorter_adaptive_regression(input_file: Path, request):
    """
    Adaptive Sorter의 회귀 테스트

    자동 전략 선택(force_strategy=None)으로 정렬한 결과가
    Golden 파일과 일치하는지 검증합니다.

    Args:
        input_file: 입력 JSON 파일 경로
        request: pytest fixture (--update-golden 옵션 확인용)
    """

    # -------------------------------------------------------------------------
    # 1. ARRANGE: 테스트 데이터 준비
    # -------------------------------------------------------------------------

    # Golden 파일 경로 계산
    relative_path = input_file.relative_to(INPUT_DIR)
    golden_file = GOLDEN_DIR / relative_path

    # 입력 파일 로드
    with open(input_file, 'r', encoding='utf-8') as f:
        elements_data = json.load(f)

    # MockElement 객체로 변환
    elements = [MockElement(**data) for data in elements_data]

    if not elements:
        pytest.skip(f"입력 파일이 비어있습니다: {input_file.name}")

    # 페이지 크기 계산
    page_width = max((e.bbox_x + e.bbox_width) for e in elements)
    page_height = max((e.bbox_y + e.bbox_height) for e in elements)

    # -------------------------------------------------------------------------
    # 2. ACT: Adaptive Sorter 실행
    # -------------------------------------------------------------------------

    sorted_elements = sort_layout_elements_adaptive(
        elements=elements,
        document_type="question_based",
        page_width=page_width,
        page_height=page_height,
        force_strategy=None  # 🔑 자동 전략 선택
    )

    # 결과를 스냅샷으로 변환 (element_id, group_id, order_in_group만)
    result_snapshot = [
        {
            "element_id": elem.element_id,
            "group_id": getattr(elem, 'group_id', -99),
            "order_in_group": getattr(elem, 'order_in_group', -99)
        }
        for elem in sorted_elements
    ]

    # -------------------------------------------------------------------------
    # 3. ASSERT: Golden 파일과 비교 또는 업데이트
    # -------------------------------------------------------------------------

    # --update-golden 모드: Golden 파일 업데이트
    if request.config.getoption("--update-golden"):
        golden_file.parent.mkdir(parents=True, exist_ok=True)
        with open(golden_file, 'w', encoding='utf-8') as f:
            json.dump(result_snapshot, f, ensure_ascii=False, indent=2)
        pytest.skip(f"✅ Golden 파일 업데이트 완료: {relative_path}")

    # 검증 모드: Golden 파일과 비교
    if not golden_file.exists():
        pytest.fail(
            f"❌ Golden 파일이 없습니다.\n"
            f"파일: {relative_path}\n"
            f"생성 방법: pytest {__file__} --update-golden -v"
        )

    # Golden 파일 로드
    with open(golden_file, 'r', encoding='utf-8') as f:
        golden_snapshot = json.load(f)

    # 비교
    if result_snapshot != golden_snapshot:
        # 차이점 분석
        differences = []

        # 길이 차이
        if len(result_snapshot) != len(golden_snapshot):
            differences.append(
                f"요소 개수 불일치: {len(result_snapshot)} vs {len(golden_snapshot)} (golden)"
            )

        # 요소별 차이
        for i, (result, golden) in enumerate(zip(result_snapshot, golden_snapshot)):
            if result != golden:
                differences.append(
                    f"Index {i}: {result} ≠ {golden}"
                )

        # 처음 5개만 표시
        diff_msg = "\n".join(differences[:5])
        if len(differences) > 5:
            diff_msg += f"\n... (총 {len(differences)}개 차이)"

        pytest.fail(
            f"❌ 정렬 결과가 Golden과 다릅니다.\n"
            f"파일: {relative_path}\n"
            f"차이점:\n{diff_msg}"
        )

    # 테스트 통과
    print(f"✅ {relative_path}: Golden Test 통과")


# =============================================================================
# 유틸리티 테스트
# =============================================================================

@pytest.mark.regression
def test_all_test_files_have_golden():
    """
    모든 입력 파일에 대응하는 Golden 파일이 있는지 확인

    이 테스트는 Golden 파일 생성 후 실행해야 합니다.
    """
    missing_golden = []

    for input_file in TEST_FILES:
        relative_path = input_file.relative_to(INPUT_DIR)
        golden_file = GOLDEN_DIR / relative_path

        if not golden_file.exists():
            missing_golden.append(str(relative_path))

    if missing_golden:
        pytest.fail(
            f"❌ {len(missing_golden)}개 Golden 파일이 누락되었습니다:\n" +
            "\n".join(f"  - {f}" for f in missing_golden[:10]) +
            (f"\n  ... (총 {len(missing_golden)}개)" if len(missing_golden) > 10 else "")
        )


@pytest.mark.regression
def test_no_orphan_golden_files():
    """
    입력 파일 없이 Golden 파일만 있는 경우 감지

    정리가 필요한 오래된 Golden 파일을 찾습니다.
    """
    if not GOLDEN_DIR.exists():
        pytest.skip("Golden 디렉토리가 없습니다.")

    golden_files = set(GOLDEN_DIR.glob("**/*.json"))
    input_files = set(TEST_FILES)

    # 상대 경로로 변환
    golden_relatives = {f.relative_to(GOLDEN_DIR) for f in golden_files}
    input_relatives = {f.relative_to(INPUT_DIR) for f in input_files}

    # 차이 확인
    orphan_goldens = golden_relatives - input_relatives

    if orphan_goldens:
        pytest.fail(
            f"⚠️ {len(orphan_goldens)}개 Golden 파일이 입력 없이 존재합니다:\n" +
            "\n".join(f"  - {f}" for f in sorted(orphan_goldens)[:10]) +
            (f"\n  ... (총 {len(orphan_goldens)}개)" if len(orphan_goldens) > 10 else "") +
            "\n\n정리가 필요할 수 있습니다."
        )


# =============================================================================
# 테스트 실행 정보
# =============================================================================

if __name__ == "__main__":
    print(f"""
    Sorter 회귀 테스트
    ================

    입력 파일: {len(TEST_FILES)}개
    위치: {INPUT_DIR}

    실행 명령어:
    -----------
    # Golden 파일 생성
    pytest {__file__} --update-golden -v

    # 회귀 테스트 실행
    pytest {__file__} -v -m regression

    # 특정 파일만 테스트
    pytest {__file__} -k "page_1" -v
    """)
