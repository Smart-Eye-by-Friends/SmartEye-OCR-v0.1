#!/bin/bash

# SmartEye v0.1 - 통합 시스템 관리 스크립트
# 전체 시스템의 생명주기를 관리하는 마스터 스크립트

set -e

echo "=========================================="
echo "SmartEye v0.1 - 통합 시스템 관리자"
echo "=========================================="

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

log_header() {
    echo -e "${CYAN}[HEADER]${NC} $1"
}

# 시스템 상태 확인
check_system_status() {
    log_header "시스템 상태 확인"
    
    # Java 애플리케이션 상태
    if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        JAVA_STATUS="🟢 Running"
    else
        JAVA_STATUS="🔴 Stopped"
    fi
    
    # LAM 마이크로서비스 상태
    if curl -s -f http://localhost:8081/health > /dev/null 2>&1; then
        LAM_STATUS="🟢 Running"
    else
        LAM_STATUS="🔴 Stopped"
    fi
    
    # Docker 컨테이너 상태
    if docker ps --format "table {{.Names}}" | grep -q "smarteye-lam-service"; then
        LAM_CONTAINER="🟢 Running"
    else
        LAM_CONTAINER="🔴 Stopped"
    fi
    
    # Docker Compose 상태
    if docker-compose ps | grep -q "Up"; then
        COMPOSE_STATUS="🟢 Running"
    else
        COMPOSE_STATUS="🔴 Stopped"
    fi
    
    echo ""
    echo "📊 현재 시스템 상태:"
    echo "  ├─ Java Application (8080):    $JAVA_STATUS"
    echo "  ├─ LAM Microservice (8081):    $LAM_STATUS"
    echo "  ├─ LAM Container:              $LAM_CONTAINER"
    echo "  └─ Docker Compose:             $COMPOSE_STATUS"
    echo ""
}

# 전체 시스템 시작
start_system() {
    log_header "SmartEye 전체 시스템 시작"
    
    MODE=${1:-dev}
    
    case $MODE in
        dev)
            log_info "개발 모드로 시스템 시작..."
            source scripts/setup-env.sh dev
            ./scripts/run.sh dev
            ;;
        prod)
            log_info "프로덕션 모드로 시스템 시작..."
            source scripts/setup-env.sh prod
            ./scripts/run.sh prod
            ;;
        docker)
            log_info "Docker 모드로 시스템 시작..."
            source scripts/setup-env.sh docker
            ./scripts/run.sh docker
            ;;
        docker-dev)
            log_info "Docker 개발 모드로 시스템 시작..."
            ./scripts/run.sh docker-dev
            ;;
        *)
            log_error "지원하지 않는 모드: $MODE"
            log_info "사용 가능한 모드: dev, prod, docker, docker-dev"
            exit 1
            ;;
    esac
}

# 전체 시스템 중지
stop_system() {
    log_header "SmartEye 전체 시스템 중지"
    
    # Java 애플리케이션 중지 (gradlew 프로세스)
    log_info "Java 애플리케이션 중지 중..."
    pkill -f "gradlew bootRun" 2>/dev/null || true
    pkill -f "smarteye-backend" 2>/dev/null || true
    
    # LAM 컨테이너 중지
    log_info "LAM 마이크로서비스 중지 중..."
    docker stop smarteye-lam-service 2>/dev/null || true
    docker rm smarteye-lam-service 2>/dev/null || true
    
    # Docker Compose 서비스 중지
    log_info "Docker Compose 서비스 중지 중..."
    docker-compose down 2>/dev/null || true
    docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
    
    log_success "전체 시스템 중지 완료"
}

# 시스템 재시작
restart_system() {
    log_header "SmartEye 시스템 재시작"
    
    MODE=${1:-dev}
    
    stop_system
    sleep 3
    start_system $MODE
}

