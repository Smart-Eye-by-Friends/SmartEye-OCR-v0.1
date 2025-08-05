"""
SmartEye TSPM (Text & Scene Processing Module) Service

TSPM 모듈의 주요 서비스 로직을 담당하는 클래스
"""

import time
import logging
from typing import List, Dict, Any, Optional
from django.core.files.storage import default_storage
import cv2
import numpy as np

from .config import TSPMConfig
from .ocr_processor import OCRProcessor
from .image_description_processor import ImageDescriptionProcessor

logger = logging.getLogger(__name__)


class TSPMService:
    """TSPM 서비스 클래스"""
    
    def __init__(self, ocr_language: Optional[str] = None, openai_api_key: Optional[str] = None):
        self.ocr_processor = OCRProcessor(language=ocr_language)
        self.image_processor = ImageDescriptionProcessor(api_key=openai_api_key)
        
        # 설정 유효성 검사
        if not TSPMConfig.validate_config():
            logger.warning("TSPM 설정에 문제가 있습니다. 일부 기능이 제한될 수 있습니다.")
        
        logger.info("TSPM 서비스 초기화 완료")
    
    def process_job(self, job_id: int, layout_result: Optional[Dict[str, Any]] = None, 
                   enable_ocr: bool = True, enable_description: bool = True) -> Dict[str, Any]:
        """LAM 결과를 기반으로 TSPM 처리 (Celery용 통합 인터페이스)"""
        from apps.analysis.models import AnalysisJob, ProcessedImage, LAMLayoutDetection
        
        try:
            # 작업 정보 가져오기
            job = AnalysisJob.objects.get(id=job_id)
            
            logger.info(f"TSPM 처리 시작: {job.job_name} (ID: {job_id})")
            
            # layout_result가 제공되지 않은 경우 DB에서 가져오기
            if layout_result is None:
                layout_result = self._get_layout_result_from_db(job_id)
            
            # 처리할 이미지들 가져오기
            images = ProcessedImage.objects.filter(
                job=job,
                processing_status='completed'
            ).order_by('id')
            
            total_images = images.count()
            if total_images == 0:
                return {
                    'success': False,
                    'error': '처리할 이미지가 없습니다.'
                }
            
            processed_count = 0
            failed_count = 0
            all_results = []
            
            for image in images:
                try:
                    # 이미지별 TSPM 처리
                    result = self._process_single_image(image)
                    
                    if result['success']:
                        processed_count += 1
                        all_results.append(result['data'])
                        logger.info(f"TSPM 처리 완료: {image.processed_filename}")
                    else:
                        failed_count += 1
                        logger.error(f"TSPM 처리 실패: {image.processed_filename} - {result.get('error')}")
                
                except Exception as e:
                    failed_count += 1
                    logger.error(f"TSPM 처리 중 오류: {image.processed_filename} - {e}")
            
            logger.info(f"TSPM 처리 완료: {job.job_name} - 성공: {processed_count}, 실패: {failed_count}")
            
            return {
                'success': True,
                'job_id': job_id,
                'total_images': total_images,
                'processed_images': processed_count,
                'failed_images': failed_count,
                'results': all_results,
                'processing_options': {
                    'enable_ocr': enable_ocr,
                    'enable_description': enable_description
                }
            }
            
        except Exception as e:
            logger.error(f"TSPM 작업 처리 실패: {job_id} - {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def process_job_legacy(self, job_id: int) -> Dict[str, Any]:
        """LAM 분석 완료된 작업에 대한 TSPM 처리"""
        from apps.analysis.models import AnalysisJob, ProcessedImage, LAMLayoutDetection
        
        try:
            # 작업 정보 가져오기
            job = AnalysisJob.objects.get(id=job_id)
            
            if job.status != 'completed':
                return {
                    'success': False,
                    'error': 'LAM 분석이 완료되지 않은 작업입니다.'
                }
            
            logger.info(f"TSPM 처리 시작: {job.job_name} (ID: {job_id})")
            
            # 처리할 이미지들 가져오기
            images = ProcessedImage.objects.filter(
                job=job,
                processing_status='completed'
            ).order_by('id')
            
            total_images = images.count()
            if total_images == 0:
                return {
                    'success': False,
                    'error': '처리할 이미지가 없습니다.'
                }
            
            processed_count = 0
            failed_count = 0
            
            for image in images:
                try:
                    # 이미지별 TSPM 처리
                    result = self._process_single_image(image)
                    
                    if result['success']:
                        processed_count += 1
                        logger.info(f"TSPM 처리 완료: {image.processed_filename}")
                    else:
                        failed_count += 1
                        logger.error(f"TSPM 처리 실패: {image.processed_filename} - {result.get('error')}")
                
                except Exception as e:
                    failed_count += 1
                    logger.error(f"TSPM 처리 중 오류: {image.processed_filename} - {e}")
            
            logger.info(f"TSPM 처리 완료: {job.job_name} - 성공: {processed_count}, 실패: {failed_count}")
            
            return {
                'success': True,
                'job_id': job_id,
                'total_images': total_images,
                'processed_images': processed_count,
                'failed_images': failed_count
            }
            
        except Exception as e:
            logger.error(f"TSPM 작업 처리 실패: {job_id} - {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def _process_single_image(self, processed_image) -> Dict[str, Any]:
        """단일 이미지에 대한 TSPM 처리"""
        try:
            # 이미지 파일 로드
            source_file = processed_image.source_file
            image_path = self._prepare_image_file(source_file, processed_image.page_number)
            
            # 이미지 읽기
            image = cv2.imread(image_path)
            if image is None:
                return {
                    'success': False,
                    'error': f'이미지 파일을 읽을 수 없습니다: {image_path}'
                }
            
            # LAM 감지 결과 가져오기
            detections = processed_image.layout_detections.all().order_by('detection_order')
            
            if not detections.exists():
                return {
                    'success': False,
                    'error': 'LAM 감지 결과가 없습니다.'
                }
            
            # 감지 결과를 딕셔너리 형태로 변환
            detection_dicts = []
            for detection in detections:
                detection_dicts.append({
                    'id': detection.id,
                    'detection_order': detection.detection_order,
                    'class_name': detection.class_name,
                    'confidence': float(detection.confidence),
                    'bbox_x1': detection.bbox_x1,
                    'bbox_y1': detection.bbox_y1,
                    'bbox_x2': detection.bbox_x2,
                    'bbox_y2': detection.bbox_y2,
                })
            
            # OCR 처리
            ocr_results = self.ocr_processor.batch_process(image, detection_dicts)
            self._save_ocr_results(detections, ocr_results)
            
            # 이미지 설명 처리
            desc_results = self.image_processor.batch_process(image, detection_dicts)
            self._save_description_results(detections, desc_results)
            
            successful_ocr = sum(1 for r in ocr_results if r.get('success', False))
            successful_desc = sum(1 for r in desc_results if r.get('success', False))
            
            return {
                'success': True,
                'total_detections': len(detection_dicts),
                'ocr_results': successful_ocr,
                'description_results': successful_desc
            }
            
        except Exception as e:
            logger.error(f"단일 이미지 TSPM 처리 실패: {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def _prepare_image_file(self, source_file, page_number: int = 1) -> str:
        """이미지 파일 준비"""
        try:
            file_path = source_file.storage_path
            
            if source_file.file_type == 'pdf':
                # PDF인 경우 LAM 서비스의 로직을 재사용
                from core.lam.service import LAMService
                lam_service = LAMService()
                return lam_service._extract_pdf_page(file_path, page_number)
            else:
                # 이미지 파일인 경우 직접 경로 반환
                if default_storage.exists(file_path):
                    return default_storage.path(file_path)
                else:
                    raise FileNotFoundError(f"이미지 파일이 존재하지 않습니다: {file_path}")
        
        except Exception as e:
            logger.error(f"이미지 파일 준비 실패: {source_file.original_filename} - {e}")
            raise
    
    def _save_ocr_results(self, detections, ocr_results: List[Dict[str, Any]]):
        """OCR 결과를 데이터베이스에 저장"""
        from apps.analysis.models import TSPMOCRResult
        
        try:
            # 결과를 detection_id로 매핑
            results_by_id = {r.get('detection_id'): r for r in ocr_results if r.get('success')}
            
            for detection in detections:
                result = results_by_id.get(detection.id)
                if result:
                    # 기존 결과 삭제 (재처리인 경우)
                    TSPMOCRResult.objects.filter(detection=detection).delete()
                    
                    # 새로운 결과 저장
                    TSPMOCRResult.objects.create(
                        detection=detection,
                        extracted_text=result.get('extracted_text', ''),
                        processed_text=result.get('processed_text', ''),
                        confidence=result.get('confidence'),
                        language=result.get('language', 'kor+eng'),
                        processing_method=result.get('processing_method', 'tesseract'),
                        text_length=result.get('text_length', 0),
                        processing_time_ms=result.get('processing_time_ms')
                    )
            
            successful_count = len(results_by_id)
            logger.info(f"OCR 결과 저장 완료: {successful_count}개")
            
        except Exception as e:
            logger.error(f"OCR 결과 저장 실패: {e}")
            raise
    
    def _save_description_results(self, detections, desc_results: List[Dict[str, Any]]):
        """이미지 설명 결과를 데이터베이스에 저장"""
        from apps.analysis.models import TSPMImageDescription
        
        try:
            # 결과를 detection_id로 매핑
            results_by_id = {r.get('detection_id'): r for r in desc_results if r.get('success')}
            
            for detection in detections:
                result = results_by_id.get(detection.id)
                if result:
                    # 기존 결과 삭제 (재처리인 경우)
                    TSPMImageDescription.objects.filter(detection=detection).delete()
                    
                    # 새로운 결과 저장
                    TSPMImageDescription.objects.create(
                        detection=detection,
                        description_text=result.get('description_text', ''),
                        subject_category=result.get('subject_category', '일반'),
                        description_type=result.get('description_type', 'figure'),
                        api_model=result.get('api_model', 'gpt-4-vision-preview'),
                        api_cost=result.get('api_cost', 0.0),
                        processing_time_ms=result.get('processing_time_ms')
                    )
            
            successful_count = len(results_by_id)
            logger.info(f"이미지 설명 결과 저장 완료: {successful_count}개")
            
        except Exception as e:
            logger.error(f"이미지 설명 결과 저장 실패: {e}")
            raise
    
    def get_tsmp_results(self, job_id: int) -> Dict[str, Any]:
        """TSPM 결과 조회"""
        from apps.analysis.models import AnalysisJob, ProcessedImage
        
        try:
            job = AnalysisJob.objects.get(id=job_id)
            
            result = {
                'job_id': job_id,
                'job_name': job.job_name,
                'images': []
            }
            
            # 이미지별 결과
            images = ProcessedImage.objects.filter(job=job).order_by('page_number')
            
            for image in images:
                image_result = {
                    'image_id': image.id,
                    'filename': image.processed_filename,
                    'page_number': image.page_number,
                    'ocr_results': [],
                    'description_results': []
                }
                
                # OCR 결과
                for ocr_result in image.layout_detections.all():
                    for ocr in ocr_result.ocr_results.all():
                        image_result['ocr_results'].append({
                            'detection_order': ocr_result.detection_order,
                            'class_name': ocr_result.class_name,
                            'extracted_text': ocr.extracted_text,
                            'processed_text': ocr.processed_text,
                            'confidence': float(ocr.confidence) if ocr.confidence else None,
                            'language': ocr.language,
                            'processing_time_ms': ocr.processing_time_ms
                        })
                
                # 이미지 설명 결과
                for desc_result in image.layout_detections.all():
                    for desc in desc_result.image_descriptions.all():
                        image_result['description_results'].append({
                            'detection_order': desc_result.detection_order,
                            'class_name': desc_result.class_name,
                            'description_text': desc.description_text,
                            'subject_category': desc.subject_category,
                            'description_type': desc.description_type,
                            'api_model': desc.api_model,
                            'processing_time_ms': desc.processing_time_ms
                        })
                
                result['images'].append(image_result)
            
            return result
            
        except Exception as e:
            logger.error(f"TSPM 결과 조회 실패: {job_id} - {e}")
            raise
    
    def cleanup(self):
        """서비스 리소스 정리"""
        try:
            if hasattr(self, 'ocr_processor'):
                self.ocr_processor.cleanup()
            
            if hasattr(self, 'image_processor'):
                self.image_processor.cleanup()
            
            logger.info("TSPM 서비스 리소스 정리 완료")
            
        except Exception as e:
            logger.warning(f"TSPM 리소스 정리 중 오류: {e}")
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        self.cleanup()
