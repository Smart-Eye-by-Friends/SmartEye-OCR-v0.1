# -*- coding: utf-8 -*-
"""
Pytest 설정 파일 (Hooks & Fixtures)
=======================================
- 이 파일은 pytest 실행 시 자동으로 로드됩니다.
- 테스트 프레임워크에 커스텀 옵션, fixture 등을 추가할 때 사용합니다.
"""

import pytest

def pytest_addoption(parser):
    """테스트 실행 시 커스텀 커맨드 라인 옵션을 추가합니다."""
    
    # --- 기존 옵션 (유지) ---
    parser.addoption(
        "--rerun-analysis", 
        action="store_true", 
        default=False, 
        help="이 플래그가 있으면, 캐시된 중간 결과를 무시하고 전체 분석(레이아웃, OCR 등)을 강제로 재실행합니다."
    )
    
    # --- [Sorter 회귀 테스트용 옵션 추가] ---
    parser.addoption(
        "--update-golden",
        action="store_true",
        default=False,
        help="[Sorter] 현재 Sorter 결과를 Golden File로 덮어씁니다."
    )