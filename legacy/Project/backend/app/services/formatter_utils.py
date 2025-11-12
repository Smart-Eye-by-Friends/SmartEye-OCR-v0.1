"""
포맷터 유틸리티 함수 모음.

포맷팅 규칙 적용, 선택지 정규화, 시각 자료 설명 병합 등
핵심 후처리 로직을 한 곳에 모아둔다.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple

from .mock_models import MockElement
from .formatter_rules import RuleConfig


AI_PRIORITY_CLASSES = {"figure", "table", "flowchart"}


# ---------------------------------------------------------------------------
# 데이터 변환 헬퍼
# ---------------------------------------------------------------------------

def ocr_inputs_to_dict(ocr_texts) -> Dict[int, str]:
    """
    OCR 입력을 element_id → text 딕셔너리로 변환.
    """
    if isinstance(ocr_texts, dict):
        return {int(k): (v or "").strip() for k, v in ocr_texts.items()}

    ocr_dict: Dict[int, str] = {}
    for item in ocr_texts or []:
        try:
            element_id = int(getattr(item, "element_id"))
            text = getattr(item, "ocr_text", "") or ""
        except AttributeError:
            continue
        cleaned = text.strip()
        if cleaned:
            ocr_dict[element_id] = cleaned
    return ocr_dict


def normalize_ai_descriptions(ai_descriptions: Optional[Dict[int, str]]) -> Dict[int, str]:
    """
    AI 설명 딕셔너리를 정리합니다.
    """
    if not ai_descriptions:
        return {}
    return {int(k): (v or "").strip() for k, v in ai_descriptions.items() if (v or "").strip()}


def split_first_line(text: str) -> Tuple[str, str]:
    """
    문자열을 첫 줄과 나머지로 분리한다.
    """
    if not text:
        return "", ""
    lines = text.splitlines()
    first = lines[0]
    remainder = "\n".join(lines[1:]).strip()
    return first, remainder


# ---------------------------------------------------------------------------
# 콘텐츠 후처리
# ---------------------------------------------------------------------------

CHOICE_PATTERN = re.compile(
    r"^(\(?\d{1,2}[\).]|[①-⑳]|[A-Z][\).]|[가-하]\.|[가-하]\))\s*(.+)$"
)


def normalize_choices(text: str) -> str:
    """
    선택지 텍스트를 표준화한다.
    - 패턴이 명확하면 그대로 사용.
    - 그렇지 않으면 '• ' 불릿을 붙인다.
    """
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    normalized: List[str] = []
    for line in lines:
        match = CHOICE_PATTERN.match(line)
        if match:
            label, body = match.groups()
            normalized.append(f"{label} {body.strip()}")
        else:
            normalized.append(f"• {line}")
    return "\n".join(normalized)


LIST_PATTERN = re.compile(r"^([\-•]|\d+\.)\s*(.+)$")


def normalize_list(text: str) -> str:
    """
    일반 리스트 텍스트를 정규화.
    """
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    normalized: List[str] = []
    for line in lines:
        match = LIST_PATTERN.match(line)
        if match:
            normalized.append(f"- {match.group(2).strip()}")
        else:
            normalized.append(f"- {line}")
    return "\n".join(normalized)


def normalize_reading_list(text: str) -> str:
    """
    일반 문서용 리스트 정규화 (불릿 기호 유지).
    """
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    normalized: List[str] = []
    for line in lines:
        match = LIST_PATTERN.match(line)
        if match:
            normalized.append(f"• {match.group(2).strip()}")
        else:
            normalized.append(f"• {line}")
    return "\n".join(normalized)


def merge_visual_description(text: str, ai_text: Optional[str]) -> str:
    """
    그림/표/순서도 설명을 결합한다.
    AI 설명이 있으면 우선 사용하고, OCR 텍스트가 있으면 다음 줄에 추가한다.
    """
    if ai_text and text:
        return f"{ai_text}\n{text}"
    return ai_text or text


def isolate_formula(text: str) -> str:
    """
    수식은 주어진 텍스트를 그대로 사용하되 앞뒤 공백을 정돈한다.
    """
    return text.strip()


def uppercase_title(text: str) -> str:
    return text.strip()


def normalize_question_type(text: str) -> str:
    """
    question type OCR 결과의 줄 정렬/노이즈 제거.
    - 줄바꿈을 공백으로 치환하여 한 줄로 정리
    (그 외 문자/공백은 원본을 최대한 유지)
    """
    normalized = unicodedata.normalize("NFKC", text or "")
    normalized = normalized.replace("\r\n", "\n").replace("\r", "\n")
    return normalized.replace("\n", " ")


TRANSFORM_DISPATCH = {
    "normalize_choices": normalize_choices,
    "normalize_list": normalize_list,
    "normalize_reading_list": normalize_reading_list,
    "merge_visual_description": merge_visual_description,
    "isolate_formula": isolate_formula,
    "uppercase_title": uppercase_title,
    "normalize_question_type": normalize_question_type,
}


# ---------------------------------------------------------------------------
# 규칙 적용 및 출력 정리
# ---------------------------------------------------------------------------

def apply_rule(rule: RuleConfig, content: str) -> str:
    """
    규칙에 따라 콘텐츠에 접두사, 들여쓰기, 접미사를 적용한다.
    """
    if not content and not rule.allow_empty:
        return ""

    working = content
    if rule.indent > 0:
        indent_str = " " * rule.indent
        indented_lines: List[str] = []
        for line in working.splitlines():
            if not line.strip():
                indented_lines.append("")
            else:
                indented_lines.append(f"{indent_str}{line}")
        working = "\n".join(indented_lines)

    if not working and not rule.keep_suffix_on_empty:
        return rule.prefix if rule.prefix else ""

    return f"{rule.prefix}{working}{rule.suffix}"


def clean_output(text: str) -> str:
    """
    최종 출력 문자열에서 연속 빈 줄 및 후행 공백을 정리한다.
    """
    lines = text.splitlines()
    cleaned: List[str] = []
    empty_streak = 0
    for line in lines:
        stripped = line.rstrip()
        if stripped == "":
            empty_streak += 1
            if empty_streak > 2:
                continue
        else:
            empty_streak = 0
        cleaned.append(stripped)
    result = "\n".join(cleaned).strip()
    return result


# ---------------------------------------------------------------------------
# 렌더링 컨텍스트
# ---------------------------------------------------------------------------

@dataclass
class RenderContext:
    """
    렌더링 시 필요한 컨텍스트.
    """

    ocr_texts: Dict[int, str]
    ai_texts: Dict[int, str]
    rules: Dict[str, RuleConfig]

    def get_texts(self, element: MockElement) -> Tuple[str, str]:
        element_id = getattr(element, "element_id", None)
        base_text = self.ocr_texts.get(element_id, "").strip()
        ai_text = self.ai_texts.get(element_id, "").strip()
        return base_text, ai_text

    def apply_transform(
        self,
        element: MockElement,
        text: str,
        *,
        base_text: str,
        ai_text: str,
    ) -> str:
        rule = self.rules.get(element.class_name)
        if not rule or not rule.transform:
            return text.strip()

        transform = TRANSFORM_DISPATCH.get(rule.transform)
        if not transform:
            return text.strip()

        if rule.transform == "merge_visual_description":
            return transform(base_text.strip(), ai_text.strip())

        return transform(text.strip())

    def format_element(self, element: MockElement, content_override: Optional[str] = None) -> str:
        """
        개별 요소를 규칙에 따라 문자열로 변환한다.
        """
        element_id = getattr(element, "element_id", None)
        base_text = (content_override if content_override is not None else self.ocr_texts.get(element_id, "")).strip()
        ai_text = self.ai_texts.get(element_id, "").strip()

        # 그림/표/순서도는 AI 설명을 우선 적용
        if element.class_name in AI_PRIORITY_CLASSES:
            working = base_text
        else:
            working = base_text or ai_text

        working = self.apply_transform(
            element,
            working,
            base_text=base_text,
            ai_text=ai_text,
        )

        if not working and ai_text and element.class_name in AI_PRIORITY_CLASSES:
            working = ai_text

        rule = self.rules.get(element.class_name)
        if rule:
            return apply_rule(rule, working)
        # 규칙이 없으면 기본적으로 한 줄 출력
        return f"{working}\n" if working else ""
