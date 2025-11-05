"""
SmartEyeSsen Backend - SQLAlchemy ORM Models (v2)
================================================
ERD v2 기준 12개 테이블 SQLAlchemy 모델 정의

테이블 목록:
1. users - 사용자 정보
2. document_types - 문서 타입 정의 (worksheet/document)
3. projects - 프로젝트 (문서 단위)
4. pages - 페이지 정보
5. layout_elements - 레이아웃 요소
6. text_contents - OCR 결과
7. ai_descriptions - AI 생성 설명
8. question_groups - 문제 그룹 (v2: 앵커 요소 기반)
9. question_elements - 문제-요소 매핑
10. text_versions - 텍스트 버전 관리
11. formatting_rules - 포맷팅 규칙
12. combined_results - 통합 문서 캐시

최종 수정일: 2025-01-22 (v2)
주요 변경사항: ERD v2 기준 완전 재작성 (앵커/자식 개념 반영)
"""

from sqlalchemy import (
    Column, Integer, String, Text, DateTime, Enum, Float,
    ForeignKey, Boolean, JSON, Index, UniqueConstraint, Computed
)
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from datetime import datetime
from .database import Base
import enum


# ============================================================================
# Enums - 열거형 정의
# ============================================================================
class SortingMethodEnum(str, enum.Enum):
    """정렬 방식"""
    QUESTION_BASED = "question_based"  # 문제지: 앵커-자식 재귀
    READING_ORDER = "reading_order"     # 일반문서: Y/X 좌표


class AnalysisModeEnum(str, enum.Enum):
    """분석 모드"""
    AUTO = "auto"
    MANUAL = "manual"
    HYBRID = "hybrid"


class ProjectStatusEnum(str, enum.Enum):
    """프로젝트 상태"""
    CREATED = "created"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    ERROR = "error"


class AnalysisStatusEnum(str, enum.Enum):
    """분석 상태"""
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    ERROR = "error"


class VersionTypeEnum(str, enum.Enum):
    """버전 유형"""
    ORIGINAL = "original"
    AUTO_FORMATTED = "auto_formatted"
    USER_EDITED = "user_edited"


# ============================================================================
# 1. Users - 사용자 정보
# ============================================================================
class User(Base):
    """사용자 정보 테이블"""
    __tablename__ = "users"
    
    user_id = Column(Integer, primary_key=True, autoincrement=True, comment="사용자 고유 ID")
    email = Column(String(255), unique=True, nullable=False, comment="이메일 (로그인 ID)")
    name = Column(String(100), nullable=False, comment="사용자 이름")
    role = Column(String(50), nullable=False, default="user", comment="역할 (admin/teacher/student/user)")
    password_hash = Column(String(255), nullable=False, comment="bcrypt 해시된 비밀번호")
    api_key = Column(String(255), nullable=True, comment="OpenAI API 키 (AES-256 암호화 저장)")
    created_at = Column(DateTime, default=func.now(), comment="계정 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 수정일")
    
    # 관계 설정
    projects = relationship("Project", back_populates="user", cascade="all, delete-orphan")
    text_versions = relationship("TextVersion", back_populates="user")
    
    # 인덱스
    __table_args__ = (
        Index("idx_email", "email"),
        Index("idx_role", "role"),
    )
    
    def __repr__(self):
        return f"<User(user_id={self.user_id}, email='{self.email}', role='{self.role}')>"


# ============================================================================
# 2. Document Types - 문서 타입 정의
# ============================================================================
class DocumentType(Base):
    """문서 타입 정의 테이블 (worksheet/document)"""
    __tablename__ = "document_types"
    
    doc_type_id = Column(Integer, primary_key=True, autoincrement=True, comment="문서 타입 고유 ID")
    type_name = Column(String(100), unique=True, nullable=False, comment="타입명 (worksheet/document/form)")
    model_name = Column(String(100), nullable=False, comment="AI 모델명 (SmartEyeSsen/DocLayout-YOLO)")
    sorting_method = Column(
        Enum(SortingMethodEnum),
        nullable=False,
        comment="정렬 방식: question_based(문제지), reading_order(일반문서)"
    )
    description = Column(Text, nullable=True, comment="타입 설명")
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="수정일")
    
    # 관계 설정
    projects = relationship("Project", back_populates="document_type")
    formatting_rules = relationship("FormattingRule", back_populates="document_type", cascade="all, delete-orphan")
    
    # 인덱스
    __table_args__ = (
        Index("idx_type_name", "type_name"),
    )
    
    def __repr__(self):
        return f"<DocumentType(doc_type_id={self.doc_type_id}, type_name='{self.type_name}', sorting='{self.sorting_method.value}')>"


