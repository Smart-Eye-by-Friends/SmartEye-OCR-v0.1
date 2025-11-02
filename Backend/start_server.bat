@echo off
REM ============================================================================
REM SmartEyeSsen Backend - 서버 시작 스크립트 (Windows)
REM ============================================================================

echo ============================================================
echo SmartEyeSsen Backend Server
echo ============================================================
echo.

REM 가상환경 확인
if exist "venv\Scripts\activate.bat" (
    echo [1/3] Activating virtual environment...
    call venv\Scripts\activate.bat
) else (
    echo [WARNING] Virtual environment not found!
    echo Please create one with: python -m venv venv
    echo.
)

REM 환경 변수 파일 확인
if not exist ".env" (
    echo [WARNING] .env file not found!
    echo Please copy .env.example to .env and configure it.
    echo.
    pause
    exit /b 1
)

echo [2/3] Checking dependencies...
pip list | findstr "fastapi" >nul
if errorlevel 1 (
    echo [INFO] Installing dependencies...
    pip install -r requirements.txt
)

echo [3/3] Starting FastAPI server...
echo.
echo ============================================================
echo Server will start at: http://localhost:8000
echo API Documentation: http://localhost:8000/docs
echo ============================================================
echo.

REM FastAPI 서버 실행
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

pause
