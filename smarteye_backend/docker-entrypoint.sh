#!/bin/bash

# Docker Entrypoint Script for SmartEye Backend

set -e

# 환경 변수 기본값 설정
export DJANGO_SETTINGS_MODULE=${DJANGO_SETTINGS_MODULE:-smarteye.settings.production}

# 데이터베이스 대기
if [ "$DATABASE_HOST" ]; then
    echo "Waiting for database..."
    while ! nc -z $DATABASE_HOST $DATABASE_PORT; do
        sleep 0.1
    done
    echo "Database started"
fi

# Redis 대기
if [ "$REDIS_URL" ]; then
    echo "Waiting for Redis..."
    until redis-cli -u $REDIS_URL ping; do
        sleep 0.1
    done
    echo "Redis started"
fi

# 데이터베이스 마이그레이션
echo "Running database migrations..."
python manage.py migrate --noinput

# 정적 파일 수집
echo "Collecting static files..."
python manage.py collectstatic --noinput

# 슈퍼유저 생성 (환경 변수가 설정된 경우)
if [ "$DJANGO_SUPERUSER_USERNAME" ] && [ "$DJANGO_SUPERUSER_EMAIL" ] && [ "$DJANGO_SUPERUSER_PASSWORD" ]; then
    echo "Creating superuser..."
    python manage.py shell << EOF
from django.contrib.auth import get_user_model
User = get_user_model()
if not User.objects.filter(username='$DJANGO_SUPERUSER_USERNAME').exists():
    User.objects.create_superuser('$DJANGO_SUPERUSER_USERNAME', '$DJANGO_SUPERUSER_EMAIL', '$DJANGO_SUPERUSER_PASSWORD')
    print('Superuser created successfully')
else:
    print('Superuser already exists')
EOF
fi

echo "Starting SmartEye Backend..."
exec "$@"
