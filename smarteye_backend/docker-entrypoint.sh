#!/bin/bash

# =============================================================================
# Docker Entrypoint Script for SmartEye Backend
# Optimized for performance and includes all code quality improvements
# =============================================================================

set -e

# 색상 출력을 위한 함수
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 환경 변수 기본값 설정
export DJANGO_SETTINGS_MODULE=${DJANGO_SETTINGS_MODULE:-smarteye.settings.production}
export PYTHONDONTWRITEBYTECODE=1
export PYTHONUNBUFFERED=1

log_info "Starting SmartEye Backend initialization..."
log_info "Django Settings Module: $DJANGO_SETTINGS_MODULE"

# 필수 디렉토리 생성
log_info "Creating necessary directories..."
mkdir -p /app/logs /app/media /app/staticfiles /app/tmp /app/cache /app/uploads /app/exports

# 데이터베이스 연결 대기 (개선된 로직)
wait_for_service() {
    local service_name="$1"
    local host="$2"
    local port="$3"
    local max_attempts=60
    local attempt=1
    
    log_info "Waiting for $service_name ($host:$port)..."
    
    while [ $attempt -le $max_attempts ]; do
        if nc -z "$host" "$port" 2>/dev/null; then
            log_info "$service_name is ready!"
            return 0
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            log_error "$service_name is not available after $max_attempts attempts"
            exit 1
        fi
        
        sleep 1
        attempt=$((attempt + 1))
    done
}

# 데이터베이스 대기
if [ "$DATABASE_HOST" ]; then
    wait_for_service "Database" "$DATABASE_HOST" "${DATABASE_PORT:-5432}"
fi

# Redis 대기 (채널 및 캐시용)
if [ "$REDIS_URL" ] || [ "$REDIS_HOST" ]; then
    REDIS_HOST_CHECK=${REDIS_HOST:-redis}
    REDIS_PORT_CHECK=${REDIS_PORT:-6379}
    wait_for_service "Redis" "$REDIS_HOST_CHECK" "$REDIS_PORT_CHECK"
    
    # Redis 연결 테스트
    log_info "Testing Redis connection..."
    REDIS_CHECK_URL=${REDIS_URL:-"redis://${REDIS_HOST_CHECK}:${REDIS_PORT_CHECK}/0"}
    if python -c "import redis; r=redis.from_url('$REDIS_CHECK_URL'); r.ping()" 2>/dev/null; then
        log_info "Redis connection successful"
    else
        log_error "Redis connection failed"
        exit 1
    fi
fi

# Django 검사 실행 (코드 품질 개선으로 안전)
log_info "Running Django system checks..."
python manage.py check --deploy --settings="$DJANGO_SETTINGS_MODULE"

# 데이터베이스 마이그레이션 (로그 최적화)
log_info "Running database migrations..."
python manage.py migrate --noinput --verbosity=1

# 정적 파일 수집 (최적화)
log_info "Collecting static files..."
python manage.py collectstatic --noinput --clear --verbosity=1

# 임시 파일 정리 (파일 리소스 관리 개선)
log_info "Cleaning up temporary files..."
find /app/tmp -type f -mtime +1 -delete 2>/dev/null || true
find /app/logs -name "*.log.*" -mtime +7 -delete 2>/dev/null || true

# 캐시 워밍업 (선택적)
if [ "$CACHE_WARMUP" = "true" ]; then
    log_info "Warming up caches..."
    python manage.py shell -c "
import django
django.setup()
from django.core.cache import cache
from django.conf import settings
cache.set('warmup', 'ready', 60)
print('Cache warmed up successfully')
" 2>/dev/null || log_warn "Cache warmup failed, continuing..."
fi

# 슈퍼유저 생성 (개선된 로직)
if [ "$DJANGO_SUPERUSER_USERNAME" ] && [ "$DJANGO_SUPERUSER_EMAIL" ] && [ "$DJANGO_SUPERUSER_PASSWORD" ]; then
    log_info "Checking/Creating superuser..."
    python manage.py shell -c "
from django.contrib.auth import get_user_model
User = get_user_model()
username = '$DJANGO_SUPERUSER_USERNAME'
email = '$DJANGO_SUPERUSER_EMAIL'
password = '$DJANGO_SUPERUSER_PASSWORD'

if not User.objects.filter(username=username).exists():
    User.objects.create_superuser(username, email, password)
    print('✓ Superuser created successfully')
else:
    print('✓ Superuser already exists')
"
fi

# 모델 가중치 파일 확인 (AI/ML 모델용)
if [ -d "/app/models" ]; then
    log_info "Checking ML model files..."
    python -c "
import os
from pathlib import Path
models_dir = Path('/app/models')
if models_dir.exists():
    model_files = list(models_dir.rglob('*.pt')) + list(models_dir.rglob('*.pth'))
    if model_files:
        print(f'✓ Found {len(model_files)} model files')
    else:
        print('⚠ No model files found, will download on first use')
"
fi

# 서비스 시작 메시지
log_info "SmartEye Backend initialization completed successfully!"
log_info "Starting application with command: $*"

# 서비스별 실행 로직
if [ "$1" = "celery" ]; then
    log_info "Starting Celery worker..."
    # Celery worker 성능 최적화 설정
    exec celery -A smarteye worker \
        --loglevel=info \
        --concurrency=4 \
        --max-tasks-per-child=100 \
        --optimization=fair \
        --without-gossip \
        --without-mingle
elif [ "$1" = "celery-beat" ]; then
    log_info "Starting Celery beat scheduler..."
    exec celery -A smarteye beat \
        --loglevel=info \
        --schedule=/app/tmp/celerybeat-schedule \
        --pidfile=/app/tmp/celerybeat.pid
elif [ "$1" = "python" ] && [ "$2" = "manage.py" ] && [ "$3" = "runserver" ]; then
    log_info "Starting Django development server..."
    exec "$@"
else
    log_info "Starting production server (Gunicorn)..."
    exec "$@"
fi
