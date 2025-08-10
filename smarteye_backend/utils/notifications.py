"""
통합 알림 서비스
WebSocket 알림 로직 중복 제거 및 통합 관리
"""
import logging
from typing import Dict, Any, Optional, List
from channels.layers import get_channel_layer
from asgiref.sync import async_to_sync
from django.contrib.auth import get_user_model

User = get_user_model()
logger = logging.getLogger(__name__)


class NotificationService:
    """
    통합 알림 서비스 클래스
    모든 WebSocket 알림을 중앙에서 관리
    """
    
    def __init__(self):
        self.channel_layer = get_channel_layer()
        
        # 그룹 이름 템플릿
        self.GROUP_TEMPLATES = {
            'user': 'user_{user_id}',
            'job': 'job_{job_id}',
            'analysis': 'analysis_{analysis_id}',
            'system': 'system_notifications',
            'admin': 'admin_notifications'
        }
        
        # 메시지 타입
        self.MESSAGE_TYPES = {
            'job_status': 'job.status_update',
            'job_progress': 'job.progress_update',
            'job_complete': 'job.complete',
            'job_error': 'job.error',
            'analysis_result': 'analysis.result',
            'system_message': 'system.message',
            'user_message': 'user.message'
        }
    
    def _get_group_name(self, group_type: str, **kwargs) -> str:
        """그룹 이름 생성"""
        template = self.GROUP_TEMPLATES.get(group_type)
        if not template:
            raise ValueError(f"Unknown group type: {group_type}")
        
        return template.format(**kwargs)
    
    def _send_to_group(self, group_name: str, message_type: str, data: Dict[str, Any]):
        """그룹으로 메시지 전송"""
        if not self.channel_layer:
            logger.warning("Channel layer가 설정되지 않았습니다. 알림을 전송할 수 없습니다.")
            return
        
        try:
            message = {
                'type': message_type.replace('.', '_'),  # 채널 메서드 이름으로 변환
                'data': data,
                'timestamp': self._get_timestamp()
            }
            
            async_to_sync(self.channel_layer.group_send)(group_name, message)
            logger.debug(f"알림 전송 완료: {group_name} - {message_type}")
            
        except Exception as e:
            logger.error(f"알림 전송 실패: {group_name} - {e}")
    
    def _get_timestamp(self) -> str:
        """현재 시간 ISO 형식으로 반환"""
        from datetime import datetime
        return datetime.now().isoformat()
    
    # ====================
    # 작업(Job) 관련 알림
    # ====================
    
    def send_job_status_update(self, job_id: int, user_id: int, status: str, 
                              message: str = None, **extra_data):
        """작업 상태 변경 알림"""
        data = {
            'job_id': job_id,
            'status': status,
            'message': message or f'작업 상태가 {status}로 변경되었습니다.'
        }
        data.update(extra_data)
        
        # 사용자별 알림
        user_group = self._get_group_name('user', user_id=user_id)
        self._send_to_group(user_group, self.MESSAGE_TYPES['job_status'], data)
        
        # 작업별 알림 (관리자 등이 구독)
        job_group = self._get_group_name('job', job_id=job_id)
        self._send_to_group(job_group, self.MESSAGE_TYPES['job_status'], data)
    
    def send_job_progress_update(self, job_id: int, user_id: int, progress: float, 
                               processed: int, total: int, current_stage: str = None):
        """작업 진행률 업데이트 알림"""
        data = {
            'job_id': job_id,
            'progress': round(progress, 2),
            'processed_images': processed,
            'total_images': total,
            'current_stage': current_stage or '처리중'
        }
        
        user_group = self._get_group_name('user', user_id=user_id)
        self._send_to_group(user_group, self.MESSAGE_TYPES['job_progress'], data)
        
        job_group = self._get_group_name('job', job_id=job_id)
        self._send_to_group(job_group, self.MESSAGE_TYPES['job_progress'], data)
    
    def send_job_completion(self, job_id: int, user_id: int, success: bool, 
                           result_data: Dict[str, Any] = None, error_message: str = None):
        """작업 완료/실패 알림"""
        message_type = self.MESSAGE_TYPES['job_complete'] if success else self.MESSAGE_TYPES['job_error']
        
        data = {
            'job_id': job_id,
            'success': success,
            'message': '작업이 성공적으로 완료되었습니다.' if success else f'작업 실패: {error_message}',
            'result': result_data,
            'error': error_message
        }
        
        user_group = self._get_group_name('user', user_id=user_id)
        self._send_to_group(user_group, message_type, data)
        
        job_group = self._get_group_name('job', job_id=job_id)
        self._send_to_group(job_group, message_type, data)
    
    # ====================
    # 분석 결과 관련 알림
    # ====================
    
    def send_analysis_result(self, analysis_id: int, user_id: int, 
                           result_type: str, result_data: Dict[str, Any]):
        """분석 결과 알림"""
        data = {
            'analysis_id': analysis_id,
            'result_type': result_type,  # 'lam', 'tspm', 'cim'
            'result': result_data
        }
        
        user_group = self._get_group_name('user', user_id=user_id)
        self._send_to_group(user_group, self.MESSAGE_TYPES['analysis_result'], data)
        
        analysis_group = self._get_group_name('analysis', analysis_id=analysis_id)
        self._send_to_group(analysis_group, self.MESSAGE_TYPES['analysis_result'], data)
    
    # ====================
    # 시스템 메시지
    # ====================
    
    def send_system_message(self, message: str, level: str = 'info', 
                          target_users: List[int] = None, broadcast: bool = False):
        """시스템 메시지 알림"""
        data = {
            'message': message,
            'level': level,  # 'info', 'warning', 'error', 'success'
            'broadcast': broadcast
        }
        
        if broadcast:
            # 전체 알림
            self._send_to_group('system_notifications', self.MESSAGE_TYPES['system_message'], data)
        elif target_users:
            # 특정 사용자들에게 알림
            for user_id in target_users:
                user_group = self._get_group_name('user', user_id=user_id)
                self._send_to_group(user_group, self.MESSAGE_TYPES['system_message'], data)
    
    def send_user_message(self, user_id: int, title: str, message: str, 
                         message_type: str = 'info', action_url: str = None):
        """개별 사용자 메시지"""
        data = {
            'title': title,
            'message': message,
            'type': message_type,
            'action_url': action_url
        }
        
        user_group = self._get_group_name('user', user_id=user_id)
        self._send_to_group(user_group, self.MESSAGE_TYPES['user_message'], data)
    
    # ====================
    # 그룹 관리
    # ====================
    
    def add_user_to_job_group(self, channel_name: str, job_id: int):
        """사용자를 작업 그룹에 추가"""
        job_group = self._get_group_name('job', job_id=job_id)
        async_to_sync(self.channel_layer.group_add)(job_group, channel_name)
        logger.debug(f"채널 {channel_name}을 그룹 {job_group}에 추가")
    
    def remove_user_from_job_group(self, channel_name: str, job_id: int):
        """사용자를 작업 그룹에서 제거"""
        job_group = self._get_group_name('job', job_id=job_id)
        async_to_sync(self.channel_layer.group_discard)(job_group, channel_name)
        logger.debug(f"채널 {channel_name}을 그룹 {job_group}에서 제거")
    
    def add_user_to_user_group(self, channel_name: str, user_id: int):
        """사용자를 개인 그룹에 추가"""
        user_group = self._get_group_name('user', user_id=user_id)
        async_to_sync(self.channel_layer.group_add)(user_group, channel_name)
        logger.debug(f"채널 {channel_name}을 그룹 {user_group}에 추가")
    
    def remove_user_from_user_group(self, channel_name: str, user_id: int):
        """사용자를 개인 그룹에서 제거"""
        user_group = self._get_group_name('user', user_id=user_id)
        async_to_sync(self.channel_layer.group_discard)(user_group, channel_name)
        logger.debug(f"채널 {channel_name}을 그룹 {user_group}에서 제거")


