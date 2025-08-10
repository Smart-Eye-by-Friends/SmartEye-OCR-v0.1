"""
WebSocket 알림 유틸리티 - 통합 알림 서비스 래퍼
기존 코드와의 호환성을 위한 래퍼 클래스들
"""

import logging
from utils.notifications import notification_service

logger = logging.getLogger(__name__)


class AnalysisNotifier:
    """분석 진행 상황 WebSocket 알림 관리자 - 통합 서비스 래퍼"""
    
    def __init__(self):
        # 통합 알림 서비스 사용
        self.notification_service = notification_service
    
    def send_progress_update(self, user_id: int, job_id: int, stage: str, 
                           percentage: int, message: str = ""):
        """분석 진행 상황 업데이트 전송"""
        try:
            # 기존 파라미터를 새로운 서비스에 맞게 변환
            processed = int((percentage / 100) * 100)  # 백분율을 처리된 수로 변환 (임시)
            total = 100
            
            self.notification_service.send_job_progress_update(
                job_id=job_id,
                user_id=user_id,
                progress=float(percentage),
                processed=processed,
                total=total,
                current_stage=stage
            )
            
            # 추가 메시지가 있으면 사용자 메시지로도 전송
            if message:
                self.notification_service.send_user_message(
                    user_id=user_id,
                    title=f'{stage} 진행중',
                    message=message,
                    message_type='info'
                )
                
            logger.info(f"진행 상황 알림 전송: Job {job_id}, {stage} {percentage}%")
            
        except Exception as e:
            logger.error(f"진행 상황 알림 전송 실패: {e}")
    
    def send_completion_notification(self, user_id: int, job_id: int, result: dict):
        """분석 완료 알림 전송"""
        try:
            # 통합 서비스의 완료 알림 사용
            self.notification_service.send_job_completion(
                job_id=job_id,
                user_id=user_id,
                success=True,
                result_data=result
            )
            
            logger.info(f"분석 완료 알림 전송: Job {job_id}")
            
        except Exception as e:
            logger.error(f"완료 알림 전송 실패: {e}")
    
    def send_failure_notification(self, user_id: int, job_id: int, error: str):
        """분석 실패 알림 전송"""
        try:
            # 통합 서비스의 실패 알림 사용
            self.notification_service.send_job_completion(
                job_id=job_id,
                user_id=user_id,
                success=False,
                error_message=error
            )
            
            logger.info(f"분석 실패 알림 전송: Job {job_id}")
            
        except Exception as e:
            logger.error(f"실패 알림 전송 실패: {e}")


class SystemNotifier:
    """시스템 상태 WebSocket 알림 관리자 - 통합 서비스 래퍼"""
    
    def __init__(self):
        # 통합 알림 서비스 사용
        self.notification_service = notification_service
    
    def send_system_status(self, status_data: dict):
        """시스템 상태 업데이트 전송"""
        try:
            # 시스템 상태를 시스템 메시지로 변환
            message = f"시스템 상태 업데이트: {status_data.get('status', '정보 없음')}"
            
            self.notification_service.send_system_message(
                message=message,
                level='info',
                broadcast=True
            )
            
            logger.debug("시스템 상태 알림 전송 완료")
            
        except Exception as e:
            logger.error(f"시스템 상태 알림 전송 실패: {e}")
    
    def send_celery_status(self, workers: list, queues: list):
        """Celery 상태 업데이트 전송"""
        try:
            # Celery 상태를 시스템 메시지로 변환
            worker_count = len(workers)
            queue_info = ', '.join([f"{q['name']}({q['messages']})" for q in queues])
            
            message = f"Celery 상태: {worker_count}개 워커 활성, 큐: {queue_info}"
            
            self.notification_service.send_system_message(
                message=message,
                level='info',
                broadcast=True
            )
            
            logger.debug("Celery 상태 알림 전송 완료")
            
        except Exception as e:
            logger.error(f"Celery 상태 알림 전송 실패: {e}")


# 전역 인스턴스 - 기존 코드와의 호환성 유지
analysis_notifier = AnalysisNotifier()
system_notifier = SystemNotifier()

# 편의 함수들 - 기존 import 패턴 지원
def send_analysis_progress(user_id: int, job_id: int, stage: str, percentage: int, message: str = ""):
    """기존 함수명 호환성"""
    analysis_notifier.send_progress_update(user_id, job_id, stage, percentage, message)

def send_analysis_complete(user_id: int, job_id: int, result: dict):
    """기존 함수명 호환성"""
    analysis_notifier.send_completion_notification(user_id, job_id, result)

def send_analysis_failed(user_id: int, job_id: int, error: str):
    """기존 함수명 호환성"""
    analysis_notifier.send_failure_notification(user_id, job_id, error)
