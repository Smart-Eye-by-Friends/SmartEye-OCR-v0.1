"""
SmartEyeSsen Backend - SQLAlchemy ORM Models (erd_schema.sql v2.1 기준)
===========================================================================
12개 테이블에 대한 SQLAlchemy 모델 정의

주요 변경사항 (기존 Backend/app/models.py 대비):
1. LayoutElement: element_type(Enum) → class_name(String)
2. LayoutElement: x_min/y_min/x_max/y_max → bbox_x/bbox_y/bbox_width/bbox_height
3. QuestionGroup: group_number → anchor_element_id + start_y + end_y
4. QuestionElement: element_order → order_in_question
5. FormattingRule: doc_type_id FK 추가
"""

from sqlalchemy import (
    Column, Integer, String, Text, Float, DateTime, Enum,
    ForeignKey, Boolean, Index, Computed
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
    email = Column(String(255), unique=True, nullable=False, comment="이메일 (로그인 ID)")
    name = Column(String(100), nullable=False, comment="사용자 이름")
    role = Column(String(50), nullable=False, default='user', comment="역할 (admin/teacher/student/user)")

    # 보안 정보
    password_hash = Column(String(255), nullable=False, comment="bcrypt 해시된 비밀번호")
    api_key = Column(String(255), nullable=True, comment="OpenAI API 키 (AES-256 암호화)")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="계정 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 수정일")

    # 관계
    projects = relationship("Project", back_populates="user", cascade="all, delete-orphan")
    text_versions = relationship("TextVersion", back_populates="user")

    # 인덱스
    __table_args__ = (
        Index("idx_email", "email"),
        Index("idx_role", "role"),
    )

    def __repr__(self):
        return f"<User(id={self.user_id}, email='{self.email}')>"


# ============================================================================
# 2. Document Types - 문서 유형
# ============================================================================
class DocumentType(Base):
    """문서 유형 테이블 (worksheet/document)"""
    __tablename__ = "document_types"

    doc_type_id = Column(Integer, primary_key=True, autoincrement=True, comment="문서 타입 ID")
    type_name = Column(String(100), unique=True, nullable=False, comment="타입명 (worksheet/document)")

    # 처리 설정
    model_name = Column(String(100), nullable=False, comment="AI 모델명 (SmartEyeSsen/DocLayout-YOLO)")
    sorting_method = Column(
        Enum('question_based', 'reading_order', name='sorting_method_enum'),
        nullable=False,
        comment="정렬 방식: question_based(문제지, 앵커-자식), reading_order(일반문서, Y/X)"
    )

    # 부가 정보
    description = Column(Text, nullable=True, comment="타입 설명")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="수정일")

    # 관계
    projects = relationship("Project", back_populates="document_type")
    formatting_rules = relationship("FormattingRule", back_populates="document_type", cascade="all, delete-orphan")

    # 인덱스
    __table_args__ = (
        Index("idx_type_name", "type_name"),
    )

    def __repr__(self):
        return f"<DocumentType(id={self.doc_type_id}, name='{self.type_name}')>"


# ============================================================================
# 3. Projects - 프로젝트 (문서 단위)
# ============================================================================
class Project(Base):
    """프로젝트 테이블 (다중 페이지 문서)"""
    __tablename__ = "projects"

    project_id = Column(Integer, primary_key=True, autoincrement=True, comment="프로젝트 ID")
    user_id = Column(Integer, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, comment="사용자 ID")
    doc_type_id = Column(Integer, ForeignKey("document_types.doc_type_id", ondelete="RESTRICT"), nullable=False, comment="문서 타입 ID")
    project_name = Column(String(255), nullable=False, comment="프로젝트 이름")

    # 진행 상태
    total_pages = Column(Integer, default=0, comment="총 페이지 수 (트리거로 자동 계산)")
    analysis_mode = Column(
        Enum('auto', 'manual', 'hybrid', name='analysis_mode_enum'),
        default='auto',
        comment="분석 모드"
    )
    status = Column(
        Enum('created', 'in_progress', 'completed', 'error', name='project_status_enum'),
        default='created',
        comment="프로젝트 상태"
    )

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="프로젝트 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 수정일")

    # 관계
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
        return f"<Project(id={self.project_id}, name='{self.project_name}')>"


