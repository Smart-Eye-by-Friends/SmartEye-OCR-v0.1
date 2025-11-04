"""
SmartEyeSsen Backend - Pydantic Schemas v2
=================================================================================
ERD v2 기준 API 요청/응답 검증을 위한 Pydantic 스키마 정의

주요 기능:
- 요청 본문 검증 (Create, Update)
- 응답 데이터 직렬화 (Response)
- 타입 힌팅 및 자동 문서화
- models.py와 100% 호환

최종 수정일: 2025-01-22 (v2)
"""

from pydantic import BaseModel, EmailStr, Field, ConfigDict
from typing import Optional, List
from datetime import datetime
from enum import Enum


# ============================================================================
# Enum 정의 (models.py와 동일)
# ============================================================================
class SortingMethodEnum(str, Enum):
    """정렬 방식"""
    QUESTION_BASED = "question_based"
    READING_ORDER = "reading_order"


class AnalysisModeEnum(str, Enum):
    """분석 모드"""
    AUTO = "auto"
    MANUAL = "manual"
    HYBRID = "hybrid"


class ProjectStatusEnum(str, Enum):
    """프로젝트 상태"""
    CREATED = "created"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    ERROR = "error"


class AnalysisStatusEnum(str, Enum):
    """분석 상태"""
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    ERROR = "error"


class VersionTypeEnum(str, Enum):
    """버전 유형"""
    ORIGINAL = "original"
    AUTO_FORMATTED = "auto_formatted"
    USER_EDITED = "user_edited"


# ============================================================================
# 1. User Schemas
# ============================================================================
class UserBase(BaseModel):
    """사용자 기본 스키마"""
    email: EmailStr = Field(..., description="이메일 (로그인 ID)")
    name: str = Field(..., min_length=1, max_length=100, description="사용자 이름")


class UserCreate(UserBase):
    """사용자 생성 스키마"""
    password: str = Field(..., min_length=8, max_length=100, description="비밀번호")
    role: Optional[str] = Field("user", max_length=50, description="역할 (admin/teacher/student/user)")
    api_key: Optional[str] = Field(None, max_length=255, description="OpenAI API 키")


class UserUpdate(BaseModel):
    """사용자 수정 스키마"""
    email: Optional[EmailStr] = None
    name: Optional[str] = Field(None, min_length=1, max_length=100)
    password: Optional[str] = Field(None, min_length=8, max_length=100)
    role: Optional[str] = Field(None, max_length=50)
    api_key: Optional[str] = Field(None, max_length=255)


class UserResponse(UserBase):
    """사용자 응답 스키마"""
    user_id: int
    role: str
    created_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 2. DocumentType Schemas
# ============================================================================
class DocumentTypeBase(BaseModel):
    """문서 타입 기본 스키마"""
    type_name: str = Field(..., min_length=1, max_length=100, description="타입명 (worksheet/document/form)")
    model_name: str = Field(..., min_length=1, max_length=100, description="AI 모델명")
    sorting_method: SortingMethodEnum = Field(..., description="정렬 방식")
    description: Optional[str] = Field(None, description="타입 설명")


class DocumentTypeCreate(DocumentTypeBase):
    """문서 타입 생성 스키마"""
    pass


class DocumentTypeUpdate(BaseModel):
    """문서 타입 수정 스키마"""
    type_name: Optional[str] = Field(None, min_length=1, max_length=100)
    model_name: Optional[str] = Field(None, min_length=1, max_length=100)
    sorting_method: Optional[SortingMethodEnum] = None
    description: Optional[str] = None


class DocumentTypeResponse(DocumentTypeBase):
    """문서 타입 응답 스키마"""
    doc_type_id: int
    created_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 3. Project Schemas
# ============================================================================
class ProjectBase(BaseModel):
    """프로젝트 기본 스키마"""
    project_name: str = Field(..., min_length=1, max_length=255, description="프로젝트 이름")
    doc_type_id: int = Field(..., description="문서 타입 ID")


class ProjectCreate(ProjectBase):
    """프로젝트 생성 스키마"""
    analysis_mode: Optional[AnalysisModeEnum] = Field(AnalysisModeEnum.AUTO, description="분석 모드")


class ProjectUpdate(BaseModel):
    """프로젝트 수정 스키마"""
    project_name: Optional[str] = Field(None, min_length=1, max_length=255)
    doc_type_id: Optional[int] = None
    analysis_mode: Optional[AnalysisModeEnum] = None
    status: Optional[ProjectStatusEnum] = None


class ProjectResponse(ProjectBase):
    """프로젝트 응답 스키마"""
    project_id: int
    user_id: int
    total_pages: int
    analysis_mode: AnalysisModeEnum
    status: ProjectStatusEnum
    created_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


