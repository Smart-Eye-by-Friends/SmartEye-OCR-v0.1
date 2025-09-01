#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmartEye LAM (Layout Analysis Module) 마이크로서비스
Python api_server.py의 analyze_layout() 메서드를 독립 서비스로 분리
"""

import os
import sys
import cv2
import io
import time
import tempfile
import numpy as np
from typing import List, Dict, Any, Optional
from PIL import Image
import torch
from pathlib import Path

# FastAPI 관련
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# 로깅
from loguru import logger

# Hugging Face Hub
from huggingface_hub import hf_hub_download

# 환경 설정
os.environ["CUDA_VISIBLE_DEVICES"] = "0" if torch.cuda.is_available() else ""
device = 'cuda:0' if torch.cuda.is_available() else 'cpu'

logger.remove()
logger.add(sys.stderr, level="INFO")

# FastAPI 앱 생성
app = FastAPI(
    title="SmartEye LAM Service",
    description="Layout Analysis Module - DocLayout-YOLO 기반 문서 레이아웃 분석",
    version="1.0.0"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class LAMAnalyzer:
    """레이아웃 분석기 - Python api_server.py의 WorksheetAnalyzer에서 LAM 관련 부분만 추출"""
    
    def __init__(self):
        self.model = None
        self.device = device
        self.models_cache = {}
        
    def download_model(self, model_choice: str = "SmartEyeSsen") -> str:
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
                filename=selected_model["filename"],
                cache_dir="./models"
            )
            logger.info(f"모델 다운로드 완료: {filepath}")
            return filepath
            
        except Exception as e:
            logger.error(f"모델 다운로드 실패: {e}")
            raise HTTPException(status_code=500, detail=f"모델 다운로드 실패: {str(e)}")
    
    def load_model(self, model_path: str, model_choice: str) -> bool:
        """DocLayout-YOLO 모델 로드"""
        try:
            # 캐시된 모델이 있으면 재사용
            if model_choice in self.models_cache:
                self.model = self.models_cache[model_choice]
                logger.info(f"캐시된 모델 사용: {model_choice}")
                return True
            
            # DocLayout-YOLO 임포트
            try:
                from doclayout_yolo import YOLOv10
            except ImportError:
                logger.error("DocLayout-YOLO가 설치되지 않았습니다.")
                # Ultralytics YOLO로 대체 시도
                try:
                    from ultralytics import YOLO as YOLOv10
                    logger.info("Ultralytics YOLO 사용")
                except ImportError:
                    raise HTTPException(status_code=500, detail="YOLO 라이브러리를 찾을 수 없습니다.")
            
            logger.info("모델 로드 중...")
            self.model = YOLOv10(model_path)
            
            # GPU 사용 가능하면 GPU로 이동
            if torch.cuda.is_available():
                self.model.to(self.device)
            
            # 모델 캐싱
            self.models_cache[model_choice] = self.model
            
            logger.info(f"모델 로드 완료! (디바이스: {self.device})")
            return True
            
        except Exception as e:
            logger.error(f"모델 로드 실패: {e}")
            return False
    
    def analyze_layout(self, image_path: str, model_choice: str = "SmartEyeSsen") -> List[Dict[str, Any]]:
        """레이아웃 분석 - Python api_server.py의 analyze_layout과 동일"""
        try:
            logger.info(f"레이아웃 분석 시작 - 모델: {model_choice}")
            
            # 모델 설정
            model_configs = {
                "SmartEyeSsen": {"imgsz": 1024, "conf": 0.25},
                "docsynth300k": {"imgsz": 1600, "conf": 0.15},
                "doclaynet_docsynth": {"imgsz": 1024, "conf": 0.25},
                "docstructbench": {"imgsz": 1024, "conf": 0.25}
            }
            
            config = model_configs.get(model_choice, model_configs["SmartEyeSsen"])
            
            # 분석 실행
            results = self.model.predict(
                image_path,
                imgsz=config["imgsz"],
                conf=config["conf"],
                iou=0.45,
                device=self.device,
                verbose=False
            )
            
            # 결과 추출
            if not results or len(results) == 0:
                logger.warning("분석 결과가 없습니다.")
                return []
            
            result = results[0]
            
            if not hasattr(result, 'boxes') or result.boxes is None:
                logger.warning("바운딩 박스 결과가 없습니다.")
                return []
            
            boxes = result.boxes.xyxy.cpu().numpy()
            classes = result.boxes.cls.cpu().numpy() 
            confs = result.boxes.conf.cpu().numpy()
            class_names = self.model.names
            
            layout_info = []
            for i, (box, cls, conf) in enumerate(zip(boxes, classes, confs)):
                x1, y1, x2, y2 = map(int, box)
                cls_id = int(cls)
                
                try:
                    cls_name = class_names[cls_id]
                except (IndexError, KeyError):
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
            
            logger.info(f"레이아웃 분석 완료: {len(layout_info)}개 영역 감지")
            return layout_info
            
        except Exception as e:
            logger.error(f"레이아웃 분석 실패: {e}")
            raise HTTPException(status_code=500, detail=f"레이아웃 분석 실패: {str(e)}")

# 글로벌 분석기 인스턴스
analyzer = LAMAnalyzer()

@app.get("/")
async def root():
    """API 상태 확인"""
    return {
        "service": "SmartEye LAM Service",
        "version": "1.0.0",
        "device": device,
        "status": "running",
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
    return {
        "status": "healthy", 
        "device": device,
        "torch_version": torch.__version__,
        "cuda_available": torch.cuda.is_available()
    }

@app.post("/analyze-layout")
async def analyze_layout(
    image: UploadFile = File(...),
    model_choice: str = Form("SmartEyeSsen")
):
    """레이아웃 분석 메인 엔드포인트"""
    start_time = time.time()
    
    try:
        logger.info(f"레이아웃 분석 요청 - 파일: {image.filename}, 모델: {model_choice}")
        
        # 이미지 검증
        if not image.content_type or not image.content_type.startswith('image/'):
            raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")
        
        # 임시 파일로 저장
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as temp_file:
            content = await image.read()
            temp_file.write(content)
            temp_file_path = temp_file.name
        
        try:
            # 모델 다운로드 및 로드
            model_path = analyzer.download_model(model_choice)
            if not analyzer.load_model(model_path, model_choice):
                raise HTTPException(status_code=500, detail="모델 로드에 실패했습니다.")
            
            # 레이아웃 분석 실행
            layout_info = analyzer.analyze_layout(temp_file_path, model_choice)
            
            processing_time = time.time() - start_time
            
            response = {
                "success": True,
                "layout_info": layout_info,
                "stats": {
                    "total_elements": len(layout_info),
                    "processing_time": round(processing_time, 2),
                    "model_used": model_choice,
                    "device": device
                },
                "message": f"레이아웃 분석 완료 - {len(layout_info)}개 요소 감지"
            }
            
            logger.info(f"레이아웃 분석 성공 - 처리시간: {processing_time:.2f}초")
            return JSONResponse(content=response)
            
        finally:
            # 임시 파일 정리
            try:
                os.unlink(temp_file_path)
            except Exception:
                pass
                
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"레이아웃 분석 중 예상치 못한 오류: {e}")
        raise HTTPException(status_code=500, detail=f"분석 중 오류가 발생했습니다: {str(e)}")

if __name__ == "__main__":
    print("🚀 SmartEye LAM 마이크로서비스를 시작합니다...")
    print(f"📱 브라우저에서 http://localhost:8001 으로 접속하세요")
    print(f"📚 API 문서는 http://localhost:8001/docs 에서 확인할 수 있습니다")
    print(f"🖥️ 디바이스: {device}")
    
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=False,  # 프로덕션에서는 False
        log_level="info"
    )