# -*- coding: utf-8 -*-
"""
SmartEyeSsen 학습지 분석 API 서버
기존 8_4worksheet_analysis_gradio_notebook.py의 핵심 파이프라인을 FastAPI로 변환
"""

import os
import sys
import cv2
import json
import time
import base64
import io
import colorsys
import random
from collections import Counter
from typing import Optional
from PIL import Image, ImageDraw, ImageFont
import numpy as np

# FastAPI 및 관련 패키지
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import uvicorn

# AI 및 OCR 관련 패키지  
import torch
from huggingface_hub import hf_hub_download
import pytesseract
import openai
from loguru import logger

# 로그 설정
logger.remove()
logger.add(sys.stderr, level="INFO")

# 디바이스 설정
device = 'cuda:0' if torch.cuda.is_available() else 'cpu'
print(f"✅ 사용 디바이스: {device}")

# FastAPI 앱 생성
app = FastAPI(
    title="SmartEyeSsen 학습지 분석 API",
    description="시각 장애 아동을 위한 AI 기반 학습지 분석 및 텍스트 변환 시스템",
    version="1.0.0"
)

# CORS 설정 (Vue.js 프론트엔드에서 접근할 수 있도록)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 특정 도메인만 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 정적 파일 제공 (결과 이미지 및 JSON 파일)
os.makedirs("static", exist_ok=True)
app.mount("/static", StaticFiles(directory="static"), name="static")


