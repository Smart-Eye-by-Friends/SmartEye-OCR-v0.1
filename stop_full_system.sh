#!/bin/bash

# SmartEye 전체 시스템 중지 스크립트

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 헤더 출력
echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}🛑 SmartEye 전체 시스템 중지${NC}"
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

# Docker Compose로 모든 서비스 중지
echo -e "${YELLOW}⏹️  모든 Docker 서비스 중지 중...${NC}"
docker-compose down --remove-orphans

# 볼륨 삭제 옵션 (선택사항)
read -p "데이터 볼륨도 삭제하시겠습니까? (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🗑️  볼륨 삭제 중...${NC}"
    docker-compose down -v
    echo -e "${GREEN}✅ 모든 데이터 볼륨이 삭제되었습니다.${NC}"
fi

# 미사용 Docker 이미지 정리 옵션
read -p "미사용 Docker 이미지를 정리하시겠습니까? (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🧹 미사용 이미지 정리 중...${NC}"
    docker system prune -f
    echo -e "${GREEN}✅ 미사용 이미지가 정리되었습니다.${NC}"
fi

echo ""
echo -e "${BLUE}=================================${NC}"
echo -e "${GREEN}🏁 시스템 중지 완료!${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""
echo -e "${YELLOW}💡 다시 시작하려면:${NC}"
echo -e "   ${BLUE}./start_full_system.sh${NC}"
echo ""