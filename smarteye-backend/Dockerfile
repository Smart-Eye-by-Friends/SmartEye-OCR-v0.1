# SmartEye Java Spring Boot Backend Dockerfile
FROM openjdk:21-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# 시스템 패키지 업데이트 및 필요한 도구 설치
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    libtesseract-dev \
    && rm -rf /var/lib/apt/lists/*

# Gradle wrapper 및 프로젝트 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 코드 복사
COPY src src

# 실행 권한 부여
RUN chmod +x gradlew

# 애플리케이션 빌드
RUN ./gradlew build -x test --no-daemon

# 필요한 디렉토리 생성
RUN mkdir -p /app/uploads /app/static /app/logs /app/tessdata

# Tesseract 데이터 경로 설정
ENV TESSERACT_DATAPATH=/usr/share/tesseract-ocr/4.00/tessdata

# 포트 노출
EXPOSE 8080

# JVM 옵션 설정
ENV JAVA_OPTS="-Xms1024m -Xmx2048m -XX:+UseG1GC -XX:G1HeapRegionSize=16m"

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# 애플리케이션 실행
CMD ["sh", "-c", "java $JAVA_OPTS -jar build/libs/*.jar"]