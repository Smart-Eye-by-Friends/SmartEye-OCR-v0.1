#!/bin/bash

# SmartEye v0.1 - 통합 빌드 및 실행 스크립트
# LAM 마이크로서비스 + Spring Boot 백엔드 통합 관리

set -e

echo "=========================================="
echo "SmartEye v0.1 - 통합 빌드 및 실행 스크립트"
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

# 환경변수 설정
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev}
# Docker BuildKit 문제 해결
export DOCKER_BUILDKIT=0

# 함수 정의
check_prerequisites() {
    log_info "전제조건 확인 중..."
    
    # Java 확인
    if ! command -v java &> /dev/null; then
        log_error "Java가 설치되지 않았습니다."
        exit 1
    fi
    
    # Docker 확인 (LAM 서비스용)
    if ! command -v docker &> /dev/null; then
        log_warning "Docker가 설치되지 않았습니다. LAM 마이크로서비스는 건너뜁니다."
        return 1
    fi
    
    return 0
}

build_project() {
    log_info "Spring Boot 프로젝트 빌드 중..."
    ./gradlew clean build --exclude-task test
    
    if [ $? -eq 0 ]; then
        log_success "Spring Boot 프로젝트 빌드 완료"
    else
        log_error "Spring Boot 프로젝트 빌드 실패"
        exit 1
    fi
}

start_lam_service() {
    log_info "LAM 마이크로서비스 시작 중..."
    
    # 기존 LAM 컨테이너 정리
    docker stop smarteye-lam-service 2>/dev/null || true
    docker rm smarteye-lam-service 2>/dev/null || true
    
    # LAM 서비스 디렉토리 확인
    if [ ! -d "smarteye-lam-service" ]; then
        log_error "LAM 서비스 디렉토리가 존재하지 않습니다."
        return 1
    fi
    
    cd smarteye-lam-service
    
    # Docker 이미지 빌드
    docker build -t smarteye-lam-service:latest . -q
    
    if [ $? -ne 0 ]; then
        log_error "LAM 서비스 Docker 이미지 빌드 실패"
        cd ..
        return 1
    fi
    
    # LAM 서비스 컨테이너 실행
    docker run -d --name smarteye-lam-service \
        -p 8081:8000 \
        -e PYTHONPATH=/app \
        smarteye-lam-service:latest
    
    cd ..
    
    if [ $? -eq 0 ]; then
        log_success "LAM 마이크로서비스 시작 완료"
        
        # 헬스체크
        log_info "LAM 서비스 헬스체크 중..."
        sleep 10
        
        for i in {1..5}; do
            if curl -f http://localhost:8081/health &>/dev/null; then
                log_success "LAM 마이크로서비스 정상 작동 확인"
                return 0
            else
                log_warning "LAM 서비스 헬스체크 시도 $i/5..."
                sleep 3
            fi
        done
        
        log_warning "LAM 서비스 헬스체크 실패 - 서비스는 실행 중"
        return 0
    else
        log_error "LAM 마이크로서비스 시작 실패"
        return 1
    fi
}

run_dev() {
    log_info "개발 모드로 실행 중..."
    
    # Docker가 있으면 LAM 서비스 시작
    if check_prerequisites; then
        start_lam_service
    fi
    
    log_info "Spring Boot 애플리케이션 개발 모드 시작..."
    ./gradlew bootRun --args='--spring.profiles.active=dev'
}

run_prod() {
    log_info "프로덕션 모드로 실행 중..."
    
    # Docker가 있으면 LAM 서비스 시작
    if check_prerequisites; then
        start_lam_service
    fi
    
    log_info "Spring Boot 애플리케이션 프로덕션 모드 시작..."
    ./gradlew bootRun --args='--spring.profiles.active=prod'
}

run_docker() {
    log_info "Docker Compose로 전체 시스템 실행 중..."
    
    if ! check_prerequisites; then
        log_error "Docker가 필요합니다."
        exit 1
    fi
    
    docker-compose up -d
    
    if [ $? -eq 0 ]; then
        log_success "Docker Compose 시스템 시작 완료"
        print_service_info
    else
        log_error "Docker Compose 시스템 시작 실패"
        exit 1
    fi
}

