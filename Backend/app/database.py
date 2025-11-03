"""
SmartEyeSsen Backend - Database Connection Configuration
=========================================================
SQLAlchemy 엔진, 세션 관리 및 Base 클래스 정의

주요 기능:
- MySQL 데이터베이스 연결 설정
- 세션 생성 및 의존성 주입
- 비동기 컨텍스트 매니저 지원
"""

from sqlalchemy import create_engine, event, text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from typing import Generator
import os
from dotenv import load_dotenv

# ============================================================================
# 환경 변수 로드
# ============================================================================
load_dotenv()

# ============================================================================
# 데이터베이스 설정
# ============================================================================
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "3306")
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DB_NAME = os.getenv("DB_NAME", "smarteyessen_db")

# MySQL 연결 URL 생성
# pymysql 드라이버 사용, charset=utf8mb4 설정
SQLALCHEMY_DATABASE_URL = (
    f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    f"?charset=utf8mb4"
)

# ============================================================================
# SQLAlchemy Engine 생성
# ============================================================================
engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    # 연결 풀 설정
    pool_size=10,  # 기본 연결 수
    max_overflow=20,  # 추가 가능한 최대 연결 수
    pool_pre_ping=True,  # 연결 유효성 자동 체크
    pool_recycle=3600,  # 1시간마다 연결 재생성
    echo=False,  # SQL 로그 출력 (개발 시 True로 변경 가능)
)

# ============================================================================
# SessionLocal 클래스 생성
# ============================================================================
SessionLocal = sessionmaker(
    autocommit=False,  # 자동 커밋 비활성화
    autoflush=False,  # 자동 플러시 비활성화
    bind=engine,
)

# ============================================================================
# Base 클래스 정의
# ============================================================================
Base = declarative_base()

# ============================================================================
# 데이터베이스 세션 의존성 함수
# ============================================================================
def get_db() -> Generator[Session, None, None]:
    """
    FastAPI 의존성 주입용 데이터베이스 세션 생성
    
    사용 예시:
    ```python
    @app.get("/users")
    def read_users(db: Session = Depends(get_db)):
        users = db.query(User).all()
        return users
    ```
    
    Yields:
        Session: SQLAlchemy 데이터베이스 세션
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# ============================================================================
# 데이터베이스 초기화 함수
# ============================================================================
def init_db():
    """
    데이터베이스 테이블 생성 (개발 환경용)
    
    주의: 운영 환경에서는 Alembic 마이그레이션 사용 권장
    """
    # models.py import하여 테이블 정의 로드
    from . import models  # 모든 모델 클래스를 Base.metadata에 등록
    
    # models.py에서 정의한 모든 테이블 생성
    Base.metadata.create_all(bind=engine)
    print("✅ Database tables created successfully!")


def drop_all_tables():
    """
    모든 테이블 삭제 (개발/테스트 환경용)
    
    ⚠️ 주의: 모든 데이터가 삭제됩니다!
    """
    # models.py import하여 테이블 정의 로드
    from . import models  # 모든 모델 클래스를 Base.metadata에 등록
    
    Base.metadata.drop_all(bind=engine)
    print("⚠️ All database tables dropped!")


# ============================================================================
# 데이터베이스 연결 테스트 함수
# ============================================================================
def test_connection():
    """
    데이터베이스 연결 테스트
    
    Returns:
        bool: 연결 성공 여부
    """
    try:
        # 간단한 쿼리 실행하여 연결 확인
        with engine.connect() as connection:
            result = connection.execute(text("SELECT 1"))
            print("✅ Database connection successful!")
            return True
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        return False


# ============================================================================
# SQLAlchemy Event Listeners (선택사항)
# ============================================================================
@event.listens_for(engine, "connect")
def set_sqlite_pragma(dbapi_conn, connection_record):
    """
    MySQL 연결 시 추가 설정
    - 타임존 설정
    - 문자셋 확인
    """
    cursor = dbapi_conn.cursor()
    # UTF-8 문자셋 강제 설정
    cursor.execute("SET NAMES utf8mb4")
    cursor.execute("SET CHARACTER SET utf8mb4")
    cursor.execute("SET character_set_connection=utf8mb4")
    cursor.close()


# ============================================================================
# 개발용 유틸리티
# ============================================================================
if __name__ == "__main__":
    """
    직접 실행 시 데이터베이스 연결 테스트
    
    실행 방법:
    ```bash
    python app/database.py
    ```
    """
    print("=" * 60)
    print("SmartEyeSsen Database Connection Test")
    print("=" * 60)
    print(f"Database URL: {SQLALCHEMY_DATABASE_URL.replace(DB_PASSWORD, '***')}")
    print("-" * 60)
    test_connection()
    print("=" * 60)
