FROM python:3.9-slim

WORKDIR /app

# 시스템 의존성 설치 (OpenCV용)
RUN apt-get update && apt-get install -y \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    wget \
    curl \
    git \
    gcc \
    g++ \
    && apt-get install -y libgl1 || true \
    && rm -rf /var/lib/apt/lists/*

# non-root 사용자 생성 (LAM 서비스 전용)
RUN groupadd -r lamuser && useradd -r -g lamuser lamuser

# pip 업그레이드
RUN pip install --no-cache-dir --upgrade pip

# requirements.txt 복사 및 설치
COPY requirements.txt .

# 기본 라이브러리 설치
RUN pip install --no-cache-dir -r requirements.txt

# DocLayout-YOLO 설치 (최신 버전)
RUN pip install --no-cache-dir doclayout-yolo || \
    pip install --no-cache-dir git+https://github.com/opendatalab/DocLayout-YOLO.git || \
    echo "DocLayout-YOLO 설치 실패, Ultralytics만 사용"

# 소스 코드 복사
COPY . .

# 모델 캐시 디렉토리 생성 및 권한 설정
RUN mkdir -p ./models ./logs && \
    chown -R lamuser:lamuser /app

# 포트 노출
EXPOSE 8001

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8001/health || exit 1

# non-root 사용자로 전환
USER lamuser

# 서비스 시작
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001", "--reload"]
