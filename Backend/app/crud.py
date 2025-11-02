"""
SmartEyeSsen Backend - CRUD Helper Functions
=============================================
데이터베이스 CRUD 작업을 위한 헬퍼 함수

주요 기능:
- Create: 새 레코드 생성
- Read: 단일/다중 레코드 조회
- Update: 기존 레코드 수정
- Delete: 레코드 삭제
"""

from sqlalchemy.orm import Session, joinedload
from sqlalchemy import desc, asc
from typing import Optional, List, Type, TypeVar
from . import models, schemas
from passlib.context import CryptContext

# 비밀번호 해싱 설정
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# TypeVar for generic CRUD operations
ModelType = TypeVar("ModelType")
CreateSchemaType = TypeVar("CreateSchemaType")
UpdateSchemaType = TypeVar("UpdateSchemaType")


# ============================================================================
# 1. User CRUD
# ============================================================================
def get_user(db: Session, user_id: int) -> Optional[models.User]:
    """사용자 ID로 조회"""
    return db.query(models.User).filter(models.User.user_id == user_id).first()


def get_user_by_username(db: Session, username: str) -> Optional[models.User]:
    """사용자명으로 조회"""
    return db.query(models.User).filter(models.User.username == username).first()


def get_user_by_email(db: Session, email: str) -> Optional[models.User]:
    """이메일로 조회"""
    return db.query(models.User).filter(models.User.email == email).first()


def get_users(db: Session, skip: int = 0, limit: int = 100) -> List[models.User]:
    """사용자 목록 조회"""
    return db.query(models.User).offset(skip).limit(limit).all()


def create_user(db: Session, user: schemas.UserCreate) -> models.User:
    """사용자 생성"""
    hashed_password = pwd_context.hash(user.password)
    db_user = models.User(
        username=user.username,
        email=user.email,
        password_hash=hashed_password
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
    if "password" in update_data:
        update_data["password_hash"] = pwd_context.hash(update_data.pop("password"))
    
    for key, value in update_data.items():
        setattr(db_user, key, value)
    
    db.commit()
    db.refresh(db_user)
    return db_user


def delete_user(db: Session, user_id: int) -> bool:
    """사용자 삭제"""
    db_user = get_user(db, user_id)
    if not db_user:
        return False
    db.delete(db_user)
    db.commit()
    return True


# ============================================================================
# 2. DocumentType CRUD
# ============================================================================
def get_document_type(db: Session, type_id: int) -> Optional[models.DocumentType]:
    """문서 유형 ID로 조회"""
    return db.query(models.DocumentType).filter(models.DocumentType.type_id == type_id).first()


def get_document_type_by_name(db: Session, type_name: str) -> Optional[models.DocumentType]:
    """문서 유형명으로 조회"""
    return db.query(models.DocumentType).filter(models.DocumentType.type_name == type_name).first()


def get_document_types(db: Session) -> List[models.DocumentType]:
    """모든 문서 유형 조회"""
    return db.query(models.DocumentType).all()


def create_document_type(db: Session, doc_type: schemas.DocumentTypeCreate) -> models.DocumentType:
    """문서 유형 생성"""
    db_doc_type = models.DocumentType(**doc_type.model_dump())
    db.add(db_doc_type)
    db.commit()
    db.refresh(db_doc_type)
    return db_doc_type


# ============================================================================
# 3. Project CRUD
# ============================================================================
def get_project(db: Session, project_id: int) -> Optional[models.Project]:
    """프로젝트 ID로 조회"""
    return db.query(models.Project).filter(models.Project.project_id == project_id).first()


def get_project_with_pages(db: Session, project_id: int) -> Optional[models.Project]:
    """페이지 포함 프로젝트 조회"""
    return db.query(models.Project).options(
        joinedload(models.Project.pages)
    ).filter(models.Project.project_id == project_id).first()


def get_projects_by_user(
    db: Session,
    user_id: int,
    skip: int = 0,
    limit: int = 100
) -> List[models.Project]:
    """사용자별 프로젝트 목록 조회"""
    return db.query(models.Project).filter(
        models.Project.user_id == user_id
    ).order_by(desc(models.Project.created_at)).offset(skip).limit(limit).all()


def create_project(db: Session, project: schemas.ProjectCreate, user_id: int) -> models.Project:
    """프로젝트 생성"""
    db_project = models.Project(**project.model_dump(), user_id=user_id)
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


def delete_project(db: Session, project_id: int) -> bool:
    """프로젝트 삭제 (CASCADE로 관련 데이터 자동 삭제)"""
    db_project = get_project(db, project_id)
    if not db_project:
        return False
    db.delete(db_project)
    db.commit()
    return True


# ============================================================================
# 4. Page CRUD
# ============================================================================
def get_page(db: Session, page_id: int) -> Optional[models.Page]:
    """페이지 ID로 조회"""
    return db.query(models.Page).filter(models.Page.page_id == page_id).first()


def get_page_with_elements(db: Session, page_id: int) -> Optional[models.Page]:
    """레이아웃 요소 포함 페이지 조회"""
    return db.query(models.Page).options(
        joinedload(models.Page.layout_elements)
    ).filter(models.Page.page_id == page_id).first()


def get_pages_by_project(db: Session, project_id: int) -> List[models.Page]:
    """프로젝트별 페이지 목록 조회 (페이지 번호 순)"""
    return db.query(models.Page).filter(
        models.Page.project_id == project_id
    ).order_by(asc(models.Page.page_number)).all()


def create_page(db: Session, page: schemas.PageCreate) -> models.Page:
    """페이지 생성"""
    db_page = models.Page(**page.model_dump())
    db.add(db_page)
    db.commit()
    db.refresh(db_page)
    return db_page


def update_page(db: Session, page_id: int, page_update: schemas.PageUpdate) -> Optional[models.Page]:
    """페이지 수정"""
    db_page = get_page(db, page_id)
    if not db_page:
        return None
    
    update_data = page_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_page, key, value)
    
    db.commit()
    db.refresh(db_page)
    return db_page