# ============================================================================
# 3. Projects - 프로젝트 (문서 단위)
# ============================================================================
class Project(Base):
    """프로젝트 테이블 (다중 페이지 문서)"""
    __tablename__ = "projects"
    
    project_id = Column(Integer, primary_key=True, autoincrement=True, comment="프로젝트 고유 ID")
    user_id = Column(Integer, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, comment="소유자 ID")
    doc_type_id = Column(Integer, ForeignKey("document_types.doc_type_id", ondelete="RESTRICT"), nullable=False, comment="문서 타입 ID")
    project_name = Column(String(255), nullable=False, comment="프로젝트 이름")
    total_pages = Column(Integer, default=0, comment="총 페이지 수 (트리거로 자동 계산)")
    analysis_mode = Column(
        Enum(AnalysisModeEnum),
        default=AnalysisModeEnum.AUTO,
        comment="분석 모드: auto/manual/hybrid"
    )
    status = Column(
        Enum(ProjectStatusEnum),
        default=ProjectStatusEnum.CREATED,
        comment="프로젝트 상태"
    )
    created_at = Column(DateTime, default=func.now(), comment="프로젝트 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 수정일")
    
    # 관계 설정
    user = relationship("User", back_populates="projects")
    document_type = relationship("DocumentType", back_populates="projects")
    pages = relationship("Page", back_populates="project", cascade="all, delete-orphan")
    combined_result = relationship("CombinedResult", back_populates="project", uselist=False, cascade="all, delete-orphan")
    
    # 인덱스
    __table_args__ = (
        Index("idx_user_id", "user_id"),
        Index("idx_doc_type_id", "doc_type_id"),
        Index("idx_status", "status"),
    )
    
    def __repr__(self):
        return f"<Project(project_id={self.project_id}, name='{self.project_name}', status='{self.status.value}')>"


# ============================================================================
# 4. Pages - 페이지 정보
# ============================================================================
class Page(Base):
    """페이지 정보 테이블"""
    __tablename__ = "pages"
    
    page_id = Column(Integer, primary_key=True, autoincrement=True, comment="페이지 고유 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), nullable=False, comment="소속 프로젝트 ID")
    page_number = Column(Integer, nullable=False, comment="페이지 번호 (1부터 시작)")
    image_path = Column(String(500), nullable=False, comment="이미지 파일 경로")
    image_width = Column(Integer, nullable=True, comment="이미지 너비 (픽셀)")
    image_height = Column(Integer, nullable=True, comment="이미지 높이 (픽셀)")
    analysis_status = Column(
        Enum(AnalysisStatusEnum),
        default=AnalysisStatusEnum.PENDING,
        comment="분석 상태"
    )
    processing_time = Column(Float, nullable=True, comment="처리 시간 (초)")
    created_at = Column(DateTime, default=func.now(), comment="페이지 추가일")
    analyzed_at = Column(DateTime, nullable=True, comment="분석 완료일")
    
    # 관계 설정
    project = relationship("Project", back_populates="pages")
    layout_elements = relationship("LayoutElement", back_populates="page", cascade="all, delete-orphan")
    question_groups = relationship("QuestionGroup", back_populates="page", cascade="all, delete-orphan")
    text_versions = relationship("TextVersion", back_populates="page", cascade="all, delete-orphan")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("project_id", "page_number", name="uk_project_page"),
        Index("idx_project_id", "project_id"),
        Index("idx_analysis_status", "analysis_status"),
    )
    
    def __repr__(self):
        return f"<Page(page_id={self.page_id}, project={self.project_id}, page_num={self.page_number}, status='{self.analysis_status.value}')>"


