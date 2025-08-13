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


@shared_task(bind=True, autoretry_for=(Exception,), **TaskRetryManager.DEFAULT_RETRY_CONFIG)
def process_complete_analysis(self, job_id: int, processing_options: Optional[Dict[str, Any]] = None):
    """완전한 분석 파이프라인을 비동기로 처리하는 Celery 작업 (리팩토링된 버전)"""
    
    pipeline = ProcessingPipeline(job_id, celery_task=self)
    
    try:
        # 파이프라인 초기화
        if not pipeline.initialize():
            raise ValueError(f"파이프라인 초기화 실패: 작업 {job_id}")
        
        # 처리 옵션 설정
        options = ProcessingOptions.from_dict(processing_options)
        
        logger.info(f"완전 분석 파이프라인 시작: 작업 {job_id}")
        
        # 단계별 실행
        lam_result = pipeline.execute_lam_stage(options)
        tspm_result = pipeline.execute_tspm_stage(options, lam_result)
        final_result = pipeline.execute_cim_stage(options, lam_result, tspm_result)
        
        # 작업 최종화
        pipeline.finalize_job(final_result)
        
        return {
            'status': 'success',
            'job_id': job_id,
            'result': final_result
        }
        
    except Exception as e:
        # 재시도 가능 여부 확인
        if TaskRetryManager.should_retry(e) and self.request.retries < self.max_retries:
            retry_delay = TaskRetryManager.calculate_retry_delay(self.request.retries)
            logger.warning(f"작업 재시도 예정: {job_id}, 지연: {retry_delay}초")
            
            raise self.retry(countdown=retry_delay, exc=e)
        
        # 재시도 불가능하거나 최대 재시도 횟수 초과
        pipeline.handle_error(e)
        TaskErrorHandler.handle_critical_error(job_id, e)
        
        logger.error(f"완전 분석 파이프라인 최종 실패: 작업 {job_id}, 오류: {str(e)}")
        raise


@shared_task(bind=True, autoretry_for=(Exception,), max_retries=2)
def process_individual_analysis(self, job_id: int, analysis_type: str, model_choice: str = 'yolo11n-doclay'):
    """개별 모듈 분석을 처리하는 Celery 작업 (리팩토링된 버전)"""
    
    runner = IndividualAnalysisRunner(job_id, analysis_type, model_choice)
    
    try:
        # 분석 타입 검증
        if not runner.validate_analysis_type():
            raise ValueError(f"지원하지 않는 분석 타입: {analysis_type}")
        
        logger.info(f"개별 분석 시작: {analysis_type} - 작업 {job_id}")
        
        # 분석 실행
        result = runner.execute()
        
        return {
            'status': 'success',
            'job_id': job_id,
            'analysis_type': analysis_type,
            'result': result
        }
        
    except Exception as e:
        # 재시도 가능 여부 확인
        if TaskRetryManager.should_retry(e) and self.request.retries < self.max_retries:
            retry_delay = TaskRetryManager.calculate_retry_delay(self.request.retries)
            logger.warning(f"개별 분석 재시도 예정: {analysis_type} - {job_id}, 지연: {retry_delay}초")
            
            raise self.retry(countdown=retry_delay, exc=e)
        
        # 재시도 불가능하거나 최대 재시도 횟수 초과
        error_category = TaskErrorHandler.categorize_error(e)
        TaskErrorHandler.handle_critical_error(job_id, e, stage=analysis_type)
        
        logger.error(f"개별 분석 최종 실패: {analysis_type} - 작업 {job_id}, 분류: {error_category}, 오류: {str(e)}")
        raise


@shared_task
def cleanup_failed_jobs():
    """실패한 작업들 정리 (정기 실행용)"""
    try:
        from datetime import datetime, timedelta
        
        # 24시간 이상 된 실패 작업들 찾기
        cutoff_time = datetime.now() - timedelta(hours=24)
        failed_jobs = AnalysisJob.objects.filter(
            status='failed',
            created_at__lt=cutoff_time
        )
        
        cleanup_count = 0
        for job in failed_jobs:
            try:
                # 관련된 파일들 정리 (필요시)
                # job.cleanup_files()  # 실제 구현 필요
                
                # 로그 정리
                logger.info(f"실패한 작업 정리: {job.id}")
                cleanup_count += 1
                
            except Exception as e:
                logger.warning(f"작업 정리 실패: {job.id} - {e}")
        
        logger.info(f"정리된 실패 작업 수: {cleanup_count}")
        return {'cleaned_up': cleanup_count}
        
    except Exception as e:
        logger.error(f"작업 정리 중 오류: {e}")
        raise


