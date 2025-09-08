#!/bin/bash

# SmartEye 전체 시스템 시작 스크립트 (Frontend + Backend Docker 통합)

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 헤더 출력
echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}🚀 SmartEye 전체 시스템 시작${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""

# 현재 디렉토리 확인
CURRENT_DIR=$(pwd)
echo -e "${YELLOW}📁 현재 디렉토리: ${CURRENT_DIR}${NC}"

# Backend 디렉토리로 이동
BACKEND_DIR="${CURRENT_DIR}/Backend"
if [ ! -d "$BACKEND_DIR" ]; then
    echo -e "${RED}❌ Backend 디렉토리를 찾을 수 없습니다: ${BACKEND_DIR}${NC}"
    exit 1
fi

echo -e "${YELLOW}📂 Backend 디렉토리로 이동 중...${NC}"
cd "$BACKEND_DIR"

# Docker와 Docker Compose 설치 확인
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker가 설치되어 있지 않습니다.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose가 설치되어 있지 않습니다.${NC}"
    exit 1
fi

# 기존 컨테이너 정리 (선택사항)
echo -e "${YELLOW}🧹 기존 컨테이너 정리 중...${NC}"
docker-compose down --remove-orphans || true

# Frontend 빌드 가능 여부 확인
FRONTEND_DIR="${CURRENT_DIR}/frontend"
if [ ! -d "$FRONTEND_DIR" ]; then
    echo -e "${RED}❌ Frontend 디렉토리를 찾을 수 없습니다: ${FRONTEND_DIR}${NC}"
    exit 1
fi

if [ ! -f "$FRONTEND_DIR/package.json" ]; then
    echo -e "${RED}❌ Frontend package.json을 찾을 수 없습니다.${NC}"
    exit 1
fi

# Docker 이미지 및 컨테이너 빌드/시작
echo -e "${GREEN}🔨 Docker 이미지 빌드 및 컨테이너 시작 중...${NC}"
echo -e "${YELLOW}⏰ 초기 빌드는 시간이 걸릴 수 있습니다 (약 5-10분)${NC}"
echo ""

# 전체 스택 빌드 및 실행
docker-compose up -d --build

# 서비스 시작 대기 및 헬스체크
echo -e "${YELLOW}⏳ 서비스 시작 대기 중...${NC}"
sleep 30

# 서비스 상태 확인
echo -e "${GREEN}📊 서비스 상태 확인 중...${NC}"
echo ""

# PostgreSQL 상태 확인
echo -n "🐘 PostgreSQL: "
if docker-compose ps postgres | grep -q "Up"; then
    echo -e "${GREEN}✅ 실행 중${NC}"
else
    echo -e "${RED}❌ 중지됨${NC}"
fi

# LAM Service 상태 확인
echo -n "🔬 LAM Service: "
if docker-compose ps lam-service | grep -q "Up"; then
    echo -e "${GREEN}✅ 실행 중${NC}"
else
    echo -e "${RED}❌ 중지됨${NC}"
fi

# Backend 상태 확인
echo -n "⚙️  Backend: "
if docker-compose ps smarteye-backend | grep -q "Up"; then
    echo -e "${GREEN}✅ 실행 중${NC}"
else
    echo -e "${RED}❌ 중지됨${NC}"
fi

# Frontend 상태 확인
echo -n "🌐 Frontend: "
if docker-compose ps frontend | grep -q "Up"; then
    echo -e "${GREEN}✅ 실행 중${NC}"
else
    echo -e "${RED}❌ 중지됨${NC}"
fi

# Nginx 상태 확인
echo -n "🌍 Nginx: "
if docker-compose ps nginx | grep -q "Up"; then
    echo -e "${GREEN}✅ 실행 중${NC}"
else
    echo -e "${RED}❌ 중지됨${NC}"
fi

echo ""
echo -e "${BLUE}=================================${NC}"
echo -e "${GREEN}🎉 시스템 시작 완료!${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""
echo -e "${GREEN}🌐 웹 서비스 접속: ${YELLOW}http://localhost${NC}"
echo -e "${GREEN}📊 Backend API: ${YELLOW}http://localhost/api/health${NC}"
echo -e "${GREEN}🔬 LAM Service: ${YELLOW}http://localhost:8001/health${NC}"
echo -e "${GREEN}🐘 PostgreSQL: ${YELLOW}localhost:5433${NC}"
echo ""
echo -e "${YELLOW}💡 팁:${NC}"
echo -e "   - 시스템 중지: ${BLUE}docker-compose down${NC}"
echo -e "   - 로그 확인: ${BLUE}docker-compose logs -f${NC}"
echo -e "   - 상태 확인: ${BLUE}docker-compose ps${NC}"
echo ""