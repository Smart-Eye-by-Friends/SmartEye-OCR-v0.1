#!/bin/bash

# SmartEye v0.4 전체 서비스 시작 스크립트
# Docker Compose 기반 마이크로서비스 시작

set -e

echo "🚀 SmartEye v0.4 서비스 시작 중..."
echo "📅 $(date)"

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Docker Compose 파일 존재 확인
if [[ ! -f "docker-compose.yml" ]]; then
    echo "❌ docker-compose.yml 파일을 찾을 수 없습니다."
    exit 1
fi

# 기존 컨테이너 정리
echo "🧹 기존 컨테이너 정리 중..."
docker-compose down --remove-orphans || true

# 이미지 빌드
echo "🔨 이미지 빌드 중..."
docker-compose build --no-cache

# 서비스 시작
echo "🎯 서비스 시작 중..."
docker-compose up -d

# 서비스 상태 확인
echo "⏳ 서비스 시작 대기 중..."
sleep 10

echo "📊 서비스 상태 확인 중..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 헬스체크
echo ""
echo "🏥 헬스체크 수행 중..."

# Backend 헬스체크
echo -n "Backend (8080): "
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 정상"
else
    echo "❌ 실패"
fi

# LAM Service 헬스체크  
echo -n "LAM Service (8001): "
if curl -s http://localhost:8001/health > /dev/null 2>&1; then
    echo "✅ 정상"
else
    echo "❌ 실패"
fi

# Database 헬스체크
echo -n "PostgreSQL (5433): "
if docker exec smarteye-postgres pg_isready -U smarteye > /dev/null 2>&1; then
    echo "✅ 정상"
else
    echo "❌ 실패"
fi

echo ""
echo "🎉 SmartEye v0.4 서비스 시작 완료!"
echo "📍 Backend API: http://localhost:8080"
echo "📍 LAM Service: http://localhost:8001"
echo "📍 API 테스트: curl -X POST -F \"image=@test_homework_image.jpg\" -F \"modelChoice=SmartEyeSsen\" http://localhost:8080/api/document/analyze"
echo ""
echo "📝 로그 확인: docker-compose logs -f [service-name]"
echo "🛑 서비스 중지: docker-compose down"

# 서비스 상태 확인
echo "📊 서비스 상태 확인 중..."
sleep 10

# PostgreSQL 연결 대기
echo "⏳ PostgreSQL 연결 대기 중..."
timeout=60
counter=0
until docker-compose exec -T postgres pg_isready -U smarteye -d smarteye_db > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        echo "❌ PostgreSQL 연결 타임아웃"
        exit 1
    fi
    echo "  PostgreSQL 대기 중... ($counter/$timeout)"
    sleep 2
    ((counter+=2))
done
echo "✅ PostgreSQL 연결 성공"

# LAM 서비스 헬스체크
echo "🔍 LAM 서비스 헬스체크 중..."
timeout=120
counter=0
until curl -f http://localhost:8001/health > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        echo "❌ LAM 서비스 헬스체크 타임아웃"
        exit 1
    fi
    echo "  LAM 서비스 대기 중... ($counter/$timeout)"
    sleep 5
    ((counter+=5))
done
echo "✅ LAM 서비스 준비 완료"

# Java 백엔드 헬스체크
echo "☕ Java 백엔드 헬스체크 중..."
timeout=90
counter=0
until curl -f http://localhost:8080/api/health > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        echo "❌ Java 백엔드 헬스체크 타임아웃"
        exit 1
    fi
    echo "  Java 백엔드 대기 중... ($counter/$timeout)"
    sleep 3
    ((counter+=3))
done
echo "✅ Java 백엔드 준비 완료"

echo ""
echo "🎉 SmartEye 서비스가 성공적으로 시작되었습니다!"
echo ""
echo "📍 서비스 접속 정보:"
echo "  - Java Backend API: http://localhost:8080"
echo "  - LAM Service API: http://localhost:8001"
echo "  - PostgreSQL: localhost:5432"
echo ""
echo "📚 API 문서:"
echo "  - Java Backend: http://localhost:8080/swagger-ui/index.html"
echo "  - LAM Service: http://localhost:8001/docs"
echo ""
echo "🔍 서비스 상태 확인:"
docker-compose ps
echo ""
echo "📋 로그 확인 방법:"
echo "  docker-compose logs -f [서비스명]"
echo "  예: docker-compose logs -f smarteye-backend"