# -*- coding: utf-8 -*-
"""
SmartEyeSsen Analysis Service (v1.1 - Duplicate Detection Filter Added)
========================================================================

학습지 분석 서비스 - 레이아웃 분석, OCR, AI 설명 생성을 담당합니다.
Refactored from api_server.py WorksheetAnalyzer class.

주요 변경사항 (v1.1):
- analyze_layout: 중복 탐지(예: question text vs choices) 제거 위한 IoU 기반 후처리 로직 추가.

주요 변경사항 (v1.0):
- 상태 저장 제거: self.layout_info, self.ocr_results, self.api_results 제거
- Mock 모델 사용: MockElement, MockTextContent 반환
- 반환 값 변경:
  - analyze_layout() → list[MockElement]
  - perform_ocr() → list[MockTextContent]
  - call_openai_api() → dict[int, str] (element_id → description)
"""

import cv2
import base64
import io
import colorsys
import random
from typing import List, Dict
from PIL import Image
import numpy as np

# AI 및 OCR 관련 패키지
import torch
from huggingface_hub import hf_hub_download
import pytesseract
import openai
from loguru import logger
import platform

# Mock 모델 임포트
from .mock_models import MockElement, MockTextContent, create_mock_element_from_detection, create_mock_text_content

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
다음 순서도를 시각 장애인이 논리적 흐름을 완전히 이해할 수 있도록 설명해주세요.

[설명 규칙]
1. 시작과 끝: 프로세스의 시작점과 종료점을 명확히 표시
2. 단계별 설명: 각 단계를 번호를 매겨 순차적으로 설명
3. 분기점 강조: 
   - 결정 포인트는 "만약...라면"으로 표현
   - 각 선택지의 결과를 명시
4. 연결 관계: 화살표 방향을 "다음", "이동", "돌아감" 등으로 표현
5. 반복 구조: 루프나 순환 구조를 명확히 설명

[출력 형식]
제목: [순서도의 목적]
시작: [시작 조건이나 입력]
프로세스:
  단계 1: [작업 내용]
  단계 2: [작업 내용]
  결정 1: 만약 [조건]이라면
    - 예: [다음 단계]로 이동
    - 아니오: [다른 단계]로 이동
  단계 3: [작업 내용]
종료: [최종 결과나 출력]
전체 흐름 요약: [프로세스의 핵심 목적]

[예시]
제목: 로그인 프로세스
시작: 사용자가 로그인 페이지 접속
프로세스:
  단계 1: 아이디와 비밀번호 입력
  단계 2: 로그인 버튼 클릭
  결정 1: 만약 입력 정보가 올바르다면
    - 예: 단계 3으로 이동
    - 아니오: 단계 4로 이동
  단계 3: 메인 페이지로 이동하고 종료
  단계 4: 오류 메시지 표시
  결정 2: 만약 3회 이상 실패했다면
    - 예: 계정 잠금 후 종료
    - 아니오: 단계 1로 돌아감
종료: 로그인 성공 또는 계정 잠금
전체 흐름 요약: 사용자 인증을 통해 시스템 접근 권한을 부여하는 보안 프로세스
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
    suppressed = [False] * num_detections # 제거할 요소 표시
    

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
                logger.debug(f"중복 탐지 제거: Box {idx2}(conf={confs[idx2]:.2f}) - "
                             f"Box {idx1}(conf={confs[idx1]:.2f})와 IoU={iou:.2f} > {iou_threshold}")
            # --- 👆 수정된 부분 끝 👆 ---
            
    # 제거되지 않은 요소들의 인덱스 반환
    final_indices = [i for i, s in enumerate(suppressed) if not s]
    logger.info(f"자동 중복 탐지 필터링: {num_detections}개 → {len(final_indices)}개 요소 (IoU > {iou_threshold})") # 로그 메시지 수정
    return final_indices

# Windows에서 Tesseract 경로 설정 (기존과 동일)
if platform.system() == "Windows":
    pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

