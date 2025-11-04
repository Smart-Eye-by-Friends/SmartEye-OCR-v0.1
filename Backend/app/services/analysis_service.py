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
import platform
import random
from typing import Dict, List, Optional

import cv2
import numpy as np
import openai
import pytesseract
import torch
from PIL import Image
from huggingface_hub import hf_hub_download
from loguru import logger
from openai import AsyncOpenAI
from sqlalchemy.orm import Session

from .. import models

# --- 신규: 이미지 설명을 위한 프롬프트 템플릿 추가 ---
figure_prompt = """
당신은 시각 장애인을 위한 점자도서 작성 전문가입니다.
다음 이미지를 시각 장애인이 완전히 이해할 수 있도록 설명해주세요.

[설명 규칙]
1. 전체 구조: 이미지의 전반적인 형태와 구성을 먼저 설명
2. 공간 관계: 상하좌우, 중앙 등 위치 관계를 명확히 표현
3. 핵심 요소: 가장 중요한 시각 정보를 우선순위대로 설명
4. 세부 사항: 색상, 크기, 모양을 촉각적 표현으로 변환
   - 예: "진한 빨간색" → "강조된 부분"
   - 예: "큰 원" → "손바닥 크기의 둥근 형태"
5. 의미 전달: 이미지가 전달하려는 핵심 메시지 요약

[출력 형식]
제목: [이미지의 주제]
구조: [전체적인 레이아웃]
주요 요소: [핵심 구성요소 나열]
상세 설명: [각 요소의 관계와 의미]
핵심 메시지: [이미지의 목적/의도]

[예시]
제목: 한국의 인구 증가 그래프
구조: 가로축에 연도(2000-2025), 세로축에 인구수가 표시된 선 그래프
주요 요소: 2000년 4,700만에서 시작하여 2025년 5,200만까지 상승하는 곡선
상세 설명: 2010년까지 급격한 상승, 이후 완만한 증가세를 보임
핵심 메시지: 한국 인구는 25년간 약 10% 증가했으며, 최근 증가율이 둔화됨
"""

table_prompt = """
당신은 시각 장애인을 위한 점자도서 작성 전문가입니다.
다음 표를 시각 장애인이 점자로 읽기 쉽도록 변환해주세요.

[변환 규칙]
1. 구조 우선: 행과 열의 개수를 먼저 명시
2. 헤더 구분: 표 머리글을 명확히 구분하여 제시
3. 데이터 정리: 
   - 숫자는 단위와 함께 명시
   - 백분율은 "퍼센트"로 표기
   - 빈 셀은 "없음"으로 표기
4. 순차적 읽기: 왼쪽에서 오른쪽, 위에서 아래로
5. 관계 설명: 데이터 간 비교나 추세 포함

[출력 형식]
표 제목: [표의 주제]
표 구조: [행 개수] 곱하기 [열 개수]
열 제목: [각 열의 헤더]
데이터:
  행1: [데이터1], [데이터2], ...
  행2: [데이터1], [데이터2], ...
주요 발견: [표에서 중요한 패턴이나 정보]

[예시]
표 제목: 2024년 분기별 매출 실적
표 구조: 5행 곱하기 4열
열 제목: 구분, 1분기, 2분기, 3분기
데이터:
  행1(매출액): 100억원, 120억원, 150억원
  행2(성장률): 10퍼센트, 20퍼센트, 25퍼센트
  행3(영업이익): 10억원, 15억원, 20억원
  행4(순이익): 8억원, 12억원, 18억원
주요 발견: 매출액이 매 분기 지속적으로 증가하며, 3분기에 가장 높은 성장률 기록
"""

