# SmartEyeSsen Backend

시각장애 학생을 위한 AI 기반 학습 자료 분석 시스템 - 백엔드 서버

## 🚀 빠른 시작

### 1. 환경 설정

```bash
# .env 파일 생성
cp .env.example .env

# .env 파일 편집 (DB 비밀번호 등 설정)
notepad .env
```

### 2. 가상환경 생성 및 활성화

```bash
# 가상환경 생성
python -m venv venv

# 가상환경 활성화 (Windows)
venv\Scripts\activate

# 가상환경 활성화 (Linux/Mac)
source venv/bin/activate
```

### 3. 의존성 설치

```bash
pip install -r requirements.txt
```

### 4. 데이터베이스 설정

MySQL 8.0 이상 필요:

```sql
-- MySQL에 데이터베이스 생성
CREATE DATABASE smarteyessen_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 권한 설정 (선택사항)
GRANT ALL PRIVILEGES ON smarteyessen_db.* TO 'your_user'@'localhost';
FLUSH PRIVILEGES;
```

### 5. 서버 실행

**방법 1: 배치 파일 사용 (Windows)**
```bash
start_server.bat
```

**방법 2: 직접 실행**
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**방법 3: Python 스크립트 실행**
```bash
python -m app.main
```

### 6. API 문서 확인

서버 실행 후 브라우저에서 접속:

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc
- **Health Check**: http://localhost:8000/health

---

## 📁 프로젝트 구조

```
Backend/
├── .env.example          # 환경 변수 템플릿
├── .env                  # 환경 변수 (git에서 제외)
├── requirements.txt      # Python 의존성
├── start_server.bat      # 서버 실행 스크립트 (Windows)
├── README.md            # 이 파일
│
├── app/                 # 메인 애플리케이션
│   ├── __init__.py     # 패키지 초기화
│   ├── main.py         # FastAPI 앱 설정
│   ├── database.py     # 데이터베이스 연결
│   ├── models.py       # SQLAlchemy ORM 모델
│   ├── schemas.py      # Pydantic 스키마
│   ├── crud.py         # CRUD 헬퍼 함수
│   │
│   └── routers/        # API 라우터
│       └── __init__.py
│
└── uploads/            # 업로드된 파일 저장
```

---

## 🗄️ 데이터베이스 스키마

### 12개 테이블 구조

| # | 테이블명 | 설명 |
|---|---------|------|
| 1 | `users` | 사용자 정보 |
| 2 | `document_types` | 문서 유형 (worksheet/document) |
| 3 | `projects` | 프로젝트 (다중 페이지 문서) |
| 4 | `pages` | 페이지 정보 |
| 5 | `layout_elements` | 레이아웃 요소 (DocLayout-YOLO) |
| 6 | `text_contents` | 텍스트 내용 (OCR 결과) |
| 7 | `ai_descriptions` | AI 생성 설명 (figure/table) |
| 8 | `question_groups` | 문제 그룹 (worksheet 전용) |
| 9 | `question_elements` | 문제 요소 (worksheet 전용) |
| 10 | `text_versions` | 텍스트 버전 관리 |
| 11 | `formatting_rules` | 서식 규칙 |
| 12 | `combined_results` | 통합 결과 (최종 문서) |

### 주요 관계

- **User (1) → (N) Project**: 사용자는 여러 프로젝트 소유
- **Project (1) → (N) Page**: 프로젝트는 여러 페이지 포함
- **Page (1) → (N) LayoutElement**: 페이지는 여러 레이아웃 요소 포함
- **LayoutElement (1) → (1) TextContent**: 1:1 관계
- **LayoutElement (1) → (1) AIDescription**: 1:1 관계
- **TextContent (1) → (N) TextVersion**: 버전 관리

---

## 🔧 API 엔드포인트 (Phase 2에서 추가 예정)