class ProjectWithPagesResponse(ProjectResponse):
    """페이지 포함 프로젝트 응답"""
    pages: List["PageResponse"] = []


# ============================================================================
# 4. Page Schemas
# ============================================================================
class PageBase(BaseModel):
    """페이지 기본 스키마"""
    page_number: int = Field(..., ge=1, description="페이지 번호")
    image_path: str = Field(..., min_length=1, max_length=500, description="이미지 파일 경로")


class PageCreate(PageBase):
    """페이지 생성 스키마"""
    project_id: int = Field(..., description="프로젝트 ID")
    image_width: Optional[int] = Field(None, ge=1, description="이미지 너비")
    image_height: Optional[int] = Field(None, ge=1, description="이미지 높이")


class PageUpdate(BaseModel):
    """페이지 수정 스키마"""
    image_path: Optional[str] = Field(None, min_length=1, max_length=500)
    image_width: Optional[int] = Field(None, ge=1)
    image_height: Optional[int] = Field(None, ge=1)
    analysis_status: Optional[AnalysisStatusEnum] = None
    processing_time: Optional[float] = Field(None, ge=0)


class PageResponse(PageBase):
    """페이지 응답 스키마"""
    page_id: int
    project_id: int
    image_width: Optional[int] = None
    image_height: Optional[int] = None
    analysis_status: AnalysisStatusEnum
    processing_time: Optional[float] = None
    created_at: datetime
    analyzed_at: Optional[datetime] = None
    
    model_config = ConfigDict(from_attributes=True)


class PageWithElementsResponse(PageResponse):
    """레이아웃 요소 포함 페이지 응답"""
    layout_elements: List["LayoutElementResponse"] = []
    text_content: Optional[str] = None


# ============================================================================
# 페이지 추가 응답/요청 스키마
# ============================================================================
class MultiPageCreateResponse(BaseModel):
    """다중 페이지 생성 응답 (PDF 업로드 시)"""
    project_id: int = Field(..., description="프로젝트 ID")
    total_created: int = Field(..., ge=0, description="생성된 페이지 수")
    source_type: str = Field(..., description="소스 타입 (pdf 또는 image)")
    pages: List[PageResponse] = Field(default=[], description="생성된 페이지 목록")

    model_config = ConfigDict(from_attributes=True)


class PageTextResponse(BaseModel):
    """페이지 텍스트 조회 응답"""
    page_id: int
    version_id: int
    version_type: str
    is_current: bool
    content: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class PageTextUpdate(BaseModel):
    """페이지 텍스트 업데이트 요청"""
    content: str = Field(..., description="저장할 전체 텍스트 내용")
    user_id: Optional[int] = Field(None, description="수정한 사용자 ID")


# ============================================================================
# 5. LayoutElement Schemas
# ============================================================================
class LayoutElementBase(BaseModel):
    """레이아웃 요소 기본 스키마"""
    class_name: str = Field(..., min_length=1, max_length=100, description="클래스명")
    confidence: float = Field(..., ge=0.0, le=1.0, description="신뢰도")
    bbox_x: int = Field(..., description="X 좌표 (왼쪽 상단)")
    bbox_y: int = Field(..., description="Y 좌표 (왼쪽 상단)")
    bbox_width: int = Field(..., ge=1, description="너비 (픽셀)")
    bbox_height: int = Field(..., ge=1, description="높이 (픽셀)")


class LayoutElementCreate(LayoutElementBase):
    """레이아웃 요소 생성 스키마"""
    page_id: int = Field(..., description="페이지 ID")


class LayoutElementUpdate(BaseModel):
    """레이아웃 요소 수정 스키마"""
    class_name: Optional[str] = Field(None, min_length=1, max_length=100)
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0)
    bbox_x: Optional[int] = None
    bbox_y: Optional[int] = None
    bbox_width: Optional[int] = Field(None, ge=1)
    bbox_height: Optional[int] = Field(None, ge=1)


class LayoutElementResponse(LayoutElementBase):
    """레이아웃 요소 응답 스키마"""
    element_id: int
    page_id: int
    area: Optional[int] = None
    y_position: Optional[int] = None
    x_position: Optional[int] = None
    created_at: datetime
    text_content: Optional["TextContentResponse"] = None
    ai_description: Optional["AIDescriptionResponse"] = None
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 6. TextContent Schemas
# ============================================================================
class TextContentBase(BaseModel):
    """텍스트 내용 기본 스키마"""
    ocr_text: str = Field(..., description="OCR 추출 텍스트")
    ocr_engine: Optional[str] = Field("PaddleOCR", max_length=50, description="OCR 엔진")
    ocr_confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="OCR 신뢰도")
    language: Optional[str] = Field("ko", max_length=10, description="언어 코드")


