#!/bin/bash

# Backend 빠른 재시작 스크립트 (개발용)

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔄 Backend 재시작 중...${NC}"

cd Backend

# Backend만 재시작 (빌드 없이)
docker-compose restart smarteye-backend

echo -e "${YELLOW}⏳ Backend 시작 대기...${NC}"
sleep 15

# 헬스체크
echo -e "${YELLOW}🏥 Backend 헬스체크...${NC}"
if curl -f http://localhost:8080/api/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Backend 재시작 완료!${NC}"
    echo -e "${GREEN}📊 Backend API: ${YELLOW}http://localhost:8080/api/health${NC}"
else
    echo -e "${RED}❌ Backend 헬스체크 실패${NC}"
    echo -e "${YELLOW}로그를 확인하세요: ${GREEN}docker-compose logs smarteye-backend${NC}"
fi

echo ""