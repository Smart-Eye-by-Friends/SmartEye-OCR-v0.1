"""
앵커 기반 텍스트 포맷터 규칙 정의
=================================

실제 서비스는 formatting_rules DB 테이블을 참고하는 것을 목표로 하지만,
현재 구현에서는 코드 레벨의 기본 규칙을 제공하고, 향후 DB 오버라이드를
위해 동일한 구조를 유지한다.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from typing import Dict, Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


@dataclass(frozen=True)
class RuleConfig:
    """
    개별 클래스에 대한 포맷팅 규칙.

    Attributes:
        prefix: 콘텐츠 앞에 붙일 문자열.
        suffix: 콘텐츠 뒤에 붙일 문자열.
        indent: 들여쓰기 공백 수(각 라인에 적용).
        transform: formatter_utils에서 사용할 후처리 함수 이름.
        allow_empty: True면 빈 콘텐츠라도 규칙을 적용.
        keep_suffix_on_empty: 빈 콘텐츠일 때도 suffix를 유지할지 여부.
    """

    prefix: str = ""
    suffix: str = "\n"
    indent: int = 0
    transform: Optional[str] = None
    allow_empty: bool = False
    keep_suffix_on_empty: bool = False


# ---------------------------------------------------------------------------
# 기본 규칙: 문제지(question_based) 문서
# ---------------------------------------------------------------------------

QUESTION_BASED_RULES: Dict[str, RuleConfig] = {
    # 앵커
    "question type": RuleConfig(prefix="\n\n[", suffix="]\n", indent=0, transform="normalize_question_type"),
    "question number": RuleConfig(prefix="\n\n", suffix=". ", indent=0, allow_empty=False),
    "second_question_number": RuleConfig(prefix="\n   ", suffix=". ", indent=3, allow_empty=False),
    # 본문
    "question text": RuleConfig(prefix="", suffix="\n", indent=3),
    "plain text": RuleConfig(prefix="", suffix="\n", indent=0),
    "unit": RuleConfig(prefix="", suffix="\n", indent=3),
    "list": RuleConfig(prefix="   - ", suffix="\n", indent=0, transform="normalize_list"),
    "choices": RuleConfig(prefix="", suffix="\n", indent=3, transform="normalize_choices"),
    # 시각 자료
    "figure": RuleConfig(prefix="\n   [그림 설명]\n", suffix="\n\n", indent=3, transform="merge_visual_description", allow_empty=True),
    "table": RuleConfig(prefix="\n   [표 설명]\n", suffix="\n\n", indent=3, transform="merge_visual_description", allow_empty=True),
    "flowchart": RuleConfig(prefix="\n   [순서도 설명]\n", suffix="\n\n", indent=3, transform="merge_visual_description", allow_empty=True),
    # 캡션 및 메타
    "figure_caption": RuleConfig(prefix="   (그림 캡션) ", suffix="\n\n", indent=0),
    "table caption": RuleConfig(prefix="   (표 캡션) ", suffix="\n\n", indent=0),
    "table footnote": RuleConfig(prefix="     * ", suffix="\n", indent=0),
    "formula_caption": RuleConfig(prefix="   (수식 설명) ", suffix="\n", indent=0),
    "isolated_formula": RuleConfig(prefix="\n   [수식]\n", suffix="\n", indent=3, transform="isolate_formula"),
}


# ---------------------------------------------------------------------------
# 기본 규칙: 일반 문서(reading_order) 문서
# ---------------------------------------------------------------------------

READING_ORDER_RULES: Dict[str, RuleConfig] = {
    "title": RuleConfig(prefix="", suffix="\n\n", indent=0, transform="uppercase_title"),
    "heading": RuleConfig(prefix="\n", suffix="\n\n", indent=0),
    "plain text": RuleConfig(prefix="", suffix="\n\n", indent=0),
    "list": RuleConfig(prefix="", suffix="\n", indent=0, transform="normalize_reading_list"),
    "figure": RuleConfig(prefix="\n[그림] ", suffix="\n\n", indent=0, transform="merge_visual_description"),
    "table": RuleConfig(prefix="\n[표] ", suffix="\n\n", indent=0, transform="merge_visual_description"),
    "figure_caption": RuleConfig(prefix="(그림 캡션) ", suffix="\n", indent=0),
    "table caption": RuleConfig(prefix="(표 캡션) ", suffix="\n", indent=0),
    "table footnote": RuleConfig(prefix="* ", suffix="\n", indent=0),
}


RULE_MAP_BY_DOC_TYPE: Dict[str, Dict[str, RuleConfig]] = {
    "question_based": QUESTION_BASED_RULES,
    "reading_order": READING_ORDER_RULES,
}


def get_rules_for_document_type(document_type: str) -> Dict[str, RuleConfig]:
    """
    지정된 문서 타입의 규칙 사전을 복사하여 반환합니다.

    Args:
        document_type: "question_based" 또는 "reading_order"

    Returns:
        class_name → RuleConfig 매핑 (복사본)
    """
    base_rules = RULE_MAP_BY_DOC_TYPE.get(document_type)
    if base_rules is None:
        raise ValueError(f"지원하지 않는 문서 타입입니다: {document_type}")
    return {class_name: replace(rule) for class_name, rule in base_rules.items()}


def fetch_db_rules(db: "Session", doc_type_id: int) -> Dict[str, Dict[str, str]]:
    """
    DB에서 formatting_rules를 조회하여 덮어쓰기 정보를 반환합니다.

    Args:
        db: SQLAlchemy 세션
        doc_type_id: document_types.doc_type_id (1=문제지, 2=일반문서)

    Returns:
        class_name → {prefix, suffix, indent} 형태의 덮어쓰기 정보
    """
    # Import here to avoid circular dependency
    from .. import crud

    db_rules = crud.get_all_formatting_rules(db)
    if not db_rules:
        return {}

    override_dict: Dict[str, Dict[str, str]] = {}
    for rule in db_rules:
        # doc_type_id가 일치하거나 NULL(공통 규칙)인 경우만 적용
        if rule.doc_type_id is None or rule.doc_type_id == doc_type_id:
            override_dict[rule.class_name] = {
                "prefix": rule.prefix or "",
                "suffix": rule.suffix or "\n",
                "indent": str(rule.indent_level or 0),
            }

    return override_dict


def override_rules_with_db(
    base_rules: Dict[str, RuleConfig],
    db_records: Optional[Dict[str, Dict[str, str]]] = None
) -> Dict[str, RuleConfig]:
    """
    DB 레코드 정보를 사용하여 규칙을 덮어씁니다.

    Args:
        base_rules: 코드 기본 규칙 사전.
        db_records: class_name → {prefix, suffix, indent} 형태의 덮어쓰기 정보.

    Returns:
        덮어쓰기 적용된 규칙 사전.
    """
    if not db_records:
        return base_rules

    updated_rules = dict(base_rules)
    for class_name, override in db_records.items():
        rule = updated_rules.get(class_name)
        if not rule:
            continue
        updated_rules[class_name] = RuleConfig(
            prefix=override.get("prefix", rule.prefix),
            suffix=override.get("suffix", rule.suffix),
            indent=int(override.get("indent", rule.indent)),
            transform=rule.transform,
            allow_empty=rule.allow_empty,
            keep_suffix_on_empty=rule.keep_suffix_on_empty,
        )
    return updated_rules


def get_rule_for_class(
    class_name: str,
    document_type: str,
    db: Optional["Session"] = None,
    doc_type_id: Optional[int] = None
) -> RuleConfig:
    """
    주어진 클래스명에 대한 포맷팅 규칙을 반환합니다.
    DB 세션이 제공되면 DB 오버라이드를 적용합니다.

    Args:
        class_name: 레이아웃 요소 클래스명
        document_type: "question_based" 또는 "reading_order"
        db: SQLAlchemy 세션 (선택)
        doc_type_id: 문서 타입 ID (선택, db 제공 시 필요)

    Returns:
        해당 클래스의 RuleConfig
    """
    base_rules = get_rules_for_document_type(document_type)

    if db and doc_type_id:
        db_records = fetch_db_rules(db, doc_type_id)
        rules = override_rules_with_db(base_rules, db_records)
    else:
        rules = base_rules

    # 기본값 반환 (규칙이 없는 경우)
    return rules.get(class_name, RuleConfig())
