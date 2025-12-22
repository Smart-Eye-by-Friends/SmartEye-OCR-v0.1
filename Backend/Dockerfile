# ============================================================================
# SmartEyeSsen Backend - Hugging Face Spaces Dockerfile
# ============================================================================
# Hugging Face Spaces 배포 전용 Dockerfile
# - 16GB RAM 활용 (DocLayout-YOLO + PyTorch)
# - 비-루트 사용자 실행 (보안 요구사항)
# - 포트 7860 필수 (HF Spaces 표준)
# ============================================================================

FROM python:3.10-slim

# ============================================================================
# 1. 시스템 패키지 설치 (Root 권한 필요)
# ============================================================================
USER root

RUN apt-get update && apt-get install -y --no-install-recommends \
    # OCR 엔진
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    # OpenCV 의존성
    libgl1 \
    libglib2.0-0 \
    # 빌드 도구
    gcc \
    g++ \
    # Git (DocLayout-YOLO 설치용)
    git \
    # 로케일 설정
    locales \
    && rm -rf /var/lib/apt/lists/*

# UTF-8 로케일 설정 (한글 지원)
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    sed -i '/ko_KR.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen

# ============================================================================
# 2. 비-루트 사용자 생성 (Hugging Face Spaces 보안 요구사항)
# ============================================================================
RUN useradd -m -u 1000 user
USER user

# 환경 변수 설정
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH \
    LANG=ko_KR.UTF-8 \
    LANGUAGE=ko_KR:en \
    LC_ALL=ko_KR.UTF-8 \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# ============================================================================
# 3. 작업 디렉토리 설정
# ============================================================================
WORKDIR $HOME/app

# ============================================================================
# 4. Python 의존성 설치
# ============================================================================
# requirements.txt 먼저 복사 (Docker 캐시 활용)
COPY --chown=user requirements.txt .

# PyPI 패키지 설치
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# DocLayout-YOLO 설치 (Git 직접 설치)
RUN pip install --no-cache-dir git+https://github.com/opendatalab/DocLayout-YOLO.git

# ============================================================================
# 5. 애플리케이션 코드 복사
# ============================================================================
COPY --chown=user . .

# ============================================================================
# 6. 임시 저장소 디렉토리 생성
# ============================================================================
# HF Spaces는 /tmp 외에는 쓰기 권한 제한될 수 있음
RUN mkdir -p /tmp/uploads && \
    mkdir -p $HOME/app/uploads $HOME/app/static $HOME/app/test_pipeline_outputs && \
    chmod -R 755 $HOME/app/uploads $HOME/app/static $HOME/app/test_pipeline_outputs

ENV TMPDIR=/tmp/uploads

# ============================================================================
# 7. Tesseract 설치 확인
# ============================================================================
RUN tesseract --version && tesseract --list-langs

# ============================================================================
# 8. 포트 설정 (HF Spaces 필수 포트)
# ============================================================================
EXPOSE 7860

# ============================================================================
# 9. 서버 실행
# ============================================================================
# Hugging Face Spaces는 7860 포트 필수
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7860", "--timeout-keep-alive", "300"]
