#!/bin/bash

# ==============================================================================
# SmartEye v0.4 - 환경 설정 스크립트
# ==============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }

# 환경 설정 함수
setup_environment() {
    local env_type=$1
    
    if [ -z "$env_type" ]; then
        echo "사용법: $0 [development|production]"
        exit 1
    fi
    
    if [ "$env_type" != "development" ] && [ "$env_type" != "production" ]; then
        log_error "올바르지 않은 환경 타입입니다. development 또는 production만 허용됩니다."
        exit 1
    fi
    
    log_info "SmartEye v0.4 환경을 $env_type 모드로 설정합니다..."
    
    # .env 파일 심볼릭 링크 생성
    if [ -L .env ]; then
        rm .env
    fi
    
    ln -s .env.$env_type .env
    log_success ".env 파일이 .env.$env_type로 연결되었습니다."
    
    # API 키 확인
    if [ "$env_type" = "production" ]; then
        if [ -z "$OPENAI_API_KEY" ]; then
            log_warning "OPENAI_API_KEY 환경변수가 설정되지 않았습니다."
            log_info "프로덕션 배포 전에 다음 명령어로 API 키를 설정하세요:"
            echo "export OPENAI_API_KEY='your-actual-api-key-here'"
        fi
        
        if [ -z "$POSTGRES_PASSWORD" ]; then
            log_warning "POSTGRES_PASSWORD 환경변수가 설정되지 않았습니다."
            log_info "프로덕션 배포 전에 다음 명령어로 데이터베이스 비밀번호를 설정하세요:"
            echo "export POSTGRES_PASSWORD='your-secure-password-here'"
        fi
    fi
    
    # 환경별 추가 설정
    if [ "$env_type" = "development" ]; then
        log_info "개발 환경 추가 설정을 적용합니다..."
        # 개발 환경용 로그 레벨 설정
        export SQL_LOGGING_LEVEL=DEBUG
        export ROOT_LOGGING_LEVEL=DEBUG
    else
        log_info "프로덕션 환경 추가 설정을 적용합니다..."
        # 프로덕션 환경용 보안 설정
        export SQL_LOGGING_LEVEL=WARN
        export ROOT_LOGGING_LEVEL=INFO
    fi
    
    log_success "환경 설정이 완료되었습니다!"
    log_info "현재 환경: $env_type"
    log_info ".env 파일 위치: $(readlink -f .env)"
}

# API 키 보안 검증 함수
check_api_security() {
    log_info "API 키 보안 설정을 검증합니다..."
    
    # .env 파일에서 하드코딩된 API 키 확인
    if grep -q "OPENAI_API_KEY=sk-" .env 2>/dev/null; then
        log_error ".env 파일에 실제 API 키가 하드코딩되어 있습니다!"
        log_warning "보안을 위해 다음 단계를 수행하세요:"
        echo "1. .env 파일에서 OPENAI_API_KEY 라인을 주석 처리"
        echo "2. 환경변수로 API 키 설정: export OPENAI_API_KEY='your-key'"
        echo "3. Docker 실행 시 -e 옵션으로 전달"
        return 1
    fi
    
    log_success "API 키 보안 설정이 올바릅니다."
    return 0
}

# 메인 실행 부분
case "${1:-}" in
    "development"|"dev")
        setup_environment "development"
        ;;
    "production"|"prod")
        setup_environment "production"
        check_api_security
        ;;
    "check"|"verify")
        check_api_security
        ;;
    *)
        echo "SmartEye v0.4 환경 설정 스크립트"
        echo ""
        echo "사용법:"
        echo "  $0 development    # 개발 환경으로 설정"
        echo "  $0 production     # 프로덕션 환경으로 설정"
        echo "  $0 check          # API 키 보안 설정 검증"
        echo ""
        echo "예시:"
        echo "  ./scripts/setup-env.sh development"
        echo "  OPENAI_API_KEY='sk-xxx' ./scripts/setup-env.sh production"
        exit 1
        ;;
esac