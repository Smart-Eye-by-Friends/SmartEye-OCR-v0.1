"""
SmartEye LAM (Layout Analysis Module) Memory Manager

노트북에서 추출한 메모리 관리 클래스를 Django로 이전
"""

import psutil
import logging
logger = logging.getLogger(__name__)
from .config import LAMConfig


class MemoryManager:
    """동적 메모리 관리 및 모니터링 클래스"""
    
    def __init__(self):
        self.process = psutil.Process()
        self.initial_memory = self.get_memory_usage_mb()
        
    def get_memory_usage_mb(self):
        """현재 메모리 사용량 (MB) 반환"""
        return self.process.memory_info().rss / 1024 / 1024
    
    def get_memory_percent(self):
        """시스템 메모리 사용률 (%) 반환"""
        return psutil.virtual_memory().percent / 100
    
    def check_memory_status(self):
        """메모리 상태 체크 및 경고"""
        memory_percent = self.get_memory_percent()
        current_mb = self.get_memory_usage_mb()
        
        if memory_percent > LAMConfig.MEMORY_CRITICAL_THRESHOLD:
            logger.critical(f"🔴 메모리 위험 수준: {memory_percent:.1%} (현재: {current_mb:.1f}MB)")
            return "critical"
        elif memory_percent > LAMConfig.MEMORY_WARNING_THRESHOLD:
            logger.warning(f"🟡 메모리 경고 수준: {memory_percent:.1%} (현재: {current_mb:.1f}MB)")
            return "warning"
        else:
            logger.info(f"🟢 메모리 정상: {memory_percent:.1%} (현재: {current_mb:.1f}MB)")
            return "normal"
    
    def adaptive_batch_size(self, base_batch_size=None):
        """메모리 상태에 따른 동적 배치 크기 조정"""
        if base_batch_size is None:
            base_batch_size = LAMConfig.DEFAULT_BATCH_SIZE
        
        memory_percent = self.get_memory_percent()
        
        if memory_percent > 0.8:
            return max(1, base_batch_size // 2)  # 메모리 부족 시 배치 크기 절반
        elif memory_percent < 0.5:
            return min(base_batch_size * 2, 8)  # 메모리 여유 시 배치 크기 증가 (최대 8)
        else:
            return base_batch_size
    
    def can_process_images(self, total_size_mb):
        """이미지 처리 가능 여부 확인"""
        memory_status = self.check_memory_status()
        
        if memory_status == "critical":
            logger.error("🔴 메모리 부족으로 처리를 중단합니다.")
            return False
        
        # 메모리 한계 체크
        if total_size_mb > LAMConfig.MEMORY_LIMIT_MB:
            logger.warning(f"⚠️ 총 이미지 크기가 {total_size_mb:.1f}MB입니다. 메모리 부족이 발생할 수 있습니다.")
            return False
        
        return True
    
    def get_memory_info(self):
        """현재 메모리 정보 반환"""
        return {
            'usage_mb': self.get_memory_usage_mb(),
            'usage_percent': self.get_memory_percent(),
            'initial_mb': self.initial_memory,
            'status': self.check_memory_status(),
            'adaptive_batch_size': self.adaptive_batch_size()
        }
