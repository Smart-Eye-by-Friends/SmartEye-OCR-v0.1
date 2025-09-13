# SmartEye Java Spring Boot Backend Dockerfile
# Multi-stage build for efficiency and smaller image size

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Alpine에서 필요한 패키지 설치 (빌드용)
RUN apk add --no-cache curl wget

# Gradle wrapper 및 의존성 파일들 먼저 복사 (캐시 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여
RUN chmod +x gradlew

# 의존성 다운로드 (레이어 캐싱으로 재빌드 시 속도 향상)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드
RUN ./gradlew build -x test --no-daemon

# Runtime stage - 더 가벼운 JRE 이미지 사용
FROM eclipse-temurin:21-jre-alpine

# 런타임에 필요한 패키지만 설치
RUN apk add --no-cache \
    curl \
    tesseract-ocr \
    tesseract-ocr-data-kor \
    tesseract-ocr-data-eng

# 작업 디렉토리 설정
WORKDIR /app

# 빌드 스테이지에서 JAR 파일만 복사
COPY --from=builder /app/build/libs/smarteye-backend-0.0.1-SNAPSHOT.jar app.jar

# 필요한 디렉토리 생성
RUN mkdir -p /app/uploads /app/static /app/logs

# Tesseract 데이터 경로 설정 (Alpine 경로)
ENV TESSERACT_DATAPATH=/usr/share/tessdata
ENV TESSDATA_PREFIX=/usr/share/tessdata

# 포트 노출
EXPOSE 8080

# JVM 옵션 설정 (컨테이너 환경에 최적화)
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 헬스체크 (사용자 정의 헬스체크 엔드포인트 사용)
HEALTHCHECK --interval=30s --timeout=15s --start-period=120s --retries=5 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# 애플리케이션 실행
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]