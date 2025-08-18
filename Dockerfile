FROM openjdk:17-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# 시스템 패키지 업데이트 및 필수 도구 설치
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    libopencv-dev \
    && rm -rf /var/lib/apt/lists/*

# Python 의존성 설치
RUN pip3 install --no-cache-dir \
    torch \
    ultralytics \
    opencv-python \
    numpy \
    pillow

# Gradle 캐시를 위한 디렉토리 생성
VOLUME ["/root/.gradle"]

# 애플리케이션 파일 복사
COPY . .

# Gradle 실행 권한 부여
RUN chmod +x ./gradlew

# 애플리케이션 빌드
RUN ./gradlew bootJar --no-daemon

# 애플리케이션 실행을 위한 디렉토리
RUN mkdir -p /app/temp /app/logs /app/models

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/build/libs/smarteye-spring-backend-0.1.0.jar"]
