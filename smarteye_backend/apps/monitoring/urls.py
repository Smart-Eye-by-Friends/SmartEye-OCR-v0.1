"""
SmartEye 모니터링 URL 설정
"""

from django.urls import path
from . import views

app_name = 'monitoring'

urlpatterns = [
    # 대시보드 페이지
    path('dashboard/', views.monitoring_dashboard, name='dashboard'),
    
    # API 엔드포인트
    path('api/metrics/', views.system_metrics, name='system_metrics'),
    path('api/memory/', views.memory_metrics, name='memory_metrics'),
    path('api/logs/recent/', views.recent_logs, name='recent_logs'),
    path('api/performance/', views.api_performance, name='api_performance'),
    path('api/health/', views.system_health_check, name='system_health_check'),
    path('api/alerts/', views.alert_status, name='alert_status'),
    path('api/alerts/threshold/', views.update_threshold, name='update_threshold'),
]