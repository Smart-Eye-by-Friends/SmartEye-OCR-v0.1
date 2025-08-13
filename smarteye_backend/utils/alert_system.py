"""
SmartEye 알림 시스템

메모리, CPU, 에러율 등의 임계값 모니터링 및 알림 발송을 담당합니다.
"""

import psutil
import logging
import smtplib
import json
import time
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional, Callable
from enum import Enum
from dataclasses import dataclass, asdict
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from django.core.cache import cache
from django.conf import settings
from django.core.mail import send_mail
from apps.analysis.models import AnalysisJob
import threading

logger = logging.getLogger(__name__)


class AlertLevel(Enum):
    """알림 수준"""
    INFO = "info"
    WARNING = "warning"
    CRITICAL = "critical"


class AlertChannel(Enum):
    """알림 채널"""
    EMAIL = "email"
    LOG = "log"
    WEBHOOK = "webhook"
    CONSOLE = "console"


@dataclass
class Alert:
    """알림 객체"""
    id: str
    title: str
    message: str
    level: AlertLevel
    timestamp: datetime
    metric_name: str
    current_value: float
    threshold_value: float
    additional_data: Dict[str, Any] = None
    resolved: bool = False
    resolved_at: Optional[datetime] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리로 변환"""
        data = asdict(self)
        data['timestamp'] = self.timestamp.isoformat()
        data['resolved_at'] = self.resolved_at.isoformat() if self.resolved_at else None
        data['level'] = self.level.value
        return data


@dataclass
class Threshold:
    """임계값 설정"""
    metric_name: str
    warning_value: float
    critical_value: float
    check_interval: int = 60  # 초
    min_duration: int = 300   # 5분간 지속되어야 알림
    comparison: str = "greater"  # greater, less, equal
    enabled: bool = True
    description: str = ""


class AlertManager:
    """알림 관리자"""
    
    def __init__(self):
        self.thresholds = self._load_default_thresholds()
        self.active_alerts: Dict[str, Alert] = {}
        self.alert_history: List[Alert] = []
        self.notification_channels = [AlertChannel.LOG, AlertChannel.CONSOLE]
        self.alert_cooldown = {}  # 중복 알림 방지
        self.monitoring_thread = None
        self.monitoring_active = False
        
        # 이메일 설정
        self.email_settings = {
            'enabled': getattr(settings, 'ALERT_EMAIL_ENABLED', False),
            'smtp_host': getattr(settings, 'EMAIL_HOST', 'localhost'),
            'smtp_port': getattr(settings, 'EMAIL_PORT', 587),
            'smtp_user': getattr(settings, 'EMAIL_HOST_USER', ''),
            'smtp_password': getattr(settings, 'EMAIL_HOST_PASSWORD', ''),
            'from_email': getattr(settings, 'ALERT_FROM_EMAIL', 'smarteye@localhost'),
            'to_emails': getattr(settings, 'ALERT_TO_EMAILS', [])
        }
        
        # 웹훅 설정
        self.webhook_settings = {
            'enabled': getattr(settings, 'ALERT_WEBHOOK_ENABLED', False),
            'url': getattr(settings, 'ALERT_WEBHOOK_URL', ''),
            'headers': getattr(settings, 'ALERT_WEBHOOK_HEADERS', {})
        }
    
    def _load_default_thresholds(self) -> Dict[str, Threshold]:
        """기본 임계값 설정 로드"""
        return {
            'memory_percent': Threshold(
                metric_name='memory_percent',
                warning_value=80.0,
                critical_value=90.0,
                check_interval=30,
                description='메모리 사용률'
            ),
            'cpu_percent': Threshold(
                metric_name='cpu_percent',
                warning_value=80.0,
                critical_value=95.0,
                check_interval=60,
                description='CPU 사용률'
            ),
            'disk_percent': Threshold(
                metric_name='disk_percent',
                warning_value=85.0,
                critical_value=95.0,
                check_interval=300,
                description='디스크 사용률'
            ),
            'error_rate': Threshold(
                metric_name='error_rate',
                warning_value=5.0,
                critical_value=10.0,
                check_interval=300,
                description='에러율 (%)'
            ),
            'response_time': Threshold(
                metric_name='response_time',
                warning_value=3000.0,
                critical_value=5000.0,
                check_interval=120,
                description='평균 응답 시간 (ms)'
            ),
            'failed_jobs': Threshold(
                metric_name='failed_jobs',
                warning_value=5.0,
                critical_value=10.0,
                check_interval=300,
                description='1시간 내 실패한 작업 수'
            )
        }
    
    def add_threshold(self, threshold: Threshold):
        """임계값 추가"""
        self.thresholds[threshold.metric_name] = threshold
        logger.info(f"임계값 추가: {threshold.metric_name}")
    
    def remove_threshold(self, metric_name: str):
        """임계값 제거"""
        if metric_name in self.thresholds:
            del self.thresholds[metric_name]
            logger.info(f"임계값 제거: {metric_name}")
    
    def update_threshold(self, metric_name: str, **kwargs):
        """임계값 업데이트"""
        if metric_name in self.thresholds:
            threshold = self.thresholds[metric_name]
            for key, value in kwargs.items():
                if hasattr(threshold, key):
                    setattr(threshold, key, value)
            logger.info(f"임계값 업데이트: {metric_name}")
    
    def enable_notification_channel(self, channel: AlertChannel):
        """알림 채널 활성화"""
        if channel not in self.notification_channels:
            self.notification_channels.append(channel)
    
    def disable_notification_channel(self, channel: AlertChannel):
        """알림 채널 비활성화"""
        if channel in self.notification_channels:
            self.notification_channels.remove(channel)
    
    def check_metric(self, metric_name: str, current_value: float, additional_data: Dict[str, Any] = None) -> Optional[Alert]:
        """메트릭 값 검사 및 알림 생성"""
        if metric_name not in self.thresholds:
            return None
        
        threshold = self.thresholds[metric_name]
        if not threshold.enabled:
            return None
        
        # 임계값 비교
        level = None
        threshold_value = None
        
        if threshold.comparison == "greater":
            if current_value >= threshold.critical_value:
                level = AlertLevel.CRITICAL
                threshold_value = threshold.critical_value
            elif current_value >= threshold.warning_value:
                level = AlertLevel.WARNING
                threshold_value = threshold.warning_value
        elif threshold.comparison == "less":
            if current_value <= threshold.critical_value:
                level = AlertLevel.CRITICAL
                threshold_value = threshold.critical_value
            elif current_value <= threshold.warning_value:
                level = AlertLevel.WARNING
                threshold_value = threshold.warning_value
        
        if level is None:
            # 임계값 위반 없음 - 기존 알림 해결 처리
            self._resolve_alert(metric_name)
            return None
        
        # 중복 알림 방지 (쿨다운 체크)
        cooldown_key = f"{metric_name}_{level.value}"
        current_time = time.time()
        
        if cooldown_key in self.alert_cooldown:
            if current_time - self.alert_cooldown[cooldown_key] < threshold.min_duration:
                return None
        
        # 새 알림 생성
        alert_id = f"{metric_name}_{level.value}_{int(current_time)}"
        alert = Alert(
            id=alert_id,
            title=f"{threshold.description} {level.value.upper()}",
            message=self._create_alert_message(metric_name, current_value, threshold_value, level),
            level=level,
            timestamp=datetime.now(),
            metric_name=metric_name,
            current_value=current_value,
            threshold_value=threshold_value,
            additional_data=additional_data or {}
        )
        
        # 알림 저장 및 쿨다운 설정
        self.active_alerts[alert_id] = alert
        self.alert_history.append(alert)
        self.alert_cooldown[cooldown_key] = current_time
        
        # 알림 발송
        self._send_alert(alert)
        
        logger.warning(f"알림 생성: {alert.title} - {alert.message}")
        return alert
    
    def _resolve_alert(self, metric_name: str):
        """메트릭 관련 활성 알림 해결"""
        resolved_alerts = []
        
        for alert_id, alert in list(self.active_alerts.items()):
            if alert.metric_name == metric_name and not alert.resolved:
                alert.resolved = True
                alert.resolved_at = datetime.now()
                resolved_alerts.append(alert)
                del self.active_alerts[alert_id]
        
        for alert in resolved_alerts:
            logger.info(f"알림 해결: {alert.title}")
            self._send_resolution_notification(alert)
    
    def _create_alert_message(self, metric_name: str, current_value: float, threshold_value: float, level: AlertLevel) -> str:
        """알림 메시지 생성"""
        threshold = self.thresholds[metric_name]
        
        if metric_name == 'memory_percent':
            return f"메모리 사용률이 {current_value:.1f}%로 {threshold_value:.1f}% 임계값을 초과했습니다."
        elif metric_name == 'cpu_percent':
            return f"CPU 사용률이 {current_value:.1f}%로 {threshold_value:.1f}% 임계값을 초과했습니다."
        elif metric_name == 'disk_percent':
            return f"디스크 사용률이 {current_value:.1f}%로 {threshold_value:.1f}% 임계값을 초과했습니다."
        elif metric_name == 'error_rate':
            return f"에러율이 {current_value:.1f}%로 {threshold_value:.1f}% 임계값을 초과했습니다."
        elif metric_name == 'response_time':
            return f"평균 응답 시간이 {current_value:.0f}ms로 {threshold_value:.0f}ms 임계값을 초과했습니다."
        elif metric_name == 'failed_jobs':
            return f"최근 1시간 실패 작업이 {current_value:.0f}개로 {threshold_value:.0f}개 임계값을 초과했습니다."
        else:
            return f"{threshold.description}: {current_value} (임계값: {threshold_value})"
    
    def _send_alert(self, alert: Alert):
        """알림 발송"""
        for channel in self.notification_channels:
            try:
                if channel == AlertChannel.LOG:
                    self._send_log_alert(alert)
                elif channel == AlertChannel.CONSOLE:
                    self._send_console_alert(alert)
                elif channel == AlertChannel.EMAIL:
                    self._send_email_alert(alert)
                elif channel == AlertChannel.WEBHOOK:
                    self._send_webhook_alert(alert)
            except Exception as e:
                logger.error(f"알림 발송 실패 ({channel.value}): {e}")
    
    def _send_log_alert(self, alert: Alert):
        """로그 알림"""
        log_level = logging.WARNING if alert.level == AlertLevel.WARNING else logging.CRITICAL
        logger.log(log_level, f"ALERT: {alert.title} - {alert.message}")
    
    def _send_console_alert(self, alert: Alert):
        """콘솔 알림"""
        timestamp = alert.timestamp.strftime("%Y-%m-%d %H:%M:%S")
        level_emoji = "⚠️" if alert.level == AlertLevel.WARNING else "🚨"
        print(f"\n{level_emoji} [{timestamp}] {alert.title}")
        print(f"   {alert.message}")
        if alert.additional_data:
            print(f"   추가 정보: {alert.additional_data}")
        print()
    
    def _send_email_alert(self, alert: Alert):
        """이메일 알림"""
        if not self.email_settings['enabled'] or not self.email_settings['to_emails']:
            return
        
        subject = f"[SmartEye Alert] {alert.title}"
        body = self._create_email_body(alert)
        
        try:
            send_mail(
                subject=subject,
                message=body,
                from_email=self.email_settings['from_email'],
                recipient_list=self.email_settings['to_emails'],
                fail_silently=False,
                html_message=self._create_html_email_body(alert)
            )
            logger.info(f"이메일 알림 발송 완료: {alert.id}")
        except Exception as e:
            logger.error(f"이메일 알림 발송 실패: {e}")
    
    def _send_webhook_alert(self, alert: Alert):
        """웹훅 알림"""
        if not self.webhook_settings['enabled'] or not self.webhook_settings['url']:
            return
        
        try:
            import requests
            
            payload = {
                'alert': alert.to_dict(),
                'system': 'SmartEye',
                'timestamp': alert.timestamp.isoformat()
            }
            
            headers = {
                'Content-Type': 'application/json',
                **self.webhook_settings['headers']
            }
            
            response = requests.post(
                self.webhook_settings['url'],
                json=payload,
                headers=headers,
                timeout=10
            )
            
            if response.status_code == 200:
                logger.info(f"웹훅 알림 발송 완료: {alert.id}")
            else:
                logger.error(f"웹훅 알림 발송 실패: HTTP {response.status_code}")
                
        except Exception as e:
            logger.error(f"웹훅 알림 발송 실패: {e}")
    
    def _send_resolution_notification(self, alert: Alert):
        """알림 해결 통지"""
        resolution_message = f"✅ 해결됨: {alert.title}"
        logger.info(resolution_message)
        
        if AlertChannel.CONSOLE in self.notification_channels:
            timestamp = alert.resolved_at.strftime("%Y-%m-%d %H:%M:%S")
            print(f"\n✅ [{timestamp}] 알림 해결: {alert.title}")
            print(f"   지속 시간: {alert.resolved_at - alert.timestamp}")
            print()
    
    def _create_email_body(self, alert: Alert) -> str:
        """이메일 본문 생성"""
        return f"""
