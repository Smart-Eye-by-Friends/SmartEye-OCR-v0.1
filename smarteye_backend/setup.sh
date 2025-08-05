#!/bin/bash

# SmartEye Backend Setup Script
echo "🚀 SmartEye Backend 설치 스크립트 시작"

# 가상환경 생성
echo "📦 가상환경 생성 중..."
python3 -m venv venv
source venv/bin/activate

# 패키지 설치
echo "📚 패키지 설치 중..."
pip install --upgrade pip
pip install -r requirements.txt

# 환경 변수 파일 생성
echo "⚙️ 환경 변수 파일 생성 중..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✅ .env 파일이 생성되었습니다. 설정을 수정해주세요."
else
    echo "ℹ️ .env 파일이 이미 존재합니다."
fi

# 데이터베이스 마이그레이션
echo "🗄️ 데이터베이스 마이그레이션 중..."
python manage.py makemigrations
python manage.py migrate

# 관리자 계정 생성 (선택사항)
echo "👤 관리자 계정을 생성하시겠습니까? (y/n)"
read -r response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    python manage.py createsuperuser
fi

# 로그 디렉토리 권한 설정
echo "📋 로그 디렉토리 권한 설정 중..."
chmod 755 logs/

echo "🎉 SmartEye Backend 설치 완료!"
echo ""
echo "다음 명령어로 개발 서버를 시작할 수 있습니다:"
echo "  python manage.py runserver"
echo ""
echo "Celery 워커를 시작하려면:"
echo "  celery -A smarteye worker --loglevel=info"
echo ""
echo "API 문서는 다음 URL에서 확인할 수 있습니다:"
echo "  http://localhost:8000/api/docs/"
