#!/bin/bash

# SmartEye v0.4 개발 환경 중지 스크립트
# Docker 컨테이너 정리

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

echo -e "${BLUE}🛑 SmartEye v0.4 개발 환경 중지${NC}"
echo "====================================="
echo

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Docker 서비스 중지
log_info "개발 환경 Docker 서비스를 중지합니다..."
cd Backend

if docker-compose -f docker-compose-dev.yml down --remove-orphans; then
    log_success "Docker 서비스 중지 완료"
else
    log_warning "일부 서비스 중지에 실패했지만 계속합니다"
fi

echo

# 정리 완료 메시지
log_success "개발 환경 정리 완료!"
echo
echo -e "${YELLOW}💡 참고사항:${NC}"
echo "  • Frontend/Backend 개발 서버는 각 터미널에서 Ctrl+C로 종료하세요"
echo "  • 다시 시작하려면: ./start_dev.sh"
echo "  • 전체 시스템 시작하려면: ./start_system.sh"
echo

echo -e "${GREEN}🎯 정리 완료! 수고하셨습니다.${NC}"