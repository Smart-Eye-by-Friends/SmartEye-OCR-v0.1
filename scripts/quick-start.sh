#!/bin/bash

# SmartEye v0.1 - 빠른 시작 스크립트
# 최소한의 설정으로 SmartEye 시스템을 빠르게 시작

set -e

echo "🚀 SmartEye v0.1 - 빠른 시작"
echo "=============================="

# 색상 정의
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# 환경 확인
check_environment() {
    log_info "환경 확인 중..."
    
    # Java 확인
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1)
        log_success "Java 설치됨: $JAVA_VERSION"
        JAVA_AVAILABLE=true
    else
        log_warning "Java가 설치되지 않았습니다."
        JAVA_AVAILABLE=false
    fi
    
    # Docker 확인
    if command -v docker &> /dev/null; then
        DOCKER_VERSION=$(docker --version)
        log_success "Docker 설치됨: $DOCKER_VERSION"
        DOCKER_AVAILABLE=true
    else
        log_warning "Docker가 설치되지 않았습니다."
        DOCKER_AVAILABLE=false
    fi
    
    # 추천 모드 결정
    if [ "$JAVA_AVAILABLE" = true ] && [ "$DOCKER_AVAILABLE" = true ]; then
        RECOMMENDED_MODE="hybrid"
        log_success "추천 모드: 하이브리드 (Java + Docker)"
    elif [ "$DOCKER_AVAILABLE" = true ]; then
        RECOMMENDED_MODE="docker"
        log_success "추천 모드: Docker 전용"
    else
        log_warning "Docker 설치가 필요합니다."
        echo "Docker 설치: https://docs.docker.com/get-docker/"
        exit 1
    fi
}

# 빠른 시작 메뉴
show_quick_start_menu() {
    echo ""
    echo "🎯 SmartEye 빠른 시작 옵션:"
    echo ""
    echo "1) 🔥 즉시 시작 (추천: $RECOMMENDED_MODE)"
    echo "2) 🛠️  개발 환경 설정"
    echo "3) 🐳 Docker 전용 모드"
    echo "4) ⚙️  고급 설정"
    echo "5) 📖 도움말"
    echo "6) 🚪 종료"
    echo ""
    echo -n "선택하세요 (1-6): "
    read -r CHOICE
    
    case $CHOICE in
        1)
            quick_start_recommended
            ;;
        2)
            setup_development_environment
            ;;
        3)
            start_docker_mode
            ;;
        4)
            advanced_setup
            ;;
        5)
            show_help
            ;;
        6)
            echo "SmartEye 설치를 종료합니다."
            exit 0
            ;;
        *)
            echo "잘못된 선택입니다. 다시 시도해주세요."
            show_quick_start_menu
            ;;
    esac
}

# 추천 모드로 즉시 시작
quick_start_recommended() {
    echo ""
    log_info "🔥 추천 모드($RECOMMENDED_MODE)로 SmartEye 시작 중..."
    
    if [ "$RECOMMENDED_MODE" = "hybrid" ]; then
        log_info "LAM 마이크로서비스는 Docker로, Spring Boot는 로컬에서 실행됩니다."
        ./scripts/system-manager.sh start dev
    elif [ "$RECOMMENDED_MODE" = "docker" ]; then
        log_info "모든 서비스를 Docker로 실행합니다."
        ./scripts/system-manager.sh start docker-dev
    fi
    
    show_success_info
}

# 개발 환경 설정
setup_development_environment() {
    echo ""
    log_info "🛠️ 개발 환경 설정 중..."
    
    # 환경변수 설정
    source scripts/setup-env.sh dev
    
    # 개발 환경 배포
    ./scripts/deploy-dev.sh hybrid
    
    show_success_info
}

# Docker 전용 모드
start_docker_mode() {
    echo ""
    log_info "🐳 Docker 전용 모드로 시작 중..."
    
    ./scripts/system-manager.sh start docker-dev
    
    show_success_info
}

