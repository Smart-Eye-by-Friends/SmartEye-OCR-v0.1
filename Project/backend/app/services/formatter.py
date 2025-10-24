"""
TextFormatter 모듈

문서 타입별 포맷팅 규칙을 사용하여 정렬된 레이아웃 요소를 읽기 쉬운 텍스트로 변환합니다.

프로젝트 계획의 Phase 2.3 의사코드 기반.

사용법:
    formatter = TextFormatter(doc_type_id=1)  # 문제지
    formatted_text = formatter.format_page(sorted_elements, ocr_texts)
"""

from typing import List, Dict, Union, Optional
from .mock_models import MockElement, MockTextContent


class FormattingRule:
    """클래스별 포맷팅 규칙을 나타냅니다."""

    def __init__(
        self,
        class_name: str,
        prefix: str = '',
        suffix: str = '',
        indent_level: int = 0,
        font_size: Optional[str] = None,
        font_weight: Optional[str] = None
    ):
        self.class_name = class_name
        self.prefix = prefix
        self.suffix = suffix
        self.indent_level = indent_level
        self.font_size = font_size
        self.font_weight = font_weight

    def apply(self, content: str) -> str:
        """콘텐츠 문자열에 포맷팅 규칙을 적용합니다."""
        indent = ' ' * self.indent_level
        return f"{self.prefix}{indent}{content}{self.suffix}"


class TextFormatter:
    """
    문서 타입 규칙에 따라 레이아웃 요소를 포맷팅합니다.

    doc_type_id를 기반으로 포맷팅 규칙을 로드하고,
    정렬된 MockElement 리스트와 OCR 텍스트에 적용합니다.
    """

    def __init__(self, doc_type_id: int):
        """
        문서 타입으로 포맷터를 초기화합니다.

        Args:
            doc_type_id: 1=문제지(question_based), 2=일반문서(reading_order)
        """
        self.doc_type_id = doc_type_id
        self.rules: Dict[str, FormattingRule] = {}
        self._load_formatting_rules()

    def _load_formatting_rules(self) -> None:
        """
        doc_type_id에 따라 하드코딩된 포맷팅 규칙을 로드합니다.

        E-R 다이어그램 (v2)의 초기 데이터 기반:
        - doc_type_id=1 (문제지): 문제 기반 레이아웃용 9개 규칙
        - doc_type_id=2 (일반문서): 일반 문서용 3개 규칙
        """
        if self.doc_type_id == 1:
            # 문제지 포맷팅 규칙 (question_based)
            # E-R 다이어그램 541-552번 줄 참조
            rules_data = [
                # 앵커(Anchors)
                ('question_type', '\n\n', '\n', 0, None, 'bold'),
                ('question_number', '\n\n', '. ', 0, None, 'bold'),
                ('second_question_number', '\n   ', '. ', 3, None, None),
                # 자식(Children)
                ('question_text', '   ', '\n', 3, None, None),
                ('list', '   - ', '\n', 3, None, None),
                ('choices', '   ', '\n', 3, None, None),
                ('figure', '\n   [그림 설명]\n   ', '\n', 3, None, None),
                ('table', '\n   [표 설명]\n   ', '\n', 3, None, None),
                ('flowchart', '\n   [순서도 설명]\n   ', '\n', 3, None, None),
            ]
        elif self.doc_type_id == 2:
            # 일반문서 포맷팅 규칙 (reading_order)
            # E-R 다이어그램 555-558번 줄 참조
            rules_data = [
                ('title', '', '\n\n', 0, None, 'bold'),
                ('plain text', '', '\n', 0, None, None),
                ('figure', '\n[그림] ', '\n', 0, None, None),
            ]
        else:
            raise ValueError(f"지원하지 않는 doc_type_id: {self.doc_type_id}")

        # 튜플을 FormattingRule 객체로 변환
        for class_name, prefix, suffix, indent, font_size, font_weight in rules_data:
            self.rules[class_name] = FormattingRule(
                class_name=class_name,
                prefix=prefix,
                suffix=suffix,
                indent_level=indent,
                font_size=font_size,
                font_weight=font_weight
            )

    def format_page(
        self,
        sorted_elements: List[MockElement],
        ocr_texts: Union[Dict[int, str], List[MockTextContent]]
    ) -> str:
        """
        정렬된 요소들을 최종 텍스트 문자열로 포맷팅합니다.

        Args:
            sorted_elements: 읽기 순서로 정렬된 MockElement 객체 리스트
                            (sorter.recursive_layout_sort 또는 좌표 정렬 결과)
            ocr_texts: element_id를 텍스트 문자열로 매핑하는 딕셔너리,
                      또는 MockTextContent 객체 리스트

        Returns:
            모든 요소가 결합된 포맷팅된 텍스트 문자열

        Example:
            >>> formatter = TextFormatter(doc_type_id=1)
            >>> elements = [mock_elem1, mock_elem2, ...]
            >>> ocr_dict = {1: "문제 1", 2: "다음 중...", ...}
            >>> formatted = formatter.format_page(elements, ocr_dict)
            >>> print(formatted)


            1. 문제 1
               다음 중 올바른 것은?
               - 선택지 1
               - 선택지 2
        """
        # ocr_texts가 리스트면 딕셔너리로 변환
        if isinstance(ocr_texts, list):
            ocr_dict = {
                text_content.element_id: text_content.ocr_text
                for text_content in ocr_texts
            }
        else:
            ocr_dict = ocr_texts

        # 각 요소를 포맷팅하고 결과 수집
        formatted_texts: List[str] = []

        for element in sorted_elements:
            # 이 요소의 OCR 텍스트 가져오기
            content = ocr_dict.get(element.element_id, '')

            # 빈 콘텐츠는 건너뛰기
            if not content or content.strip() == '':
                continue

            # 이 클래스의 포맷팅 규칙 가져오기
            rule = self.rules.get(element.class_name)

            if rule:
                # 포맷팅 규칙 적용
                formatted = rule.apply(content)
                formatted_texts.append(formatted)
            else:
                # 규칙이 없으면 줄바꿈만 추가한 일반 텍스트 사용
                formatted_texts.append(f"{content}\n")

        # 모든 포맷팅된 텍스트 결합
        combined_text = ''.join(formatted_texts)

        return combined_text

    def get_rule(self, class_name: str) -> Optional[FormattingRule]:
        """
        특정 클래스의 포맷팅 규칙을 가져옵니다.

        Args:
            class_name: 레이아웃 요소 클래스명

        Returns:
            FormattingRule 객체, 없으면 None
        """
        return self.rules.get(class_name)

    def has_rule(self, class_name: str) -> bool:
        """
        클래스의 포맷팅 규칙 존재 여부를 확인합니다.

        Args:
            class_name: 레이아웃 요소 클래스명

        Returns:
            규칙이 있으면 True, 없으면 False
        """
        return class_name in self.rules