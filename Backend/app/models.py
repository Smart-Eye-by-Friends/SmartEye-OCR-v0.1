"""
SmartEyeSsen Backend - SQLAlchemy ORM Models
============================================
12개 테이블에 대한 SQLAlchemy 모델 정의

테이블 목록:
1. users - 사용자 정보
2. document_types - 문서 유형 (worksheet/document)
3. projects - 프로젝트 (문서 단위)
4. pages - 페이지 정보
5. layout_elements - 레이아웃 요소
6. text_contents - 텍스트 내용
7. ai_descriptions - AI 생성 설명
8. question_groups - 문제 그룹
9. question_elements - 문제 요소
10. text_versions - 텍스트 버전 관리
11. formatting_rules - 서식 규칙
12. combined_results - 통합 결과
"""

from sqlalchemy import (
    Column, Integer, String, Text, DateTime, Enum, Numeric,
    ForeignKey, Boolean, JSON, DECIMAL, Index
)
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from datetime import datetime
from .database import Base


# ============================================================================
# 1. Users - 사용자 정보
# ============================================================================
class User(Base):
    """사용자 정보 테이블"""
    __tablename__ = "users"
    
    user_id = Column(Integer, primary_key=True, autoincrement=True, comment="사용자 ID")
    username = Column(String(50), unique=True, nullable=False, comment="사용자명")
    email = Column(String(100), unique=True, nullable=False, comment="이메일")
    password_hash = Column(String(255), nullable=False, comment="비밀번호 해시")
    created_at = Column(DateTime, default=func.now(), comment="생성일시")
    last_login = Column(DateTime, nullable=True, comment="마지막 로그인")
    
    # 관계 설정
    projects = relationship("Project", back_populates="user", cascade="all, delete-orphan")
    
    def __repr__(self):
        return f"<User(id={self.user_id}, username='{self.username}')>"


# ============================================================================
# 2. Document Types - 문서 유형
# ============================================================================
class DocumentType(Base):
    """문서 유형 테이블 (worksheet/document)"""
    __tablename__ = "document_types"
    
    type_id = Column(Integer, primary_key=True, autoincrement=True, comment="유형 ID")
    type_name = Column(
        Enum('worksheet', 'document', name='document_type_enum'),
        unique=True,
        nullable=False,
        comment="문서 유형"
    )
    description = Column(String(255), nullable=True, comment="유형 설명")
    
    # 관계 설정
    projects = relationship("Project", back_populates="document_type")
    
    def __repr__(self):
        return f"<DocumentType(id={self.type_id}, name='{self.type_name}')>"


# ============================================================================
# 3. Projects - 프로젝트 (문서 단위)
# ============================================================================
class Project(Base):
    """프로젝트 테이블 (다중 페이지 문서)"""
    __tablename__ = "projects"
    
    project_id = Column(Integer, primary_key=True, autoincrement=True, comment="프로젝트 ID")
    user_id = Column(Integer, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, comment="사용자 ID")
    project_name = Column(String(255), nullable=False, comment="프로젝트명")
    type_id = Column(Integer, ForeignKey("document_types.type_id", ondelete="SET NULL"), nullable=True, comment="문서 유형 ID")
    created_at = Column(DateTime, default=func.now(), comment="생성일시")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="수정일시")
    
    # 관계 설정
    user = relationship("User", back_populates="projects")
    document_type = relationship("DocumentType", back_populates="projects")
    pages = relationship("Page", back_populates="project", cascade="all, delete-orphan")
    
    # 인덱스
    __table_args__ = (
        Index("idx_user_created", "user_id", "created_at"),
    )
    
    def __repr__(self):
        return f"<Project(id={self.project_id}, name='{self.project_name}')>"


# ============================================================================
# 4. Pages - 페이지 정보
# ============================================================================
class Page(Base):
    """페이지 정보 테이블"""
    __tablename__ = "pages"
    
    page_id = Column(Integer, primary_key=True, autoincrement=True, comment="페이지 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), nullable=False, comment="프로젝트 ID")
    page_number = Column(Integer, nullable=False, comment="페이지 번호")
    image_path = Column(String(512), nullable=True, comment="이미지 경로")
    image_width = Column(Integer, nullable=True, comment="이미지 너비")
    image_height = Column(Integer, nullable=True, comment="이미지 높이")
    uploaded_at = Column(DateTime, default=func.now(), comment="업로드일시")
    
    # 관계 설정
    project = relationship("Project", back_populates="pages")
    layout_elements = relationship("LayoutElement", back_populates="page", cascade="all, delete-orphan")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        Index("idx_project_page", "project_id", "page_number", unique=True),
    )
    
    def __repr__(self):
        return f"<Page(id={self.page_id}, project={self.project_id}, page_num={self.page_number})>"


