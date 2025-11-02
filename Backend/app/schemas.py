"""
SmartEyeSsen Backend - Pydantic Schemas
=======================================
API 요청/응답 검증을 위한 Pydantic 스키마 정의

주요 기능:
- 요청 본문 검증 (Create, Update)
- 응답 데이터 직렬화 (Read)
- 타입 힌팅 및 자동 문서화
"""

from pydantic import BaseModel, EmailStr, Field, ConfigDict
from typing import Optional, List
from datetime import datetime
from enum import Enum


# ============================================================================
# Enum 정의
# ============================================================================
class DocumentTypeEnum(str, Enum):
    """문서 유형"""
    WORKSHEET = "worksheet"
    DOCUMENT = "document"


class ElementTypeEnum(str, Enum):
    """레이아웃 요소 유형"""
    TEXT = "text"
    TITLE = "title"
    FIGURE = "figure"
    FIGURE_CAPTION = "figure_caption"
    TABLE = "table"
    TABLE_CAPTION = "table_caption"
    HEADER = "header"
    FOOTER = "footer"
    REFERENCE = "reference"
    EQUATION = "equation"


class AlignmentEnum(str, Enum):
    """텍스트 정렬"""
    LEFT = "left"
    CENTER = "center"
    RIGHT = "right"
    JUSTIFY = "justify"


class OutputFormatEnum(str, Enum):
    """출력 형식"""
    DOCX = "docx"
    PDF = "pdf"
    TXT = "txt"


# ============================================================================
# 1. User Schemas
# ============================================================================
class UserBase(BaseModel):
    """사용자 기본 스키마"""
    username: str = Field(..., min_length=3, max_length=50, description="사용자명")
    email: EmailStr = Field(..., description="이메일")


class UserCreate(UserBase):
    """사용자 생성 스키마"""
    password: str = Field(..., min_length=8, max_length=100, description="비밀번호")


class UserUpdate(BaseModel):
    """사용자 수정 스키마"""
    username: Optional[str] = Field(None, min_length=3, max_length=50)
    email: Optional[EmailStr] = None
    password: Optional[str] = Field(None, min_length=8, max_length=100)


class UserResponse(UserBase):
    """사용자 응답 스키마"""
    user_id: int
    created_at: datetime
    last_login: Optional[datetime] = None
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 2. DocumentType Schemas
# ============================================================================
class DocumentTypeBase(BaseModel):
    """문서 유형 기본 스키마"""
    type_name: DocumentTypeEnum = Field(..., description="문서 유형")
    description: Optional[str] = Field(None, max_length=255, description="유형 설명")


class DocumentTypeCreate(DocumentTypeBase):
    """문서 유형 생성 스키마"""
    pass


class DocumentTypeResponse(DocumentTypeBase):
    """문서 유형 응답 스키마"""
    type_id: int
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 3. Project Schemas
# ============================================================================
class ProjectBase(BaseModel):
    """프로젝트 기본 스키마"""
    project_name: str = Field(..., min_length=1, max_length=255, description="프로젝트명")
    type_id: Optional[int] = Field(None, description="문서 유형 ID")


class ProjectCreate(ProjectBase):
    """프로젝트 생성 스키마"""
    pass


class ProjectUpdate(BaseModel):
    """프로젝트 수정 스키마"""
    project_name: Optional[str] = Field(None, min_length=1, max_length=255)
    type_id: Optional[int] = None


class ProjectResponse(ProjectBase):
    """프로젝트 응답 스키마"""
    project_id: int
    user_id: int
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
    image_path: Optional[str] = Field(None, max_length=512, description="이미지 경로")
    image_width: Optional[int] = Field(None, ge=1, description="이미지 너비")
    image_height: Optional[int] = Field(None, ge=1, description="이미지 높이")


class PageCreate(PageBase):
    """페이지 생성 스키마"""
    project_id: int = Field(..., description="프로젝트 ID")


class PageUpdate(BaseModel):
    """페이지 수정 스키마"""
    image_path: Optional[str] = Field(None, max_length=512)
    image_width: Optional[int] = Field(None, ge=1)
    image_height: Optional[int] = Field(None, ge=1)


class PageResponse(PageBase):
    """페이지 응답 스키마"""
    page_id: int
    project_id: int
    uploaded_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


class PageWithElementsResponse(PageResponse):
    """레이아웃 요소 포함 페이지 응답"""
    layout_elements: List["LayoutElementResponse"] = []


# ============================================================================
# 5. LayoutElement Schemas
# ============================================================================
class LayoutElementBase(BaseModel):
    """레이아웃 요소 기본 스키마"""
    element_type: ElementTypeEnum = Field(..., description="요소 유형")
    x_min: int = Field(..., description="좌측 상단 X 좌표")
    y_min: int = Field(..., description="좌측 상단 Y 좌표")
    x_max: int = Field(..., description="우측 하단 X 좌표")
    y_max: int = Field(..., description="우측 하단 Y 좌표")
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="신뢰도")


class LayoutElementCreate(LayoutElementBase):
    """레이아웃 요소 생성 스키마"""
    page_id: int = Field(..., description="페이지 ID")


class LayoutElementUpdate(BaseModel):
    """레이아웃 요소 수정 스키마"""
    element_type: Optional[ElementTypeEnum] = None
    x_min: Optional[int] = None
    y_min: Optional[int] = None
    x_max: Optional[int] = None
    y_max: Optional[int] = None
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0)


class LayoutElementResponse(LayoutElementBase):
    """레이아웃 요소 응답 스키마"""
    element_id: int
    page_id: int
    area: Optional[int] = None
    x_position: Optional[int] = None
    y_position: Optional[int] = None
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 6. TextContent Schemas
# ============================================================================
class TextContentBase(BaseModel):
    """텍스트 내용 기본 스키마"""
    raw_text: Optional[str] = Field(None, description="원본 OCR 텍스트")
    edited_text: Optional[str] = Field(None, description="편집된 텍스트")


