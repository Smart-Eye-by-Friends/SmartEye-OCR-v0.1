"""
SmartEyeSsen Backend - CRUD Operations (v2)
=============================================
ERD v2 기준 12개 테이블 CRUD 함수 정의

최종 수정일: 2025-01-22 (v2)
models.py와 100% 호환
"""
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import desc, asc, and_, or_, func
from typing import Optional, List, Dict, Any
from datetime import datetime
from . import models, schemas
from passlib.context import CryptContext

# 비밀번호 암호화 설정
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# ============================================================================
# Utility Functions - 비밀번호 암호화
# ============================================================================
def verify_password(plain_password: str, hashed_password: str) -> bool:
    """비밀번호 검증"""
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """비밀번호 해싱"""
    return pwd_context.hash(password)

# ============================================================================
# 1. User CRUD - 사용자 관리
# ============================================================================
def get_user(db: Session, user_id: int) -> Optional[models.User]:
    """사용자 ID로 조회"""
    return db.query(models.User).filter(models.User.user_id == user_id).first()


def get_user_by_email(db: Session, email: str) -> Optional[models.User]:
    """이메일로 사용자 조회 (로그인용)"""
    return db.query(models.User).filter(models.User.email == email).first()


def get_users(
    db: Session,
    skip: int = 0,
    limit: int = 100,
    role: Optional[str] = None,
    search: Optional[str] = None
) -> List[models.User]:
    """사용자 목록 조회 (필터링, 검색, 페이징)"""
    query = db.query(models.User)
    
    # 역할 필터
    if role:
        query = query.filter(models.User.role == role)
    
    # 검색 (이름 또는 이메일)
    if search:
        query = query.filter(
            or_(
                models.User.name.ilike(f"%{search}%"),
                models.User.email.ilike(f"%{search}%")
            )
        )
    
    return query.order_by(desc(models.User.created_at)).offset(skip).limit(limit).all()


def create_user(db: Session, user: schemas.UserCreate) -> models.User:
    """사용자 생성"""
    hashed_password = get_password_hash(user.password)
    db_user = models.User(
        email=user.email,
        name=user.name,
        role=user.role if hasattr(user, 'role') else "user",
        password_hash=hashed_password,
        api_key=user.api_key if hasattr(user, 'api_key') else None
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user


def update_user(db: Session, user_id: int, user_update: schemas.UserUpdate) -> Optional[models.User]:
    """사용자 정보 수정"""
    db_user = get_user(db, user_id)
    if not db_user:
        return None
    
    update_data = user_update.model_dump(exclude_unset=True)
    
    # 비밀번호 변경 시 해싱
    if "password" in update_data:
        update_data["password_hash"] = get_password_hash(update_data.pop("password"))
    
    for key, value in update_data.items():
        setattr(db_user, key, value)
    
    db.commit()
    db.refresh(db_user)
    return db_user


def delete_user(db: Session, user_id: int) -> bool:
    """사용자 삭제 (CASCADE로 관련 데이터 자동 삭제)"""
    db_user = get_user(db, user_id)
    if not db_user:
        return False
    db.delete(db_user)
    db.commit()
    return True


def authenticate_user(db: Session, email: str, password: str) -> Optional[models.User]:
    """사용자 인증 (로그인)"""
    user = get_user_by_email(db, email)
    if not user:
        return None
    if not verify_password(password, user.password_hash):
        return None
    return user

# ============================================================================
# 2. DocumentType CRUD - 문서 타입 관리
# ============================================================================
def get_document_type(db: Session, doc_type_id: int) -> Optional[models.DocumentType]:
    """문서 타입 ID로 조회"""
    return db.query(models.DocumentType).filter(
        models.DocumentType.doc_type_id == doc_type_id
    ).first()


def get_document_type_by_name(db: Session, type_name: str) -> Optional[models.DocumentType]:
    """문서 타입명으로 조회"""
    return db.query(models.DocumentType).filter(
        models.DocumentType.type_name == type_name
    ).first()


def get_document_types(
    db: Session,
    sorting_method: Optional[models.SortingMethodEnum] = None
) -> List[models.DocumentType]:
    """문서 타입 목록 조회 (정렬 방식 필터)"""
    query = db.query(models.DocumentType)
    
    if sorting_method:
        query = query.filter(models.DocumentType.sorting_method == sorting_method)
    
    return query.order_by(asc(models.DocumentType.type_name)).all()


def create_document_type(db: Session, doc_type: schemas.DocumentTypeCreate) -> models.DocumentType:
    """문서 타입 생성"""
    db_doc_type = models.DocumentType(**doc_type.model_dump())
    db.add(db_doc_type)
    db.commit()
    db.refresh(db_doc_type)
    return db_doc_type


def update_document_type(
    db: Session,
    doc_type_id: int,
    doc_type_update: schemas.DocumentTypeUpdate
) -> Optional[models.DocumentType]:
    """문서 타입 수정"""
    db_doc_type = get_document_type(db, doc_type_id)
    if not db_doc_type:
        return None
    
    update_data = doc_type_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_doc_type, key, value)
    
    db.commit()
    db.refresh(db_doc_type)
    return db_doc_type


