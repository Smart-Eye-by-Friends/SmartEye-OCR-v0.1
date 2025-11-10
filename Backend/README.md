# SmartEyeSsen Backend

> FastAPI · MySQL · DocLayout-YOLO 기반 AI 문서 분석 백엔드

## 📚 목차

- [프로젝트 개요](#-프로젝트-개요)
- [디렉터리 구조](#-디렉터리-구조)
- [실행 모드](#-실행-모드)
- [환경 변수](#-환경-변수)
- [데이터베이스 & Docker 구성](#-데이터베이스--docker-구성)
- [FastAPI 모듈 구성](#-fastapi-모듈-구성)
- [테스트 & 스크립트](#-테스트--스크립트)
- [자주 묻는 문제](#-자주-묻는-문제)
- [참고 자료](#-참고-자료)

---

## 🎯 프로젝트 개요

- PDF/이미지 업로드 → **DocLayout-YOLO + Tesseract**로 레이아웃과 텍스트를 추출하고, **OpenAI Vision**으로 도표·표 설명을 생성합니다.
- 결과물은 **SmartEye 정렬 규칙**을 거쳐 프로젝트/페이지/요소 단위로 저장되며, **DOCX** 다운로드까지 제공됩니다.
- 운영 환경은 DigitalOcean Droplet에서 `docker-compose.prod.yml`을 통해 **MySQL + Backend + Frontend + Certbot** 컨테이너로 배포됩니다.

---

## 🗂 디렉터리 구조

```
Backend/
├── app/
│   ├── main.py            # FastAPI 엔트리포인트
│   ├── database.py        # 세션/엔진, MySQL 연결
│   ├── models.py          # SQLAlchemy ORM
│   ├── schemas.py         # Pydantic v2 스키마
│   ├── crud.py            # DB 접근 헬퍼
│   ├── routers/           # 프로젝트/페이지/분석/다운로드 라우터
│   └── services/          # OCR·레이아웃·정렬·AI 설명 모듈
├── scripts/
│   ├── init_db_complete.sql   # 12개 테이블 + 초기 데이터
│   └── reset_db.sh (옵션)     # 개발용 초기화 스크립트
├── uploads/, static/          # 업로드/정적 결과 (컨테이너 볼륨 연결)
├── Dockerfile                 # 멀티 스테이지 프로덕션 이미지
├── docker-compose.yml         # 백엔드 단독 MySQL 컨테이너
├── requirements.txt           # Python 의존성
└── README.md                  # 본 문서
```

---

## ⚙ 실행 모드

### 1. 로컬 개발 (venv + Uvicorn)

```bash
cd Backend
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

필요 시 `OPENAI_API_KEY`를 `.env`에 설정하면 AI 설명 기능이 활성화됩니다.

### 2. 백엔드 전용 Docker Compose (MySQL 포함)

`Backend/docker-compose.yml`은 MySQL 8.0 컨테이너만 띄워 FastAPI를 로컬에서 실행할 때 사용합니다.

```bash
cd Backend
docker compose up -d                      # smart_mysql 컨테이너 시작 (기본 포트 3308→3306)
uvicorn app.main:app --reload
```

종료 또는 초기화:

```bash
docker compose down                       # 컨테이너만 종료
docker compose down -v                    # smart_mysql_data 볼륨까지 삭제 (⚠ 전체 데이터 삭제)
```

### 3. 프로덕션 Docker Compose (전체 스택)

루트 `docker-compose.prod.yml`의 `backend` 서비스는 다음과 같이 구성됩니다.

```yaml
backend:
  build:
    context: ./Backend
    dockerfile: Dockerfile
  env_file:
    - Backend/.env
  environment:
    DB_HOST: mysql
    DB_PORT: 3306
    ENVIRONMENT: production
  volumes:
    - ./Backend/uploads:/app/uploads
    - ./Backend/static:/app/static
  depends_on:
    mysql:
      condition: service_healthy
```

배포 시 서버에서:

```bash
git checkout main && git pull --ff-only origin main
docker compose -f docker-compose.prod.yml build backend
docker compose -f docker-compose.prod.yml up -d --force-recreate backend
```

---

## 🔐 환경 변수

`.env.example`을 기반으로 `.env`를 생성합니다.

| 변수 | 설명 | 비고 |
|------|------|------|
| `DB_HOST`, `DB_PORT` | MySQL 접속 정보 | Docker 사용 시 `mysql`/`3306`으로 자동 override |
| `DB_USER`, `DB_PASSWORD`, `DB_NAME` | DB 계정 | 초기 스크립트 기본값: root / change_this_password / smarteyessen_db |
| `DATABASE_URL` | SQLAlchemy 접속 URL | 변경 불필요 (템플릿 자동 조합) |
| `API_HOST`, `API_PORT` | FastAPI 서버 호스트/포트 | 기본 `0.0.0.0:8000` |
| `ENVIRONMENT` | `development` / `production` | Compose에서 `production`으로 강제 |
| `OPENAI_API_KEY` | 선택 항목 | 없으면 AI 설명 비활성화 |
| `UPLOAD_DIR`, `MAX_FILE_SIZE`, `ALLOWED_EXTENSIONS` | 업로드 설정 | 기본 100 MB, jpg/jpeg/png/pdf |
| `SECRET_KEY`, `ALGORITHM` | JWT/보안 옵션 | 필요 시 업데이트 |
| `USE_ADAPTIVE_SORTER`, `PDF_PROCESSOR_DPI` 등 | 파이프라인 동작 제어 | `.env.example` 참고 |

---

## 🐳 데이터베이스 & Docker 구성

### Backend/docker-compose.yml (로컬 MySQL)

| 항목 | 값 |
|------|----|
| 이미지 | `mysql:8.0` |
| 컨테이너 이름 | `smart_mysql` |
| 포트 | 호스트 `${MYSQL_PORT:-3308} → 3306` |
| 볼륨 | `smart_mysql_data:/var/lib/mysql` (Named Volume) |
| 초기화 | `./scripts/init_db_complete.sql` → `/docker-entrypoint-initdb.d/01_init.sql` |
| 문자셋 | `utf8mb4 / utf8mb4_unicode_ci` |
| 헬스체크 | `mysqladmin ping` (10초 간격, 5회 재시도) |

### Backend/Dockerfile (프로덕션 이미지)

1. **Builder 단계 (python:3.9-slim)**  
   - Tesseract(ko/en), OpenCV 의존 패키지 설치  
   - `pip install -r requirements.txt` + `doclayout-yolo`  
2. **Runtime 단계 (python:3.9-slim)**  
   - 런타임 패키지 설치 후 Builder에서 site-packages 복사  
   - `ko_KR.UTF-8` 로케일 생성  
   - `/app/uploads`, `/app/static`, `/app/test_pipeline_outputs` 생성 및 권한 부여  
   - Healthcheck: `requests.get('http://localhost:8000/health')`  
   - CMD: Gunicorn + UvicornWorker (1 worker, timeout 300초)

### DB 초기 스키마

- `scripts/init_db_complete.sql`이 12개 테이블(users, projects, pages, … combined_results)과 시드 데이터(document_types 2건, formatting_rules 25건)를 생성합니다.
- `combined_results.combined_text` 타입은 `LONGTEXT`로 4GB까지 저장할 수 있습니다.

---

## 🧠 FastAPI 모듈 구성

| 영역 | 설명 |
|------|------|
| `routers/projects.py` | 프로젝트 CRUD, 분석 트리거 |
| `routers/pages.py` | 페이지 업로드, 텍스트 버전 API |
| `routers/analyze.py` | DocLayout-YOLO 실행, Tesseract OCR, AI 설명 |
| `routers/download.py` | 통합 텍스트/WORD 생성 |
| `services/layout_service.py` | 모델 로딩, 레이아웃 후처리 |
| `services/ocr_service.py` | PDF 분리, 이미지 전처리, Tesseract 호출 |
| `services/sorter_service.py` | 문제지/일반 문서별 정렬 로직 |
| `services/ai_description_service.py` | OpenAI Vision 호출 및 캐싱 |

모든 라우터는 `app/main.py`에서 FastAPI 인스턴스에 등록되며, `database.SessionLocal` 의존성을 주입해 트랜잭션을 관리합니다.

---

## 🧪 테스트 & 스크립트

- **Pytest**: 루트에서 `pytest -c Project/pytest.ini` 실행 (회귀/통합 시 `-m regression` 사용)
- **start_backend.sh**: 의존성 체크 후 Uvicorn 실행 (루트 스크립트)
- **scripts/reset_db.sh**: 개발 DB 초기화 (데이터 전체 삭제)
- **api_server.py**: 레거시 단일 스크립트 실행(필요 시만 사용)

---

## 🚨 자주 묻는 문제

| 증상 | 해결 방법 |
|------|-----------|
| MySQL 컨테이너 헬스체크 실패 | `docker compose logs mysql` 확인, 포트 충돌 시 `MYSQL_PORT` 변경, `docker compose down -v`로 재생성 |
| `DataError: ... combined_text` | `scripts/init_db_complete.sql` 최신 버전 적용 후 `reset_db.sh` 실행 |
| Tesseract 언어 미탑재 | Dockerfile 이미지는 `tesseract-ocr-kor/eng`를 포함함. 로컬 수동 설치 시 `sudo apt install tesseract-ocr-kor` |
| OpenAI 오류 | `.env`의 `OPENAI_API_KEY` 확인, 요청 수 제한 시 `OPENAI_MAX_CONCURRENCY` 값 조정 |
| 업로드 파일 미저장 | 컨테이너 볼륨이 올바르게 마운트되었는지 (`./Backend/uploads:/app/uploads`) 확인 |

---

## 📎 참고 자료

- `../README.md` – 전체 시스템 개요 및 배포 전략
- `Backend/docs/Backend API 문서/` – 세부 API 스펙