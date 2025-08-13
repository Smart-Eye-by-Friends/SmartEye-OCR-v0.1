"""
Celery Task Services

Celery 작업을 위한 서비스 레이어
"""

import logging
import traceback
from typing import Dict, Any, Optional, Callable
from dataclasses import dataclass
from django.db import transaction
from celery.exceptions import Retry

from .models import AnalysisJob, AnalysisResult
from .notifications import analysis_notifier
from core.lam.service import LAMService
from core.tspm.service import TSPMService
from core.cim.service import CIMService
from utils.enhanced_logging import get_pipeline_logger, pipeline_stage_context
from utils.performance_monitor import get_performance_optimizer

logger = logging.getLogger(__name__)


@dataclass
class ProcessingStage:
    """처리 단계 정보"""
    name: str
    start_progress: int
    end_progress: int
    description: str


@dataclass
class ProcessingOptions:
    """처리 옵션"""
    model_choice: str = 'yolo11n-doclay'
    enable_ocr: bool = True
    enable_description: bool = True
    visualization_type: str = 'comparison'
    
    @classmethod
    def from_dict(cls, data: Optional[Dict[str, Any]]) -> 'ProcessingOptions':
        """딕셔너리에서 ProcessingOptions 생성"""
        if not data:
            return cls()
        
        return cls(
            model_choice=data.get('model_choice', 'yolo11n-doclay'),
            enable_ocr=data.get('enable_ocr', True),
            enable_description=data.get('enable_description', True),
            visualization_type=data.get('visualization_type', 'comparison')
        )