# 디바이스 설정 (기존과 동일)
device = 'cuda:0' if torch.cuda.is_available() else 'cpu'


class AnalysisService:
    """학습지 분석 서비스 - 상태 없는 함수형 디자인"""

    def __init__(self):
        """분석 서비스 초기화 (기존과 동일)"""
        self.model = None
        self.device = device

    def download_model(self, model_choice="SmartEyeSsen"):
        """모델 다운로드 (기존과 동일)"""
        models = {
            "doclaynet_docsynth": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
                "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt"
            },
            "docstructbench": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
                "filename": "doclayout_yolo_docstructbench_imgsz1024.pt"
            },
            "docsynth300k": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
                "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt"
            },
            "SmartEyeSsen": {
                "repo_id": "AkJeond/SmartEye",
                "filename": "best.pt"
            }
        }
        selected_model = models.get(model_choice, models["SmartEyeSsen"])
        try:
            logger.info(f"모델 다운로드 중: {selected_model['repo_id']}")
            filepath = hf_hub_download(
                repo_id=selected_model["repo_id"],
                filename=selected_model["filename"]
            )
            logger.info(f"모델 다운로드 완료: {filepath}")
            return filepath
        except Exception as e:
            logger.error(f"모델 다운로드 실패: {e}")
            raise

    def load_model(self, model_path):
        """모델 로드 (기존과 동일)"""
        try:
            try: from doclayout_yolo import YOLOv10
            except ImportError:
                logger.error("DocLayout-YOLO가 설치되지 않았습니다.")
                return False
            logger.info("모델 로드 중...")
            self.model = YOLOv10(model_path, task='predict')
            self.model.to(self.device)
            if hasattr(self.model, 'training'): self.model.training = False
            logger.info("모델 로드 완료!")
            return True
        except Exception as e:
            logger.error(f"모델 로드 실패: {e}")
            return False

    def analyze_layout(self, image: np.ndarray, model_choice: str = "SmartEyeSsen") -> List[MockElement]:
        """레이아웃 분석 + 중복 탐지 필터링 추가"""
        try:
            logger.info("레이아웃 분석 시작...")
            temp_path = "temp_image.jpg"
            cv2.imwrite(temp_path, image)

            if model_choice == "SmartEyeSsen": imgsz, conf = 1024, 0.25
            elif model_choice == "docsynth300k": imgsz, conf = 1600, 0.15
            else: imgsz, conf = 1024, 0.25

            results = self.model.predict(
                temp_path, imgsz=imgsz, conf=conf, iou=0.45, device=self.device
            )

            # --- 결과 추출 ---
            boxes = results[0].boxes.xyxy.cpu().numpy() # [x1, y1, x2, y2] 형식
            classes = results[0].boxes.cls.cpu().numpy()
            confs = results[0].boxes.conf.cpu().numpy()
            class_names = self.model.names # 클래스 ID -> 이름 매핑

            if not boxes.size: # 탐지 결과 없으면 빈 리스트 반환
                logger.warning("레이아웃 분석 결과, 감지된 요소가 없습니다.")
                return []

            # --- 신규: 중복 탐지 필터링 ---
            final_indices = filter_duplicate_detections(boxes, classes, confs, class_names, iou_threshold=0.7) # IoU 90% 이상 겹치면 제거

            # --- MockElement 리스트 생성 (필터링된 인덱스만 사용) ---
            mock_elements = []
            element_id_counter = 1 # 최종 요소 ID는 1부터 시작
            for i in final_indices: # 필터링 후 남은 인덱스만 사용
                box = boxes[i]
                cls_id = int(classes[i])
                conf_val = float(confs[i])
                x1, y1, x2, y2 = map(int, box)

                try:
                    cls_name = class_names[cls_id]
                except (IndexError, KeyError): # class_names가 list 또는 dict 일 수 있음
                    cls_name = f"unknown_{cls_id}"

                # 너무 작은 영역 제외 (기존 로직 유지)
                width = x2 - x1
                height = y2 - y1
                area = width * height
                if area < 100: continue

                # MockElement 생성
                detection_result = {
                    'class_name': cls_name,
                    'confidence': conf_val,
                    'bbox': [x1, y1, width, height] # [x, y, width, height]
                }
                mock_element = create_mock_element_from_detection(
                    element_id=element_id_counter, # 순차적 ID 부여
                    detection_result=detection_result,
                    page_id=None
                )
                mock_elements.append(mock_element)
                element_id_counter += 1

            logger.info(f"레이아웃 분석 완료: 최종 {len(mock_elements)}개 요소")
            return mock_elements

        except Exception as e:
            logger.error(f"레이아웃 분석 실패: {e}", exc_info=True) # 상세 에러 로그 추가
            return []

    def perform_ocr(self, image: np.ndarray, layout_elements: List[MockElement]) -> List[MockTextContent]:
        """OCR 처리 (영역별 전처리 추가)"""
        target_classes = [
            'plain text', 'unit', 'question type', 'question text', 'question number', 'title',
            'figure_caption', 'table caption', 'table footnote', 'isolate_formula', 'formula_caption',
            'list', 'choices', 'page', 'second_question_number'
        ]
        ocr_results = []
        custom_config = r'--oem 3 --psm 6'
        logger.info(f"OCR 처리 시작... 총 {len(layout_elements)}개 레이아웃 요소 중 OCR 대상 필터링")
        logger.info(f"OCR 대상 클래스 목록: {target_classes}")
        detected_classes = {elem.class_name for elem in layout_elements} # Set으로 변경
        logger.info(f"감지된 모든 클래스: {detected_classes}")
        
        target_count = 0
        text_id_counter = 1
        
        for element in layout_elements:
            cls_name = element.class_name # Pydantic 모델은 이미 lower() 불필요
            logger.debug(f"레이아웃 ID {element.element_id}: 클래스 '{cls_name}' 확인 중...") # DEBUG 레벨로 변경
            if cls_name not in target_classes:
                logger.debug(f"  → OCR 대상 아님")
                continue
            
            target_count += 1
            logger.debug(f"  → OCR 대상 {target_count}: ID {element.element_id} - 클래스 '{cls_name}'")
            
            # 1. 영역 이미지 잘라내기 (기존 코드)
            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            # 이미지 경계 내로 좌표 조정
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(image.shape[1], x2), min(image.shape[0], y2)
            
            if y2 <= y1 or x2 <= x1: # 크기가 0이거나 음수인 경우 건너뛰기
                logger.warning(f"  → 유효하지 않은 BBox 크기: ID {element.element_id}, 건너<0xEB><0x9B><0x84>뜀")
                continue
            cropped_img = image[y1:y2, x1:x2]
            
            try:
                # --- 👇 영역별 전처리 단계 시작 👇 ---

                # 2. 그레이스케일 변환: 색상 정보 제거
                gray_img = cv2.cvtColor(cropped_img, cv2.COLOR_BGR2GRAY)

                # 3. 이진화 (Otsu's Binarization): 텍스트/배경 명확화
                # Otsu 방식은 임계값을 자동으로 결정해 줍니다.
                # 필요에 따라 cv2.adaptiveThreshold 등 다른 방식 사용 가능
                _, binary_img = cv2.threshold(gray_img, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

                # 4. (선택적) 노이즈 제거: Median 필터 적용 (작은 점 제거에 효과적)
                # 커널 크기(예: 3)는 실험을 통해 조정
                denoised_img = cv2.medianBlur(binary_img, 3)

                # --- 👆 영역별 전처리 단계 끝 👆 ---

                # 5. 전처리된 이미지로 OCR 수행
                # Pillow 이미지로 변환 (Tesseract는 Pillow 이미지 입력 선호)
                pil_img = Image.fromarray(cropped_img)
                text = pytesseract.image_to_string(pil_img, lang='kor', config=custom_config).strip()
                
                if len(text) > 1: # 빈 문자열이 아닌 경우만
                    mock_text_content = create_mock_text_content(
                        text_id=text_id_counter, element_id=element.element_id, ocr_result=text,
                        ocr_confidence=None, ocr_engine="Tesseract"
                    )
                    ocr_results.append(mock_text_content)
                    text_id_counter += 1
                    logger.info(f"✅ OCR 성공: ID {element.element_id} ({cls_name}) - '{text[:50].replace(chr(10), ' ')}...' ({len(text)}자)") # 개행문자 제거
                else: logger.warning(f"⚠️ OCR 결과 없음: ID {element.element_id} ({cls_name})")
            except Exception as e: logger.error(f"OCR 실패: ID {element.element_id} - {e}", exc_info=True) # 상세 에러
        logger.info(f"OCR 처리 완료: {len(ocr_results)}개 텍스트 블록")
        return ocr_results


    def call_openai_api(self, image: np.ndarray, layout_elements: List[MockElement], api_key: str) -> Dict[int, str]:
        """OpenAI API 호출 (기존과 동일, 로깅 레벨 조정)"""
        if not api_key:
            logger.warning("API 키가 없어 AI 설명을 건너<0xEB><0x9B><0x84>뜁니다.")
            return {}
        target_classes = ['figure', 'table', 'flowchart']
        ai_descriptions = {}
        try:
            client = openai.OpenAI(api_key=api_key)
            logger.info("OpenAI API 처리 시작...")
        except Exception as e:
            logger.error(f"OpenAI 클라이언트 초기화 실패: {e}")
            return {}
        prompts = {
            'figure': figure_prompt, 
            'table': table_prompt, 
            'flowchart': flowchart_prompt
            }
        system_prompt = "당신은 시각 장애 아동 학습 AI 비서입니다. 시각 자료 내용을 한국어로 간결, 명확하게 설명하세요. 음성 변환 가능하게 직접적이고 이해하기 쉽게 작성하세요."
        for element in layout_elements:
            cls_name = element.class_name
            if cls_name not in target_classes: continue
            x1, y1 = element.bbox_x, element.bbox_y
            x2, y2 = x1 + element.bbox_width, y1 + element.bbox_height
            if y2 <= y1 or x2 <= x1: continue # 크기 0 방지
            cropped_img = image[y1:y2, x1:x2]
            pil_img = Image.fromarray(cv2.cvtColor(cropped_img, cv2.COLOR_BGR2RGB))
            buffered = io.BytesIO(); pil_img.save(buffered, format="PNG")
            img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
            prompt = prompts.get(cls_name, f"이 {cls_name} 내용 설명")
            try:
                response = client.chat.completions.create(
                    model="gpt-4-turbo", # 또는 gpt-4o
                    messages=[{"role": "system", "content": system_prompt},
                              {"role": "user", "content": [{"type": "text", "text": prompt},
                                                          {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{img_base64}"}}]}],
                    temperature=0.2, max_tokens=600 )
                description = response.choices[0].message.content.strip()
                ai_descriptions[element.element_id] = description
                logger.info(f"API 응답 완료: ID {element.element_id} - {cls_name}")
            except Exception as e: logger.error(f"API 요청 실패: ID {element.element_id} - {e}", exc_info=True) # 상세 에러
        logger.info(f"OpenAI API 처리 완료: {len(ai_descriptions)}개 설명 생성")
        return ai_descriptions

    def visualize_results(self, image: np.ndarray, layout_elements: List[MockElement]) -> np.ndarray:
        """결과 시각화 (기존과 동일)"""
        img_result = image.copy(); overlay = image.copy()
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
            cv2.rectangle(img_result, (x1, y1_label - labelSize[1] - 10), (x1 + labelSize[0], y1_label), color, -1)
            cv2.putText(img_result, label, (x1, y1_label - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        img_result = cv2.addWeighted(overlay, 0.2, img_result, 0.8, 0)
        return cv2.cvtColor(img_result, cv2.COLOR_BGR2RGB)