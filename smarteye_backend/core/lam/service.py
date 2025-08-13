"""
SmartEye LAM (Layout Analysis Module) Service

비즈니스 로직을 담당하는 서비스 레이어
"""

import os
import json
import logging
from pathlib import Path
from typing import List, Dict, Any, Optional
from django.core.files.storage import default_storage
from django.conf import settings
from django.db.models import F

from .model_manager import ModelManager
from .memory_manager import MemoryManager
from .config import LAMConfig
from apps.analysis.models import AnalysisJob, ProcessedImage, LAMLayoutDetection
from utils.base import ModelService
from utils.performance_monitor import get_performance_optimizer, PerformanceContextManager, monitor_performance

logger = logging.getLogger(__name__)


class LAMService(ModelService):
    """LAM 서비스 클래스"""
    
    def __init__(self, model_choice: Optional[str] = None):
        # BaseService 초기화
        self.model_choice = model_choice or 'smarteye_finetuned'  # 파인튜닝된 모델로 변경
        model_path = LAMConfig.MODEL_PATHS.get(self.model_choice) if hasattr(LAMConfig, 'MODEL_PATHS') else None
        super().__init__(model_path=model_path)
        
        self.model_manager = ModelManager(self.model_choice)
        self.memory_manager = MemoryManager()
        self.performance_optimizer = get_performance_optimizer()
        
        # 리소스 관리에 추가
        self.add_resource(self.model_manager)
        self.add_resource(self.memory_manager)
        
        # 성능 모니터링 시작
        self.performance_optimizer.start_monitoring()
        
        self.logger.info(f"LAM 서비스 초기화: 모델={self.model_choice}")
    
    def load_model(self):
        """모델 로드 구현"""
        if not self._model_loaded:
            self._model = self.model_manager.load_model()
            self._model_loaded = True
            self.logger.info(f"LAM 모델 로드 완료: {self.model_choice}")
        return self._model
    
    def process(self, job_id: int) -> Dict[str, Any]:
        """BaseService 추상 메서드 구현"""
        return self.process_job(job_id)
    
    @monitor_performance('LAM_JOB_PROCESSING')
    def process_job(self, job_id: int) -> Dict[str, Any]:
        """분석 작업 처리 (성능 모니터링 포함)"""
        try:
            with PerformanceContextManager(str(job_id), 'LAM_TOTAL_PROCESSING'):
                # 작업 조회
                job = AnalysisJob.objects.get(id=job_id)
                job.status = 'processing'
                job.save()
                
                logger.info(f"LAM 분석 작업 시작: {job.job_name} (ID: {job_id})")
                
                # 메모리 압박 상태 확인 및 최적화
                if self.performance_optimizer.memory_monitor.check_memory_pressure():
                    logger.warning(f"메모리 압박 감지, 리소스 정리 실행")
                    self.performance_optimizer.cleanup_resources()
                
                # 처리할 이미지들 조회
                processed_images = job.processed_images.filter(processing_status='pending')
                
                if not processed_images.exists():
                    logger.warning(f"처리할 이미지가 없습니다: Job {job_id}")
                    return {'status': 'error', 'message': '처리할 이미지가 없습니다.'}
                
                # 배치 처리 (최적화된 배치 크기 사용)
                results = self._process_images_batch(processed_images.all(), job)
                
                # 작업 상태 업데이트
                if all(result['success'] for result in results):
                    job.status = 'completed'
                    logger.info(f"LAM 분석 작업 완료: {job.job_name}")
                else:
                    job.status = 'failed'
                    failed_count = sum(1 for result in results if not result['success'])
                    logger.error(f"LAM 분석 작업 일부 실패: {failed_count}/{len(results)}개 이미지")
                
                job.save()
                
                return {
                    'status': 'completed' if job.status == 'completed' else 'partial_failure',
                    'total_images': len(results),
                    'successful_images': sum(1 for result in results if result['success']),
                    'failed_images': sum(1 for result in results if not result['success']),
                    'results': results
                }
            
        except AnalysisJob.DoesNotExist:
            logger.error(f"분석 작업을 찾을 수 없습니다: {job_id}")
            return {'status': 'error', 'message': '분석 작업을 찾을 수 없습니다.'}
        except Exception as e:
            logger.error(f"LAM 분석 작업 중 오류 발생: {e}")
            return {'status': 'error', 'message': str(e)}
    
    def _process_images_batch(self, processed_images: List[ProcessedImage], job: AnalysisJob) -> List[Dict[str, Any]]:
        """이미지 배치 처리 (성능 최적화 포함)"""
        results = []
        
        # 성능 최적화된 배치 크기 계산
        optimized_batch_size = self.performance_optimizer.memory_monitor.calculate_optimal_batch_size()
        legacy_batch_size = self.memory_manager.adaptive_batch_size()
        
        # 더 보수적인 배치 크기 선택
        batch_size = min(optimized_batch_size, legacy_batch_size)
        
        logger.info(f"최적화된 배치 크기: {batch_size} (성능={optimized_batch_size}, 메모리={legacy_batch_size})")
        
        for i in range(0, len(processed_images), batch_size):
            batch = processed_images[i:i + batch_size]
            batch_num = i//batch_size + 1
            
            with PerformanceContextManager(str(job.id), f'LAM_BATCH_{batch_num}'):
                logger.info(f"배치 {batch_num} 처리 중 ({len(batch)}개 이미지)")
                
                # 배치 시작 전 메모리 상태 확인
                memory_info = self.performance_optimizer.memory_monitor.get_current_memory_info()
                if memory_info['percent'] > 90:
                    logger.warning(f"배치 {batch_num}: 메모리 사용량 위험 수준 ({memory_info['percent']:.1f}%)")
                    self.performance_optimizer.cleanup_resources()
                
                for processed_image in batch:
                    result = self._process_single_image(processed_image, job)
                    results.append(result)
                    
                    # 진행률 업데이트
                    # F() 쿼리를 사용하여 동시성 문제 해결
                    AnalysisJob.objects.filter(id=job.id).update(
                        processed_images=F('processed_images') + 1
                    )
                
                # 배치 완료 후 메모리 정리 (필요시)
                if memory_info['percent'] > 80:
                    self.performance_optimizer.cleanup_resources()
        
        return results
    
    def _process_single_image(self, processed_image: ProcessedImage, job: AnalysisJob) -> Dict[str, Any]:
        """단일 이미지 처리"""
        try:
            # 이미지 상태 업데이트
            processed_image.processing_status = 'processing'
            processed_image.save()
            
            # 이미지 파일 경로
            image_path = processed_image.source_file.storage_path
            if not default_storage.exists(image_path):
                raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {image_path}")
            
            # 실제 파일 경로 얻기
            actual_path = default_storage.path(image_path)
            
            # LAM 모델로 분석
            detections = self.model_manager.predict(actual_path)
            
            # 결과 저장
            self._save_detections(processed_image, detections)
            
            # 상태 업데이트
            processed_image.processing_status = 'completed'
            processed_image.save()
            
            logger.info(f"이미지 처리 완료: {processed_image.processed_filename}")
            
            return {
                'success': True,
                'image_id': processed_image.id,
                'detections_count': len(detections),
                'message': '처리 완료'
            }
            
        except Exception as e:
            # 오류 처리
            processed_image.processing_status = 'failed'
            processed_image.error_message = str(e)
            processed_image.save()
            
            logger.error(f"이미지 처리 실패 {processed_image.processed_filename}: {e}")
            
            return {
                'success': False,
                'image_id': processed_image.id,
                'error': str(e)
            }
    
    def _save_detections(self, processed_image: ProcessedImage, detections: List[Dict[str, Any]]):
        """감지 결과를 데이터베이스에 저장"""
        for detection_data in detections:
            LAMLayoutDetection.objects.create(
                image=processed_image,
                detection_order=detection_data['detection_order'],
                class_name=detection_data['class_name'],
                confidence=detection_data['confidence'],
                bbox_x1=detection_data['bbox_x1'],
                bbox_y1=detection_data['bbox_y1'],
                bbox_x2=detection_data['bbox_x2'],
                bbox_y2=detection_data['bbox_y2'],
                area_pixels=detection_data['area_pixels'],
                width_ratio=detection_data['width_ratio'],
                height_ratio=detection_data['height_ratio'],
                center_x=detection_data['center_x'],
                center_y=detection_data['center_y']
            )
    
    def get_analysis_results(self, job_id: int) -> Dict[str, Any]:
        """분석 결과 조회"""
        try:
            job = AnalysisJob.objects.get(id=job_id)
            
            # 처리된 이미지들과 감지 결과 조회
            results = []
            for processed_image in job.processed_images.all():
                detections = []
                for detection in processed_image.layout_detections.all().order_by('detection_order'):
                    detections.append({
                        'detection_order': detection.detection_order,
                        'class_name': detection.class_name,
                        'confidence': float(detection.confidence),
                        'bbox': {
                            'x1': detection.bbox_x1,
                            'y1': detection.bbox_y1,
                            'x2': detection.bbox_x2,
                            'y2': detection.bbox_y2,
                        },
                        'area_pixels': detection.area_pixels,
                        'center': {
                            'x': detection.center_x,
                            'y': detection.center_y
                        }
                    })
                
                results.append({
                    'image_id': processed_image.id,
                    'filename': processed_image.processed_filename,
                    'page_number': processed_image.page_number,
                    'status': processed_image.processing_status,
                    'detections': detections
                })
            
            return {
                'job_id': job.id,
                'job_name': job.job_name,
                'status': job.status,
                'model_type': job.model_type,
                'total_images': job.total_images,
                'processed_images': job.processed_images,
                'results': results
            }
            
        except AnalysisJob.DoesNotExist:
            return {'error': '분석 작업을 찾을 수 없습니다.'}
    
    def cleanup(self):
        """리소스 정리"""
        if hasattr(self, 'model_manager'):
            self.model_manager.cleanup()
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        self.cleanup()
