#!/bin/bash
# ============================================================================
# SmartEyeSsen 배포 스크립트 (DigitalOcean Droplet)
# ============================================================================
# 사용법: bash scripts/deploy.sh
# 또는: chmod +x scripts/deploy.sh && ./scripts/deploy.sh

set -e  # 에러 발생 시 스크립트 중단

echo "======================================================================"
echo "🚀 SmartEyeSsen 프로덕션 배포 시작"
echo "======================================================================"

# ============================================================================
# 1. 환경 변수 확인
# ============================================================================
echo ""
echo "📋 Step 1/7: 환경 변수 확인"
echo "----------------------------------------------------------------------"

if [ ! -f "Backend/.env" ]; then
    echo "❌ Backend/.env 파일이 없습니다."
    echo "📝 다음 명령으로 .env 파일을 생성하세요:"
    echo "   cp Backend/.env.example Backend/.env"
    echo "   vim Backend/.env  # 실제 값으로 수정"
    exit 1
fi

echo "✅ Backend/.env 파일 존재 확인"

# OPENAI_API_KEY 확인 (선택적)
if grep -q "your_openai_api_key_here" Backend/.env; then
    echo "⚠️  경고: OpenAI API 키가 템플릿 값입니다."
    echo "   AI 설명 생성 기능을 사용하려면 실제 API 키를 설정하세요."
fi

# DB_HOST 확인
if grep -q "DB_HOST=mysql" Backend/.env; then
    echo "✅ DB_HOST가 Docker Compose 서비스 이름(mysql)으로 설정됨"
else
    echo "⚠️  경고: DB_HOST가 'mysql'이 아닙니다. Docker Compose 환경에서는 'mysql'로 설정해야 합니다."
fi

# ENVIRONMENT 확인
if grep -q "ENVIRONMENT=production" Backend/.env; then
    echo "✅ ENVIRONMENT=production 설정 확인"
else
    echo "⚠️  경고: ENVIRONMENT가 'production'이 아닙니다."
fi

# ============================================================================
# 2. Docker 설치 확인
# ============================================================================
echo ""
echo "🐳 Step 2/7: Docker 설치 확인"
echo "----------------------------------------------------------------------"

if ! command -v docker &> /dev/null; then
    echo "❌ Docker가 설치되어 있지 않습니다."
    echo "📝 다음 명령으로 Docker를 설치하세요:"
    echo "   curl -fsSL https://get.docker.com -o get-docker.sh"
    echo "   sudo sh get-docker.sh"
    exit 1
fi

echo "✅ Docker 버전: $(docker --version)"

if ! command -v docker compose &> /dev/null; then
    echo "❌ Docker Compose가 설치되어 있지 않습니다."
    exit 1
fi

echo "✅ Docker Compose 버전: $(docker compose version)"

# ============================================================================
# 3. 기존 컨테이너 정리 (선택적)
# ============================================================================
echo ""
echo "🧹 Step 3/7: 기존 컨테이너 정리 (있을 경우)"
echo "----------------------------------------------------------------------"

if [ "$(docker ps -aq -f name=smarteyessen)" ]; then
    echo "기존 컨테이너를 중지하고 제거합니다..."
    docker compose -f docker-compose.prod.yml down
    echo "✅ 기존 컨테이너 제거 완료"
else
    echo "✅ 기존 컨테이너 없음"
fi

# ============================================================================
# 4. Docker 이미지 빌드
# ============================================================================
echo ""
echo "🏗️  Step 4/7: Docker 이미지 빌드"
echo "----------------------------------------------------------------------"

echo "Backend 이미지 빌드 중... (약 3-5분 소요)"
docker compose -f docker-compose.prod.yml build backend

echo "Frontend 이미지 빌드 중... (약 2-3분 소요)"
docker compose -f docker-compose.prod.yml build frontend

echo "✅ 모든 이미지 빌드 완료"

# ============================================================================
# 5. 컨테이너 시작
# ============================================================================
echo ""
echo "🚀 Step 5/7: 컨테이너 시작"
echo "----------------------------------------------------------------------"

docker compose -f docker-compose.prod.yml up -d

echo "✅ 컨테이너 시작 완료"
echo ""
echo "실행 중인 컨테이너:"
docker ps --filter "name=smarteyessen"

# ============================================================================
# 6. 헬스체크 및 검증
# ============================================================================
echo ""
echo "🏥 Step 6/7: 헬스체크 (30초 대기)"
echo "----------------------------------------------------------------------"

sleep 30

# MySQL 연결 확인
echo "MySQL 연결 확인 중..."
MYSQL_PASSWORD=$(grep MYSQL_ROOT_PASSWORD Backend/.env | cut -d '=' -f2 || echo "change_this_password")

if docker exec smarteyessen_mysql mysqladmin ping -h localhost -u root -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
    echo "✅ MySQL 연결 성공"
else
    echo "❌ MySQL 연결 실패"
    echo "로그 확인:"
    docker compose -f docker-compose.prod.yml logs mysql --tail=20
    exit 1
fi

# Backend API 확인
echo "Backend API 연결 확인 중..."
if curl -f http://localhost:8000/health -o /dev/null -s 2>/dev/null || \
   docker exec smarteyessen_backend python -c "import requests; requests.get('http://localhost:8000/health', timeout=5)" 2>/dev/null; then
    echo "✅ Backend API 연결 성공"
else
    echo "⚠️  Backend API 연결 실패 (컨테이너 내부에서만 접근 가능할 수 있음)"
    echo "로그 확인:"
    docker compose -f docker-compose.prod.yml logs backend --tail=20
fi

# Frontend 확인
echo "Frontend 연결 확인 중..."
if curl -f http://localhost/ -o /dev/null -s 2>/dev/null; then
    echo "✅ Frontend 연결 성공"
else
    echo "❌ Frontend 연결 실패"
    echo "로그 확인:"
    docker compose -f docker-compose.prod.yml logs frontend --tail=20
    exit 1
fi

# ============================================================================
# 7. 배포 완료
# ============================================================================
echo ""
echo "======================================================================"
echo "✅ SmartEyeSsen 배포 완료!"
echo "======================================================================"
echo ""
echo "📍 접속 정보:"
echo "   - Frontend: http://localhost (또는 http://YOUR_DOMAIN)"
echo "   - Backend API: http://localhost/api"
echo "   - API Docs: http://localhost/docs"
echo ""
echo "📊 컨테이너 상태 확인:"
echo "   docker compose -f docker-compose.prod.yml ps"
echo ""
echo "📋 로그 확인:"
echo "   docker compose -f docker-compose.prod.yml logs -f"
echo ""
echo "🛑 중지:"
echo "   docker compose -f docker-compose.prod.yml down"
echo ""
echo "======================================================================"
echo "🔒 다음 단계: SSL 인증서 설정"
echo "======================================================================"
echo ""
echo "1. DNS가 전파될 때까지 대기 (10-30분)"
echo "   nslookup YOUR_DOMAIN"
echo ""
echo "2. Let's Encrypt 인증서 발급:"
echo "   docker compose -f docker-compose.prod.yml run --rm certbot certonly \\"
echo "     --webroot --webroot-path=/var/www/certbot \\"
echo "     --email YOUR_EMAIL \\"
echo "     --agree-tos --no-eff-email \\"
echo "     -d YOUR_DOMAIN -d www.YOUR_DOMAIN"
echo ""
echo "3. Frontend/default.conf에서 HTTPS 설정 주석 해제 후 재시작:"
echo "   docker compose -f docker-compose.prod.yml restart frontend"
echo ""
echo "======================================================================"
