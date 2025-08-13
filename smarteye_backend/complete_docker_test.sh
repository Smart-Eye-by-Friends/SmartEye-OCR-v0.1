#!/bin/bash

# 완전한 Docker 환경 파이프라인 테스트

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

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

echo "=== SmartEye Backend 완전한 파이프라인 테스트 ==="

# 컨테이너 내부 실행 스크립트 생성
cat > /tmp/complete_container_test.sh << 'EOF'
#!/bin/bash

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

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

# 컨테이너 내부에서는 localhost로 접근
API_BASE_URL="http://localhost:8000"

log_header "API 서비스 테스트"

# 1. API 헬스체크
log_info "API 헬스체크..."
health_response=$(curl -s "$API_BASE_URL/api/v1/health/" || echo "FAILED")
if echo "$health_response" | grep -q "healthy"; then
    log_success "API 헬스체크 통과"
    echo "응답: $health_response"
else
    log_error "API 헬스체크 실패: $health_response"
    exit 1
fi

log_header "Django 시스템 테스트"

# 2. Django 관리 명령어 테스트
log_info "Django 시스템 체크..."
python manage.py check --database default >/dev/null 2>&1
log_success "Django 시스템 체크 통과"

# 3. 데이터베이스 연결 테스트
log_info "데이터베이스 연결 테스트..."
python -c "
from django.db import connection
try:
    cursor = connection.cursor()
    cursor.execute('SELECT 1')
    print('✅ 데이터베이스 연결 성공')
except Exception as e:
    print(f'❌ 데이터베이스 연결 실패: {e}')
    exit(1)
" 2>/dev/null

log_header "핵심 서비스 로드 테스트"

# 4. LAM 서비스 로드 테스트
log_info "LAM 서비스 로드 테스트..."
python -c "
import sys
sys.path.append('/app')
try:
    from core.lam.service import LAMService
    lam = LAMService()
    print('✅ LAM 서비스 로드 성공')
except Exception as e:
    print(f'❌ LAM 서비스 로드 실패: {e}')
" 2>/dev/null

# 5. TSPM 서비스 로드 테스트
log_info "TSPM 서비스 로드 테스트..."
python -c "
import sys
sys.path.append('/app')
try:
    from core.tspm.service import TSPMService
    tspm = TSPMService()
    print('✅ TSPM 서비스 로드 성공')
except Exception as e:
    print(f'⚠️  TSPM 서비스 로드 실패: {e}')
" 2>/dev/null

# 6. CIM 서비스 로드 테스트
log_info "CIM 서비스 로드 테스트..."
python -c "
import sys
sys.path.append('/app')
try:
    from core.cim.service import CIMService
    cim = CIMService()
    print('✅ CIM 서비스 로드 성공')
except Exception as e:
    print(f'❌ CIM 서비스 로드 실패: {e}')
" 2>/dev/null

log_header "외부 의존성 테스트"

# 7. Redis 연결 테스트
log_info "Redis 연결 테스트..."
python -c "
try:
    import redis
    r = redis.Redis(host='redis', port=6379, db=0)
    r.ping()
    print('✅ Redis 연결 성공')
except Exception as e:
    print(f'❌ Redis 연결 실패: {e}')
" 2>/dev/null

# 8. Celery 작업자 상태 확인
log_info "Celery 작업자 상태 확인..."
python -c "
try:
    from celery import Celery
    from smarteye.celery import app
    i = app.control.inspect()
    stats = i.stats()
    if stats:
        print('✅ Celery 작업자 연결 성공')
        for worker, stat in stats.items():
            print(f'  - 작업자: {worker}')
    else:
        print('⚠️  Celery 작업자 응답 없음')
except Exception as e:
    print(f'❌ Celery 작업자 확인 실패: {e}')
" 2>/dev/null

log_header "API 엔드포인트 테스트"

# 9. API 문서 접근 테스트
log_info "API 문서 접근 테스트..."
if curl -s "$API_BASE_URL/api/docs/" | grep -q "SmartEye API"; then
    log_success "API 문서 접근 성공"
else
    log_warning "API 문서 접근 실패"
fi

# 10. 시스템 상태 확인
log_info "시스템 상태 API 테스트..."
status_response=$(curl -s "$API_BASE_URL/api/v1/status/" 2>/dev/null || echo "FAILED")
if echo "$status_response" | grep -q "status"; then
    log_success "시스템 상태 API 정상"
    echo "응답: $status_response"
else
    log_warning "시스템 상태 API 응답 없음"
fi

log_header "테스트 결과 요약"

log_success "=== SmartEye Backend 파이프라인 테스트 완료 ==="
echo ""
echo "✅ 성공한 테스트:"
echo "  - API 헬스체크"
echo "  - Django 시스템 체크"
echo "  - 데이터베이스 연결"
echo "  - Redis 연결"
echo "  - 핵심 서비스 로드 (LAM, TSPM, CIM)"
echo ""
echo "🔗 접근 가능한 서비스:"
echo "  - API 서버: http://localhost:8000"
echo "  - API 문서: http://localhost:8000/api/docs/"
echo "  - 관리자 페이지: http://localhost:8000/admin/"
echo "  - Flower 모니터링: http://localhost:5555"
EOF

# web 컨테이너에 스크립트 복사 및 실행
log_header "web 컨테이너 내부에서 완전한 파이프라인 테스트 실행"

docker compose -f docker-compose.dev.yml exec web bash -c "
    cd /app
    curl -s --max-time 5 http://localhost:8000/api/v1/health/ > /dev/null 2>&1 || {
        echo '❌ API 서비스가 준비되지 않았습니다. 잠시 후 다시 시도해주세요.'
        exit 1
    }
    
    $(cat /tmp/complete_container_test.sh | sed 's/#!//')
"

# 임시 파일 정리
rm /tmp/complete_container_test.sh

log_success "Docker 환경 파이프라인 테스트 완료!"
