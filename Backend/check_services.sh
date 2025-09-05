#!/bin/bash

# SmartEye v0.4 서비스 상태 체크 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "🔍 SmartEye v0.4 서비스 상태 체크"
echo "📅 $(date)"
echo ""

# Docker 서비스 상태 확인
echo "🐳 Docker 컨테이너 상태:"
docker-compose ps

echo ""
echo "🏥 헬스체크:"

# PostgreSQL 체크
echo -n "PostgreSQL (5433): "
if docker exec smarteye-postgres pg_isready -U smarteye > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 정상${NC}"
else
    echo -e "${RED}❌ 실패${NC}"
    echo "  해결방법: docker-compose restart postgres"
fi

# LAM Service 체크
echo -n "LAM Service (8001): "
if curl -s http://localhost:8001/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 정상${NC}"
    # 모델 캐시 상태도 확인
    models=$(curl -s http://localhost:8001/health | jq -r '.cached_models | length' 2>/dev/null || echo "0")
    echo "  캐시된 모델: ${models}개"
else
    echo -e "${RED}❌ 실패${NC}"
    echo "  해결방법: docker-compose restart lam-service"
fi

# Backend 체크
echo -n "Backend (8080): "
if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 정상${NC}"
    # 메모리 사용량도 확인
    memory_info=$(curl -s http://localhost:8080/api/health | jq -r '.system.freeMemory, .system.maxMemory' 2>/dev/null)
    if [[ -n "$memory_info" ]]; then
        echo "  메모리 상태: 확인 완료"
    fi
else
    echo -e "${RED}❌ 실패${NC}"
    echo "  해결방법: docker-compose restart smarteye-backend"
fi

echo ""
echo "📊 리소스 사용량:"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" $(docker-compose ps -q) 2>/dev/null || echo "리소스 정보를 가져올 수 없습니다."

echo ""
echo "🔧 문제 해결 명령어:"
echo "  전체 재시작: docker-compose restart"
echo "  로그 확인: docker-compose logs [서비스명]"
echo "  서비스 중지: docker-compose down"
echo "  완전 재빌드: docker-compose down && docker-compose build --no-cache && docker-compose up -d"
