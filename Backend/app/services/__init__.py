"""
SmartEyeSsen Backend - Services Module
=======================================
비즈니스 로직 서비스 모듈

주요 서비스:
- formatter_rules: 포맷팅 규칙 정의 (코드 기반)
- formatter: 텍스트 포맷팅 처리
- sorter: 레이아웃 정렬 알고리즘
- analysis_service: 페이지 분석 파이프라인
- batch_analysis: 다중 페이지 일괄 분석
- download_service: 문서 생성 및 다운로드
"""

from .formatter_rules import (
    RuleConfig,
    QUESTION_BASED_RULES,
    READING_ORDER_RULES,
    get_rules_for_document_type,
    fetch_db_rules,
    override_rules_with_db,
    get_rule_for_class
)

from .formatter import (
    TextFormatter
)

from .sorter import (
    sort_layout_elements,
    save_sorting_results_to_db
)

from .analysis_service import analyze_page
from .batch_analysis import analyze_project_batch, analyze_project_batch_async
from .text_version_service import (
    create_text_version,
    get_current_page_text,
    save_user_edited_version,
)
from .download_service import generate_document

__all__ = [
    # Formatter rules
    "RuleConfig",
    "QUESTION_BASED_RULES",
    "READING_ORDER_RULES",
    "get_rules_for_document_type",
    "fetch_db_rules",
    "override_rules_with_db",
    "get_rule_for_class",

    # Formatter
    "TextFormatter",

    # Sorter
    "sort_layout_elements",
    "save_sorting_results_to_db",

    # Analysis
    "analyze_page",
    "analyze_project_batch",
    "analyze_project_batch_async",

    # Text Versions
    "create_text_version",
    "get_current_page_text",
    "save_user_edited_version",

    # Download
    "generate_document",
]
