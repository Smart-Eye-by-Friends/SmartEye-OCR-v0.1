#!/bin/bash

# SmartEye v0.4 전체 시스템 상태 확인 스크립트
# Frontend + Backend 통합 상태 체크

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🔍 SmartEye v0.4 전체 시스템 상태 확인"
echo "📅 $(date)"
echo ""

# Backend 서비스 상태 확인
echo "🖥️  Backend 서비스 상태:"
if [[ -d "Backend" ]]; then
    cd Backend
    if [[ -f "check_services.sh" ]]; then
        chmod +x check_services.sh
        ./check_services.sh
    else
        echo -e "${RED}❌ Backend check_services.sh를 찾을 수 없습니다.${NC}"
    fi
    cd ..
else
    echo -e "${RED}❌ Backend 디렉토리를 찾을 수 없습니다.${NC}"
fi

echo ""

# Frontend 서비스 상태 확인 (향후 추가)
if [[ -d "Frontend" ]]; then
    echo "🎨 Frontend 서비스 상태:"
    echo -e "${YELLOW}⚠️  Frontend 상태 확인 기능 구현 예정${NC}"
else
    echo "🎨 Frontend: 아직 설정되지 않음"
fi

echo ""
echo "📊 전체 시스템 요약:"
echo "  - Backend: $(docker ps --filter "name=smarteye-" --format "table {{.Names}}" | grep -c smarteye || echo "0")개 컨테이너 실행 중"
echo "  - Frontend: 구현 예정"
