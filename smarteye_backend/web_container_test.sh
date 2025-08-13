#!/bin/bash

# 기존 web 컨테이너 내부에서 파이프라인 테스트 실행

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

echo "=== Web 컨테이너 내부 파이프라인 테스트 ==="

# Docker를 제거한 간소화된 테스트 스크립트 생성
log_info "Docker 의존성 없는 테스트 스크립트 생성..."

cat > container_test.sh << 'EOF'
#!/bin/bash

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

echo "=== SmartEye Backend 컨테이너 내부 테스트 ==="

# 컨테이너 내부에서는 localhost로 접근
API_BASE_URL="http://localhost:8000"

# 1. API 헬스체크
log_info "API 헬스체크..."
if command -v curl >/dev/null 2>&1; then
    health_response=$(curl -s "$API_BASE_URL/api/v1/health/" || echo "FAILED")
    if echo "$health_response" | grep -q "healthy"; then
        log_success "API 헬스체크 통과"
        echo "응답: $health_response"
    else
        log_error "API 헬스체크 실패: $health_response"
        exit 1
    fi
else
    log_info "curl이 설치되어 있지 않아 Python으로 테스트..."
    python -c "
import urllib.request
import json
try:
    with urllib.request.urlopen('$API_BASE_URL/api/v1/health/') as response:
        data = json.loads(response.read().decode())
        if data.get('status') == 'healthy':
            print('✅ API 헬스체크 통과')
            print(f'응답: {data}')
        else:
            print('❌ API 헬스체크 실패')
            exit(1)
except Exception as e:
    print(f'❌ API 헬스체크 오류: {e}')
    exit(1)
"
fi

# 2. Django 관리 명령어 테스트
log_info "Django 시스템 체크..."
python manage.py check --database default
log_success "Django 시스템 체크 통과"

# 3. 데이터베이스 마이그레이션 상태 확인
log_info "데이터베이스 마이그레이션 상태 확인..."
python manage.py showmigrations
log_success "마이그레이션 상태 확인 완료"

# 4. LAM 서비스 로드 테스트
log_info "LAM 서비스 로드 테스트..."
python -c "
try:
    from core.services.lam_service import LAMService
    lam = LAMService()
    print('✅ LAM 서비스 로드 성공')
except Exception as e:
    print(f'❌ LAM 서비스 로드 실패: {e}')
"

# 5. Redis 연결 테스트
log_info "Redis 연결 테스트..."
python -c "
try:
    import redis
    from django.conf import settings
    r = redis.Redis(host='redis', port=6379, db=0)
    r.ping()
    print('✅ Redis 연결 성공')
except Exception as e:
    print(f'❌ Redis 연결 실패: {e}')
"

log_success "모든 컨테이너 내부 테스트 완료!"
EOF

chmod +x container_test.sh

# web 컨테이너 내부에서 실행
log_info "web 컨테이너 내부에서 테스트 실행..."
docker compose -f docker-compose.dev.yml exec web bash -c "
    cd /app && 
    $(cat container_test.sh)
"

# 임시 파일 정리
rm container_test.sh
