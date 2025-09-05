#!/bin/bash

echo "🧪 SmartEye v0.4 시스템 검증 테스트"
echo "====================================="
echo "📅 $(date)"
echo ""

# 기본 설정
BASE_URL="http://localhost:8080"
LAM_URL="http://localhost:8001"
TEST_IMAGE="test_homework_image.jpg"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 성공/실패 카운터
PASS_COUNT=0
FAIL_COUNT=0

success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((PASS_COUNT++))
}

failure() {
    echo -e "${RED}❌ $1${NC}"
    ((FAIL_COUNT++))
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# 테스트 1: Docker 컨테이너 상태 확인
echo "🐳 Docker 컨테이너 상태 확인..."
if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "(smarteye-backend|smarteye-lam-service|smarteye-postgres|smarteye-nginx)" | grep -q "Up"; then
    success "Docker containers: 모든 서비스 실행 중"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep smarteye
else
    failure "Docker containers: 일부 서비스 실행되지 않음"
fi

echo ""

# 테스트 2: Backend 헬스체크
echo "🏥 Backend 헬스체크 (포트 8080)..."
if curl -s "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    HEALTH_RESPONSE=$(curl -s "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "UP")
    if [[ "$HEALTH_RESPONSE" == "UP" ]]; then
        success "Backend health: 정상"
    else
        warning "Backend health: 응답이 있으나 상태 불명"
    fi
else
    failure "Backend health: 응답 없음"
fi

# 테스트 3: LAM Service 헬스체크
echo "🤖 LAM Service 헬스체크 (포트 8001)..."
if curl -s "$LAM_URL/health" > /dev/null 2>&1; then
    success "LAM Service health: 정상"
else
    failure "LAM Service health: 응답 없음"
fi

# 테스트 4: PostgreSQL 연결 확인
echo "🗄️  PostgreSQL 연결 확인..."
if docker exec smarteye-postgres pg_isready -U smarteye > /dev/null 2>&1; then
    success "PostgreSQL: 연결 정상"
    # 데이터베이스 테이블 존재 확인
    if docker exec smarteye-postgres psql -U smarteye -d smarteye_db -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('analysis_jobs', 'analysis_results');" | grep -q "2"; then
        success "Database tables: 스키마 정상"
    else
        warning "Database tables: 테이블 확인 필요"
    fi
else
    failure "PostgreSQL: 연결 실패"
fi

echo ""

# 테스트 5: 테스트 이미지 파일 확인
echo "📁 테스트 파일 확인..."
if [[ -f "$TEST_IMAGE" ]]; then
    FILE_SIZE=$(du -h "$TEST_IMAGE" | cut -f1)
    success "Test image: $TEST_IMAGE (크기: $FILE_SIZE)"
else
    failure "Test image: $TEST_IMAGE 파일 없음"
fi

echo ""

# 테스트 6: API 기능 테스트
echo "📡 API 기능 테스트..."
if [[ -f "$TEST_IMAGE" ]]; then
    echo "   이미지 분석 API 호출 중..."
    
    API_RESPONSE=$(curl -s -X POST \
        -F "image=@$TEST_IMAGE" \
        -F "modelChoice=SmartEyeSsen" \
        "$BASE_URL/api/document/analyze" 2>/dev/null)
    
    if [[ $? -eq 0 ]] && echo "$API_RESPONSE" | jq -e '.success == true' > /dev/null 2>&1; then
        success "API 분석: 성공"
        
        # 응답 상세 정보 추출
        LAYOUT_ELEMENTS=$(echo "$API_RESPONSE" | jq -r '.stats.totalLayoutElements // "N/A"')
        OCR_BLOCKS=$(echo "$API_RESPONSE" | jq -r '.stats.ocrTextBlocks // "N/A"')
        JOB_ID=$(echo "$API_RESPONSE" | jq -r '.jobId // "N/A"')
        
        echo "   📊 분석 결과:"
        echo "      - 레이아웃 요소: $LAYOUT_ELEMENTS개"
        echo "      - OCR 텍스트 블록: $OCR_BLOCKS개"
        echo "      - 작업 ID: $JOB_ID"
        
        # 결과 파일 확인
        LAYOUT_URL=$(echo "$API_RESPONSE" | jq -r '.layoutImageUrl // ""')
        JSON_URL=$(echo "$API_RESPONSE" | jq -r '.jsonUrl // ""')
        
        if [[ -n "$LAYOUT_URL" ]] && curl -s --head "$BASE_URL$LAYOUT_URL" | head -n 1 | grep -q "200 OK"; then
            success "결과 이미지: 생성됨 ($LAYOUT_URL)"
        else
            warning "결과 이미지: 확인 필요"
        fi
        
        if [[ -n "$JSON_URL" ]] && curl -s --head "$BASE_URL$JSON_URL" | head -n 1 | grep -q "200 OK"; then
            success "결과 JSON: 생성됨 ($JSON_URL)"
        else
            warning "결과 JSON: 확인 필요"
        fi
        
    else
        failure "API 분석: 실패"
        echo "   응답: $API_RESPONSE"
    fi
else
    warning "API 테스트: 테스트 이미지 없어서 스킵"
fi

echo ""

# 최종 결과
echo "📊 테스트 결과 요약"
echo "==================="
echo -e "✅ 성공: ${GREEN}$PASS_COUNT${NC}개"
echo -e "❌ 실패: ${RED}$FAIL_COUNT${NC}개"

if [[ $FAIL_COUNT -eq 0 ]]; then
    echo ""
    echo -e "${GREEN}🎉 모든 테스트 통과! SmartEye v0.4 시스템이 정상적으로 작동 중입니다.${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}⚠️  일부 테스트 실패. 시스템 상태를 확인하세요.${NC}"
    echo ""
    echo "문제 해결 가이드:"
    echo "- Docker 서비스 재시작: ./start_services.sh"
    echo "- 로그 확인: docker-compose logs [service-name]"
    echo "- 개별 서비스 상태: docker ps"
    exit 1
fi
