"""
SmartEye 모니터링 API Views

성능 메트릭, 시스템 상태, 로그 조회 등을 제공합니다.
"""

import psutil
import logging
from datetime import datetime, timedelta
from typing import Dict, Any, List
from django.http import JsonResponse
from django.views.decorators.http import require_http_methods
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.decorators import user_passes_test
from django.shortcuts import render
from django.core.cache import cache
from rest_framework import status
from rest_framework.response import Response
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAdminUser
from apps.analysis.models import AnalysisJob
from utils.api_optimization import get_api_optimizer
from utils.performance_monitor import get_performance_optimizer
from utils.alert_system import get_alert_manager
import json
import os

logger = logging.getLogger(__name__)


def is_admin_or_staff(user):
    """관리자 또는 스태프 여부 확인"""
    return user.is_authenticated and (user.is_staff or user.is_superuser)


@user_passes_test(is_admin_or_staff)
def monitoring_dashboard(request):
    """모니터링 대시보드 페이지"""
    return render(request, 'admin/monitoring_dashboard.html')


@api_view(['GET'])
@permission_classes([IsAdminUser])
def system_metrics(request):
    """시스템 전체 메트릭 조회"""
    try:
        # 메모리 정보
        memory = psutil.virtual_memory()
        memory_info = {
            'total': memory.total,
            'available': memory.available,
            'percent': round(memory.percent, 1),
            'used': memory.used,
            'free': memory.free
        }
        
        # CPU 정보
        cpu_percent = psutil.cpu_percent(interval=1)
        cpu_count = psutil.cpu_count()
        
        # 디스크 정보
        disk = psutil.disk_usage('/')
        disk_info = {
            'total': disk.total,
            'used': disk.used,
            'free': disk.free,
            'percent': round((disk.used / disk.total) * 100, 1)
        }
        
        # 프로세스 정보
        process_count = len(psutil.pids())
        
        # 시스템 상태 판단
        system_health = 'healthy'
        if memory.percent > 85 or cpu_percent > 90 or disk_info['percent'] > 90:
            system_health = 'warning'
        if memory.percent > 95 or cpu_percent > 95 or disk_info['percent'] > 95:
            system_health = 'critical'
        
        # API 성능 메트릭
        api_optimizer = get_api_optimizer()
        cache_stats = api_optimizer.get_cache_stats()
        
        # 작업 통계
        job_stats = get_job_statistics()
        
        # 성능 최적화 메트릭
        try:
            performance_optimizer = get_performance_optimizer()
            performance_stats = performance_optimizer.get_performance_stats()
        except:
            performance_stats = {}
        
        metrics = {
            'timestamp': datetime.now().isoformat(),
            'system_health': system_health,
            'memory': memory_info,
            'cpu': {
                'percent': cpu_percent,
                'count': cpu_count
            },
            'disk': disk_info,
            'processes': process_count,
            'api_performance': {
                'avg_response_time': performance_stats.get('avg_response_time', 0),
                'total_requests': cache_stats.get('total_requests', 0),
                'cache_hit_rate': cache_stats.get('cache_hit_rate', '0%')
            },
            'cache': {
                'hits': cache_stats.get('cache_hits', 0),
                'misses': cache_stats.get('cache_misses', 0),
                'compressed_responses': cache_stats.get('compressed_responses', 0)
            },
            'jobs': job_stats,
            'errors': get_error_statistics()
        }
        
        return Response(metrics)
        
    except Exception as e:
        logger.error(f"시스템 메트릭 조회 실패: {e}")
        return Response(
            {'error': '시스템 메트릭을 가져올 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['GET'])
@permission_classes([IsAdminUser])
def memory_metrics(request):
    """메모리 사용량 상세 정보"""
    try:
        memory = psutil.virtual_memory()
        swap = psutil.swap_memory()
        
        # 프로세스별 메모리 사용량 TOP 10
        processes = []
        for proc in psutil.process_iter(['pid', 'name', 'memory_percent', 'memory_info']):
            try:
                proc_info = proc.info
                if proc_info['memory_percent'] > 0.1:  # 0.1% 이상만
                    processes.append({
                        'pid': proc_info['pid'],
                        'name': proc_info['name'],
                        'memory_percent': round(proc_info['memory_percent'], 2),
                        'memory_mb': round(proc_info['memory_info'].rss / 1024 / 1024, 1)
                    })
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        
        # 메모리 사용량 기준으로 정렬
        processes.sort(key=lambda x: x['memory_percent'], reverse=True)
        top_processes = processes[:10]
        
        return Response({
            'virtual_memory': {
                'total': memory.total,
                'available': memory.available,
                'percent': round(memory.percent, 1),
                'used': memory.used,
                'free': memory.free,
                'active': memory.active,
                'inactive': memory.inactive,
                'buffers': memory.buffers,
                'cached': memory.cached,
                'shared': memory.shared
            },
            'swap_memory': {
                'total': swap.total,
                'used': swap.used,
                'free': swap.free,
                'percent': round(swap.percent, 1)
            },
            'top_processes': top_processes,
            'memory_optimization': get_memory_optimization_stats()
        })
        
    except Exception as e:
        logger.error(f"메모리 메트릭 조회 실패: {e}")
        return Response(
            {'error': '메모리 메트릭을 가져올 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['GET'])
@permission_classes([IsAdminUser])
def alert_status(request):
    """알림 시스템 상태 조회"""
    try:
        alert_manager = get_alert_manager()
        
        return Response({
            'active_alerts': alert_manager.get_active_alerts(),
            'alert_statistics': alert_manager.get_alert_statistics(),
            'alert_history': alert_manager.get_alert_history(hours=24),
            'thresholds': {
                name: {
                    'metric_name': threshold.metric_name,
                    'warning_value': threshold.warning_value,
                    'critical_value': threshold.critical_value,
                    'enabled': threshold.enabled,
                    'description': threshold.description
                }
                for name, threshold in alert_manager.thresholds.items()
            }
        })
        
    except Exception as e:
        logger.error(f"알림 상태 조회 실패: {e}")
        return Response(
            {'error': '알림 상태를 가져올 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['POST'])
@permission_classes([IsAdminUser])
def update_threshold(request):
    """임계값 설정 업데이트"""
    try:
        metric_name = request.data.get('metric_name')
        if not metric_name:
            return Response(
                {'error': 'metric_name이 필요합니다'},
                status=status.HTTP_400_BAD_REQUEST
            )
        
        alert_manager = get_alert_manager()
        
        # 업데이트할 값들 추출
        update_data = {}
        for key in ['warning_value', 'critical_value', 'enabled', 'check_interval', 'min_duration']:
            if key in request.data:
                update_data[key] = request.data[key]
        
        if not update_data:
            return Response(
                {'error': '업데이트할 값이 없습니다'},
                status=status.HTTP_400_BAD_REQUEST
            )
        
        alert_manager.update_threshold(metric_name, **update_data)
        
        return Response({
            'message': f'{metric_name} 임계값이 업데이트되었습니다',
            'updated_threshold': {
                name: {
                    'metric_name': threshold.metric_name,
                    'warning_value': threshold.warning_value,
                    'critical_value': threshold.critical_value,
                    'enabled': threshold.enabled,
                    'description': threshold.description
                }
                for name, threshold in alert_manager.thresholds.items()
                if name == metric_name
            }
        })
        
    except Exception as e:
        logger.error(f"임계값 업데이트 실패: {e}")
        return Response(
            {'error': '임계값 업데이트에 실패했습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['GET'])
@permission_classes([IsAdminUser])
def recent_logs(request):
    """최근 로그 조회"""
    try:
        lines = int(request.GET.get('lines', 50))
        level = request.GET.get('level', 'INFO')
        
        logs = get_recent_logs(lines, level)
        
        return Response(logs)
        
    except Exception as e:
        logger.error(f"로그 조회 실패: {e}")
        return Response(
            {'error': '로그를 가져올 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['GET'])
@permission_classes([IsAdminUser])
def api_performance(request):
    """API 성능 통계"""
    try:
        hours = int(request.GET.get('hours', 24))
        
        # 성능 최적화 도구에서 메트릭 수집
        try:
            performance_optimizer = get_performance_optimizer()
            performance_data = performance_optimizer.get_detailed_metrics(hours)
        except:
            performance_data = {}
        
        # API 최적화 도구에서 캐시 메트릭 수집
        api_optimizer = get_api_optimizer()
        cache_stats = api_optimizer.get_cache_stats()
        
        return Response({
            'time_range_hours': hours,
            'performance_metrics': performance_data,
            'cache_statistics': cache_stats,
            'response_time_percentiles': get_response_time_percentiles(),
            'endpoint_statistics': get_endpoint_statistics()
        })
        
    except Exception as e:
        logger.error(f"API 성능 통계 조회 실패: {e}")
        return Response(
            {'error': 'API 성능 통계를 가져올 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


@api_view(['POST'])
@permission_classes([IsAdminUser])
def system_health_check(request):
    """시스템 상태 점검"""
    try:
        health_results = {
            'timestamp': datetime.now().isoformat(),
            'overall_status': 'healthy',
            'checks': {}
        }
        
        # 메모리 점검
        memory = psutil.virtual_memory()
        health_results['checks']['memory'] = {
            'status': 'healthy' if memory.percent < 85 else 'warning' if memory.percent < 95 else 'critical',
            'usage_percent': memory.percent,
            'message': f'메모리 사용률: {memory.percent}%'
        }
        
        # CPU 점검
        cpu_percent = psutil.cpu_percent(interval=1)
        health_results['checks']['cpu'] = {
            'status': 'healthy' if cpu_percent < 80 else 'warning' if cpu_percent < 95 else 'critical',
            'usage_percent': cpu_percent,
            'message': f'CPU 사용률: {cpu_percent}%'
        }
        
        # 디스크 점검
        disk = psutil.disk_usage('/')
        disk_percent = (disk.used / disk.total) * 100
        health_results['checks']['disk'] = {
            'status': 'healthy' if disk_percent < 80 else 'warning' if disk_percent < 95 else 'critical',
            'usage_percent': round(disk_percent, 1),
            'message': f'디스크 사용률: {disk_percent:.1f}%'
        }
        
        # 데이터베이스 연결 점검
        try:
            from django.db import connection
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1")
            health_results['checks']['database'] = {
                'status': 'healthy',
                'message': '데이터베이스 연결 정상'
            }
        except Exception as e:
            health_results['checks']['database'] = {
                'status': 'critical',
                'message': f'데이터베이스 연결 실패: {str(e)}'
            }
        
        # Redis 연결 점검 (캐시)
        try:
            cache.set('health_check', 'ok', 60)
            cache.get('health_check')
            health_results['checks']['redis'] = {
                'status': 'healthy',
                'message': 'Redis 연결 정상'
            }
        except Exception as e:
            health_results['checks']['redis'] = {
                'status': 'critical',
                'message': f'Redis 연결 실패: {str(e)}'
            }
        
        # OpenAI API 연결 점검
        try:
            from core.tspm.image_description_processor import ImageDescriptionProcessor
            processor = ImageDescriptionProcessor()
            if processor.test_api_connection():
                health_results['checks']['openai_api'] = {
                    'status': 'healthy',
                    'message': 'OpenAI API 연결 정상'
                }
            else:
                health_results['checks']['openai_api'] = {
                    'status': 'warning',
                    'message': 'OpenAI API 연결 확인 필요'
                }
        except Exception as e:
            health_results['checks']['openai_api'] = {
                'status': 'warning',
                'message': f'OpenAI API 점검 실패: {str(e)}'
            }
        
        # 전체 상태 결정
        statuses = [check['status'] for check in health_results['checks'].values()]
        if 'critical' in statuses:
            health_results['overall_status'] = 'critical'
        elif 'warning' in statuses:
            health_results['overall_status'] = 'warning'
        
        return Response(health_results)
        
    except Exception as e:
        logger.error(f"시스템 상태 점검 실패: {e}")
        return Response(
            {'error': '시스템 상태 점검을 수행할 수 없습니다'},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR
        )


# 헬퍼 함수들

def get_job_statistics() -> Dict[str, Any]:
    """작업 통계 조회"""
    try:
        now = datetime.now()
        today = now.date()
        
        # 오늘의 작업 통계
        today_jobs = AnalysisJob.objects.filter(created_at__date=today)
        
        return {
            'active': AnalysisJob.objects.filter(status='processing').count(),
            'pending': AnalysisJob.objects.filter(status='pending').count(),
            'processing': AnalysisJob.objects.filter(status='processing').count(),
            'completed': AnalysisJob.objects.filter(status='completed').count(),
            'failed': AnalysisJob.objects.filter(status='failed').count(),
            'today_total': today_jobs.count(),
            'today_completed': today_jobs.filter(status='completed').count(),
            'today_failed': today_jobs.filter(status='failed').count()
        }
    except Exception:
        return {
            'active': 0, 'pending': 0, 'processing': 0,
            'completed': 0, 'failed': 0, 'today_total': 0,
            'today_completed': 0, 'today_failed': 0
        }


def get_error_statistics() -> Dict[str, Any]:
    """에러 통계 조회"""
    try:
        now = datetime.now()
        last_24h = now - timedelta(hours=24)
        
        # 최근 24시간 작업 통계
        recent_jobs = AnalysisJob.objects.filter(created_at__gte=last_24h)
        total_jobs = recent_jobs.count()
        failed_jobs = recent_jobs.filter(status='failed').count()
        
        error_rate = (failed_jobs / total_jobs * 100) if total_jobs > 0 else 0
        
        # 시간별 에러 수 (최근 24시간)
        hourly_errors = []
        for i in range(24):
            hour_start = now - timedelta(hours=i+1)
            hour_end = now - timedelta(hours=i)
            hour_errors = AnalysisJob.objects.filter(
                status='failed',
                created_at__gte=hour_start,
                created_at__lt=hour_end
            ).count()
            
            hourly_errors.append({
                'hour': hour_start.strftime('%H:00'),
                'count': hour_errors
            })
        
        return {
            'rate': round(error_rate, 1),
            'total_failures': failed_jobs,
            'total_jobs': total_jobs,
            'hourly': list(reversed(hourly_errors))
        }
    except Exception:
        return {
            'rate': 0,
            'total_failures': 0,
            'total_jobs': 0,
            'hourly': []
        }


def get_recent_logs(lines: int = 50, level: str = 'INFO') -> List[Dict[str, str]]:
    """최근 로그 조회"""
    try:
        log_file = '/app/logs/django.log'
        if not os.path.exists(log_file):
            return [{'timestamp': datetime.now().isoformat(), 'level': 'INFO', 'message': '로그 파일이 없습니다.'}]
        
        logs = []
        with open(log_file, 'r', encoding='utf-8') as f:
            # 파일 끝에서부터 읽기
            lines_list = f.readlines()
            recent_lines = lines_list[-lines:] if len(lines_list) > lines else lines_list
            
            for line in recent_lines:
                try:
                    # 로그 파싱 (간단한 형태)
                    if ' - ' in line:
                        parts = line.strip().split(' - ', 2)
                        if len(parts) >= 3:
                            timestamp = parts[0]
                            log_level = parts[1]
                            message = parts[2]
                            
                            if level.upper() in log_level.upper() or level.upper() == 'ALL':
                                logs.append({
                                    'timestamp': timestamp,
                                    'level': log_level,
                                    'message': message
                                })
                except Exception:
                    continue
        
        return logs[-lines:] if len(logs) > lines else logs
        
    except Exception as e:
        return [{'timestamp': datetime.now().isoformat(), 'level': 'ERROR', 'message': f'로그 읽기 실패: {str(e)}'}]


def get_memory_optimization_stats() -> Dict[str, Any]:
    """메모리 최적화 통계"""
    try:
        from utils.performance_monitor import get_performance_optimizer
        optimizer = get_performance_optimizer()
        return optimizer.get_memory_stats()
    except Exception:
        return {
            'optimal_batch_size': 5,
            'memory_pressure': 'low',
            'gc_collections': 0,
            'memory_leaks_detected': False
        }


def get_response_time_percentiles() -> Dict[str, float]:
    """응답 시간 백분위수"""
    try:
        from utils.performance_monitor import get_performance_optimizer
        optimizer = get_performance_optimizer()
        return optimizer.get_response_time_percentiles()
    except Exception:
        return {
            'p50': 0.0,
            'p75': 0.0,
            'p90': 0.0,
            'p95': 0.0,
            'p99': 0.0
        }


def get_endpoint_statistics() -> List[Dict[str, Any]]:
    """엔드포인트별 통계"""
    try:
        from utils.performance_monitor import get_performance_optimizer
        optimizer = get_performance_optimizer()
        return optimizer.get_endpoint_stats()
    except Exception:
        return []