def delete_document_type(db: Session, doc_type_id: int) -> bool:
    """문서 타입 삭제"""
    db_doc_type = get_document_type(db, doc_type_id)
    if not db_doc_type:
        return False
    db.delete(db_doc_type)
    db.commit()
    return True

# ============================================================================
# 3. Project CRUD - 프로젝트 관리
# ============================================================================
def get_project(db: Session, project_id: int) -> Optional[models.Project]:
    """프로젝트 ID로 조회"""
    return db.query(models.Project).filter(
        models.Project.project_id == project_id
    ).first()


def get_project_with_pages(db: Session, project_id: int) -> Optional[models.Project]:
    """프로젝트 + 페이지 목록 조회 (JOIN)"""
    return db.query(models.Project).options(
        joinedload(models.Project.pages)
    ).filter(
        models.Project.project_id == project_id
    ).first()


def get_project_page_statuses(db: Session, project_id: int) -> List[Tuple[int, int, models.AnalysisStatusEnum]]:
    """프로젝트 페이지 ID, 번호, 상태만 조회"""
    rows = (
        db.query(
            models.Page.page_id,
            models.Page.page_number,
            models.Page.analysis_status,
        )
        .filter(models.Page.project_id == project_id)
        .order_by(asc(models.Page.page_number))
        .all()
    )
    return rows


def get_project_with_details(db: Session, project_id: int) -> Optional[models.Project]:
    """프로젝트 전체 정보 조회 (페이지, 문서타입, 통합결과)"""
    return db.query(models.Project).options(
        joinedload(models.Project.pages),
        joinedload(models.Project.document_type),
        joinedload(models.Project.combined_result)
    ).filter(
        models.Project.project_id == project_id
    ).first()


def get_projects_by_user(
    db: Session,
    user_id: int,
    skip: int = 0,
    limit: int = 100,
    status: Optional[models.ProjectStatusEnum] = None,
    doc_type_id: Optional[int] = None
) -> List[models.Project]:
    """사용자별 프로젝트 목록 조회"""
    query = db.query(models.Project).filter(models.Project.user_id == user_id)
    
    if status:
        query = query.filter(models.Project.status == status)
    
    if doc_type_id:
        query = query.filter(models.Project.doc_type_id == doc_type_id)
    
    return query.order_by(desc(models.Project.created_at)).offset(skip).limit(limit).all()


def create_project(db: Session, project: schemas.ProjectCreate, user_id: int) -> models.Project:
    """프로젝트 생성"""
    db_project = models.Project(
        **project.model_dump(),
        user_id=user_id,
        status=models.ProjectStatusEnum.CREATED
    )
    db.add(db_project)
    db.commit()
    db.refresh(db_project)
    return db_project


def update_project(
    db: Session,
    project_id: int,
    project_update: schemas.ProjectUpdate
) -> Optional[models.Project]:
    """프로젝트 수정"""
    db_project = get_project(db, project_id)
    if not db_project:
        return None
    
    update_data = project_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_project, key, value)
    
    db.commit()
    db.refresh(db_project)
    return db_project