# 싱글톤 인스턴스
notification_service = NotificationService()


# ====================
# 편의 함수들
# ====================

def notify_job_started(job_id: int, user_id: int, job_name: str):
    """작업 시작 알림"""
    notification_service.send_job_status_update(
        job_id=job_id,
        user_id=user_id,
        status='processing',
        message=f'작업 "{job_name}"이 시작되었습니다.',
        job_name=job_name
    )


def notify_job_progress(job_id: int, user_id: int, progress: float, 
                       processed: int, total: int, stage: str = None):
    """작업 진행 상황 알림"""
    notification_service.send_job_progress_update(
        job_id=job_id,
        user_id=user_id,
        progress=progress,
        processed=processed,
        total=total,
        current_stage=stage
    )


def notify_job_completed(job_id: int, user_id: int, job_name: str, result_data: Dict[str, Any]):
    """작업 완료 알림"""
    notification_service.send_job_completion(
        job_id=job_id,
        user_id=user_id,
        success=True,
        result_data=result_data
    )
    
    notification_service.send_user_message(
        user_id=user_id,
        title='분석 완료',
        message=f'작업 "{job_name}"이 성공적으로 완료되었습니다.',
        message_type='success',
        action_url=f'/analysis/jobs/{job_id}/results'
    )


def notify_job_failed(job_id: int, user_id: int, job_name: str, error_message: str):
    """작업 실패 알림"""
    notification_service.send_job_completion(
        job_id=job_id,
        user_id=user_id,
        success=False,
        error_message=error_message
    )
    
    notification_service.send_user_message(
        user_id=user_id,
        title='분석 실패',
        message=f'작업 "{job_name}"에서 오류가 발생했습니다: {error_message}',
        message_type='error',
        action_url=f'/analysis/jobs/{job_id}'
    )


def notify_analysis_stage_complete(job_id: int, user_id: int, stage: str, result_data: Dict[str, Any]):
    """분석 단계 완료 알림 (LAM, TSPM, CIM)"""
    stage_names = {
        'lam': 'Layout Analysis (LAM)',
        'tspm': 'Text & Scene Processing (TSPM)', 
        'cim': 'Content Integration (CIM)'
    }
    
    stage_name = stage_names.get(stage, stage.upper())
    
    notification_service.send_analysis_result(
        analysis_id=job_id,
        user_id=user_id,
        result_type=stage,
        result_data=result_data
    )
    
    notification_service.send_user_message(
        user_id=user_id,
        title=f'{stage_name} 완료',
        message=f'{stage_name} 처리가 완료되었습니다.',
        message_type='info'
    )


def notify_quota_warning(user_id: int, usage_percentage: float):
    """API 할당량 경고"""
    if usage_percentage >= 90:
        level = 'error'
        message = f'API 할당량의 {usage_percentage:.1f}%를 사용했습니다. 곧 한계에 도달합니다.'
    elif usage_percentage >= 75:
        level = 'warning'
        message = f'API 할당량의 {usage_percentage:.1f}%를 사용했습니다.'
    else:
        return  # 경고 필요 없음
    
    notification_service.send_user_message(
        user_id=user_id,
        title='API 할당량 경고',
        message=message,
        message_type=level,
        action_url='/account/quota'
    )


def notify_system_maintenance(message: str, start_time: str, end_time: str):
    """시스템 점검 알림"""
    notification_service.send_system_message(
        message=f'시스템 점검 안내: {message} (시간: {start_time} ~ {end_time})',
        level='warning',
        broadcast=True
    )