#!/bin/bash

# SmartEye v0.1 - 시스템 중지 스크립트 (레거시 호환성)
# 새로운 system-manager.sh로 리다이렉트

echo "🔄 새로운 시스템 관리자로 리다이렉트 중..."
echo "앞으로는 다음 명령어를 사용해주세요:"
echo "  ./scripts/system-manager.sh stop"
echo ""

# 새로운 시스템 매니저 호출
exec ./scripts/system-manager.sh stop
                    break
                fi
                sleep 1
            done
            
            # 강제 종료 (필요한 경우)
            if ps -p $JAVA_PID > /dev/null 2>&1; then
                log_warning "강제 종료 중..."
                kill -9 $JAVA_PID
                sleep 2
                
                if ! ps -p $JAVA_PID > /dev/null 2>&1; then
                    log_success "Java 애플리케이션 강제 종료 완료"
                else
                    log_error "Java 애플리케이션 종료 실패"
                fi
            fi
        else
            log_info "Java 애플리케이션이 이미 종료되었습니다."
        fi
        
        # PID 파일 제거
        rm -f smarteye.pid
    else
        log_info "PID 파일을 찾을 수 없습니다. 프로세스 이름으로 검색 중..."
        
        # 프로세스 이름으로 검색하여 종료
        PIDS=$(pgrep -f "smarteye.*\.jar" || true)
        if [ ! -z "$PIDS" ]; then
            for PID in $PIDS; do
                log_info "Java 프로세스 종료 중 (PID: $PID)..."
                kill $PID 2>/dev/null || true
            done
            sleep 3
            
            # 강제 종료 확인
            REMAINING_PIDS=$(pgrep -f "smarteye.*\.jar" || true)
            if [ ! -z "$REMAINING_PIDS" ]; then
                for PID in $REMAINING_PIDS; do
                    log_warning "강제 종료 중 (PID: $PID)..."
                    kill -9 $PID 2>/dev/null || true
                done
            fi
            
            log_success "Java 애플리케이션 종료 완료"
        else
            log_info "실행 중인 Java 애플리케이션을 찾을 수 없습니다."
        fi
    fi
}

# 함수: Docker 서비스 중지
stop_docker_services() {
    log_info "Docker 서비스 중지 중..."
    
    # 통합된 Docker Compose 중지
    if [ -f "docker-compose.yml" ]; then
        log_info "메인 Docker Compose 서비스 중지..."
        docker-compose down --remove-orphans
    fi
    
    # 개발 환경 Docker Compose 중지
    if [ -f "docker-compose.dev.yml" ]; then
        log_info "개발 환경 Docker Compose 서비스 중지..."
        docker-compose -f docker-compose.dev.yml down --remove-orphans
    fi
    
    log_success "Docker 서비스 중지 완료"
}

# 함수: 컨테이너 정리
cleanup_containers() {
    log_info "관련 컨테이너 정리 중..."
    
    # SmartEye 관련 컨테이너 찾기 및 중지
    CONTAINERS=$(docker ps -a --filter "name=smarteye" --format "{{.Names}}" || true)
    if [ ! -z "$CONTAINERS" ]; then
        for CONTAINER in $CONTAINERS; do
            log_info "컨테이너 중지 및 제거: $CONTAINER"
            docker stop $CONTAINER 2>/dev/null || true
            docker rm $CONTAINER 2>/dev/null || true
        done
    fi
    
    # LAM 관련 컨테이너 찾기 및 중지
    LAM_CONTAINERS=$(docker ps -a --filter "name=lam" --format "{{.Names}}" || true)
    if [ ! -z "$LAM_CONTAINERS" ]; then
        for CONTAINER in $LAM_CONTAINERS; do
            log_info "LAM 컨테이너 중지 및 제거: $CONTAINER"
            docker stop $CONTAINER 2>/dev/null || true
            docker rm $CONTAINER 2>/dev/null || true
        done
    fi
    
    # Redis 캐시 컨테이너 (있는 경우)
    REDIS_CONTAINERS=$(docker ps -a --filter "name=redis-cache" --format "{{.Names}}" || true)
    if [ ! -z "$REDIS_CONTAINERS" ]; then
        for CONTAINER in $REDIS_CONTAINERS; do
            log_info "Redis 컨테이너 중지 및 제거: $CONTAINER"
            docker stop $CONTAINER 2>/dev/null || true
            docker rm $CONTAINER 2>/dev/null || true
        done
    fi
    
    log_success "컨테이너 정리 완료"
}

