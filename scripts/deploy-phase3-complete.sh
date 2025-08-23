#!/bin/bash

# 3단계: SmartEye 시스템 전체 배포 스크립트
# LAM 마이크로서비스 최적화 + Java 네이티브 TSPM + 성능 모니터링

set -e

echo "=========================================="
echo "SmartEye v0.1 - 3단계 시스템 전체 배포"
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
LAM_SERVICE_DIR="$PROJECT_ROOT/smarteye-lam-service"

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
    
    # Java 확인
    if ! command -v java &> /dev/null; then
        log_error "Java가 설치되지 않았습니다."
        exit 1
    fi
    
    # Gradle 확인
    if [ ! -f "./gradlew" ]; then
        log_error "Gradle wrapper를 찾을 수 없습니다."
        exit 1
    fi
    
    log_success "전제조건 확인 완료"
}

# 함수: Java 애플리케이션 빌드
build_java_application() {
    log_info "Java 애플리케이션 빌드 중..."
    
    # Gradle 권한 설정
    chmod +x ./gradlew
    
    # 테스트 포함 빌드
    ./gradlew clean build
    
    if [ $? -eq 0 ]; then
        log_success "Java 애플리케이션 빌드 완료"
    else
        log_error "Java 애플리케이션 빌드 실패"
        exit 1
    fi
}

# 함수: 기존 서비스 정리
cleanup_existing_services() {
    log_info "기존 서비스 정리 중..."
    
    # 기존 컨테이너 중지 및 제거
    docker-compose -f docker-compose.yml down --remove-orphans 2>/dev/null || true
    docker-compose -f docker-compose.optimized.yml down --remove-orphans 2>/dev/null || true
    
    # 사용하지 않는 이미지 정리
    docker image prune -f
    
    log_success "기존 서비스 정리 완료"
}

# 함수: LAM 마이크로서비스 배포
deploy_lam_microservice() {
    log_info "LAM 마이크로서비스 배포 중..."
    
    if [ ! -d "$LAM_SERVICE_DIR" ]; then
        log_error "LAM 서비스 디렉토리를 찾을 수 없습니다: $LAM_SERVICE_DIR"
        exit 1
    fi
    
    cd "$LAM_SERVICE_DIR"
    
    # 최적화된 Docker 이미지 빌드
    if [ -f "Dockerfile.optimized" ]; then
        log_info "최적화된 Dockerfile 사용"
        docker build -f Dockerfile.optimized -t smarteye-lam-optimized:latest .
    else
        log_info "기본 Dockerfile 사용"
        docker build -t smarteye-lam-service:latest .
    fi
    
    if [ $? -eq 0 ]; then
        log_success "LAM 마이크로서비스 이미지 빌드 완료"
    else
        log_error "LAM 마이크로서비스 이미지 빌드 실패"
        exit 1
    fi
    
    cd "$PROJECT_ROOT"
}

# 함수: 전체 시스템 시작
start_system() {
    log_info "전체 시스템 시작 중..."
    
    # 환경 변수 파일 확인
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            log_info ".env 파일이 없습니다. .env.example을 복사합니다."
            cp .env.example .env
            log_warning ".env 파일을 생성했습니다. 실제 값으로 수정해주세요."
        else
            log_warning ".env 파일과 .env.example 파일이 모두 없습니다."
        fi
    fi
    
    # 통합된 Docker Compose 사용
    log_info "통합된 Docker Compose 구성 사용"
    docker-compose up -d
    
    if [ $? -eq 0 ]; then
        log_success "Docker Compose 서비스 시작 완료"
    else
        log_error "Docker Compose 서비스 시작 실패"
        exit 1
    fi
}

# 함수: Java 애플리케이션 시작
start_java_application() {
    log_info "Java 애플리케이션 시작 중..."
    
    # 백그라운드에서 Spring Boot 애플리케이션 실행
    nohup java -jar build/libs/smarteye-*.jar > app.log 2>&1 &
    JAVA_PID=$!
    
    # PID 저장
    echo $JAVA_PID > smarteye.pid
    
    log_success "Java 애플리케이션 시작 완료 (PID: $JAVA_PID)"
}

# 함수: 서비스 상태 확인
check_services() {
    log_info "서비스 상태 확인 중..."
    
    # Docker 서비스 상태 확인
    log_info "Docker 서비스 상태:"
    docker-compose ps
    
    # LAM 마이크로서비스 헬스체크
    log_info "LAM 마이크로서비스 헬스체크..."
    sleep 10  # 서비스 시작 대기
    
    for i in {1..5}; do
        if curl -f http://localhost:8081/health &>/dev/null; then
            log_success "LAM 마이크로서비스 정상 작동"
            break
        else
            log_warning "LAM 마이크로서비스 헬스체크 시도 $i/5..."
            sleep 5
        fi
        
        if [ $i -eq 5 ]; then
            log_error "LAM 마이크로서비스 헬스체크 실패"
        fi
    done
    
    # Java 애플리케이션 헬스체크
    log_info "Java 애플리케이션 헬스체크..."
    sleep 15  # 애플리케이션 시작 대기
    
    for i in {1..10}; do
        if curl -f http://localhost:8080/actuator/health &>/dev/null; then
            log_success "Java 애플리케이션 정상 작동"
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

# 함수: 배포 정보 출력
print_deployment_info() {
    log_success "=========================================="
    log_success "SmartEye v0.1 - 3단계 배포 완료!"
    log_success "=========================================="
    
    echo
    log_info "서비스 접속 정보:"
    echo "  - Java 애플리케이션: http://localhost:8080"
    echo "  - LAM 마이크로서비스: http://localhost:8081"
    echo "  - Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "  - LAM Swagger: http://localhost:8081/docs"
    
    echo
    log_info "성능 모니터링:"
    echo "  - 시스템 대시보드: http://localhost:8080/api/v3/monitoring/dashboard"
    echo "  - 성능 요약: http://localhost:8080/api/v3/monitoring/performance/summary"
    echo "  - 성능 알림: http://localhost:8080/api/v3/monitoring/performance/alerts"
    
    echo
    log_info "주요 API 엔드포인트:"
    echo "  - 통합 분석: POST /api/v2/analysis/integrated"
    echo "  - LAM 분석: POST /api/v1/lam/analyze"
    echo "  - TSPM 분석: POST /api/v1/tspm/analyze"
    
    echo
    log_info "로그 파일:"
    echo "  - Java 애플리케이션: app.log"
    echo "  - Docker 로그: docker-compose logs -f"
    
    echo
    log_info "중지 방법:"
    echo "  - ./scripts/stop-system.sh"
    echo "  - docker-compose down"
}

# 메인 실행 함수
main() {
    log_info "3단계 SmartEye 시스템 배포 시작..."
    
    check_prerequisites
    build_java_application
    cleanup_existing_services
    deploy_lam_microservice
    start_system
    start_java_application
    check_services
    print_deployment_info
    
    log_success "전체 배포 완료!"
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