def delete_page(db: Session, page_id: int) -> bool:
    """페이지 삭제"""
    db_page = get_page(db, page_id)
    if not db_page:
        return False
    db.delete(db_page)
    db.commit()
    return True


# ============================================================================
# 5. LayoutElement CRUD
# ============================================================================
def get_layout_element(db: Session, element_id: int) -> Optional[models.LayoutElement]:
    """레이아웃 요소 ID로 조회"""
    return db.query(models.LayoutElement).filter(
        models.LayoutElement.element_id == element_id
    ).first()


def get_layout_element_with_content(db: Session, element_id: int) -> Optional[models.LayoutElement]:
    """텍스트 및 AI 설명 포함 레이아웃 요소 조회"""
    return db.query(models.LayoutElement).options(
        joinedload(models.LayoutElement.text_content),
        joinedload(models.LayoutElement.ai_description)
    ).filter(models.LayoutElement.element_id == element_id).first()


def get_layout_elements_by_page(db: Session, page_id: int) -> List[models.LayoutElement]:
    """페이지별 레이아웃 요소 목록 조회 (Y 좌표 순)"""
    return db.query(models.LayoutElement).filter(
        models.LayoutElement.page_id == page_id
    ).order_by(asc(models.LayoutElement.y_position)).all()


def create_layout_element(db: Session, element: schemas.LayoutElementCreate) -> models.LayoutElement:
    """레이아웃 요소 생성"""
    db_element = models.LayoutElement(**element.model_dump())
    db.add(db_element)
    db.commit()
    db.refresh(db_element)
    return db_element


def create_layout_elements_bulk(
    db: Session,
    elements: List[schemas.LayoutElementCreate]
) -> List[models.LayoutElement]:
    """레이아웃 요소 일괄 생성"""
    db_elements = [models.LayoutElement(**elem.model_dump()) for elem in elements]
    db.add_all(db_elements)
    db.commit()
    for elem in db_elements:
        db.refresh(elem)
    return db_elements


def update_layout_element(
    db: Session,
    element_id: int,
    element_update: schemas.LayoutElementUpdate
) -> Optional[models.LayoutElement]:
    """레이아웃 요소 수정"""
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
    """레이아웃 요소 삭제"""
    db_element = get_layout_element(db, element_id)
    if not db_element:
        return False
    db.delete(db_element)
    db.commit()
    return True


# ============================================================================
# 6. TextContent CRUD
# ============================================================================
def get_text_content(db: Session, content_id: int) -> Optional[models.TextContent]:
    """텍스트 내용 ID로 조회"""
    return db.query(models.TextContent).filter(
        models.TextContent.content_id == content_id
    ).first()


def get_text_content_by_element(db: Session, element_id: int) -> Optional[models.TextContent]:
    """요소 ID로 텍스트 내용 조회"""
    return db.query(models.TextContent).filter(
        models.TextContent.element_id == element_id
    ).first()


def create_text_content(db: Session, content: schemas.TextContentCreate) -> models.TextContent:
    """텍스트 내용 생성"""
    db_content = models.TextContent(**content.model_dump())
    db.add(db_content)
    db.commit()
    db.refresh(db_content)
    return db_content


def update_text_content(
    db: Session,
    content_id: int,
    content_update: schemas.TextContentUpdate
) -> Optional[models.TextContent]:
    """텍스트 내용 수정 (버전 자동 증가)"""
    db_content = get_text_content(db, content_id)
    if not db_content:
        return None
    
    # 이전 버전 저장
    if db_content.edited_text:
        version = models.TextVersion(
            content_id=content_id,
            version_number=db_content.version,
            version_text=db_content.edited_text
        )
        db.add(version)
    
    # 새 버전으로 업데이트
    update_data = content_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_content, key, value)
    
    db_content.version += 1
    from datetime import datetime
    db_content.last_edited_at = datetime.now()
    
    db.commit()
    db.refresh(db_content)
    return db_content


