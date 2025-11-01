# -*- coding: utf-8 -*-
"""
앵커 기반 TextFormatter 회귀 테스트
"""

from typing import Dict, Optional

import textwrap

import pytest

from backend.app.services.formatter import TextFormatter
from backend.app.services.formatter_utils import clean_output
from backend.app.services.mock_models import MockElement, MockTextContent


def _make_element(
    element_id: int,
    class_name: str,
    *,
    x: int = 0,
    y: int = 0,
    w: int = 100,
    h: int = 40,
    group_id: Optional[int] = None,
    order_in_group: int = 0,
    order_in_question: int = 0,
) -> MockElement:
    elem = MockElement(
        element_id=element_id,
        class_name=class_name,
        confidence=0.95,
        bbox_x=x,
        bbox_y=y,
        bbox_width=w,
        bbox_height=h,
    )
    elem.group_id = group_id
    elem.order_in_group = order_in_group
    elem.order_in_question = order_in_question
    return elem


def test_question_block_with_sub_blocks():
    formatter = TextFormatter(doc_type_id=1)

    elements = [
        _make_element(1, "question type", group_id=0, order_in_group=0, order_in_question=0),
        _make_element(2, "question number", group_id=1, order_in_group=0, order_in_question=1),
        _make_element(3, "question text", group_id=1, order_in_group=1, order_in_question=2),
        _make_element(4, "choices", group_id=1, order_in_group=2, order_in_question=3),
        _make_element(5, "figure", group_id=1, order_in_group=3, order_in_question=4),
        _make_element(6, "second_question_number", group_id=2, order_in_group=0, order_in_question=5),
        _make_element(7, "question text", group_id=2, order_in_group=1, order_in_question=6),
    ]

    ocr_dict: Dict[int, str] = {
        1: "객관식",
        2: "1",
        3: "다음 중 올바른 것은?",
        4: "1) 선택지 1\n2) 선택지 2",
        6: "(1)",
        7: "설명형 문항",
    }
    ai_descriptions = {5: "AI가 설명"}

    formatted = formatter.format_page(
        elements,
        [MockTextContent(text_id=i, element_id=i, ocr_text=text) for i, text in ocr_dict.items()],
        ai_descriptions=ai_descriptions,
    )

    expected = clean_output(
        textwrap.dedent(
            """
            [객관식]



            1. 다음 중 올바른 것은?
               1) 선택지 1
               2) 선택지 2

               [그림 설명]
               AI가 설명

                  (1). 설명형 문항
            """
        ).strip()
    )

    assert clean_output(formatted) == expected


def test_orphan_block_without_header():
    formatter = TextFormatter(doc_type_id=1)
    elements = [
        _make_element(10, "plain text", group_id=None, order_in_group=0, order_in_question=0),
    ]
    ocr_dict = {10: "고아 요소 내용"}
    formatted = formatter.format_page(
        elements,
        [MockTextContent(text_id=10, element_id=10, ocr_text=ocr_dict[10])],
    )
    assert clean_output(formatted) == "고아 요소 내용"


def test_reading_order_document():
    formatter = TextFormatter(doc_type_id=2)
    elements = [
        _make_element(20, "title", group_id=0, order_in_group=0, order_in_question=0),
        _make_element(21, "plain text", group_id=1, order_in_group=0, order_in_question=1),
        _make_element(22, "list", group_id=2, order_in_group=0, order_in_question=2),
    ]
    texts = {
        20: "문서 제목",
        21: "첫 번째 문단입니다.",
        22: "항목1\n항목2",
    }
    formatted = formatter.format_page(
        elements,
        [MockTextContent(text_id=eid, element_id=eid, ocr_text=text) for eid, text in texts.items()],
    )
    expected = clean_output(
        textwrap.dedent(
            """
            문서 제목

            첫 번째 문단입니다.

            • 항목1
            • 항목2
            """
        ).strip()
    )
    assert clean_output(formatted) == expected
