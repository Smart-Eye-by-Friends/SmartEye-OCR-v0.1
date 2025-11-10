import sys
from pathlib import Path

import pytest
from dotenv import load_dotenv
from sqlalchemy import text

# ---------------------------------------------------------------------------
# 경로 및 환경 변수 설정
# ---------------------------------------------------------------------------
REPO_ROOT = Path(__file__).resolve().parents[3]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

load_dotenv(REPO_ROOT / "Backend" / ".env", override=False)

from Backend.app.database import Base, SessionLocal, engine  # noqa: E402


# ---------------------------------------------------------------------------
# 공용 헬퍼
# ---------------------------------------------------------------------------
def truncate_database(session) -> None:
    """외래키 제약을 고려하여 주요 테이블을 비워 테스트 간 간섭을 제거한다."""
    session.execute(text("SET FOREIGN_KEY_CHECKS=0"))
    for table in [
        "question_elements",
        "question_groups",
        "text_versions",
        "layout_elements",
        "pages",
        "projects",
        "document_types",
        "users",
    ]:
        session.execute(text(f"TRUNCATE TABLE {table}"))
    session.execute(text("SET FOREIGN_KEY_CHECKS=1"))
    session.commit()


# ---------------------------------------------------------------------------
# Pytest Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture(scope="session", autouse=True)
def ensure_schema() -> None:
    """테스트 세션 시작 시 스키마가 준비되어 있는지 확인한다."""
    Base.metadata.create_all(bind=engine)


@pytest.fixture(scope="function")
def real_db_session(ensure_schema):
    """
    실제 MySQL 세션을 제공한다.

    각 테스트 종료 후 세션을 닫고 변경 사항은 사전에 truncate_database를 호출하여 초기화한다.
    """
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture(scope="function")
def clean_db(real_db_session):
    """
    테스트 진입 전후로 DB를 깨끗하게 유지하기 위한 헬퍼.

    예:
        def test_something(clean_db, real_db_session):
            ...
    """
    truncate_database(real_db_session)
    try:
        yield real_db_session
    finally:
        truncate_database(real_db_session)
