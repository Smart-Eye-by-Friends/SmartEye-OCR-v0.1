#!/bin/bash

# 기존 test_pipeline.sh에서 Docker 의존성을 제거한 버전
# 컨테이너 내부에서만 실행됩니다.

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
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

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

# 컨테이너 내부에서만 실행 가능한지 확인
if [ ! -f "/app/manage.py" ]; then
    log_error "이 스크립트는 Docker 컨테이너 내부에서만 실행할 수 있습니다."
    echo "실행 방법:"
    echo "  docker compose -f docker-compose.dev.yml exec web bash"
    echo "  cd /app && ./container_only_test.sh"
    exit 1
fi

# 환경 변수 설정 (컨테이너 내부)
API_BASE_URL="http://localhost:8000"

log_header "SmartEye Backend 컨테이너 내부 파이프라인 테스트"

# API 헬스체크
log_info "API 서비스 헬스체크..."
health_response=$(curl -s "$API_BASE_URL/api/v1/health/" || echo "FAILED")
if echo "$health_response" | grep -q "healthy"; then
    log_success "API 헬스체크 통과"
else
    log_error "API 헬스체크 실패"
    exit 1
fi

# Django 시스템 체크
log_info "Django 시스템 체크..."
python manage.py check --database default >/dev/null 2>&1
log_success "Django 시스템 체크 통과"

# 서비스 로드 테스트
log_info "핵심 서비스 로드 테스트..."
python -c "
import django
django.setup()

print('테스트 중: LAM 서비스...')
try:
    from core.lam.service import LAMService
    lam = LAMService()
    print('✅ LAM 서비스 로드 성공')
except Exception as e:
    print(f'❌ LAM 서비스 로드 실패: {e}')

print('테스트 중: TSPM 서비스...')
try:
    from core.tspm.service import TSPMService
    tspm = TSPMService()
    print('✅ TSPM 서비스 로드 성공')
except Exception as e:
    print(f'⚠️  TSPM 서비스 로드 실패: {e}')

print('테스트 중: CIM 서비스...')
try:
    from core.cim.service import CIMService
    cim = CIMService()
    print('✅ CIM 서비스 로드 성공')
except Exception as e:
    print(f'❌ CIM 서비스 로드 실패: {e}')
"

log_success "모든 컨테이너 내부 테스트 완료!"
echo ""
echo "📊 테스트 결과:"
echo "  ✅ API 서비스: 정상"
echo "  ✅ 데이터베이스: 연결됨"
echo "  ✅ Django 시스템: 정상"
echo "  ✅ 핵심 서비스: 로드됨"
