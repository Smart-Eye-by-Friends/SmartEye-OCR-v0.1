#!/bin/bash

# 간단한 SmartEye 파이프라인 테스트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

API_BASE_URL="http://localhost:8000"

echo "=== SmartEye Backend 간단 테스트 ==="

# 1. API 헬스체크
log_info "API 헬스체크 테스트..."
health_response=$(curl -s "$API_BASE_URL/api/v1/health/")
if echo "$health_response" | grep -q "healthy"; then
    log_success "API 헬스체크 통과"
    echo "응답: $health_response"
else
    log_error "API 헬스체크 실패"
    echo "응답: $health_response"
    exit 1
fi

# 2. Docker 서비스 상태 확인
log_info "Docker 서비스 상태 확인..."
docker compose -f docker-compose.dev.yml ps

# 3. 데이터베이스 연결 테스트 (컨테이너 내부에서)
log_info "데이터베이스 연결 테스트..."
docker compose -f docker-compose.dev.yml exec -T web python manage.py check --database default
if [ $? -eq 0 ]; then
    log_success "데이터베이스 연결 성공"
else
    log_error "데이터베이스 연결 실패"
    exit 1
fi

# 4. Redis 연결 테스트
log_info "Redis 연결 테스트..."
docker compose -f docker-compose.dev.yml exec -T redis redis-cli ping
if [ $? -eq 0 ]; then
    log_success "Redis 연결 성공"
else
    log_error "Redis 연결 실패"
    exit 1
fi

log_success "모든 기본 테스트 통과!"
echo ""
echo "서비스 URL:"
echo "  • API 서버: $API_BASE_URL"
echo "  • API 문서: $API_BASE_URL/api/docs/"
echo "  • 관리자 페이지: $API_BASE_URL/admin/"
echo "  • Flower 모니터링: http://localhost:5555"
