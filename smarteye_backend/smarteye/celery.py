"""
Celery 설정
"""

import os
from celery import Celery

# Django 설정 모듈 지정
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')

app = Celery('smarteye')

# Django 설정에서 Celery 설정 로드
app.config_from_object('django.conf:settings', namespace='CELERY')

# Django 앱에서 task 자동 발견
app.autodiscover_tasks()

# 주기적 작업 설정
app.conf.beat_schedule = {
    'cleanup-temp-files': {
        'task': 'apps.analysis.tasks.cleanup_temp_files',
        'schedule': 3600.0,  # 1시간마다 실행
    },
}

app.conf.timezone = 'Asia/Seoul'


@app.task(bind=True)
def debug_task(self):
    print(f'Request: {self.request!r}')
