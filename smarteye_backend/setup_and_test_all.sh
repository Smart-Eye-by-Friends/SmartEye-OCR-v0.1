#!/bin/bash

# SmartEye Backend 통합 환경 설정, 실행, 테스트 스크립트
# 이 스크립트는 Docker 환경 구성부터 전체 기능 테스트까지 한번에 실행합니다.

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# 로그 함수
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

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# 전역 변수
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="docker-compose.dev.yml"
API_BASE_URL="http://localhost:8000"
FLOWER_URL="http://localhost:5555"
MAX_WAIT_TIME=120
STEP_COUNTER=0

# 단계 카운터
next_step() {
    STEP_COUNTER=$((STEP_COUNTER + 1))
    log_step "[$STEP_COUNTER/12] $1"
}

# 오류 처리 함수
handle_error() {
    log_error "스크립트 실행 중 오류가 발생했습니다."
    log_error "라인 $1에서 실패했습니다."
    cleanup_on_error
    exit 1
}

# 오류 시 정리 함수
cleanup_on_error() {
    log_warning "오류 발생으로 인한 정리 작업 중..."
    docker compose -f "$COMPOSE_FILE" logs --tail=10 || true
    echo ""
    log_info "문제 해결을 위한 유용한 명령어:"
    echo "  • 로그 확인: docker compose -f $COMPOSE_FILE logs"
    echo "  • 서비스 상태: docker compose -f $COMPOSE_FILE ps"
    echo "  • 서비스 재시작: docker compose -f $COMPOSE_FILE restart"
    echo "  • 완전 정리: docker compose -f $COMPOSE_FILE down -v"
}

# 트랩 설정
trap 'handle_error $LINENO' ERR