### 사용자 관리
- `POST /api/v1/users` - 사용자 생성
- `GET /api/v1/users/{user_id}` - 사용자 조회
- `PUT /api/v1/users/{user_id}` - 사용자 수정
- `DELETE /api/v1/users/{user_id}` - 사용자 삭제

### 프로젝트 관리
- `POST /api/v1/projects` - 프로젝트 생성
- `GET /api/v1/projects` - 프로젝트 목록
- `GET /api/v1/projects/{project_id}` - 프로젝트 상세
- `PUT /api/v1/projects/{project_id}` - 프로젝트 수정
- `DELETE /api/v1/projects/{project_id}` - 프로젝트 삭제

### 페이지 관리
- `POST /api/v1/pages` - 페이지 생성 (이미지 업로드)
- `GET /api/v1/pages/{page_id}` - 페이지 조회
- `DELETE /api/v1/pages/{page_id}` - 페이지 삭제

### 레이아웃 분석
- `POST /api/v1/analyze/layout` - 레이아웃 분석 (DocLayout-YOLO)
- `POST /api/v1/analyze/ocr` - OCR 실행 (PaddleOCR)
- `POST /api/v1/analyze/describe` - AI 설명 생성 (GPT-4o-mini)

### 텍스트 편집
- `PUT /api/v1/text/{content_id}` - 텍스트 수정
- `GET /api/v1/text/{content_id}/versions` - 버전 히스토리

### 문서 생성
- `POST /api/v1/export/docx` - DOCX 문서 생성
- `POST /api/v1/export/pdf` - PDF 문서 생성

---

## 🧪 개발 도구

### 데이터베이스 연결 테스트

```bash
python app/database.py
```

### 데이터베이스 마이그레이션 (Alembic)

```bash
# 초기화
alembic init alembic

# 마이그레이션 파일 생성
alembic revision --autogenerate -m "Initial migration"

# 마이그레이션 적용
alembic upgrade head

# 롤백
alembic downgrade -1
```

---

## 📝 환경 변수 설명

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `DB_HOST` | MySQL 호스트 | `localhost` |
| `DB_PORT` | MySQL 포트 | `3306` |
| `DB_USER` | MySQL 사용자명 | `root` |
| `DB_PASSWORD` | MySQL 비밀번호 | - |
| `DB_NAME` | 데이터베이스 이름 | `smarteyessen_db` |
| `API_HOST` | API 서버 호스트 | `0.0.0.0` |
| `API_PORT` | API 서버 포트 | `8000` |
| `CORS_ORIGINS` | CORS 허용 출처 | `http://localhost:3000` |
| `OPENAI_API_KEY` | OpenAI API 키 | - |
| `ENVIRONMENT` | 환경 (development/production) | `development` |

---

## 🐛 문제 해결

### 1. 데이터베이스 연결 실패

```bash
# MySQL 서비스 확인
net start MySQL80

# .env 파일의 DB 설정 확인
DB_PASSWORD=your_actual_password
```

### 2. 포트 충돌

```bash
# 다른 포트로 실행
uvicorn app.main:app --reload --port 8001
```

### 3. 의존성 설치 오류

```bash
# pip 업그레이드
python -m pip install --upgrade pip

# 캐시 삭제 후 재설치
pip cache purge
pip install -r requirements.txt
```

---

## 📚 참고 자료

- [FastAPI 공식 문서](https://fastapi.tiangolo.com/)
- [SQLAlchemy 공식 문서](https://www.sqlalchemy.org/)
- [Pydantic 공식 문서](https://docs.pydantic.dev/)
- [MySQL 8.0 문서](https://dev.mysql.com/doc/refman/8.0/en/)

---

## 📄 라이선스

MIT License

---

## 👥 개발팀

SmartEyeSsen Team - 시각장애 학생을 위한 AI 학습 도구

---

**Phase 1 완료**: 데이터베이스 및 백엔드 기반 구축 ✅
