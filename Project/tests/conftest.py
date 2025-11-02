# -*- coding: utf-8 -*-
"""
Pytest 설정 파일 (Hooks & Fixtures)
=======================================
- 이 파일은 pytest 실행 시 자동으로 로드됩니다.
- 테스트 프레임워크에 커스텀 옵션, fixture 등을 추가할 때 사용합니다.
"""

import pytest


def pytest_configure(config):
    """pytest 실행 시 커스텀 마커를 등록합니다."""

    # Phase별 테스트 마커 등록 (Sorter 리팩토링용)
    config.addinivalue_line(
        "markers",
        "phase1: Phase 1 - 강제 전략 검증 (프로토타입)"
    )
    config.addinivalue_line(
        "markers",
        "phase2: Phase 2 - 자동 전략 선택 검증"
    )
    config.addinivalue_line(
        "markers",
        "phase3: Phase 3 - Hybrid 전략 검증"
    )
    config.addinivalue_line(
        "markers",
        "phase4: Phase 4 - 성능 최적화 검증"
    )


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

    parser.addoption(
        "--update-formatted-golden",
        action="store_true",
        default=False,
        help="텍스트 포맷팅 골든 파일을 갱신합니다."
    )
