#!/bin/bash

# ==============================================================================
# SmartEye v0.4 - 모니터링 시작 스크립트 (Prometheus + Grafana)
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

# 현재 디렉토리를 프로젝트 루트로 설정
cd "$(dirname "$0")/.."

log_info "SmartEye v0.4 모니터링 스택을 시작합니다..."

# Docker 네트워크 확인 및 생성
if ! docker network ls | grep -q "smarteye-network"; then
    log_info "Docker 네트워크 'smarteye-network'를 생성합니다..."
    docker network create smarteye-network
    log_success "Docker 네트워크가 생성되었습니다."
else
    log_info "Docker 네트워크 'smarteye-network'가 이미 존재합니다."
fi

# 모니터링 스택 시작
log_info "Prometheus + Grafana 스택을 시작합니다..."
docker-compose -f monitoring/docker-compose.monitoring.yml up -d

# 서비스 상태 확인
log_info "서비스 상태를 확인합니다..."
sleep 10

# Prometheus 상태 확인
if curl -f http://localhost:9090/-/healthy &>/dev/null; then
    log_success "Prometheus가 정상적으로 실행 중입니다. (http://localhost:9090)"
else
    log_warning "Prometheus 상태 확인에 실패했습니다. 몇 초 후 다시 확인해보세요."
fi

# Grafana 상태 확인
if curl -f http://localhost:3001/api/health &>/dev/null; then
    log_success "Grafana가 정상적으로 실행 중입니다. (http://localhost:3001)"
    log_info "Grafana 로그인 정보:"
    echo "  - URL: http://localhost:3001"
    echo "  - Username: admin"
    echo "  - Password: smarteye2024"
else
    log_warning "Grafana 상태 확인에 실패했습니다. 몇 초 후 다시 확인해보세요."
fi

# cAdvisor 상태 확인
if curl -f http://localhost:8080/healthz &>/dev/null; then
    log_success "cAdvisor가 정상적으로 실행 중입니다. (http://localhost:8080)"
else
    log_warning "cAdvisor 상태 확인에 실패했습니다."
fi

log_success "모니터링 스택 시작이 완료되었습니다!"
log_info "모니터링 대시보드:"
echo "  • Prometheus: http://localhost:9090"
echo "  • Grafana: http://localhost:3001 (admin/smarteye2024)"
echo "  • cAdvisor: http://localhost:8080"
echo ""
log_info "SmartEye 메트릭 확인:"
echo "  • Backend Metrics: http://localhost:8080/actuator/prometheus"
echo "  • LAM Service Metrics: http://localhost:8001/metrics"
echo ""
log_warning "중지하려면: docker-compose -f monitoring/docker-compose.monitoring.yml down"