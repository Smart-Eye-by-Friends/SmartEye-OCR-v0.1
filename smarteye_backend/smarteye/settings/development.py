"""
Development settings for SmartEye project.
"""

import environ

# Import all settings from base
from .base import (
    BASE_DIR, SECRET_KEY, ALLOWED_HOSTS, DJANGO_APPS, THIRD_PARTY_APPS, 
    LOCAL_APPS, INSTALLED_APPS, MIDDLEWARE, ROOT_URLCONF, TEMPLATES,
    WSGI_APPLICATION, ASGI_APPLICATION, AUTH_PASSWORD_VALIDATORS,
    LANGUAGE_CODE, TIME_ZONE, USE_I18N, USE_TZ, STATIC_URL, STATIC_ROOT,
    STATICFILES_DIRS, MEDIA_URL, MEDIA_ROOT, DEFAULT_AUTO_FIELD,
    AUTH_USER_MODEL, REST_FRAMEWORK, SIMPLE_JWT, CORS_ALLOWED_ORIGINS,
    CELERY_BROKER_URL, CELERY_RESULT_BACKEND, CELERY_ACCEPT_CONTENT,
    CELERY_TASK_SERIALIZER, CELERY_RESULT_SERIALIZER, CELERY_TIMEZONE,
    CHANNEL_LAYERS, FILE_UPLOAD_MAX_MEMORY_SIZE, DATA_UPLOAD_MAX_MEMORY_SIZE,
    SMARTEYE_CONFIG, LOGGING, SPECTACULAR_SETTINGS
)

# Environment variables
env = environ.Env()

# Development specific settings
DEBUG = True

# Use environment variables for database settings
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql',
        'NAME': env('DATABASE_NAME', default='smarteye_dev'),
        'USER': env('DATABASE_USER', default='smarteye_user'),
        'PASSWORD': env('DATABASE_PASSWORD', default='password'),
        'HOST': env('DATABASE_HOST', default='localhost'),
        'PORT': env('DATABASE_PORT', default='5432'),
    }
}

# Development email backend
EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'

# Allow all origins in development
CORS_ALLOW_ALL_ORIGINS = True

# Debug toolbar for development
if DEBUG:
    try:
        import debug_toolbar
        INSTALLED_APPS = list(INSTALLED_APPS) + ['debug_toolbar']
        MIDDLEWARE = list(MIDDLEWARE) + ['debug_toolbar.middleware.DebugToolbarMiddleware']
    except ImportError:
        pass

# Debug toolbar settings
INTERNAL_IPS = [
    '127.0.0.1',
    'localhost',
]

# Celery settings for development
CELERY_TASK_ALWAYS_EAGER = False
CELERY_TASK_EAGER_PROPAGATES = True

# Development file storage
DEFAULT_FILE_STORAGE = 'django.core.files.storage.FileSystemStorage'

# Override SmartEye development settings
SMARTEYE_CONFIG = SMARTEYE_CONFIG.copy()
SMARTEYE_CONFIG.update({
    'DEBUG_MODE': True,
    'MOCK_API_CALLS': False,
    'SAVE_DEBUG_IMAGES': True,
    'LOG_LEVEL': 'DEBUG',
    'BATCH_SIZE': 2,
    'MAX_WORKERS': 2,
    'MEMORY_LIMIT_MB': 500,
})

# Override logging for development
import copy
LOGGING = copy.deepcopy(LOGGING)
LOGGING['handlers']['file']['level'] = 'DEBUG'
LOGGING['handlers']['console']['level'] = 'DEBUG'
LOGGING['root']['level'] = 'DEBUG'
LOGGING['loggers']['django']['level'] = 'DEBUG'
