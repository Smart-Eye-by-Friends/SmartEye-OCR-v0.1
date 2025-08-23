#!/bin/bash

# SmartEye v0.1 - 개발 환경 배포 스크립트 (최신 아키텍처 반영)

set -e

echo "=========================================="
echo "SmartEye v0.1 - 개발 환경 배포"
echo "=========================================="

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

# 환경 변수 설정
export DOCKER_BUILDKIT=0
export COMPOSE_DOCKER_CLI_BUILD=0

# 프로젝트 루트 디렉토리
PROJECT_ROOT=$(pwd)

# 함수: 전제조건 확인
check_prerequisites() {
    log_info "전제조건 확인 중..."
    
    # Docker 확인
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되지 않았습니다."
        log_error "Docker 설치: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    # Docker Compose 확인
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose가 설치되지 않았습니다."
        log_error "Docker Compose 설치: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    # Java 확인
    if ! command -v java &> /dev/null; then
        log_warning "Java가 설치되지 않았습니다. Docker 전용 모드로 실행됩니다."
    fi
    
    log_success "전제조건 확인 완료"
}

# 함수: 기존 개발 서비스 정리
cleanup_dev_services() {
    log_info "기존 개발 서비스 정리 중..."
    
    # 기존 개발 컨테이너 중지 및 제거
    docker-compose -f docker-compose.dev.yml down --remove-orphans 2>/dev/null || true
    
    # 독립 실행 중인 LAM 서비스도 정리
    docker stop smarteye-lam-service 2>/dev/null || true
    docker rm smarteye-lam-service 2>/dev/null || true
    
    # Java 프로세스 정리
    pkill -f "gradlew bootRun" 2>/dev/null || true
    
    log_success "기존 개발 서비스 정리 완료"
}

# 함수: 개발 환경 설정
setup_dev_environment() {
    log_info "개발 환경 설정 중..."
    
    # 개발용 환경 변수 파일 생성 (없으면)
    if [ ! -f ".env.dev" ]; then
        log_info "개발용 환경 변수 파일 생성 중..."
        cat > .env.dev << EOF
# SmartEye 개발 환경 설정
SPRING_PROFILES_ACTIVE=dev
OPENAI_API_KEY=dummy-api-key-for-dev
DB_NAME=smarteye_dev
DB_USERNAME=dev
DB_PASSWORD=dev
LAM_SERVICE_URL=http://smarteye-lam-dev:8081

# 개발용 리소스 설정
UVICORN_WORKERS=2
MODEL_CACHE_SIZE=1GB
LOG_LEVEL=DEBUG
EOF
        log_success "개발용 환경 변수 파일 생성 완료"
    fi
    
    # 개발용 디렉토리 생성
    mkdir -p temp logs models data
    
    # 권한 설정
    chmod 755 temp logs models data
    
    log_success "개발 환경 설정 완료"
}

# 함수: 개발 환경 시작 (Docker 모드)
start_dev_docker() {
    log_info "Docker Compose 개발 환경 시작 중..."
    
    # 개발 환경 Docker Compose 실행
    docker-compose -f docker-compose.dev.yml --env-file .env.dev up -d
    
    if [ $? -eq 0 ]; then
        log_success "Docker Compose 개발 환경 시작 완료"
    else
        log_error "Docker Compose 개발 환경 시작 실패"
        exit 1
    fi
}

# 함수: 개발 환경 시작 (하이브리드 모드)
start_dev_hybrid() {
    log_info "하이브리드 개발 환경 시작 중..."
    
    # LAM 마이크로서비스만 Docker로 시작
    log_info "LAM 마이크로서비스 시작 중..."
    ./scripts/deploy-lam-microservice.sh
    
    # Spring Boot는 로컬에서 실행
    log_info "Spring Boot 애플리케이션 개발 모드 시작 중..."
    source scripts/setup-env.sh dev
    ./scripts/run.sh dev &
    
    log_success "하이브리드 개발 환경 시작 완료"
}

