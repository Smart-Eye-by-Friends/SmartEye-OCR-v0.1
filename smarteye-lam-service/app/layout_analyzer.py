"""
레이아웃 분석기
DocLayout-YOLO 모델을 사용한 문서 레이아웃 분석
"""

import cv2
import numpy as np
import time
import os
import asyncio
from pathlib import Path
from typing import Dict, List, Any, Optional
import logging

from .model_manager import ModelManager

# 로깅 설정
logger = logging.getLogger(__name__)

class LayoutAnalyzer:
    """문서 레이아웃 분석기"""
    
    def __init__(self, settings):
        self.settings = settings
        self.model = None
        self.model_loaded = False
        self.model_version = "DocLayout-YOLO-v1.0"
        self.gpu_available = False
        self.model_manager = ModelManager(settings)
        
        # 초기화 시간 기록
        self.initialized_at = None
        
    async def initialize(self):
        """모델 초기화"""
        try:
            logger.info("DocLayout-YOLO 모델 초기화 시작...")
            
            # GPU 사용 가능성 확인
            self.gpu_available = self._check_gpu_availability()
            
            # DocLayout-YOLO GitHub 리포지토리 확인 및 설치
            logger.info("DocLayout-YOLO 설치 확인 중...")
            doclayout_path = self.model_manager.ensure_doclayout_yolo()
            logger.info(f"DocLayout-YOLO 설치 경로: {doclayout_path}")
            
            # 모델 다운로드 (허깅페이스에서)
            logger.info("허깅페이스에서 모델 다운로드 중...")
            model_path = self.settings.download_model()
            logger.info(f"모델 다운로드 완료: {model_path}")
            
            # 실제 환경에서는 여기서 DocLayout-YOLO 모델을 로드
            # from doclayout_yolo import YOLOv10
            # self.model = YOLOv10(model_path)
            # 현재는 모의 초기화
            await asyncio.sleep(1)  # 모델 로딩 시뮬레이션
            
            self.model_loaded = True
            self.initialized_at = int(time.time())
            
            logger.info(f"모델 초기화 완료 - GPU: {self.gpu_available}, 경로: {model_path}")
            
        except Exception as e:
            logger.error(f"모델 초기화 실패: {e}")
            raise e
    
    def _check_gpu_availability(self) -> bool:
        """GPU 사용 가능성 확인"""
        try:
            # 실제 환경에서는 torch.cuda.is_available() 등을 사용
            return self.settings.use_gpu and os.path.exists("/usr/bin/nvidia-smi")
        except:
            return False
    
    async def analyze_layout(self, image_path: str, job_id: str, options: Dict[str, Any]) -> Dict[str, Any]:
        """
        이미지 레이아웃 분석
        
        Args:
            image_path: 분석할 이미지 경로
            job_id: 작업 ID
            options: 분석 옵션
            
        Returns:
            분석 결과 딕셔너리
        """
        if not self.model_loaded:
            raise RuntimeError("모델이 로드되지 않았습니다")
        
        try:
            # 이미지 로드 및 정보 추출
            image_info = self._get_image_info(image_path)
            
            # 실제 레이아웃 분석 (현재는 모의 분석)
            layout_blocks = await self._perform_layout_analysis(
                image_path, 
                options.get("confidence_threshold", self.settings.confidence_threshold)
            )
            
            return {
                "image_info": image_info,
                "layout_blocks": layout_blocks,
                "detected_objects_count": len(layout_blocks),
                "model_used": self.model_version
            }
            
        except Exception as e:
            logger.error(f"레이아웃 분석 실패 - Job ID: {job_id}, 오류: {e}")
            raise e
    
    def _get_image_info(self, image_path: str) -> Dict[str, Any]:
        """이미지 정보 추출"""
        try:
            # OpenCV로 이미지 로드
            img = cv2.imread(image_path)
            if img is None:
                raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
            
            height, width, channels = img.shape
            file_size = os.path.getsize(image_path)
            
            # 파일 확장자로 포맷 추정
            file_ext = Path(image_path).suffix.lower()
            format_map = {'.jpg': 'JPEG', '.jpeg': 'JPEG', '.png': 'PNG', '.bmp': 'BMP'}
            image_format = format_map.get(file_ext, 'UNKNOWN')
            
            return {
                "width": width,
                "height": height,
                "format": image_format,
                "file_size": file_size,
                "channels": channels
            }
            
        except Exception as e:
            logger.error(f"이미지 정보 추출 실패: {e}")
            raise e
    
    async def _perform_layout_analysis(self, image_path: str, confidence_threshold: float) -> List[Dict[str, Any]]:
        """
        실제 레이아웃 분석 수행
        현재는 모의 분석 - 실제 환경에서는 DocLayout-YOLO 모델 사용
        """
        try:
            # 이미지 로드
            img = cv2.imread(image_path)
            height, width = img.shape[:2]
            
            # 모의 레이아웃 분석 결과 생성
            # 실제 환경에서는 DocLayout-YOLO 모델 추론 결과를 사용
            mock_detections = [
                {
                    "class_name": "title",
                    "confidence": 0.95,
                    "x1": int(width * 0.1),
                    "y1": int(height * 0.05),
                    "x2": int(width * 0.9),
                    "y2": int(height * 0.15)
                },
                {
                    "class_name": "plain text",
                    "confidence": 0.88,
                    "x1": int(width * 0.1),
                    "y1": int(height * 0.2),
                    "x2": int(width * 0.9),
                    "y2": int(height * 0.6)
                },
                {
                    "class_name": "table",
                    "confidence": 0.82,
                    "x1": int(width * 0.15),
                    "y1": int(height * 0.65),
                    "x2": int(width * 0.85),
                    "y2": int(height * 0.9)
                },
                {
                    "class_name": "figure",
                    "confidence": 0.76,
                    "x1": int(width * 0.2),
                    "y1": int(height * 0.3),
                    "x2": int(width * 0.8),
                    "y2": int(height * 0.5)
                }
            ]
            
            # 신뢰도 임계값 필터링
            filtered_detections = [
                det for det in mock_detections 
                if det["confidence"] >= confidence_threshold
            ]
            
            # LayoutBlock 형태로 변환
            layout_blocks = []
            for i, det in enumerate(filtered_detections):
                coordinates = {
                    "x1": det["x1"],
                    "y1": det["y1"],
                    "x2": det["x2"],
                    "y2": det["y2"]
                }
                
                width_block = det["x2"] - det["x1"]
                height_block = det["y2"] - det["y1"]
                area = width_block * height_block
                
                layout_block = {
                    "block_index": i,
                    "class_name": det["class_name"],
                    "confidence": det["confidence"],
                    "coordinates": coordinates,
                    "area": area,
                    "width": width_block,
                    "height": height_block
                }
                
                layout_blocks.append(layout_block)
            
            # 비동기 처리 시뮬레이션
            await asyncio.sleep(0.1)
            
            logger.info(f"레이아웃 분석 완료 - 감지된 블록: {len(layout_blocks)}개")
            
            return layout_blocks
            
        except Exception as e:
            logger.error(f"레이아웃 분석 수행 실패: {e}")
            raise e
    
    async def check_health(self) -> Dict[str, Any]:
        """서비스 상태 확인"""
        return {
            "model_loaded": self.model_loaded,
            "model_version": self.model_version,
            "gpu_available": self.gpu_available,
            "initialized_at": self.initialized_at
        }
    
    async def get_model_info(self) -> Dict[str, Any]:
        """모델 정보 반환"""
        return {
            "model_name": "DocLayout-YOLO",
            "model_version": self.model_version,
            "model_path": self.settings.model_path,
            "supported_classes": self.settings.supported_classes,
            "input_size": (1024, 1024),  # DocLayout-YOLO 기본 입력 크기
            "confidence_threshold": self.settings.confidence_threshold,
            "gpu_enabled": self.gpu_available,
            "loaded_at": self.initialized_at
        }
    
    async def cleanup(self):
        """리소스 정리"""
        try:
            if self.model is not None:
                # 실제 환경에서는 모델 메모리 해제
                pass
            
            self.model_loaded = False
            logger.info("LayoutAnalyzer 정리 완료")
            
        except Exception as e:
            logger.error(f"LayoutAnalyzer 정리 실패: {e}")
            raise e