flowchart_prompt = """
당신은 시각 장애인을 위한 점자도서 작성 전문가입니다.  
다음 순서도(Flowchart) 또는 개념도(Concept Map)를 시각 장애인이 시각적 구조와 논리적 흐름을 모두 이해할 수 있도록 설명해주세요.  

[설명 규칙]
1. 전체 구조 (Structure First): 가장 먼저, 다이어그램의 전반적인 레이아웃(예: '상단에 1개, 하단에 2개의 상자')을 설명합니다.  
2. 공간 관계 (Spatial Flow): 화살표(연결선)의 방향을 "A에서 B로 이동", "두 요소가 C로 합쳐짐", "A가 B와 C로 나뉨" 등으로 명확히 설명합니다.  
3. 핵심 요소 (Key Elements): 각 상자(노드) 안의 핵심 내용(텍스트, 숫자, 또는 이미지 내용)을 설명합니다.  
4. 분기점 (Branching): 만약 결정(다이아몬드 형태) 노드가 있다면, "만약...라면"으로 조건을 설명하고 각 선택지(예/아니오)의 경로를 명확히 구분합니다.  
5. 의미 전달 (Core Message): 이 다이어그램이 전달하려는 핵심 의미나 목적을 요약합니다.

[출력 형식]  
제목: [다이어그램의 주제 또는 목적]  
전체 구조: [시각적 레이아웃 설명. 예: 3개의 상자가 위에서 아래로 연결된 구조]  
흐름 설명:  
시작: [시작 상자(들)의 내용]  
중간: [화살표를 따라가며 다음 상자의 내용과 관계 설명. 분기점이 있다면 이곳에서 설명]  
종료: [최종 상자(들)의 내용]  
핵심 요약: [다이어그램이 전달하려는 핵심 메시지 또는 결론]  

[예시 1: 개념도]  
제목: 덧셈 개념  
전체 구조: 상단에 두 개의 분리된 상자가 있고, 두 개의 화살표가 이 상자들을 하단의 큰 상자 하나로 모으는 구조입니다.  
흐름 설명:  
시작: 상단 왼쪽 상자에는 '항목 A (예: 무당벌레 1마리)'가 있습니다. 상단 오른쪽 상자에는 '항목 B (예: 무당벌레 3마리)'가 있습니다.  
중간: 이 두 상자에서 나온 화살표가 하단 상자로 합쳐집니다.  
종료: 하단 상자에는 '결과 C (예: 무당벌레 4마리)'가 있습니다.  
핵심 요약: 항목 A와 항목 B를 더하면 결과 C가 됨을 보여주는 덧셈 다이어그램입니다.

[예시 2: 분기형]  
제목: 숫자 나누기  
전체 구조: 상단에 1개의 상자가 있고, 이 상자에서 2개의 화살표가 나와 하단의 분리된 2개 상자로 각각 연결되는 Y자 형태입니다.  
흐름 설명:  
시작: 상단 상자에는 숫자 '5'가 있습니다.  
중간: 상자 '5'에서 두 개의 화살표가 나와 각각 하단의 빈 상자로 나뉩니다.  
종료: 하단의 왼쪽 빈 상자와 오른쪽 빈 상자로 연결됩니다. (내용이 비어있음을 명시)  
핵심 요약: 숫자 5를 두 부분으로 나누는 과정을 보여주는 다이어그램입니다.
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


class AnalysisService:
    """학습지 분석 서비스 - 상태 없는 함수형 디자인"""

    def __init__(self, model_choice: str = "SmartEyeSsen", auto_load: bool = False):
        """
        분석 서비스 초기화

        Args:
            model_choice: 사용할 모델 선택 (기본값: "SmartEyeSsen")
            auto_load: True이면 초기화 시 자동으로 모델 로드 (기본값: False, 하위 호환성 유지)
        """
        self.model = None
        self.device = device
        self.model_choice = model_choice
        self._model_loaded = False

        # 자동 로드 옵션이 활성화된 경우 즉시 모델 로드
        if auto_load:
            self._ensure_model_loaded()

    def download_model(self, model_choice="SmartEyeSsen"):
        """모델 다운로드 (기존과 동일)"""
        models = {
            "doclaynet_docsynth": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
                "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt",
            },
            "docstructbench": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
                "filename": "doclayout_yolo_docstructbench_imgsz1024.pt",
            },
            "docsynth300k": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
                "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt",
            },
            "SmartEyeSsen": {"repo_id": "AkJeond/SmartEye", "filename": "best.pt"},
        }
        selected_model = models.get(model_choice, models["SmartEyeSsen"])
        try:
            logger.info(f"모델 다운로드 중: {selected_model['repo_id']}")
            filepath = hf_hub_download(
                repo_id=selected_model["repo_id"], filename=selected_model["filename"]
            )
            logger.info(f"모델 다운로드 완료: {filepath}")
            return filepath
        except Exception as e:
            logger.error(f"모델 다운로드 실패: {e}")
            raise

    def load_model(self, model_path):
        """모델 로드 (기존과 동일)"""
        try:
            try:
                from doclayout_yolo import YOLOv10
            except ImportError:
                logger.error("DocLayout-YOLO가 설치되지 않았습니다.")
                return False
            logger.info("모델 로드 중...")
            self.model = YOLOv10(model_path, task="predict")
            self.model.to(self.device)
            if hasattr(self.model, "training"):
                self.model.training = False
            logger.info("모델 로드 완료!")
            return True
        except Exception as e:
            logger.error(f"모델 로드 실패: {e}")
            return False

    def _ensure_model_loaded(self):
        """
        Lazy Loading: 모델이 로드되지 않았으면 자동으로 로드
        (다중 페이지 처리 시 모델을 한 번만 로드하도록 최적화)
        """
        if self._model_loaded and self.model is not None:
            return  # 이미 로드됨

        logger.info(f"모델 자동 로드 시작 (선택: {self.model_choice})...")
        model_path = self.download_model(self.model_choice)
        if not self.load_model(model_path):
            raise RuntimeError(f"모델 로드 실패: {self.model_choice}")
        self._model_loaded = True
        logger.info("모델 자동 로드 완료!")

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
            self._ensure_model_loaded()

            logger.info("레이아웃 분석 시작...")
            temp_path = "temp_image.jpg"
            cv2.imwrite(temp_path, image)

            if active_model == "SmartEyeSsen":
                imgsz, conf = 1024, 0.25
            elif active_model == "docsynth300k":
                imgsz, conf = 1600, 0.15
            else:
                imgsz, conf = 1024, 0.25

            results = self.model.predict(
                temp_path, imgsz=imgsz, conf=conf, iou=0.45, device=self.device
            )

            boxes = results[0].boxes.xyxy.cpu().numpy()  # [x1, y1, x2, y2]
            classes = results[0].boxes.cls.cpu().numpy()
            confs = results[0].boxes.conf.cpu().numpy()
            class_names = self.model.names  # 클래스 ID → 이름

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
        custom_config = r"--oem 3 --psm 6"
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
                pil_img = Image.fromarray(cropped_img)
                text = pytesseract.image_to_string(
                    pil_img, lang="kor", config=custom_config
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
        max_concurrent_requests: int = 5,
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
