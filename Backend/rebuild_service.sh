#!/bin/bash

# 개별 서비스 재빌드 스크립트

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SERVICE_NAME=$1

if [ -z "$SERVICE_NAME" ]; then
    echo -e "${BLUE}=================================${NC}"
    echo -e "${BLUE}🔧 서비스 재빌드 스크립트${NC}"
    echo -e "${BLUE}=================================${NC}"
    echo ""
    echo -e "${YELLOW}사용법: ./rebuild_service.sh [서비스명]${NC}"
    echo ""
    echo -e "${GREEN}사용 가능한 서비스:${NC}"
    echo "  • frontend         - React 앱 재빌드"
    echo "  • smarteye-backend - Java Spring Boot 재빌드"
    echo "  • lam-service      - Python FastAPI 재빌드"
    echo "  • nginx            - Nginx 재시작"
    echo "  • all              - 모든 서비스 재빌드"
    echo ""
    exit 1
fi

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}🔨 서비스 재빌드: ${SERVICE_NAME}${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""

case $SERVICE_NAME in
    "frontend")
        echo -e "${YELLOW}📦 Frontend 재빌드 중...${NC}"
        docker-compose build --no-cache frontend
        docker-compose up -d frontend
        docker-compose restart nginx
        echo -e "${GREEN}✅ Frontend 재빌드 완료!${NC}"
        ;;
    
    "smarteye-backend")
        echo -e "${YELLOW}⚙️ Backend 재빌드 중...${NC}"
        docker-compose build --no-cache smarteye-backend
        docker-compose up -d smarteye-backend
        echo -e "${GREEN}✅ Backend 재빌드 완료!${NC}"
        ;;
    
    "lam-service")
        echo -e "${YELLOW}🔬 LAM Service 재빌드 중...${NC}"
        docker-compose build --no-cache lam-service
        docker-compose up -d lam-service
        echo -e "${GREEN}✅ LAM Service 재빌드 완료!${NC}"
        ;;
    
    "nginx")
        echo -e "${YELLOW}🌍 Nginx 재시작 중...${NC}"
        docker-compose restart nginx
        echo -e "${GREEN}✅ Nginx 재시작 완료!${NC}"
        ;;
    
    "all")
        echo -e "${YELLOW}🔨 모든 서비스 재빌드 중...${NC}"
        docker-compose down
        docker-compose up -d --build --force-recreate
        echo -e "${GREEN}✅ 모든 서비스 재빌드 완료!${NC}"
        ;;
    
    *)
        echo -e "${RED}❌ 알 수 없는 서비스: ${SERVICE_NAME}${NC}"
        echo -e "${YELLOW}사용 가능한 서비스: frontend, smarteye-backend, lam-service, nginx, all${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${YELLOW}📊 현재 서비스 상태:${NC}"
docker-compose ps | grep -E "(frontend|smarteye-backend|nginx)"

echo ""
echo -e "${GREEN}🌐 웹 서비스: ${YELLOW}http://localhost${NC}"
echo -e "${GREEN}📊 Backend API: ${YELLOW}http://localhost/api/health${NC}"
echo ""