# 고급 설정
advanced_setup() {
    echo ""
    echo "⚙️ 고급 설정 옵션:"
    echo ""
    echo "1) 프로덕션 모드 시작"
    echo "2) 환경변수 수동 설정"
    echo "3) LAM 서비스만 시작"
    echo "4) 시스템 상태 확인"
    echo "5) 뒤로 가기"
    echo ""
    echo -n "선택하세요 (1-5): "
    read -r ADV_CHOICE
    
    case $ADV_CHOICE in
        1)
            log_info "프로덕션 모드로 시작 중..."
            ./scripts/system-manager.sh start prod
            show_success_info
            ;;
        2)
            echo ""
            echo "환경변수 설정 옵션:"
            echo "1) 개발용 (H2 DB)"
            echo "2) 프로덕션용 (PostgreSQL)"
            echo "3) Docker 용"
            echo -n "선택하세요 (1-3): "
            read -r ENV_CHOICE
            case $ENV_CHOICE in
                1) source scripts/setup-env.sh dev ;;
                2) source scripts/setup-env.sh prod ;;
                3) source scripts/setup-env.sh docker ;;
            esac
            advanced_setup
            ;;
        3)
            log_info "LAM 마이크로서비스만 시작 중..."
            ./scripts/deploy-lam-microservice.sh
            ;;
        4)
            ./scripts/system-manager.sh status
            ./scripts/system-manager.sh health
            advanced_setup
            ;;
        5)
            show_quick_start_menu
            ;;
        *)
            echo "잘못된 선택입니다."
            advanced_setup
            ;;
    esac
}

# 성공 정보 표시
show_success_info() {
    echo ""
    log_success "🎉 SmartEye가 성공적으로 시작되었습니다!"
    echo ""
    echo "📊 접속 정보:"
    echo "  ├─ 메인 애플리케이션: http://localhost:8080"
    echo "  ├─ LAM 마이크로서비스: http://localhost:8081"
    echo "  ├─ API 문서: http://localhost:8080/swagger-ui.html"
    echo "  └─ 상태 확인: http://localhost:8080/actuator/health"
    echo ""
    echo "🔧 관리 명령어:"
    echo "  ├─ 상태 확인: ./scripts/system-manager.sh status"
    echo "  ├─ 로그 확인: ./scripts/system-manager.sh logs"
    echo "  ├─ 서비스 중지: ./scripts/system-manager.sh stop"
    echo "  └─ 헬스체크: ./scripts/system-manager.sh health"
    echo ""
    echo "💡 문제가 발생하면 다음을 시도해보세요:"
    echo "  1. ./scripts/system-manager.sh health"
    echo "  2. ./scripts/system-manager.sh restart"
    echo "  3. ./scripts/system-manager.sh reset"
    echo ""
}

# 도움말
show_help() {
    echo ""
    echo "📖 SmartEye v0.1 도움말"
    echo "======================"
    echo ""
    echo "SmartEye는 하이브리드 마이크로서비스 문서 분석 시스템입니다."
    echo ""
    echo "🏗️ 시스템 구성:"
    echo "  ├─ Spring Boot Backend (8080) - 메인 API 서버"
    echo "  ├─ LAM Microservice (8081) - 레이아웃 분석 (Python/FastAPI)"
    echo "  ├─ TSPM (Java Native) - 텍스트 처리 (OCR + Vision API)"
    echo "  └─ CIM (Integration) - 결과 통합"
    echo ""
    echo "🚀 실행 모드:"
    echo "  ├─ 하이브리드: LAM(Docker) + Spring Boot(로컬)"
    echo "  ├─ Docker: 모든 서비스를 Docker로 실행"
    echo "  └─ 프로덕션: 최적화된 설정으로 실행"
    echo ""
    echo "📂 주요 스크립트:"
    echo "  ├─ quick-start.sh - 빠른 시작 (이 스크립트)"
    echo "  ├─ system-manager.sh - 전체 시스템 관리"
    echo "  ├─ run.sh - 개별 서비스 실행"
    echo "  ├─ deploy-dev.sh - 개발 환경 배포"
    echo "  └─ setup-env.sh - 환경변수 설정"
    echo ""
    echo "🔗 더 많은 정보:"
    echo "  ├─ README.md - 프로젝트 개요"
    echo "  ├─ QUICKSTART.md - 빠른 시작 가이드"
    echo "  └─ docs/ - 상세 문서"
    echo ""
    
    show_quick_start_menu
}

# 메인 실행
main() {
    check_environment
    show_quick_start_menu
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
