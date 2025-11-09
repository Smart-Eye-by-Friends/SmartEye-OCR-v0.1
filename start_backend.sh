#!/bin/bash

# SmartEyeSsen 백엔드 서버 시작 스크립트

echo "🚀 SmartEyeSsen 백엔드 서버를 시작합니다..."

# 현재 디렉토리 확인
if [ ! -f "api_server.py" ]; then
    echo "❌ api_server.py 파일을 찾을 수 없습니다. 올바른 디렉토리에서 실행하세요."
    exit 1
fi

# DocLayout-YOLO 설치 확인
if [ ! -d "DocLayout-YOLO" ]; then
    echo "📥 DocLayout-YOLO를 설치합니다..."
    git clone https://github.com/opendatalab/DocLayout-YOLO.git
    cd DocLayout-YOLO
    pip install -e .
    cd ..
    echo "✅ DocLayout-YOLO 설치 완료"
fi

# Python 의존성 확인 및 설치
echo "📦 Python 의존성을 확인합니다..."
pip install -r requirements.txt

# 서버 시작
echo "🌐 FastAPI 서버를 시작합니다..."
echo "📍 백엔드 API: http://localhost:8000"
echo "📚 API 문서: http://localhost:8000/docs"
echo ""
echo "종료하려면 Ctrl+C를 누르세요"

python api_server.py
