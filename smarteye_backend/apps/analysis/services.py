"""
SmartEye Analysis Services

분석 관련 비즈니스 로직을 담당하는 서비스 모듈
"""

import logging
import json
import os
from datetime import datetime
from typing import Dict, Any, List, Optional
from django.conf import settings
from django.http import JsonResponse
from rest_framework import status
from rest_framework.response import Response

from .models import AnalysisResult, AnalysisJob

logger = logging.getLogger(__name__)


class BookResultMerger:
    """책 결과 병합 서비스"""
    
    def __init__(self):
        self.logger = logger
    
    def merge_results(self, request) -> Response:
        """사용자별 페이지 JSON 결과를 책 단위로 병합"""
        try:
            # 1. 요청 데이터 검증 및 추출
            validation_result = self._validate_merge_request(request)
            if isinstance(validation_result, Response):
                return validation_result
            
            user_id, book_name, job_ids = validation_result
            
            # 2. 사용자 권한 확인
            if not self._check_user_permission(request.user, user_id):
                return self._error_response(
                    '다른 사용자의 결과는 병합할 수 없습니다.',
                    status.HTTP_403_FORBIDDEN
                )
            
            # 3. 병합할 분석 결과 조회
            results = self._get_analysis_results(user_id, job_ids)
            if not results.exists():
                return self._error_response(
                    '병합할 분석 결과가 없습니다.',
                    status.HTTP_404_NOT_FOUND
                )
            
            # 4. 책 데이터 병합
            merged_book = self._create_merged_book(results, book_name, user_id)
            
            # 5. 파일 저장 (옵션)
            file_path = None
            if request.data.get('save_to_file', False):
                file_path = self._save_merged_book_to_file(merged_book, user_id, book_name)
            
            # 6. 성공 응답
            return self._success_response(merged_book, file_path, results.count())
            
        except Exception as e:
            self.logger.error(f"페이지 병합 실패: {e}")
            import traceback
            traceback.print_exc()
            return self._error_response(f'페이지 병합 중 오류가 발생했습니다: {str(e)}')
    
    def _validate_merge_request(self, request) -> tuple:
        """병합 요청 검증"""
        user_id = request.data.get('user_id', request.user.id)
        book_name = request.data.get('book_name', 'SmartEye 통합 분석 결과')
        job_ids = request.data.get('job_ids', [])
        
        return user_id, book_name, job_ids
    
    def _check_user_permission(self, current_user, target_user_id: int) -> bool:
        """사용자 권한 확인"""
        return target_user_id == current_user.id or current_user.is_staff
    
    def _get_analysis_results(self, user_id: int, job_ids: List[int]):
        """분석 결과 조회"""
        results_query = AnalysisResult.objects.filter(job__user_id=user_id)
        
        if job_ids:
            results_query = results_query.filter(job_id__in=job_ids)
        
        return results_query.order_by('job__created_at', 'job_id')
    
    def _create_merged_book(self, results, book_name: str, user_id: int) -> Dict[str, Any]:
        """병합된 책 데이터 생성"""
        merged_book = self._initialize_book_structure(book_name, user_id, results)
        
        page_number = 1
        stats = {'total_confidence': 0, 'total_detections': 0, 'total_processing_time': 0}
        
        for result in results:
            try:
                page_info = self._create_page_info(result, page_number)
                merged_book['pages'].append(page_info)
                
                # 통계 누적
                stats['total_confidence'] += page_info['confidence_score']
                stats['total_detections'] += page_info['detection_count']
                stats['total_processing_time'] += page_info['processing_time']
                
                # 컨텐츠 요약 업데이트
                self._update_content_summary(merged_book, result, page_number)
                
                page_number += 1
                
            except Exception as e:
                self.logger.warning(f"페이지 {page_number} 병합 중 오류: {e}")
                continue
        
        # 최종 통계 계산
        self._finalize_book_statistics(merged_book, stats, page_number - 1)
        
        return merged_book
    
    def _initialize_book_structure(self, book_name: str, user_id: int, results) -> Dict[str, Any]:
        """책 구조 초기화"""
        return {
            'book_info': {
                'book_name': book_name,
                'user_id': user_id,
                'total_pages': 0,
                'created_at': results.first().created_at.isoformat(),
                'merged_at': None,
                'analysis_summary': {
                    'total_jobs': results.count(),
                    'total_processing_time': 0,
                    'average_confidence': 0,
                    'total_detections': 0
                }
            },
            'pages': [],
            'content_summary': {
                'text_blocks': [],
                'images': [],
                'tables': [],
                'other_elements': []
            }
        }
    
    def _create_page_info(self, result, page_number: int) -> Dict[str, Any]:
        """페이지 정보 생성"""
        result_data = result.result_data if isinstance(result.result_data, dict) else {}
        
        return {
            'page_number': page_number,
            'job_id': result.job_id,
            'job_name': result.job.job_name if result.job else f'Job {result.job_id}',
            'processing_time': float(result.processing_time_seconds) if result.processing_time_seconds else 0,
            'confidence_score': float(result.confidence_score) if result.confidence_score else 0,
            'detection_count': result.total_detected_elements or 0,
            'content': {
                'text_content': result.text_content or '',
                'braille_content': result.braille_content or '',
                'layout_analysis': result_data.get('layout_analysis', {}),
                'ocr_results': result_data.get('ocr_results', {}),
                'image_descriptions': result_data.get('image_descriptions', {}),
                'integrated_content': result_data.get('integrated_content', {})
            },
            'file_info': {
                'pdf_path': result.pdf_path or '',
                'json_path': result.json_path or '',
                'xml_path': result.xml_path or ''
            }
        }
    
    def _update_content_summary(self, merged_book: Dict, result, page_number: int):
        """컨텐츠 요약 업데이트"""
        if result.text_content:
            text_preview = result.text_content[:200] + '...' if len(result.text_content) > 200 else result.text_content
            merged_book['content_summary']['text_blocks'].append({
                'page': page_number,
                'content': text_preview
            })
    
    def _finalize_book_statistics(self, merged_book: Dict, stats: Dict, page_count: int):
        """최종 통계 계산"""
        merged_book['book_info']['total_pages'] = page_count
        merged_book['book_info']['merged_at'] = datetime.now().isoformat()
        merged_book['book_info']['analysis_summary'].update({
            'total_processing_time': round(stats['total_processing_time'], 2),
            'average_confidence': round(stats['total_confidence'] / page_count, 3) if page_count > 0 else 0,
            'total_detections': stats['total_detections']
        })
    
    def _save_merged_book_to_file(self, merged_book: Dict, user_id: int, book_name: str) -> str:
        """병합된 책 데이터를 파일로 저장"""
        filename = f"merged_book_{user_id}_{book_name.replace(' ', '_')}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        file_path = os.path.join(settings.MEDIA_ROOT, 'merged_results', filename)
        
        # 디렉토리 생성
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        
        # JSON 파일 저장
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(merged_book, f, ensure_ascii=False, indent=2)
        
        self.logger.info(f"병합된 결과 저장됨: {file_path}")
        return file_path
    
    def _success_response(self, merged_book: Dict, file_path: Optional[str], total_jobs: int) -> Response:
        """성공 응답 생성"""
        page_count = len(merged_book['pages'])
        
        return Response({
            'success': True,
            'message': f'{page_count}개 페이지가 성공적으로 병합되었습니다.',
            'merged_book': merged_book,
            'file_path': file_path,
            'statistics': {
                'total_pages': page_count,
                'total_jobs': total_jobs,
                'total_processing_time': merged_book['book_info']['analysis_summary']['total_processing_time'],
                'average_confidence': merged_book['book_info']['analysis_summary']['average_confidence'],
                'total_detections': merged_book['book_info']['analysis_summary']['total_detections']
            }
        }, status=status.HTTP_200_OK)
    
    def _error_response(self, message: str, status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR) -> Response:
        """에러 응답 생성"""
        return Response({'error': message}, status=status_code)