class WorksheetAnalyzer:
    """학습지 분석기 클래스 - Gradio 버전에서 이식"""
    
    def __init__(self):
        self.model = None
        self.device = device
        self.layout_info = []
        self.ocr_results = []
        self.api_results = []

    def download_model(self, model_choice="SmartEyeSsen"):
        """사전 훈련된 DocLayout-YOLO 모델 다운로드"""
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
                "repo_id": "AkJeond/SmartEyeSsen",
                "filename": "best_tuned_model.pt"
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
        """DocLayout-YOLO 모델 로드"""
        try:
            # DocLayout-YOLO 임포트 (여기서 임포트하여 모델이 필요할 때만 로드)
            try:
                from doclayout_yolo import YOLOv10
            except ImportError:
                logger.error("DocLayout-YOLO가 설치되지 않았습니다. 설치 가이드를 확인하세요.")
                return False

            logger.info("모델 로드 중...")
            self.model = YOLOv10(model_path, task='predict')
            self.model.to(self.device)
            if hasattr(self.model, 'training'):
                self.model.training = False
            logger.info("모델 로드 완료!")
            return True
        except Exception as e:
            logger.error(f"모델 로드 실패: {e}")
            return False

    def analyze_layout(self, image, model_choice="SmartEyeSsen"):
        """레이아웃 분석"""
        try:
            logger.info("레이아웃 분석 시작...")

            # 임시 이미지 저장
            temp_path = "temp_image.jpg"
            cv2.imwrite(temp_path, image)

            # 모델 설정
            if model_choice == "SmartEyeSsen":
                imgsz = 1024
                conf = 0.25
            elif model_choice == "docsynth300k":
                imgsz = 1600
                conf = 0.15
            else:  # doclaynet_docsynth
                imgsz = 1024
                conf = 0.25

            # 분석 실행
            results = self.model.predict(
                temp_path,
                imgsz=imgsz,
                conf=conf,
                iou=0.45,
                device=self.device
            )

            # 결과 추출
            boxes = results[0].boxes.xyxy.cpu().numpy()
            classes = results[0].boxes.cls.cpu().numpy()
            confs = results[0].boxes.conf.cpu().numpy()
            class_names = self.model.names

            layout_info = []
            for i, (box, cls, conf) in enumerate(zip(boxes, classes, confs)):
                x1, y1, x2, y2 = map(int, box)
                cls_id = int(cls)

                try:
                    cls_name = class_names[cls_id]
                except IndexError:
                    cls_name = f"unknown_{cls_id}"

                area = (x2 - x1) * (y2 - y1)
                if area < 100:  # 너무 작은 영역 제외
                    continue

                layout_info.append({
                    'id': i,
                    'class_name': cls_name,
                    'confidence': float(conf),
                    'box': [int(x1), int(y1), int(x2), int(y2)],
                    'width': int(x2 - x1),
                    'height': int(y2 - y1),
                    'area': area
                })

            self.layout_info = layout_info
            logger.info(f"레이아웃 분석 완료: {len(layout_info)}개 영역 감지")
            return layout_info

        except Exception as e:
            logger.error(f"레이아웃 분석 실패: {e}")
            return []

    def perform_ocr(self, image):
        """OCR 처리"""
        target_classes = [
            'title', 'plain text', 'abandon text',
            'table caption', 'table footnote',
            'isolated formula', 'formula caption', 'question type',
            'question text', 'question number'
        ]

        ocr_results = []
        custom_config = r'--oem 3 --psm 6'

        logger.info("OCR 처리 시작...")

        for layout in self.layout_info:
            cls_name = layout['class_name'].lower()
            if cls_name not in target_classes:
                continue

            x1, y1, x2, y2 = layout['box']
            x1 = max(0, x1)
            y1 = max(0, y1)
            x2 = min(image.shape[1], x2)
            y2 = min(image.shape[0], y2)

            cropped_img = image[y1:y2, x1:x2]

            try:
                pil_img = Image.fromarray(cropped_img)
                text = pytesseract.image_to_string(
                    pil_img,
                    lang='kor+eng',
                    config=custom_config
                ).strip()

                if len(text) > 1:
                    ocr_results.append({
                        'id': layout['id'],
                        'class_name': cls_name,
                        'coordinates': [x1, y1, x2, y2],
                        'text': text
                    })
                    logger.info(f"OCR 완료: ID {layout['id']} - {len(text)}자")

            except Exception as e:
                logger.error(f"OCR 실패: ID {layout['id']} - {e}")

        self.ocr_results = ocr_results
        logger.info(f"OCR 처리 완료: {len(ocr_results)}개 텍스트 블록")
        return ocr_results

    def call_openai_api(self, image, api_key):
        """OpenAI Vision API 호출"""
        if not api_key:
            logger.warning("API 키가 제공되지 않아 AI 설명을 건너뜁니다.")
            return []

        target_classes = ['figure', 'table']
        api_results = []

        try:
            client = openai.OpenAI(api_key=api_key)
            logger.info("OpenAI API 처리 시작...")
        except Exception as e:
            logger.error(f"OpenAI 클라이언트 초기화 실패: {e}")
            return []

        prompts = {
            'figure': "이 그림(figure)의 내용을 간단히 요약해 주세요.",
            'table': "이 표(table)의 주요 내용을 요약해 주세요."
        }

        system_prompt = """당신은 시각 장애 아동을 위한 학습 AI 비서입니다.
시각 자료의 내용을 한국어로 간결하고 명확하게 설명해주세요.
설명은 음성으로 변환될 수 있도록 직접적이고 이해하기 쉽게 작성해주세요."""

        for layout in self.layout_info:
            cls_name = layout['class_name'].lower()
            if cls_name not in target_classes:
                continue

            x1, y1, x2, y2 = layout['box']
            cropped_img = image[y1:y2, x1:x2]

            # 이미지를 base64로 인코딩
            pil_img = Image.fromarray(cv2.cvtColor(cropped_img, cv2.COLOR_BGR2RGB))
            buffered = io.BytesIO()
            pil_img.save(buffered, format="PNG")
            img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")

            prompt = prompts.get(cls_name, f"이 {cls_name}의 내용을 간단히 설명해 주세요.")

            try:
                response = client.chat.completions.create(
                    model="gpt-4-turbo",
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {
                            "role": "user",
                            "content": [
                                {"type": "text", "text": prompt},
                                {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{img_base64}"}}
                            ]
                        }
                    ],
                    temperature=0.2,
                    max_tokens=600
                )

                description = response.choices[0].message.content.strip()

                api_results.append({
                    'id': layout['id'],
                    'class_name': cls_name,
                    'coordinates': [x1, y1, x2, y2],
                    'description': description
                })

                logger.info(f"API 응답 완료: ID {layout['id']} - {cls_name}")

            except Exception as e:
                logger.error(f"API 요청 실패: ID {layout['id']} - {e}")

        self.api_results = api_results
        logger.info(f"OpenAI API 처리 완료: {len(api_results)}개 설명 생성")
        return api_results

    def visualize_results(self, image):
        """결과 시각화"""
        img_result = image.copy()
        overlay = image.copy()

        # 클래스별 색상 생성
        random.seed(42)

        unique_classes = list(set(layout['class_name'] for layout in self.layout_info))
        class_colors = {}

        for i, cls_name in enumerate(unique_classes):
            h = i / max(1, len(unique_classes))
            s = 0.8
            v = 0.9
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            class_colors[cls_name] = (int(b * 255), int(g * 255), int(r * 255))

        # 바운딩 박스 그리기
        for layout in self.layout_info:
            x1, y1, x2, y2 = layout['box']
            cls_name = layout['class_name']
            color = class_colors[cls_name]

            # 반투명 오버레이
            cv2.rectangle(overlay, (x1, y1), (x2, y2), color, -1)

            # 테두리
            cv2.rectangle(img_result, (x1, y1), (x2, y2), color, 2)

            # 라벨
            label = f"{cls_name} ({layout['confidence']:.2f})"
            labelSize, _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            y1_label = max(y1, labelSize[1] + 10)

            cv2.rectangle(
                img_result,
                (x1, y1_label - labelSize[1] - 10),
                (x1 + labelSize[0], y1_label),
                color,
                -1
            )

            cv2.putText(
                img_result,
                label,
                (x1, y1_label - 5),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 255, 255),
                1
            )

        # 반투명 적용
        img_result = cv2.addWeighted(overlay, 0.2, img_result, 0.8, 0)

        return cv2.cvtColor(img_result, cv2.COLOR_BGR2RGB)

    def create_text_visualization(self, image):
        """텍스트가 삽입된 문서 시각화"""
        canvas_height, canvas_width = image.shape[:2]
        canvas = np.ones((canvas_height, canvas_width, 3), dtype=np.uint8) * 255

        # OCR 및 API 바운딩 박스 그리기
        for result in self.ocr_results + self.api_results:
            x1, y1, x2, y2 = result['coordinates']
            cv2.rectangle(canvas, (x1, y1), (x2, y2), (0, 0, 0), 2)

        # PIL로 변환하여 한글 텍스트 추가
        canvas_pil = Image.fromarray(cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB))
        draw = ImageDraw.Draw(canvas_pil)

        # 기본 폰트 사용 (Windows에서)
        try:
            font = ImageDraw.getfont()
        except:
            from PIL import ImageFont
            try:
                font = ImageFont.load_default()
            except:
                font = None

        # 텍스트 추가
        for result in self.ocr_results:
            x1, y1, x2, y2 = result['coordinates']
            text = result['text'].replace('\n', ' ')
            if len(text) > 50:
                text = text[:50] + "..."
            if font:
                draw.text((x1 + 5, y1 + 5), text, font=font, fill=(0, 0, 0))

        for result in self.api_results:
            x1, y1, x2, y2 = result['coordinates']
            text = result['description'].replace('\n', ' ')
            if len(text) > 50:
                text = text[:50] + "..."
            if font:
                draw.text((x1 + 5, y1 + 5), text, font=font, fill=(0, 0, 0))

        return np.array(canvas_pil)

    def create_cim_result(self, layout_info, ocr_results, ai_results):
        """CIM 결과 생성 (시각화 제거, JSON 통합만)"""
        from datetime import datetime
        
        # JSON 통합 결과 생성
        cim_result = {
            "document_structure": {
                "layout_analysis": {
                    "total_elements": len(layout_info),
                    "elements": []
                },
                "text_content": [],
                "ai_descriptions": []
            },
            "metadata": {
                "analysis_date": datetime.now().isoformat(),
                "total_text_regions": len([info for info in layout_info if 'text' in info.get('class_name', '').lower()]),
                "total_figures": len([info for info in layout_info if info.get('class_name') == 'figure']),
                "total_tables": len([info for info in layout_info if info.get('class_name') == 'table'])
            }
        }
        
        # 레이아웃 정보 통합
        for i, info in enumerate(layout_info):
            element = {
                "id": i,
                "class": info.get('class_name', 'unknown'),
                "confidence": float(info.get('confidence', 0.0)),
                "bbox": info.get('box', []),
                "area": info.get('area', 0)
            }
            
            # OCR 텍스트가 있는 경우 추가
            ocr_text = None
            for ocr_result in ocr_results:
                if ocr_result.get('id') == info.get('id'):
                    ocr_text = ocr_result.get('text', '')
                    break
            
            if ocr_text and ocr_text.strip():
                element["text"] = ocr_text
                cim_result["document_structure"]["text_content"].append({
                    "element_id": i,
                    "text": ocr_text,
                    "class": info.get('class_name', 'unknown')
                })
            
            # AI 설명이 있는 경우 추가
            ai_description = None
            for ai_result in ai_results:
                if ai_result.get('id') == info.get('id'):
                    ai_description = ai_result.get('description', '')
                    break
            
            if ai_description and ai_description.strip():
                element["ai_description"] = ai_description
                cim_result["document_structure"]["ai_descriptions"].append({
                    "element_id": i,
                    "description": ai_description,
                    "class": info.get('class_name', 'unknown')
                })
            
            cim_result["document_structure"]["layout_analysis"]["elements"].append(element)
        
        # 통계 계산
        text_elements = [e for e in cim_result["document_structure"]["layout_analysis"]["elements"] if "text" in e]
        ai_elements = [e for e in cim_result["document_structure"]["layout_analysis"]["elements"] if "ai_description" in e]
        
        stats = {
            "total_elements": len(layout_info),
            "text_elements": len(text_elements),
            "ai_described_elements": len(ai_elements),
            "class_distribution": {}
        }
        
        # 클래스별 분포 계산
        for info in layout_info:
            class_name = info.get('class_name', 'unknown')
            stats["class_distribution"][class_name] = stats["class_distribution"].get(class_name, 0) + 1
        
        return cim_result, stats