def update_project_status(
    db: Session,
    project_id: int,
    status: models.ProjectStatusEnum
) -> Optional[models.Project]:
    """프로젝트 상태 업데이트"""
    db_project = get_project(db, project_id)
    if not db_project:
        return None
    
    db_project.status = status
    db.commit()
    db.refresh(db_project)
    return db_project


def delete_project(db: Session, project_id: int) -> bool:
    """프로젝트 삭제 (CASCADE로 모든 하위 데이터 삭제)"""
    db_project = get_project(db, project_id)
    if not db_project:
        return False
    db.delete(db_project)
    db.commit()
    return True


def get_project_statistics(db: Session, project_id: int) -> Optional[Dict[str, Any]]:
    """프로젝트 통계 정보 조회"""
    project = get_project(db, project_id)
    if not project:
        return None
    
    # 페이지 수 집계
    total_pages = db.query(func.count(models.Page.page_id)).filter(
        models.Page.project_id == project_id
    ).scalar()
    
    # 분석 완료 페이지 수
    completed_pages = db.query(func.count(models.Page.page_id)).filter(
        and_(
            models.Page.project_id == project_id,
            models.Page.analysis_status == models.AnalysisStatusEnum.COMPLETED
        )
    ).scalar()
    
    # 총 레이아웃 요소 수
    total_elements = db.query(func.count(models.LayoutElement.element_id)).join(
        models.Page
    ).filter(
        models.Page.project_id == project_id
    ).scalar()
    
    return {
        "project_id": project_id,
        "project_name": project.project_name,
        "total_pages": total_pages,
        "completed_pages": completed_pages,
        "pending_pages": total_pages - completed_pages,
        "total_elements": total_elements,
        "status": project.status.value,
        "created_at": project.created_at,
        "updated_at": project.updated_at
    }

# Page CRUD
def get_page(db: Session, page_id: int) -> Optional[models.Page]:
    return db.query(models.Page).filter(models.Page.page_id == page_id).first()

def get_page_with_elements(db: Session, page_id: int) -> Optional[models.Page]:
    return (
        db.query(models.Page)
        .options(
            joinedload(models.Page.layout_elements)
            .joinedload(models.LayoutElement.text_content),
            joinedload(models.Page.layout_elements)
            .joinedload(models.LayoutElement.ai_description),
        )
        .filter(models.Page.page_id == page_id)
        .first()
    )

def get_pages_by_project(db: Session, project_id: int, analysis_status: Optional[str] = None) -> List[models.Page]:
    query = db.query(models.Page).filter(models.Page.project_id == project_id)
    if analysis_status:
        query = query.filter(models.Page.analysis_status == analysis_status)
    return query.order_by(asc(models.Page.page_number)).all()

def create_page(db: Session, page: schemas.PageCreate) -> models.Page:
    db_page = models.Page(**page.model_dump(), analysis_status=models.AnalysisStatusEnum.PENDING)
    db.add(db_page)
    db.commit()
    db.refresh(db_page)
    return db_page

def update_page(db: Session, page_id: int, page_update: schemas.PageUpdate) -> Optional[models.Page]:
    db_page = get_page(db, page_id)
    if not db_page:
        return None
    update_data = page_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_page, key, value)
    db.commit()
    db.refresh(db_page)
    return db_page

def update_page_analysis_status(db: Session, page_id: int, status: models.AnalysisStatusEnum, processing_time: Optional[float] = None) -> Optional[models.Page]:
    db_page = get_page(db, page_id)
    if not db_page:
        return None
    db_page.analysis_status = status
    if processing_time is not None:
        db_page.processing_time = processing_time
    if status == models.AnalysisStatusEnum.COMPLETED:
        db_page.analyzed_at = datetime.now()
    db.commit()
    db.refresh(db_page)
    return db_page

def delete_page(db: Session, page_id: int) -> bool:
    db_page = get_page(db, page_id)
    if not db_page:
        return False
    db.delete(db_page)
    db.commit()
    return True

# LayoutElement CRUD
def get_layout_element(db: Session, element_id: int) -> Optional[models.LayoutElement]:
    return db.query(models.LayoutElement).filter(models.LayoutElement.element_id == element_id).first()