class AnalysisStatisticsService:
    """분석 통계 서비스"""
    
    @staticmethod
    def get_user_analysis_stats(user_id: int) -> Dict[str, Any]:
        """사용자별 분석 통계 조회"""
        jobs = AnalysisJob.objects.filter(user_id=user_id)
        
        return {
            'total_jobs': jobs.count(),
            'completed_jobs': jobs.filter(status='completed').count(),
            'failed_jobs': jobs.filter(status='failed').count(),
            'processing_jobs': jobs.filter(status='processing').count(),
            'total_images_processed': sum(job.processed_images for job in jobs),
            'average_processing_time': jobs.aggregate(
                avg_time=models.Avg('processing_time_seconds')
            )['avg_time'] or 0
        }
    
    @staticmethod
    def get_model_usage_stats() -> Dict[str, int]:
        """모델별 사용 통계"""
        from django.db.models import Count
        
        model_stats = AnalysisJob.objects.values('model_type').annotate(
            count=Count('id')
        ).order_by('-count')
        
        return {stat['model_type']: stat['count'] for stat in model_stats}


class JobProgressCalculator:
    """작업 진행률 계산 서비스"""
    
    @staticmethod
    def calculate_progress(job: AnalysisJob) -> Dict[str, Any]:
        """작업 진행률 계산"""
        if job.total_images <= 0:
            return {
                'progress_percent': 0,
                'status': job.status,
                'estimated_completion': None
            }
        
        progress_percent = (job.processed_images / job.total_images) * 100
        
        # 예상 완료 시간 계산 (간단한 선형 추정)
        estimated_completion = None
        if job.started_at and progress_percent > 0:
            elapsed_time = (datetime.now() - job.started_at).total_seconds()
            if progress_percent < 100:
                estimated_total_time = elapsed_time * (100 / progress_percent)
                remaining_time = estimated_total_time - elapsed_time
                estimated_completion = datetime.now().timestamp() + remaining_time
        
        return {
            'progress_percent': round(progress_percent, 2),
            'status': job.status,
            'processed_images': job.processed_images,
            'total_images': job.total_images,
            'failed_images': job.failed_images,
            'estimated_completion': estimated_completion
        }