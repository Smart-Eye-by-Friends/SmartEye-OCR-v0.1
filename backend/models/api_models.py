"""
API data models for SmartEye backend
"""
from typing import List, Dict, Any, Optional, Union
from pydantic import BaseModel, Field
from enum import Enum


class TaskStatus(str, Enum):
    """Task processing status"""
    PENDING = "pending"
    PROCESSING = "processing" 
    COMPLETED = "completed"
    FAILED = "failed"


class ProcessingMode(str, Enum):
    """Processing mode options"""
    FAST = "fast"
    ACCURATE = "accurate"
    COMPLETE = "complete"


# Request Models
class SingleImageRequest(BaseModel):
    """Request for single image analysis"""
    confidence_threshold: float = Field(default=0.25, ge=0.1, le=0.9)
    merge_boxes: bool = Field(default=True)
    iou_threshold: float = Field(default=0.3, ge=0.1, le=0.8)
    processing_mode: ProcessingMode = Field(default=ProcessingMode.COMPLETE)
    include_visualization: bool = Field(default=True)


class BatchImageRequest(BaseModel):
    """Request for batch image analysis"""
    confidence_threshold: float = Field(default=0.25, ge=0.1, le=0.9)
    merge_boxes: bool = Field(default=True)
    iou_threshold: float = Field(default=0.3, ge=0.1, le=0.8)
    processing_mode: ProcessingMode = Field(default=ProcessingMode.FAST)
    batch_size: Optional[int] = Field(default=None, ge=1, le=10)


class PDFProcessRequest(BaseModel):
    """Request for PDF processing"""
    confidence_threshold: float = Field(default=0.25, ge=0.1, le=0.9)
    merge_boxes: bool = Field(default=True)
    iou_threshold: float = Field(default=0.3, ge=0.1, le=0.8)
    processing_mode: ProcessingMode = Field(default=ProcessingMode.FAST)
    max_pages: Optional[int] = Field(default=None, ge=1, le=100)


# Response Models
class FileInfo(BaseModel):
    """File information"""
    filename: str
    size_mb: float
    mime_type: str
    extension: str
    width: Optional[int] = None
    height: Optional[int] = None
    page_count: Optional[int] = None


class LayoutObject(BaseModel):
    """Layout analysis object"""
    id: int
    class_name: str
    confidence: float
    coordinates: List[int] = Field(..., min_items=4, max_items=4)
    area: float


class ContentObject(BaseModel):
    """Content analysis object"""
    id: int
    class_name: str
    coordinates: List[int] = Field(..., min_items=4, max_items=4)
    confidence: float
    content: str
    content_type: str  # "text" or "description"
    method: str  # "OCR" or "OpenAI_Vision"


class LayoutAnalysisResult(BaseModel):
    """Layout analysis result"""
    detected_objects_count: int
    model_used: str
    confidence_threshold: float
    layout_info: List[LayoutObject]


class ContentAnalysisResult(BaseModel):
    """Content analysis result"""
    total_objects: int
    ocr_objects: int
    api_objects: int
    results: List[ContentObject]


class MemoryInfo(BaseModel):
    """Memory usage information"""
    total_gb: float
    available_gb: float
    used_gb: float
    percent_used: float
    gpu_total_gb: Optional[float] = None
    gpu_allocated_gb: Optional[float] = None
    gpu_percent_used: Optional[float] = None


class TaskInfo(BaseModel):
    """Task information"""
    task_id: str
    status: TaskStatus
    created_at: float
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    progress: float = Field(..., ge=0, le=100)
    message: str = ""
    error: Optional[str] = None


class SingleImageResult(BaseModel):
    """Single image processing result"""
    task_id: str
    file_info: FileInfo
    layout_analysis: LayoutAnalysisResult
    content_analysis: ContentAnalysisResult
    processing_time: float
    visualization_path: Optional[str] = None
    memory_usage: MemoryInfo


class BatchImageResult(BaseModel):
    """Batch image processing result"""
    task_id: str
    total_images: int
    successful_images: int
    failed_images: int
    batch_size_used: int
    processing_time: float
    results: List[Dict[str, Any]]
    failures: List[Dict[str, str]]
    memory_usage: MemoryInfo


class PDFProcessResult(BaseModel):
    """PDF processing result"""
    task_id: str
    pdf_info: FileInfo
    page_count: int
    batch_result: BatchImageResult
    processing_time: float


# Status Response Models
class TaskStatusResponse(BaseModel):
    """Task status response"""
    task_info: TaskInfo
    result: Optional[Union[SingleImageResult, BatchImageResult, PDFProcessResult]] = None


class TaskResultResponse(BaseModel):
    """Task result response"""
    task_id: str
    status: TaskStatus
    result: Optional[Union[SingleImageResult, BatchImageResult, PDFProcessResult]] = None
    error: Optional[str] = None


# System Status Models
class SystemHealth(BaseModel):
    """System health information"""
    status: str
    memory_status: str
    gpu_available: bool
    models_loaded: bool
    uptime_seconds: float
    active_tasks: int
    completed_tasks: int
    failed_tasks: int


class ModelInfo(BaseModel):
    """Model information"""
    model_name: str
    description: str
    loaded: bool
    device: str
    repo_id: str
    filename: str


class SystemInfo(BaseModel):
    """System information"""
    app_name: str
    version: str
    models: List[ModelInfo]
    memory_info: MemoryInfo
    health: SystemHealth


# Error Response Models
class ErrorResponse(BaseModel):
    """Error response"""
    error: str
    detail: Optional[str] = None
    task_id: Optional[str] = None
    timestamp: float


class ValidationError(BaseModel):
    """Validation error details"""
    field: str
    message: str
    value: Any