def get_layout_element_with_content(db: Session, element_id: int) -> Optional[models.LayoutElement]:
    return db.query(models.LayoutElement).options(joinedload(models.LayoutElement.text_content), joinedload(models.LayoutElement.ai_description)).filter(models.LayoutElement.element_id == element_id).first()

def get_layout_elements_by_page(db: Session, page_id: int, class_name: Optional[str] = None) -> List[models.LayoutElement]:
    query = db.query(models.LayoutElement).filter(models.LayoutElement.page_id == page_id)
    if class_name:
        query = query.filter(models.LayoutElement.class_name == class_name)
    return query.order_by(asc(models.LayoutElement.y_position), asc(models.LayoutElement.x_position)).all()

def create_layout_element(db: Session, element: schemas.LayoutElementCreate) -> models.LayoutElement:
    db_element = models.LayoutElement(**element.model_dump())
    db.add(db_element)
    db.commit()
    db.refresh(db_element)
    return db_element

def create_layout_elements_bulk(db: Session, elements: List[schemas.LayoutElementCreate]) -> List[models.LayoutElement]:
    db_elements = [models.LayoutElement(**elem.model_dump()) for elem in elements]
    db.add_all(db_elements)
    db.commit()
    for elem in db_elements:
        db.refresh(elem)
    return db_elements

def update_layout_element(db: Session, element_id: int, element_update: schemas.LayoutElementUpdate) -> Optional[models.LayoutElement]:
    db_element = get_layout_element(db, element_id)
    if not db_element:
        return None
    update_data = element_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_element, key, value)
    db.commit()
    db.refresh(db_element)
    return db_element

def delete_layout_element(db: Session, element_id: int) -> bool:
    db_element = get_layout_element(db, element_id)
    if not db_element:
        return False
    db.delete(db_element)
    db.commit()
    return True

# TextContent CRUD
def get_text_content(db: Session, text_id: int) -> Optional[models.TextContent]:
    return db.query(models.TextContent).filter(models.TextContent.text_id == text_id).first()

def get_text_content_by_element(db: Session, element_id: int) -> Optional[models.TextContent]:
    return db.query(models.TextContent).filter(models.TextContent.element_id == element_id).first()

def create_text_content(db: Session, content: schemas.TextContentCreate) -> models.TextContent:
    db_content = models.TextContent(**content.model_dump())
    db.add(db_content)
    db.commit()
    db.refresh(db_content)
    return db_content

def update_text_content(db: Session, text_id: int, content_update: schemas.TextContentUpdate) -> Optional[models.TextContent]:
    db_content = get_text_content(db, text_id)
    if not db_content:
        return None
    update_data = content_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_content, key, value)
    db.commit()
    db.refresh(db_content)
    return db_content

# ============================================================================
# 7. AIDescription CRUD - AI 설명 관리
# ============================================================================
def get_ai_description(db: Session, ai_desc_id: int) -> Optional[models.AIDescription]:
    """AI 설명 ID로 조회"""
    return db.query(models.AIDescription).filter(
        models.AIDescription.ai_desc_id == ai_desc_id
    ).first()


def get_ai_description_by_element(db: Session, element_id: int) -> Optional[models.AIDescription]:
    """레이아웃 요소별 AI 설명 조회"""
    return db.query(models.AIDescription).filter(
        models.AIDescription.element_id == element_id
    ).first()


def create_ai_description(db: Session, description: schemas.AIDescriptionCreate) -> models.AIDescription:
    """AI 설명 생성"""
    db_description = models.AIDescription(**description.model_dump())
    db.add(db_description)
    db.commit()
    db.refresh(db_description)
    return db_description


def update_ai_description(
    db: Session,
    ai_desc_id: int,
    description_update: schemas.AIDescriptionUpdate
) -> Optional[models.AIDescription]:
    """AI 설명 수정"""
    db_description = get_ai_description(db, ai_desc_id)
    if not db_description:
        return None
    
    update_data = description_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_description, key, value)
    
    db.commit()
    db.refresh(db_description)
    return db_description