# ============================================================================
# 5. Layout Elements - 레이아웃 요소 (v2: order_index 삭제)
# ============================================================================
class LayoutElement(Base):
    """레이아웃 요소 테이블 (AI 모델 검출 결과)"""
    __tablename__ = "layout_elements"
    
    element_id = Column(Integer, primary_key=True, autoincrement=True, comment="요소 고유 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="소속 페이지 ID")
    class_name = Column(String(100), nullable=False, comment="클래스명 (question_number/figure/table/text 등)")
    confidence = Column(Float, nullable=False, comment="신뢰도 (0.0~1.0)")
    
    # 바운딩 박스 좌표
    bbox_x = Column(Integer, nullable=False, comment="X 좌표 (왼쪽 상단)")
    bbox_y = Column(Integer, nullable=False, comment="Y 좌표 (왼쪽 상단)")
    bbox_width = Column(Integer, nullable=False, comment="너비 (픽셀)")
    bbox_height = Column(Integer, nullable=False, comment="높이 (픽셀)")
    
    # 자동 계산 컬럼 (GENERATED COLUMN) - SQLAlchemy에서는 Computed 사용
    area = Column(Integer, Computed("bbox_width * bbox_height"), comment="면적 (자동 계산)")
    y_position = Column(Integer, Computed("bbox_y"), comment="Y 정렬용 좌표 (자동 계산)")
    x_position = Column(Integer, Computed("bbox_x"), comment="X 정렬용 좌표 (자동 계산)")
    
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    
    # 관계 설정
    page = relationship("Page", back_populates="layout_elements")
    text_content = relationship("TextContent", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    ai_description = relationship("AIDescription", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    question_group = relationship("QuestionGroup", back_populates="anchor_element", uselist=False, cascade="all, delete-orphan")
    question_elements = relationship("QuestionElement", back_populates="layout_element", cascade="all, delete-orphan")
    
    # 인덱스
    __table_args__ = (
        Index("idx_page_id", "page_id"),
        Index("idx_class_name", "class_name"),
        Index("idx_position", "page_id", "y_position", "x_position"),  # 복합 인덱스
    )
    
    def __repr__(self):
        return f"<LayoutElement(element_id={self.element_id}, class='{self.class_name}', conf={self.confidence:.3f})>"


# ============================================================================
# 6. Text Contents - OCR 결과
# ============================================================================
class TextContent(Base):
    """OCR 결과 테이블"""
    __tablename__ = "text_contents"
    
    text_id = Column(Integer, primary_key=True, autoincrement=True, comment="OCR 결과 고유 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="레이아웃 요소 ID (1:1 매핑)")
    ocr_text = Column(Text, nullable=False, comment="OCR 추출 텍스트")
    ocr_engine = Column(String(50), default="PaddleOCR", comment="사용한 OCR 엔진")
    ocr_confidence = Column(Float, nullable=True, comment="OCR 신뢰도 (0.0~1.0)")
    language = Column(String(10), default="ko", comment="언어 코드 (ko/en/ja/zh)")
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    
    # 관계 설정
    layout_element = relationship("LayoutElement", back_populates="text_content")
    
    # 인덱스
    __table_args__ = (
        UniqueConstraint("element_id", name="uk_element"),
        Index("idx_language", "language"),
        # FULLTEXT 인덱스는 MySQL 특정 기능이므로 생략 (필요시 Raw SQL로 추가)
    )
    
    def __repr__(self):
        return f"<TextContent(text_id={self.text_id}, element={self.element_id}, engine='{self.ocr_engine}')>"


# ============================================================================
# 7. AI Descriptions - AI 생성 설명
# ============================================================================
class AIDescription(Base):
    """AI 생성 설명 테이블 (figure/table 설명)"""
    __tablename__ = "ai_descriptions"
    
    ai_desc_id = Column(Integer, primary_key=True, autoincrement=True, comment="AI 설명 고유 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="레이아웃 요소 ID (1:1 매핑)")
    description = Column(Text, nullable=False, comment="AI가 생성한 설명 텍스트")
    ai_model = Column(String(100), default="gpt-4o-mini", comment="사용한 AI 모델명")
    prompt_used = Column(Text, nullable=True, comment="사용한 프롬프트 (디버깅용)")
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    
    # 관계 설정
    layout_element = relationship("LayoutElement", back_populates="ai_description")
    
    # 인덱스
    __table_args__ = (
        UniqueConstraint("element_id", name="uk_element"),
        Index("idx_ai_model", "ai_model"),
    )
    
    def __repr__(self):
        return f"<AIDescription(ai_desc_id={self.ai_desc_id}, element={self.element_id}, model='{self.ai_model}')>"


# ============================================================================
# 8. Question Groups - 문제 그룹 (v2: 앵커 요소 기반)
# ============================================================================
class QuestionGroup(Base):
    """문제 그룹 테이블 (앵커 요소 기준)"""
    __tablename__ = "question_groups"
    
    question_group_id = Column(Integer, primary_key=True, autoincrement=True, comment="문제 그룹 고유 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="소속 페이지 ID")
    anchor_element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="앵커 요소 ID (FK: layout_elements)")
    
    # Y좌표 범위
    start_y = Column(Integer, nullable=False, comment="문제 시작 Y좌표")
    end_y = Column(Integer, nullable=False, comment="문제 종료 Y좌표")
    
    # 통계 정보
    element_count = Column(Integer, default=0, comment="문제에 속한 요소 개수 (자식 요소 수)")
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    
    # 관계 설정
    page = relationship("Page", back_populates="question_groups")
    anchor_element = relationship("LayoutElement", back_populates="question_group")
    question_elements = relationship("QuestionElement", back_populates="question_group", cascade="all, delete-orphan")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("anchor_element_id", name="uk_anchor_element"),
        Index("idx_page_id", "page_id"),
    )
    
    def __repr__(self):
        return f"<QuestionGroup(group_id={self.question_group_id}, anchor={self.anchor_element_id}, Y={self.start_y}-{self.end_y})>"


# ============================================================================
# 9. Question Elements - 문제-요소 매핑
# ============================================================================
class QuestionElement(Base):
    """문제-요소 매핑 테이블 (자식 요소 관리)"""
    __tablename__ = "question_elements"
    
    qe_id = Column(Integer, primary_key=True, autoincrement=True, comment="매핑 레코드 고유 ID")
    question_group_id = Column(Integer, ForeignKey("question_groups.question_group_id", ondelete="CASCADE"), nullable=False, comment="문제 그룹 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), nullable=False, comment="자식 요소 ID")
    order_in_question = Column(Integer, nullable=False, comment="문제 내 요소 순서 (1, 2, 3, ...) - Y좌표 기준")
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    
    # 관계 설정
    question_group = relationship("QuestionGroup", back_populates="question_elements")
    layout_element = relationship("LayoutElement", back_populates="question_elements")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("question_group_id", "element_id", name="uk_question_element"),
        Index("idx_order", "question_group_id", "order_in_question"),
    )
    
    def __repr__(self):
        return f"<QuestionElement(qe_id={self.qe_id}, group={self.question_group_id}, element={self.element_id}, order={self.order_in_question})>"


# ============================================================================
# 10. Text Versions - 텍스트 버전 관리
# ============================================================================
class TextVersion(Base):
    """텍스트 버전 관리 테이블"""
    __tablename__ = "text_versions"
    
    version_id = Column(Integer, primary_key=True, autoincrement=True, comment="버전 고유 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="소속 페이지 ID")
    user_id = Column(Integer, ForeignKey("users.user_id", ondelete="SET NULL"), nullable=True, comment="수정한 사용자 ID (사용자 수정 시)")
    content = Column(Text, nullable=False, comment="텍스트 내용")
    version_number = Column(Integer, nullable=False, comment="버전 번호 (1, 2, 3, ...)")
    version_type = Column(
        Enum(VersionTypeEnum),
        nullable=False,
        comment="버전 유형: original/auto_formatted/user_edited"
    )
    is_current = Column(Boolean, default=False, comment="현재 버전 여부")
    created_at = Column(DateTime, default=func.now(), comment="버전 생성일")
    
    # 관계 설정
    page = relationship("Page", back_populates="text_versions")
    user = relationship("User", back_populates="text_versions")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("page_id", "version_number", name="uk_page_version"),
        Index("idx_page_id", "page_id"),
        Index("idx_is_current", "is_current"),
    )
    
    def __repr__(self):
        return f"<TextVersion(version_id={self.version_id}, page={self.page_id}, v={self.version_number}, type='{self.version_type.value}')>"


# ============================================================================
# 11. Formatting Rules - 포맷팅 규칙 (v2: 앵커/자식 클래스 규칙)
# ============================================================================
class FormattingRule(Base):
    """포맷팅 규칙 테이블"""
    __tablename__ = "formatting_rules"
    
    rule_id = Column(Integer, primary_key=True, autoincrement=True, comment="규칙 고유 ID")
    doc_type_id = Column(Integer, ForeignKey("document_types.doc_type_id", ondelete="CASCADE"), nullable=False, comment="문서 타입 ID")
    class_name = Column(String(100), nullable=False, comment="적용 클래스명 (question_number/figure/text 등)")
    
    # 포맷팅 설정
    prefix = Column(String(50), default="", comment="접두사 (예: '\\n\\n', '   ')")
    suffix = Column(String(50), default="", comment="접미사 (예: '. ', '\\n')")
    indent_level = Column(Integer, default=0, comment="들여쓰기 레벨 (0~10)")
    
    # 스타일 설정 (선택 사항)
    font_size = Column(String(20), nullable=True, comment="폰트 크기 (예: '14pt')")
    font_weight = Column(String(20), nullable=True, comment="폰트 두께 (예: 'bold')")
    
    created_at = Column(DateTime, default=func.now(), comment="규칙 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="규칙 수정일")
    
    # 관계 설정
    document_type = relationship("DocumentType", back_populates="formatting_rules")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("doc_type_id", "class_name", name="uk_type_class"),
        Index("idx_doc_type_id", "doc_type_id"),
    )
    
    def __repr__(self):
        return f"<FormattingRule(rule_id={self.rule_id}, doc_type={self.doc_type_id}, class='{self.class_name}')>"


# ============================================================================
# 12. Combined Results - 통합 문서 캐시
# ============================================================================
class CombinedResult(Base):
    """통합 문서 캐시 테이블"""
    __tablename__ = "combined_results"
    
    combined_id = Column(Integer, primary_key=True, autoincrement=True, comment="통합 결과 고유 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), unique=True, nullable=False, comment="프로젝트 ID (1:1 매핑)")
    combined_text = Column(Text(16777215), nullable=False, comment="통합된 전체 텍스트 (페이지별 결과 합침) - MEDIUMTEXT")
    combined_stats = Column(JSON, nullable=True, comment="통계 정보 (JSON 형식: 페이지수, 단어수, 문제수 등)")
    generated_at = Column(DateTime, default=func.now(), comment="최초 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 업데이트일")
    
    # 관계 설정
    project = relationship("Project", back_populates="combined_result")
    
    # 인덱스 및 제약조건
    __table_args__ = (
        UniqueConstraint("project_id", name="uk_project"),
        Index("idx_project_id", "project_id"),
    )
    
    def __repr__(self):
        return f"<CombinedResult(combined_id={self.combined_id}, project={self.project_id})>"


# ============================================================================
# 모델 초기화 순서 (참고용)
# ============================================================================
"""
외래 키 의존성 순서:
1. User (독립)
2. DocumentType (독립)
3. Project (User, DocumentType 의존)
4. Page (Project 의존)
5. LayoutElement (Page 의존)
6. TextContent (LayoutElement 의존)
7. AIDescription (LayoutElement 의존)
8. QuestionGroup (Page, LayoutElement 의존) - 앵커 관계
9. QuestionElement (QuestionGroup, LayoutElement 의존)
10. TextVersion (Page, User 의존)
11. FormattingRule (DocumentType 의존)
12. CombinedResult (Project 의존)
"""
