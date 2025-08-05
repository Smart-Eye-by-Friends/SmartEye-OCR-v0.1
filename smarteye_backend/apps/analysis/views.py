from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.core.files.storage import default_storage
from django.shortcuts import get_object_or_404
import logging

from .models import AnalysisJob, ProcessedImage, AnalysisResult
from .serializers import AnalysisJobSerializer, ProcessedImageSerializer
from .tasks import process_complete_analysis, process_individual_analysis
from apps.files.models import SourceFile
from core.lam.service import LAMService

logger = logging.getLogger(__name__)


class AnalysisJobViewSet(viewsets.ModelViewSet):
    """분석 작업 관리 ViewSet"""
    
    serializer_class = AnalysisJobSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        """현재 사용자의 작업만 조회"""
        return AnalysisJob.objects.filter(user=self.request.user).order_by('-created_at')
    
    def perform_create(self, serializer):
        """작업 생성 시 현재 사용자 자동 설정"""
        serializer.save(user=self.request.user)
    
    @action(detail=False, methods=['post'])
    def upload_and_analyze(self, request):
        """파일 업로드 및 완전 분석 파이프라인 시작"""
        try:
            # 파일 업로드 처리
            uploaded_files = request.FILES.getlist('files')
            if not uploaded_files:
                return Response(
                    {'error': '업로드할 파일이 없습니다.'},
                    status=status.HTTP_400_BAD_REQUEST
                )
            
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
                
                # PDF인 경우 페이지별로 이미지 생성
                if file.name.lower().endswith('.pdf'):
                    # PDF 처리 로직 (추후 구현)
                    page_count = 1  # 임시로 1페이지로 설정
                else:
                    page_count = 1
                
                # 처리할 이미지 생성
                for page_num in range(1, page_count + 1):
                    ProcessedImage.objects.create(
                        source_file=source_file,
                        job=job,
                        processed_filename=f"{file.name}_page_{page_num}",
                        page_number=page_num,
                        image_width=1920,  # 임시 값
                        image_height=1080,  # 임시 값
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
    def progress(self, request, pk=None):
        """분석 진행률 조회"""
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
    def models(self, request):
        """사용 가능한 모델 목록 조회"""
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