# 함수: 네트워크 정리
cleanup_networks() {
    log_info "Docker 네트워크 정리 중..."
    
    # SmartEye 관련 네트워크 찾기 및 제거
    NETWORKS=$(docker network ls --filter "name=smarteye" --format "{{.Name}}" || true)
    if [ ! -z "$NETWORKS" ]; then
        for NETWORK in $NETWORKS; do
            log_info "네트워크 제거: $NETWORK"
            docker network rm $NETWORK 2>/dev/null || true
        done
    fi
    
    log_success "네트워크 정리 완료"
}

# 함수: 포트 확인 및 정리
cleanup_ports() {
    log_info "포트 사용 확인 중..."
    
    # 8080 포트 확인
    PROCESS_8080=$(lsof -ti:8080 || true)
    if [ ! -z "$PROCESS_8080" ]; then
        log_warning "포트 8080을 사용하는 프로세스 발견: $PROCESS_8080"
        for PID in $PROCESS_8080; do
            log_info "포트 8080 사용 프로세스 종료 (PID: $PID)..."
            kill $PID 2>/dev/null || true
        done
    fi
    
    # 8081 포트 확인
    PROCESS_8081=$(lsof -ti:8081 || true)
    if [ ! -z "$PROCESS_8081" ]; then
        log_warning "포트 8081을 사용하는 프로세스 발견: $PROCESS_8081"
        for PID in $PROCESS_8081; do
            log_info "포트 8081 사용 프로세스 종료 (PID: $PID)..."
            kill $PID 2>/dev/null || true
        done
    fi
    
    log_success "포트 정리 완료"
}

# 함수: 임시 파일 정리
cleanup_temp_files() {
    log_info "임시 파일 정리 중..."
    
    # 임시 디렉토리 정리
    if [ -d "temp" ]; then
        log_info "temp 디렉토리 정리..."
        rm -rf temp/* 2>/dev/null || true
    fi
    
    # LAM 서비스 임시 파일 정리
    if [ -d "smarteye-lam-service/temp" ]; then
        log_info "LAM 서비스 임시 파일 정리..."
        rm -rf smarteye-lam-service/temp/* 2>/dev/null || true
    fi
    
    # 로그 파일 정리 (선택적)
    if [ -f "app.log" ]; then
        log_info "로그 파일 백업 및 초기화..."
        mv app.log "app.log.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
    fi
    
    log_success "임시 파일 정리 완료"
}

# 함수: 상태 확인
verify_shutdown() {
    log_info "종료 상태 확인 중..."
    
    # Java 프로세스 확인
    JAVA_PROCESSES=$(pgrep -f "smarteye.*\.jar" || true)
    if [ -z "$JAVA_PROCESSES" ]; then
        log_success "Java 애플리케이션 종료 확인"
    else
        log_warning "일부 Java 프로세스가 여전히 실행 중입니다: $JAVA_PROCESSES"
    fi
    
    # Docker 컨테이너 확인
    RUNNING_CONTAINERS=$(docker ps --filter "name=smarteye" --format "{{.Names}}" || true)
    if [ -z "$RUNNING_CONTAINERS" ]; then
        log_success "Docker 컨테이너 종료 확인"
    else
        log_warning "일부 컨테이너가 여전히 실행 중입니다: $RUNNING_CONTAINERS"
    fi
    
    # 포트 확인
    PORT_8080=$(lsof -ti:8080 || true)
    PORT_8081=$(lsof -ti:8081 || true)
    
    if [ -z "$PORT_8080" ] && [ -z "$PORT_8081" ]; then
        log_success "모든 포트가 해제되었습니다"
    else
        if [ ! -z "$PORT_8080" ]; then
            log_warning "포트 8080이 여전히 사용 중입니다"
        fi
        if [ ! -z "$PORT_8081" ]; then
            log_warning "포트 8081이 여전히 사용 중입니다"
        fi
    fi
}

# 메인 실행 함수
main() {
    log_info "3단계 SmartEye 시스템 전체 중지 시작..."
    
    stop_java_application
    stop_docker_services
    cleanup_containers
    cleanup_networks
    cleanup_ports
    cleanup_temp_files
    verify_shutdown
    
    log_success "=========================================="
    log_success "SmartEye v0.1 - 3단계 시스템 중지 완료!"
    log_success "=========================================="
    
    echo
    log_info "시스템이 완전히 중지되었습니다."
    log_info "재시작하려면: ./scripts/deploy-phase3-complete.sh"
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
