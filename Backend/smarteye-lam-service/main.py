# -*- coding: utf-8 -*-
"""
SmartEye LAM (Layout Analysis Module) 마이크로서비스
레이아웃 분석 전용 독립 서비스
"""

import os
import sys
import time
import tempfile
import traceback
from typing import Dict, List, Optional

# FastAPI 관련
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

# AI/ML 관련
import torch
from huggingface_hub import hf_hub_download

# 로깅
from loguru import logger

# 구조화된 분석 기능은 Java 백엔드에서 처리

# 로그 설정
logger.remove()
logger.add(sys.stderr, level="INFO", format="{time} | {level} | {module}:{function}:{line} - {message}")

# 디바이스 설정
device = 'cuda' if torch.cuda.is_available() else 'cpu'

print("🚀 SmartEye LAM 마이크로서비스를 시작합니다...")
print("📱 브라우저에서 http://localhost:8001 으로 접속하세요")
print("📚 API 문서는 http://localhost:8001/docs 에서 확인할 수 있습니다")
print(f"🖥️ 디바이스: {device}")

# FastAPI 앱 생성
app = FastAPI(
    title="SmartEye LAM Service",
    description="Layout Analysis Module - 레이아웃 분석 마이크로서비스",
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
    def __init__(self):
        self.models_cache = {}
        self.device = device
        self.model = None
        
        # 모델 설정
        self.model_configs = {
            "SmartEyeSsen": {"imgsz": 1024, "conf": 0.25, "description": "SmartEye 전용 모델"},
            "docsynth300k": {"imgsz": 1600, "conf": 0.20, "description": "DocLayout-YOLO DocSynth300K"},
            "doclaynet_docsynth": {"imgsz": 1024, "conf": 0.25, "description": "DocLayout-YOLO DocLayNet"},
            "docstructbench": {"imgsz": 1024, "conf": 0.25, "description": "DocLayout-YOLO DocStructBench"}
        }

    def download_model(self, model_choice="SmartEyeSsen"):
        """HuggingFace Hub에서 모델 다운로드"""
        models = {
            "SmartEyeSsen": {
                "repo_id": "AkJeond/SmartEyeSsen",
                "filename": "best_tuned_model.pt"
            },
            "doclaynet_docsynth": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
                "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt"
            },
            "docsynth300k": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
                "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt"
            },
            "docstructbench": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
                "filename": "doclayout_yolo_docstructbench_imgsz1024.pt"
            }
        }
        
        selected_model = models.get(model_choice)
        if not selected_model:
            logger.error(f"지원하지 않는 모델: {model_choice}")
            return None
            
        try:
            logger.info(f"모델 다운로드 중: {selected_model['repo_id']}")
            
            model_path = hf_hub_download(
                repo_id=selected_model["repo_id"],
                filename=selected_model["filename"],
                cache_dir="./models",
                force_download=False,
                resume_download=True
            )
            
            logger.info(f"모델 다운로드 완료: {model_path}")
            return model_path
            
        except Exception as e:
            logger.error(f"모델 다운로드 실패 - {model_choice}: {str(e)}")
            return None

    def load_model(self, model_choice="SmartEyeSsen"):
        """모델 로드 (강화된 호환성 및 폴백)"""
        try:
            # 캐시된 모델 확인
            if model_choice in self.models_cache:
                cached_data = self.models_cache[model_choice]
                if isinstance(cached_data, dict):
                    self.model = cached_data["model"]
                    model_type = cached_data.get("type", "Unknown")
                else:
                    # 이전 버전 호환성
                    self.model = cached_data
                    model_type = "Legacy"
                logger.info(f"캐시된 모델 사용: {model_choice} ({model_type})")
                return True

            # 모델 다운로드
            model_path = self.download_model(model_choice)
            if not model_path:
                logger.error(f"모델 다운로드 실패: {model_choice}")
                return False

            logger.info(f"모델 로드 중: {model_choice}")
            
            # 1차 시도: DocLayout-YOLO
            model = None
            model_type = "Unknown"
            
            try:
                from doclayout_yolo import YOLOv10
                model = YOLOv10(model_path)
                model_type = "DocLayout-YOLO"
                logger.info(f"✅ DocLayout-YOLO 모델 로드 성공: {model_choice}")
                
                # 기본 테스트 실행으로 호환성 확인
                import tempfile
                import numpy as np
                from PIL import Image
                
                # 더미 이미지로 테스트
                test_img = Image.fromarray(np.zeros((640, 640, 3), dtype=np.uint8))
                with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tmp:
                    test_img.save(tmp.name)
                    test_results = model.predict(tmp.name, verbose=False, save=False)
                    import os
                    os.unlink(tmp.name)
                
                logger.info(f"✅ DocLayout-YOLO 호환성 테스트 통과: {model_choice}")
                
            except Exception as e:
                logger.warning(f"DocLayout-YOLO 실패: {e}")
                model = None
                
                # 2차 시도: Ultralytics YOLO 폴백
                try:
                    from ultralytics import YOLO
                    model = YOLO(model_path)
                    model_type = "Ultralytics-YOLO"
                    logger.info(f"⚠️ Ultralytics YOLO로 폴백: {model_choice}")
                    
                except Exception as e2:
                    logger.error(f"❌ Ultralytics YOLO도 실패: {e2}")
                    return False
            
            if model is None:
                logger.error(f"❌ 모든 YOLO 로드 시도 실패: {model_choice}")
                return False
            
            # 모델 설정 (추론 모드만)
            if hasattr(model, 'to'):
                model.to(self.device)
            # eval() 호출 시 훈련 관련 설정이 로드되어 오류 발생하므로 생략
            
            # 캐시에 저장 (모델 타입 정보도 함께)
            self.models_cache[model_choice] = {"model": model, "type": model_type}
            self.model = model
            
            logger.info(f"✅ 모델 로드 및 캐시 완료: {model_choice} ({model_type})")
            return True
            
        except Exception as e:
            logger.error(f"모델 로드 실패: {e}")
            logger.error(f"상세 오류: {traceback.format_exc()}")
            return False

    def analyze_layout(self, image_path: str, model_choice: str = "SmartEyeSsen"):
        """레이아웃 분석 수행"""
        try:
            # 모델 로드
            if not self.load_model(model_choice):
                return None
            
            # 모델 설정 가져오기
            config = self.model_configs.get(model_choice, self.model_configs["SmartEyeSsen"])
            
            logger.info(f"분석 시작 - 이미지: {image_path}, 모델: {model_choice}")
            logger.info(f"설정 - imgsz: {config['imgsz']}, conf: {config['conf']}")
            
            # 예측 수행
            results = self.model.predict(
                image_path,
                imgsz=config["imgsz"],
                conf=config["conf"],
                iou=0.45,
                device=self.device,
                verbose=False,
                save=False
            )
            
            # 결과 처리
            if not results:
                logger.warning("예측 결과가 없습니다.")
                return []
            
            result = results[0]
            
            # 바운딩 박스 정보 추출
            layout_info = []
            
            if hasattr(result, 'boxes') and result.boxes is not None:
                boxes = result.boxes.xyxy.cpu().numpy()
                scores = result.boxes.conf.cpu().numpy()
                classes = result.boxes.cls.cpu().numpy()
                
                # 클래스 이름 매핑
                class_names = getattr(result, 'names', {})
                
                logger.info(f"감지된 객체 수: {len(boxes)}")
                logger.info(f"클래스 분포: {dict(zip(*torch.unique(result.boxes.cls, return_counts=True)))}")
                
                for i, (box, score, cls_id) in enumerate(zip(boxes, scores, classes)):
                    x1, y1, x2, y2 = box
                    class_name = class_names.get(int(cls_id), f"class_{int(cls_id)}")
                    
                    layout_info.append({
                        "class": class_name,
                        "class_id": int(cls_id),
                        "confidence": float(score),
                        "bbox": {
                            "x1": float(x1),
                            "y1": float(y1),
                            "x2": float(x2),
                            "y2": float(y2)
                        }
                    })
            
            logger.info(f"분석 완료 - 총 {len(layout_info)}개 요소 감지")
            return layout_info
            
        except Exception as e:
            logger.error(f"레이아웃 분석 실패: {e}")
            logger.error(f"상세 오류: {traceback.format_exc()}")
            return None