class ProcessingPipeline:
    """분석 처리 파이프라인 관리 클래스"""
    
    # 처리 단계 정의
    STAGES = [
        ProcessingStage('LAM', 10, 30, 'Layout Analysis'),
        ProcessingStage('TSPM', 40, 70, 'Text & Scene Processing'), 
        ProcessingStage('CIM', 80, 95, 'Content Integration')
    ]
    
    def __init__(self, job_id: int, celery_task=None):
        self.job_id = job_id
        self.job_id_str = str(job_id)
        self.celery_task = celery_task
        self.pipeline_logger = get_pipeline_logger()
        self.performance_optimizer = get_performance_optimizer()
        self.job = None
        self.user_id = None
        
    def initialize(self) -> bool:
        """파이프라인 초기화"""
        try:
            self.job = AnalysisJob.objects.get(id=self.job_id)
            self.user_id = str(self.job.user.id)
            
            # 파이프라인 로깅 시작
            self.pipeline_logger.start_job(
                self.job_id_str, 
                self.job.job_name, 
                self.user_id
            )
            
            # 성능 모니터링 시작
            self.performance_optimizer.start_monitoring()
            
            # 작업 상태를 진행 중으로 업데이트
            with transaction.atomic():
                job = AnalysisJob.objects.select_for_update().get(id=self.job_id)
                job.status = 'processing'
                job.save()
            
            logger.info(f"파이프라인 초기화 완료: 작업 {self.job_id}")
            return True
            
        except AnalysisJob.DoesNotExist:
            logger.error(f"작업을 찾을 수 없습니다: {self.job_id}")
            return False
        except Exception as e:
            logger.error(f"파이프라인 초기화 실패: {e}")
            return False
    
    def update_progress(self, stage: str, percentage: int, message: str = ""):
        """진행률 업데이트"""
        try:
            # Celery 상태 업데이트
            if self.celery_task:
                self.celery_task.update_state(
                    state='PROGRESS',
                    meta={
                        'stage': stage,
                        'percentage': percentage,
                        'message': message,
                        'job_id': self.job_id
                    }
                )
            
            # WebSocket 알림 전송
            analysis_notifier.send_progress_update(
                user_id=self.user_id,
                job_id=self.job_id,
                stage=stage,
                percentage=percentage,
                message=message
            )
            
            logger.info(f"진행률 업데이트: {stage} - {percentage}% - {message}")
            
        except Exception as e:
            logger.warning(f"진행률 업데이트 실패: {e}")
    
    def execute_lam_stage(self, options: ProcessingOptions) -> Dict[str, Any]:
        """LAM 단계 실행"""
        stage = self.STAGES[0]  # LAM 단계
        
        self.update_progress(stage.name, stage.start_progress, f'{stage.description} 시작')
        
        with pipeline_stage_context(self.job_id_str, stage.name) as stage_logger:
            try:
                lam_service = LAMService(model_choice=options.model_choice)
                result = lam_service.process_job(self.job_id)
                
                stage_logger.complete_stage(
                    self.job_id_str, 
                    stage.name, 
                    {'status': 'success', 'model': options.model_choice}
                )
                
                self.update_progress(stage.name, stage.end_progress, f'{stage.description} 완료')
                return result
                
            except Exception as e:
                stage_logger.fail_stage(self.job_id_str, stage.name, str(e))
                raise
    
    def execute_tspm_stage(self, options: ProcessingOptions, lam_result: Dict[str, Any]) -> Dict[str, Any]:
        """TSPM 단계 실행"""
        stage = self.STAGES[1]  # TSPM 단계
        
        self.update_progress(stage.name, stage.start_progress, f'{stage.description} 시작')
        
        with pipeline_stage_context(self.job_id_str, stage.name) as stage_logger:
            try:
                tspm_service = TSPMService()
                result = tspm_service.process_job(
                    job_id=self.job_id,
                    layout_result=lam_result,
                    enable_ocr=options.enable_ocr,
                    enable_description=options.enable_description
                )
                
                stage_logger.complete_stage(
                    self.job_id_str, 
                    stage.name,
                    {
                        'status': 'success',
                        'enable_ocr': options.enable_ocr,
                        'enable_description': options.enable_description
                    }
                )
                
                self.update_progress(stage.name, stage.end_progress, f'{stage.description} 완료')
                return result
                
            except Exception as e:
                stage_logger.fail_stage(self.job_id_str, stage.name, str(e))
                raise
    
    def execute_cim_stage(self, options: ProcessingOptions, lam_result: Dict[str, Any], 
                         tspm_result: Dict[str, Any]) -> Dict[str, Any]:
        """CIM 단계 실행"""
        stage = self.STAGES[2]  # CIM 단계
        
        self.update_progress(stage.name, stage.start_progress, f'{stage.description} 시작')
        
        with pipeline_stage_context(self.job_id_str, stage.name) as stage_logger:
            try:
                cim_service = CIMService()
                result = cim_service.integrate_results(
                    job_id=self.job_id,
                    lam_result=lam_result,
                    tspm_result=tspm_result,
                    output_format=['json', 'pdf', 'xml'],
                    visualization_type=options.visualization_type
                )
                
                stage_logger.complete_stage(
                    self.job_id_str,
                    stage.name,
                    {
                        'status': 'success',
                        'output_formats': ['json', 'pdf', 'xml'],
                        'visualization_type': options.visualization_type
                    }
                )
                
                self.update_progress(stage.name, stage.end_progress, f'{stage.description} 완료')
                return result
                
            except Exception as e:
                stage_logger.fail_stage(self.job_id_str, stage.name, str(e))
                raise
    
    def finalize_job(self, final_result: Dict[str, Any]):
        """작업 최종화"""
        try:
            self.update_progress('완료', 100, '분석 완료')
            
            # 작업 상태를 완료로 업데이트
            with transaction.atomic():
                job = AnalysisJob.objects.select_for_update().get(id=self.job_id)
                job.status = 'completed'
                job.save()
            
            # 결과 저장 (CIM 서비스에서 이미 생성되었지만, 상태 확인)
            try:
                AnalysisResult.objects.get(job_id=self.job_id)
                logger.info(f"분석 결과가 이미 저장됨: 작업 {self.job_id}")
            except AnalysisResult.DoesNotExist:
                logger.warning(f"분석 결과가 저장되지 않았습니다: 작업 {self.job_id}")
            
            # 완료 알림 전송
            analysis_notifier.send_completion_notification(
                user_id=self.user_id,
                job_id=self.job_id,
                result=final_result
            )
            
            # 파이프라인 로깅 완료
            self.pipeline_logger.complete_job(
                self.job_id_str,
                {'status': 'success', 'final_result': final_result}
            )
            
            logger.info(f"완전 분석 파이프라인 완료: 작업 {self.job_id}")
            
        except Exception as e:
            logger.error(f"작업 최종화 실패: {e}")
            raise
    
    def handle_error(self, error: Exception, stage: str = None):
        """에러 처리"""
        try:
            error_message = str(error)
            
            # 작업 상태를 실패로 업데이트
            with transaction.atomic():
                job = AnalysisJob.objects.select_for_update().get(id=self.job_id)
                job.status = 'failed'
                job.error_message = error_message
                job.save()
            
            # 실패 알림 전송
            analysis_notifier.send_error_notification(
                user_id=self.user_id,
                job_id=self.job_id,
                error_message=error_message,
                stage=stage
            )
            
            # 파이프라인 로깅 실패
            self.pipeline_logger.fail_job(
                self.job_id_str,
                {
                    'error': error_message,
                    'stage': stage,
                    'traceback': traceback.format_exc()
                }
            )
            
            logger.error(f"분석 파이프라인 실패: 작업 {self.job_id}, 단계: {stage}, 오류: {error_message}")
            
        except Exception as e:
            logger.critical(f"에러 처리 중 추가 오류 발생: {e}")


