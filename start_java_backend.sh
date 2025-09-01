#!/bin/bash

# Java 백엔드 빌드 및 실행 스크립트

echo "🚀 SmartEye Java 백엔드 시작..."

# 현재 디렉토리를 java-backend로 변경
cd java-backend

# Gradle 빌드
echo "📦 Gradle 빌드 시작..."
./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo "✅ 빌드 성공!"
    
    # 필요한 디렉토리 생성
    mkdir -p uploads static temp uploads/images uploads/pdfs
    
    echo "🏃‍♂️ 애플리케이션 시작..."
    # Spring Boot 애플리케이션 실행
    ./gradlew bootRun
else
    echo "❌ 빌드 실패!"
    exit 1
fi