# 글로벌 분석기 인스턴스
analyzer = LAMAnalyzer()

@app.get("/")
async def root():
    """루트 엔드포인트"""
    return {"message": "SmartEye LAM Service", "status": "running", "device": device}

@app.get("/health")
async def health_check():
    """헬스체크 엔드포인트"""
    return {"status": "healthy", "device": device, "cached_models": list(analyzer.models_cache.keys())}

@app.post("/analyze-layout")
async def analyze_layout(
    image: UploadFile = File(...),
    model_choice: str = Form("SmartEyeSsen")
):
    """레이아웃 분석 메인 엔드포인트"""
    start_time = time.time()
    
    try:
        logger.info(f"레이아웃 분석 요청 - 파일: {image.filename}, 모델: {model_choice}")
        
        # 지원 모델 확인
        if model_choice not in analyzer.model_configs:
            raise HTTPException(
                status_code=400, 
                detail=f"지원하지 않는 모델: {model_choice}. 지원 모델: {list(analyzer.model_configs.keys())}"
            )
        
        # 임시 파일로 저장
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as temp_file:
            content = await image.read()
            temp_file.write(content)
            temp_file_path = temp_file.name
        
        try:
            # 레이아웃 분석 수행
            layout_results = analyzer.analyze_layout(temp_file_path, model_choice)
            
            if layout_results is None:
                raise HTTPException(status_code=500, detail="모델 로드에 실패했습니다.")
            
            processing_time = time.time() - start_time
            
            response = {
                "success": True,
                "processing_time": round(processing_time, 2),
                "model_used": model_choice,
                "device": device,
                "results": {
                    "layout_analysis": layout_results,
                    "total_elements": len(layout_results)
                }
            }
            
            logger.info(f"분석 완료 - 처리시간: {processing_time:.2f}초, 요소 수: {len(layout_results)}")
            return JSONResponse(content=response)
            
        finally:
            # 임시 파일 정리
            os.unlink(temp_file_path)
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"예상치 못한 오류: {e}")
        logger.error(f"상세 오류: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")

# /analyze-structured 엔드포인트는 제거됨 - 구조화 분석은 Java 백엔드에서 처리

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001, reload=True)