# 시스템 리셋 (데이터 초기화 포함)
reset_system() {
    log_header "SmartEye 시스템 리셋 (주의: 데이터가 삭제됩니다!)"
    
    echo "⚠️  경고: 이 작업은 다음을 수행합니다:"
    echo "  - 모든 서비스 중지"
    echo "  - Docker 이미지 및 볼륨 삭제"
    echo "  - 임시 파일 삭제"
    echo "  - 로그 파일 삭제"
    echo ""
    echo "계속하시겠습니까? (yes/no): "
    read -r CONFIRM
    
    if [ "$CONFIRM" = "yes" ]; then
        log_info "시스템 리셋 진행 중..."
        
        # 서비스 중지
        stop_system
        
        # Docker 이미지 제거
        log_info "Docker 이미지 제거 중..."
        docker rmi smarteye-lam-service:latest 2>/dev/null || true
        docker rmi smarteye-backend:latest 2>/dev/null || true
        
        # Docker 볼륨 제거
        log_info "Docker 볼륨 제거 중..."
        docker volume rm smarteye-lam-cache 2>/dev/null || true
        docker volume rm smarteye-lam-models 2>/dev/null || true
        
        # 임시 파일 및 로그 제거
        log_info "임시 파일 제거 중..."
        rm -rf temp/* 2>/dev/null || true
        rm -rf logs/* 2>/dev/null || true
        rm -rf data/*.db 2>/dev/null || true
        
        # 빌드 아티팩트 제거
        log_info "빌드 아티팩트 제거 중..."
        ./gradlew clean 2>/dev/null || true
        
        log_success "시스템 리셋 완료!"
        echo "💡 새로 시작하려면: ./scripts/system-manager.sh start [mode]"
    else
        log_info "시스템 리셋이 취소되었습니다."
    fi
}

# 시스템 헬스체크
health_check() {
    log_header "SmartEye 시스템 헬스체크"
    
    ERROR_COUNT=0
    
    # Java 애플리케이션 헬스체크
    log_info "Java 애플리케이션 헬스체크..."
    if curl -s -f http://localhost:8080/actuator/health > /dev/null; then
        HEALTH_RESPONSE=$(curl -s http://localhost:8080/actuator/health)
        log_success "Java 애플리케이션 정상"
        echo "   응답: $HEALTH_RESPONSE"
    else
        log_error "Java 애플리케이션 응답 없음"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
    
    # LAM 마이크로서비스 헬스체크
    log_info "LAM 마이크로서비스 헬스체크..."
    if curl -s -f http://localhost:8081/health > /dev/null; then
        LAM_HEALTH=$(curl -s http://localhost:8081/health)
        log_success "LAM 마이크로서비스 정상"
        echo "   응답: $LAM_HEALTH"
    else
        log_error "LAM 마이크로서비스 응답 없음"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
    
    # 통합 API 테스트
    log_info "통합 API 테스트..."
    if curl -s -f http://localhost:8080/api/v2/analysis/status > /dev/null; then
        API_STATUS=$(curl -s http://localhost:8080/api/v2/analysis/status)
        log_success "통합 API 정상"
        echo "   응답: $API_STATUS"
    else
        log_error "통합 API 응답 없음"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
    
    # 결과 요약
    echo ""
    if [ $ERROR_COUNT -eq 0 ]; then
        log_success "🎉 모든 시스템이 정상 작동 중입니다!"
    else
        log_error "❌ $ERROR_COUNT개의 시스템에서 문제가 발견되었습니다."
        echo ""
        echo "문제 해결 방법:"
        echo "  1. 시스템 재시작: ./scripts/system-manager.sh restart"
        echo "  2. 로그 확인: ./scripts/system-manager.sh logs"
        echo "  3. 시스템 리셋: ./scripts/system-manager.sh reset"
    fi
}

# 로그 확인
show_logs() {
    log_header "SmartEye 시스템 로그"
    
    SERVICE=${1:-all}
    
    case $SERVICE in
        java|backend)
            log_info "Java 애플리케이션 로그:"
            if [ -f "logs/smarteye.log" ]; then
                tail -50 logs/smarteye.log
            else
                echo "로그 파일이 없습니다: logs/smarteye.log"
            fi
            ;;
        lam|microservice)
            log_info "LAM 마이크로서비스 로그:"
            docker logs --tail 50 smarteye-lam-service 2>/dev/null || echo "LAM 컨테이너가 실행 중이지 않습니다."
            ;;
        all|*)
            log_info "Java 애플리케이션 로그 (최근 20줄):"
            if [ -f "logs/smarteye.log" ]; then
                tail -20 logs/smarteye.log
            else
                echo "로그 파일이 없습니다: logs/smarteye.log"
            fi
            echo ""
            log_info "LAM 마이크로서비스 로그 (최근 20줄):"
            docker logs --tail 20 smarteye-lam-service 2>/dev/null || echo "LAM 컨테이너가 실행 중이지 않습니다."
            ;;
    esac
}

# 도움말
show_help() {
    echo "SmartEye v0.1 - 통합 시스템 관리자"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  status                   현재 시스템 상태 확인"
    echo "  start [mode]            시스템 시작 (mode: dev|prod|docker|docker-dev)"
    echo "  stop                    전체 시스템 중지"
    echo "  restart [mode]          시스템 재시작"
    echo "  reset                   시스템 리셋 (데이터 삭제 포함)"
    echo "  health                  시스템 헬스체크"
    echo "  logs [service]          로그 확인 (service: java|lam|all)"
    echo "  help                    도움말 표시"
    echo ""
    echo "Examples:"
    echo "  $0 start dev            # 개발 모드로 시작"
    echo "  $0 start docker         # Docker 모드로 시작"
    echo "  $0 health               # 헬스체크 실행"
    echo "  $0 logs lam             # LAM 서비스 로그만 확인"
    echo "  $0 restart prod         # 프로덕션 모드로 재시작"
    echo ""
    echo "System Architecture:"
    echo "  ├─ Spring Boot Backend (8080)    - Main API Server"
    echo "  ├─ LAM Microservice (8081)       - Layout Analysis"
    echo "  ├─ TSPM (Java Native)            - Text Processing"
    echo "  └─ CIM (Integration)              - Content Integration"
    echo ""
}

# 메인 로직
case "${1:-status}" in
    status)
        check_system_status
        ;;
    start)
        start_system ${2:-dev}
        ;;
    stop)
        stop_system
        ;;
    restart)
        restart_system ${2:-dev}
        ;;
    reset)
        reset_system
        ;;
    health)
        health_check
        ;;
    logs)
        show_logs ${2:-all}
        ;;
    help)
        show_help
        ;;
    *)
        echo "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
