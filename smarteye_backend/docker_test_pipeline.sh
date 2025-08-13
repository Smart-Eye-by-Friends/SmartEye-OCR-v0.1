#!/bin/bash

# Docker 환경에서 파이프라인 테스트를 실행하는 스크립트
# Docker 소켓을 마운트하여 컨테이너 내부에서 Docker 명령어 사용 가능

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo "=== Docker 환경 파이프라인 테스트 ==="

# Docker 소켓을 마운트하여 테스트 컨테이너 실행
log_info "Docker 소켓을 마운트하여 테스트 컨테이너 실행..."

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd):/workspace" \
  -w /workspace \
  --network smarteye_backend_default \
  docker:dind sh -c "
    apk add --no-cache curl bash
    chmod +x ./test_pipeline.sh
    # 컨테이너 내부에서는 서비스 이름으로 접근
    export API_BASE_URL='http://smarteye_backend-web-1:8000'
    ./test_pipeline.sh --quick
  "
