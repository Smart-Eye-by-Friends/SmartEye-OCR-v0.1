# -*- coding: utf-8 -*-
"""
SmartEyeSsen Analysis Service (v1.1 - Duplicate Detection Filter Added)
========================================================================

학습지 분석 서비스 - 레이아웃 분석, OCR, AI 설명 생성을 담당합니다.
Refactored from api_server.py WorksheetAnalyzer class.

주요 변경사항 (DB 통합 버전):
- analyze_layout: DocLayout-YOLO 결과를 layout_elements 테이블에 저장 후 ORM 객체 반환
- perform_ocr: text_contents 테이블에 OCR 결과 upsert
- call_openai_api / call_openai_api_async: ai_descriptions 테이블에 설명 텍스트 upsert
- 중복 탐지 필터링(IoU 기반) 로직 유지
"""

import asyncio
import base64
import colorsys
import io
import os
import platform
import random
from dataclasses import dataclass
from typing import Dict, List, Optional

import cv2
import numpy as np
import openai
import pytesseract
import torch
from PIL import Image
from loguru import logger
from openai import AsyncOpenAI
from sqlalchemy.orm import Session

try:
    import google.generativeai as genai
    GENAI_AVAILABLE = True
except ImportError:
    GENAI_AVAILABLE = False
    logger.warning("⚠️ google-generativeai 패키지가 설치되지 않았습니다. Tesseract OCR만 사용 가능합니다.")

from .. import models
from .model_registry import model_registry


class GeminiOCRError(Exception):
    """Gemini OCR 처리 중 발생하는 예외."""


@dataclass
class GeminiOCRJob:
    element: models.LayoutElement
    cls_name: str
    pil_image: Image.Image
    cropped_img: np.ndarray


@dataclass
class GeminiOCRResult:
    job: GeminiOCRJob
    text: str = ""
    engine: str = "Gemini-2.5-Flash-Lite"
    error: Optional[Exception] = None


def _safe_int(value: Optional[str], default: int) -> int:
    try:
        return int(value) if value is not None else default
    except ValueError:
        logger.warning(
            f"⚠️ 환경 변수 값이 정수가 아닙니다. 기본값 {default} 사용: {value}"
        )
        return default


def _safe_float(value: Optional[str], default: float) -> float:
    try:
        return float(value) if value is not None else default
    except ValueError:
        logger.warning(
            f"⚠️ 환경 변수 값이 실수가 아닙니다. 기본값 {default} 사용: {value}"
        )
        return default


