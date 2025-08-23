#!/bin/bash

# SmartEye LAM 마이크로서비스 독립 배포 스크립트
# LAM 서비스만 단독으로 배포하고 관리

set -e

echo "=========================================="
echo "SmartEye LAM 마이크로서비스 배포"
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

# 함수: 전제조건 확인
check_prerequisites() {
    log_info "전제조건 확인 중..."
    
    # Docker 확인
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되지 않았습니다."
        log_error "Docker 설치: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    # LAM 서비스 디렉토리 확인
    if [ ! -d "smarteye-lam-service" ]; then
        log_error "LAM 서비스 디렉토리가 존재하지 않습니다."
        log_error "경로: ./smarteye-lam-service/"
        exit 1
    fi
    
    # Dockerfile 확인
    if [ ! -f "smarteye-lam-service/Dockerfile" ]; then
        log_error "LAM 서비스 Dockerfile이 존재하지 않습니다."
        exit 1
    fi
    
    log_success "전제조건 확인 완료"
}

# 함수: 기존 LAM 서비스 정리
cleanup_existing_service() {
    log_info "기존 LAM 서비스 정리 중..."
    
    # 기존 컨테이너 중지 및 제거
    if docker ps -a --format "table {{.Names}}" | grep -q "smarteye-lam-service"; then
        log_info "기존 LAM 컨테이너 중지 중..."
        docker stop smarteye-lam-service 2>/dev/null || true
        docker rm smarteye-lam-service 2>/dev/null || true
        log_success "기존 LAM 컨테이너 정리 완료"
    else
        log_info "기존 LAM 컨테이너가 없습니다."
    fi
}

# 함수: LAM 서비스 Docker 이미지 빌드
build_lam_image() {
    log_info "LAM 마이크로서비스 Docker 이미지 빌드 중..."
    
    cd smarteye-lam-service
    
    # 최적화된 Dockerfile이 있으면 사용
    DOCKERFILE="Dockerfile"
    if [ -f "Dockerfile.optimized" ]; then
        DOCKERFILE="Dockerfile.optimized"
        log_info "최적화된 Dockerfile 사용: $DOCKERFILE"
    fi
    
    # Docker 이미지 빌드
    docker build -f $DOCKERFILE -t smarteye-lam-service:latest .
    
    if [ $? -eq 0 ]; then
        log_success "LAM Docker 이미지 빌드 완료"
    else
        log_error "LAM Docker 이미지 빌드 실패"
        cd ..
        exit 1
    fi
    
    cd ..
}

# 함수: LAM 서비스 컨테이너 실행
start_lam_container() {
    log_info "LAM 마이크로서비스 컨테이너 시작 중..."
    
    # 환경변수 설정
    ENV_ARGS=""
    ENV_ARGS="$ENV_ARGS -e PYTHONPATH=/app"
    ENV_ARGS="$ENV_ARGS -e PYTHONUNBUFFERED=1"
    ENV_ARGS="$ENV_ARGS -e MODEL_CACHE_DIR=/app/cache"
    ENV_ARGS="$ENV_ARGS -e TORCH_CACHE_DIR=/app/cache/torch"
    ENV_ARGS="$ENV_ARGS -e HF_CACHE_DIR=/app/cache/huggingface"
    
    # 볼륨 마운트 설정
    VOLUME_ARGS=""
    VOLUME_ARGS="$VOLUME_ARGS -v smarteye-lam-cache:/app/cache"
    VOLUME_ARGS="$VOLUME_ARGS -v smarteye-lam-models:/app/models"
    
    # LAM 서비스 컨테이너 실행
    docker run -d --name smarteye-lam-service \
        -p 8081:8000 \
        $ENV_ARGS \
        $VOLUME_ARGS \
        --restart unless-stopped \
        smarteye-lam-service:latest
    
    if [ $? -eq 0 ]; then
        log_success "LAM 마이크로서비스 컨테이너 시작 완료"
    else
        log_error "LAM 마이크로서비스 컨테이너 시작 실패"
        exit 1
    fi
}

# 함수: LAM 서비스 헬스체크
health_check() {
    log_info "LAM 서비스 헬스체크 중..."
    
    # 서비스 시작 대기
    log_info "서비스 초기화 대기 중..."
    sleep 15
    
    # 헬스체크 시도
    MAX_RETRIES=10
    RETRY_COUNT=0
    
    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
        if curl -s -f http://localhost:8081/health > /dev/null; then
            log_success "LAM 마이크로서비스 정상 작동 확인"
            
            # 추가 정보 조회
            log_info "LAM 서비스 정보 조회 중..."
            MODELS_INFO=$(curl -s http://localhost:8081/models/info 2>/dev/null || echo "모델 정보 조회 실패")
            echo "모델 정보: $MODELS_INFO"
            
            return 0
        else
            RETRY_COUNT=$((RETRY_COUNT + 1))
            log_warning "LAM 서비스 헬스체크 시도 $RETRY_COUNT/$MAX_RETRIES..."
            sleep 5
        fi
    done
    
    log_error "LAM 서비스 헬스체크 실패"
    
    # 컨테이너 로그 출력
    log_info "컨테이너 로그 (마지막 20줄):"
    docker logs --tail 20 smarteye-lam-service
    
    return 1
}

# 함수: 배포 정보 출력
print_deployment_info() {
    log_success "=========================================="
    log_success "LAM 마이크로서비스 배포 완료!"
    log_success "=========================================="
    
    echo ""
    log_info "접속 정보:"
    echo "  • LAM 서비스 URL: http://localhost:8081"
    echo "  • 헬스체크: http://localhost:8081/health"
    echo "  • API 문서: http://localhost:8081/docs"
    echo "  • 모델 정보: http://localhost:8081/models/info"
    
    echo ""
    log_info "주요 API 엔드포인트:"
    echo "  • POST /analyze/layout - 레이아웃 분석"
    echo "  • GET /health - 서비스 상태 확인"
    echo "  • GET /models/info - 사용 가능한 모델 정보"
    
    echo ""
    log_info "관리 명령어:"
    echo "  • 상태 확인: docker ps | grep smarteye-lam"
    echo "  • 로그 확인: docker logs smarteye-lam-service"
    echo "  • 서비스 중지: docker stop smarteye-lam-service"
    echo "  • 서비스 재시작: docker restart smarteye-lam-service"
    echo "  • 컨테이너 제거: docker rm smarteye-lam-service"
    
    echo ""
    log_info "테스트 명령어:"
    echo '  curl -X GET http://localhost:8081/health'
    echo '  curl -X GET http://localhost:8081/models/info'
}

# 메인 실행 함수
main() {
    log_info "LAM 마이크로서비스 배포 시작..."
    
    check_prerequisites
    cleanup_existing_service
    build_lam_image
    start_lam_container
    
    if health_check; then
        print_deployment_info
        log_success "LAM 마이크로서비스 배포 성공!"
    else
        log_error "LAM 마이크로서비스 배포 실패"
        exit 1
    fi
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
