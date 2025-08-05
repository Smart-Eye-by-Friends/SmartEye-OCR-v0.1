"""
SmartEye LAM (Layout Analysis Module) Model Manager

DocLayout-YOLO 모델 관리 및 추론을 담당하는 클래스
"""

import os
import torch
from pathlib import Path
from typing import List, Dict, Any, Optional
from huggingface_hub import hf_hub_download
import logging
logger = logging.getLogger(__name__)

from .config import LAMConfig
from .memory_manager import MemoryManager


class ModelManager:
    """DocLayout-YOLO 모델 관리 클래스"""
    
    def __init__(self, model_choice: str = None):
        self.model_choice = model_choice or LAMConfig.DEFAULT_MODEL
        self.model = None
        self.model_path = None
        self.memory_manager = MemoryManager()
        
        # 모델 설정 검증
        if self.model_choice not in LAMConfig.MODEL_CONFIGS:
            available_models = ", ".join(LAMConfig.MODEL_CONFIGS.keys())
            raise ValueError(f"지원하지 않는 모델: {self.model_choice}. 사용 가능한 모델: {available_models}")
    
    def download_model(self) -> str:
        """사전 훈련된 DocLayout-YOLO 모델 다운로드"""
        selected_model = LAMConfig.MODEL_CONFIGS[self.model_choice]
        
        try:
            logger.info(f"다운로드 중: {selected_model['repo_id']} - {selected_model['filename']}")
            
            filepath = hf_hub_download(
                repo_id=selected_model["repo_id"],
                filename=selected_model["filename"]
            )
            
            logger.info(f"모델이 다운로드 되었습니다: {filepath}")
            self.model_path = filepath
            return filepath
            
        except Exception as e:
            logger.error(f"모델 다운로드 중 오류 발생: {e}")
            raise RuntimeError(f"모델 다운로드 실패: {self.model_choice}") from e
    
    def load_model(self) -> Any:
        """모델 로드"""
        if self.model_path is None:
            self.download_model()
        
        try:
            from ultralytics import YOLO
            
            logger.info(f"모델 로딩 중: {self.model_path}")
            self.model = YOLO(self.model_path)
            logger.info("모델 로딩 완료")
            
            return self.model
            
        except Exception as e:
            logger.error(f"모델 로딩 중 오류 발생: {e}")
            raise RuntimeError(f"모델 로딩 실패") from e
    
    def get_model_config(self) -> Dict[str, Any]:
        """현재 모델 설정 반환"""
        return LAMConfig.MODEL_CONFIGS[self.model_choice]
    
    def predict(self, image_path: str, **kwargs) -> List[Dict[str, Any]]:
        """이미지에 대한 레이아웃 분석 수행"""
        if self.model is None:
            self.load_model()
        
        model_config = self.get_model_config()
        
        # 예측 파라미터 설정
        predict_kwargs = {
            'imgsz': model_config['imgsz'],
            'conf': model_config['conf'],
            'verbose': False,
            **kwargs
        }
        
        try:
            logger.info(f"레이아웃 분석 시작: {image_path}")
            
            # 메모리 상태 확인
            memory_status = self.memory_manager.check_memory_status()
            if memory_status == "critical":
                raise RuntimeError("메모리 부족으로 분석을 중단합니다.")
            
            # 예측 실행
            results = self.model.predict(image_path, **predict_kwargs)
            
            # 결과 파싱
            detections = self._parse_results(results)
            
            logger.info(f"레이아웃 분석 완료: {len(detections)}개 객체 감지됨")
            return detections
            
        except Exception as e:
            logger.error(f"레이아웃 분석 중 오류 발생: {e}")
            raise RuntimeError(f"레이아웃 분석 실패") from e
    
    def _parse_results(self, results) -> List[Dict[str, Any]]:
        """YOLO 결과를 표준화된 형태로 변환"""
        detections = []
        
        for result in results:
            if result.boxes is not None:
                boxes = result.boxes
                
                for i, box in enumerate(boxes):
                    # 바운딩 박스 좌표
                    x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                    
                    # 클래스 정보
                    class_id = int(box.cls[0].cpu().numpy())
                    class_name = result.names[class_id]
                    confidence = float(box.conf[0].cpu().numpy())
                    
                    # 영역 계산
                    width = x2 - x1
                    height = y2 - y1
                    area = width * height
                    center_x = x1 + width / 2
                    center_y = y1 + height / 2
                    
                    # 이미지 크기 기준 비율 계산
                    img_height, img_width = result.orig_shape
                    width_ratio = width / img_width
                    height_ratio = height / img_height
                    
                    detection = {
                        'detection_order': i + 1,
                        'class_name': class_name,
                        'confidence': confidence,
                        'bbox_x1': int(x1),
                        'bbox_y1': int(y1),
                        'bbox_x2': int(x2),
                        'bbox_y2': int(y2),
                        'area_pixels': int(area),
                        'width_ratio': width_ratio,
                        'height_ratio': height_ratio,
                        'center_x': int(center_x),
                        'center_y': int(center_y),
                        'image_width': img_width,
                        'image_height': img_height
                    }
                    
                    detections.append(detection)
        
        # 신뢰도 순으로 정렬
        detections.sort(key=lambda x: x['confidence'], reverse=True)
        
        # detection_order 재정렬
        for i, detection in enumerate(detections):
            detection['detection_order'] = i + 1
        
        return detections
    
    def batch_predict(self, image_paths: List[str], **kwargs) -> Dict[str, List[Dict[str, Any]]]:
        """여러 이미지에 대한 배치 예측"""
        if self.model is None:
            self.load_model()
        
        # 적응적 배치 크기 계산
        batch_size = self.memory_manager.adaptive_batch_size()
        logger.info(f"배치 크기: {batch_size}")
        
        all_results = {}
        
        for i in range(0, len(image_paths), batch_size):
            batch_paths = image_paths[i:i + batch_size]
            
            logger.info(f"배치 {i//batch_size + 1}/{(len(image_paths) + batch_size - 1)//batch_size} 처리 중...")
            
            for image_path in batch_paths:
                try:
                    detections = self.predict(image_path, **kwargs)
                    all_results[image_path] = detections
                except Exception as e:
                    logger.error(f"이미지 처리 실패 {image_path}: {e}")
                    all_results[image_path] = []
        
        return all_results
    
    def cleanup(self):
        """리소스 정리"""
        if self.model is not None:
            del self.model
            self.model = None
            
        # GPU 메모리 정리
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        
        logger.info("모델 리소스 정리 완료")
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        self.cleanup()
