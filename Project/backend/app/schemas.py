# -*- coding: utf-8 -*-
"""
FastAPI Pydantic 스키마 정의 (Phase 3.1, 3.3 추가)
==============================================
API 요청 본문 및 응답 본문의 데이터 구조를 정의합니다.
"""

from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime

# ============================================================================
# Phase 3.1: 프로젝트 및 페이지 스키마
# ============================================================================

# --- Project Schemas ---
class ProjectBase(BaseModel):
    """프로젝트 기본 정보"""
    project_name: str = Field(..., min_length=1, max_length=255, description="프로젝트 이름")
    doc_type_id: int = Field(..., description="문서 타입 ID (1: worksheet, 2: document 등)")
    # user_id는 인증 시스템 구현 후 제거 예정, 임시로 포함
    user_id: int = Field(1, description="사용자 ID (임시)")

class ProjectCreate(ProjectBase):
    """프로젝트 생성 요청"""
    pass

class Project(ProjectBase):
    """프로젝트 상세 정보 응답"""
    project_id: int
    total_pages: int = Field(0, description="총 페이지 수")
    status: str = Field("created", description="프로젝트 상태")
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True # ORM 모델 등에서 속성으로 읽기 활성화

class ProjectListResponse(BaseModel):
    """프로젝트 목록 응답"""
    projects: List[Project]

# --- Page Schemas ---
class PageBase(BaseModel):
    """페이지 기본 정보 (응답용)"""
    page_id: int
    project_id: int
    page_number: int
    image_path: str
    analysis_status: str = Field("pending", description="분석 상태")

class PageCreateResponse(PageBase):
    """페이지 생성/추가 응답"""
    pass

class PageListResponse(BaseModel):
    """페이지 목록 응답"""
    project_id: int
    total_pages: int
    pages: List[PageBase]


# ============================================================================
# Phase 3.2: 배치 분석 스키마 (변경 없음)
# ============================================================================

class AnalyzeProjectRequest(BaseModel):
    """배치 분석 요청 모델"""
    document_type: str = Field(default="question_based", description="문서 타입")
    use_ai_descriptions: bool = Field(default=True, description="AI 설명 생성 여부")
    api_key: Optional[str] = Field(default=None, description="OpenAI API 키")

class PageAnalysisResult(BaseModel):
    """단일 페이지 분석 결과"""
    page_id: int
    page_number: int
    status: str
    processing_time: float
    stats: Dict[str, Any] = Field({}, description="분석 통계")
    error: Optional[str] = None

class AnalyzeProjectResponse(BaseModel):
    """배치 분석 응답 모델"""
    project_id: int
    total_pages: int
    processed_pages: int
    successful_pages: int
    failed_pages: int
    total_time: float
    status: str
    page_results: List[PageAnalysisResult]

class ProjectStatusResponse(BaseModel):
    """프로젝트 상태 응답 모델"""
    project_id: int
    project_name: str
    total_pages: int
    status: str
    created_at: datetime # datetime으로 변경
    updated_at: datetime # datetime으로 변경
    pages: List[Dict[str, Any]] # 페이지 상세 정보 포함


# ============================================================================
# Phase 3.3: 통합 다운로드 스키마
# ============================================================================

class CombinedTextStats(BaseModel):
    """통합 텍스트 통계"""
    total_pages: int
    total_words: int
    total_characters: int

class CombinedTextResponse(BaseModel):
    """통합 텍스트 응답"""
    project_id: int
    combined_text: str
    stats: CombinedTextStats
    generated_at: datetime # datetime으로 변경

class DownloadResponse(BaseModel):
    """파일 다운로드 성공 응답 (메타데이터용)"""
    message: str = "Word 문서가 성공적으로 생성되었습니다."
    project_id: int
    filename: str
    download_url: str


# ============================================================================
# 공통 스키마
# ============================================================================

class MessageResponse(BaseModel):
    """간단한 메시지 응답"""
    message: str

# ============================================================================
# 페이지 텍스트 스키마
# ============================================================================

class PageTextResponse(BaseModel):
    """페이지 텍스트 조회 응답"""
    page_id: int
    version_id: int
    version_type: str
    is_current: bool
    content: str
    created_at: datetime

class PageTextUpdate(BaseModel):
    """페이지 텍스트 업데이트 요청"""
    content: str = Field(..., description="사용자가 수정한 전체 텍스트 내용")
    user_id: Optional[int] = Field(1, description="수정한 사용자 ID (임시)")