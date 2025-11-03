# -*- coding: utf-8 -*-
"""
앵커 기반 TextFormatter
========================

정렬기(sorter.py)가 부여한 앵커/그룹 정보를 활용하여
사람이 읽기 쉬운 텍스트를 생성한다.

기본 규칙은 코드 내에 정의되어 있으며, formatting_rules DB 테이블을
통해 오버라이드할 수 있도록 확장 지점을 남겨둔다.
"""

from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple, Union, TYPE_CHECKING

from loguru import logger

from .formatter_rules import (
    RuleConfig,
    get_rules_for_document_type,
    override_rules_with_db,
    fetch_db_rules,
)
from .formatter_utils import (
    RenderContext,
    apply_rule,
    clean_output,
    normalize_ai_descriptions,
    ocr_inputs_to_dict,
    split_first_line,
)

if TYPE_CHECKING:
    from sqlalchemy.orm import Session
    from ..models import LayoutElement, TextContent


DOC_TYPE_ID_MAP = {
    1: "reading_order",  # 일반문서 → reading_order
    2: "question_based",  # 수학문제 → question_based
    3: "reading_order",  # 표/차트 → reading_order
}

ANCHOR_CLASSES = {"question type", "question number", "second_question_number"}


@dataclass
class ElementGroupBundle:
    """
    앵커와 자식 요소들을 묶은 번들.

    Note: LayoutElement 타입 대신 Any 사용 (순환 import 방지)
    """

    anchor: Optional[any]  # LayoutElement
    children: List[any]  # List[LayoutElement]
    ordered_elements: List[any]  # List[LayoutElement]


