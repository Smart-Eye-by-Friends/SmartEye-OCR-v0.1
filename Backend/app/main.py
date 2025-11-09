"""
SmartEyeSsen Backend - FastAPI Main Application
================================================
FastAPI 메인 애플리케이션 및 라우터 설정

주요 기능:
- FastAPI 앱 초기화
- CORS 설정
- 라우터 등록
- 데이터베이스 초기화
- API 문서화
"""

import os
from pathlib import Path

from dotenv import load_dotenv

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text
from sqlalchemy.orm import Session

from .database import engine, get_db, init_db, test_connection
from . import models
from .routers import analysis, downloads, pages, projects
from .services.model_registry import model_registry

# 환경 변수 로드
load_dotenv()

# 환경 설정 (development | production)
ENVIRONMENT = os.getenv("ENVIRONMENT", "development")

UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "uploads")).resolve()
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# ============================================================================
# FastAPI 앱 초기화
# ============================================================================
app = FastAPI(
    title="SmartEyeSsen API",
    description="""
    ## SmartEyeSsen Backend API
    
    시각장애 학생을 위한 AI 기반 학습 자료 분석 시스템
    
    ### 주요 기능
    * 📄 **다중 페이지 문서 처리**: Worksheet 및 Document 유형 지원
    * 🤖 **AI 레이아웃 분석**: DocLayout-YOLO 기반 레이아웃 감지
    * 🔍 **OCR 텍스트 추출**: Tesseract OCR 기반 텍스트 인식
    * ✏️ **텍스트 편집 및 버전 관리**: TinyMCE 편집기 지원
    * 🖼️ **AI 설명 생성**: GPT-4-turbo 기반 figure/table/flowchart 설명
    * 📊 **문제 기반 정렬**: Worksheet 전용 문제 번호 기반 정렬
    * 📐 **좌표 기반 정렬**: Document 전용 좌표 기반 정렬
    * 📥 **통합 문서 다운로드**: DOCX 형식 지원
    
    ### 기술 스택
    * **Backend**: FastAPI + SQLAlchemy
    * **Database**: MySQL 8.0
    * **AI Models**: DocLayout-YOLO, Tesseract OCR, GPT-4-turbo
    * **Document**: python-docx
    """,
    version="1.0.1",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)

# ============================================================================
# CORS 설정 (환경별 분리)
# ============================================================================
if ENVIRONMENT == "production":
    # 프로덕션: 엄격한 CORS (실제로는 Nginx Reverse Proxy로 Same-Origin 처리)
    CORS_ORIGINS_ENV = os.getenv("CORS_ORIGINS", "")
    CORS_ORIGINS = CORS_ORIGINS_ENV.split(",") if CORS_ORIGINS_ENV else ["*"]
    CORS_METHODS = ["GET", "POST", "PUT", "DELETE", "PATCH"]
    CORS_HEADERS = ["Content-Type", "Authorization", "X-Requested-With"]
else:
    # 개발: 유연한 CORS
    CORS_ORIGINS = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000,http://localhost:8080,http://127.0.0.1:5173").split(",")
    CORS_METHODS = ["*"]
    CORS_HEADERS = ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,  # 허용할 출처
    allow_credentials=True,
    allow_methods=CORS_METHODS,  # 환경별 메소드 제한
    allow_headers=CORS_HEADERS,  # 환경별 헤더 제한
)

# 업로드 파일 정적 서빙 (프론트엔드 썸네일 표시 등)
app.mount(
    "/uploads",
    StaticFiles(directory=str(UPLOAD_DIR)),
    name="uploads",
)

# ============================================================================
# 시작 이벤트
# ============================================================================
@app.on_event("startup")
async def startup_event():
    """
    애플리케이션 시작 시 실행
    - 데이터베이스 연결 테스트
    - 테이블 생성 (개발 환경)
    """
    print("=" * 60)
    print("🚀 SmartEyeSsen Backend Starting...")
    print("=" * 60)
    
    # 데이터베이스 연결 테스트
    if test_connection():
        print("✅ Database connection successful")
    else:
        print("❌ Database connection failed")
        print("⚠️ Please check your database configuration")
    
    # 테이블 생성 (개발 환경에서만)
    if os.getenv("ENVIRONMENT", "development") == "development":
        try:
            init_db()
            print("✅ Database tables initialized")
        except Exception as e:
            print(f"⚠️ Table initialization warning: {e}")

    preload_env = os.getenv("MODEL_PRELOAD", "SmartEyeSsen")
    preload_targets = [
        name.strip()
        for name in preload_env.split(",")
        if name.strip()
    ]
    if preload_targets:
        try:
            model_registry.preload(preload_targets)
            print(f"🧠 Preloaded models: {', '.join(preload_targets)}")
        except Exception as e:
            print(f"⚠️ Model preload failed: {e}")
    
    print("=" * 60)
    print("✅ SmartEyeSsen Backend Ready!")
    print(f"📖 API Docs: http://localhost:{os.getenv('API_PORT', 8000)}/docs")
    print("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    """애플리케이션 종료 시 실행"""
    print("\n" + "=" * 60)
    print("👋 SmartEyeSsen Backend Shutting down...")
    print("=" * 60)


# ============================================================================
# 루트 엔드포인트
# ============================================================================
@app.get("/", tags=["Root"])
async def root():
    """
    루트 엔드포인트
    
    서버 상태 및 기본 정보 반환
    """
    return {
        "message": "Welcome to SmartEyeSsen API",
        "version": "1.0.1",
        "status": "running",
        "docs": "/docs",
        "redoc": "/redoc"
    }


@app.get("/health", tags=["Root"])
async def health_check(db: Session = Depends(get_db)):
    """
    헬스 체크 엔드포인트
    
    서버 및 데이터베이스 상태 확인
    """
    try:
        # 간단한 쿼리로 DB 연결 확인
        db.execute(text("SELECT 1"))
        db_status = "connected"
    except Exception as e:
        db_status = f"error: {str(e)}"
    
    return {
        "status": "healthy",
        "database": db_status,
        "api_version": "1.0.0"
    }


# ============================================================================
# 예외 핸들러
# ============================================================================
@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    """HTTP 예외 핸들러"""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": exc.detail,
            "status_code": exc.status_code
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    """일반 예외 핸들러"""
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal Server Error",
            "detail": str(exc),
            "status_code": 500
        }
    )


# =========================================================================
# 라우터 등록
# =========================================================================
app.include_router(projects.router)
app.include_router(pages.router)
app.include_router(analysis.router)
app.include_router(downloads.router)


# ============================================================================
# 개발 서버 실행 (직접 실행 시)
# ============================================================================
if __name__ == "__main__":
    import uvicorn
    
    HOST = os.getenv("API_HOST", "0.0.0.0")
    PORT = int(os.getenv("API_PORT", 8000))
    RELOAD = os.getenv("API_RELOAD", "True").lower() == "true"
    
    uvicorn.run(
        "main:app",
        host=HOST,
        port=PORT,
        reload=RELOAD,
        log_level="info"
    )