# 함수: 개발 서비스 상태 확인
check_dev_services() {
    log_info "개발 서비스 상태 확인 중..."
    
    # Docker 서비스 상태 확인
    if docker-compose -f docker-compose.dev.yml ps | grep -q "Up"; then
        log_info "Docker Compose 개발 서비스 상태:"
        docker-compose -f docker-compose.dev.yml ps
    fi
    
    # LAM 마이크로서비스 헬스체크
    log_info "LAM 마이크로서비스 헬스체크..."
    sleep 15  # 서비스 시작 대기
    
    for i in {1..5}; do
        if curl -f http://localhost:8081/health &>/dev/null; then
            log_success "LAM 마이크로서비스 정상 작동 (개발 환경)"
            break
        else
            log_warning "LAM 마이크로서비스 헬스체크 시도 $i/5..."
            sleep 5
        fi
        
        if [ $i -eq 5 ]; then
            log_warning "LAM 마이크로서비스 헬스체크 시간 초과"
        fi
    done
    
    # Java 애플리케이션 헬스체크
    log_info "Java 애플리케이션 헬스체크..."
    sleep 20  # 애플리케이션 시작 대기
    
    for i in {1..10}; do
        if curl -f http://localhost:8080/actuator/health &>/dev/null; then
            log_success "Java 애플리케이션 정상 작동 (개발 환경)"
            break
        else
            log_warning "Java 애플리케이션 헬스체크 시도 $i/10..."
            sleep 5
        fi
        
        if [ $i -eq 10 ]; then
            log_warning "Java 애플리케이션 헬스체크 시간 초과"
        fi
    done
}

# 함수: 개발 환경 정보 출력
print_dev_info() {
    log_success "=========================================="
    log_success "SmartEye v0.1 - 개발 환경 배포 완료!"
    log_success "=========================================="
    
    echo ""
    log_info "개발 환경 서비스 접속 정보:"
    echo "  ┌─ 메인 서비스"
    echo "  ├─ Spring Boot 애플리케이션: http://localhost:8080"
    echo "  ├─ LAM 마이크로서비스: http://localhost:8081"
    echo "  ├─ Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "  └─ H2 콘솔 (개발용): http://localhost:8080/h2-console"
    echo ""
    echo "  ┌─ 개발 도구 (Docker 모드일 때)"
    echo "  ├─ PostgreSQL 개발용: localhost:5433"
    echo "  ├─ Redis 개발용: localhost:6380"
    echo "  └─ LAM API 문서: http://localhost:8081/docs"
    
    echo ""
    log_info "주요 API 엔드포인트:"
    echo "  ├─ 통합 분석: POST /api/analysis/complete"
    echo "  ├─ LAM 분석: POST /api/analysis/lam"
    echo "  ├─ TSPM 분석: POST /api/analysis/tspm"
    echo "  ├─ 상태 확인: GET /api/analysis/status"
    echo "  └─ 헬스체크: GET /actuator/health"
    
    echo ""
    log_info "개발 환경 특징:"
    echo "  ├─ 🔄 소스 코드 핫 리로드 (Docker 모드)"
    echo "  ├─ 📊 디버그 로깅 활성화"
    echo "  ├─ 🚀 개발용 더미 API 키 사용"
    echo "  ├─ 💾 H2 인메모리 데이터베이스"
    echo "  └─ 🔧 개발 도구 자동 설정"
    
    echo ""
    log_info "관리 명령어:"
    echo "  ├─ 상태 확인: ./scripts/system-manager.sh status"
    echo "  ├─ 로그 확인: ./scripts/system-manager.sh logs"
    echo "  ├─ 서비스 재시작: ./scripts/system-manager.sh restart dev"
    echo "  └─ 서비스 중지: ./scripts/system-manager.sh stop"
    
    echo ""
    log_info "테스트 명령어:"
    echo "  curl -X GET http://localhost:8080/actuator/health"
    echo "  curl -X GET http://localhost:8081/health"
}

# 메인 실행 함수
main() {
    local MODE=${1:-hybrid}
    
    log_info "SmartEye 개발 환경 배포 시작... (모드: $MODE)"
    
    check_prerequisites
    cleanup_dev_services
    setup_dev_environment
    
    case $MODE in
        docker)
            start_dev_docker
            ;;
        hybrid|*)
            start_dev_hybrid
            ;;
    esac
    
    check_dev_services
    print_dev_info
    
    log_success "개발 환경 배포 완료!"
}

