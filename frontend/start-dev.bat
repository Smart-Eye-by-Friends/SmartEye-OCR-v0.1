@echo off
echo SmartEye React Frontend 개발 서버 시작...
echo.

:: Node.js 버전 확인
node --version
if %errorlevel% neq 0 (
    echo 오류: Node.js가 설치되어 있지 않습니다.
    echo Node.js를 설치한 후 다시 시도해주세요.
    pause
    exit /b 1
)

:: npm 의존성 설치 (node_modules가 없는 경우)
if not exist "node_modules" (
    echo 의존성을 설치하는 중...
    npm install
    if %errorlevel% neq 0 (
        echo 오류: 의존성 설치에 실패했습니다.
        pause
        exit /b 1
    )
)

:: 개발 서버 시작
echo 개발 서버를 시작합니다...
echo 브라우저에서 http://localhost:3000을 열어주세요.
echo.
echo 서버를 중지하려면 Ctrl+C를 누르세요.
echo.

npm start
