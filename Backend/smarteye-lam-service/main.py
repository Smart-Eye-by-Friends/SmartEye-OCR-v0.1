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

# CORS 설정 - 보안 강화
import os
allowed_origins = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "X-Requested-With"],
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

# Level 3: LAM 서비스 메모리 최적화 버전
class MemoryOptimizedLAMAnalyzer(LAMAnalyzer):
    """
    메모리 최적화된 LAM 분석기
    - 모델 언로드 기능
    - CUDA 메모리 자동 정리
    - 배치 처리 지원
    """
    
    def __init__(self):
        super().__init__()
        self.memory_stats = {
            "peak_memory_mb": 0,
            "current_memory_mb": 0,
            "model_loads": 0,
            "model_unloads": 0,
            "cache_hits": 0,
            "gc_calls": 0
        }
        
    def get_memory_usage(self):
        """현재 메모리 사용량 반환 (MB 단위)"""
        import psutil
        import gc
        
        process = psutil.Process()
        memory_mb = process.memory_info().rss / 1024 / 1024
        
        # CUDA 메모리 확인 (가능한 경우)
        cuda_memory_mb = 0
        if torch.cuda.is_available():
            cuda_memory_mb = torch.cuda.memory_allocated() / 1024 / 1024
            
        total_memory_mb = memory_mb + cuda_memory_mb
        
        # 통계 업데이트
        self.memory_stats["current_memory_mb"] = total_memory_mb
        if total_memory_mb > self.memory_stats["peak_memory_mb"]:
            self.memory_stats["peak_memory_mb"] = total_memory_mb
            
        return {
            "ram_mb": round(memory_mb, 2),
            "cuda_mb": round(cuda_memory_mb, 2),
            "total_mb": round(total_memory_mb, 2)
        }
    
    def cleanup_memory(self, force_gc=True):
        """메모리 정리 및 CUDA 캐시 해제"""
        logger.info("메모리 정리 시작...")
        
        # CUDA 메모리 정리
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            logger.info("CUDA 메모리 캐시 정리 완료")
        
        # Python 가비지 컨렉터
        if force_gc:
            import gc
            collected = gc.collect()
            self.memory_stats["gc_calls"] += 1
            logger.info(f"가비지 컨렉터 실행: {collected}개 객체 해제")
    
    def unload_model(self, model_choice):
        """모델 언로드 및 메모리 해제"""
        if model_choice in self.models_cache:
            logger.info(f"모델 언로드: {model_choice}")
            
            # 모델 참조 삭제
            del self.models_cache[model_choice]
            
            # 현재 모델이 언로드된 모델이면 None으로 설정
            if self.model is not None:
                self.model = None
                
            self.memory_stats["model_unloads"] += 1
            
            # 메모리 정리
            self.cleanup_memory()
            
            logger.info(f"모델 {model_choice} 언로드 완료")
            return True
        return False
    
    def load_model(self, model_choice="SmartEyeSsen"):
        """메모리 최적화된 모델 로드"""
        try:
            # 캐시 히트 확인
            if model_choice in self.models_cache:
                self.memory_stats["cache_hits"] += 1
                logger.info(f"캐시된 모델 사용: {model_choice}")
                
                cached_data = self.models_cache[model_choice]
                if isinstance(cached_data, dict):
                    self.model = cached_data["model"]
                else:
                    self.model = cached_data
                return True
            
            # 기존 모델이 다르면 언로드 (메모리 절약)
            if self.model is not None and len(self.models_cache) > 0:
                # 최대 2개 모델만 유지 (메모리 제한)
                if len(self.models_cache) >= 2:
                    # LRU: 가장 오래된 모델 언로드
                    oldest_model = next(iter(self.models_cache))
                    self.unload_model(oldest_model)
                    logger.info(f"LRU 정책으로 모델 언로드: {oldest_model}")
            
            # 메모리 사용량 로깅 (로드 전)
            memory_before = self.get_memory_usage()
            logger.info(f"모델 로드 전 메모리: {memory_before}")
            
            # 모델 다운로드
            model_path = self.download_model(model_choice)
            if not model_path:
                logger.error(f"모델 다운로드 실패: {model_choice}")
                return False
            
            # 모델 로드
            success = super().load_model(model_choice)
            if success:
                self.memory_stats["model_loads"] += 1
                
                # 메모리 사용량 로깅 (로드 후)
                memory_after = self.get_memory_usage()
                memory_diff = memory_after["total_mb"] - memory_before["total_mb"]
                logger.info(f"모델 로드 후 메모리: {memory_after} (+{memory_diff:.2f}MB)")
            
            return success
            
        except Exception as e:
            logger.error(f"메모리 최적화된 모델 로드 실패: {e}")
            return False
    
    def analyze_layout(self, image_path: str, model_choice: str = "SmartEyeSsen"):
        """메모리 최적화된 레이아웃 분석"""
        try:
            # 메모리 사용량 모니터링 시작
            memory_start = self.get_memory_usage()
            logger.info(f"분석 시작 메모리: {memory_start}")
            
            # 모델 로드
            if not self.load_model(model_choice):
                return None
            
            # 분석 수행
            results = super().analyze_layout(image_path, model_choice)
            
            # 메모리 사용량 모니터링 종료
            memory_end = self.get_memory_usage()
            memory_diff = memory_end["total_mb"] - memory_start["total_mb"]
            logger.info(f"분석 완룼 메모리: {memory_end} (+{memory_diff:.2f}MB)")
            
            # 임계값 초과 시 메모리 정리 (예: 4GB)
            if memory_end["total_mb"] > 4096:
                logger.warning(f"메모리 임계값 초과 ({memory_end['total_mb']:.2f}MB > 4096MB), 정리 시작")
                self.cleanup_memory(force_gc=True)
                
                memory_after_cleanup = self.get_memory_usage()
                logger.info(f"정리 후 메모리: {memory_after_cleanup}")
            
            return results
            
        except Exception as e:
            logger.error(f"메모리 최적화 분석 실패: {e}")
            # 오류 발생 시 메모리 정리
            self.cleanup_memory(force_gc=True)
            return None
    
    def get_memory_stats(self):
        """메모리 통계 반환"""
        current_memory = self.get_memory_usage()
        stats = self.memory_stats.copy()
        stats["current_memory"] = current_memory
        stats["cached_models"] = list(self.models_cache.keys())
        stats["cache_size"] = len(self.models_cache)
        return stats