# 도움말
show_help() {
    echo "SmartEye v0.1 - 개발 환경 배포 스크립트"
    echo ""
    echo "Usage: $0 [mode]"
    echo ""
    echo "Modes:"
    echo "  hybrid    LAM은 Docker, Spring Boot는 로컬 실행 (기본값)"
    echo "  docker    모든 서비스를 Docker Compose로 실행"
    echo "  help      도움말 표시"
    echo ""
    echo "Examples:"
    echo "  $0            # 하이브리드 모드"
    echo "  $0 hybrid     # 하이브리드 모드"
    echo "  $0 docker     # 완전 Docker 모드"
    echo ""
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-hybrid}" in
        help)
            show_help
            ;;
        *)
            main "$@"
            ;;
    esac
fi

# 함수: 개발 서비스 상태 확인
check_dev_services() {
    log_info "개발 서비스 상태 확인 중..."
    
    # Docker 서비스 상태 확인
    log_info "개발 환경 Docker 서비스 상태:"
    docker-compose -f docker-compose.dev.yml ps
    
    # LAM 마이크로서비스 헬스체크 (개발용)
    log_info "LAM 마이크로서비스 헬스체크..."
    sleep 15  # 서비스 시작 대기
    
    for i in {1..5}; do
        if curl -f http://localhost:8081/health &>/dev/null; then
            log_success "LAM 마이크로서비스 정상 작동 (개발 환경)"
            break
        else
            log_warning "LAM 마이크로서비스 헬스체크 시도 $i/5..."
            sleep 5
        fi
        
        if [ $i -eq 5 ]; then
            log_error "LAM 마이크로서비스 헬스체크 실패"
        fi
    done
    
    # Java 애플리케이션 헬스체크 (개발용)
    log_info "Java 애플리케이션 헬스체크..."
    sleep 20  # 애플리케이션 시작 대기
    
    for i in {1..10}; do
        if curl -f http://localhost:8080/actuator/health &>/dev/null; then
            log_success "Java 애플리케이션 정상 작동 (개발 환경)"
            break
        else
            log_warning "Java 애플리케이션 헬스체크 시도 $i/10..."
            sleep 5
        fi
        
        if [ $i -eq 10 ]; then
            log_error "Java 애플리케이션 헬스체크 실패"
        fi
    done
}

# 함수: 개발 환경 정보 출력
print_dev_info() {
    log_success "=========================================="
    log_success "SmartEye v0.1 - 개발 환경 배포 완료!"
    log_success "=========================================="
    
    echo
    log_info "개발 환경 서비스 접속 정보:"
    echo "  - Java 애플리케이션: http://localhost:8080"
    echo "  - LAM 마이크로서비스: http://localhost:8081"
    echo "  - Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "  - LAM API 문서: http://localhost:8081/docs"
    echo "  - PostgreSQL (개발용): localhost:5433"
    echo "  - Redis (개발용): localhost:6380"
    
    echo
    log_info "개발용 데이터베이스 접속:"
    echo "  - 호스트: localhost"
    echo "  - 포트: 5433"
    echo "  - 데이터베이스: smarteye_dev"
    echo "  - 사용자: dev"
    echo "  - 비밀번호: dev"
    
    echo
    log_info "개발 도구:"
    echo "  - 로그 확인: docker-compose -f docker-compose.dev.yml logs -f"
    echo "  - 서비스 재시작: docker-compose -f docker-compose.dev.yml restart"
    echo "  - 서비스 중지: docker-compose -f docker-compose.dev.yml down"
    
    echo
    log_info "개발 환경 특징:"
    echo "  - 소스 코드 마운트 (실시간 반영)"
    echo "  - 리소스 절약 설정"
    echo "  - 디버그 로그 활성화"
    echo "  - 개발용 더미 API 키 사용"
}

# 메인 실행 함수
main() {
    log_info "SmartEye 개발 환경 배포 시작..."
    
    check_prerequisites
    cleanup_dev_services
    setup_dev_environment
    start_dev_system
    check_dev_services
    print_dev_info
    
    log_success "개발 환경 배포 완료!"
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
