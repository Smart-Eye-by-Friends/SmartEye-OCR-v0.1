#!/bin/bash

# SmartEye v0.4 통합 서비스 시작 스크립트 (Enhanced Version)
# Docker Compose 기반 마이크로서비스 시작 with Frontend 통합 지원

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 기본 모드 설정
MODE="production"
COMPOSE_FILE="docker-compose.yml"
DETACHED=true
SHOW_LOGS=false

# 도움말 함수
show_help() {
    echo "SmartEye v0.4 통합 서비스 관리 도구"
    echo ""
    echo "사용법:"
    echo "  $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  --dev, -d           개발 모드로 실행 (Hot Reload 지원)"
    echo "  --prod, -p          프로덕션 모드로 실행 (기본값)"
    echo "  --logs, -l          서비스 시작 후 실시간 로그 표시"
    echo "  --foreground, -f    포그라운드에서 실행"
    echo "  --help, -h          이 도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0 --dev --logs     # 개발 모드로 시작하고 로그 표시"
    echo "  $0 --prod           # 프로덕션 모드로 백그라운드 실행"
    echo ""
}

# 인수 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        --dev|-d)
            MODE="development"
            COMPOSE_FILE="docker-compose-dev.yml"
            shift
            ;;
        --prod|-p)
            MODE="production"
            COMPOSE_FILE="docker-compose.yml"
            shift
            ;;
        --logs|-l)
            SHOW_LOGS=true
            shift
            ;;
        --foreground|-f)
            DETACHED=false
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

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

log_mode() {
    echo -e "${MAGENTA}🎯 $1${NC}"
}

# 오류 시 정리 함수
cleanup_on_error() {
    log_error "오류가 발생했습니다. 서비스를 정리합니다..."
    docker-compose -f "$COMPOSE_FILE" down --remove-orphans || true
    exit 1
}

# 트랩 설정 (오류 시 정리)
trap cleanup_on_error ERR

echo "🚀 SmartEye v0.4 통합 서비스 시작 중..."
echo "📅 $(date)"
log_mode "실행 모드: $MODE"
log_info "Docker Compose 파일: $COMPOSE_FILE"

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
if [[ ! -f "$COMPOSE_FILE" ]]; then
    log_error "$COMPOSE_FILE 파일을 찾을 수 없습니다."
    exit 1
fi

# Frontend 디렉토리 확인
FRONTEND_DIR="../Frontend"
if [[ ! -d "$FRONTEND_DIR" ]]; then
    log_warning "Frontend 디렉토리를 찾을 수 없습니다: $FRONTEND_DIR"
    log_warning "Frontend 서비스는 건너뜁니다."
fi

# 필수 파일 존재 확인
required_files=("init.sql" "smarteye-backend/Dockerfile" "smarteye-lam-service/Dockerfile")
if [[ "$MODE" == "development" ]]; then
    required_files+=("$FRONTEND_DIR/Dockerfile.dev")
else
    required_files+=("$FRONTEND_DIR/Dockerfile")
fi

for file in "${required_files[@]}"; do
    if [[ ! -f "$file" ]]; then
        log_warning "필수 파일이 누락되었습니다: $file"
    fi
done

# 기존 컨테이너 정리
log_info "기존 컨테이너 정리 중..."
docker-compose -f "$COMPOSE_FILE" down --remove-orphans || true

# 사용하지 않는 이미지 정리 (선택적)
if [[ "$MODE" == "production" ]]; then
    log_info "사용하지 않는 Docker 리소스 정리 중..."
    docker system prune -f || true
fi

# 이미지 빌드
log_info "이미지 빌드 중..."
if ! docker-compose -f "$COMPOSE_FILE" build --no-cache; then
    log_error "이미지 빌드에 실패했습니다."
    exit 1
fi

# 서비스 시작
log_info "서비스 시작 중..."
if [[ "$DETACHED" == true ]]; then
    docker-compose -f "$COMPOSE_FILE" up -d
else
    log_warning "포그라운드 모드에서 실행 중... Ctrl+C로 종료하세요."
    docker-compose -f "$COMPOSE_FILE" up
    exit 0
fi

log_info "서비스 초기화 대기 중..."
sleep 20

# PostgreSQL 연결 대기 (향상된 버전)
log_info "PostgreSQL 연결 대기 중..."
timeout=90
counter=0
postgres_container=""

if [[ "$MODE" == "development" ]]; then
    postgres_container="smarteye-postgres-dev"
else
    postgres_container="smarteye-postgres"
fi

while ! docker-compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U smarteye -d smarteye_db > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        log_error "PostgreSQL 연결 타임아웃 ($timeout초)"
        echo "PostgreSQL 로그:"
        docker-compose -f "$COMPOSE_FILE" logs postgres | tail -20
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
        if [[ "$MODE" == "development" ]]; then
            docker-compose -f "$COMPOSE_FILE" logs lam-service-dev | tail -20
        else
            docker-compose -f "$COMPOSE_FILE" logs lam-service | tail -20
        fi
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
        if [[ "$MODE" == "development" ]]; then
            docker-compose -f "$COMPOSE_FILE" logs smarteye-backend-dev | tail -20
        else
            docker-compose -f "$COMPOSE_FILE" logs smarteye-backend | tail -20
        fi
        cleanup_on_error
    fi
    echo "  Java 백엔드 대기 중... ($counter/$timeout초)"
    sleep 4
    ((counter+=4))