run_docker_dev() {
    log_info "Docker Compose 개발 환경으로 전체 시스템 실행 중..."
    
    if ! check_prerequisites; then
        log_error "Docker가 필요합니다."
        exit 1
    fi
    
    docker-compose -f docker-compose.dev.yml up -d
    
    if [ $? -eq 0 ]; then
        log_success "Docker Compose 개발 환경 시작 완료"
        print_service_info_dev
    else
        log_error "Docker Compose 개발 환경 시작 실패"
        exit 1
    fi
}

package_jar() {
    log_info "JAR 패키지 생성 중..."
    ./gradlew bootJar
    
    if [ $? -eq 0 ]; then
        log_success "JAR 패키지 생성 완료: build/libs/"
        ls -la build/libs/*.jar
    else
        log_error "JAR 패키지 생성 실패"
        exit 1
    fi
}

stop_services() {
    log_info "SmartEye 서비스 중지 중..."
    
    # LAM 컨테이너 중지
    docker stop smarteye-lam-service 2>/dev/null || true
    docker rm smarteye-lam-service 2>/dev/null || true
    
    # Docker Compose 서비스 중지
    docker-compose down 2>/dev/null || true
    docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
    
    log_success "SmartEye 서비스 중지 완료"
}

print_service_info() {
    echo ""
    log_success "=========================================="
    log_success "SmartEye v0.1 시스템 시작 완료!"
    log_success "=========================================="
    echo ""
    log_info "서비스 접속 정보:"
    echo "  • Spring Boot 애플리케이션: http://localhost:8080"
    echo "  • LAM 마이크로서비스: http://localhost:8081"
    echo "  • Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "  • H2 콘솔: http://localhost:8080/h2-console"
    echo ""
    log_info "주요 API 엔드포인트:"
    echo "  • 통합 분석: POST http://localhost:8080/api/v2/analysis/integrated"
    echo "  • 상태 확인: GET http://localhost:8080/api/v2/analysis/status"
    echo ""
}

print_service_info_dev() {
    echo ""
    log_success "=========================================="
    log_success "SmartEye v0.1 개발 환경 시작 완료!"
    log_success "=========================================="
    echo ""
    log_info "개발 환경 서비스 접속 정보:"
    echo "  • Spring Boot 애플리케이션: http://localhost:8080"
    echo "  • LAM 마이크로서비스: http://localhost:8081"
    echo "  • PostgreSQL 개발용: localhost:5433"
    echo "  • Redis 개발용: localhost:6380"
    echo ""
}

show_help() {
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "SmartEye v0.1 - 하이브리드 마이크로서비스 시스템"
    echo ""
    echo "Options:"
    echo "  build         Spring Boot 프로젝트만 빌드"
    echo "  dev           개발 모드로 실행 (LAM + Spring Boot)"
    echo "  prod          프로덕션 모드로 실행 (LAM + Spring Boot)"
    echo "  docker        Docker Compose로 전체 시스템 실행"
    echo "  docker-dev    Docker Compose 개발 환경으로 실행"
    echo "  package       JAR 패키지 생성"
    echo "  stop          모든 SmartEye 서비스 중지"
    echo "  help          도움말 표시"
    echo ""
    echo "Examples:"
    echo "  $0 dev        # 개발용 로컬 실행"
    echo "  $0 docker-dev # Docker 개발 환경"
    echo "  $0 docker     # Docker 프로덕션 환경"
    echo ""
}

# 메인 로직
case "${1:-dev}" in
    build)
        build_project
        ;;
    dev)
        build_project && run_dev
        ;;
    prod)
        build_project && run_prod
        ;;
    docker)
        run_docker
        ;;
    docker-dev)
        run_docker_dev
        ;;
    package)
        build_project && package_jar
        ;;
    stop)
        stop_services
        ;;
    help)
        show_help
        ;;
    *)
        echo "Unknown option: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
