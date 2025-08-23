#!/bin/bash

# SmartEye v0.1 - 개발 환경 배포 스크립트

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
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# 프로젝트 루트 디렉토리
PROJECT_ROOT=$(pwd)

# 함수: 전제조건 확인
check_prerequisites() {
    log_info "전제조건 확인 중..."
    
    # Docker 확인
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되지 않았습니다."
        exit 1
    fi
    
    # Docker Compose 확인
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose가 설치되지 않았습니다."
        exit 1
    fi
    
    log_success "전제조건 확인 완료"
}

# 함수: 기존 개발 서비스 정리
cleanup_dev_services() {
    log_info "기존 개발 서비스 정리 중..."
    
    # 기존 개발 컨테이너 중지 및 제거
    docker-compose -f docker-compose.dev.yml down --remove-orphans 2>/dev/null || true
    
    log_success "기존 개발 서비스 정리 완료"
}

# 함수: 개발 환경 설정
setup_dev_environment() {
    log_info "개발 환경 설정 중..."
    
    # 개발용 환경 변수 파일 확인
    if [ ! -f ".env.dev" ]; then
        log_error ".env.dev 파일을 찾을 수 없습니다."
        exit 1
    fi
    
    # 개발용 디렉토리 생성
    mkdir -p temp logs models data
    
    log_success "개발 환경 설정 완료"
}

# 함수: 개발 환경 시작
start_dev_system() {
    log_info "개발 환경 시작 중..."
    
    # 개발 환경 Docker Compose 실행
    docker-compose -f docker-compose.dev.yml --env-file .env.dev up -d
    
    if [ $? -eq 0 ]; then
        log_success "개발 환경 시작 완료"
    else
        log_error "개발 환경 시작 실패"
        exit 1
    fi
}

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