class TextContentCreate(TextContentBase):
    """텍스트 내용 생성 스키마"""
    element_id: int = Field(..., description="요소 ID")


class TextContentUpdate(BaseModel):
    """텍스트 내용 수정 스키마"""
    edited_text: Optional[str] = Field(None, description="편집된 텍스트")


class TextContentResponse(TextContentBase):
    """텍스트 내용 응답 스키마"""
    content_id: int
    element_id: int
    version: int
    last_edited_at: Optional[datetime] = None
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 7. AIDescription Schemas
# ============================================================================
class AIDescriptionBase(BaseModel):
    """AI 설명 기본 스키마"""
    description_text: Optional[str] = Field(None, description="AI 생성 설명")
    model_name: Optional[str] = Field(None, max_length=100, description="AI 모델명")


class AIDescriptionCreate(AIDescriptionBase):
    """AI 설명 생성 스키마"""
    element_id: int = Field(..., description="요소 ID")


class AIDescriptionResponse(AIDescriptionBase):
    """AI 설명 응답 스키마"""
    description_id: int
    element_id: int
    generated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 8. QuestionGroup Schemas
# ============================================================================
class QuestionGroupBase(BaseModel):
    """문제 그룹 기본 스키마"""
    group_number: int = Field(..., ge=1, description="문제 번호")
    page_id: int = Field(..., description="페이지 ID")


class QuestionGroupCreate(QuestionGroupBase):
    """문제 그룹 생성 스키마"""
    pass


class QuestionGroupResponse(QuestionGroupBase):
    """문제 그룹 응답 스키마"""
    group_id: int
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 9. QuestionElement Schemas
# ============================================================================
class QuestionElementBase(BaseModel):
    """문제 요소 기본 스키마"""
    group_id: int = Field(..., description="그룹 ID")
    element_id: int = Field(..., description="요소 ID")
    element_order: int = Field(..., ge=1, description="요소 순서")


class QuestionElementCreate(QuestionElementBase):
    """문제 요소 생성 스키마"""
    pass


class QuestionElementResponse(QuestionElementBase):
    """문제 요소 응답 스키마"""
    question_element_id: int
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 10. TextVersion Schemas
# ============================================================================
class TextVersionBase(BaseModel):
    """텍스트 버전 기본 스키마"""
    version_number: int = Field(..., ge=1, description="버전 번호")
    version_text: str = Field(..., description="버전 텍스트")


class TextVersionCreate(TextVersionBase):
    """텍스트 버전 생성 스키마"""
    content_id: int = Field(..., description="내용 ID")


class TextVersionResponse(TextVersionBase):
    """텍스트 버전 응답 스키마"""
    version_id: int
    content_id: int
    edited_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 11. FormattingRule Schemas
# ============================================================================
class FormattingRuleBase(BaseModel):
    """서식 규칙 기본 스키마"""
    element_type: ElementTypeEnum = Field(..., description="요소 유형")
    font_name: Optional[str] = Field(None, max_length=100, description="폰트명")
    font_size: Optional[int] = Field(None, ge=1, le=72, description="폰트 크기")
    font_bold: bool = Field(False, description="굵게")
    font_italic: bool = Field(False, description="기울임")
    font_underline: bool = Field(False, description="밑줄")
    alignment: AlignmentEnum = Field(AlignmentEnum.LEFT, description="정렬")
    line_spacing: Optional[float] = Field(None, ge=0.5, le=3.0, description="줄 간격")
    space_before: Optional[int] = Field(None, ge=0, description="앞 여백 (pt)")
    space_after: Optional[int] = Field(None, ge=0, description="뒤 여백 (pt)")


class FormattingRuleCreate(FormattingRuleBase):
    """서식 규칙 생성 스키마"""
    pass


class FormattingRuleUpdate(BaseModel):
    """서식 규칙 수정 스키마"""
    font_name: Optional[str] = Field(None, max_length=100)
    font_size: Optional[int] = Field(None, ge=1, le=72)
    font_bold: Optional[bool] = None
    font_italic: Optional[bool] = None
    font_underline: Optional[bool] = None
    alignment: Optional[AlignmentEnum] = None
    line_spacing: Optional[float] = Field(None, ge=0.5, le=3.0)
    space_before: Optional[int] = Field(None, ge=0)
    space_after: Optional[int] = Field(None, ge=0)


class FormattingRuleResponse(FormattingRuleBase):
    """서식 규칙 응답 스키마"""
    rule_id: int
    
    model_config = ConfigDict(from_attributes=True)


# ============================================================================
# 12. CombinedResult Schemas
# ============================================================================
class CombinedResultBase(BaseModel):
    """통합 결과 기본 스키마"""
    combined_text: Optional[str] = Field(None, description="통합 텍스트")
    output_format: OutputFormatEnum = Field(OutputFormatEnum.DOCX, description="출력 형식")
    file_path: Optional[str] = Field(None, max_length=512, description="파일 경로")


class CombinedResultCreate(CombinedResultBase):
    """통합 결과 생성 스키마"""
    project_id: int = Field(..., description="프로젝트 ID")


class CombinedResultResponse(CombinedResultBase):
    """통합 결과 응답 스키마"""
    result_id: int
    project_id: int
    generated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


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


class ProjectDetailResponse(ProjectResponse):
    """상세 프로젝트 정보 (페이지 및 결과 포함)"""
    pages: List[PageDetailResponse] = []
    combined_results: List[CombinedResultResponse] = []
    document_type: Optional[DocumentTypeResponse] = None


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