# ============================================================================
# 4. Pages - 페이지 정보
# ============================================================================
class Page(Base):
    """페이지 정보 테이블"""
    __tablename__ = "pages"

    page_id = Column(Integer, primary_key=True, autoincrement=True, comment="페이지 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), nullable=False, comment="프로젝트 ID")
    page_number = Column(Integer, nullable=False, comment="페이지 번호 (1부터 시작)")

    # 이미지 정보
    image_path = Column(String(500), nullable=False, comment="이미지 파일 경로")
    image_width = Column(Integer, nullable=True, comment="이미지 너비 (픽셀)")
    image_height = Column(Integer, nullable=True, comment="이미지 높이 (픽셀)")

    # 분석 상태
    analysis_status = Column(
        Enum('pending', 'processing', 'completed', 'error', name='analysis_status_enum'),
        default='pending',
        comment="분석 상태"
    )
    processing_time = Column(Float, nullable=True, comment="처리 시간 (초)")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="페이지 추가일")
    analyzed_at = Column(DateTime, nullable=True, comment="분석 완료일")

    # 관계
    project = relationship("Project", back_populates="pages")
    layout_elements = relationship("LayoutElement", back_populates="page", cascade="all, delete-orphan")
    question_groups = relationship("QuestionGroup", back_populates="page", cascade="all, delete-orphan")
    text_versions = relationship("TextVersion", back_populates="page", cascade="all, delete-orphan")

    # 인덱스 및 제약조건
    __table_args__ = (
        Index("uk_project_page", "project_id", "page_number", unique=True),
        Index("idx_project_id", "project_id"),
        Index("idx_analysis_status", "analysis_status"),
    )

    def __repr__(self):
        return f"<Page(id={self.page_id}, project={self.project_id}, page_num={self.page_number})>"


