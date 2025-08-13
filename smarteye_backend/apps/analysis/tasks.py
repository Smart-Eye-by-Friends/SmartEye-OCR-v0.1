"""
Celery 작업 정의 - 전체 SmartEye 파이프라인 처리 (리팩토링된 버전)
"""

from celery import shared_task
import logging
import traceback
from typing import Optional, Dict, Any

from .task_services import (
    ProcessingPipeline, 
    ProcessingOptions, 
    IndividualAnalysisRunner,
    TaskRetryManager,
    TaskErrorHandler
)
from .models import AnalysisJob

logger = logging.getLogger(__name__)


@shared_task(bind=True)
def process_complete_analysis(self, job_id: int, processing_options: Optional[Dict[str, Any]] = None):
    """완전한 분석 파이프라인을 비동기로 처리하는 Celery 작업 (향상된 로깅 포함)"""
    job_id_str = str(job_id)
    
    try:
        # 작업 정보 가져오기
        job = AnalysisJob.objects.get(id=job_id)
        user_id = str(job.user.id)
        
        # 파이프라인 로깅 시작
        pipeline_logger.start_job(job_id_str, job.job_name, user_id)
        
        # 성능 모니터링 시작
        performance_optimizer.start_monitoring()
        
        logger.info(f"완전 분석 파이프라인 시작: 작업 {job_id}")
        
        # 작업 상태를 진행 중으로 업데이트
        with transaction.atomic():
            job = AnalysisJob.objects.select_for_update().get(id=job_id)
            job.status = 'processing'
            job.save()
        
        # 진행률 업데이트 함수 (WebSocket 알림 포함)
        def update_progress(stage: str, percentage: int, message: str = ""):
            # Celery 상태 업데이트
            self.update_state(
                state='PROGRESS',
                meta={
                    'stage': stage,
                    'percentage': percentage,
                    'message': message,
                    'job_id': job_id
                }
            )
            
            # WebSocket 알림 전송
            analysis_notifier.send_progress_update(
                user_id=user_id,
                job_id=job_id,
                stage=stage,
                percentage=percentage,
                message=message
            )
            
            logger.info(f"진행률 업데이트: {stage} - {percentage}% - {message}")
        
        # 처리 옵션 기본값 설정
        options = processing_options or {}
        model_choice = options.get('model_choice', 'yolo11n-doclay')
        enable_ocr = options.get('enable_ocr', True)
        enable_description = options.get('enable_description', True)
        visualization_type = options.get('visualization_type', 'comparison')
        
        # 1단계: LAM (Layout Analysis Module) 처리
        update_progress('LAM', 10, 'Layout Analysis 시작')
        with pipeline_stage_context(job_id_str, 'LAM') as stage_logger:
            lam_service = LAMService(model_choice=model_choice)
            lam_result = lam_service.process_job(job_id)
            stage_logger.complete_stage(job_id_str, 'LAM', {'status': 'success', 'model': model_choice})
        update_progress('LAM', 30, 'Layout Analysis 완료')
        
        # 2단계: TSPM (Text & Scene Processing Module) 처리
        update_progress('TSPM', 40, 'Text & Scene Processing 시작')
        with pipeline_stage_context(job_id_str, 'TSPM') as stage_logger:
            tspm_service = TSPMService()
            tspm_result = tspm_service.process_job(
                job_id=job_id,
                layout_result=lam_result,
                enable_ocr=enable_ocr,
                enable_description=enable_description
            )
            stage_logger.complete_stage(job_id_str, 'TSPM', {
                'status': 'success', 
                'ocr_enabled': enable_ocr,
                'description_enabled': enable_description
            })
        update_progress('TSPM', 70, 'Text & Scene Processing 완료')
        
        # 3단계: CIM (Content Integration Module) 처리
        update_progress('CIM', 80, 'Content Integration 시작')
        with pipeline_stage_context(job_id_str, 'CIM') as stage_logger:
            cim_service = CIMService()
            final_result = cim_service.process_job(
                job_id=job_id,
                lam_result=lam_result,
                tspm_result=tspm_result,
                visualization_type=visualization_type
            )
            stage_logger.complete_stage(job_id_str, 'CIM', {
                'status': 'success',
                'visualization_type': visualization_type
            })
        update_progress('CIM', 95, 'Content Integration 완료')
        
        # 결과 저장
        with transaction.atomic():
            job = AnalysisJob.objects.select_for_update().get(id=job_id)
            job.status = 'completed'
            job.save()
            
            # 결과 저장
            result_data = {
                'lam_result': lam_result,
                'tspm_result': tspm_result,
                'final_result': final_result,
                'processing_options': options
            }
            
            AnalysisResult.objects.create(
                job=job,
                result_data=result_data,
                processing_time=final_result.get('processing_time', 0),
                detection_count=len(lam_result.get('detections', [])) if lam_result.get('detections') else 0,
                confidence_score=final_result.get('confidence_score', 0.0)
            )
        
        # 리소스 정리
        lam_service.cleanup()
        tspm_service.cleanup()
        cim_service.cleanup()
        
        # 완료 알림
        update_progress('완료', 100, '전체 분석 파이프라인 완료')
        
        final_response = {
            'status': 'success',
            'job_id': job_id,
            'final_result': final_result,
            'summary': {
                'detection_count': len(lam_result.get('detections', [])) if lam_result.get('detections') else 0,
                'processing_time': final_result.get('processing_time', 0),
                'confidence_score': final_result.get('confidence_score', 0.0)
            }
        }
        
        # 파이프라인 완료 로깅
        pipeline_logger.complete_job(job_id_str, True, final_response)
        
        # 성능 리포트 생성
        performance_report_path = performance_optimizer.export_metrics_report()
        pipeline_report_path = pipeline_logger.export_pipeline_report(job_id_str)
        
        logger.info(f"성능 리포트 생성: {performance_report_path}")
        logger.info(f"파이프라인 리포트 생성: {pipeline_report_path}")
        
        # WebSocket 완료 알림 전송
        analysis_notifier.send_completion_notification(
            user_id=user_id,
            job_id=job_id,
            result=final_response
        )
        
        logger.info(f"완전 분석 파이프라인 완료: 작업 {job_id}")
        return final_response
        
    except Exception as e:
        # 향상된 에러 로깅
        error_context = {
            'job_id': job_id_str,
            'processing_options': processing_options,
            'error_type': type(e).__name__,
            'error_trace': traceback.format_exc()
        }
        
        # 파이프라인 에러 로깅
        pipeline_logger.log_error(job_id_str, 'PIPELINE', e, error_context)
        pipeline_logger.complete_job(job_id_str, False, {'error': str(e)})
        
        # 성능 및 에러 리포트 생성
        performance_report_path = performance_optimizer.export_metrics_report()
        error_report_path = pipeline_logger.error_tracker.export_error_report()
        
        logger.error(f"완전 분석 파이프라인 실패: 작업 {job_id} - {e}")
        logger.error(f"에러 리포트 생성: {error_report_path}")
        logger.error(f"성능 리포트 생성: {performance_report_path}")
        
        # 실패 상태로 업데이트
        try:
            with transaction.atomic():
                job = AnalysisJob.objects.select_for_update().get(id=job_id)
                job.status = 'failed'
                job.error_message = str(e)
                job.save()
                user_id = job.user.id
                
            # WebSocket 실패 알림 전송
            analysis_notifier.send_failure_notification(
                user_id=user_id,
                job_id=job_id,
                error=str(e)
            )
            
        except Exception as update_error:
            logger.error(f"작업 상태 업데이트 실패: {update_error}")
            pipeline_logger.log_error(job_id_str, 'DB_UPDATE', update_error)
        
        # 작업 실패 상태 업데이트
        self.update_state(
            state='FAILURE',
            meta={
                'error': str(e),
                'traceback': traceback.format_exc(),
                'job_id': job_id
            }
        )
        raise


