#!/bin/bash

# LAM 마이크로서비스 실행 스크립트

echo "🤖 SmartEye LAM 마이크로서비스 시작..."

# 현재 디렉토리를 lam-microservice로 변경
cd lam-microservice

# Python 가상환경 확인 및 생성
if [ ! -d "venv" ]; then
    echo "📦 Python 가상환경 생성 중..."
    python3 -m venv venv
fi

# 가상환경 활성화
echo "🔧 가상환경 활성화..."
source venv/bin/activate

# 의존성 설치
echo "📚 의존성 설치 중..."
pip install -r requirements.txt

# DocLayout-YOLO 설치 (수동 설치 필요)
echo "⚠️  DocLayout-YOLO를 수동으로 설치해주세요:"
echo "   pip install git+https://github.com/opendatalab/DocLayout-YOLO.git"
echo ""

# LAM 서비스 실행
echo "🚀 LAM 마이크로서비스 시작..."
python lam_service.py