class IndividualAnalysisRunner:
    """개별 분석 실행기"""
    
    ANALYSIS_SERVICES = {
        'lam': LAMService,
        'tspm': TSPMService,
        'cim': CIMService
    }
    
    def __init__(self, job_id: int, analysis_type: str, model_choice: str = 'yolo11n-doclay'):
        self.job_id = job_id
        self.analysis_type = analysis_type
        self.model_choice = model_choice
        self.pipeline_logger = get_pipeline_logger()
        
    def validate_analysis_type(self) -> bool:
        """분석 타입 유효성 검증"""
        return self.analysis_type in self.ANALYSIS_SERVICES
    
    def execute(self) -> Dict[str, Any]:
        """개별 분석 실행"""
        if not self.validate_analysis_type():
            raise ValueError(f"지원하지 않는 분석 타입: {self.analysis_type}")
        
        job_id_str = str(self.job_id)
        
        try:
            # 작업 정보 가져오기
            job = AnalysisJob.objects.get(id=self.job_id)
            user_id = str(job.user.id)
            
            # 로깅 시작
            self.pipeline_logger.start_individual_analysis(
                job_id_str,
                self.analysis_type,
                user_id
            )
            
            logger.info(f"개별 분석 시작: {self.analysis_type} - 작업 {self.job_id}")
            
            # 서비스 인스턴스 생성 및 실행
            service_class = self.ANALYSIS_SERVICES[self.analysis_type]
            
            if self.analysis_type == 'lam':
                service = service_class(model_choice=self.model_choice)
                result = service.process_job(self.job_id)
            elif self.analysis_type == 'tspm':
                service = service_class()
                # TSPM은 LAM 결과가 필요하므로 이를 확인
                result = service.process_job(self.job_id)
            elif self.analysis_type == 'cim':
                service = service_class()
                # CIM은 LAM과 TSPM 결과가 모두 필요
                result = service.integrate_results(self.job_id)
            
            # 로깅 완료
            self.pipeline_logger.complete_individual_analysis(
                job_id_str,
                self.analysis_type,
                {'status': 'success', 'result': result}
            )
            
            logger.info(f"개별 분석 완료: {self.analysis_type} - 작업 {self.job_id}")
            return result
            
        except AnalysisJob.DoesNotExist:
            error_msg = f"작업을 찾을 수 없습니다: {self.job_id}"
            logger.error(error_msg)
            raise ValueError(error_msg)
        except Exception as e:
            error_msg = f"{self.analysis_type} 분석 실패: {str(e)}"
            
            # 에러 로깅
            self.pipeline_logger.fail_individual_analysis(
                job_id_str,
                self.analysis_type,
                {
                    'error': error_msg,
                    'traceback': traceback.format_exc()
                }
            )
            
            logger.error(error_msg)
            raise