GEMINI_MAX_CONCURRENCY = _safe_int(os.getenv("GEMINI_MAX_CONCURRENCY"), 30)
GEMINI_MAX_RETRIES = _safe_int(os.getenv("GEMINI_MAX_RETRIES"), 3)
GEMINI_RETRY_BASE_DELAY = _safe_float(os.getenv("GEMINI_RETRY_BASE_DELAY"), 1.5)
GEMINI_SAFETY_SETTINGS = [
    {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"},
]

# --- 신규: 이미지 설명을 위한 프롬프트 템플릿 추가 (규정 기반 최적화) ---
figure_prompt = """
당신은 시각장애 학생을 위한 이미지 설명 전문가입니다.

역할:
- 한국 점자 도서 규정(제2절 시각 자료, p.82-91)을 정확히 따릅니다.
- 초등학생(8-14세)이 이해할 수 있는 쉬운 말을 씁니다.
- 점자 독자가 촉각으로 정보를 파악하도록 합니다.
- 객관적 사실만 설명하고 주관적 판단은 피합니다.

설명 규칙 (한국 점자 규정 준수):
1. 제목 확인: 원본에 제목이 있으면 그대로 사용, 없으면 '제목 없음'으로 표기
2. 그림/사진 유형: 실사, 삽화, 도형, 사진 등 구체적으로 명시
3. 읽기 순서: 왼쪽→오른쪽, 위→아래 순서 준수
4. 구조 설명:
   - 배경: 사진/그림의 배경이나 설정
   - 중심 피사체: 가장 중요한 대상
   - 위치 관계: 요소들 간의 공간적 배치
5. 세부 설명:
   - 크기, 색상, 재질 등 물리적 특성
   - 표정, 자세, 동작(인물/동물 포함 시)
   - 숫자나 문자가 있으면 명확하게 전달
6. 문장 길이: 초등학생 이해도 기준, 30자 이내 권장
7. 추측 금지: 원본에 없는 정보는 절대 추가하지 않음

[출력 형식]
이미지 개요:
- 제목: [제목 또는 '제목 없음']
- 유형: [실사/삽화/도형/사진 등]

구성 요소:
- 배경: [배경 설명]
- 중심 대상: [주요 피사체 설명]
- 위치 관계: [공간적 배치 설명]

세부 내용:
[색상, 크기, 특징 등 구체적 설명]

특이 사항:
[문자, 기호, 주목할 만한 요소 등]
"""

table_prompt = """
당신은 시각장애 학생을 위한 표 설명 전문가입니다.

역할:
- 한국 점자 도서 규정(제3장 제1절 표, p.62-81)을 정확히 따릅니다.
- 특히 simple_table 규정(수차/몇 개 단어 표, p.65-66)을 적용합니다.
- 초등학생(8-14세)이 이해할 수 있는 쉬운 말을 씁니다.
- 표의 구조를 명확하게 전달합니다.
- 점자 독자가 행별로 정확하게 파악할 수 있게 합니다.

설명 규칙 (한국 점자 규정 준수):
1. 제목과 단위 명시:
   - 표의 제목이 있으면 우선 표기
   - 제목이 없으면 '제목 없음'으로 표기
   - 표의 단위 명시 (예: %, 명, kg 등)

2. 표의 구조 파악 (simple_table 규정):
   - 행 수(몇 줄)와 열 수(몇 칸) 명확히 표기
   - 열 제목(컬럼 헤더) 차례대로 나열
   - 행 제목(로우 헤더) 확인

3. 행별 순차 설명:
   - 첫 번째 행부터 마지막 행까지 순서대로
   - 각 행 내에서는 왼쪽 열부터 오른쪽 열 순서로
   - 행 제목과 데이터를 명확히 연결

4. 수치 표현:
   - 숫자는 반드시 단위와 함께 표기 (예: "3명", "50%")
   - 크기 비교 추가 (예: "A가 B보다 높음")
   - 증감 방향 명시 (예: "증가", "감소")

5. 특이 사항 처리:
   - 빈 칸이 있으면 명시 (예: "해당 데이터 없음")
   - 별표(*), 주석이 있으면 설명
   - 병합된 셀(merged cell)이 있으면 구조 명확히

6. 핵심 메시지:
   - 표가 무엇을 보여주는지 한 문장으로 요약

[출력 형식]
표의 정보:
- 제목: [제목 또는 '제목 없음']
- 크기: [행 수]행 × [열 수]열
- 단위: [%, 명, kg 등]

표의 구조:
- 열 제목: [열1], [열2], [열3] ... (순서대로)
- 행 제목: [행1], [행2], [행3] ... (있으면)

행별 데이터:
첫 번째 행 ([행_제목1]): [데이터1], [데이터2], [데이터3]
두 번째 행 ([행_제목2]): [데이터1], [데이터2], [데이터3]
세 번째 행 ([행_제목3]): [필요 시 계속]

특이 사항: [빈칸, 병합, 주석 등]

핵심: [표가 보여주는 핵심 정보 한 문장]
"""

flowchart_prompt = """
당신은 시각장애 학생을 위한 순서도 설명 전문가입니다.

역할:
- 한국 점자 도서 규정(제2절, p.108-112)을 정확히 따릅니다.
- 초등학생(8-14세)이 이해할 수 있는 쉬운 말을 씁니다.
- 순서도의 흐름과 논리를 명확하게 전달합니다.
- 점자 독자가 단계별 진행을 정확하게 파악할 수 있게 합니다.

설명 규칙 (한국 점자 규정 준수):
1. 제목과 주제:
   - 순서도의 제목이 있으면 명시
   - 이 순서도가 무엇에 대한 것인지 명확히

2. 시작점과 종료점:
   - 시작점(시작 노드) 명확히 표기
   - 종료점(끝 노드) 명확히 표기
   - 전체 단계 수 표기

3. 단계별 진행 설명 (왼쪽→오른쪽, 위→아래 순서):
   - 첫 번째: [단계 내용]
   - 두 번째: [단계 내용]
   - 세 번째: [필요 시 계속]
   - 각 단계는 번호를 붙여서 순차 표기

4. 분기/조건 표현:
   - "만약 ~~라면" 형식으로 조건문 풀이
   - 각 조건별 진행 경로 명확히
   - 예/아니오 선택지 있으면 구분

5. 루프/반복 표현:
   - "다시 ~~로 돌아감" 형식으로 표현
   - 왜 반복되는지 이유 함께 명시

6. 문장 규칙:
   - 초등학생 이해 수준 기준
   - 간단하고 명확한 표현
   - 원본에 없는 정보는 추가하지 않음

[출력 형식]
순서도 개요:
- 제목: [제목 또는 '제목 없음']
- 주제: [무엇에 대한 순서도인가]
- 시작점: [시작]
- 종료점: [끝]
- 총 단계: [대략 몇 단계]

단계별 진행:
1단계: [내용]
   ↓
2단계: [내용]
   ↓
3단계: [필요 시 계속]

분기/조건 (있으면):
[조건]: [결과 경로]

핵심: [이 순서도가 설명하는 전체 흐름 한 문장]
"""


# --- 신규: IoU 계산 함수 추가 ---
def calculate_iou(box1, box2):
    """두 바운딩 박스 간의 IoU(Intersection over Union) 계산"""
    # box 형식: [x1, y1, x2, y2]
    x1_inter = max(box1[0], box2[0])
    y1_inter = max(box1[1], box2[1])
    x2_inter = min(box1[2], box2[2])
    y2_inter = min(box1[3], box2[3])

    inter_area = max(0, x2_inter - x1_inter) * max(0, y2_inter - y1_inter)

    box1_area = (box1[2] - box1[0]) * (box1[3] - box1[1])
    box2_area = (box2[2] - box2[0]) * (box2[3] - box2[1])

    union_area = box1_area + box2_area - inter_area

    if union_area == 0:
        return 0.0
    return inter_area / union_area


# --- 신규: 중복 제거 후처리 함수 추가 ---
def filter_duplicate_detections(boxes, classes, confs, class_names, iou_threshold=0.7):
    """
    모든 클래스 쌍에 대해 IoU 기반으로 중복 탐지를 필터링. (자동 방식)
    신뢰도가 낮은 쪽을 제거.
    """
    num_detections = len(boxes)
    suppressed = [False] * num_detections  # 제거할 요소 표시

    indices = list(range(num_detections))
    # 신뢰도 높은 순으로 정렬 (높은 것을 남기기 위함)
    indices.sort(key=lambda i: confs[i], reverse=True)

    for i in range(num_detections):
        idx1 = indices[i]
        if suppressed[idx1]:
            continue

        box1 = boxes[idx1]
        # cls_id1 = int(classes[idx1]) # 클래스 정보는 제거 로직에 불필요
        # cls_name1 = class_names.get(cls_id1, f"unknown_{cls_id1}") # 클래스 정보는 제거 로직에 불필요

        for j in range(i + 1, num_detections):
            idx2 = indices[j]
            if suppressed[idx2]:
                continue

            box2 = boxes[idx2]
            # cls_id2 = int(classes[idx2]) # 클래스 정보는 제거 로직에 불필요
            # cls_name2 = class_names.get(cls_id2, f"unknown_{cls_id2}") # 클래스 정보는 제거 로직에 불필요

            # --- 👇 수정된 부분 시작 👇 ---
            # 특정 클래스 쌍 확인 조건 제거: 모든 쌍에 대해 IoU 계산
            # if (cls_name1, cls_name2) in problematic_pairs: # 이 조건 제거
            iou = calculate_iou(box1, box2)
            if iou > iou_threshold:
                # 신뢰도 낮은 쪽(idx2)을 제거 대상으로 표시
                suppressed[idx2] = True
                # 로그 메시지에서 클래스 이름 제거 (선택 사항)
                logger.debug(
                    f"중복 탐지 제거: Box {idx2}(conf={confs[idx2]:.2f}) - "
                    f"Box {idx1}(conf={confs[idx1]:.2f})와 IoU={iou:.2f} > {iou_threshold}"
                )
            # --- 👆 수정된 부분 끝 👆 ---

    # 제거되지 않은 요소들의 인덱스 반환
    final_indices = [i for i, s in enumerate(suppressed) if not s]
    logger.info(
        f"자동 중복 탐지 필터링: {num_detections}개 → {len(final_indices)}개 요소 (IoU > {iou_threshold})"
    )  # 로그 메시지 수정
    return final_indices


# Windows에서 Tesseract 경로 설정 (기존과 동일)
if platform.system() == "Windows":
    pytesseract.pytesseract.tesseract_cmd = (
        r"C:\Program Files\Tesseract-OCR\tesseract.exe"
    )

# 디바이스 설정 (기존과 동일)
device = "cuda:0" if torch.cuda.is_available() else "cpu"

# Google Gemini API 초기화
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
gemini_available = False
if GENAI_AVAILABLE and GEMINI_API_KEY and GEMINI_API_KEY != "your_gemini_api_key_here":
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        gemini_available = True
        logger.info("✅ Gemini API 초기화 완료 (OCR 엔진으로 사용 가능)")
    except Exception as e:
        logger.warning(f"⚠️ Gemini API 초기화 실패: {e} - Tesseract OCR로 대체됩니다")
        gemini_available = False
else:
    if not GENAI_AVAILABLE:
        logger.info("ℹ️ google-generativeai 패키지 미설치 - Tesseract OCR 사용")
    else:
        logger.info("ℹ️ GEMINI_API_KEY 미설정 - Tesseract OCR 사용")


class AnalysisService:
    """학습지 분석 서비스 - 상태 없는 함수형 디자인"""

    def __init__(self, model_choice: str = "SmartEyeSsen", auto_load: bool = False):
        """
        분석 서비스 초기화

        Args:
            model_choice: 사용할 모델 선택 (기본값: "SmartEyeSsen")
            auto_load: True이면 초기화 시 자동으로 모델 로드 (기본값: False, 하위 호환성 유지)
        """
        self.device = device
        self.model_choice = model_choice
        self.model_registry = model_registry
        self._model_handle = None
        self._model_loaded = False
        self.gemini_max_concurrency = max(1, GEMINI_MAX_CONCURRENCY)
        self.gemini_max_retries = max(1, GEMINI_MAX_RETRIES)
        self.gemini_retry_base_delay = max(0.1, GEMINI_RETRY_BASE_DELAY)

        # 자동 로드 옵션이 활성화된 경우 즉시 모델 로드
        if auto_load:
            self._ensure_model_loaded()

    def _ensure_model_loaded(self, model_choice: Optional[str] = None):
        """
        Lazy Loading: 모델이 로드되지 않았으면 자동으로 로드
        (다중 페이지 처리 시 모델을 한 번만 로드하도록 최적화)
        """
        target_model = model_choice or self.model_choice
        if (
            self._model_loaded
            and self._model_handle is not None
            and self._model_handle.name == target_model
        ):
            return self._model_handle

        handle = self.model_registry.get_model(target_model, device=self.device)
        self._model_handle = handle
        self.model_choice = target_model
        self._model_loaded = True
        return handle

    def analyze_layout(
        self,
        image: np.ndarray,
        *,
        page_id: int,
        db: Session,
        model_choice: Optional[str] = None,
    ) -> List[models.LayoutElement]:
        """
        레이아웃 분석 + 중복 탐지 필터링 후 결과를 DB에 저장한다.

        Args:
            image: 분석할 이미지 (numpy array)
            page_id: 결과를 저장할 pages.page_id
            db: SQLAlchemy Session
            model_choice: 사용할 모델 (미지정 시 인스턴스 기본값 사용)

        Returns:
            DB에 저장된 LayoutElement ORM 객체 리스트
        """
        active_model = model_choice or self.model_choice

        try:
            # 모델 선택이 변경되었으면 재로드
            if active_model != self.model_choice:
                logger.warning(f"모델 변경 감지: {self.model_choice} -> {active_model}")
                self.model_choice = active_model
                self._model_loaded = False

            # Lazy Loading: 모델이 없으면 자동 로드
            handle = self._ensure_model_loaded(active_model)
            model = handle.model
            model_spec = handle.spec

            logger.info("레이아웃 분석 시작...")
            temp_path = "temp_image.jpg"
            cv2.imwrite(temp_path, image)

            imgsz, conf = model_spec.imgsz, model_spec.conf

            results = model.predict(
                temp_path, imgsz=imgsz, conf=conf, iou=0.45, device=self.device
            )

            boxes = results[0].boxes.xyxy.cpu().numpy()  # [x1, y1, x2, y2]
            classes = results[0].boxes.cls.cpu().numpy()
            confs = results[0].boxes.conf.cpu().numpy()
            class_names = model.names  # 클래스 ID → 이름

            detection_records: List[Dict[str, float]] = []

            if not boxes.size:
                logger.warning("레이아웃 분석 결과, 감지된 요소가 없습니다.")
                return self._create_elements_from_layout(
                    detections=detection_records, page_id=page_id, db=db
                )

            final_indices = filter_duplicate_detections(
                boxes, classes, confs, class_names, iou_threshold=0.7
            )

            for i in final_indices:
                box = boxes[i]
                cls_id = int(classes[i])
                conf_val = float(confs[i])
                x1, y1, x2, y2 = map(int, box)

                cls_name = (
                    class_names.get(cls_id, f"unknown_{cls_id}")
                    if isinstance(class_names, dict)
                    else None
                )
                if cls_name is None:
                    try:
                        cls_name = class_names[cls_id]
                    except (IndexError, KeyError):
                        cls_name = f"unknown_{cls_id}"

                width = x2 - x1
                height = y2 - y1
                area = width * height
                if area < 100:
                    continue

                detection_records.append(
                    {
                        "class_name": cls_name,
                        "confidence": conf_val,
                        "bbox_x": x1,
                        "bbox_y": y1,
                        "bbox_width": width,
                        "bbox_height": height,
                    }
                )

            elements = self._create_elements_from_layout(
                detections=detection_records, page_id=page_id, db=db
            )
            logger.info(f"레이아웃 분석 완료: 최종 {len(elements)}개 요소 저장")
            return elements

        except Exception as e:
            logger.error(f"레이아웃 분석 실패: {e}", exc_info=True)
            return []

    def _create_elements_from_layout(
        self, *, detections: List[Dict[str, float]], page_id: int, db: Session
    ) -> List[models.LayoutElement]:
        """
        감지 결과를 layout_elements 테이블에 저장하고 ORM 객체 리스트를 반환한다.
        """
        logger.debug(f"페이지 {page_id} 기존 레이아웃 요소 정리")
        existing_elements = (
            db.query(models.LayoutElement)
            .filter(models.LayoutElement.page_id == page_id)
            .all()
        )
        for element in existing_elements:
            db.delete(element)
        db.flush()  # CASCADE 관계 정리

        if not detections:
            db.commit()
            return []

        created_elements: List[models.LayoutElement] = []
        for record in detections:
            element = models.LayoutElement(
                page_id=page_id,
                class_name=record["class_name"],
                confidence=record["confidence"],
                bbox_x=int(record["bbox_x"]),
                bbox_y=int(record["bbox_y"]),
                bbox_width=int(record["bbox_width"]),
                bbox_height=int(record["bbox_height"]),
            )
            db.add(element)
            created_elements.append(element)

        db.flush()
        db.commit()
        for element in created_elements:
            db.refresh(element)

        return created_elements

    def _upsert_text_content(
        self,
        *,
        db: Session,
        element_id: int,
        ocr_text: str,
        ocr_engine: str,
        language: str,
        ocr_confidence: Optional[float] = None,
    ) -> models.TextContent:
        """
        텍스트 콘텐츠를 생성하거나 업데이트한다.
        """
        existing = (
            db.query(models.TextContent)
            .filter(models.TextContent.element_id == element_id)
            .one_or_none()
        )

        if existing:
            existing.ocr_text = ocr_text
            existing.ocr_engine = ocr_engine
            existing.language = language
            existing.ocr_confidence = ocr_confidence
            db.flush()
            return existing

        content = models.TextContent(
            element_id=element_id,
            ocr_text=ocr_text,
            ocr_engine=ocr_engine,
            ocr_confidence=ocr_confidence,
            language=language,
        )
        db.add(content)
        db.flush()
        return content

    def _upsert_ai_descriptions(
        self,
        *,
        db: Session,
        descriptions: Dict[int, str],
        model_name: str,
        prompt: Optional[str],
    ) -> List[models.AIDescription]:
        """
        AI 설명을 생성하거나 갱신한다.
        """
        saved_records: List[models.AIDescription] = []
        for element_id, description in descriptions.items():
            existing = (
                db.query(models.AIDescription)
                .filter(models.AIDescription.element_id == element_id)
                .one_or_none()
            )

            if existing:
                existing.description = description
                existing.ai_model = model_name
                existing.prompt_used = prompt
                db.flush()
                saved_records.append(existing)
                continue

            record = models.AIDescription(
                element_id=element_id,
                description=description,
                ai_model=model_name,
                prompt_used=prompt,
            )
            db.add(record)
            saved_records.append(record)

        db.flush()
        db.commit()
        for record in saved_records:
            db.refresh(record)

        return saved_records

    def perform_ocr(
        self,
        image: np.ndarray,
        layout_elements: List[models.LayoutElement],
        *,
        db: Session,
        language: str = "kor",
    ) -> List[models.TextContent]:
        """OCR 처리 (영역별 전처리 추가) 및 text_contents 테이블 저장"""
        target_classes = [
            "plain text",
            "unit",
            "question type",
            "question text",
            "question number",
            "title",
            "figure_caption",
            "table caption",
            "table footnote",
            "isolate_formula",
            "formula_caption",
            "list",
            "choices",
            "page",
            "second_question_number",
        ]
        ocr_results: List[models.TextContent] = []
        custom_config = r"--oem 1 --psm 4 -c preserve_interword_spaces=1"
        logger.info(
            f"OCR 처리 시작... 총 {len(layout_elements)}개 레이아웃 요소 중 OCR 대상 필터링"
        )
        logger.info(f"OCR 대상 클래스 목록: {target_classes}")
        detected_classes = {elem.class_name for elem in layout_elements}  # Set으로 변경
        logger.info(f"감지된 모든 클래스: {detected_classes}")

        target_count = 0
        for element in layout_elements:
            cls_name = element.class_name  # Pydantic 모델은 이미 lower() 불필요
            logger.debug(
                f"레이아웃 ID {element.element_id}: 클래스 '{cls_name}' 확인 중..."
            )  # DEBUG 레벨로 변경
            if cls_name not in target_classes:
                logger.debug(f"  → OCR 대상 아님")
                continue

            target_count += 1
            logger.debug(
                f"  → OCR 대상 {target_count}: ID {element.element_id} - 클래스 '{cls_name}'"
            )

            # 1. 영역 이미지 잘라내기 (기존 코드)
            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            # 이미지 경계 내로 좌표 조정
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(image.shape[1], x2), min(image.shape[0], y2)

            if y2 <= y1 or x2 <= x1:  # 크기가 0이거나 음수인 경우 건너뛰기
                logger.warning(
                    f"  → 유효하지 않은 BBox 크기: ID {element.element_id}, 건너뜀"
                )
                continue
            cropped_img = image[y1:y2, x1:x2]

            try:
                # --- 👇 영역별 전처리 단계 시작 👇 ---

                # 2. 그레이스케일 변환: 색상 정보 제거
                gray_img = cv2.cvtColor(cropped_img, cv2.COLOR_BGR2GRAY)

                # 3. 이진화 (Otsu's Binarization): 텍스트/배경 명확화
                # Otsu 방식은 임계값을 자동으로 결정해 줍니다.
                # 필요에 따라 cv2.adaptiveThreshold 등 다른 방식 사용 가능
                _, binary_img = cv2.threshold(
                    gray_img, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
                )

                # 4. (선택적) 노이즈 제거: Median 필터 적용 (작은 점 제거에 효과적)
                # 커널 크기(예: 3)는 실험을 통해 조정
                denoised_img = cv2.medianBlur(binary_img, 3)

                # --- 👆 영역별 전처리 단계 끝 👆 ---

                # 5. 전처리된 이미지로 OCR 수행
                # Pillow 이미지로 변환 (Tesseract는 Pillow 이미지 입력 선호)
                pil_img = Image.fromarray(denoised_img)
                text = pytesseract.image_to_string(
                    pil_img, lang="kor+eng", config=custom_config
                ).strip()

                if len(text) > 1:  # 빈 문자열이 아닌 경우만
                    db_text = self._upsert_text_content(
                        db=db,
                        element_id=element.element_id,
                        ocr_text=text,
                        ocr_engine="Tesseract",
                        language=language,
                    )
                    ocr_results.append(db_text)
                    logger.info(
                        f"✅ OCR 성공: ID {element.element_id} ({cls_name}) - '{text[:50].replace(chr(10), ' ')}...' ({len(text)}자)"
                    )  # 개행문자 제거
                else:
                    logger.warning(
                        f"⚠️ OCR 결과 없음: ID {element.element_id} ({cls_name})"
                    )
            except Exception as e:
                logger.error(
                    f"OCR 실패: ID {element.element_id} - {e}", exc_info=True
                )  # 상세 에러

        db.commit()
        for content in ocr_results:
            db.refresh(content)

        logger.info(f"OCR 처리 완료: {len(ocr_results)}개 텍스트 블록 저장")
        return ocr_results

    def call_openai_api(
        self,
        image: np.ndarray,
        layout_elements: List[models.LayoutElement],
        *,
        api_key: Optional[str],
        db: Session,
        model_name: str = "gpt-4-turbo",
    ) -> Dict[int, str]:
        """OpenAI API 호출 및 ai_descriptions 테이블 저장"""
        if not api_key:
            logger.warning("API 키가 없어 AI 설명 생성을 건너뜁니다.")
            return {}
        target_classes = ["figure", "table", "flowchart"]
        ai_descriptions: Dict[int, str] = {}

        try:
            client = openai.OpenAI(api_key=api_key)
            logger.info("OpenAI API 처리 시작...")
        except Exception as e:
            logger.error(f"OpenAI 클라이언트 초기화 실패: {e}")
            return {}

        prompts = {
            "figure": figure_prompt,
            "table": table_prompt,
            "flowchart": flowchart_prompt,
        }
        system_prompt = (
            "당신은 시각 장애 아동 학습 AI 비서입니다. "
            "시각 자료 내용을 한국어로 간결하고 명확하게 설명하세요. "
            "음성 변환 시 이해하기 쉽도록 직접적인 문장을 사용하세요."
        )

        for element in layout_elements:
            cls_name = element.class_name
            if cls_name not in target_classes:
                continue

            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            if y2 <= y1 or x2 <= x1:
                continue

            cropped_img = image[y1:y2, x1:x2]
            pil_img = Image.fromarray(cv2.cvtColor(cropped_img, cv2.COLOR_BGR2RGB))
            buffered = io.BytesIO()
            pil_img.save(buffered, format="PNG")
            img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
            prompt = prompts.get(cls_name, f"이 {cls_name} 내용 설명")

            try:
                response = client.chat.completions.create(
                    model=model_name,
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {
                            "role": "user",
                            "content": [
                                {"type": "text", "text": prompt},
                                {
                                    "type": "image_url",
                                    "image_url": {
                                        "url": f"data:image/png;base64,{img_base64}"
                                    },
                                },
                            ],
                        },
                    ],
                    temperature=0.2,
                    max_tokens=600,
                )
                description = response.choices[0].message.content.strip()
                ai_descriptions[element.element_id] = description
                logger.info(f"API 응답 완료: ID {element.element_id} - {cls_name}")
            except Exception as e:
                logger.error(
                    f"API 요청 실패: ID {element.element_id} - {e}", exc_info=True
                )

        saved = self._upsert_ai_descriptions(
            db=db, descriptions=ai_descriptions, model_name=model_name, prompt=None
        )
        logger.info(f"OpenAI API 처리 완료: {len(saved)}개 설명 생성 및 저장")
        return ai_descriptions

    async def call_openai_api_async(
        self,
        image: np.ndarray,
        layout_elements: List[models.LayoutElement],
        api_key: str,
        *,
        db: Optional[Session] = None,
        model_name: str = "gpt-4-turbo",
        max_concurrent_requests: int = 15,
    ) -> Dict[int, str]:
        """
        OpenAI API 비동기 병렬 호출 (성능 최적화 버전)

        Args:
            image: 원본 이미지 (BGR 포맷)
            layout_elements: 레이아웃 요소 리스트
            api_key: OpenAI API 키
            db: SQLAlchemy Session (선택, 제공 시 DB에 설명 저장)
            model_name: 사용할 OpenAI 모델 이름
            max_concurrent_requests: 최대 동시 요청 수 (기본값: 5)

        Returns:
            Dict[int, str]: {element_id: AI 설명} 딕셔너리

        주요 개선사항:
        - 비동기 병렬 처리로 처리 시간 70% 단축
        - asyncio.Semaphore로 Rate Limit 대응
        - 지수 백오프 재시도 로직 (exponential backoff)
        """
        if not api_key:
            logger.warning("API 키가 없어 AI 설명 생성을 건너뜁니다.")
            return {}

        # 1. 대상 클래스 필터링 (figure, table, flowchart만 처리)
        target_classes = ["figure", "table", "flowchart"]
        target_elements = [
            elem for elem in layout_elements if elem.class_name in target_classes
        ]

        if not target_elements:
            logger.info("AI 설명 대상 요소가 없습니다.")
            return {}

        logger.info(
            f"OpenAI API 비동기 처리 시작... (총 {len(target_elements)}개 요소)"
        )

        # 2. AsyncOpenAI 클라이언트 초기화
        try:
            async_client = AsyncOpenAI(api_key=api_key)
        except Exception as e:
            logger.error(f"AsyncOpenAI 클라이언트 초기화 실패: {e}")
            return {}

        # 3. Semaphore로 동시 요청 수 제한 (Rate Limit 대응)
        semaphore = asyncio.Semaphore(max_concurrent_requests)

        # 4. 모든 비동기 태스크 생성
        tasks = [
            self._process_single_element_async(
                async_client=async_client,
                image=image,
                element=elem,
                semaphore=semaphore,
                model_name=model_name,
            )
            for elem in target_elements
        ]

        # 5. 병렬 실행 (asyncio.gather)
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # 6. 결과 매핑 및 예외 처리
        ai_descriptions = {}
        success_count = 0
        error_count = 0

        for element, result in zip(target_elements, results):
            if isinstance(result, Exception):
                logger.error(f"API 실패: Element {element.element_id} - {result}")
                error_count += 1
            elif result:  # 성공 시 (빈 문자열이 아닌 경우)
                ai_descriptions[element.element_id] = result
                success_count += 1
                logger.info(
                    f"✅ API 성공: Element {element.element_id} ({element.class_name})"
                )

        logger.info(
            f"OpenAI API 비동기 처리 완료: "
            f"성공 {success_count}건, 실패 {error_count}건 / 총 {len(target_elements)}건"
        )

        if db and ai_descriptions:
            saved = self._upsert_ai_descriptions(
                db=db, descriptions=ai_descriptions, model_name=model_name, prompt=None
            )
            logger.info(f"AI 설명 {len(saved)}건 저장 완료 (비동기)")

        return ai_descriptions

    async def _process_single_element_async(
        self,
        async_client: AsyncOpenAI,
        image: np.ndarray,
        element: models.LayoutElement,
        semaphore: asyncio.Semaphore,
        model_name: str,
    ) -> str:
        """
        단일 element에 대한 비동기 AI 설명 생성 (지수 백오프 재시도 포함)

        Args:
            async_client: AsyncOpenAI 클라이언트
            image: 원본 이미지
            element: 처리할 레이아웃 요소
            semaphore: 동시 요청 수 제한용 Semaphore
            model_name: 사용할 OpenAI 모델 이름

        Returns:
            str: AI 생성 설명 텍스트

        재시도 로직:
        - 최대 3회 재시도
        - 대기 시간: 1초 → 2초 → 4초 (지수 백오프)
        """
        # 1. 이미지 크롭 및 검증
        x1, y1 = element.bbox_x, element.bbox_y
        x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height

        # 크기 검증
        if y2 <= y1 or x2 <= x1:
            logger.warning(f"유효하지 않은 BBox 크기: Element {element.element_id}")
            return ""

        # 이미지 크롭
        cropped_img = image[y1:y2, x1:x2]

        # 2. PIL 이미지 변환 및 Base64 인코딩
        pil_img = Image.fromarray(cv2.cvtColor(cropped_img, cv2.COLOR_BGR2RGB))
        buffered = io.BytesIO()
        pil_img.save(buffered, format="PNG")
        img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")

        # 3. 프롬프트 선택
        prompts = {
            "figure": figure_prompt,
            "table": table_prompt,
            "flowchart": flowchart_prompt,
        }
        prompt = prompts.get(element.class_name, f"이 {element.class_name} 내용 설명")

        system_prompt = (
            "당신은 시각 장애 아동 학습 AI 비서입니다. "
            "시각 자료 내용을 한국어로 간결, 명확하게 설명하세요. "
            "음성 변환 가능하게 직접적이고 이해하기 쉽게 작성하세요."
        )

        # 4. 지수 백오프 재시도 로직
        max_retries = 3
        base_delay = 1.0  # 초 단위

        async with semaphore:  # Rate Limit 제어
            for attempt in range(max_retries):
                try:
                    # API 호출
                    response = await async_client.chat.completions.create(
                        model=model_name,
                        messages=[
                            {"role": "system", "content": system_prompt},
                            {
                                "role": "user",
                                "content": [
                                    {"type": "text", "text": prompt},
                                    {
                                        "type": "image_url",
                                        "image_url": {
                                            "url": f"data:image/png;base64,{img_base64}"
                                        },
                                    },
                                ],
                            },
                        ],
                        temperature=0.2,
                        max_tokens=600,
                    )

                    # 성공 시 결과 반환
                    description = response.choices[0].message.content.strip()
                    logger.debug(
                        f"API 응답 완료 (시도 {attempt + 1}/{max_retries}): "
                        f"Element {element.element_id}"
                    )
                    return description

                except openai.RateLimitError as e:
                    # Rate Limit 오류: 지수 백오프 대기 후 재시도
                    if attempt < max_retries - 1:
                        delay = base_delay * (2**attempt)  # 1초 → 2초 → 4초
                        logger.warning(
                            f"⚠️ Rate Limit 오류 (Element {element.element_id}): "
                            f"{delay}초 대기 후 재시도 ({attempt + 1}/{max_retries})"
                        )
                        await asyncio.sleep(delay)
                    else:
                        logger.error(
                            f"❌ Rate Limit 오류 최종 실패 (Element {element.element_id}): {e}"
                        )
                        raise  # 최종 실패 시 예외 전파

                except openai.APIError as e:
                    # API 일반 오류: 지수 백오프 대기 후 재시도
                    if attempt < max_retries - 1:
                        delay = base_delay * (2**attempt)
                        logger.warning(
                            f"⚠️ API 오류 (Element {element.element_id}): "
                            f"{delay}초 대기 후 재시도 ({attempt + 1}/{max_retries}) - {e}"
                        )
                        await asyncio.sleep(delay)
                    else:
                        logger.error(
                            f"❌ API 오류 최종 실패 (Element {element.element_id}): {e}"
                        )
                        raise

                except Exception as e:
                    # 기타 예외: 즉시 실패
                    logger.error(
                        f"❌ 예상치 못한 오류 (Element {element.element_id}): {e}",
                        exc_info=True,
                    )
                    raise

        # 모든 재시도 실패 시 빈 문자열 반환 (unreachable, but for type safety)
        return ""

    def visualize_results(
        self, image: np.ndarray, layout_elements: List[models.LayoutElement]
    ) -> np.ndarray:
        """결과 시각화 (기존과 동일)"""
        img_result = image.copy()
        overlay = image.copy()
        random.seed(42)
        unique_classes = list({elem.class_name for elem in layout_elements})
        class_colors = {}
        for i, cls_name in enumerate(unique_classes):
            h, s, v = i / max(1, len(unique_classes)), 0.8, 0.9
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            class_colors[cls_name] = (int(b * 255), int(g * 255), int(r * 255))
        for element in layout_elements:
            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            cls_name, color = element.class_name, class_colors[element.class_name]
            cv2.rectangle(overlay, (x1, y1), (x2, y2), color, -1)
            cv2.rectangle(img_result, (x1, y1), (x2, y2), color, 2)
            label = f"{cls_name} ({element.confidence:.2f})"
            labelSize, _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            y1_label = max(y1, labelSize[1] + 10)
            cv2.rectangle(
                img_result,
                (x1, y1_label - labelSize[1] - 10),
                (x1 + labelSize[0], y1_label),
                color,
                -1,
            )
            cv2.putText(
                img_result,
                label,
                (x1, y1_label - 5),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 255, 255),
                1,
            )
        img_result = cv2.addWeighted(overlay, 0.2, img_result, 0.8, 0)
        return cv2.cvtColor(img_result, cv2.COLOR_BGR2RGB)

    async def perform_ocr_async(
        self,
        image: np.ndarray,
        layout_elements: List[models.LayoutElement],
        *,
        db: Session,
        language: str = "kor",
        use_gemini: bool = True,
        max_concurrent_requests: Optional[int] = None,
    ) -> List[models.TextContent]:
        """
        Gemini 비동기 + Tesseract 동기 하이브리드 OCR 파이프라인.
        """
        target_classes = [
            "plain text",
            "unit",
            "question type",
            "question text",
            "question number",
            "title",
            "figure_caption",
            "table caption",
            "table footnote",
            "isolate_formula",
            "formula_caption",
            "list",
            "choices",
            "page",
            "second_question_number",
        ]
        tesseract_only_classes = {"question number", "second_question_number"}
        gemini_classes = [
            cls for cls in target_classes if cls not in tesseract_only_classes
        ]

        ocr_results: List[models.TextContent] = []
        tesseract_config = r"--oem 3 --psm 6"
        concurrency_limit = max(
            1, max_concurrent_requests or self.gemini_max_concurrency
        )

        logger.info(
            f"하이브리드 OCR 처리 시작... 총 {len(layout_elements)}개 레이아웃 요소 중 OCR 대상 필터링"
        )
        logger.info(f"  - Tesseract 전용 클래스 (2개): {sorted(tesseract_only_classes)}")
        logger.info(f"  - Gemini API 사용 클래스 (13개): {gemini_classes}")
        logger.info(f"  - Gemini API 가용 여부: {gemini_available}")
        if gemini_available and use_gemini:
            logger.info(f"  - Gemini 동시 처리 제한: {concurrency_limit}")
        detected_classes = {elem.class_name for elem in layout_elements}
        logger.info(f"  - 감지된 모든 클래스: {detected_classes}")

        gemini_jobs: List[GeminiOCRJob] = []
        target_count = 0
        for element in layout_elements:
            cls_name = element.class_name
            logger.debug(
                f"레이아웃 ID {element.element_id}: 클래스 '{cls_name}' 확인 중..."
            )
            if cls_name not in target_classes:
                logger.debug("  → OCR 대상 아님")
                continue

            target_count += 1
            logger.debug(
                f"  → OCR 대상 {target_count}: ID {element.element_id} - 클래스 '{cls_name}'"
            )

            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(image.shape[1], x2), min(image.shape[0], y2)

            if y2 <= y1 or x2 <= x1:
                logger.warning(
                    f"  → 유효하지 않은 BBox 크기: ID {element.element_id}, 건너뜀"
                )
                continue
            cropped_img = image[y1:y2, x1:x2]

            should_use_gemini = (
                use_gemini and gemini_available and cls_name in gemini_classes
            )

            if should_use_gemini:
                pil_img = Image.fromarray(cv2.cvtColor(cropped_img, cv2.COLOR_BGR2RGB))
                gemini_jobs.append(
                    GeminiOCRJob(
                        element=element,
                        cls_name=cls_name,
                        pil_image=pil_img,
                        cropped_img=cropped_img,
                    )
                )
                continue

            text = self._run_tesseract_ocr(
                cropped_img=cropped_img,
                language=language,
                config=tesseract_config,
            )
            self._store_ocr_result(
                db=db,
                ocr_results=ocr_results,
                element=element,
                text=text,
                engine_name="Tesseract",
                language=language,
                cls_name=cls_name,
            )

        if gemini_jobs:
            logger.info(
                f"Gemini OCR 비동기 처리 시작: {len(gemini_jobs)}개 요소 (동시 {concurrency_limit})"
            )
            gemini_results = await self._execute_gemini_jobs_async(
                jobs=gemini_jobs,
                language=language,
                concurrency_limit=concurrency_limit,
            )

            for result in gemini_results:
                element = result.job.element
                cls_name = result.job.cls_name
                text = result.text
                engine_name = result.engine

                if not text:
                    warn_msg = (
                        f"⚠️ [{cls_name}] Gemini OCR 실패: ID {element.element_id}"
                    )
                    if result.error:
                        warn_msg += f" - {result.error}"
                    logger.warning(warn_msg + " - Tesseract로 대체")
                    text = self._run_tesseract_ocr(
                        cropped_img=result.job.cropped_img,
                        language=language,
                        config=tesseract_config,
                    )
                    engine_name = "Tesseract (Fallback)"
                else:
                    logger.debug(
                        f"  → [{cls_name}] Gemini API 응답 성공: {len(text)}자"
                    )

                self._store_ocr_result(
                    db=db,
                    ocr_results=ocr_results,
                    element=element,
                    text=text,
                    engine_name=engine_name,
                    language=language,
                    cls_name=cls_name,
                )

        db.commit()
        for content in ocr_results:
            db.refresh(content)

        engine_summary = "하이브리드 OCR (Tesseract + Gemini-2.5-Flash-Lite)"
        logger.info(
            f"OCR 처리 완료 ({engine_summary}): {len(ocr_results)}개 텍스트 블록 저장"
        )
        return ocr_results

    def perform_ocr(
        self,
        image: np.ndarray,
        layout_elements: List[models.LayoutElement],
        *,
        db: Session,
        language: str = "kor",
        use_gemini: bool = True,
        max_concurrent_requests: Optional[int] = None,
    ) -> List[models.TextContent]:
        """
        동기 환경 호환을 위한 래퍼. 비동기 컨텍스트에서는 `await perform_ocr_async()` 사용.
        """
        try:
            asyncio.get_running_loop()
        except RuntimeError:
            return asyncio.run(
                self.perform_ocr_async(
                    image,
                    layout_elements,
                    db=db,
                    language=language,
                    use_gemini=use_gemini,
                    max_concurrent_requests=max_concurrent_requests,
                )
            )
        raise RuntimeError(
            "perform_ocr()는 비동기 이벤트 루프 안에서 호출할 수 없습니다. "
            "대신 await analysis_service.perform_ocr_async(...)를 사용하세요."
        )

    def _run_tesseract_ocr(
        self, *, cropped_img: np.ndarray, language: str, config: str
    ) -> str:
        """Tesseract OCR 전처리 및 실행."""
        gray_img = cv2.cvtColor(cropped_img, cv2.COLOR_BGR2GRAY)
        _, binary_img = cv2.threshold(
            gray_img, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
        )
        _ = cv2.medianBlur(binary_img, 3)  # 노이즈 제거 (추후 필요 시 활용)
        pil_img = Image.fromarray(cropped_img)
        tess_lang = language or "kor+eng"
        if tess_lang == "kor":
            tess_lang = "kor+eng"
        return (
            pytesseract.image_to_string(pil_img, lang=tess_lang, config=config)
            .strip()
        )

    def _store_ocr_result(
        self,
        *,
        db: Session,
        ocr_results: List[models.TextContent],
        element: models.LayoutElement,
        text: str,
        engine_name: str,
        language: str,
        cls_name: str,
    ) -> None:
        """OCR 결과 DB 저장 + 로깅."""
        normalized_text = (text or "").strip()
        if len(normalized_text) <= 1:
            logger.warning(
                f"⚠️ OCR 결과 없음 ({engine_name}): ID {element.element_id} ({cls_name})"
            )
            return

        db_text = self._upsert_text_content(
            db=db,
            element_id=element.element_id,
            ocr_text=normalized_text,
            ocr_engine=engine_name,
            language=language,
            ocr_confidence=None,
        )
        ocr_results.append(db_text)
        preview = normalized_text[:50].replace("\n", " ")
        logger.info(
            f"✅ OCR 성공 ({engine_name}): ID {element.element_id} ({cls_name}) - "
            f"'{preview}...' ({len(normalized_text)}자)"
        )

    async def _execute_gemini_jobs_async(
        self,
        *,
        jobs: List[GeminiOCRJob],
        language: str,
        concurrency_limit: int,
    ) -> List[GeminiOCRResult]:
        """Gemini OCR 작업을 비동기로 실행."""
        if not jobs:
            return []

        semaphore = asyncio.Semaphore(max(1, concurrency_limit))
        tasks = [
            self.call_gemini_ocr_async(
                job=job,
                language=language,
                semaphore=semaphore,
                max_retries=self.gemini_max_retries,
                base_delay=self.gemini_retry_base_delay,
            )
            for job in jobs
        ]
        return await asyncio.gather(*tasks)

    async def call_gemini_ocr_async(
        self,
        *,
        job: GeminiOCRJob,
        language: str,
        semaphore: asyncio.Semaphore,
        max_retries: int,
        base_delay: float,
    ) -> GeminiOCRResult:
        """단일 Gemini OCR 호출 (세마포어 + 재시도 포함)."""
        if not gemini_available:
            return GeminiOCRResult(
                job=job, text="", error=GeminiOCRError("Gemini API 비활성화")
            )

        retries = max(1, max_retries)
        delay = max(0.1, base_delay)

        async with semaphore:
            for attempt in range(1, retries + 1):
                try:
                    text = await asyncio.to_thread(
                        self._generate_gemini_text,
                        job.pil_image.copy(),
                        language,
                    )
                    if not text:
                        raise GeminiOCRError("Gemini 응답이 비어 있습니다.")
                    return GeminiOCRResult(job=job, text=text)

                except Exception as exc:
                    if attempt < retries:
                        wait_time = delay * (2 ** (attempt - 1))
                        logger.warning(
                            f"⚠️ [{job.cls_name}] Gemini OCR 실패 (시도 {attempt}/{retries}): "
                            f"{exc} - {wait_time:.1f}s 후 재시도"
                        )
                        await asyncio.sleep(wait_time)
                    else:
                        logger.error(
                            f"❌ [{job.cls_name}] Gemini OCR 최종 실패: {exc}"
                        )
                        return GeminiOCRResult(job=job, text="", error=exc)

        return GeminiOCRResult(job=job, text="", error=GeminiOCRError("알 수 없는 오류"))

    def _generate_gemini_text(
        self, pil_img: Image.Image, language: str = "kor"
    ) -> str:
        """Gemini 2.5 Flash Lite 호출."""
        if not gemini_available:
            raise GeminiOCRError("Gemini API가 활성화되지 않았습니다.")

        prompt = (
            "Extract all text from this image exactly as it appears, regardless of language. "
            "Return only the plain text without any markdown formatting, translations, explanations, or additional comments."
        )
        model = genai.GenerativeModel("gemini-2.5-flash-lite")
        response = model.generate_content(
            [pil_img, prompt],
            safety_settings=GEMINI_SAFETY_SETTINGS,
        )

        candidates = getattr(response, "candidates", None)
        if not candidates:
            feedback = getattr(response, "prompt_feedback", None)
            raise GeminiOCRError(f"후보 응답 없음 (prompt_feedback={feedback})")

        first_candidate = candidates[0]
        parts = getattr(first_candidate.content, "parts", None)
        if not parts:
            raise GeminiOCRError("응답 콘텐츠가 비어 있습니다.")

        return response.text.strip()


def analyze_page(
    *,
    page_id: int,
    image: np.ndarray,
    db: Session,
    api_key: Optional[str] = None,
    model_choice: Optional[str] = None,
) -> Dict[str, object]:
    """단일 페이지에 대한 전체 분석 파이프라인을 실행한다."""
    service = AnalysisService(
        model_choice=model_choice or "SmartEyeSsen", auto_load=False
    )

    layout_elements = service.analyze_layout(
        image=image, page_id=page_id, db=db, model_choice=model_choice
    )

    text_contents = service.perform_ocr(
        image=image, layout_elements=layout_elements, db=db
    )

    ai_descriptions: Dict[int, str] = {}
    if api_key:
        ai_descriptions = service.call_openai_api(
            image=image, layout_elements=layout_elements, api_key=api_key, db=db
        )

    return {
        "layout_elements": layout_elements,
        "text_contents": text_contents,
        "ai_descriptions": ai_descriptions,
    }
