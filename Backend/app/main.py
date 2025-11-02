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

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
import os
from dotenv import load_dotenv

from .database import engine, get_db, init_db, test_connection
from . import models

# 환경 변수 로드
load_dotenv()

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
    * 🔍 **OCR 텍스트 추출**: PaddleOCR 기반 텍스트 인식
    * ✏️ **텍스트 편집 및 버전 관리**: TinyMCE 편집기 지원
    * 🖼️ **AI 설명 생성**: GPT-4o-mini 기반 figure/table 설명
    * 📊 **문제 기반 정렬**: Worksheet 전용 문제 번호 기반 정렬
    * 📐 **좌표 기반 정렬**: Document 전용 좌표 기반 정렬
    * 📥 **통합 문서 다운로드**: DOCX/PDF/TXT 형식 지원
    
    ### 기술 스택
    * **Backend**: FastAPI + SQLAlchemy
    * **Database**: MySQL 8.0
    * **AI Models**: DocLayout-YOLO, PaddleOCR, GPT-4o-mini
    * **Document**: python-docx
    """,
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)

# ============================================================================
# CORS 설정
# ============================================================================
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,  # 허용할 출처
    allow_credentials=True,
    allow_methods=["*"],  # 모든 HTTP 메소드 허용
    allow_headers=["*"],  # 모든 헤더 허용
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
        "version": "1.0.0",
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
        db.execute("SELECT 1")
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


# ============================================================================
# 라우터 등록 (Phase 2에서 추가 예정)
# ============================================================================
# from .routers import users, projects, pages, layout_elements
# app.include_router(users.router, prefix="/api/v1/users", tags=["Users"])
# app.include_router(projects.router, prefix="/api/v1/projects", tags=["Projects"])
# app.include_router(pages.router, prefix="/api/v1/pages", tags=["Pages"])
# app.include_router(layout_elements.router, prefix="/api/v1/elements", tags=["Layout Elements"])


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