# ============================================================================
# 5. Layout Elements - 레이아웃 요소
# ============================================================================
class LayoutElement(Base):
    """레이아웃 요소 테이블 (DocLayout-YOLO 감지 결과)"""
    __tablename__ = "layout_elements"
    
    element_id = Column(Integer, primary_key=True, autoincrement=True, comment="요소 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="페이지 ID")
    element_type = Column(
        Enum('text', 'title', 'figure', 'figure_caption', 'table', 'table_caption', 
             'header', 'footer', 'reference', 'equation', name='element_type_enum'),
        nullable=False,
        comment="요소 유형"
    )
    x_min = Column(Integer, nullable=False, comment="좌측 상단 X 좌표")
    y_min = Column(Integer, nullable=False, comment="좌측 상단 Y 좌표")
    x_max = Column(Integer, nullable=False, comment="우측 하단 X 좌표")
    y_max = Column(Integer, nullable=False, comment="우측 하단 Y 좌표")
    confidence = Column(DECIMAL(5, 4), nullable=True, comment="신뢰도")
    area = Column(Integer, nullable=True, comment="영역 크기 (자동 계산)")
    x_position = Column(Integer, nullable=True, comment="X 중심 좌표 (자동 계산)")
    y_position = Column(Integer, nullable=True, comment="Y 중심 좌표 (자동 계산)")
    
    # 관계 설정
    page = relationship("Page", back_populates="layout_elements")
    text_content = relationship("TextContent", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    ai_description = relationship("AIDescription", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    question_elements = relationship("QuestionElement", back_populates="layout_element", cascade="all, delete-orphan")
    
    # 인덱스
    __table_args__ = (
        Index("idx_page_element", "page_id", "element_id"),
    )
    
    def __repr__(self):
        return f"<LayoutElement(id={self.element_id}, type='{self.element_type}')>"


# ============================================================================
# 6. Text Contents - 텍스트 내용
# ============================================================================
class TextContent(Base):
    """텍스트 내용 테이블 (OCR 결과)"""
    __tablename__ = "text_contents"
    
    content_id = Column(Integer, primary_key=True, autoincrement=True, comment="내용 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="요소 ID")
    raw_text = Column(Text, nullable=True, comment="원본 OCR 텍스트")
    edited_text = Column(Text, nullable=True, comment="편집된 텍스트")
    version = Column(Integer, default=1, comment="버전 번호")
    last_edited_at = Column(DateTime, nullable=True, comment="마지막 편집일시")
    
    # 관계 설정
    layout_element = relationship("LayoutElement", back_populates="text_content")
    text_versions = relationship("TextVersion", back_populates="text_content", cascade="all, delete-orphan")
    
    def __repr__(self):
        return f"<TextContent(id={self.content_id}, element={self.element_id}, v={self.version})>"


# ============================================================================
# 7. AI Descriptions - AI 생성 설명
# ============================================================================
class AIDescription(Base):
    """AI 생성 설명 테이블 (figure/table 설명)"""
    __tablename__ = "ai_descriptions"
    
    description_id = Column(Integer, primary_key=True, autoincrement=True, comment="설명 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="요소 ID")
    description_text = Column(Text, nullable=True, comment="AI 생성 설명")
    model_name = Column(String(100), nullable=True, comment="사용된 AI 모델명")
    generated_at = Column(DateTime, default=func.now(), comment="생성일시")
    
    # 관계 설정
    layout_element = relationship("LayoutElement", back_populates="ai_description")
    
    def __repr__(self):
        return f"<AIDescription(id={self.description_id}, element={self.element_id})>"


# ============================================================================
# 8. Question Groups - 문제 그룹
# ============================================================================
class QuestionGroup(Base):
    """문제 그룹 테이블 (worksheet 전용)"""
    __tablename__ = "question_groups"
    
    group_id = Column(Integer, primary_key=True, autoincrement=True, comment="그룹 ID")
    group_number = Column(Integer, nullable=False, comment="문제 번호")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="페이지 ID")
    
    # 관계 설정
    question_elements = relationship("QuestionElement", back_populates="question_group", cascade="all, delete-orphan")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        Index("idx_page_group", "page_id", "group_number", unique=True),
    )
    
    def __repr__(self):
        return f"<QuestionGroup(id={self.group_id}, num={self.group_number})>"


# ============================================================================
# 9. Question Elements - 문제 요소
# ============================================================================
class QuestionElement(Base):
    """문제 요소 테이블 (worksheet 전용)"""
    __tablename__ = "question_elements"
    
    question_element_id = Column(Integer, primary_key=True, autoincrement=True, comment="문제 요소 ID")
    group_id = Column(Integer, ForeignKey("question_groups.group_id", ondelete="CASCADE"), nullable=False, comment="그룹 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), nullable=False, comment="요소 ID")
    element_order = Column(Integer, nullable=False, comment="요소 순서")
    
    # 관계 설정
    question_group = relationship("QuestionGroup", back_populates="question_elements")
    layout_element = relationship("LayoutElement", back_populates="question_elements")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        Index("idx_group_order", "group_id", "element_order"),
        Index("idx_element_unique", "element_id", unique=True),
    )
    
    def __repr__(self):
        return f"<QuestionElement(id={self.question_element_id}, group={self.group_id})>"


# ============================================================================
# 10. Text Versions - 텍스트 버전 관리
# ============================================================================
class TextVersion(Base):
    """텍스트 버전 관리 테이블"""
    __tablename__ = "text_versions"
    
    version_id = Column(Integer, primary_key=True, autoincrement=True, comment="버전 ID")
    content_id = Column(Integer, ForeignKey("text_contents.content_id", ondelete="CASCADE"), nullable=False, comment="내용 ID")
    version_number = Column(Integer, nullable=False, comment="버전 번호")
    version_text = Column(Text, nullable=False, comment="버전 텍스트")
    edited_at = Column(DateTime, default=func.now(), comment="편집일시")
    
    # 관계 설정
    text_content = relationship("TextContent", back_populates="text_versions")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        Index("idx_content_version", "content_id", "version_number", unique=True),
    )
    
    def __repr__(self):
        return f"<TextVersion(id={self.version_id}, content={self.content_id}, v={self.version_number})>"


# ============================================================================
# 11. Formatting Rules - 서식 규칙
# ============================================================================
class FormattingRule(Base):
    """서식 규칙 테이블"""
    __tablename__ = "formatting_rules"
    
    rule_id = Column(Integer, primary_key=True, autoincrement=True, comment="규칙 ID")
    element_type = Column(
        Enum('text', 'title', 'figure', 'figure_caption', 'table', 'table_caption', 
             'header', 'footer', 'reference', 'equation', name='element_type_enum'),
        nullable=False,
        comment="요소 유형"
    )
    font_name = Column(String(100), nullable=True, comment="폰트명")
    font_size = Column(Integer, nullable=True, comment="폰트 크기")
    font_bold = Column(Boolean, default=False, comment="굵게")
    font_italic = Column(Boolean, default=False, comment="기울임")
    font_underline = Column(Boolean, default=False, comment="밑줄")
    alignment = Column(
        Enum('left', 'center', 'right', 'justify', name='alignment_enum'),
        default='left',
        comment="정렬"
    )
    line_spacing = Column(DECIMAL(3, 1), nullable=True, comment="줄 간격")
    space_before = Column(Integer, nullable=True, comment="앞 여백 (pt)")
    space_after = Column(Integer, nullable=True, comment="뒤 여백 (pt)")
    
    # 인덱스
    __table_args__ = (
        Index("idx_element_type", "element_type", unique=True),
    )
    
    def __repr__(self):
        return f"<FormattingRule(id={self.rule_id}, type='{self.element_type}')>"


# ============================================================================
# 12. Combined Results - 통합 결과
# ============================================================================
class CombinedResult(Base):
    """통합 결과 테이블 (최종 문서 생성용)"""
    __tablename__ = "combined_results"
    
    result_id = Column(Integer, primary_key=True, autoincrement=True, comment="결과 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), nullable=False, comment="프로젝트 ID")
    combined_text = Column(Text, nullable=True, comment="통합 텍스트")
    output_format = Column(
        Enum('docx', 'pdf', 'txt', name='output_format_enum'),
        default='docx',
        comment="출력 형식"
    )
    file_path = Column(String(512), nullable=True, comment="파일 경로")
    generated_at = Column(DateTime, default=func.now(), comment="생성일시")
    
    # 인덱스
    __table_args__ = (
        Index("idx_project_generated", "project_id", "generated_at"),
    )
    
    def __repr__(self):
        return f"<CombinedResult(id={self.result_id}, project={self.project_id})>"


# ============================================================================
# 모델 초기화 순서 (참고용)
# ============================================================================
"""
외래 키 의존성 순서:
1. User (독립)
2. DocumentType (독립)
3. FormattingRule (독립)
4. Project (User, DocumentType 의존)
5. Page (Project 의존)
6. LayoutElement (Page 의존)
7. TextContent (LayoutElement 의존)
8. AIDescription (LayoutElement 의존)
9. QuestionGroup (Page 의존)
10. QuestionElement (QuestionGroup, LayoutElement 의존)
11. TextVersion (TextContent 의존)
12. CombinedResult (Project 의존)
"""