class TextFormatter:
    """
    정렬된 LayoutElement + OCR 텍스트를 받아 최종 포맷팅된 문자열을 생성한다.
    """

    def __init__(
        self,
        doc_type_id: int,
        *,
        db: Optional["Session"] = None,
        use_db_rules: bool = False,
        db_rule_records: Optional[Dict[str, Dict[str, str]]] = None,
    ) -> None:
        """
        Args:
            doc_type_id: document_types 테이블의 doc_type_id (1=문제지, 2=일반 문서).
            db: SQLAlchemy 세션 (use_db_rules=True일 때 필수).
            use_db_rules: True면 DB에서 덮어쓴 규칙을 반영.
            db_rule_records: 외부에서 주입한 규칙 덮어쓰기 정보 (테스트용).
        """
        self.doc_type_id = doc_type_id
        self.document_type = DOC_TYPE_ID_MAP.get(doc_type_id, "question_based")
        base_rules = get_rules_for_document_type(self.document_type)

        # DB 규칙 오버라이드 적용
        if use_db_rules and db:
            db_records = fetch_db_rules(db, doc_type_id)
            self.rules = override_rules_with_db(base_rules, db_records)
        elif db_rule_records:
            # 테스트용: 직접 주입된 규칙 사용
            self.rules = override_rules_with_db(base_rules, db_rule_records)
        else:
            self.rules = base_rules

        self.use_db_rules = use_db_rules
        self.db = db

    # ------------------------------------------------------------------
    # 퍼블릭 API
    # ------------------------------------------------------------------
    def format_page(
        self,
        sorted_elements: List["LayoutElement"],
        ocr_texts: Union[Dict[int, str], List["TextContent"]],
        *,
        ai_descriptions: Optional[Dict[int, str]] = None,
        metadata: Optional[Dict[str, Union[str, int]]] = None,
    ) -> str:
        """
        Args:
            sorted_elements: sorter가 반환한 LayoutElement 리스트 (order_in_question 필수).
            ocr_texts: element_id → 텍스트 딕셔너리 또는 TextContent 리스트.
            ai_descriptions: AI가 생성한 시각 자료 설명 (선택).
            metadata: 출력 상단 등에 활용할 추가 정보 (현재는 미사용).
        """
        logger.info(
            f"[Formatter] format_page 시작: sorted_elements={len(sorted_elements)}, "
            f"ocr_texts_count={len(ocr_texts) if isinstance(ocr_texts, dict) else len(ocr_texts)}, "
            f"ai_descriptions_count={len(ai_descriptions) if ai_descriptions else 0}"
        )
        
        if not sorted_elements:
            logger.warning("[Formatter] sorted_elements가 비어있음 - 빈 문자열 반환")
            return ""

        ocr_dict = ocr_inputs_to_dict(ocr_texts)
        ai_dict = normalize_ai_descriptions(ai_descriptions)
        logger.debug(f"[Formatter] ocr_dict keys: {list(ocr_dict.keys())[:10]}")
        logger.debug(f"[Formatter] ai_dict keys: {list(ai_dict.keys())[:10]}")
        
        context = RenderContext(ocr_dict, ai_dict, self.rules)

        valid_elements = self._filter_elements(sorted_elements)
        logger.info(f"[Formatter] 필터링 후: {len(valid_elements)}개 유효 요소")
        
        if not valid_elements:
            logger.warning("[Formatter] 포맷팅 대상 요소가 없습니다 - 빈 문자열 반환")
            return ""

        if self.document_type == "reading_order":
            rendered = self._render_reading_order(valid_elements, context)
            result = clean_output("".join(rendered))
            logger.info(f"[Formatter] reading_order 포맷팅 완료: {len(result)}자")
            return result

        group_bundles = self._build_group_bundles(valid_elements)
        logger.info(f"[Formatter] 그룹 번들 생성: {len(group_bundles)}개")
        
        rendered_blocks = [
            self._render_group(bundle, context) for bundle in group_bundles
        ]
        combined = "".join(rendered_blocks)
        result = clean_output(combined)
        
        logger.info(
            f"[Formatter] question_based 포맷팅 완료: {len(result)}자, "
            f"{len(group_bundles)}개 그룹"
        )
        return result

    # ------------------------------------------------------------------
    # 내부 유틸 (공통)
    # ------------------------------------------------------------------
    @staticmethod
    def _filter_elements(elements: Iterable[MockElement]) -> List[MockElement]:
        """
        면적 또는 필수 속성이 없는 요소를 제외한다.
        """
        filtered: List[MockElement] = []
        for element in elements:
            try:
                area = getattr(element, "area")
            except AttributeError:
                area = element.bbox_width * element.bbox_height
            if area <= 0:
                continue
            filtered.append(element)
        return filtered

    @staticmethod
    def _element_sort_key(element: MockElement) -> Tuple[int, int, int, int]:
        large_offset = 10**7
        order_in_question = getattr(element, "order_in_question", None)
        order_key = (
            order_in_question
            if order_in_question is not None
            else large_offset + int(getattr(element, "y_position", 0))
        )
        return (
            order_key,
            int(getattr(element, "order_in_group", 0)),
            int(getattr(element, "y_position", 0)),
            int(getattr(element, "x_position", 0)),
        )

    def _build_group_bundles(
        self, elements: List[MockElement]
    ) -> List[ElementGroupBundle]:
        """
        group_id 기준으로 요소를 묶고, 앵커/자식 정보를 포함한 번들을 생성한다.
        """
        ordered_elements = sorted(elements, key=self._element_sort_key)
        grouped: "OrderedDict[Union[int, str], List[MockElement]]" = OrderedDict()

        orphan_counter = 0
        for element in ordered_elements:
            group_id = getattr(element, "group_id", None)
            if group_id is None:
                group_id = f"_orphan_{orphan_counter}"
                orphan_counter += 1
            grouped.setdefault(group_id, []).append(element)

        bundles: List[ElementGroupBundle] = []
        for elems in grouped.values():
            anchor = self._pick_anchor(elems)
            children = self._sorted_children(elems, anchor)
            bundles.append(
                ElementGroupBundle(
                    anchor=anchor, children=children, ordered_elements=elems
                )
            )
        return bundles

    @staticmethod
    def _pick_anchor(elements: Iterable[MockElement]) -> Optional[MockElement]:
        anchors = [e for e in elements if e.class_name in ANCHOR_CLASSES]
        if not anchors:
            return None
        anchors.sort(
            key=lambda e: (
                int(getattr(e, "order_in_group", 0)),
                int(getattr(e, "y_position", 0)),
                int(getattr(e, "x_position", 0)),
            )
        )
        return anchors[0]

    def _sorted_children(
        self, elements: Iterable[MockElement], anchor: Optional[MockElement]
    ) -> List[MockElement]:
        return sorted(
            [e for e in elements if e is not anchor],
            key=self._element_sort_key,
        )

    # ------------------------------------------------------------------
    # 렌더링 로직
    # ------------------------------------------------------------------
    def _render_group(self, bundle: ElementGroupBundle, context: RenderContext) -> str:
        anchor = bundle.anchor
        if anchor is None:
            return self._render_orphan_block(
                bundle.children or bundle.ordered_elements, context
            )

        if anchor.class_name == "question type":
            return self._render_section_block(anchor, bundle.children, context)
        if anchor.class_name == "question number":
            return self._render_question_block(anchor, bundle.children, context)
        if anchor.class_name == "second_question_number":
            return self._render_sub_question_block(anchor, bundle.children, context)
        # 예상치 못한 앵커는 고아 블록으로 처리
        logger.debug(
            f"알 수 없는 앵커 클래스 '{anchor.class_name}' → 고아 블록으로 처리"
        )
        return self._render_orphan_block(bundle.ordered_elements, context)

    def _render_section_block(
        self,
        anchor: MockElement,
        children: List[MockElement],
        context: RenderContext,
    ) -> str:
        output_parts: List[str] = []
        rendered_anchor = context.format_element(anchor)
        if rendered_anchor.strip():
            output_parts.append(rendered_anchor)

        for child in children:
            rendered_child = context.format_element(child)
            if rendered_child.strip():
                output_parts.append(rendered_child)

        return "".join(output_parts)

    def _render_question_block(
        self,
        anchor: MockElement,
        children: List[MockElement],
        context: RenderContext,
    ) -> str:
        return self._render_numbered_block(
            anchor, children, context, primary_class="question text"
        )

    def _render_sub_question_block(
        self,
        anchor: MockElement,
        children: List[MockElement],
        context: RenderContext,
    ) -> str:
        return self._render_numbered_block(
            anchor, children, context, primary_class="question text"
        )

    def _render_numbered_block(
        self,
        anchor: MockElement,
        children: List[MockElement],
        context: RenderContext,
        *,
        primary_class: str,
    ) -> str:
        output_parts: List[str] = []

        primary_element = next(
            (child for child in children if child.class_name == primary_class), None
        )
        # 앵커 텍스트 및 기본 포맷
        anchor_text, _ = context.get_texts(anchor)
        anchor_rule = context.rules.get(anchor.class_name, RuleConfig())
        if anchor_text or anchor_rule.allow_empty:
            anchor_line = apply_rule(anchor_rule, anchor_text).rstrip("\n")
        else:
            anchor_line = anchor_rule.prefix

        first_line = ""
        remainder = ""
        if primary_element:
            base_text, ai_text = context.get_texts(primary_element)
            working = base_text or ai_text
            working = context.apply_transform(
                primary_element,
                working,
                base_text=base_text,
                ai_text=ai_text,
            )
            if not working and ai_text:
                working = ai_text
            first_line, remainder = split_first_line(working)

        if anchor_line:
            leading_newlines = len(anchor_line) - len(anchor_line.lstrip("\n"))
            anchor_core = anchor_line.lstrip("\n")
            combined_line = anchor_core + (first_line or "")
            prefix_newlines = "\n" * leading_newlines
            output_parts.append(prefix_newlines + combined_line.rstrip())
        elif first_line:
            output_parts.append(first_line)

        question_rule = context.rules.get(primary_class)
        if primary_element and remainder:
            if question_rule:
                output_parts.append(apply_rule(question_rule, remainder).rstrip("\n"))
            else:
                output_parts.append(remainder)

        rendered_children = []
        for child in children:
            if child is primary_element:
                continue
            rendered = context.format_element(child)
            if rendered.strip():
                if output_parts and not output_parts[-1].endswith("\n\n"):
                    output_parts[-1] = output_parts[-1].rstrip("\n") + "\n\n"
                rendered_children.append(rendered)
        output_parts.extend(rendered_children)

        return "".join(output_parts)

    def _render_orphan_block(
        self,
        elements: List[MockElement],
        context: RenderContext,
    ) -> str:
        outputs: List[str] = []
        for element in elements:
            rendered = context.format_element(element)
            if rendered.strip():
                outputs.append(rendered)
        return "".join(outputs)

    def _render_reading_order(
        self,
        elements: List[MockElement],
        context: RenderContext,
    ) -> List[str]:
        sorted_elements = sorted(
            elements,
            key=lambda e: (
                int(getattr(e, "y_position", 0)),
                int(getattr(e, "x_position", 0)),
                int(getattr(e, "element_id", 0)),
            ),
        )
        outputs = []
        for element in sorted_elements:
            rendered = context.format_element(element)
            if rendered.strip():
                outputs.append(rendered)
        return outputs