# 메모리 최적화된 글로벌 분석기 인스턴스
analyzer = MemoryOptimizedLAMAnalyzer()

@app.get("/")
async def root():
    """루트 엔드포인트"""
    return {"message": "SmartEye LAM Service", "status": "running", "device": device}

@app.get("/health")
async def health_check():
    """헬스체크 엔드포인트 + 메모리 사용량 정보"""
    memory_info = analyzer.get_memory_usage()
    memory_stats = analyzer.get_memory_stats()
    
    return {
        "status": "healthy", 
        "device": device, 
        "cached_models": list(analyzer.models_cache.keys()),
        "memory_usage": memory_info,
        "memory_stats": memory_stats
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

# Level 3: 메모리 관리 엔드포인트들

@app.post("/memory/cleanup")
async def cleanup_memory():
    """다이렉트 메모리 정리 요청"""
    try:
        memory_before = analyzer.get_memory_usage()
        analyzer.cleanup_memory(force_gc=True)
        memory_after = analyzer.get_memory_usage()
        
        memory_freed = memory_before["total_mb"] - memory_after["total_mb"]
        
        return {
            "success": True,
            "memory_before": memory_before,
            "memory_after": memory_after,
            "memory_freed_mb": round(memory_freed, 2),
            "message": f"{memory_freed:.2f}MB 메모리 해제"
        }
    except Exception as e:
        logger.error(f"메모리 정리 실패: {e}")
        return {"success": False, "error": str(e)}

@app.post("/memory/unload-model")
async def unload_model(model_name: str):
    """특정 모델 언로드 요청"""
    try:
        memory_before = analyzer.get_memory_usage()
        success = analyzer.unload_model(model_name)
        
        if success:
            memory_after = analyzer.get_memory_usage()
            memory_freed = memory_before["total_mb"] - memory_after["total_mb"]
            
            return {
                "success": True,
                "model_name": model_name,
                "memory_before": memory_before,
                "memory_after": memory_after,
                "memory_freed_mb": round(memory_freed, 2),
                "message": f"모델 {model_name} 언로드 완료"
            }
        else:
            return {
                "success": False,
                "model_name": model_name,
                "message": f"모델 {model_name}을(를) 찾을 수 없습니다"
            }
            
    except Exception as e:
        logger.error(f"모델 언로드 실패: {e}")
        return {"success": False, "error": str(e)}

@app.get("/memory/stats")
async def get_memory_stats():
    """상세 메모리 통계 조회"""
    try:
        return {
            "success": True,
            "stats": analyzer.get_memory_stats(),
            "device_info": {
                "device": device,
                "cuda_available": torch.cuda.is_available(),
                "cuda_device_count": torch.cuda.device_count() if torch.cuda.is_available() else 0
            }
        }
    except Exception as e:
        logger.error(f"메모리 통계 조회 실패: {e}")
        return {"success": False, "error": str(e)}

# /analyze-structured 엔드포인트는 제거됨 - 구조화 분석은 Java 백엔드에서 처리

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001, reload=True)