SmartEye 시스템 알림

제목: {alert.title}
수준: {alert.level.value.upper()}
시간: {alert.timestamp.strftime("%Y-%m-%d %H:%M:%S")}

내용:
{alert.message}

메트릭: {alert.metric_name}
현재값: {alert.current_value}
임계값: {alert.threshold_value}

추가 정보:
{json.dumps(alert.additional_data, indent=2, ensure_ascii=False) if alert.additional_data else '없음'}

이 알림은 SmartEye 모니터링 시스템에서 자동으로 발송되었습니다.
"""
    
    def _create_html_email_body(self, alert: Alert) -> str:
        """HTML 이메일 본문 생성"""
        color = "#ff6b35" if alert.level == AlertLevel.CRITICAL else "#ffa500"
        
        return f"""
<html>
<body style="font-family: Arial, sans-serif;">
    <div style="background-color: {color}; color: white; padding: 20px; border-radius: 5px;">
        <h2>🚨 SmartEye 시스템 알림</h2>
        <h3>{alert.title}</h3>
    </div>
    
    <div style="padding: 20px;">
        <p><strong>알림 수준:</strong> {alert.level.value.upper()}</p>
        <p><strong>발생 시간:</strong> {alert.timestamp.strftime("%Y-%m-%d %H:%M:%S")}</p>
        <p><strong>메시지:</strong> {alert.message}</p>
        
        <table style="border-collapse: collapse; width: 100%; margin-top: 20px;">
            <tr style="background-color: #f2f2f2;">
                <td style="border: 1px solid #ddd; padding: 8px;"><strong>메트릭</strong></td>
                <td style="border: 1px solid #ddd; padding: 8px;">{alert.metric_name}</td>
            </tr>
            <tr>
                <td style="border: 1px solid #ddd; padding: 8px;"><strong>현재값</strong></td>
                <td style="border: 1px solid #ddd; padding: 8px;">{alert.current_value}</td>
            </tr>
            <tr style="background-color: #f2f2f2;">
                <td style="border: 1px solid #ddd; padding: 8px;"><strong>임계값</strong></td>
                <td style="border: 1px solid #ddd; padding: 8px;">{alert.threshold_value}</td>
            </tr>
        </table>
        
        <p style="margin-top: 20px; font-size: 12px; color: #666;">
            이 알림은 SmartEye 모니터링 시스템에서 자동으로 발송되었습니다.
        </p>
    </div>
