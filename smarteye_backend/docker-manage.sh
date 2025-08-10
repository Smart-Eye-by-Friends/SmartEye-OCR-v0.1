#!/bin/bash

# SmartEye Docker Management Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수들
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

# 도움말 표시
show_help() {
    echo "SmartEye Docker Management Script"
    echo
    echo "사용법: $0 [COMMAND] [OPTIONS]"
    echo
    echo "Commands:"
    echo "  dev           개발 환경 시작"
    echo "  prod          프로덕션 환경 시작"
    echo "  stop          모든 서비스 중지"
    echo "  restart       서비스 재시작"
    echo "  logs          로그 확인"
    echo "  shell         Django shell 접속"
    echo "  dbshell       데이터베이스 shell 접속"
    echo "  migrate       데이터베이스 마이그레이션"
    echo "  collectstatic 정적 파일 수집"
    echo "  test          테스트 실행"
    echo "  clean         사용하지 않는 Docker 리소스 정리"
    echo "  build         이미지 다시 빌드"
    echo "  health        헬스체크 실행"
    echo "  backup        데이터베이스 백업"
    echo "  restore       데이터베이스 복원"
    echo
    echo "Options:"
    echo "  -f, --follow  로그 팔로우 (logs 명령어와 함께 사용)"
    echo "  -h, --help    이 도움말 표시"
}

# 환경 파일 확인
check_env_file() {
    if [ ! -f .env.docker ]; then
        log_warning ".env.docker 파일이 없습니다. .env.example을 참고하여 생성하세요."
        return 1
    fi
}

# 개발 환경 시작
start_dev() {
    log_info "개발 환경을 시작합니다..."
    docker compose -f docker-compose.dev.yml up -d --build
    log_success "개발 환경이 시작되었습니다!"
    log_info "웹 애플리케이션: http://localhost:8000"
    log_info "Flower 모니터링: http://localhost:5555"
    log_info "관리자 페이지: http://localhost:8000/admin/"
}

# 프로덕션 환경 시작
start_prod() {
    log_info "프로덕션 환경을 시작합니다..."
    check_env_file || return 1
    docker compose --env-file .env.docker up -d
    log_success "프로덕션 환경이 시작되었습니다!"
    log_info "웹 애플리케이션: http://localhost"
    log_info "관리자 페이지: http://localhost/admin/"
}

# 서비스 중지
stop_services() {
    log_info "모든 서비스를 중지합니다..."
    docker compose -f docker-compose.dev.yml down 2>/dev/null || true
    docker compose --env-file .env.docker down 2>/dev/null || true
    log_success "모든 서비스가 중지되었습니다."
}

# 서비스 재시작
restart_services() {
    log_info "서비스를 재시작합니다..."
    stop_services
    if [ "$ENV" = "prod" ]; then
        start_prod
    else
        start_dev
    fi
}

# 로그 확인
show_logs() {
    local follow_flag=""
    if [ "$1" = "-f" ] || [ "$1" = "--follow" ]; then
        follow_flag="-f"
    fi
    
    log_info "로그를 확인합니다..."
    if [ -f docker-compose.dev.yml ] && docker-compose -f docker-compose.dev.yml ps -q > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml logs $follow_flag
    else
        docker-compose --env-file .env.docker logs $follow_flag
    fi
}

# Django shell 접속
run_shell() {
    log_info "Django shell에 접속합니다..."
    if docker-compose -f docker-compose.dev.yml ps web > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec web python manage.py shell
    else
        docker-compose --env-file .env.docker exec web python manage.py shell
    fi
}

# 데이터베이스 shell 접속
run_dbshell() {
    log_info "데이터베이스 shell에 접속합니다..."
    if docker-compose -f docker-compose.dev.yml ps web > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec web python manage.py dbshell
    else
        docker-compose --env-file .env.docker exec web python manage.py dbshell
    fi
}

# 마이그레이션 실행
migrate() {
    echo "[INFO] 데이터베이스 마이그레이션을 실행합니다..."
    docker compose -f docker-compose.dev.yml exec web python manage.py migrate
    echo "[SUCCESS] 마이그레이션이 완료되었습니다!"
}

