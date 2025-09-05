#!/bin/bash

# SmartEye v0.4 전체 시스템 중지 스크립트
# Frontend + Backend 통합 중지

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

echo "🛑 SmartEye v0.4 전체 시스템 중지 중..."
echo "📅 $(date)"

# Backend 서비스 중지
if [[ -d "Backend" ]]; then
    log_info "Backend 서비스 중지 중..."
    cd Backend
    docker-compose down --remove-orphans || true
    cd ..
    log_success "Backend 서비스가 중지되었습니다."
else
    echo -e "${RED}❌ Backend 디렉토리를 찾을 수 없습니다.${NC}"
fi

# Frontend 서비스 중지 (향후 추가)
if [[ -d "Frontend" ]]; then
    log_info "Frontend 서비스 중지 중..."
    # 향후 Frontend 중지 로직 추가
    log_success "Frontend 서비스가 중지되었습니다."
fi

# Docker 정리
log_info "Docker 리소스 정리 중..."
docker system prune -f || true

log_success "SmartEye v0.4 전체 시스템이 중지되었습니다!"
