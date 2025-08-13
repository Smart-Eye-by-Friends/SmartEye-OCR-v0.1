from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.core.files.storage import default_storage
from django.shortcuts import get_object_or_404
import logging
from typing import List, Dict, Any, Optional, Union
from dataclasses import dataclass

from .models import AnalysisJob, ProcessedImage, AnalysisResult
from .serializers import AnalysisJobSerializer, ProcessedImageSerializer
from .tasks import process_complete_analysis, process_individual_analysis
from .services import BookResultMerger, AnalysisStatisticsService, JobProgressCalculator
from apps.files.models import SourceFile
from core.lam.service import LAMService
from utils.file_validation import FileValidator, FileValidationConfig
from utils.file_processors import BatchFileProcessor
from utils.response_helpers import ResponseHelper
from utils.mixins import SmartEyeViewSetMixin, StatusFilterMixin, SearchMixin
from utils.api_optimization import optimize_api, cached_response, performance_monitoring
from utils.security_enhancements import secure_api, rate_limit

logger = logging.getLogger(__name__)


@dataclass
class ProcessingOptions:
    """분석 처리 옵션"""
    model_choice: str = 'yolo11n-doclay'
    enable_ocr: bool = True
    enable_description: bool = True
    visualization_type: str = 'comparison'


class AnalysisJobViewSet(SmartEyeViewSetMixin, StatusFilterMixin, SearchMixin, viewsets.ModelViewSet):
    """분석 작업 관리 ViewSet (리팩토링된 버전)"""
    
    queryset = AnalysisJob.objects.all()
    serializer_class = AnalysisJobSerializer
    search_fields = ['job_name', 'description']
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.file_validator = FileValidator()
        self.file_processor = BatchFileProcessor()
        self.job_progress_calculator = JobProgressCalculator()
    
    @optimize_api(cache_timeout=60, enable_compression=True)  # 목록 조회 캐싱
    def list(self, request, *args, **kwargs):
        """분석 작업 목록 조회 (최적화 적용)"""
        return super().list(request, *args, **kwargs)
    
    @optimize_api(cache_timeout=300, enable_compression=True)  # 상세 조회 캐싱
    def retrieve(self, request, *args, **kwargs):
        """분석 작업 상세 조회 (최적화 적용)"""
        return super().retrieve(request, *args, **kwargs)
    
    @action(detail=False, methods=['post'])
    @secure_api(endpoint_type='upload', max_input_length=1000)
    @performance_monitoring(include_metrics=True)
    def upload_and_analyze(self, request):
        """파일 업로드 및 완전 분석 파이프라인 시작 (개선된 버전)"""
        try:
            # 파일 업로드 처리
            uploaded_files = request.FILES.getlist('files')
            if not uploaded_files:
                return Response(
                    {'error': '업로드할 파일이 없습니다.'},
                    status=status.HTTP_400_BAD_REQUEST
                )
            
            # 파일 형식 및 크기 검증
            supported_formats = ['.jpg', '.jpeg', '.png', '.pdf', '.bmp', '.tiff']
            max_file_size_mb = 50  # 50MB 제한
            
            for file in uploaded_files:
                # 파일 확장자 검증
                file_ext = '.' + file.name.lower().split('.')[-1] if '.' in file.name else ''
                if file_ext not in supported_formats:
                    return Response({
                        'error': f'지원하지 않는 파일 형식입니다: {file.name}. 지원 형식: {", ".join(supported_formats)}'
                    }, status=status.HTTP_400_BAD_REQUEST)
                
                # 파일 크기 검증
                file_size_mb = file.size / (1024 * 1024)
                if file_size_mb > max_file_size_mb:
                    return Response({
                        'error': f'파일 크기가 너무 큽니다: {file.name} ({file_size_mb:.2f}MB). 최대 {max_file_size_mb}MB까지 지원합니다.'
                    }, status=status.HTTP_400_BAD_REQUEST)
            
            # 작업 설정
            job_name = request.data.get('job_name', 'SmartEye 완전 분석')
            model_choice = request.data.get('model_choice', 'yolo11n-doclay')
            description = request.data.get('description', '')
            enable_ocr = request.data.get('enable_ocr', True)
            enable_description = request.data.get('enable_description', True)
            visualization_type = request.data.get('visualization_type', 'comparison')
            
            # 분석 작업 생성
            job = AnalysisJob.objects.create(
                user=request.user,
                job_name=job_name,
                description=description,
                model_type=model_choice,
                total_images=0,  # 나중에 업데이트
                status='pending',
                enable_ocr=enable_ocr,
                enable_api=enable_description
            )
            
            # 파일 저장 및 이미지 생성
            total_images = 0
            for file in uploaded_files:
                # 원본 파일 저장
                source_file = SourceFile.objects.create(
                    user=request.user,
                    original_filename=file.name,
                    stored_filename=file.name,
                    file_type='pdf' if file.name.lower().endswith('.pdf') else 'image',
                    file_size_mb=file.size / (1024 * 1024),
                    upload_status='completed'
                )
                
                # 파일 저장
                file_path = default_storage.save(f'uploads/{file.name}', file)
                source_file.storage_path = file_path
                source_file.save()
                
                # 파일 형식별 처리
                if file.name.lower().endswith('.pdf'):
                    # PDF 처리: 페이지별로 이미지 생성
                    try:
                        pdf_processor = PDFProcessor(scale_factor=2.0, dpi=150)
                        
                        # PDF 정보 추출
                        pdf_info = pdf_processor.get_pdf_info(file)
                        
                        if pdf_info.get('error'):
                            logger.error(f"PDF 정보 추출 실패: {pdf_info['error']}")
                            return Response({
                                'error': f'PDF 파일을 처리할 수 없습니다: {pdf_info["error"]}'
                            }, status=status.HTTP_400_BAD_REQUEST)
                        
                        if pdf_info.get('is_encrypted'):
                            return Response({
                                'error': '암호화된 PDF는 지원하지 않습니다.'
                            }, status=status.HTTP_400_BAD_REQUEST)
                        
                        page_count = pdf_info['page_count']
                        
                        # 각 페이지를 ProcessedImage로 생성
                        for page_num in range(1, page_count + 1):
                            ProcessedImage.objects.create(
                                source_file=source_file,
                                job=job,
                                processed_filename=f"{file.name}_page_{page_num}",
                                page_number=page_num,
                                image_width=int(pdf_info.get('page_width', 1920)),
                                image_height=int(pdf_info.get('page_height', 1080)),
                                processing_status='pending'
                            )
                        
                        logger.info(f"PDF 처리 준비 완료: {page_count} 페이지")
                        
                    except ImportError:
                        logger.error("PyMuPDF 라이브러리가 설치되지 않았습니다.")
                        return Response({
                            'error': 'PDF 처리를 위한 라이브러리가 설치되지 않았습니다.'
                        }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
                        
                    except Exception as e:
                        logger.error(f"PDF 처리 중 오류 발생: {e}")
                        return Response({
                            'error': f'PDF 처리 중 오류가 발생했습니다: {str(e)}'
                        }, status=status.HTTP_400_BAD_REQUEST)
                        
                else:
                    # 일반 이미지 파일 처리
                    page_count = 1
                    
                    # 이미지 파일의 실제 크기 추출 (가능한 경우)
                    try:
                        from PIL import Image
                        with Image.open(file) as img:
                            width, height = img.size
                    except Exception:
                        # 기본값 사용
                        width, height = 1920, 1080
                    
                    ProcessedImage.objects.create(
                        source_file=source_file,
                        job=job,
                        processed_filename=file.name,
                        page_number=1,
                        image_width=width,
                        image_height=height,
                        processing_status='pending'
                    )
                    total_images += 1
            
            # 총 이미지 수 업데이트
            job.total_images = total_images
            job.save()
            
            # 처리 옵션 설정
            processing_options = {
                'model_choice': model_choice,
                'enable_ocr': enable_ocr,
                'enable_description': enable_description,
                'visualization_type': visualization_type
            }
            
            # 완전 분석 파이프라인 시작
            task = process_complete_analysis.delay(job.pk, processing_options)
            
            logger.info(f"완전 분석 작업 생성됨: {job.job_name} (ID: {job.pk}, Task: {task.id})")
            
            return Response({
                'job_id': job.pk,
                'task_id': task.id,
                'status': 'processing',
                'message': 'SmartEye 완전 분석이 시작되었습니다.',
                'total_images': total_images,
                'processing_options': processing_options
            }, status=status.HTTP_201_CREATED)
            
        except Exception as e:
            logger.error(f"파일 업로드 및 분석 시작 실패: {e}")
            return Response(
                {'error': f'분석 시작 중 오류가 발생했습니다: {str(e)}'},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
    
    @action(detail=True, methods=['get'])
    @cached_response(timeout=10, cache_by_user=True)  # 10초 캐싱
    def progress(self, request, pk=None):
        """분석 진행률 조회 (캐싱 적용)"""
        job = self.get_object()
        
        progress_percent = 0
        if job.total_images > 0:
            progress_percent = (job.processed_images / job.total_images) * 100
        
        return Response({
            'job_id': job.id,
            'job_name': job.job_name,
            'status': job.status,
            'progress': round(progress_percent, 2),
            'processed_images': job.processed_images,
            'total_images': job.total_images,
            'failed_images': job.failed_images,
            'started_at': job.started_at,
            'estimated_completion': job.estimated_completion
        })
    

    
    @action(detail=True, methods=['post'])
    def cancel(self, request, pk=None):
        """분석 작업 취소"""
        job = self.get_object()
        
        if job.status in ['completed', 'failed', 'cancelled']:
            return Response({
                'message': '이미 완료되었거나 취소된 작업입니다.',
                'status': job.status
            }, status=status.HTTP_400_BAD_REQUEST)
        
        job.status = 'cancelled'
        job.save()
        
        logger.info(f"분석 작업 취소됨: {job.job_name} (ID: {job.id})")
        
        return Response({
            'message': '작업이 취소되었습니다.',
            'job_id': job.id
        })
    
    @action(detail=False, methods=['get'])
    @cached_response(timeout=3600, cache_by_user=False)  # 1시간 캐싱, 사용자별 구분 없음
    def models(self, request):
        """사용 가능한 모델 목록 조회 (캐싱 적용)"""
        from core.lam.config import LAMConfig
        
        models = []
        for key, config in LAMConfig.MODEL_CONFIGS.items():
            models.append({
                'key': key,
                'name': key.replace('_', ' ').title(),
                'repo_id': config['repo_id'],
                'imgsz': config['imgsz'],
                'conf': config['conf']
            })
        
        return Response({
            'models': models,
            'default_model': LAMConfig.DEFAULT_MODEL
        })

    @action(detail=True, methods=['get'])
    def task_status(self, request, pk=None):
        """Celery 작업 진행 상태 조회"""
        try:
            job = self.get_object()
            
            # 최근 작업 ID 가져오기 (실제로는 작업에서 task_id를 저장해야 함)
            task_id = request.query_params.get('task_id')
            if not task_id:
                return Response({
                    'job_id': job.pk,
                    'status': job.status,
                    'message': '작업 상태를 확인할 수 없습니다.'
                })
            
            # Celery 작업 상태 확인
            try:
                from celery.result import AsyncResult
                result = AsyncResult(task_id)
                
                response_data = {
                    'job_id': job.pk,
                    'task_id': task_id,
                    'task_state': result.state,
                    'job_status': job.status
                }
                
                if result.state == 'PROGRESS':
                    response_data['progress'] = result.info
                elif result.state == 'SUCCESS':
                    response_data['result'] = result.result
                elif result.state == 'FAILURE':
                    response_data['error'] = str(result.info)
                    
                return Response(response_data)
                
            except ImportError:
                # Celery가 설치되지 않은 경우
                return Response({
                    'job_id': job.pk,
                    'task_id': task_id,
                    'task_state': 'UNKNOWN',
                    'job_status': job.status,
                    'message': 'Celery가 설치되지 않았습니다.'
                })
            
        except Exception as e:
            logger.error(f"작업 상태 조회 실패: {e}")
            return Response(
                {'error': '작업 상태 조회 중 오류가 발생했습니다.'},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    @action(detail=True, methods=['get'])
    def results(self, request, pk=None):
        """분석 결과 조회"""
        try:
            job = self.get_object()
            
            # 결과 조회
            try:
                result = AnalysisResult.objects.get(job=job)
                return Response({
                    'job_id': job.pk,
                    'result_type': result.result_type,
                    'result_data': result.result_data,
                    'processing_time': result.processing_time,
                    'detection_count': result.detection_count,
                    'confidence_score': result.confidence_score,
                    'file_paths': result.file_paths,
                    'created_at': result.created_at,
                    'updated_at': result.updated_at
                })
            except AnalysisResult.DoesNotExist:
                return Response({
                    'job_id': job.pk,
                    'status': job.status,
                    'message': '분석 결과가 아직 생성되지 않았습니다.'
                })
                
        except Exception as e:
            logger.error(f"분석 결과 조회 실패: {e}")
            return Response(
                {'error': '분석 결과 조회 중 오류가 발생했습니다.'},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    @action(detail=False, methods=['post'])
    def individual_analysis(self, request):
        """개별 모듈 분석 시작 (LAM, TSPM, CIM 중 하나만)"""
        try:
            job_id = request.data.get('job_id')
            analysis_type = request.data.get('analysis_type')  # 'lam', 'tspm', 'cim'
            model_choice = request.data.get('model_choice', 'yolo11n-doclay')
            
            if not job_id or not analysis_type:
                return Response(
                    {'error': 'job_id와 analysis_type이 필요합니다.'},
                    status=status.HTTP_400_BAD_REQUEST
                )
            
            if analysis_type not in ['lam', 'tspm', 'cim']:
                return Response(
                    {'error': 'analysis_type은 lam, tspm, cim 중 하나여야 합니다.'},
                    status=status.HTTP_400_BAD_REQUEST
                )
            
            # 작업 권한 확인
            try:
                job = AnalysisJob.objects.get(pk=job_id, user=request.user)
            except AnalysisJob.DoesNotExist:
                return Response(
                    {'error': '작업을 찾을 수 없거나 권한이 없습니다.'},
                    status=status.HTTP_404_NOT_FOUND
                )
            
            # 개별 분석 시작
            task = process_individual_analysis.delay(job_id, analysis_type, model_choice)
            
            logger.info(f"개별 분석 시작: {analysis_type} - Job {job_id}, Task {task.id}")
            
            return Response({
                'job_id': job_id,
                'task_id': task.id,
                'analysis_type': analysis_type,
                'status': 'processing',
                'message': f'{analysis_type.upper()} 분석이 시작되었습니다.'
            }, status=status.HTTP_202_ACCEPTED)
            
        except Exception as e:
            logger.error(f"개별 분석 시작 실패: {e}")
            return Response(
                {'error': f'개별 분석 시작 중 오류가 발생했습니다: {str(e)}'},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


class ProcessedImageViewSet(viewsets.ReadOnlyModelViewSet):
    """처리된 이미지 조회 ViewSet"""
    
    serializer_class = ProcessedImageSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        """현재 사용자의 이미지만 조회"""
        return ProcessedImage.objects.filter(
            job__user=self.request.user
        ).order_by('-created_at')
    
    @action(detail=True, methods=['get'])
    def detections(self, request, pk=None):
        """이미지의 레이아웃 감지 결과 조회"""
        image = self.get_object()
        
        detections = []
        for detection in image.layout_detections.all().order_by('detection_order'):
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
        
        return Response({
            'image_id': image.id,
            'filename': image.processed_filename,
            'page_number': image.page_number,
            'status': image.processing_status,
            'detections': detections
        })

    @action(detail=False, methods=['post'])
    def merge_book_results(self, request):
        """사용자별 페이지 JSON 결과를 책 단위로 병합"""
        try:
            # 요청 파라미터 확인
            user_id = request.data.get('user_id', request.user.id)
            book_name = request.data.get('book_name', 'SmartEye 통합 분석 결과')
            job_ids = request.data.get('job_ids', [])  # 특정 작업들만 병합할 경우
            
            # 사용자 권한 확인 (자신의 결과만 병합 가능)
            if user_id != request.user.id and not request.user.is_staff:
                return Response({
                    'error': '다른 사용자의 결과는 병합할 수 없습니다.'
                }, status=status.HTTP_403_FORBIDDEN)
            
            # 병합할 분석 결과 조회
            results_query = AnalysisResult.objects.filter(job__user_id=user_id)
            
            if job_ids:
                results_query = results_query.filter(job_id__in=job_ids)
            
            results = results_query.order_by('job__created_at', 'job_id')
            
            if not results.exists():
                return Response({
                    'error': '병합할 분석 결과가 없습니다.'
                }, status=status.HTTP_404_NOT_FOUND)
            
            # 책 단위 JSON 구조 생성
            merged_book = {
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
            
            total_confidence = 0
            total_detections = 0
            total_processing_time = 0
            page_number = 1
            
            # 각 분석 결과를 페이지로 병합
            for result in results:
                try:
                    # 결과 데이터 파싱
                    result_data = result.result_data if isinstance(result.result_data, dict) else {}
                    
                    # 페이지 정보 구성
                    page_info = {
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
                    
                    merged_book['pages'].append(page_info)
                    
                    # 통계 누적
                    total_confidence += page_info['confidence_score']
                    total_detections += page_info['detection_count']
                    total_processing_time += page_info['processing_time']
                    
                    # 컨텐츠 요약 업데이트
                    if result.text_content:
                        merged_book['content_summary']['text_blocks'].append({
                            'page': page_number,
                            'content': result.text_content[:200] + '...' if len(result.text_content) > 200 else result.text_content
                        })
                    
                    page_number += 1
                    
                except Exception as e:
                    logger.warning(f"페이지 {page_number} 병합 중 오류: {e}")
                    continue
            
            # 최종 통계 계산
            page_count = len(merged_book['pages'])
            merged_book['book_info']['total_pages'] = page_count
            merged_book['book_info']['merged_at'] = __import__('datetime').datetime.now().isoformat()
            merged_book['book_info']['analysis_summary'].update({
                'total_processing_time': round(total_processing_time, 2),
                'average_confidence': round(total_confidence / page_count, 3) if page_count > 0 else 0,
                'total_detections': total_detections
            })
            
            # 병합 결과를 파일로 저장 (선택사항)
            save_to_file = request.data.get('save_to_file', False)
            file_path = None
            
            if save_to_file:
                import json
                import os
                from django.conf import settings
                
                # 파일 저장 경로 생성
                filename = f"merged_book_{user_id}_{book_name.replace(' ', '_')}_{__import__('datetime').datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
                file_path = os.path.join(settings.MEDIA_ROOT, 'merged_results', filename)
                
                # 디렉토리 생성
                os.makedirs(os.path.dirname(file_path), exist_ok=True)
                
                # JSON 파일 저장
                with open(file_path, 'w', encoding='utf-8') as f:
                    json.dump(merged_book, f, ensure_ascii=False, indent=2)
                
                logger.info(f"병합된 결과 저장됨: {file_path}")
            
            return Response({
                'success': True,
                'message': f'{page_count}개 페이지가 성공적으로 병합되었습니다.',
                'merged_book': merged_book,
                'file_path': file_path,
                'statistics': {
                    'total_pages': page_count,
                    'total_jobs': results.count(),
                    'total_processing_time': round(total_processing_time, 2),
                    'average_confidence': round(total_confidence / page_count, 3) if page_count > 0 else 0,
                    'total_detections': total_detections
                }
            }, status=status.HTTP_200_OK)
            
        except Exception as e:
            logger.error(f"페이지 병합 실패: {e}")
            import traceback
            traceback.print_exc()
            return Response({
                'error': f'페이지 병합 중 오류가 발생했습니다: {str(e)}'
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