# 글로벌 분석기 인스턴스
analyzer = WorksheetAnalyzer()


@app.post("/analyze")
async def analyze_worksheet(
    image: UploadFile = File(...),
    model_choice: str = Form("SmartEyeSsen"),
    api_key: Optional[str] = Form(None)
):
    """
    학습지 분석 메인 엔드포인트
    """
    try:
        # 이미지 읽기
        image_bytes = await image.read()
        pil_image = Image.open(io.BytesIO(image_bytes))
        
        # PIL 이미지를 OpenCV BGR 형태로 변환
        cv_image = cv2.cvtColor(np.array(pil_image), cv2.COLOR_RGB2BGR)
        
        # 모델 다운로드 및 로드
        logger.info(f"모델 선택: {model_choice}")
        model_path = analyzer.download_model(model_choice)
        
        if not analyzer.load_model(model_path):
            raise HTTPException(status_code=500, detail="모델 로드에 실패했습니다.")
        
        # 레이아웃 분석
        layout_info = analyzer.analyze_layout(cv_image, model_choice)
        if not layout_info:
            raise HTTPException(status_code=400, detail="레이아웃 분석에 실패했습니다. 감지된 요소가 없습니다.")
        
        # OCR 처리
        analyzer.perform_ocr(cv_image)
        
        # OpenAI API 처리 (API 키가 있는 경우)
        if api_key and api_key.strip():
            analyzer.call_openai_api(cv_image, api_key)
        else:
            analyzer.api_results = []
        
        # 레이아웃 결과 시각화
        layout_viz = analyzer.visualize_results(cv_image)
        
        # 레이아웃 결과 이미지를 파일로 저장
        timestamp = int(time.time())
        layout_viz_path = f"static/layout_viz_{timestamp}.png"
        
        layout_viz_pil = Image.fromarray(layout_viz)
        layout_viz_pil.save(layout_viz_path)
        
        # CIM 통합 결과 생성 (JSON 데이터만)
        cim_result, cim_stats = analyzer.create_cim_result(
            analyzer.layout_info, 
            analyzer.ocr_results, 
            analyzer.api_results
        )
        
        # JSON 파일 저장
        from datetime import datetime
        json_filename = f"analysis_result_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        json_filepath = f"static/{json_filename}"
        
        with open(json_filepath, 'w', encoding='utf-8') as f:
            json.dump(cim_result, f, indent=2, ensure_ascii=False)
        
        # 통계 생성
        class_counts = Counter(item['class_name'] for item in layout_info)
        stats = {
            "total_layout_elements": len(layout_info),
            "ocr_text_blocks": len(analyzer.ocr_results),
            "ai_descriptions": len(analyzer.api_results),
            "class_counts": dict(class_counts),
            "cim_stats": cim_stats
        }
        
        # OCR 텍스트 통합 (편집 가능한 형태로)
        combined_ocr_text = ""
        for result in analyzer.ocr_results:
            combined_ocr_text += f"[{result['class_name']}]\n{result['text']}\n\n"
        
        # AI 설명 통합
        combined_ai_text = ""
        for result in analyzer.api_results:
            combined_ai_text += f"[{result['class_name']}]\n{result['description']}\n\n"
        
        return JSONResponse({
            "success": True,
            "layout_image_url": f"/{layout_viz_path}",
            "json_url": f"/{json_filepath}",
            "stats": stats,
            "ocr_results": analyzer.ocr_results,
            "ai_results": analyzer.api_results,
            "ocr_text": combined_ocr_text.strip(),
            "ai_text": combined_ai_text.strip(),
            "timestamp": timestamp
        })
        
    except Exception as e:
        logger.error(f"분석 중 오류 발생: {e}")
        raise HTTPException(status_code=500, detail=f"분석 중 오류가 발생했습니다: {str(e)}")


@app.get("/")
async def root():
    """API 상태 확인"""
    return {
        "message": "SmartEyeSsen 학습지 분석 API",
        "version": "1.0.0",
        "device": device,
        "available_models": [
            "SmartEyeSsen",
            "docstructbench", 
            "doclaynet_docsynth",
            "docsynth300k"
        ]
    }


@app.get("/health")
async def health_check():
    """헬스 체크 엔드포인트"""
    return {"status": "healthy", "device": device}


if __name__ == "__main__":
    print("🚀 SmartEyeSsen API 서버를 시작합니다...")
    print(f"📱 브라우저에서 http://localhost:8000 으로 접속하세요")
    print(f"📚 API 문서는 http://localhost:8000/docs 에서 확인할 수 있습니다")
    
    uvicorn.run(
        "api_server:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info"
    )
