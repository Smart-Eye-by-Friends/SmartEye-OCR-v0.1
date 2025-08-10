"""
기본 서비스 클래스 및 추상화
"""
import logging
from abc import ABC, abstractmethod
from typing import Any, List, Optional, Dict
from django.db.models import F


class BaseService(ABC):
    """
    모든 서비스 클래스의 공통 기본 클래스
    - 리소스 관리
    - 로깅
    - 공통 유틸리티 메서드
    """
    
    def __init__(self):
        self.logger = logging.getLogger(f'smarteye.{self.__class__.__name__.lower()}')
        self._resources = []
        self._initialized = False
    
    def add_resource(self, resource):
        """
        관리할 리소스 추가
        
        Args:
            resource: 정리가 필요한 리소스 (파일, 연결 등)
        """
        if resource and resource not in self._resources:
            self._resources.append(resource)
            self.logger.debug(f"리소스 추가: {type(resource).__name__}")
    
    def cleanup(self):
        """
        모든 리소스 정리
        """
        cleanup_count = 0
        
        for resource in self._resources[:]:  # 복사본으로 순회
            try:
                if hasattr(resource, 'close'):
                    resource.close()
                    cleanup_count += 1
                elif hasattr(resource, 'cleanup'):
                    resource.cleanup()
                    cleanup_count += 1
                elif hasattr(resource, 'disconnect'):
                    resource.disconnect()
                    cleanup_count += 1
                    
            except Exception as e:
                self.logger.warning(f"리소스 정리 실패 {type(resource).__name__}: {e}")
        
        self._resources.clear()
        
        if cleanup_count > 0:
            self.logger.debug(f"{cleanup_count}개 리소스 정리 완료")
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        try:
            self.cleanup()
        except Exception:
            pass  # 소멸자에서는 예외를 무시
    
    def __enter__(self):
        """Context Manager 진입"""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context Manager 종료 시 정리"""
        self.cleanup()
    
    @abstractmethod
    def process(self, *args, **kwargs) -> Dict[str, Any]:
        """
        주요 처리 로직 (서브클래스에서 구현)
        
        Returns:
            Dict: 처리 결과
        """
        pass
    
    def get_logger(self) -> logging.Logger:
        """로거 반환"""
        return self.logger
    
    def log_performance(self, operation_name: str, duration_ms: float):
        """
        성능 로깅
        
        Args:
            operation_name: 작업명
            duration_ms: 소요 시간 (밀리초)
        """
        if duration_ms > 1000:  # 1초 이상
            self.logger.warning(f"{operation_name} 느린 작업: {duration_ms:.2f}ms")
        else:
            self.logger.debug(f"{operation_name} 완료: {duration_ms:.2f}ms")


class ProcessingService(BaseService):
    """
    데이터 처리 서비스를 위한 기본 클래스
    """
    
    def __init__(self):
        super().__init__()
        self._processed_count = 0
        self._error_count = 0
    
    def increment_processed(self):
        """처리된 항목 수 증가"""
        self._processed_count += 1
    
    def increment_errors(self):
        """오류 수 증가"""
        self._error_count += 1
    
    def get_statistics(self) -> Dict[str, int]:
        """처리 통계 반환"""
        return {
            'processed': self._processed_count,
            'errors': self._error_count,
            'total': self._processed_count + self._error_count
        }
    
    def reset_statistics(self):
        """통계 초기화"""
        self._processed_count = 0
        self._error_count = 0
    
    def log_statistics(self):
        """통계 로깅"""
        stats = self.get_statistics()
        self.logger.info(
            f"처리 완료: {stats['processed']}개 성공, "
            f"{stats['errors']}개 실패 (총 {stats['total']}개)"
        )


class AsyncProcessingService(ProcessingService):
    """
    비동기 처리 서비스를 위한 기본 클래스
    """
    
    def __init__(self, max_concurrent_jobs: int = 3):
        super().__init__()
        self.max_concurrent_jobs = max_concurrent_jobs
        self._active_jobs = set()
    
    def add_job(self, job_id: str):
        """활성 작업 추가"""
        self._active_jobs.add(job_id)
        self.logger.debug(f"작업 시작: {job_id} ({len(self._active_jobs)}개 활성)")
    
    def remove_job(self, job_id: str):
        """활성 작업 제거"""
        self._active_jobs.discard(job_id)
        self.logger.debug(f"작업 완료: {job_id} ({len(self._active_jobs)}개 활성)")
    
    def can_accept_job(self) -> bool:
        """새 작업 수용 가능 여부"""
        return len(self._active_jobs) < self.max_concurrent_jobs
    
    def get_active_job_count(self) -> int:
        """활성 작업 수 반환"""
        return len(self._active_jobs)


class CacheableService(BaseService):
    """
    캐시 기능을 제공하는 서비스 기본 클래스
    """
    
    def __init__(self, cache_ttl: int = 3600):
        super().__init__()
        self.cache_ttl = cache_ttl
        self._cache = {}
    
    def get_cache_key(self, *args, **kwargs) -> str:
        """
        캐시 키 생성 (서브클래스에서 재정의 가능)
        
        Returns:
            str: 캐시 키
        """
        import hashlib
        
        key_data = f"{args}_{kwargs}".encode()
        return f"{self.__class__.__name__}_{hashlib.md5(key_data).hexdigest()}"
    
    def get_from_cache(self, key: str) -> Optional[Any]:
        """캐시에서 값 조회"""
        import time
        
        if key in self._cache:
            data, timestamp = self._cache[key]
            if time.time() - timestamp < self.cache_ttl:
                self.logger.debug(f"캐시 히트: {key}")
                return data
            else:
                # 만료된 캐시 제거
                del self._cache[key]
                self.logger.debug(f"캐시 만료: {key}")
        
        return None
    
    def set_cache(self, key: str, value: Any):
        """캐시에 값 저장"""
        import time
        
        self._cache[key] = (value, time.time())
        self.logger.debug(f"캐시 저장: {key}")
    
    def clear_cache(self):
        """캐시 비우기"""
        cache_size = len(self._cache)
        self._cache.clear()
        self.logger.debug(f"캐시 정리: {cache_size}개 항목 삭제")
    
    def cleanup(self):
        """리소스 정리 시 캐시도 정리"""
        self.clear_cache()
        super().cleanup()


class ModelService(BaseService):
    """
    ML/AI 모델을 사용하는 서비스를 위한 기본 클래스
    """
    
    def __init__(self, model_path: str = None):
        super().__init__()
        self.model_path = model_path
        self._model = None
        self._model_loaded = False
    
    def load_model(self):
        """모델 로드 (서브클래스에서 구현)"""
        raise NotImplementedError("Subclasses must implement load_model()")
    
    def unload_model(self):
        """모델 언로드"""
        if self._model is not None:
            try:
                # GPU 메모리 정리
                import gc
                
                if hasattr(self._model, 'cpu'):
                    self._model.cpu()
                
                del self._model
                self._model = None
                
                # PyTorch GPU 메모리 정리
                try:
                    import torch
                    if torch.cuda.is_available():
                        torch.cuda.empty_cache()
                        torch.cuda.synchronize()
                except ImportError:
                    pass
                
                gc.collect()
                self._model_loaded = False
                self.logger.info("모델 언로드 완료")
                
            except Exception as e:
                self.logger.error(f"모델 언로드 실패: {e}")
    
    def is_model_loaded(self) -> bool:
        """모델 로드 상태 확인"""
        return self._model_loaded and self._model is not None
    
    def cleanup(self):
        """리소스 정리 시 모델도 언로드"""
        self.unload_model()
        super().cleanup()


class LoggerMixin:
    """
    로깅 기능을 제공하는 Mixin 클래스
    """
    
    @property
    def logger(self) -> logging.Logger:
        """로거 프로퍼티"""
        if not hasattr(self, '_logger'):
            self._logger = logging.getLogger(f'smarteye.{self.__class__.__name__.lower()}')
        return self._logger
    
    def log_method_call(self, method_name: str, *args, **kwargs):
        """메서드 호출 로깅"""
        self.logger.debug(f"{method_name} 호출: args={args[:2]}{'...' if len(args) > 2 else ''}")
    
    def log_performance_warning(self, operation: str, duration_ms: float, threshold_ms: float = 1000):
        """성능 경고 로깅"""
        if duration_ms > threshold_ms:
            self.logger.warning(f"{operation} 성능 경고: {duration_ms:.2f}ms (임계값: {threshold_ms}ms)")


# 서비스 인스턴스를 위한 레지스트리
class ServiceRegistry:
    """서비스 인스턴스 관리를 위한 레지스트리"""
    
    _instances = {}
    
    @classmethod
    def register(cls, service_name: str, service_instance: BaseService):
        """서비스 등록"""
        cls._instances[service_name] = service_instance
    
    @classmethod
    def get(cls, service_name: str) -> Optional[BaseService]:
        """서비스 조회"""
        return cls._instances.get(service_name)
    
    @classmethod
    def cleanup_all(cls):
        """모든 등록된 서비스 정리"""
        for name, service in cls._instances.items():
            try:
                service.cleanup()
            except Exception as e:
                logging.getLogger('smarteye.registry').error(
                    f"서비스 {name} 정리 실패: {e}"
                )
        cls._instances.clear()