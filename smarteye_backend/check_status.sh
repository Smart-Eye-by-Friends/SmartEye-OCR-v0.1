#!/bin/bash

# SmartEye Backend 서비스 상태 확인 스크립트

set -e

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=== SmartEye Backend 서비스 상태 확인 ===${NC}"
echo ""

# Docker 서비스 상태
echo -e "${YELLOW}🐳 Docker 서비스 상태:${NC}"
docker compose -f docker-compose.dev.yml ps

echo ""

# API 헬스체크
echo -e "${YELLOW}🏥 API 헬스체크:${NC}"
health_response=$(curl -s http://localhost:8000/api/v1/health/ || echo "연결 실패")
if echo "$health_response" | grep -q "healthy"; then
    echo -e "  ${GREEN}✅ API 서버 정상${NC}"
else
    echo -e "  ${RED}❌ API 서버 문제: $health_response${NC}"
fi

# Redis 연결 확인
echo -e "${YELLOW}📦 Redis 연결:${NC}"
if docker compose -f docker-compose.dev.yml exec -T redis redis-cli ping | grep -q "PONG"; then
    echo -e "  ${GREEN}✅ Redis 정상${NC}"
else
    echo -e "  ${RED}❌ Redis 연결 실패${NC}"
fi

# 데이터베이스 연결 확인
echo -e "${YELLOW}🗄️  데이터베이스 연결:${NC}"
if docker compose -f docker-compose.dev.yml exec -T db pg_isready -U smarteye_user -d smarteye_db &>/dev/null; then
    echo -e "  ${GREEN}✅ PostgreSQL 정상${NC}"
else
    echo -e "  ${RED}❌ PostgreSQL 연결 실패${NC}"
fi

# Celery 작업자 확인
echo -e "${YELLOW}⚡ Celery 작업자:${NC}"
celery_status=$(docker compose -f docker-compose.dev.yml exec -T web celery -A smarteye inspect active 2>/dev/null || echo "실패")
if echo "$celery_status" | grep -q "OK"; then
    echo -e "  ${GREEN}✅ Celery 작업자 정상${NC}"
else
    echo -e "  ${RED}❌ Celery 작업자 문제${NC}"
fi

echo ""
echo -e "${BLUE}=== 접속 정보 ===${NC}"
echo "🌐 웹 서비스:"
echo "  • API 서버: http://localhost:8000"
echo "  • API 문서: http://localhost:8000/api/docs/"
echo "  • 관리자 페이지: http://localhost:8000/admin/"
echo ""
echo "📊 모니터링:"
echo "  • Flower (Celery): http://localhost:5555"
echo "  • 사용자명: admin / 비밀번호: smarteye_flower_password!@#$"
