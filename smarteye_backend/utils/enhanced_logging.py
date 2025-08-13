"""
SmartEye 고급 로깅 시스템

구조화된 로깅, 에러 추적, 성능 메트릭 수집을 제공합니다.
"""

import logging
import json
import traceback
import uuid
from datetime import datetime
from typing import Dict, Any, Optional, List
from dataclasses import dataclass, asdict
from pathlib import Path
from functools import wraps
from contextlib import contextmanager
import threading
import time

from django.conf import settings
from django.utils import timezone


@dataclass
class LogEvent:
    """로그 이벤트 데이터 클래스"""
    timestamp: str
    level: str
    message: str
    logger_name: str
    job_id: Optional[str] = None
    user_id: Optional[str] = None
    request_id: Optional[str] = None
    stage: Optional[str] = None
    duration_ms: Optional[float] = None
    memory_usage_mb: Optional[float] = None
    error_type: Optional[str] = None
    error_trace: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class StructuredLogger:
    """구조화된 로깅을 위한 클래스"""
    
    def __init__(self, name: str):
        self.name = name
        self.logger = logging.getLogger(name)
        self._context = threading.local()
    
    def set_context(self, **kwargs):
        """로깅 컨텍스트 설정"""
        if not hasattr(self._context, 'data'):
            self._context.data = {}
        self._context.data.update(kwargs)
    
    def clear_context(self):
        """로깅 컨텍스트 초기화"""
        if hasattr(self._context, 'data'):
            self._context.data.clear()
    
    def get_context(self) -> Dict[str, Any]:
        """현재 로깅 컨텍스트 반환"""
        if hasattr(self._context, 'data'):
            return self._context.data.copy()
        return {}
    
    def _create_log_event(self, level: str, message: str, 
                         duration_ms: float = None, error: Exception = None,
                         **kwargs) -> LogEvent:
        """로그 이벤트 생성"""
        context = self.get_context()
        
        event = LogEvent(
            timestamp=timezone.now().isoformat(),
            level=level,
            message=message,
            logger_name=self.name,
            job_id=context.get('job_id') or kwargs.get('job_id'),
            user_id=context.get('user_id') or kwargs.get('user_id'),
            request_id=context.get('request_id') or kwargs.get('request_id'),
            stage=context.get('stage') or kwargs.get('stage'),
            duration_ms=duration_ms,
            memory_usage_mb=kwargs.get('memory_usage_mb'),
            metadata={**context, **kwargs}
        )
        
        if error:
            event.error_type = type(error).__name__
            event.error_trace = traceback.format_exc()
        
        return event
    
    def _log_event(self, event: LogEvent):
        """로그 이벤트 출력"""
        log_dict = asdict(event)
        
        # None 값 제거
        log_dict = {k: v for k, v in log_dict.items() if v is not None}
        
        # JSON 형태로 출력
        structured_msg = json.dumps(log_dict, ensure_ascii=False)
        
        # 표준 로거로 출력
        log_level = getattr(logging, event.level.upper())
        self.logger.log(log_level, structured_msg)
    
    def info(self, message: str, **kwargs):
        """정보 로그"""
        event = self._create_log_event('info', message, **kwargs)
        self._log_event(event)
    
    def warning(self, message: str, **kwargs):
        """경고 로그"""
        event = self._create_log_event('warning', message, **kwargs)
        self._log_event(event)
    
    def error(self, message: str, error: Exception = None, **kwargs):
        """에러 로그"""
        event = self._create_log_event('error', message, error=error, **kwargs)
        self._log_event(event)
    
    def debug(self, message: str, **kwargs):
        """디버그 로그"""
        event = self._create_log_event('debug', message, **kwargs)
        self._log_event(event)
    
    def performance(self, message: str, duration_ms: float, **kwargs):
        """성능 로그"""
        event = self._create_log_event('info', message, duration_ms=duration_ms, **kwargs)
        self._log_event(event)


