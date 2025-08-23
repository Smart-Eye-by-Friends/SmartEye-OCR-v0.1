# SmartEye LAM 마이크로서비스 - 기본 Dockerfile
FROM python:3.10-slim

# 시스템 패키지 설치
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    git \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# Python 패키지 설치
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# 애플리케이션 코드 복사
COPY app/ ./app/

# 환경 변수 설정
ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1

# 모델 및 임시 디렉토리 생성
RUN mkdir -p /app/models /app/cache /tmp/smarteye-lam

# 비root 사용자 생성 및 권한 설정
RUN groupadd -r smarteye && useradd -r -g smarteye smarteye
RUN chown -R smarteye:smarteye /app
USER smarteye

# 포트 노출
EXPOSE 8081

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8081/health || exit 1

# 애플리케이션 실행
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]