def delete_ai_description(db: Session, ai_desc_id: int) -> bool:
    """AI 설명 삭제"""
    db_description = get_ai_description(db, ai_desc_id)
    if not db_description:
        return False
    db.delete(db_description)
    db.commit()
    return True

# QuestionGroup CRUD
def get_question_group(db: Session, question_group_id: int) -> Optional[models.QuestionGroup]:
    return db.query(models.QuestionGroup).filter(models.QuestionGroup.question_group_id == question_group_id).first()

def get_question_group_by_anchor(db: Session, anchor_element_id: int) -> Optional[models.QuestionGroup]:
    return db.query(models.QuestionGroup).filter(models.QuestionGroup.anchor_element_id == anchor_element_id).first()

def get_question_groups_by_page(db: Session, page_id: int) -> List[models.QuestionGroup]:
    return db.query(models.QuestionGroup).filter(models.QuestionGroup.page_id == page_id).order_by(asc(models.QuestionGroup.start_y)).all()

def create_question_group(db: Session, group: schemas.QuestionGroupCreate) -> models.QuestionGroup:
    db_group = models.QuestionGroup(**group.model_dump())
    db.add(db_group)
    db.commit()
    db.refresh(db_group)
    return db_group

def update_question_group(db: Session, question_group_id: int, group_update: schemas.QuestionGroupUpdate) -> Optional[models.QuestionGroup]:
    db_group = get_question_group(db, question_group_id)
    if not db_group:
        return None
    update_data = group_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_group, key, value)
    db.commit()
    db.refresh(db_group)
    return db_group

# QuestionElement CRUD
def get_question_element(db: Session, qe_id: int) -> Optional[models.QuestionElement]:
    return db.query(models.QuestionElement).filter(models.QuestionElement.qe_id == qe_id).first()

def get_question_elements_by_group(db: Session, question_group_id: int) -> List[models.QuestionElement]:
    return db.query(models.QuestionElement).filter(models.QuestionElement.question_group_id == question_group_id).order_by(asc(models.QuestionElement.order_in_question)).all()

def create_question_element(db: Session, element: schemas.QuestionElementCreate) -> models.QuestionElement:
    db_element = models.QuestionElement(**element.model_dump())
    db.add(db_element)
    db.commit()
    db.refresh(db_element)
    return db_element

def create_question_elements_bulk(db: Session, elements: List[schemas.QuestionElementCreate]) -> List[models.QuestionElement]:
    db_elements = [models.QuestionElement(**elem.model_dump()) for elem in elements]
    db.add_all(db_elements)
    db.commit()
    for elem in db_elements:
        db.refresh(elem)
    return db_elements

# ============================================================================
# 10. TextVersion CRUD - 텍스트 버전 관리
# ============================================================================
def get_text_version(db: Session, version_id: int) -> Optional[models.TextVersion]:
    """텍스트 버전 ID로 조회"""
    return db.query(models.TextVersion).filter(
        models.TextVersion.version_id == version_id
    ).first()


def get_text_versions_by_page(
    db: Session,
    page_id: int,
    current_only: bool = False,
    version_type: Optional[models.VersionTypeEnum] = None
) -> List[models.TextVersion]:
    """페이지별 텍스트 버전 목록 조회"""
    query = db.query(models.TextVersion).filter(models.TextVersion.page_id == page_id)
    
    if current_only:
        query = query.filter(models.TextVersion.is_current == True)
    
    if version_type:
        query = query.filter(models.TextVersion.version_type == version_type)
    
    return query.order_by(desc(models.TextVersion.version_number)).all()


def get_current_text_version(db: Session, page_id: int) -> Optional[models.TextVersion]:
    """페이지의 현재 활성 버전 조회"""
    return db.query(models.TextVersion).filter(
        and_(
            models.TextVersion.page_id == page_id,
            models.TextVersion.is_current == True
        )
    ).first()


