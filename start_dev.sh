#!/bin/bash

# SmartEye v0.4 개선된 개발 환경 시작 스크립트
# 네이티브 개발 서버 사용으로 빠른 개발 경험 제공

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# 로고 및 헤더
echo -e "${MAGENTA}🚀 SmartEye v0.4 개선된 개발 환경${NC}"
echo -e "${BLUE}⚡ 빠른 시작 • 🔄 Hot Reload • 🐛 쉬운 디버깅${NC}"
echo "================================================"
echo

# 로그 함수들
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

log_step() {
    echo -e "${MAGENTA}🎯 $1${NC}"
}

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${GREEN}📁 프로젝트 루트: $SCRIPT_DIR${NC}"
echo

# 필수 조건 확인
log_step "1단계: 필수 조건 확인"

# Java 확인
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
    log_success "Java 발견: $JAVA_VERSION"
else
    log_error "Java가 설치되지 않았습니다. Java 21을 설치해주세요."
    exit 1
fi

# Node.js 확인
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v)
    log_success "Node.js 발견: $NODE_VERSION"
else
    log_error "Node.js가 설치되지 않았습니다. Node.js 18+을 설치해주세요."
    exit 1
fi

# Docker 확인
if command -v docker &> /dev/null; then
    log_success "Docker 발견"
else
    log_error "Docker가 설치되지 않았습니다."
    exit 1
fi

echo

# 2단계: 필수 서비스 시작 (PostgreSQL + LAM Service)
log_step "2단계: 필수 Docker 서비스 시작 (PostgreSQL + LAM Service)"

cd Backend

# 기존 컨테이너 정리
log_info "기존 컨테이너 정리 중..."
docker-compose -f docker-compose-dev.yml down --remove-orphans > /dev/null 2>&1 || true

# 필수 서비스만 시작
log_info "PostgreSQL과 LAM Service를 시작합니다..."
if docker-compose -f docker-compose-dev.yml up -d postgres lam-service-dev; then
    log_success "Docker 서비스 시작 완료"
else
    log_error "Docker 서비스 시작 실패"
    exit 1
fi

echo

# 3단계: 서비스 대기
log_step "3단계: 서비스 초기화 대기"

# PostgreSQL 대기
log_info "PostgreSQL 준비 대기 중..."
timeout=60
counter=0
while ! docker exec smarteye-postgres-dev pg_isready -U smarteye > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "PostgreSQL 시작 타임아웃"
        exit 1
    fi
    echo -n "."
    sleep 2
    ((counter+=2))
done
log_success "PostgreSQL 준비 완료"

# LAM Service 대기  
log_info "LAM Service 준비 대기 중..."
timeout=120
counter=0
while ! curl -f http://localhost:8001/health > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "LAM Service 시작 타임아웃"
        exit 1
    fi
    echo -n "."
    sleep 3
    ((counter+=3))
done
log_success "LAM Service 준비 완료"

echo

# 4단계: 개발 서버 가이드 출력
log_step "4단계: 개발 서버 시작 가이드"

cd ..

echo -e "${GREEN}🎉 필수 서비스 준비 완료!${NC}"
echo
echo -e "${YELLOW}이제 다음 명령어를 별도 터미널에서 실행하세요:${NC}"
echo
echo -e "${BLUE}📟 터미널 1: Backend 시작${NC}"
echo -e "${MAGENTA}cd Backend/smarteye-backend${NC}"
echo -e "${MAGENTA}./gradlew bootRun --args='--spring.profiles.active=dev'${NC}"
echo
echo -e "${BLUE}📱 터미널 2: Frontend 시작${NC}"
echo -e "${MAGENTA}cd Frontend${NC}"
echo -e "${MAGENTA}npm start${NC}"
echo

echo -e "${GREEN}🌐 접속 정보:${NC}"
echo "  • Frontend: http://localhost:3000"
echo "  • Backend API: http://localhost:8080"  
echo "  • LAM Service: http://localhost:8001"
echo "  • PostgreSQL: localhost:5433"
echo

echo -e "${BLUE}📚 추가 정보:${NC}"
echo "  • Swagger UI: http://localhost:8080/swagger-ui/index.html"
echo "  • LAM API Docs: http://localhost:8001/docs"
echo "  • Health Check: curl http://localhost:8080/api/health"
echo

echo -e "${YELLOW}💡 개발 팁:${NC}"
echo "  • Frontend/Backend 변경 시 자동 Hot Reload"
echo "  • IDE에서 Backend 직접 디버깅 가능"
echo "  • Ctrl+C로 개발 서버 종료"
echo "  • Docker 서비스는 백그라운드에서 계속 실행"
echo

echo -e "${GREEN}⚡ 빠른 개발 환경이 준비되었습니다!${NC}"
echo -e "${MAGENTA}Happy Coding! 🚀${NC}"
echo

# 컨테이너 상태 표시
echo -e "${BLUE}📊 현재 Docker 컨테이너 상태:${NC}"
docker-compose -f Backend/docker-compose-dev.yml ps