# ============================================================================
# 7. AIDescription CRUD
# ============================================================================
def get_ai_description(db: Session, description_id: int) -> Optional[models.AIDescription]:
    """AI 설명 ID로 조회"""
    return db.query(models.AIDescription).filter(
        models.AIDescription.description_id == description_id
    ).first()


def get_ai_description_by_element(db: Session, element_id: int) -> Optional[models.AIDescription]:
    """요소 ID로 AI 설명 조회"""
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


# ============================================================================
# 8. QuestionGroup CRUD
# ============================================================================
def get_question_group(db: Session, group_id: int) -> Optional[models.QuestionGroup]:
    """문제 그룹 ID로 조회"""
    return db.query(models.QuestionGroup).filter(
        models.QuestionGroup.group_id == group_id
    ).first()


def get_question_groups_by_page(db: Session, page_id: int) -> List[models.QuestionGroup]:
    """페이지별 문제 그룹 목록 조회"""
    return db.query(models.QuestionGroup).filter(
        models.QuestionGroup.page_id == page_id
    ).order_by(asc(models.QuestionGroup.group_number)).all()


def create_question_group(db: Session, group: schemas.QuestionGroupCreate) -> models.QuestionGroup:
    """문제 그룹 생성"""
    db_group = models.QuestionGroup(**group.model_dump())
    db.add(db_group)
    db.commit()
    db.refresh(db_group)
    return db_group


# ============================================================================
# 9. QuestionElement CRUD
# ============================================================================
def get_question_element(db: Session, question_element_id: int) -> Optional[models.QuestionElement]:
    """문제 요소 ID로 조회"""
    return db.query(models.QuestionElement).filter(
        models.QuestionElement.question_element_id == question_element_id
    ).first()


def get_question_elements_by_group(db: Session, group_id: int) -> List[models.QuestionElement]:
    """그룹별 문제 요소 목록 조회"""
    return db.query(models.QuestionElement).filter(
        models.QuestionElement.group_id == group_id
    ).order_by(asc(models.QuestionElement.element_order)).all()


def create_question_element(db: Session, element: schemas.QuestionElementCreate) -> models.QuestionElement:
    """문제 요소 생성"""
    db_element = models.QuestionElement(**element.model_dump())
    db.add(db_element)
    db.commit()
    db.refresh(db_element)
    return db_element


# ============================================================================
# 10. FormattingRule CRUD
# ============================================================================
def get_formatting_rule(db: Session, rule_id: int) -> Optional[models.FormattingRule]:
    """서식 규칙 ID로 조회"""
    return db.query(models.FormattingRule).filter(
        models.FormattingRule.rule_id == rule_id
    ).first()


def get_formatting_rule_by_type(db: Session, element_type: str) -> Optional[models.FormattingRule]:
    """요소 유형으로 서식 규칙 조회"""
    return db.query(models.FormattingRule).filter(
        models.FormattingRule.element_type == element_type
    ).first()


def get_all_formatting_rules(db: Session) -> List[models.FormattingRule]:
    """모든 서식 규칙 조회"""
    return db.query(models.FormattingRule).all()


def create_formatting_rule(db: Session, rule: schemas.FormattingRuleCreate) -> models.FormattingRule:
    """서식 규칙 생성"""
    db_rule = models.FormattingRule(**rule.model_dump())
    db.add(db_rule)
    db.commit()
    db.refresh(db_rule)
    return db_rule


def update_formatting_rule(
    db: Session,
    rule_id: int,
    rule_update: schemas.FormattingRuleUpdate
) -> Optional[models.FormattingRule]:
    """서식 규칙 수정"""
    db_rule = get_formatting_rule(db, rule_id)
    if not db_rule:
        return None
    
    update_data = rule_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_rule, key, value)
    
    db.commit()
    db.refresh(db_rule)
    return db_rule


# ============================================================================
# 11. CombinedResult CRUD
# ============================================================================
def get_combined_result(db: Session, result_id: int) -> Optional[models.CombinedResult]:
    """통합 결과 ID로 조회"""
    return db.query(models.CombinedResult).filter(
        models.CombinedResult.result_id == result_id
    ).first()


def get_combined_results_by_project(db: Session, project_id: int) -> List[models.CombinedResult]:
    """프로젝트별 통합 결과 목록 조회"""
    return db.query(models.CombinedResult).filter(
        models.CombinedResult.project_id == project_id
    ).order_by(desc(models.CombinedResult.generated_at)).all()


def create_combined_result(db: Session, result: schemas.CombinedResultCreate) -> models.CombinedResult:
    """통합 결과 생성"""
    db_result = models.CombinedResult(**result.model_dump())
    db.add(db_result)
    db.commit()
    db.refresh(db_result)
    return db_result


def delete_combined_result(db: Session, result_id: int) -> bool:
    """통합 결과 삭제"""
    db_result = get_combined_result(db, result_id)
    if not db_result:
        return False
    db.delete(db_result)
    db.commit()
    return True