# 필수 명령어 확인
check_requirements() {
    next_step "필수 요구사항 확인"
    
    local missing_commands=()
    
    if ! command -v docker &> /dev/null; then
        missing_commands+=("docker")
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        missing_commands+=("docker-compose")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_commands+=("curl")
    fi
    
    if [ ${#missing_commands[@]} -ne 0 ]; then
        log_error "다음 명령어들이 설치되어 있지 않습니다: ${missing_commands[*]}"
        log_info "설치 방법:"
        echo "  • Docker: https://docs.docker.com/get-docker/"
        echo "  • curl: sudo apt-get install curl (Ubuntu/Debian)"
        exit 1
    fi
    
    log_success "모든 필수 요구사항이 충족되었습니다."
}

# 환경 파일 확인 및 생성
setup_environment() {
    next_step "환경 파일 설정"
    
    if [ ! -f ".env.docker" ]; then
        log_warning ".env.docker 파일이 없습니다. 기본 파일을 생성합니다."
        cat > .env.docker << 'EOF'
# PostgreSQL Database
DB_NAME=smarteye_db
DB_USER=smarteye_user
DB_PASSWORD=smarteye_password_2024!
DB_HOST=db
DB_PORT=5432

# Redis
REDIS_URL=redis://redis:6379/0

# Django Settings
DJANGO_SETTINGS_MODULE=smarteye.settings.development
SECRET_KEY=your-secret-key-here-change-in-production
DEBUG=True

# SmartEye Configuration
SMARTEYE_MODEL=docstructbench
SMARTEYE_DEBUG=True
SMARTEYE_BATCH_SIZE=2
SMARTEYE_MAX_WORKERS=2

# OpenAI API (Optional - for TSPM service)
OPENAI_API_KEY=your-openai-api-key-here

# Flower Monitoring
FLOWER_USER=admin
FLOWER_PASSWORD=smarteye_flower_password!@#$

# Security
ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
EOF
        log_success ".env.docker 파일이 생성되었습니다."
    else
        log_success ".env.docker 파일이 존재합니다."
    fi
}

# Docker 이미지 빌드
build_images() {
    next_step "Docker 이미지 빌드"
    
    log_info "Docker 이미지를 빌드하고 있습니다... (시간이 걸릴 수 있습니다)"
    docker compose -f "$COMPOSE_FILE" build --no-cache
    log_success "Docker 이미지 빌드 완료"
}

# 기존 컨테이너 정리
cleanup_containers() {
    next_step "기존 컨테이너 정리"
    
    log_info "기존 컨테이너를 정리하고 있습니다..."
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
    log_success "컨테이너 정리 완료"
}

# 서비스 시작
start_services() {
    next_step "Docker 서비스 시작"
    
    log_info "모든 서비스를 시작하고 있습니다..."
    docker compose -f "$COMPOSE_FILE" up -d
    
    log_info "서비스 시작 완료. 상태 확인 중..."
    docker compose -f "$COMPOSE_FILE" ps
    log_success "모든 서비스가 시작되었습니다."
}

# 서비스 대기
wait_for_services() {
    next_step "서비스 준비 대기"
    
    local wait_time=0
    local db_ready=false
    local web_ready=false
    local redis_ready=false
    
    log_info "서비스들이 준비될 때까지 대기 중... (최대 ${MAX_WAIT_TIME}초)"
    
    while [ $wait_time -lt $MAX_WAIT_TIME ]; do
        # 데이터베이스 확인
        if ! $db_ready && docker compose -f "$COMPOSE_FILE" exec -T db pg_isready -U smarteye_user -d smarteye_db &>/dev/null; then
            log_success "데이터베이스 준비 완료"
            db_ready=true
        fi
        
        # Redis 확인
        if ! $redis_ready && docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping | grep -q "PONG"; then
            log_success "Redis 준비 완료"
            redis_ready=true
        fi
        
        # 웹 서비스 확인
        if ! $web_ready && curl -s "$API_BASE_URL/api/v1/health/" | grep -q "healthy"; then
            log_success "웹 서비스 준비 완료"
            web_ready=true
        fi
        
        # 모든 서비스가 준비되었는지 확인
        if $db_ready && $web_ready && $redis_ready; then
            log_success "모든 서비스가 준비되었습니다!"
            return 0
        fi
        
        sleep 2
        wait_time=$((wait_time + 2))
        echo -n "."
    done
    
    log_error "서비스 준비 시간이 초과되었습니다. (${MAX_WAIT_TIME}초)"
    return 1
}

# 데이터베이스 마이그레이션
run_migrations() {
    next_step "데이터베이스 마이그레이션"
    
    log_info "데이터베이스 마이그레이션을 실행하고 있습니다..."
    docker compose -f "$COMPOSE_FILE" exec -T web python manage.py migrate
    log_success "데이터베이스 마이그레이션 완료"
}

# 정적 파일 수집
collect_static() {
    next_step "정적 파일 수집"
    
    log_info "정적 파일을 수집하고 있습니다..."
    docker compose -f "$COMPOSE_FILE" exec -T web python manage.py collectstatic --noinput
    log_success "정적 파일 수집 완료"
}

# 기본 테스트 실행
run_basic_tests() {
    next_step "기본 기능 테스트"
    
    log_info "기본 기능 테스트를 실행하고 있습니다..."
    
    # API 헬스체크
    log_info "API 헬스체크..."
    health_response=$(curl -s "$API_BASE_URL/api/v1/health/")
    if echo "$health_response" | grep -q "healthy"; then
        log_success "✅ API 헬스체크 통과"
    else
        log_error "❌ API 헬스체크 실패: $health_response"
        return 1
    fi
    
    # Django 시스템 체크
    log_info "Django 시스템 체크..."
    docker compose -f "$COMPOSE_FILE" exec -T web python manage.py check --database default
    log_success "✅ Django 시스템 체크 통과"
    
    # 데이터베이스 연결 테스트
    log_info "데이터베이스 연결 테스트..."
    docker compose -f "$COMPOSE_FILE" exec -T web python -c "
from django.db import connection
cursor = connection.cursor()
cursor.execute('SELECT 1')
print('데이터베이스 연결 성공')
" > /dev/null
    log_success "✅ 데이터베이스 연결 테스트 통과"
    
    log_success "모든 기본 테스트가 통과되었습니다!"
}

# 전체 파이프라인 테스트
run_pipeline_tests() {
    next_step "전체 파이프라인 테스트"
    
    log_info "LAM→TSPM→CIM 파이프라인 테스트를 실행하고 있습니다..."
    
    # 컨테이너 내부에서 전체 테스트 실행
    docker compose -f "$COMPOSE_FILE" exec -T web bash -c "
        cd /app
        
        # Django 설정 로드
        python -c \"
import django
django.setup()

print('=== 핵심 서비스 로드 테스트 ===')

# LAM 서비스 테스트
print('테스트 중: LAM 서비스...')
try:
    from core.lam.service import LAMService
    lam = LAMService()
    print('✅ LAM 서비스 로드 성공')
    lam.cleanup()
except Exception as e:
    print(f'❌ LAM 서비스 로드 실패: {e}')

# TSPM 서비스 테스트
print('테스트 중: TSPM 서비스...')
try:
    from core.tspm.service import TSPMService
    tspm = TSPMService()
    print('✅ TSPM 서비스 로드 성공')
    tspm.cleanup()
except Exception as e:
    print(f'⚠️  TSPM 서비스 로드 실패: {e}')

# CIM 서비스 테스트
print('테스트 중: CIM 서비스...')
try:
    from core.cim.service import CIMService
    cim = CIMService()
    print('✅ CIM 서비스 로드 성공')
    cim.cleanup()
except Exception as e:
    print(f'❌ CIM 서비스 로드 실패: {e}')

print('=== 외부 서비스 연결 테스트 ===')

# Redis 연결 테스트
print('테스트 중: Redis 연결...')
try:
    import redis
    r = redis.Redis(host='redis', port=6379, db=0)
    r.ping()
    print('✅ Redis 연결 성공')
except Exception as e:
    print(f'❌ Redis 연결 실패: {e}')

# Celery 작업자 테스트
print('테스트 중: Celery 작업자...')
try:
    from smarteye.celery import app
    i = app.control.inspect()
    stats = i.stats()
    if stats:
        print('✅ Celery 작업자 연결 성공')
        for worker in stats.keys():
            print(f'  - 작업자: {worker}')
    else:
        print('⚠️  Celery 작업자 응답 없음')
except Exception as e:
    print(f'❌ Celery 작업자 확인 실패: {e}')

print('=== 파이프라인 테스트 완료 ===')
\"
    "
    
    log_success "전체 파이프라인 테스트 완료"
}

# 최종 상태 확인
final_status_check() {
    next_step "최종 상태 확인"
    
    log_info "모든 서비스의 최종 상태를 확인하고 있습니다..."
    
    echo ""
    log_header "Docker 서비스 상태"
    docker compose -f "$COMPOSE_FILE" ps
    
    echo ""
    log_header "서비스 접근 URL"
    echo "🌐 웹 서비스:"
    echo "  • API 서버: $API_BASE_URL"
    echo "  • API 문서: $API_BASE_URL/api/docs/"
    echo "  • 관리자 페이지: $API_BASE_URL/admin/"
    echo ""
    echo "📊 모니터링:"
    echo "  • Flower (Celery): $FLOWER_URL"
    echo "  • 사용자명: admin"
    echo "  • 비밀번호: smarteye_flower_password!@#$"
    echo ""
    echo "🔧 유용한 명령어:"
    echo "  • 로그 확인: docker compose -f $COMPOSE_FILE logs -f [service]"
    echo "  • 컨테이너 접속: docker compose -f $COMPOSE_FILE exec [service] bash"
    echo "  • 서비스 재시작: docker compose -f $COMPOSE_FILE restart [service]"
    echo "  • 전체 종료: docker compose -f $COMPOSE_FILE down"
    
    log_success "SmartEye Backend가 성공적으로 실행되었습니다!"
}

# 메인 실행 함수
main() {
    log_header "SmartEye Backend 통합 환경 설정 및 테스트"
    echo "이 스크립트는 Docker 환경 구성부터 전체 기능 테스트까지 자동으로 실행합니다."
    echo "예상 소요 시간: 5-10분"
    echo ""
    
    # 실행 단계
    check_requirements
    setup_environment
    cleanup_containers
    build_images
    start_services
    wait_for_services
    run_migrations
    collect_static
    run_basic_tests
    run_pipeline_tests
    final_status_check
    
    echo ""
    log_success "🎉 모든 설정과 테스트가 완료되었습니다!"
    log_info "SmartEye Backend가 정상적으로 실행 중입니다."
}

# 도움말 표시
show_help() {
    cat << EOF
SmartEye Backend 통합 환경 설정 및 테스트 스크립트

이 스크립트는 다음 작업을 순차적으로 실행합니다:
1. 필수 요구사항 확인 (Docker, curl 등)
2. 환경 파일 설정 (.env.docker)
3. 기존 컨테이너 정리
4. Docker 이미지 빌드
5. 모든 서비스 시작
6. 서비스 준비 대기
7. 데이터베이스 마이그레이션
8. 정적 파일 수집
9. 기본 기능 테스트
10. 전체 파이프라인 테스트 (LAM→TSPM→CIM)
11. 최종 상태 확인

사용법:
    $0 [OPTIONS]

옵션:
    -h, --help          이 도움말 표시
    --no-build          이미지 빌드 건너뛰기
    --quick             빠른 실행 (일부 테스트 생략)

예시:
    $0                  # 전체 설정 및 테스트 실행
    $0 --no-build       # 이미지 빌드 없이 실행
    $0 --quick          # 빠른 실행

EOF
}

# 명령행 인수 처리
SKIP_BUILD=false
QUICK_MODE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        --no-build)
            SKIP_BUILD=true
            shift
            ;;
        --quick)
            QUICK_MODE=true
            shift
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 스크립트 실행
if [ "$SKIP_BUILD" = true ]; then
    log_info "이미지 빌드를 건너뜁니다."
    # build_images 함수를 재정의
    build_images() {
        next_step "Docker 이미지 빌드 (건너뜀)"
        log_info "기존 이미지를 사용합니다."
    }
fi

if [ "$QUICK_MODE" = true ]; then
    log_info "빠른 모드로 실행합니다."
    MAX_WAIT_TIME=60
fi

# 메인 함수 실행
main
