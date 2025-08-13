#!/bin/bash

# SmartEye 백엔드 테스트 실행 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 사용법 출력
usage() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  --unit           단위 테스트만 실행"
    echo "  --integration    통합 테스트만 실행"
    echo "  --regression     회귀 테스트만 실행"
    echo "  --performance    성능 테스트만 실행"
    echo "  --coverage       코드 커버리지와 함께 실행"
    echo "  --parallel       병렬 테스트 실행"
    echo "  --fast           빠른 테스트 (일부 생략)"
    echo "  --clean          테스트 전 환경 초기화"
    echo "  --help           이 도움말 출력"
    echo ""
    echo "예시:"
    echo "  $0 --coverage --parallel  # 커버리지와 병렬로 전체 테스트"
    echo "  $0 --unit --fast          # 빠른 단위 테스트만"
    echo "  $0 --clean --regression   # 환경 초기화 후 회귀 테스트"
}

# 기본 설정
UNIT_ONLY=false
INTEGRATION_ONLY=false
REGRESSION_ONLY=false
PERFORMANCE_ONLY=false
COVERAGE=false
PARALLEL=false
FAST=false
CLEAN=false

# 명령행 인수 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        --unit)
            UNIT_ONLY=true
            shift
            ;;
        --integration)
            INTEGRATION_ONLY=true
            shift
            ;;
        --regression)
            REGRESSION_ONLY=true
            shift
            ;;
        --performance)
            PERFORMANCE_ONLY=true
            shift
            ;;
        --coverage)
            COVERAGE=true
            shift
            ;;
        --parallel)
            PARALLEL=true
            shift
            ;;
        --fast)
            FAST=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            usage
            exit 1
            ;;
    esac
done

# 환경 초기화
if [ "$CLEAN" = true ]; then
    log_info "테스트 환경 초기화 중..."
    
    # Docker 컨테이너와 볼륨 정리
    docker compose -f docker-compose.test.yml down -v --remove-orphans 2>/dev/null || true
    
    # 테스트 관련 파일 정리
    find . -name "*.pyc" -delete 2>/dev/null || true
    find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
    find . -name ".pytest_cache" -type d -exec rm -rf {} + 2>/dev/null || true
    
    # 로그 파일 정리
    rm -rf logs/test.log coverage_reports/* 2>/dev/null || true
    
    log_success "환경 초기화 완료"
fi

# Docker 서비스 시작
log_info "테스트 인프라 시작 중..."
docker compose -f docker-compose.test.yml up -d test-postgres test-redis

# 서비스 준비 대기
log_info "데이터베이스 준비 대기 중..."
timeout=60
while [ $timeout -gt 0 ]; do
    if docker compose -f docker-compose.test.yml exec -T test-postgres pg_isready -U smarteye_test -d smarteye_test >/dev/null 2>&1; then
        break
    fi
    sleep 2
    timeout=$((timeout-2))
done

if [ $timeout -le 0 ]; then
    log_error "데이터베이스 연결 타임아웃"
    exit 1
fi

log_info "Redis 준비 대기 중..."
timeout=30
while [ $timeout -gt 0 ]; do
    if docker compose -f docker-compose.test.yml exec -T test-redis redis-cli ping >/dev/null 2>&1; then
        break
    fi
    sleep 1
    timeout=$((timeout-1))
done

if [ $timeout -le 0 ]; then
    log_error "Redis 연결 타임아웃"
    exit 1
fi

log_success "테스트 인프라 준비 완료"

# pytest 명령어 구성
PYTEST_ARGS=""

# 테스트 종류별 설정
if [ "$UNIT_ONLY" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS tests/unit/"
elif [ "$INTEGRATION_ONLY" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS tests/integration/"
elif [ "$REGRESSION_ONLY" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS tests/regression/"
elif [ "$PERFORMANCE_ONLY" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS tests/performance/"
else
    PYTEST_ARGS="$PYTEST_ARGS tests/"
fi

# 병렬 실행 설정
if [ "$PARALLEL" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS -n auto"
fi

# 빠른 테스트 설정
if [ "$FAST" = true ]; then
    PYTEST_ARGS="$PYTEST_ARGS --maxfail=3 -x"
else
    PYTEST_ARGS="$PYTEST_ARGS --maxfail=10"
fi

# 커버리지 설정
if [ "$COVERAGE" = true ]; then
    log_info "코드 커버리지와 함께 테스트 실행 중..."
    
    # 커버리지 디렉토리 생성
    mkdir -p coverage_reports
    
    # 커버리지 테스트 실행
    docker compose -f docker-compose.test.yml run --rm test-coverage || {
        log_error "테스트 실패"
        docker compose -f docker-compose.test.yml logs test-coverage
        exit 1
    }
    
    log_success "코드 커버리지 리포트가 coverage_reports/ 디렉토리에 생성되었습니다"
    
else
    log_info "테스트 실행 중: $PYTEST_ARGS"
    
    # 일반 테스트 실행
    docker compose -f docker-compose.test.yml run --rm -e PYTEST_ARGS="$PYTEST_ARGS" test-app $PYTEST_ARGS || {
        log_error "테스트 실패"
        docker compose -f docker-compose.test.yml logs test-app
        exit 1
    }
fi

log_success "모든 테스트가 성공적으로 완료되었습니다!"

# 정리 옵션
read -p "테스트 환경을 정리하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "테스트 환경 정리 중..."
    docker compose -f docker-compose.test.yml down -v
    log_success "테스트 환경 정리 완료"
fi