# Optimized Multi-stage Dockerfile for SmartEye Backend
# Incorporates all code quality improvements and performance optimizations
FROM python:3.12-slim AS base

# SmartEye 성능 최적화 환경 변수
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONPATH=/app \
    DJANGO_SETTINGS_MODULE=smarteye.settings.production \
    # PDF 처리 최적화
    PYTORCH_ENABLE_MPS_FALLBACK=1 \
    OMP_NUM_THREADS=4 \
    # 메모리 최적화
    MALLOC_MMAP_THRESHOLD_=131072 \
    MALLOC_TRIM_THRESHOLD_=131072 \
    # Django 최적화
    DJANGO_DEBUG=False \
    DJANGO_USE_TZ=True \
    # Celery 최적화
    CELERY_OPTIMIZATION=fair \
    C_FORCE_ROOT=1

# 시스템 패키지 설치 (AI/ML 및 문서 처리용) - 최적화됨
RUN apt-get update && apt-get install -y --no-install-recommends \
    # 빌드 도구 (필수만)
    build-essential \
    pkg-config \
    git \
    curl \
    netcat-openbsd \
    # 데이터베이스 클라이언트
    libpq-dev \
    postgresql-client \
    # OCR 도구 (Tesseract + 한국어/영어 모델)
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    # 이미지/문서 처리 최적화 라이브러리
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    libopencv-dev \
    # PDF 처리 지원 (PyMuPDF 최적화)
    libfitz-dev \
    libmupdf-dev \
    # 폰트 (한국어 문서 처리)
    fonts-nanum \
    fonts-noto-cjk \
    fonts-dejavu-core \
    # 멀티미디어 처리
    ffmpeg \
    libmagic1 \
    # 메모리 최적화 도구
    libjemalloc2 \
    # 시스템 정리
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean \
    # jemalloc 메모리 할당자 설정
    && echo '/usr/lib/x86_64-linux-gnu/libjemalloc.so.2' >> /etc/ld.so.preload

# Stage 2: Python dependencies - 최적화된 설치
FROM base AS dependencies

WORKDIR /app

# pip 최적화 및 업그레이드
RUN pip install --no-cache-dir --upgrade pip setuptools wheel

# 의존성 파일 복사 
COPY requirements.txt .

# 의존성 설치 - 멀티스레드 및 최적화 적용
RUN pip install --no-cache-dir -r requirements.txt \
    # PyTorch CPU 최적화 버전 (GPU 없는 환경용)
    --extra-index-url https://download.pytorch.org/whl/cpu \
    # 컴파일 최적화 옵션
    && python -c "import torch; print('PyTorch version:', torch.__version__)" \
    && python -c "import cv2; print('OpenCV version:', cv2.__version__)" \
    && python -c "import fitz; print('PyMuPDF version:', fitz.VersionBind)" \
    # 불필요한 캐시 제거
    && pip cache purge

# Stage 3: Final application image - 최적화 및 보안 강화
FROM dependencies AS final

# 작업 디렉토리 설정
WORKDIR /app

# 애플리케이션 사용자 생성 (보안 강화)
RUN groupadd -r smarteye && \
    useradd -r -g smarteye -d /app -s /bin/bash smarteye && \
    # 성능을 위한 추가 디렉토리 생성
    mkdir -p /app/logs /app/media /app/staticfiles /app/tmp \
             /app/cache /app/uploads /app/exports \
    && chown -R smarteye:smarteye /app

# 애플리케이션 코드 복사 (최적화된 .dockerignore 고려)
COPY --chown=smarteye:smarteye . .

# Django 스태틱 파일 수집 (프로덕션 최적화)
RUN python manage.py collectstatic --noinput --clear --settings=smarteye.settings.production

# 코드 검증 및 최적화 (선택적)
RUN python -m py_compile smarteye/wsgi.py && \
    python -c "import django; django.setup(); print('Django configuration validated')"

# 엔트리포인트 및 헬스체크 스크립트 설정
COPY docker-entrypoint.sh healthcheck.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh /usr/local/bin/healthcheck.sh

# 포트 노출
EXPOSE 8000

# 보안을 위한 사용자 변경
USER smarteye

# 성능 최적화된 헬스체크
HEALTHCHECK --interval=45s --timeout=15s --start-period=60s --retries=3 \
    CMD /usr/local/bin/healthcheck.sh

# 성능 최적화된 Gunicorn 설정
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["gunicorn", "smarteye.wsgi:application", \
     "--bind", "0.0.0.0:8000", \
     "--workers", "4", \
     "--worker-class", "gevent", \
     "--worker-connections", "1000", \
     "--max-requests", "1000", \
     "--max-requests-jitter", "100", \
     "--timeout", "120", \
     "--keep-alive", "5", \
     "--log-level", "info", \
     "--access-logfile", "/app/logs/gunicorn_access.log", \
     "--error-logfile", "/app/logs/gunicorn_error.log"]
