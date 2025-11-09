@echo off
REM SmartEyeSsen 프론트엔드 서버 시작 스크립트 (Windows)

echo 🚀 SmartEyeSsen 프론트엔드 서버를 시작합니다...

REM 현재 디렉토리 확인
if not exist "package.json" (
    echo ❌ package.json 파일을 찾을 수 없습니다. 올바른 디렉토리에서 실행하세요.
    pause
    exit /b 1
)

REM Node.js 의존성 설치 확인
if not exist "node_modules" (
    echo 📦 Node.js 의존성을 설치합니다...
    npm install
    echo ✅ 의존성 설치 완료
)

REM 서버 시작
echo 🌐 Vue.js 개발 서버를 시작합니다...
echo 📍 프론트엔드: http://localhost:5173
echo.
echo 백엔드 서버(http://localhost:8000)도 실행 중인지 확인하세요!
echo.
echo 종료하려면 Ctrl+C를 누르세요

npm run dev
