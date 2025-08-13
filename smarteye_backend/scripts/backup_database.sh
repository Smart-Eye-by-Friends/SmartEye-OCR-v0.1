#!/bin/bash

# SmartEye Backend 데이터베이스 백업 스크립트

set -e

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 설정
BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
COMPOSE_FILE="docker-compose.dev.yml"

# 백업 디렉토리 생성
mkdir -p "$BACKUP_DIR"

# PostgreSQL 백업
backup_database() {
    log_info "데이터베이스 백업 시작..."
    
    local backup_file="$BACKUP_DIR/smarteye_db_$TIMESTAMP.sql"
    
    docker compose -f "$COMPOSE_FILE" exec -T db pg_dump \
        -U smarteye_user \
        -d smarteye_db \
        --clean \
        --if-exists \
        --create \
        > "$backup_file"
    
    if [ -f "$backup_file" ]; then
        local size=$(du -h "$backup_file" | cut -f1)
        log_info "데이터베이스 백업 완료: $backup_file ($size)"
    else
        log_error "데이터베이스 백업 실패"
        exit 1
    fi
}

# 미디어 파일 백업
backup_media() {
    log_info "미디어 파일 백업 시작..."
    
    local media_backup="$BACKUP_DIR/media_$TIMESTAMP.tar.gz"
    
    if [ -d "./media" ]; then
        tar -czf "$media_backup" media/
        log_info "미디어 파일 백업 완료: $media_backup"
    else
        log_warning "미디어 디렉토리가 없습니다"
    fi
}

# 로그 파일 백업
backup_logs() {
    log_info "로그 파일 백업 시작..."
    
    local logs_backup="$BACKUP_DIR/logs_$TIMESTAMP.tar.gz"
    
    if [ -d "./logs" ]; then
        tar -czf "$logs_backup" logs/
        log_info "로그 파일 백업 완료: $logs_backup"
    else
        log_warning "로그 디렉토리가 없습니다"
    fi
}

# 백업 정리 (7일 이상 된 백업 삭제)
cleanup_old_backups() {
    log_info "오래된 백업 파일 정리..."
    
    find "$BACKUP_DIR" -name "*.sql" -mtime +7 -delete
    find "$BACKUP_DIR" -name "*.tar.gz" -mtime +7 -delete
    
    log_info "백업 정리 완료"
}

# 메인 실행
main() {
    log_info "SmartEye Backend 백업 시작 ($TIMESTAMP)"
    
    backup_database
    backup_media
    backup_logs
    cleanup_old_backups
    
    log_info "백업 완료! 파일 위치: $BACKUP_DIR"
}

main "$@"