def create_text_version(db: Session, version: schemas.TextVersionCreate) -> models.TextVersion:
    """텍스트 버전 생성 (is_current=True시 기존 버전 비활성화)"""
    # 새 버전이 현재 버전으로 설정되면 기존 현재 버전 해제
    if version.is_current:
        db.query(models.TextVersion).filter(
            and_(
                models.TextVersion.page_id == version.page_id,
                models.TextVersion.is_current == True
            )
        ).update({"is_current": False})
    
    db_version = models.TextVersion(**version.model_dump())
    db.add(db_version)
    db.commit()
    db.refresh(db_version)
    return db_version


def set_current_version(db: Session, version_id: int) -> Optional[models.TextVersion]:
    """특정 버전을 현재 버전으로 설정"""
    db_version = get_text_version(db, version_id)
    if not db_version:
        return None
    
    # 같은 페이지의 다른 현재 버전 해제
    db.query(models.TextVersion).filter(
        and_(
            models.TextVersion.page_id == db_version.page_id,
            models.TextVersion.is_current == True
        )
    ).update({"is_current": False})
    
    # 선택한 버전을 현재 버전으로 설정
    db_version.is_current = True
    db.commit()
    db.refresh(db_version)
    return db_version


def delete_text_version(db: Session, version_id: int) -> bool:
    """텍스트 버전 삭제 (현재 버전은 삭제 불가)"""
    db_version = get_text_version(db, version_id)
    if not db_version:
        return False
    
    # 현재 버전은 삭제 불가
    if db_version.is_current:
        return False
    
    db.delete(db_version)
    db.commit()
    return True

# FormattingRule CRUD
def get_formatting_rule(db: Session, rule_id: int) -> Optional[models.FormattingRule]:
    return db.query(models.FormattingRule).filter(models.FormattingRule.rule_id == rule_id).first()

def get_formatting_rule_by_class(db: Session, doc_type_id: int, class_name: str) -> Optional[models.FormattingRule]:
    return db.query(models.FormattingRule).filter(and_(models.FormattingRule.doc_type_id == doc_type_id, models.FormattingRule.class_name == class_name)).first()

def get_formatting_rules_by_doc_type(db: Session, doc_type_id: int) -> List[models.FormattingRule]:
    return db.query(models.FormattingRule).filter(models.FormattingRule.doc_type_id == doc_type_id).all()

def get_all_formatting_rules(db: Session) -> List[models.FormattingRule]:
    """모든 포맷팅 규칙 조회"""
    return db.query(models.FormattingRule).all()

def create_formatting_rule(db: Session, rule: schemas.FormattingRuleCreate) -> models.FormattingRule:
    db_rule = models.FormattingRule(**rule.model_dump())
    db.add(db_rule)
    db.commit()
    db.refresh(db_rule)
    return db_rule

def update_formatting_rule(db: Session, rule_id: int, rule_update: schemas.FormattingRuleUpdate) -> Optional[models.FormattingRule]:
    db_rule = get_formatting_rule(db, rule_id)
    if not db_rule:
        return None
    update_data = rule_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_rule, key, value)
    db.commit()
    db.refresh(db_rule)
    return db_rule

# CombinedResult CRUD
def get_combined_result(db: Session, combined_id: int) -> Optional[models.CombinedResult]:
    return db.query(models.CombinedResult).filter(models.CombinedResult.combined_id == combined_id).first()

def get_combined_result_by_project(db: Session, project_id: int) -> Optional[models.CombinedResult]:
    return db.query(models.CombinedResult).filter(models.CombinedResult.project_id == project_id).first()

def create_combined_result(db: Session, result: schemas.CombinedResultCreate) -> models.CombinedResult:
    db_result = models.CombinedResult(**result.model_dump())
    db.add(db_result)
    db.commit()
    db.refresh(db_result)
    return db_result

def update_combined_result(db: Session, combined_id: int, result_update: schemas.CombinedResultUpdate) -> Optional[models.CombinedResult]:
    db_result = get_combined_result(db, combined_id)
    if not db_result:
        return None
    update_data = result_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_result, key, value)
    db.commit()
    db.refresh(db_result)
    return db_result

def delete_combined_result(db: Session, combined_id: int) -> bool:
    db_result = get_combined_result(db, combined_id)
    if not db_result:
        return False
    db.delete(db_result)
    db.commit()
    return True