class TaskRetryManager:
    """태스크 재시도 관리자"""
    
    DEFAULT_RETRY_CONFIG = {
        'max_retries': 3,
        'countdown': 60,
        'backoff': True,
        'jitter': True
    }
    
    @staticmethod
    def should_retry(exception: Exception) -> bool:
        """재시도 가능한 예외인지 확인"""
        # 임시적인 오류들 (네트워크, 리소스 부족 등)
        retryable_exceptions = [
            'TimeoutError',
            'ConnectionError',
            'MemoryError',
            'ResourceTemporaryUnavailable'
        ]
        
        exception_name = type(exception).__name__
        return exception_name in retryable_exceptions
    
    @staticmethod
    def calculate_retry_delay(retry_count: int, base_delay: int = 60) -> int:
        """재시도 지연 시간 계산 (지수 백오프)"""
        import random
        
        delay = base_delay * (2 ** retry_count)
        # 지터 추가 (±25%)
        jitter = random.uniform(0.75, 1.25)
        
        return int(delay * jitter)
    
    @staticmethod
    def create_retry_exception(original_exception: Exception, retry_count: int, 
                             max_retries: int, countdown: int) -> Retry:
        """재시도 예외 생성"""
        return Retry(
            f"작업 재시도 {retry_count}/{max_retries}: {str(original_exception)}",
            countdown=countdown
        )


class TaskErrorHandler:
    """태스크 에러 핸들러"""
    
    @staticmethod
    def handle_critical_error(job_id: int, error: Exception, stage: str = None):
        """치명적 오류 처리"""
        try:
            # 작업을 실패 상태로 설정
            with transaction.atomic():
                job = AnalysisJob.objects.select_for_update().get(id=job_id)
                job.status = 'failed'
                job.error_message = str(error)
                job.save()
            
            # 관리자에게 알림 (필요시)
            TaskErrorHandler._notify_administrators(job_id, error, stage)
            
            logger.critical(f"치명적 오류 발생 - 작업 {job_id}, 단계: {stage}, 오류: {str(error)}")
            
        except Exception as e:
            logger.critical(f"오류 처리 중 추가 오류: {e}")
    
    @staticmethod
    def _notify_administrators(job_id: int, error: Exception, stage: str = None):
        """관리자 알림 (이메일, 슬랙 등)"""
        # 실제 구현에서는 이메일이나 슬랙 알림을 보낼 수 있음
        logger.warning(f"관리자 알림 필요: 작업 {job_id} 실패")
    
    @staticmethod
    def categorize_error(error: Exception) -> str:
        """오류 분류"""
        error_type = type(error).__name__
        error_message = str(error).lower()
        
        if 'memory' in error_message or error_type == 'MemoryError':
            return 'MEMORY_ERROR'
        elif 'timeout' in error_message or error_type == 'TimeoutError':
            return 'TIMEOUT_ERROR'
        elif 'connection' in error_message:
            return 'CONNECTION_ERROR'
        elif 'permission' in error_message:
            return 'PERMISSION_ERROR'
        elif 'file' in error_message and ('not found' in error_message or 'missing' in error_message):
            return 'FILE_NOT_FOUND'
        else:
            return 'UNKNOWN_ERROR'