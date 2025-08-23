"""
데이터 모델 정의
Pydantic 모델을 사용한 요청/응답 스키마
"""

from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from enum import Enum

class AnalysisOptions(BaseModel):
    """분석 옵션"""
    confidence_threshold: float = Field(default=0.5, ge=0.0, le=1.0, description="신뢰도 임계값")
    model_version: str = Field(default="latest", description="사용할 모델 버전")
    max_image_size: Optional[int] = Field(default=4096, description="최대 이미지 크기")
    enable_preprocessing: bool = Field(default=True, description="이미지 전처리 활성화")

class LayoutAnalysisRequest(BaseModel):
    """레이아웃 분석 요청"""
    image_path: str = Field(..., description="분석할 이미지 파일 경로")
    job_id: str = Field(..., description="작업 고유 ID")
    options: Optional[AnalysisOptions] = Field(default_factory=AnalysisOptions, description="분석 옵션")

class Coordinates(BaseModel):
    """좌표 정보"""
    x1: int = Field(..., description="좌상단 X 좌표")
    y1: int = Field(..., description="좌상단 Y 좌표")
    x2: int = Field(..., description="우하단 X 좌표")
    y2: int = Field(..., description="우하단 Y 좌표")

class LayoutBlock(BaseModel):
    """레이아웃 블록 정보"""
    block_index: int = Field(..., description="블록 인덱스")
    class_name: str = Field(..., description="블록 클래스명 (title, plain text, table, etc.)")
    confidence: float = Field(..., ge=0.0, le=1.0, description="신뢰도")
    coordinates: Coordinates = Field(..., description="블록 좌표")
    area: int = Field(..., description="블록 면적 (픽셀)")
    width: Optional[int] = Field(None, description="블록 너비")
    height: Optional[int] = Field(None, description="블록 높이")

class ImageInfo(BaseModel):
    """이미지 정보"""
    width: int = Field(..., description="이미지 너비")
    height: int = Field(..., description="이미지 높이")
    format: str = Field(..., description="이미지 포맷")
    file_size: Optional[int] = Field(None, description="파일 크기 (바이트)")
    channels: Optional[int] = Field(None, description="채널 수")

class LayoutAnalysisResponse(BaseModel):
    """레이아웃 분석 응답"""
    job_id: str = Field(..., description="작업 고유 ID")
    status: str = Field(..., description="처리 상태 (success, failed)")
    processing_time_ms: int = Field(..., description="처리 시간 (밀리초)")
    image_info: ImageInfo = Field(..., description="이미지 정보")
    layout_blocks: List[LayoutBlock] = Field(..., description="감지된 레이아웃 블록 목록")
    detected_objects_count: int = Field(..., description="감지된 객체 수")
    model_used: str = Field(..., description="사용된 모델명")
    error_message: Optional[str] = Field(None, description="오류 메시지 (실패 시)")

class HealthStatus(str, Enum):
    """헬스 체크 상태"""
    HEALTHY = "healthy"
    UNHEALTHY = "unhealthy"
    DEGRADED = "degraded"

class HealthResponse(BaseModel):
    """헬스 체크 응답"""
    status: HealthStatus = Field(..., description="서비스 상태")
    service: str = Field(..., description="서비스명")
    version: str = Field(..., description="서비스 버전")
    timestamp: int = Field(..., description="응답 타임스탬프")
    model_loaded: Optional[bool] = Field(None, description="모델 로딩 상태")
    model_version: Optional[str] = Field(None, description="로딩된 모델 버전")
    gpu_available: Optional[bool] = Field(None, description="GPU 사용 가능 여부")
    error: Optional[str] = Field(None, description="오류 메시지 (비정상 시)")

class ModelInfo(BaseModel):
    """모델 정보"""
    model_name: str = Field(..., description="모델명")
    model_version: str = Field(..., description="모델 버전")
    model_path: str = Field(..., description="모델 파일 경로")
    supported_classes: List[str] = Field(..., description="지원하는 클래스 목록")
    input_size: tuple = Field(..., description="입력 이미지 크기")
    confidence_threshold: float = Field(..., description="기본 신뢰도 임계값")
    gpu_enabled: bool = Field(..., description="GPU 사용 여부")
    loaded_at: Optional[int] = Field(None, description="로딩 시간 (타임스탬프)")

class ErrorResponse(BaseModel):
    """오류 응답"""
    error: str = Field(..., description="오류 유형")
    message: str = Field(..., description="오류 메시지")
    detail: Optional[str] = Field(None, description="상세 오류 정보")
    timestamp: int = Field(..., description="오류 발생 시간")
    job_id: Optional[str] = Field(None, description="관련 작업 ID")
