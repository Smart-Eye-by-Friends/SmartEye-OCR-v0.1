"""
SmartEye 모니터링 앱 설정
"""

from django.apps import AppConfig


class MonitoringConfig(AppConfig):
    """모니터링 앱 설정"""
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'apps.monitoring'
    verbose_name = 'SmartEye 모니터링'

    def ready(self):
        """앱 준비 완료 시 호출"""
        try:
            # 알림 모니터링 시작
            from utils.alert_system import start_alert_monitoring
            start_alert_monitoring()
            
            # 성능 모니터링 시작
            from utils.performance_monitor import get_performance_optimizer
            optimizer = get_performance_optimizer()
            optimizer.start_monitoring()
            
        except Exception as e:
            import logging
            logger = logging.getLogger(__name__)
            logger.error(f"모니터링 시스템 시작 실패: {e}")