# 정적 파일 수집
collectstatic() {
    echo "[INFO] 정적 파일을 수집합니다..."
    docker compose -f docker-compose.dev.yml exec web python manage.py collectstatic --noinput
    echo "[SUCCESS] 정적 파일 수집이 완료되었습니다!"
}

# 테스트 실행
run_tests() {
    log_info "테스트를 실행합니다..."
    if docker-compose -f docker-compose.dev.yml ps web > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec web python manage.py test
    else
        docker-compose --env-file .env.docker exec web python manage.py test
    fi
}

# Docker 리소스 정리
clean_docker() {
    log_info "사용하지 않는 Docker 리소스를 정리합니다..."
    docker system prune -f
    docker volume prune -f
    log_success "Docker 리소스 정리가 완료되었습니다."
}

# 이미지 다시 빌드
rebuild_images() {
    log_info "Docker 이미지를 다시 빌드합니다..."
    if [ "$ENV" = "prod" ]; then
        docker-compose --env-file .env.docker build --no-cache
    else
        docker-compose -f docker-compose.dev.yml build --no-cache
    fi
    log_success "이미지 빌드가 완료되었습니다."
}

# 헬스체크 실행
run_healthcheck() {
    log_info "헬스체크를 실행합니다..."
    if docker-compose -f docker-compose.dev.yml ps web > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec web ./healthcheck.sh
    else
        docker-compose --env-file .env.docker exec web ./healthcheck.sh
    fi
}

# 데이터베이스 백업
backup_database() {
    log_info "데이터베이스를 백업합니다..."
    local backup_file="backup_$(date +%Y%m%d_%H%M%S).sql"
    
    if docker-compose -f docker-compose.dev.yml ps db > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec db pg_dump -U smarteye_user smarteye_dev > "$backup_file"
    else
        docker-compose --env-file .env.docker exec db pg_dump -U smarteye_user smarteye_db > "$backup_file"
    fi
    
    log_success "데이터베이스가 $backup_file로 백업되었습니다."
}

# 데이터베이스 복원
restore_database() {
    local backup_file="$1"
    
    if [ -z "$backup_file" ]; then
        log_error "백업 파일을 지정해주세요."
        echo "사용법: $0 restore <backup_file>"
        return 1
    fi
    
    if [ ! -f "$backup_file" ]; then
        log_error "백업 파일 '$backup_file'을 찾을 수 없습니다."
        return 1
    fi
    
    log_info "데이터베이스를 복원합니다..."
    log_warning "기존 데이터가 모두 삭제됩니다. 계속하시겠습니까? (y/N)"
    read -r confirm
    
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        log_info "복원이 취소되었습니다."
        return 0
    fi
    
    if docker-compose -f docker-compose.dev.yml ps db > /dev/null 2>&1; then
        docker-compose -f docker-compose.dev.yml exec -T db psql -U smarteye_user smarteye_dev < "$backup_file"
    else
        docker-compose --env-file .env.docker exec -T db psql -U smarteye_user smarteye_db < "$backup_file"
    fi
    
    log_success "데이터베이스 복원이 완료되었습니다."
}

# 메인 로직
case "$1" in
    "dev")
        ENV="dev"
        start_dev
        ;;
    "prod")
        ENV="prod"
        start_prod
        ;;
    "stop")
        stop_services
        ;;
    "restart")
        restart_services
        ;;
    "logs")
        show_logs "$2"
        ;;
    "shell")
        run_shell
        ;;
    "dbshell")
        run_dbshell
        ;;
    "migrate")
        migrate
        ;;
    "collectstatic")
        collectstatic
        ;;
    "test")
        run_tests
        ;;
    "clean")
        clean_docker
        ;;
    "build")
        rebuild_images
        ;;
    "health")
        run_healthcheck
        ;;
    "backup")
        backup_database
        ;;
    "restore")
        restore_database "$2"
        ;;
    "-h"|"--help"|"help"|"")
        show_help
        ;;
    *)
        log_error "알 수 없는 명령어: $1"
        show_help
        exit 1
        ;;
esac