# ============================================================================
# 5. Layout Elements - 레이아웃 요소 ⭐ 핵심 변경
# ============================================================================
class LayoutElement(Base):
    """레이아웃 요소 테이블 (DocLayout-YOLO 감지 결과)"""
    __tablename__ = "layout_elements"

    element_id = Column(Integer, primary_key=True, autoincrement=True, comment="요소 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="페이지 ID")

    # 분류 정보 (✅ String으로 변경!)
    class_name = Column(String(100), nullable=False, comment="클래스명 (question_number/figure/table/text 등)")
    confidence = Column(Float, nullable=False, comment="신뢰도 (0.0~1.0)")

    # 바운딩 박스 좌표 (✅ YOLO 출력 형식!)
    bbox_x = Column(Integer, nullable=False, comment="X 좌표 (왼쪽 상단)")
    bbox_y = Column(Integer, nullable=False, comment="Y 좌표 (왼쪽 상단)")
    bbox_width = Column(Integer, nullable=False, comment="너비 (픽셀)")
    bbox_height = Column(Integer, nullable=False, comment="높이 (픽셀)")

    # 자동 계산 컬럼 (GENERATED COLUMN)
    area = Column(Integer, Computed("bbox_width * bbox_height"), comment="면적 (자동 계산)")
    y_position = Column(Integer, Computed("bbox_y"), comment="Y 정렬용 좌표")
    x_position = Column(Integer, Computed("bbox_x"), comment="X 정렬용 좌표")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")

    # 관계
    page = relationship("Page", back_populates="layout_elements")
    text_content = relationship("TextContent", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    ai_description = relationship("AIDescription", back_populates="layout_element", uselist=False, cascade="all, delete-orphan")
    question_elements = relationship("QuestionElement", back_populates="layout_element", cascade="all, delete-orphan")

    # 인덱스
    __table_args__ = (
        Index("idx_page_id", "page_id"),
        Index("idx_class_name", "class_name"),
        Index("idx_position", "page_id", "y_position", "x_position"),  # 정렬 최적화
    )

    def __repr__(self):
        return f"<LayoutElement(id={self.element_id}, class='{self.class_name}')>"


# ============================================================================
# 6. Text Contents - 텍스트 내용 (OCR 결과)
# ============================================================================
class TextContent(Base):
    """텍스트 내용 테이블 (OCR 결과)"""
    __tablename__ = "text_contents"

    text_id = Column(Integer, primary_key=True, autoincrement=True, comment="텍스트 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="요소 ID (1:1)")

    # OCR 결과
    ocr_text = Column(Text, nullable=False, comment="OCR 추출 텍스트")
    ocr_engine = Column(String(50), default='PaddleOCR', comment="사용한 OCR 엔진")
    ocr_confidence = Column(Float, nullable=True, comment="OCR 신뢰도 (0.0~1.0)")
    language = Column(String(10), default='ko', comment="언어 코드 (ko/en/ja/zh)")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")

    # 관계
    layout_element = relationship("LayoutElement", back_populates="text_content")

    # 인덱스
    __table_args__ = (
        Index("idx_language", "language"),
    )

    def __repr__(self):
        return f"<TextContent(id={self.text_id}, element={self.element_id})>"


# ============================================================================
# 7. AI Descriptions - AI 생성 설명
# ============================================================================
class AIDescription(Base):
    """AI 생성 설명 테이블 (figure/table 설명)"""
    __tablename__ = "ai_descriptions"

    ai_desc_id = Column(Integer, primary_key=True, autoincrement=True, comment="AI 설명 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), unique=True, nullable=False, comment="요소 ID (1:1)")

    # AI 생성 결과
    description = Column(Text, nullable=False, comment="AI가 생성한 설명 텍스트")
    ai_model = Column(String(100), default='gpt-4o-mini', comment="사용한 AI 모델명")
    prompt_used = Column(Text, nullable=True, comment="사용한 프롬프트 (디버깅용)")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")

    # 관계
    layout_element = relationship("LayoutElement", back_populates="ai_description")

    # 인덱스
    __table_args__ = (
        Index("idx_ai_model", "ai_model"),
    )

    def __repr__(self):
        return f"<AIDescription(id={self.ai_desc_id}, element={self.element_id})>"


# ============================================================================
# 8. Question Groups - 문제 그룹 ⭐ 핵심 변경
# ============================================================================
class QuestionGroup(Base):
    """문제 그룹 테이블 (worksheet 전용, 앵커 기반)"""
    __tablename__ = "question_groups"

    question_group_id = Column(Integer, primary_key=True, autoincrement=True, comment="문제 그룹 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="페이지 ID")

    # ✅ 앵커 요소 참조 (1:1 관계)
    anchor_element_id = Column(
        Integer,
        ForeignKey("layout_elements.element_id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
        comment="앵커 요소 ID (question_type, question_number 등)"
    )

    # Y좌표 범위 (앵커 Y ~ 다음 앵커 직전)
    start_y = Column(Integer, nullable=False, comment="문제 시작 Y좌표")
    end_y = Column(Integer, nullable=False, comment="문제 종료 Y좌표")

    # 통계 정보
    element_count = Column(Integer, default=0, comment="문제에 속한 요소 개수 (자식 요소 수)")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")

    # 관계
    page = relationship("Page", back_populates="question_groups")
    question_elements = relationship("QuestionElement", back_populates="question_group", cascade="all, delete-orphan")
    anchor_element = relationship("LayoutElement", foreign_keys=[anchor_element_id])

    # 인덱스
    __table_args__ = (
        Index("idx_page_id", "page_id"),
    )

    def __repr__(self):
        return f"<QuestionGroup(id={self.question_group_id}, anchor={self.anchor_element_id})>"


# ============================================================================
# 9. Question Elements - 문제 요소 ⭐ 핵심 변경
# ============================================================================
class QuestionElement(Base):
    """문제 요소 테이블 (worksheet 전용, 자식 요소 + 정렬 순서)"""
    __tablename__ = "question_elements"

    qe_id = Column(Integer, primary_key=True, autoincrement=True, comment="문제 요소 ID")
    question_group_id = Column(Integer, ForeignKey("question_groups.question_group_id", ondelete="CASCADE"), nullable=False, comment="문제 그룹 ID")
    element_id = Column(Integer, ForeignKey("layout_elements.element_id", ondelete="CASCADE"), nullable=False, comment="요소 ID")

    # ✅ 정렬 순서 (sorter.py가 저장하는 핵심 필드!)
    order_in_question = Column(Integer, nullable=False, comment="문제 내 요소 순서 (1, 2, 3, ...) - Y좌표 기준 자동 정렬")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="생성일")

    # 관계
    question_group = relationship("QuestionGroup", back_populates="question_elements")
    layout_element = relationship("LayoutElement", back_populates="question_elements")

    # 인덱스
    __table_args__ = (
        Index("idx_order", "question_group_id", "order_in_question"),
        Index("uk_question_element", "question_group_id", "element_id", unique=True),
    )

    def __repr__(self):
        return f"<QuestionElement(id={self.qe_id}, group={self.question_group_id}, order={self.order_in_question})>"


# ============================================================================
# 10. Text Versions - 텍스트 버전 관리
# ============================================================================
class TextVersion(Base):
    """텍스트 버전 관리 테이블"""
    __tablename__ = "text_versions"

    version_id = Column(Integer, primary_key=True, autoincrement=True, comment="버전 ID")
    page_id = Column(Integer, ForeignKey("pages.page_id", ondelete="CASCADE"), nullable=False, comment="페이지 ID")
    user_id = Column(Integer, ForeignKey("users.user_id", ondelete="SET NULL"), nullable=True, comment="수정한 사용자 ID")

    # 버전 정보
    content = Column(Text, nullable=False, comment="텍스트 내용")
    version_number = Column(Integer, nullable=False, comment="버전 번호 (1, 2, 3, ...)")
    version_type = Column(
        Enum('original', 'auto_formatted', 'user_edited', name='version_type_enum'),
        nullable=False,
        comment="버전 유형"
    )

    # 상태 플래그
    is_current = Column(Boolean, default=False, comment="현재 버전 여부")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="버전 생성일")

    # 관계
    page = relationship("Page", back_populates="text_versions")
    user = relationship("User", back_populates="text_versions")

    # 인덱스
    __table_args__ = (
        Index("uk_page_version", "page_id", "version_number", unique=True),
        Index("idx_page_id", "page_id"),
        Index("idx_is_current", "is_current"),
    )

    def __repr__(self):
        return f"<TextVersion(id={self.version_id}, page={self.page_id}, v={self.version_number})>"


# ============================================================================
# 11. Formatting Rules - 서식 규칙 ⭐ 핵심 변경
# ============================================================================
class FormattingRule(Base):
    """서식 규칙 테이블 (DB 선택적 오버라이드, 기본은 formatter_rules.py)"""
    __tablename__ = "formatting_rules"

    rule_id = Column(Integer, primary_key=True, autoincrement=True, comment="규칙 ID")
    doc_type_id = Column(Integer, ForeignKey("document_types.doc_type_id", ondelete="CASCADE"), nullable=False, comment="문서 타입 ID")
    class_name = Column(String(100), nullable=False, comment="적용 클래스명 (question_number/figure/text 등)")

    # 포맷팅 설정
    prefix = Column(String(50), default='', comment="접두사 (예: '\\n\\n', '   ')")
    suffix = Column(String(50), default='', comment="접미사 (예: '. ', '\\n')")
    indent_level = Column(Integer, default=0, comment="들여쓰기 레벨 (0~10)")

    # 스타일 설정 (선택 사항)
    font_size = Column(String(20), nullable=True, comment="폰트 크기 (예: '14pt')")
    font_weight = Column(String(20), nullable=True, comment="폰트 두께 (예: 'bold')")

    # 타임스탬프
    created_at = Column(DateTime, default=func.now(), comment="규칙 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="규칙 수정일")

    # 관계
    document_type = relationship("DocumentType", back_populates="formatting_rules")

    # 인덱스
    __table_args__ = (
        Index("uk_type_class", "doc_type_id", "class_name", unique=True),
        Index("idx_doc_type_id", "doc_type_id"),
    )

    def __repr__(self):
        return f"<FormattingRule(id={self.rule_id}, class='{self.class_name}')>"


# ============================================================================
# 12. Combined Results - 통합 결과
# ============================================================================
class CombinedResult(Base):
    """통합 결과 테이블 (최종 문서 생성용)"""
    __tablename__ = "combined_results"

    combined_id = Column(Integer, primary_key=True, autoincrement=True, comment="통합 결과 ID")
    project_id = Column(Integer, ForeignKey("projects.project_id", ondelete="CASCADE"), unique=True, nullable=False, comment="프로젝트 ID (1:1)")

    # 통합 결과
    combined_text = Column(Text, nullable=False, comment="통합된 전체 텍스트 (페이지별 결과 합침)")
    combined_stats = Column(Text, nullable=True, comment="통계 정보 (JSON 형식: 페이지수, 단어수, 문제수 등)")

    # 타임스탬프
    generated_at = Column(DateTime, default=func.now(), comment="최초 생성일")
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), comment="마지막 업데이트일")

    # 관계
    project = relationship("Project", back_populates="combined_result")

    # 인덱스
    __table_args__ = (
        Index("idx_project_id", "project_id"),
    )

    def __repr__(self):
        return f"<CombinedResult(id={self.combined_id}, project={self.project_id})>"
