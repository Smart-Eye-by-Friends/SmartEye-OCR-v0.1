"""
WebSocket 알림 유틸리티
"""

from channels.layers import get_channel_layer
from asgiref.sync import async_to_sync
import logging
from datetime import datetime

logger = logging.getLogger(__name__)


class AnalysisNotifier:
    """분석 진행 상황 WebSocket 알림 관리자"""
    
    def __init__(self):
        self.channel_layer = get_channel_layer()
    
    def send_progress_update(self, user_id: int, job_id: int, stage: str, 
                           percentage: int, message: str = ""):
        """분석 진행 상황 업데이트 전송"""
        if not self.channel_layer:
            logger.warning("채널 레이어가 설정되지 않았습니다.")
            return
        
        try:
            # 사용자별 그룹에 전송
            user_group = f"analysis_progress_{user_id}"
            async_to_sync(self.channel_layer.group_send)(
                user_group,
                {
                    'type': 'analysis_progress',
                    'job_id': job_id,
                    'stage': stage,
                    'percentage': percentage,
                    'message': message,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            # 작업별 그룹에도 전송
            job_group = f"job_progress_{job_id}"
            async_to_sync(self.channel_layer.group_send)(
                job_group,
                {
                    'type': 'analysis_progress',
                    'job_id': job_id,
                    'stage': stage,
                    'percentage': percentage,
                    'message': message,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            logger.info(f"진행 상황 알림 전송: Job {job_id}, {stage} {percentage}%")
            
        except Exception as e:
            logger.error(f"진행 상황 알림 전송 실패: {e}")
    
    def send_completion_notification(self, user_id: int, job_id: int, result: dict):
        """분석 완료 알림 전송"""
        if not self.channel_layer:
            logger.warning("채널 레이어가 설정되지 않았습니다.")
            return
        
        try:
            # 사용자별 그룹에 전송
            user_group = f"analysis_progress_{user_id}"
            async_to_sync(self.channel_layer.group_send)(
                user_group,
                {
                    'type': 'analysis_completed',
                    'job_id': job_id,
                    'result': result,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            # 작업별 그룹에도 전송
            job_group = f"job_progress_{job_id}"
            async_to_sync(self.channel_layer.group_send)(
                job_group,
                {
                    'type': 'analysis_completed',
                    'job_id': job_id,
                    'result': result,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            logger.info(f"분석 완료 알림 전송: Job {job_id}")
            
        except Exception as e:
            logger.error(f"완료 알림 전송 실패: {e}")
    
    def send_failure_notification(self, user_id: int, job_id: int, error: str):
        """분석 실패 알림 전송"""
        if not self.channel_layer:
            logger.warning("채널 레이어가 설정되지 않았습니다.")
            return
        
        try:
            # 사용자별 그룹에 전송
            user_group = f"analysis_progress_{user_id}"
            async_to_sync(self.channel_layer.group_send)(
                user_group,
                {
                    'type': 'analysis_failed',
                    'job_id': job_id,
                    'error': error,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            # 작업별 그룹에도 전송
            job_group = f"job_progress_{job_id}"
            async_to_sync(self.channel_layer.group_send)(
                job_group,
                {
                    'type': 'analysis_failed',
                    'job_id': job_id,
                    'error': error,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            logger.info(f"분석 실패 알림 전송: Job {job_id}")
            
        except Exception as e:
            logger.error(f"실패 알림 전송 실패: {e}")


class SystemNotifier:
    """시스템 상태 WebSocket 알림 관리자"""
    
    def __init__(self):
        self.channel_layer = get_channel_layer()
    
    def send_system_status(self, status_data: dict):
        """시스템 상태 업데이트 전송"""
        if not self.channel_layer:
            logger.warning("채널 레이어가 설정되지 않았습니다.")
            return
        
        try:
            async_to_sync(self.channel_layer.group_send)(
                "system_status",
                {
                    'type': 'system_status',
                    'data': status_data,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            logger.debug("시스템 상태 알림 전송 완료")
            
        except Exception as e:
            logger.error(f"시스템 상태 알림 전송 실패: {e}")
    
    def send_celery_status(self, workers: list, queues: list):
        """Celery 상태 업데이트 전송"""
        if not self.channel_layer:
            logger.warning("채널 레이어가 설정되지 않았습니다.")
            return
        
        try:
            async_to_sync(self.channel_layer.group_send)(
                "system_status",
                {
                    'type': 'celery_status',
                    'workers': workers,
                    'queues': queues,
                    'timestamp': datetime.now().isoformat()
                }
            )
            
            logger.debug("Celery 상태 알림 전송 완료")
            
        except Exception as e:
            logger.error(f"Celery 상태 알림 전송 실패: {e}")


# 전역 인스턴스
analysis_notifier = AnalysisNotifier()
system_notifier = SystemNotifier()