</body>
</html>
"""
    
    def start_monitoring(self):
        """모니터링 시작"""
        if self.monitoring_active:
            return
        
        self.monitoring_active = True
        self.monitoring_thread = threading.Thread(target=self._monitoring_loop, daemon=True)
        self.monitoring_thread.start()
        logger.info("알림 시스템 모니터링 시작")
    
    def stop_monitoring(self):
        """모니터링 중지"""
        self.monitoring_active = False
        if self.monitoring_thread:
            self.monitoring_thread.join(timeout=5)
        logger.info("알림 시스템 모니터링 중지")
    
    def _monitoring_loop(self):
        """모니터링 루프"""
        while self.monitoring_active:
            try:
                self._check_all_metrics()
                time.sleep(30)  # 30초마다 체크
            except Exception as e:
                logger.error(f"모니터링 루프 오류: {e}")
                time.sleep(60)  # 오류 시 1분 대기
    
    def _check_all_metrics(self):
        """모든 메트릭 검사"""
        try:
            # 메모리 사용률
            memory = psutil.virtual_memory()
            self.check_metric('memory_percent', memory.percent)
            
            # CPU 사용률
            cpu_percent = psutil.cpu_percent(interval=1)
            self.check_metric('cpu_percent', cpu_percent)
            
            # 디스크 사용률
            disk = psutil.disk_usage('/')
            disk_percent = (disk.used / disk.total) * 100
            self.check_metric('disk_percent', disk_percent)
            
            # 에러율 체크
            error_rate = self._calculate_error_rate()
            self.check_metric('error_rate', error_rate)
            
            # 응답 시간 체크
            avg_response_time = self._get_average_response_time()
            if avg_response_time is not None:
                self.check_metric('response_time', avg_response_time)
            
            # 실패한 작업 수 체크
            failed_jobs_count = self._get_failed_jobs_count()
            self.check_metric('failed_jobs', failed_jobs_count)
            
        except Exception as e:
            logger.error(f"메트릭 검사 실패: {e}")
    
    def _calculate_error_rate(self) -> float:
        """에러율 계산"""
        try:
            now = datetime.now()
            last_hour = now - timedelta(hours=1)
            
            total_jobs = AnalysisJob.objects.filter(created_at__gte=last_hour).count()
            failed_jobs = AnalysisJob.objects.filter(
                created_at__gte=last_hour, 
                status='failed'
            ).count()
            
            if total_jobs == 0:
                return 0.0
            
            return (failed_jobs / total_jobs) * 100
        except Exception:
            return 0.0
    
    def _get_average_response_time(self) -> Optional[float]:
        """평균 응답 시간 조회"""
        try:
            from utils.performance_monitor import get_performance_optimizer
            optimizer = get_performance_optimizer()
            stats = optimizer.get_performance_stats()
            return stats.get('avg_response_time', None)
        except Exception:
            return None
    
    def _get_failed_jobs_count(self) -> float:
        """최근 1시간 실패 작업 수"""
        try:
            now = datetime.now()
            last_hour = now - timedelta(hours=1)
            
            return AnalysisJob.objects.filter(
                created_at__gte=last_hour,
                status='failed'
            ).count()
        except Exception:
            return 0.0
    
    def get_active_alerts(self) -> List[Dict[str, Any]]:
        """활성 알림 목록 조회"""
        return [alert.to_dict() for alert in self.active_alerts.values()]
    
    def get_alert_history(self, hours: int = 24) -> List[Dict[str, Any]]:
        """알림 히스토리 조회"""
        cutoff_time = datetime.now() - timedelta(hours=hours)
        recent_alerts = [
            alert.to_dict() for alert in self.alert_history 
            if alert.timestamp >= cutoff_time
        ]
        return sorted(recent_alerts, key=lambda x: x['timestamp'], reverse=True)
    
    def get_alert_statistics(self) -> Dict[str, Any]:
        """알림 통계"""
        now = datetime.now()
        last_24h = now - timedelta(hours=24)
        last_week = now - timedelta(days=7)
        
        recent_alerts = [alert for alert in self.alert_history if alert.timestamp >= last_24h]
        weekly_alerts = [alert for alert in self.alert_history if alert.timestamp >= last_week]
        
        return {
            'active_alerts': len(self.active_alerts),
            'last_24h_alerts': len(recent_alerts),
            'last_week_alerts': len(weekly_alerts),
            'critical_alerts_24h': len([a for a in recent_alerts if a.level == AlertLevel.CRITICAL]),
            'warning_alerts_24h': len([a for a in recent_alerts if a.level == AlertLevel.WARNING]),
            'most_frequent_metric': self._get_most_frequent_metric(recent_alerts),
            'enabled_thresholds': len([t for t in self.thresholds.values() if t.enabled]),
            'notification_channels': [c.value for c in self.notification_channels]
        }
    
    def _get_most_frequent_metric(self, alerts: List[Alert]) -> Optional[str]:
        """가장 빈번한 알림 메트릭"""
        if not alerts:
            return None
        
        metric_counts = {}
        for alert in alerts:
            metric_counts[alert.metric_name] = metric_counts.get(alert.metric_name, 0) + 1
        
        return max(metric_counts, key=metric_counts.get) if metric_counts else None


# 전역 인스턴스
alert_manager = AlertManager()


def get_alert_manager() -> AlertManager:
    """알림 관리자 인스턴스 반환"""
    return alert_manager


def start_alert_monitoring():
    """알림 모니터링 시작 (Django 앱 시작 시 호출)"""
    alert_manager.start_monitoring()


def stop_alert_monitoring():
    """알림 모니터링 중지 (Django 앱 종료 시 호출)"""
    alert_manager.stop_monitoring()