"""
WebSocket 라우팅 설정
"""

from django.urls import re_path
from . import consumers

websocket_urlpatterns = [
    # 분석 진행 상황 실시간 업데이트
    re_path(r'ws/analysis/progress/$', consumers.AnalysisProgressConsumer.as_asgi()),
    
    # 시스템 상태 모니터링 (관리자용)
    re_path(r'ws/system/status/$', consumers.SystemStatusConsumer.as_asgi()),
]
