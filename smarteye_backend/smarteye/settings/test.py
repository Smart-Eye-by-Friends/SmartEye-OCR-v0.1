"""
테스트 환경용 Django 설정
"""

from .base import *
import os

# 테스트 환경 디버그 설정
DEBUG = False
TESTING = True

# 테스트 데이터베이스 설정
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql',
        'NAME': env('DATABASE_NAME', default='smarteye_test'),
        'USER': env('DATABASE_USER', default='smarteye_test'),
        'PASSWORD': env('DATABASE_PASSWORD', default='test_password_123'),
        'HOST': env('DATABASE_HOST', default='localhost'),
        'PORT': env('DATABASE_PORT', default='5433'),
        'OPTIONS': {
            'charset': 'utf8',
        },
        'TEST': {
            'NAME': 'test_smarteye_test',
            'CHARSET': 'utf8',
            'COLLATION': 'utf8_general_ci',
        }
    }
}

# Redis 테스트 설정
CACHES = {
    'default': {
        'BACKEND': 'django_redis.cache.RedisCache',
        'LOCATION': env('REDIS_URL', default='redis://localhost:6380/0'),
        'OPTIONS': {
            'CLIENT_CLASS': 'django_redis.client.DefaultClient',
        },
        'KEY_PREFIX': 'smarteye_test',
        'TIMEOUT': 300,
    }
}

# Celery 테스트 설정 (동기 실행)
CELERY_TASK_ALWAYS_EAGER = True
CELERY_TASK_EAGER_PROPAGATES = True
CELERY_BROKER_URL = env('CELERY_BROKER_URL', default='redis://localhost:6380/0')
CELERY_RESULT_BACKEND = env('CELERY_RESULT_BACKEND', default='redis://localhost:6380/0')

# 채널 테스트 설정
CHANNEL_LAYERS = {
    'default': {
        'BACKEND': 'channels.layers.InMemoryChannelLayer',
    },
}

# 파일 업로드 테스트 설정
MEDIA_ROOT = env('MEDIA_ROOT', default=BASE_DIR / 'test_media')
MEDIA_URL = '/test_media/'

# 외부 API 비활성화 (테스트용)
SMARTEYE_CONFIG.update({
    'OPENAI_API_KEY': 'test-key-mock',
    'DISABLE_EXTERNAL_APIS': True,
    'MAX_UPLOAD_SIZE': '10MB',  # 테스트용으로 작게 설정
    'BATCH_SIZE': 1,  # 테스트 속도 향상
    'MAX_WORKERS': 1,
    'MEMORY_LIMIT_MB': 100,
})

# 로깅 테스트 설정
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'handlers': {
        'console': {
            'level': 'DEBUG',
            'class': 'logging.StreamHandler',
        },
        'file': {
            'level': 'DEBUG',
            'class': 'logging.FileHandler',
            'filename': BASE_DIR / 'logs' / 'test.log',
        },
    },
    'root': {
        'handlers': ['console'],
        'level': 'WARNING',
    },
    'loggers': {
        'smarteye': {
            'handlers': ['console', 'file'],
            'level': 'DEBUG',
            'propagate': False,
        },
        'django.db.backends': {
            'handlers': ['console'],
            'level': 'INFO',  # SQL 쿼리 로깅
            'propagate': False,
        },
    },
}

# 이메일 테스트 설정
EMAIL_BACKEND = 'django.core.mail.backends.locmem.EmailBackend'

# 보안 설정 완화 (테스트용)
SECRET_KEY = 'test-secret-key-for-testing-only-not-for-production'
ALLOWED_HOSTS = ['*']
CORS_ALLOW_ALL_ORIGINS = True

# 비밀번호 검증 비활성화 (테스트 속도 향상)
AUTH_PASSWORD_VALIDATORS = []

# 정적 파일 테스트 설정
STATICFILES_STORAGE = 'django.contrib.staticfiles.storage.StaticFilesStorage'

# JWT 토큰 만료 시간 단축 (테스트용)
SIMPLE_JWT.update({
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=5),
    'REFRESH_TOKEN_LIFETIME': timedelta(minutes=10),
})

# 테스트 fixtures 디렉토리
FIXTURE_DIRS = [
    BASE_DIR / 'tests' / 'fixtures',
]

# 테스트용 임시 디렉토리
import tempfile
TEST_TEMP_DIR = tempfile.mkdtemp(prefix='smarteye_test_')

# Performance settings for faster tests
DEFAULT_AUTO_FIELD = 'django.db.models.AutoField'
USE_TZ = True
USE_I18N = False  # 테스트에서 국제화 비활성화로 속도 향상

# Test-specific middleware (일부 미들웨어 제거)
MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'corsheaders.middleware.CorsMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    # 테스트에서는 ClickjackingMiddleware 제거
]