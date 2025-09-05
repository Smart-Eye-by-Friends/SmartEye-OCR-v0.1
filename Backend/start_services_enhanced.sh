#!/bin/bash

# SmartEye v0.4 전체 서비스 시작 스크립트 (Enhanced Version)
# Docker Compose 기반 마이크로서비스 시작 with 향상된 오류 처리

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

# 오류 시 정리 함수
cleanup_on_error() {
    log_error "오류가 발생했습니다. 서비스를 정리합니다..."
    docker-compose down --remove-orphans || true
    exit 1
}

# 트랩 설정 (오류 시 정리)
trap cleanup_on_error ERR

echo "🚀 SmartEye v0.4 서비스 시작 중..."
echo "📅 $(date)"

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Docker 설치 확인
if ! command -v docker &> /dev/null; then
    log_error "Docker가 설치되어 있지 않습니다."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose가 설치되어 있지 않습니다."
    exit 1
fi

# Docker 서비스 실행 확인
if ! docker info &> /dev/null; then
    log_error "Docker 서비스가 실행되지 않았습니다. Docker를 시작해주세요."
    exit 1
fi

# Docker Compose 파일 존재 확인
if [[ ! -f "docker-compose.yml" ]]; then
    log_error "docker-compose.yml 파일을 찾을 수 없습니다."
    exit 1
fi

# 필수 파일 존재 확인
required_files=("init.sql" "smarteye-backend/Dockerfile" "smarteye-lam-service/Dockerfile")
for file in "${required_files[@]}"; do
    if [[ ! -f "$file" ]]; then
        log_warning "필수 파일이 누락되었습니다: $file"
    fi
done

# 기존 컨테이너 정리
log_info "기존 컨테이너 정리 중..."
docker-compose down --remove-orphans || true

# 사용하지 않는 이미지 정리 (선택적)
log_info "사용하지 않는 Docker 리소스 정리 중..."
docker system prune -f || true

# 이미지 빌드
log_info "이미지 빌드 중..."
if ! docker-compose build --no-cache; then
    log_error "이미지 빌드에 실패했습니다."
    exit 1
fi

# 서비스 시작
log_info "서비스 시작 중..."
docker-compose up -d

log_info "기본 서비스 시작 대기 중..."
sleep 20

# PostgreSQL 연결 대기 (향상된 버전)
log_info "PostgreSQL 연결 대기 중..."
timeout=90
counter=0
while ! docker-compose exec -T postgres pg_isready -U smarteye -d smarteye_db > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "PostgreSQL 연결 타임아웃 ($timeout초)"
        echo "PostgreSQL 로그:"
        docker-compose logs postgres | tail -20
        cleanup_on_error
    fi
    echo "  PostgreSQL 대기 중... ($counter/$timeout초)"
    sleep 3
    ((counter+=3))
done
log_success "PostgreSQL 연결 성공"

# LAM 서비스 헬스체크 (향상된 버전)
log_info "LAM 서비스 헬스체크 중..."
timeout=240  # 4분으로 연장
counter=0
while ! curl -f http://localhost:8001/health > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "LAM 서비스 헬스체크 타임아웃 ($timeout초)"
        echo "LAM 서비스 로그:"
        docker-compose logs lam-service | tail -20
        cleanup_on_error
    fi
    echo "  LAM 서비스 대기 중... ($counter/$timeout초)"
    sleep 5
    ((counter+=5))
done
log_success "LAM 서비스 준비 완료"

# Java 백엔드 헬스체크 (향상된 버전)
log_info "Java 백엔드 헬스체크 중..."
timeout=150  # 2.5분으로 연장
counter=0
while ! curl -f http://localhost:8080/api/health > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "Java 백엔드 헬스체크 타임아웃 ($timeout초)"
        echo "Java 백엔드 로그:"
        docker-compose logs smarteye-backend | tail -20
        cleanup_on_error
    fi
    echo "  Java 백엔드 대기 중... ($counter/$timeout초)"
    sleep 4
    ((counter+=4))
done
log_success "Java 백엔드 준비 완료"

echo ""
log_success "SmartEye 서비스가 성공적으로 시작되었습니다!"
echo ""
echo "📍 서비스 접속 정보:"
echo "  - Java Backend API: http://localhost:8080"
echo "  - LAM Service API: http://localhost:8001"
echo "  - PostgreSQL: localhost:5433"
echo ""
echo "📚 API 문서:"
echo "  - Java Backend Swagger: http://localhost:8080/swagger-ui/index.html"
echo "  - LAM Service FastAPI: http://localhost:8001/docs"
echo ""
echo "🏥 최종 헬스체크:"

# Backend 헬스체크
echo -n "  - Backend (8080): "
if response=$(curl -s http://localhost:8080/api/health 2>&1); then
    log_success "정상"
else
    log_error "실패"
fi

# LAM Service 헬스체크  
echo -n "  - LAM Service (8001): "
if response=$(curl -s http://localhost:8001/health 2>&1); then
    log_success "정상"
else
    log_error "실패"
fi

# PostgreSQL 헬스체크
echo -n "  - PostgreSQL (5433): "
if docker exec smarteye-postgres pg_isready -U smarteye > /dev/null 2>&1; then
    log_success "정상"
else
    log_error "실패"
fi

echo ""
echo "🔍 서비스 상태 확인:"
docker-compose ps

echo ""
echo "📋 유용한 명령어:"
echo "  📊 서비스 상태: docker-compose ps"
echo "  📝 로그 확인: docker-compose logs -f [서비스명]"
echo "  🔄 서비스 재시작: docker-compose restart [서비스명]"
echo "  🛑 서비스 중지: docker-compose down"
echo ""
echo "📍 API 테스트 예제:"
echo "  curl -X POST -F \"image=@test_homework_image.jpg\" -F \"modelChoice=SmartEyeSsen\" http://localhost:8080/api/document/analyze"
echo ""
echo "🎉 모든 서비스가 준비되었습니다!"