class ErrorTracker:
    """에러 추적 및 분석 클래스"""
    
    def __init__(self):
        self.error_history: List[LogEvent] = []
        self.max_history_size = 1000
        self.lock = threading.Lock()
    
    def track_error(self, error: Exception, context: Dict[str, Any] = None):
        """에러 추적"""
        with self.lock:
            error_event = LogEvent(
                timestamp=timezone.now().isoformat(),
                level='error',
                message=str(error),
                logger_name='error_tracker',
                error_type=type(error).__name__,
                error_trace=traceback.format_exc(),
                metadata=context or {}
            )
            
            self.error_history.append(error_event)
            
            # 히스토리 크기 제한
            if len(self.error_history) > self.max_history_size:
                self.error_history = self.error_history[-self.max_history_size:]
    
    def get_error_summary(self, hours: int = 24) -> Dict[str, Any]:
        """에러 요약 통계"""
        cutoff_time = timezone.now().timestamp() - (hours * 3600)
        
        recent_errors = [
            e for e in self.error_history
            if datetime.fromisoformat(e.timestamp).timestamp() > cutoff_time
        ]
        
        if not recent_errors:
            return {'total_errors': 0, 'error_types': {}, 'recent_errors': []}
        
        error_types = {}
        for error in recent_errors:
            error_type = error.error_type or 'Unknown'
            error_types[error_type] = error_types.get(error_type, 0) + 1
        
        return {
            'total_errors': len(recent_errors),
            'error_types': error_types,
            'most_common_error': max(error_types.items(), key=lambda x: x[1])[0],
            'recent_errors': [asdict(e) for e in recent_errors[-10:]]  # 최근 10개
        }
    
    def export_error_report(self, output_path: str = None) -> str:
        """에러 리포트 내보내기"""
        if not output_path:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            output_path = f"/tmp/smarteye_error_report_{timestamp}.json"
        
        report_data = {
            'generated_at': timezone.now().isoformat(),
            'summary_24h': self.get_error_summary(24),
            'summary_7d': self.get_error_summary(24 * 7),
            'all_errors': [asdict(e) for e in self.error_history]
        }
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        
        return output_path