done
log_success "Java 백엔드 준비 완료"

# Frontend 헬스체크
if [[ -d "$FRONTEND_DIR" ]]; then
    log_info "Frontend 헬스체크 중..."
    timeout=120
    counter=0
    
    if [[ "$MODE" == "development" ]]; then
        frontend_url="http://localhost:3000"
        container_name="smarteye-frontend-dev"
    else
        frontend_url="http://localhost:3000"
        container_name="smarteye-frontend"
    fi
    
    while ! curl -f "$frontend_url/health" > /dev/null 2>&1 && ! curl -f "$frontend_url" > /dev/null 2>&1; do
        if [[ $counter -ge $timeout ]]; then
            log_error "Frontend 헬스체크 타임아웃 ($timeout초)"
            echo "Frontend 로그:"
            docker-compose -f "$COMPOSE_FILE" logs "$container_name" | tail -20
            log_warning "Frontend를 건너뜁니다."
            break
        fi
        echo "  Frontend 대기 중... ($counter/$timeout초)"
        sleep 3
        ((counter+=3))
    done
    
    if [[ $counter -lt $timeout ]]; then
        log_success "Frontend 준비 완료"
    fi
fi

echo ""
log_success "SmartEye 통합 서비스가 성공적으로 시작되었습니다!"
echo ""
echo "📍 서비스 접속 정보 ($MODE 모드):"
echo "  - Java Backend API: http://localhost:8080"
echo "  - LAM Service API: http://localhost:8001"
echo "  - PostgreSQL: localhost:5433"

if [[ -d "$FRONTEND_DIR" ]]; then
    if [[ "$MODE" == "development" ]]; then
        echo "  - Frontend (개발): http://localhost:3000"
        echo "  - Frontend (Hot Reload): 활성화"
    elif [[ "$MODE" == "production" ]]; then
        echo "  - Frontend (통합): http://localhost (Nginx를 통한 서빙)"
        echo "  - Nginx 프록시: http://localhost:80"
    fi
fi

echo ""
echo "📚 API 문서:"
echo "  - Java Backend Swagger: http://localhost:8080/swagger-ui/index.html"
echo "  - LAM Service FastAPI: http://localhost:8001/docs"
echo ""

if [[ "$MODE" == "development" ]]; then
    echo "🔧 개발 모드 기능:"
    echo "  - Backend Debug 포트: localhost:5005"
    echo "  - 소스코드 변경 시 자동 리로드"
    echo "  - 실시간 로그 모니터링 가능"
    echo ""
fi

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

# Frontend 헬스체크
if [[ -d "$FRONTEND_DIR" ]]; then
    if [[ "$MODE" == "development" ]]; then
        echo -n "  - Frontend Dev (3000): "
        if response=$(curl -s http://localhost:3000 2>&1); then
            log_success "정상"
        else
            log_error "실패"
        fi
    else
        echo -n "  - Frontend (80): "
        if response=$(curl -s http://localhost 2>&1); then
            log_success "정상"
        else
            log_error "실패"
        fi
    fi
fi

# PostgreSQL 헬스체크
echo -n "  - PostgreSQL (5433): "
if docker exec "$postgres_container" pg_isready -U smarteye > /dev/null 2>&1; then
    log_success "정상"
else
    log_error "실패"
fi

echo ""
echo "🔍 서비스 상태 확인:"
docker-compose -f "$COMPOSE_FILE" ps

echo ""
echo "📋 유용한 명령어:"
echo "  📊 서비스 상태: docker-compose -f $COMPOSE_FILE ps"
echo "  📝 로그 확인: docker-compose -f $COMPOSE_FILE logs -f [서비스명]"
echo "  🔄 서비스 재시작: docker-compose -f $COMPOSE_FILE restart [서비스명]"
echo "  🛑 서비스 중지: docker-compose -f $COMPOSE_FILE down"
echo ""

if [[ "$MODE" == "development" ]]; then
    echo "🔧 개발 모드 명령어:"
    echo "  � Backend 디버그: 포트 5005로 디버거 연결"
    echo "  🔥 Frontend Hot Reload: http://localhost:3000에서 자동 갱신"
    echo "  📱 모바일 테스트: http://[IP]:3000 (네트워크 내 다른 기기에서)"
    echo ""
fi

echo "�📍 API 테스트 예제:"
if [[ -f "../test_homework_image.jpg" ]]; then
    echo "  curl -X POST -F \"image=@../test_homework_image.jpg\" -F \"modelChoice=SmartEyeSsen\" http://localhost:8080/api/document/analyze"
else
    echo "  curl -X POST -F \"image=@your_image.jpg\" -F \"modelChoice=SmartEyeSsen\" http://localhost:8080/api/document/analyze"
fi
echo ""

# 로그 표시 옵션
if [[ "$SHOW_LOGS" == true ]]; then
    echo ""
    log_info "실시간 로그를 표시합니다... (Ctrl+C로 종료)"
    echo ""
    sleep 2
    docker-compose -f "$COMPOSE_FILE" logs -f
fi

echo "🎉 모든 서비스가 준비되었습니다!"
echo "   SmartEye v0.4 - AI 기반 문서 분석 시스템"
echo ""

# 트랩 해제
trap - ERR