class TextContentCreate(TextContentBase):
    """텍스트 내용 생성 스키마"""
    element_id: int = Field(..., description="레이아웃 요소 ID")


class TextContentUpdate(BaseModel):
    """텍스트 내용 수정 스키마"""
    ocr_text: Optional[str] = None
    ocr_engine: Optional[str] = Field(None, max_length=50)
    ocr_confidence: Optional[float] = Field(None, ge=0.0, le=1.0)
    language: Optional[str] = Field(None, max_length=10)


class TextContentResponse(TextContentBase):
    """텍스트 내용 응답 스키마"""
    text_id: int
    element_id: int
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 7. AIDescription Schemas
# ============================================================================
class AIDescriptionBase(BaseModel):
    """AI 설명 기본 스키마"""
    description: str = Field(..., description="AI 생성 설명")
    ai_model: Optional[str] = Field("gpt-4o-mini", max_length=100, description="AI 모델명")
    prompt_used: Optional[str] = Field(None, description="사용한 프롬프트")


class AIDescriptionCreate(AIDescriptionBase):
    """AI 설명 생성 스키마"""
    element_id: int = Field(..., description="레이아웃 요소 ID")


class AIDescriptionUpdate(BaseModel):
    """AI 설명 수정 스키마"""
    description: Optional[str] = None
    ai_model: Optional[str] = Field(None, max_length=100)
    prompt_used: Optional[str] = None


class AIDescriptionResponse(AIDescriptionBase):
    """AI 설명 응답 스키마"""
    ai_desc_id: int
    element_id: int
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 8. QuestionGroup Schemas
# ============================================================================
class QuestionGroupBase(BaseModel):
    """문제 그룹 기본 스키마"""
    page_id: int = Field(..., description="페이지 ID")
    anchor_element_id: int = Field(..., description="앵커 요소 ID")
    start_y: int = Field(..., description="문제 시작 Y좌표")
    end_y: int = Field(..., description="문제 종료 Y좌표")


class QuestionGroupCreate(QuestionGroupBase):
    """문제 그룹 생성 스키마"""
    element_count: Optional[int] = Field(0, ge=0, description="요소 개수")


class QuestionGroupUpdate(BaseModel):
    """문제 그룹 수정 스키마"""
    start_y: Optional[int] = None
    end_y: Optional[int] = None
    element_count: Optional[int] = Field(None, ge=0)


class QuestionGroupResponse(QuestionGroupBase):
    """문제 그룹 응답 스키마"""
    question_group_id: int
    element_count: int
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 9. QuestionElement Schemas
# ============================================================================
class QuestionElementBase(BaseModel):
    """문제 요소 기본 스키마"""
    question_group_id: int = Field(..., description="문제 그룹 ID")
    element_id: int = Field(..., description="레이아웃 요소 ID")
    order_in_question: int = Field(..., ge=1, description="문제 내 순서")


class QuestionElementCreate(QuestionElementBase):
    """문제 요소 생성 스키마"""
    pass


class QuestionElementUpdate(BaseModel):
    """문제 요소 수정 스키마"""
    order_in_question: Optional[int] = Field(None, ge=1)


class QuestionElementResponse(QuestionElementBase):
    """문제 요소 응답 스키마"""
    qe_id: int
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 10. TextVersion Schemas
# ============================================================================
class TextVersionBase(BaseModel):
    """텍스트 버전 기본 스키마"""
    page_id: int = Field(..., description="페이지 ID")
    content: str = Field(..., description="텍스트 내용")
    version_number: int = Field(..., ge=1, description="버전 번호")
    version_type: VersionTypeEnum = Field(..., description="버전 유형")


class TextVersionCreate(TextVersionBase):
    """텍스트 버전 생성 스키마"""
    user_id: Optional[int] = Field(None, description="수정한 사용자 ID")
    is_current: Optional[bool] = Field(False, description="현재 버전 여부")


class TextVersionUpdate(BaseModel):
    """텍스트 버전 수정 스키마"""
    content: Optional[str] = None
    is_current: Optional[bool] = None


class TextVersionResponse(TextVersionBase):
    """텍스트 버전 응답 스키마"""
    version_id: int
    user_id: Optional[int] = None
    is_current: bool
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 11. FormattingRule Schemas
# ============================================================================
class FormattingRuleBase(BaseModel):
    """포맷팅 규칙 기본 스키마"""
    doc_type_id: int = Field(..., description="문서 타입 ID")
    class_name: str = Field(..., min_length=1, max_length=100, description="클래스명")
    prefix: Optional[str] = Field("", max_length=50, description="접두사")
    suffix: Optional[str] = Field("", max_length=50, description="접미사")
    indent_level: Optional[int] = Field(0, ge=0, le=10, description="들여쓰기 레벨")


