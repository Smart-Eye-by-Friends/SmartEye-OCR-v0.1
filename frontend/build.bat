@echo off
echo SmartEye React Frontend 프로덕션 빌드...
echo.

:: Node.js 버전 확인
node --version
if %errorlevel% neq 0 (
    echo 오류: Node.js가 설치되어 있지 않습니다.
    pause
    exit /b 1
)

:: 의존성 설치
echo 의존성을 설치하는 중...
npm install
if %errorlevel% neq 0 (
    echo 오류: 의존성 설치에 실패했습니다.
    pause
    exit /b 1
)

:: 기존 빌드 삭제
if exist "build" (
    echo 기존 빌드 폴더를 삭제합니다...
    rmdir /s /q build
)

:: 프로덕션 빌드
echo 프로덕션 빌드를 시작합니다...
npm run build
if %errorlevel% neq 0 (
    echo 오류: 빌드에 실패했습니다.
    pause
    exit /b 1
)

echo.
echo 빌드가 완료되었습니다!
echo 빌드된 파일은 'build' 폴더에 있습니다.
echo.
pause