@shared_task
def generate_analysis_report(user_id: int, date_range: Dict[str, str] = None):
    """사용자별 분석 리포트 생성 (비동기)"""
    try:
        from datetime import datetime, timedelta
        from .services import AnalysisStatisticsService
        
        # 날짜 범위 설정
        if date_range:
            start_date = datetime.fromisoformat(date_range['start'])
            end_date = datetime.fromisoformat(date_range['end'])
        else:
            end_date = datetime.now()
            start_date = end_date - timedelta(days=30)  # 기본 30일
        
        # 통계 수집
        jobs = AnalysisJob.objects.filter(
            user_id=user_id,
            created_at__gte=start_date,
            created_at__lte=end_date
        )
        
        report_data = {
            'user_id': user_id,
            'period': {
                'start': start_date.isoformat(),
                'end': end_date.isoformat()
            },
            'summary': {
                'total_jobs': jobs.count(),
                'completed': jobs.filter(status='completed').count(),
                'failed': jobs.filter(status='failed').count(),
                'processing': jobs.filter(status='processing').count(),
                'cancelled': jobs.filter(status='cancelled').count()
            },
            'models_used': AnalysisStatisticsService.get_model_usage_stats(),
            'performance': AnalysisStatisticsService.get_user_analysis_stats(user_id)
        }
        
        # 리포트 저장 (필요시 파일로 저장하거나 데이터베이스에 저장)
        logger.info(f"분석 리포트 생성 완료: 사용자 {user_id}")
        
        return report_data
        
    except Exception as e:
        logger.error(f"분석 리포트 생성 실패: 사용자 {user_id}, 오류: {e}")
        raise


@shared_task
def health_check_services():
    """서비스 상태 점검 (정기 실행용)"""
    try:
        health_status = {
            'timestamp': datetime.now().isoformat(),
            'services': {}
        }
        
        # LAM 서비스 상태 점검
        try:
            from core.lam.service import LAMService
            lam_service = LAMService()
            # 간단한 상태 점검 로직
            health_status['services']['lam'] = {'status': 'healthy', 'message': 'LAM service is operational'}
        except Exception as e:
            health_status['services']['lam'] = {'status': 'unhealthy', 'message': str(e)}
        
        # TSPM 서비스 상태 점검
        try:
            from core.tspm.service import TSPMService
            tspm_service = TSPMService()
            # 간단한 상태 점검 로직
            health_status['services']['tspm'] = {'status': 'healthy', 'message': 'TSPM service is operational'}
        except Exception as e:
            health_status['services']['tspm'] = {'status': 'unhealthy', 'message': str(e)}
        
        # CIM 서비스 상태 점검
        try:
            from core.cim.service import CIMService
            cim_service = CIMService()
            # 간단한 상태 점검 로직
            health_status['services']['cim'] = {'status': 'healthy', 'message': 'CIM service is operational'}
        except Exception as e:
            health_status['services']['cim'] = {'status': 'unhealthy', 'message': str(e)}
        
        # 전체 상태 판단
        unhealthy_services = [name for name, status in health_status['services'].items() if status['status'] != 'healthy']
        
        if unhealthy_services:
            health_status['overall_status'] = 'degraded'
            health_status['unhealthy_services'] = unhealthy_services
            logger.warning(f"일부 서비스가 비정상 상태입니다: {unhealthy_services}")
        else:
            health_status['overall_status'] = 'healthy'
            logger.info("모든 서비스가 정상 작동 중입니다.")
        
        return health_status
        
    except Exception as e:
        logger.error(f"서비스 상태 점검 실패: {e}")
        raise


# 정기 작업 등록을 위한 Celery Beat 설정 예시
"""
CELERYBEAT_SCHEDULE = {
    'cleanup-failed-jobs': {
        'task': 'apps.analysis.tasks.cleanup_failed_jobs',
        'schedule': crontab(hour=2, minute=0),  # 매일 오전 2시
    },
    'health-check-services': {
        'task': 'apps.analysis.tasks.health_check_services',
        'schedule': crontab(minute='*/15'),  # 15분마다
    },
}
"""