@shared_task
def process_individual_analysis(job_id: int, analysis_type: str, model_choice: Optional[str] = None):
    """개별 분석 작업을 처리하는 Celery 작업 (LAM만 또는 특정 모듈만)"""
    logger.info(f"개별 분석 시작: 작업 {job_id}, 타입: {analysis_type}")
    
    try:
        result = None
        
        if analysis_type == 'lam':
            # LAM만 처리
            lam_service = LAMService(model_choice=model_choice or 'yolo11n-doclay')
            result = lam_service.process_job(job_id)
            lam_service.cleanup()
            
        elif analysis_type == 'tspm':
            # TSPM만 처리 (LAM 결과 필요)
            tspm_service = TSPMService()
            result = tspm_service.process_job(job_id)
            tspm_service.cleanup()
            
        elif analysis_type == 'cim':
            # CIM만 처리 (LAM, TSPM 결과 필요)
            cim_service = CIMService()
            result = cim_service.process_job(job_id)
            cim_service.cleanup()
        
        logger.info(f"개별 분석 완료: 작업 {job_id}, 타입: {analysis_type}")
        return result
        
    except Exception as e:
        logger.error(f"개별 분석 실패: 작업 {job_id}, 타입: {analysis_type} - {e}")
        raise


@shared_task
def cleanup_temp_files():
    """임시 파일 정리 작업"""
    logger.info("임시 파일 정리 작업 시작")
    
    try:
        import os
        import time
        from django.conf import settings
        from core.lam.config import LAMConfig
        
        media_root = settings.MEDIA_ROOT
        temp_prefix = LAMConfig.TEMP_FILE_PREFIX
        
        # 24시간 이상 된 임시 파일 삭제
        current_time = time.time()
        deleted_count = 0
        
        for root, dirs, files in os.walk(media_root):
            for file in files:
                if file.startswith(temp_prefix):
                    file_path = os.path.join(root, file)
                    file_age = current_time - os.path.getctime(file_path)
                    
                    # 24시간 = 86400초
                    if file_age > 86400:
                        try:
                            os.remove(file_path)
                            deleted_count += 1
                            logger.debug(f"임시 파일 삭제: {file_path}")
                        except Exception as e:
                            logger.warning(f"임시 파일 삭제 실패: {file_path} - {e}")
        
        logger.info(f"임시 파일 정리 완료: {deleted_count}개 파일 삭제")
        return {'deleted_files': deleted_count}
        
    except Exception as e:
        logger.error(f"임시 파일 정리 실패: {e}")
        raise
