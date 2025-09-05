"""
SmartEye LAM (Layout Analysis Module) 마이크로서비스
FastAPI 기반 문서 레이아웃 분석 서비스
"""

from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn
import os
import time
import uuid
from typing import Optional, List, Dict, Any
import cv2
import numpy as np
from pathlib import Path
import json
from loguru import logger

from .models import (
    LayoutAnalysisRequest, 
    LayoutAnalysisResponse, 
    LayoutBlock,
    ImageInfo,
    HealthResponse,
    ModelInfo
)
from .layout_analyzer import LayoutAnalyzer
from .config import get_settings

# 설정 로드
settings = get_settings()

# FastAPI 앱 생성
app = FastAPI(
    title="SmartEye LAM Service",
    description="Layout Analysis Module - 문서 레이아웃 분석 마이크로서비스",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 개발 환경에서는 모든 오리진 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 레이아웃 분석기 초기화
layout_analyzer = LayoutAnalyzer(settings)

@app.on_event("startup")
async def startup_event():
    """서비스 시작 시 초기화"""
    logger.info("SmartEye LAM Service 시작 중...")
    
    # 모델 로딩
    try:
        await layout_analyzer.initialize()
        logger.info("DocLayout-YOLO 모델 로딩 완료")
    except Exception as e:
        logger.error(f"모델 로딩 실패: {e}")
        raise e
    
    logger.info(f"LAM Service가 포트 {settings.port}에서 시작되었습니다")

@app.on_event("shutdown")
async def shutdown_event():
    """서비스 종료 시 정리"""
    logger.info("SmartEye LAM Service 종료 중...")
    await layout_analyzer.cleanup()

@app.get("/", response_model=Dict[str, str])
async def root():
    """루트 엔드포인트"""
    return {
        "service": "SmartEye LAM Service",
        "version": "1.0.0",
        "status": "running",
        "docs": "/docs"
    }

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """서비스 상태 확인"""
    try:
        # 모델 상태 확인
        model_status = await layout_analyzer.check_health()
        
        return HealthResponse(
            status="healthy",
            service="LAM Service",
            version="1.0.0",
            timestamp=int(time.time()),
            model_loaded=model_status["model_loaded"],
            model_version=model_status["model_version"],
            gpu_available=model_status["gpu_available"]
        )
    except Exception as e:
        logger.error(f"Health check 실패: {e}")
        return HealthResponse(
            status="unhealthy",
            service="LAM Service",
            version="1.0.0",
            timestamp=int(time.time()),
            error=str(e)
        )

@app.get("/models/info", response_model=ModelInfo)
async def get_model_info():
    """모델 정보 조회"""
    try:
        model_info = await layout_analyzer.get_model_info()
        return ModelInfo(**model_info)
    except Exception as e:
        logger.error(f"모델 정보 조회 실패: {e}")
        raise HTTPException(status_code=500, detail=f"모델 정보 조회 실패: {str(e)}")

@app.post("/analyze/layout", response_model=LayoutAnalysisResponse)
async def analyze_layout(request: LayoutAnalysisRequest):
    """
    문서 레이아웃 분석
    
    Args:
        request: 레이아웃 분석 요청 데이터
        
    Returns:
        LayoutAnalysisResponse: 분석 결과
    """
    start_time = time.time()
    
    try:
        logger.info(f"레이아웃 분석 시작 - Job ID: {request.job_id}")
        
        # 이미지 파일 존재 확인
        if not os.path.exists(request.image_path):
            raise HTTPException(status_code=404, detail=f"이미지 파일을 찾을 수 없습니다: {request.image_path}")
        
        # 레이아웃 분석 실행
        analysis_result = await layout_analyzer.analyze_layout(
            image_path=request.image_path,
            job_id=request.job_id,
            options=request.options
        )
        
        processing_time = int((time.time() - start_time) * 1000)
        
        logger.info(f"레이아웃 분석 완료 - Job ID: {request.job_id}, 처리 시간: {processing_time}ms")
        
        return LayoutAnalysisResponse(
            job_id=request.job_id,
            status="success",
            processing_time_ms=processing_time,
            **analysis_result
        )
        
    except HTTPException:
        raise
    except Exception as e:
        processing_time = int((time.time() - start_time) * 1000)
        logger.error(f"레이아웃 분석 실패 - Job ID: {request.job_id}, 오류: {e}")
        
        raise HTTPException(
            status_code=500, 
            detail=f"레이아웃 분석 실패: {str(e)}"
        )

@app.post("/analyze/layout/upload", response_model=LayoutAnalysisResponse)
async def analyze_layout_upload(
    file: UploadFile = File(...),
    job_id: Optional[str] = Form(None),
    confidence_threshold: Optional[float] = Form(0.5),
    model_version: Optional[str] = Form("latest")
):
    """
    파일 업로드를 통한 레이아웃 분석
    
    Args:
        file: 업로드된 이미지 파일
        job_id: 작업 ID (선택적)
        confidence_threshold: 신뢰도 임계값
        model_version: 모델 버전
        
    Returns:
        LayoutAnalysisResponse: 분석 결과
    """
    if job_id is None:
        job_id = str(uuid.uuid4())
    
    start_time = time.time()
    temp_file_path = None
    
    try:
        logger.info(f"업로드 파일 레이아웃 분석 시작 - Job ID: {job_id}, 파일: {file.filename}")
        
        # 임시 파일 저장
        temp_dir = Path(settings.temp_dir)
        temp_dir.mkdir(exist_ok=True)
        
        temp_file_path = temp_dir / f"{job_id}_{file.filename}"
        
        # 파일 저장
        with open(temp_file_path, "wb") as buffer:
            content = await file.read()
            buffer.write(content)
        
        # 레이아웃 분석 실행
        analysis_result = await layout_analyzer.analyze_layout(
            image_path=str(temp_file_path),
            job_id=job_id,
            options={
                "confidence_threshold": confidence_threshold,
                "model_version": model_version
            }
        )
        
        processing_time = int((time.time() - start_time) * 1000)
        
        logger.info(f"업로드 파일 레이아웃 분석 완료 - Job ID: {job_id}, 처리 시간: {processing_time}ms")
        
        return LayoutAnalysisResponse(
            job_id=job_id,
            status="success",
            processing_time_ms=processing_time,
            **analysis_result
        )
        
    except Exception as e:
        processing_time = int((time.time() - start_time) * 1000)
        logger.error(f"업로드 파일 레이아웃 분석 실패 - Job ID: {job_id}, 오류: {e}")
        
        raise HTTPException(
            status_code=500, 
            detail=f"레이아웃 분석 실패: {str(e)}"
        )
    
    finally:
        # 임시 파일 정리
        if temp_file_path and temp_file_path.exists():
            try:
                temp_file_path.unlink()
            except Exception as e:
                logger.warning(f"임시 파일 삭제 실패: {e}")

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="info"
    )
