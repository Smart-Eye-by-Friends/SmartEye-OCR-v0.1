"""
SmartEye 성능 모니터링 유틸리티

메모리 사용량, CPU 사용률, 처리 시간 등을 모니터링하고 최적화를 제공합니다.
"""

import psutil
import time
import logging
import threading
from typing import Dict, Any, Optional, List
from dataclasses import dataclass
from datetime import datetime, timedelta
import json

logger = logging.getLogger(__name__)


@dataclass
class PerformanceMetrics:
    """성능 메트릭 데이터 클래스"""
    timestamp: datetime
    memory_usage_mb: float
    memory_percent: float
    cpu_percent: float
    active_processes: int
    processing_time_ms: Optional[float] = None
    job_id: Optional[str] = None
    stage: Optional[str] = None


class MemoryMonitor:
    """메모리 사용량 모니터링 클래스"""
    
    def __init__(self, threshold_mb: int = 2048, warning_threshold: float = 80.0):
        self.threshold_mb = threshold_mb
        self.warning_threshold = warning_threshold  # 퍼센트
        self.metrics_history: List[PerformanceMetrics] = []
        self.max_history_size = 1000
        
    def get_current_memory_info(self) -> Dict[str, float]:
        """현재 메모리 사용량 정보 반환"""
        memory = psutil.virtual_memory()
        return {
            'total_mb': memory.total / (1024 * 1024),
            'available_mb': memory.available / (1024 * 1024),
            'used_mb': memory.used / (1024 * 1024),
            'percent': memory.percent
        }
    
    def check_memory_pressure(self) -> bool:
        """메모리 압박 상태 확인"""
        memory_info = self.get_current_memory_info()
        return memory_info['percent'] > self.warning_threshold
    
    def get_available_memory_mb(self) -> float:
        """사용 가능한 메모리 용량(MB) 반환"""
        memory = psutil.virtual_memory()
        return memory.available / (1024 * 1024)
    
    def calculate_optimal_batch_size(self, base_batch_size: int = 5) -> int:
        """현재 메모리 상황에 따른 최적 배치 크기 계산"""
        memory_info = self.get_current_memory_info()
        memory_ratio = memory_info['percent'] / 100.0
        
        if memory_ratio < 0.5:  # 50% 미만
            return base_batch_size * 2
        elif memory_ratio < 0.7:  # 70% 미만
            return base_batch_size
        elif memory_ratio < 0.85:  # 85% 미만
            return max(1, base_batch_size // 2)
        else:  # 85% 이상
            return 1
    
    def record_metrics(self, job_id: str = None, stage: str = None, 
                      processing_time_ms: float = None) -> PerformanceMetrics:
        """현재 성능 메트릭 기록"""
        memory_info = self.get_current_memory_info()
        cpu_percent = psutil.cpu_percent(interval=0.1)
        
        metrics = PerformanceMetrics(
            timestamp=datetime.now(),
            memory_usage_mb=memory_info['used_mb'],
            memory_percent=memory_info['percent'],
            cpu_percent=cpu_percent,
            active_processes=len(psutil.pids()),
            processing_time_ms=processing_time_ms,
            job_id=job_id,
            stage=stage
        )
        
        self.metrics_history.append(metrics)
        
        # 히스토리 크기 제한
        if len(self.metrics_history) > self.max_history_size:
            self.metrics_history = self.metrics_history[-self.max_history_size:]
        
        return metrics
    
    def get_average_metrics(self, minutes: int = 5) -> Dict[str, float]:
        """지정된 시간 동안의 평균 메트릭 계산"""
        cutoff_time = datetime.now() - timedelta(minutes=minutes)
        recent_metrics = [
            m for m in self.metrics_history 
            if m.timestamp > cutoff_time
        ]
        
        if not recent_metrics:
            return {}
        
        return {
            'avg_memory_percent': sum(m.memory_percent for m in recent_metrics) / len(recent_metrics),
            'avg_cpu_percent': sum(m.cpu_percent for m in recent_metrics) / len(recent_metrics),
            'avg_processing_time_ms': sum(
                m.processing_time_ms for m in recent_metrics 
                if m.processing_time_ms is not None
            ) / len([m for m in recent_metrics if m.processing_time_ms is not None])
        }


class SmartImageCompressor:
    """지능형 이미지 압축 클래스"""
    
    def __init__(self, memory_monitor: MemoryMonitor):
        self.memory_monitor = memory_monitor
        self.compression_profiles = {
            'high_quality': {'quality': 95, 'max_dimension': 2048},
            'balanced': {'quality': 85, 'max_dimension': 1536},
            'memory_optimized': {'quality': 75, 'max_dimension': 1024},
            'emergency': {'quality': 60, 'max_dimension': 768}
        }
    
    def select_compression_profile(self) -> Dict[str, Any]:
        """현재 메모리 상황에 따른 압축 프로필 선택"""
        memory_percent = self.memory_monitor.get_current_memory_info()['percent']
        
        if memory_percent < 50:
            return self.compression_profiles['high_quality']
        elif memory_percent < 70:
            return self.compression_profiles['balanced']
        elif memory_percent < 85:
            return self.compression_profiles['memory_optimized']
        else:
            return self.compression_profiles['emergency']
    
    def estimate_compressed_size(self, original_size_mb: float, profile: Dict[str, Any]) -> float:
        """압축 후 예상 크기 추정"""
        quality_factor = profile['quality'] / 100.0
        dimension_factor = min(1.0, profile['max_dimension'] / 2048.0)
        
        # 간단한 추정 공식 (실제로는 더 복잡할 수 있음)
        estimated_size = original_size_mb * quality_factor * dimension_factor * 0.7
        return max(0.1, estimated_size)  # 최소 0.1MB


class PerformanceOptimizer:
    """성능 최적화 관리자"""
    
    def __init__(self):
        self.memory_monitor = MemoryMonitor()
        self.image_compressor = SmartImageCompressor(self.memory_monitor)
        self._monitoring_active = False
        self._monitoring_thread = None
        
    def start_monitoring(self, interval_seconds: int = 30):
        """백그라운드 모니터링 시작"""
        if self._monitoring_active:
            return
        
        self._monitoring_active = True
        self._monitoring_thread = threading.Thread(
            target=self._background_monitoring,
            args=(interval_seconds,),
            daemon=True
        )
        self._monitoring_thread.start()
        logger.info("성능 모니터링 시작")
    
    def stop_monitoring(self):
        """백그라운드 모니터링 중지"""
        self._monitoring_active = False
        if self._monitoring_thread:
            self._monitoring_thread.join(timeout=5)
        logger.info("성능 모니터링 중지")
    
    def _background_monitoring(self, interval_seconds: int):
        """백그라운드 모니터링 스레드"""
        while self._monitoring_active:
            try:
                metrics = self.memory_monitor.record_metrics()
                
                # 메모리 압박 시 경고 로그
                if self.memory_monitor.check_memory_pressure():
                    logger.warning(
                        f"메모리 사용량 높음: {metrics.memory_percent:.1f}% "
                        f"({metrics.memory_usage_mb:.1f}MB)"
                    )
                
                time.sleep(interval_seconds)
                
            except Exception as e:
                logger.error(f"모니터링 중 오류 발생: {e}")
                time.sleep(interval_seconds)
    
    def get_optimization_recommendations(self) -> Dict[str, Any]:
        """현재 상황에 따른 최적화 권장사항 반환"""
        memory_info = self.memory_monitor.get_current_memory_info()
        avg_metrics = self.memory_monitor.get_average_metrics()
        
        recommendations = {
            'batch_size': self.memory_monitor.calculate_optimal_batch_size(),
            'compression_profile': self.image_compressor.select_compression_profile(),
            'memory_cleanup_needed': memory_info['percent'] > 85,
            'reduce_concurrent_jobs': memory_info['percent'] > 90,
        }
        
        if avg_metrics:
            recommendations['performance_trend'] = {
                'memory_trend': 'increasing' if memory_info['percent'] > avg_metrics.get('avg_memory_percent', 0) else 'stable',
                'avg_processing_time': avg_metrics.get('avg_processing_time_ms', 0)
            }
        
        return recommendations
    
    def cleanup_resources(self):
        """리소스 정리"""
        import gc
        import tempfile
        import shutil
        import os
        
        # 가비지 컬렉션 강제 실행
        gc.collect()
        
        # 임시 파일 정리
        temp_dir = tempfile.gettempdir()
        for filename in os.listdir(temp_dir):
            if filename.startswith(('tspm_', 'smarteye_', 'temp_')):
                try:
                    filepath = os.path.join(temp_dir, filename)
                    if os.path.isfile(filepath):
                        os.remove(filepath)
                    elif os.path.isdir(filepath):
                        shutil.rmtree(filepath)
                except Exception as e:
                    logger.debug(f"임시 파일 정리 실패: {filename} - {e}")
        
        logger.info("리소스 정리 완료")
    
    def export_metrics_report(self, output_path: str = None) -> str:
        """메트릭 리포트 내보내기"""
        if not output_path:
            output_path = f"/tmp/smarteye_metrics_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        
        report_data = {
            'generated_at': datetime.now().isoformat(),
            'current_metrics': self.memory_monitor.get_current_memory_info(),
            'recent_average': self.memory_monitor.get_average_metrics(),
            'recommendations': self.get_optimization_recommendations(),
            'history_count': len(self.memory_monitor.metrics_history)
        }
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        
        logger.info(f"메트릭 리포트 생성: {output_path}")
        return output_path


# 전역 성능 최적화 인스턴스
performance_optimizer = PerformanceOptimizer()


def get_performance_optimizer() -> PerformanceOptimizer:
    """성능 최적화 인스턴스 반환"""
    return performance_optimizer


class PerformanceContextManager:
    """성능 측정 컨텍스트 매니저"""
    
    def __init__(self, job_id: str, stage: str):
        self.job_id = job_id
        self.stage = stage
        self.start_time = None
        self.optimizer = get_performance_optimizer()
    
    def __enter__(self):
        self.start_time = time.time()
        logger.debug(f"[{self.job_id}] {self.stage} 시작")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.start_time:
            processing_time_ms = (time.time() - self.start_time) * 1000
            self.optimizer.memory_monitor.record_metrics(
                job_id=self.job_id,
                stage=self.stage,
                processing_time_ms=processing_time_ms
            )
            logger.debug(f"[{self.job_id}] {self.stage} 완료: {processing_time_ms:.1f}ms")


# 사용 예시를 위한 데코레이터
def monitor_performance(stage: str):
    """성능 모니터링 데코레이터"""
    def decorator(func):
        def wrapper(*args, **kwargs):
            job_id = kwargs.get('job_id', 'unknown')
            with PerformanceContextManager(job_id, stage):
                return func(*args, **kwargs)
        return wrapper
    return decorator