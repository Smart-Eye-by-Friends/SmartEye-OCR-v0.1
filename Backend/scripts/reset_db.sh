#!/bin/bash

# ============================================================================
# SmartEye OCR Database Reset Script (v2.1)
# ============================================================================
# 기능:
# - Docker MySQL 컨테이너에서 DB 완전 초기화
# - init_db_complete.sql 자동 실행 (테이블 + 초기 데이터)
# - combined_text: MEDIUMTEXT (16MB 지원)
# ============================================================================

set -e  # 에러 시 즉시 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}SmartEye OCR Database Reset${NC}"
echo -e "${YELLOW}======================================${NC}"

# 1. Docker 컨테이너 확인
CONTAINER_NAME="smart_mysql"
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo -e "${RED}Error: Docker container '${CONTAINER_NAME}' is not running${NC}"
    echo -e "${YELLOW}Please start with: cd Backend && docker-compose up -d${NC}"
    exit 1
fi

# 2. 스크립트 경로 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/init_db_complete.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}Error: $SQL_FILE not found${NC}"
    exit 1
fi

# 3. 사용자 확인
echo -e "${YELLOW}⚠️  Warning: This will DELETE all existing data!${NC}"
echo -e "${YELLOW}Database: smarteyessen_db${NC}"
read -p "Continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo -e "${GREEN}Cancelled.${NC}"
    exit 0
fi

# 4. DB 초기화 실행
echo -e "${GREEN}Resetting database...${NC}"
docker exec -i ${CONTAINER_NAME} mysql -u root -p1q2w3e4r < "$SQL_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}✅ Database reset completed!${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo ""
    echo "📋 Created tables:"
    echo "  - users, document_types, projects, pages"
    echo "  - layout_elements, text_contents, ai_descriptions"
    echo "  - question_groups, question_elements"
    echo "  - text_versions, formatting_rules"
    echo "  - combined_results (LONGTEXT, up to 4GB)"
    echo ""
    echo "� Initial data:"
    echo "  - 2 document types (worksheet, document)"
    echo "  - 25+ formatting rules (auto-generated)"
    echo ""
    echo "🚀 Next steps:"
    echo "  1. Start backend: uvicorn Backend.app.main:app --reload"
    echo "  2. Check health: curl http://localhost:8000/health"
else
    echo -e "${RED}❌ Database reset failed!${NC}"
    exit 1
fi

echo "✅ Table initialization complete."

echo "🎉 Database reset finished. You can now rerun backend services or seed data as needed."
