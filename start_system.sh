#!/bin/bash

# SmartEye v0.4 전체 시스템 시작 스크립트
# Frontend + Backend 통합 실행

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

echo "🚀 SmartEye v0.4 전체 시스템 시작 중..."
echo "📅 $(date)"

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Backend 디렉토리 존재 확인
if [[ ! -d "Backend" ]]; then
    log_error "Backend 디렉토리를 찾을 수 없습니다."
    exit 1
fi

# Frontend 디렉토리 확인 (선택적)
if [[ -d "Frontend" ]]; then
    log_info "Frontend 디렉토리 감지됨"
    # 향후 Frontend 시작 로직 추가
fi

# Backend 서비스 시작
log_info "Backend 서비스 시작 중..."
cd Backend

# Backend의 start_services_enhanced.sh 실행
if [[ -f "start_services_enhanced.sh" ]]; then
    chmod +x start_services_enhanced.sh
    ./start_services_enhanced.sh
else
    log_error "start_services_enhanced.sh를 찾을 수 없습니다."
    exit 1
fi

cd ..

log_success "SmartEye v0.4 전체 시스템이 시작되었습니다!"
echo ""
echo "📍 접속 정보:"
echo "  - Backend API: http://localhost:8080"
echo "  - LAM Service: http://localhost:8001"
echo "  - System Health: http://localhost:80/health"
echo ""
echo "📚 API 문서:"
echo "  - Backend Swagger: http://localhost:8080/swagger-ui/index.html"
echo "  - LAM Service Docs: http://localhost:8001/docs"
