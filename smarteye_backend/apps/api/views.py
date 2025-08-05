from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from django.db import connection
from django.conf import settings
import psutil
import torch


@api_view(['GET'])
def health_check(request):
    """시스템 헬스 체크"""
    try:
        # 데이터베이스 연결 확인
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
        
        db_status = "healthy"
    except Exception as e:
        db_status = f"error: {str(e)}"
    
    return Response({
        'status': 'healthy',
        'database': db_status,
        'debug': settings.DEBUG,
        'version': '1.0.0'
    })


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def system_status(request):
    """시스템 상태 정보"""
    
    # 메모리 정보
    memory = psutil.virtual_memory()
    
    # GPU 정보 (PyTorch CUDA)
    gpu_info = {
        'available': torch.cuda.is_available(),
        'device_count': torch.cuda.device_count() if torch.cuda.is_available() else 0,
        'current_device': torch.cuda.current_device() if torch.cuda.is_available() else None
    }
    
    if torch.cuda.is_available():
        gpu_info['memory_allocated'] = torch.cuda.memory_allocated() / 1024**3  # GB
        gpu_info['memory_reserved'] = torch.cuda.memory_reserved() / 1024**3  # GB
    
    return Response({
        'cpu': {
            'percent': psutil.cpu_percent(interval=1),
            'count': psutil.cpu_count()
        },
        'memory': {
            'total_gb': memory.total / 1024**3,
            'available_gb': memory.available / 1024**3,
            'percent': memory.percent
        },
        'disk': {
            'total_gb': psutil.disk_usage('/').total / 1024**3,
            'free_gb': psutil.disk_usage('/').free / 1024**3,
            'percent': psutil.disk_usage('/').percent
        },
        'gpu': gpu_info,
        'user': {
            'username': request.user.username,
            'api_quota_used': request.user.api_quota_used,
            'api_quota_limit': request.user.api_quota_limit,
            'can_use_api': request.user.can_use_api()
        }
    })