class FormattingRuleCreate(FormattingRuleBase):
    """포맷팅 규칙 생성 스키마"""
    font_size: Optional[str] = Field(None, max_length=20, description="폰트 크기")
    font_weight: Optional[str] = Field(None, max_length=20, description="폰트 두께")


class FormattingRuleUpdate(BaseModel):
    """포맷팅 규칙 수정 스키마"""
    class_name: Optional[str] = Field(None, min_length=1, max_length=100)
    prefix: Optional[str] = Field(None, max_length=50)
    suffix: Optional[str] = Field(None, max_length=50)
    indent_level: Optional[int] = Field(None, ge=0, le=10)
    font_size: Optional[str] = Field(None, max_length=20)
    font_weight: Optional[str] = Field(None, max_length=20)


class FormattingRuleResponse(FormattingRuleBase):
    """포맷팅 규칙 응답 스키마"""
    rule_id: int
    font_size: Optional[str] = None
    font_weight: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 12. CombinedResult Schemas
# ============================================================================
class CombinedResultBase(BaseModel):
    """통합 결과 기본 스키마"""
    combined_text: str = Field(..., description="통합된 전체 텍스트")
    combined_stats: Optional[dict] = Field(None, description="통계 정보 (JSON)")


class CombinedResultCreate(CombinedResultBase):
    """통합 결과 생성 스키마"""
    project_id: int = Field(..., description="프로젝트 ID")


class CombinedResultUpdate(BaseModel):
    """통합 결과 수정 스키마"""
    combined_text: Optional[str] = None
    combined_stats: Optional[dict] = None


class CombinedResultResponse(CombinedResultBase):
    """통합 결과 응답 스키마"""
    combined_id: int
    project_id: int
    generated_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


class CombinedTextStats(BaseModel):
    """통합 텍스트 통계"""
    total_pages: int
    total_words: int
    total_characters: int


class CombinedTextResponse(BaseModel):
    """통합 텍스트 응답"""
    project_id: int
    project_name: Optional[str] = None
    combined_text: str
    stats: CombinedTextStats
    generated_at: datetime


class DownloadResponse(BaseModel):
    """문서 다운로드 메타데이터 응답"""
    message: str = Field(
        "Word 문서가 성공적으로 생성되었습니다.", description="응답 메시지"
    )
    project_id: int
    filename: str
    download_url: Optional[str] = Field(
        None, description="다운로드 가능한 URL (선택 사항)"
    )


# ============================================================================
# 복합 응답 스키마 (관계 포함)
# ============================================================================
class LayoutElementWithContentResponse(LayoutElementResponse):
    """텍스트 및 AI 설명 포함 레이아웃 요소"""
    text_content: Optional[TextContentResponse] = None
    ai_description: Optional[AIDescriptionResponse] = None


class PageDetailResponse(PageResponse):
    """상세 페이지 정보 (모든 관계 포함)"""
    layout_elements: List[LayoutElementWithContentResponse] = []
    question_groups: List[QuestionGroupResponse] = []


class ProjectDetailResponse(ProjectResponse):
    """상세 프로젝트 정보 (페이지 및 결과 포함)"""
    pages: List[PageDetailResponse] = []
    combined_result: Optional[CombinedResultResponse] = None
    document_type: Optional[DocumentTypeResponse] = None


class QuestionGroupWithElementsResponse(QuestionGroupResponse):
    """요소 포함 문제 그룹 응답"""
    question_elements: List[QuestionElementResponse] = []


# ============================================================================
# 유틸리티 스키마
# ============================================================================
class MessageResponse(BaseModel):
    """일반 메시지 응답"""
    message: str
    detail: Optional[str] = None


class ErrorResponse(BaseModel):
    """에러 응답"""
    error: str
    detail: Optional[str] = None
    status_code: int


class PaginationParams(BaseModel):
    """페이지네이션 파라미터"""
    skip: int = Field(0, ge=0, description="건너뛸 개수")
    limit: int = Field(100, ge=1, le=1000, description="조회 개수")


class PaginatedResponse(BaseModel):
    """페이지네이션 응답"""
    total: int = Field(..., description="전체 개수")
    skip: int = Field(..., description="건너뛴 개수")
    limit: int = Field(..., description="조회 개수")
    items: List = Field(..., description="데이터 목록")


# ============================================================================
# Forward Reference 해결
# ============================================================================
ProjectWithPagesResponse.model_rebuild()
PageWithElementsResponse.model_rebuild()
PageDetailResponse.model_rebuild()
ProjectDetailResponse.model_rebuild()
LayoutElementWithContentResponse.model_rebuild()
QuestionGroupWithElementsResponse.model_rebuild()
