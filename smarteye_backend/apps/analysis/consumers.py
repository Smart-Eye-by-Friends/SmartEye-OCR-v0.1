"""
WebSocket 소비자 - 실시간 분석 진행 상황 업데이트
"""

import json
import logging
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth import get_user_model

logger = logging.getLogger(__name__)
User = get_user_model()


class AnalysisProgressConsumer(AsyncWebsocketConsumer):
    """분석 진행 상황 실시간 업데이트 WebSocket 소비자"""
    
    async def connect(self):
        """WebSocket 연결"""
        self.user = self.scope["user"]
        
        # 인증되지 않은 사용자 거부
        if self.user.is_anonymous:
            await self.close()
            return
        
        # 사용자별 그룹 생성
        self.group_name = f"analysis_progress_{self.user.id}"
        
        # 그룹에 채널 추가
        await self.channel_layer.group_add(
            self.group_name,
            self.channel_name
        )
        
        await self.accept()
        logger.info(f"WebSocket 연결됨: 사용자 {self.user.username}")
        
        # 연결 확인 메시지 전송
        await self.send(text_data=json.dumps({
            'type': 'connection_established',
            'message': 'WebSocket 연결이 설정되었습니다.',
            'user_id': self.user.id
        }))

    async def disconnect(self, close_code):
        """WebSocket 연결 해제"""
        # 그룹에서 채널 제거
        await self.channel_layer.group_discard(
            self.group_name,
            self.channel_name
        )
        logger.info(f"WebSocket 연결 해제됨: 사용자 {self.user.username}, 코드: {close_code}")

    async def receive(self, text_data):
        """클라이언트로부터 메시지 수신"""
        try:
            text_data_json = json.loads(text_data)
            message_type = text_data_json.get('type', 'unknown')
            
            if message_type == 'subscribe_job':
                # 특정 작업에 대한 구독 요청
                job_id = text_data_json.get('job_id')
                if job_id:
                    # 작업 권한 확인
                    has_permission = await self.check_job_permission(job_id)
                    if has_permission:
                        # 작업별 그룹에 추가
                        job_group = f"job_progress_{job_id}"
                        await self.channel_layer.group_add(
                            job_group,
                            self.channel_name
                        )
                        await self.send(text_data=json.dumps({
                            'type': 'subscription_confirmed',
                            'job_id': job_id,
                            'message': f'작업 {job_id} 진행 상황 구독을 시작했습니다.'
                        }))
                    else:
                        await self.send(text_data=json.dumps({
                            'type': 'error',
                            'message': '해당 작업에 대한 권한이 없습니다.'
                        }))
                        
            elif message_type == 'unsubscribe_job':
                # 특정 작업에 대한 구독 해제
                job_id = text_data_json.get('job_id')
                if job_id:
                    job_group = f"job_progress_{job_id}"
                    await self.channel_layer.group_discard(
                        job_group,
                        self.channel_name
                    )
                    await self.send(text_data=json.dumps({
                        'type': 'unsubscription_confirmed',
                        'job_id': job_id,
                        'message': f'작업 {job_id} 진행 상황 구독을 해제했습니다.'
                    }))
                    
        except Exception as e:
            logger.error(f"WebSocket 메시지 처리 오류: {e}")
            await self.send(text_data=json.dumps({
                'type': 'error',
                'message': '메시지 처리 중 오류가 발생했습니다.'
            }))

    async def analysis_progress(self, event):
        """분석 진행 상황 업데이트 전송"""
        await self.send(text_data=json.dumps({
            'type': 'analysis_progress',
            'job_id': event['job_id'],
            'stage': event['stage'],
            'percentage': event['percentage'],
            'message': event['message'],
            'timestamp': event.get('timestamp')
        }))

    async def analysis_completed(self, event):
        """분석 완료 알림 전송"""
        await self.send(text_data=json.dumps({
            'type': 'analysis_completed',
            'job_id': event['job_id'],
            'result': event['result'],
            'timestamp': event.get('timestamp')
        }))

    async def analysis_failed(self, event):
        """분석 실패 알림 전송"""
        await self.send(text_data=json.dumps({
            'type': 'analysis_failed',
            'job_id': event['job_id'],
            'error': event['error'],
            'timestamp': event.get('timestamp')
        }))

    @database_sync_to_async
    def check_job_permission(self, job_id):
        """작업에 대한 사용자 권한 확인"""
        try:
            from .models import AnalysisJob
            AnalysisJob.objects.get(id=job_id, user=self.user)
            return True
        except AnalysisJob.DoesNotExist:
            return False


class SystemStatusConsumer(AsyncWebsocketConsumer):
    """시스템 상태 모니터링 WebSocket 소비자"""
    
    async def connect(self):
        """WebSocket 연결"""
        self.user = self.scope["user"]
        
        # 관리자만 접근 허용
        if self.user.is_anonymous or not self.user.is_staff:
            await self.close()
            return
        
        # 시스템 상태 그룹에 추가
        self.group_name = "system_status"
        await self.channel_layer.group_add(
            self.group_name,
            self.channel_name
        )
        
        await self.accept()
        logger.info(f"시스템 상태 WebSocket 연결됨: 관리자 {self.user.username}")

    async def disconnect(self, close_code):
        """WebSocket 연결 해제"""
        await self.channel_layer.group_discard(
            self.group_name,
            self.channel_name
        )
        logger.info(f"시스템 상태 WebSocket 연결 해제됨: {close_code}")

    async def system_status(self, event):
        """시스템 상태 업데이트 전송"""
        await self.send(text_data=json.dumps({
            'type': 'system_status',
            'data': event['data'],
            'timestamp': event.get('timestamp')
        }))

    async def celery_status(self, event):
        """Celery 워커 상태 업데이트 전송"""
        await self.send(text_data=json.dumps({
            'type': 'celery_status',
            'workers': event['workers'],
            'queues': event['queues'],
            'timestamp': event.get('timestamp')
        }))
