#!/bin/bash

# SmartEye 개발 환경 시작 스크립트 (핫 리로드 지원)

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 헤더 출력
echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}🔧 SmartEye 개발 환경 시작${NC}"
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

cd "$BACKEND_DIR"

echo -e "${YELLOW}🧹 기존 컨테이너 정리 중...${NC}"
docker-compose down --remove-orphans || true

# 개발 환경 모드 선택
echo -e "${YELLOW}🔨 개발 환경을 선택하세요:${NC}"
echo "1) Frontend 개별 개발 (npm start) + Backend Docker"
echo "2) 전체 Docker (재빌드 포함)"
echo ""
read -p "선택 (1 또는 2): " -n 1 -r
echo ""

if [[ $REPLY == "1" ]]; then
    # Option 1: Frontend 개별 개발 모드
    echo -e "${GREEN}🔧 Frontend 개별 개발 모드 시작${NC}"
    echo -e "${YELLOW}📦 Backend 서비스만 Docker로 실행...${NC}"
    
    # Backend 의존 서비스들만 실행
    docker-compose up -d postgres lam-service smarteye-backend
    
    echo -e "${YELLOW}⏳ Backend 서비스 시작 대기...${NC}"
    sleep 20
    
    # Frontend npm install 확인
    FRONTEND_DIR="${CURRENT_DIR}/frontend"
    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        echo -e "${YELLOW}📦 Frontend 의존성 설치 중...${NC}"
        cd "$FRONTEND_DIR"
        npm install
        cd "$BACKEND_DIR"
    fi
    
    echo -e "${GREEN}✅ Backend 서비스 준비 완료!${NC}"
    echo ""
    echo -e "${BLUE}=================================${NC}"
    echo -e "${GREEN}🎯 다음 단계:${NC}"
    echo -e "${BLUE}=================================${NC}"
    echo ""
    echo -e "${YELLOW}새 터미널을 열고 다음 명령어를 실행하세요:${NC}"
    echo -e "${GREEN}cd ${CURRENT_DIR}/frontend${NC}"
    echo -e "${GREEN}npm start${NC}"
    echo ""
    echo -e "${YELLOW}Frontend 개발 서버: ${GREEN}http://localhost:3000${NC}"
    echo -e "${YELLOW}Backend API: ${GREEN}http://localhost:8080/api/health${NC}"
    echo -e "${YELLOW}Swagger UI: ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
    echo ""
    echo -e "${YELLOW}💡 코드 변경 시:${NC}"
    echo -e "   - Frontend: 자동 핫 리로드 (npm start)"
    echo -e "   - Backend: ./restart_backend.sh 실행"

elif [[ $REPLY == "2" ]]; then
    # Option 2: 전체 Docker 재빌드 모드
    echo -e "${GREEN}🔨 전체 Docker 재빌드 모드 시작${NC}"
    echo -e "${YELLOW}⚠️ 초기 빌드는 시간이 걸릴 수 있습니다 (약 5-10분)${NC}"
    echo ""
    
    # 전체 스택 빌드 및 실행
    docker-compose up -d --build --force-recreate
    
    echo -e "${YELLOW}⏳ 서비스 시작 대기...${NC}"
    sleep 30
    
    echo -e "${GREEN}✅ 전체 시스템 재빌드 완료!${NC}"
    echo ""
    echo -e "${BLUE}=================================${NC}"
    echo -e "${GREEN}🌐 서비스 접속 정보:${NC}"
    echo -e "${BLUE}=================================${NC}"
    echo ""
    echo -e "${GREEN}웹 서비스: ${YELLOW}http://localhost${NC}"
    echo -e "${GREEN}Backend API: ${YELLOW}http://localhost/api/health${NC}"
    echo -e "${GREEN}LAM Service: ${YELLOW}http://localhost:8001/health${NC}"
    echo ""
    echo -e "${YELLOW}💡 코드 변경 시:${NC}"
    echo -e "   - 이 스크립트를 다시 실행 (옵션 2 선택)"
    echo -e "   - 또는 ${GREEN}./rebuild_service.sh [service-name]${NC} 실행"

else
    echo -e "${RED}❌ 잘못된 선택입니다. 1 또는 2를 입력하세요.${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}🛑 시스템 중지: ${GREEN}./stop_full_system.sh${NC}"
echo ""