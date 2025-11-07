# ============================================================================
# SmartEyeSsen Backend - Production Dockerfile
# Multi-stage build for optimized image size
# Python 3.9 기반 (Project/requirements.txt 호환)
# ============================================================================

# Stage 1: Builder - Install dependencies
FROM python:3.9-slim as builder

# Set working directory
WORKDIR /app

# Install system dependencies for Tesseract OCR and OpenCV
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    libgl1 \
    libglib2.0-0 \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Verify Tesseract installation
RUN tesseract --version && tesseract --list-langs

# Copy Backend requirements (pymysql, gunicorn 포함)
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir doclayout-yolo

# ============================================================================
# Stage 2: Runtime - Minimal production image
FROM python:3.9-slim

# Set working directory
WORKDIR /app

# Install runtime dependencies only
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    libgl1 \
    libglib2.0-0 \
    locales \
    && rm -rf /var/lib/apt/lists/*

# Set UTF-8 locale (한글 인코딩 문제 해결)
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    sed -i '/ko_KR.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG=ko_KR.UTF-8 \
    LANGUAGE=ko_KR:en \
    LC_ALL=ko_KR.UTF-8

# Copy Python packages from builder
COPY --from=builder /usr/local/lib/python3.9/site-packages /usr/local/lib/python3.9/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin

# Copy application code
COPY . .

# Create necessary directories
RUN mkdir -p /app/uploads /app/static /app/test_pipeline_outputs && \
    chmod -R 755 /app/uploads /app/static /app/test_pipeline_outputs

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/usr/local/bin:$PATH"

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8000/health', timeout=5)"

# Run with gunicorn for production
# Workers: 1 (메모리 최적화 - 전시회 환경, 1.7GB 사용)
# Timeout: 300s (AI 모델 추론 시간 고려)
CMD ["gunicorn", "app.main:app", \
     "--workers", "1", \
     "--worker-class", "uvicorn.workers.UvicornWorker", \
     "--bind", "0.0.0.0:8000", \
     "--timeout", "300", \
     "--access-logfile", "-", \
     "--error-logfile", "-"]