class PipelineLogger:
    """파이프라인 전용 로거"""
    
    def __init__(self):
        self.structured_logger = StructuredLogger('smarteye.pipeline')
        self.error_tracker = ErrorTracker()
        self.pipeline_states: Dict[str, Dict] = {}
        self.lock = threading.Lock()
    
    def start_job(self, job_id: str, job_name: str, user_id: str = None):
        """작업 시작 로깅"""
        with self.lock:
            self.pipeline_states[job_id] = {
                'job_name': job_name,
                'user_id': user_id,
                'started_at': timezone.now(),
                'stages': {},
                'errors': []
            }
        
        self.structured_logger.set_context(
            job_id=job_id,
            user_id=user_id,
            job_name=job_name
        )
        
        self.structured_logger.info(f"파이프라인 작업 시작: {job_name}")
    
    def start_stage(self, job_id: str, stage: str):
        """단계 시작 로깅"""
        if job_id in self.pipeline_states:
            with self.lock:
                self.pipeline_states[job_id]['stages'][stage] = {
                    'started_at': timezone.now(),
                    'status': 'processing'
                }
        
        self.structured_logger.set_context(job_id=job_id, stage=stage)
        self.structured_logger.info(f"단계 시작: {stage}")
    
    def complete_stage(self, job_id: str, stage: str, result: Dict[str, Any] = None):
        """단계 완료 로깅"""
        if job_id in self.pipeline_states and stage in self.pipeline_states[job_id]['stages']:
            with self.lock:
                stage_info = self.pipeline_states[job_id]['stages'][stage]
                stage_info['completed_at'] = timezone.now()
                stage_info['status'] = 'completed'
                stage_info['duration_ms'] = (
                    stage_info['completed_at'] - stage_info['started_at']
                ).total_seconds() * 1000
                
                if result:
                    stage_info['result'] = result
        
        duration_ms = None
        if job_id in self.pipeline_states:
            stage_info = self.pipeline_states[job_id]['stages'].get(stage, {})
            duration_ms = stage_info.get('duration_ms')
        
        self.structured_logger.performance(
            f"단계 완료: {stage}",
            duration_ms=duration_ms,
            stage_result=result
        )
    
    def log_error(self, job_id: str, stage: str, error: Exception, context: Dict[str, Any] = None):
        """에러 로깅"""
        error_context = {
            'job_id': job_id,
            'stage': stage,
            **(context or {})
        }
        
        # 파이프라인 상태에 에러 추가
        if job_id in self.pipeline_states:
            with self.lock:
                self.pipeline_states[job_id]['errors'].append({
                    'stage': stage,
                    'error_type': type(error).__name__,
                    'error_message': str(error),
                    'timestamp': timezone.now().isoformat()
                })
        
        # 에러 추적
        self.error_tracker.track_error(error, error_context)
        
        # 구조화된 로깅
        self.structured_logger.error(
            f"파이프라인 에러 [{stage}]: {str(error)}",
            error=error,
            **error_context
        )
    
    def complete_job(self, job_id: str, success: bool, final_result: Dict[str, Any] = None):
        """작업 완료 로깅"""
        if job_id not in self.pipeline_states:
            return
        
        with self.lock:
            job_state = self.pipeline_states[job_id]
            job_state['completed_at'] = timezone.now()
            job_state['status'] = 'completed' if success else 'failed'
            job_state['total_duration_ms'] = (
                job_state['completed_at'] - job_state['started_at']
            ).total_seconds() * 1000
            
            if final_result:
                job_state['final_result'] = final_result
        
        self.structured_logger.performance(
            f"파이프라인 작업 {'완료' if success else '실패'}: {job_state['job_name']}",
            duration_ms=job_state['total_duration_ms'],
            success=success,
            error_count=len(job_state['errors'])
        )
        
        # 컨텍스트 정리
        self.structured_logger.clear_context()
    
    def get_job_status(self, job_id: str) -> Optional[Dict[str, Any]]:
        """작업 상태 조회"""
        return self.pipeline_states.get(job_id)
    
    def export_pipeline_report(self, job_id: str = None, output_path: str = None) -> str:
        """파이프라인 리포트 내보내기"""
        if not output_path:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            job_suffix = f"_{job_id}" if job_id else ""
            output_path = f"/tmp/smarteye_pipeline_report{job_suffix}_{timestamp}.json"
        
        if job_id:
            report_data = {
                'job_id': job_id,
                'job_state': self.pipeline_states.get(job_id),
                'generated_at': timezone.now().isoformat()
            }
        else:
            report_data = {
                'all_jobs': self.pipeline_states,
                'error_summary': self.error_tracker.get_error_summary(),
                'generated_at': timezone.now().isoformat()
            }
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False, default=str)
        
        return output_path


# 전역 인스턴스
pipeline_logger = PipelineLogger()


def get_pipeline_logger() -> PipelineLogger:
    """파이프라인 로거 인스턴스 반환"""
    return pipeline_logger


@contextmanager
def pipeline_stage_context(job_id: str, stage: str):
    """파이프라인 단계 컨텍스트 매니저"""
    logger = get_pipeline_logger()
    logger.start_stage(job_id, stage)
    
    try:
        yield logger
        logger.complete_stage(job_id, stage)
    except Exception as e:
        logger.log_error(job_id, stage, e)
        raise


def log_pipeline_errors(job_id: str, stage: str):
    """파이프라인 에러 로깅 데코레이터"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                logger = get_pipeline_logger()
                logger.log_error(job_id, stage, e)
                raise
        return wrapper
    return decorator


def setup_enhanced_logging():
    """고급 로깅 시스템 설정"""
    # 로그 디렉터리 생성
    log_dir = Path('/app/logs')
    log_dir.mkdir(exist_ok=True)
    
    # 구조화된 로깅 설정
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler(log_dir / 'smarteye.log'),
            logging.StreamHandler()
        ]
    )
    
    # 에러 전용 로그 파일
    error_handler = logging.FileHandler(log_dir / 'smarteye_errors.log')
    error_handler.setLevel(logging.ERROR)
    error_handler.setFormatter(logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    ))
    
    logging.getLogger().addHandler(error_handler)
    
    return get_pipeline_logger()


# 사용 예시를 위한 편의 함수
def create_structured_logger(name: str) -> StructuredLogger:
    """구조화된 로거 생성"""
    return StructuredLogger(name)