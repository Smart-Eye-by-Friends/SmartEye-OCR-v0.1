# SmartEyeSsen

> 시각장애 학습자를 위한 AI 기반 문서 분석 · 텍스트 변환 플랫폼  

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/downloads/)
[![React](https://img.shields.io/badge/react-19.1+-61dafb.svg)](https://react.dev/)
[![FastAPI](https://img.shields.io/badge/fastapi-0.104+-teal.svg)](https://fastapi.tiangolo.com/)

## 📚 목차

- [프로젝트 개요](#-프로젝트-개요)
- [저장소 구조](#-저장소-구조)
- [핵심 기능](#-핵심-기능)
- [프론트엔드 구성](#-프론트엔드-구성)
- [백엔드 구성](#-백엔드-구성)
- [Docker & 배포 전략](#-docker--배포-전략)
- [로컬 개발 & 테스트](#-로컬-개발--테스트)
- [주요 API 엔드포인트](#-주요-api-엔드포인트)
- [문서 & 참고 자료](#-문서--참고-자료)
- [기여 & 라이선스](#-기여--라이선스)
- [프로젝트 메타](#-프로젝트-메타)

---

## 🎯 프로젝트 개요

**SmartEyeSsen**은 PDF·이미지 기반 학습 자료를 업로드하면 AI 파이프라인이 레이아웃을 분석하고, 한국어 OCR 및 생성형 모델을 활용해 접근 가능한 텍스트 문서(DOCX 포함)로 변환하는 풀스택 서비스입니다.  
DigitalOcean Droplet 위에서 Docker Compose(`docker-compose.prod.yml`)로 프론트엔드/백엔드/DB/Certbot이 함께 구동되며, 운영 배포 기본 브랜치는 `main`입니다.

---

## 🗂 저장소 구조

```text
SmartEye-OCR-v0.1/
├── Backend/                # FastAPI 서비스, Dockerfile, requirements.txt
│   ├── app/                # main.py, routers/, services/, schemas.py 등
│   ├── scripts/            # DB 초기화 스크립트, 배포 유틸
│   ├── uploads/, static/   # 업로드/정적 파일 (docker 볼륨으로 연결)
│   └── README.md           # 세부 백엔드 문서
├── Frontend/               # React 19 + Vite 클라이언트, Nginx 설정
│   ├── src/components/     # UI 컴포넌트
│   ├── src/contexts/, hooks/, styles/
│   ├── Dockerfile, default.conf, nginx.conf
│   └── README.md           # 세부 프론트 문서
├── docker-compose.prod.yml # 프로덕션 Compose (mysql/backend/frontend/certbot)
└── 기타 문서 ( etc.)
```

---

## 🚀 핵심 기능

- **다중 페이지 문서 처리**: PDF·이미지 업로드, 자동 페이지 분할 및 진행 상태 추적
- **AI 레이아웃 분석**: DocLayout-YOLO를 활용한 블록 감지 + 위치기반 정렬
- **OCR**: Tesseract로 텍스트 추출, 신뢰도 저장
- **AI 설명 생성**: OpenAI Vision을 통한 그림/표/순서도 설명
- **지능형 정렬**: 문서 타입(문제지/일반)에 맞춘 정렬·포맷팅 규칙 25종 이상
- **버전 관리형 편집기**: Original / Auto Formatted / User Edited 버전 저장 및 복원
- **DOCX 다운로드**: 통합 텍스트를 Word로 변환 후 제공

---

## 🖥 프론트엔드 구성

### 기술 요약

- React 19.1, Vite 7, TypeScript 5.9, Zustand/Context 조합 상태 관리
- Axios 기반 API 모듈(`src/services/`)과 커스텀 훅(`src/hooks/`)을 분리
- 스타일은 CSS Modules + 전역 `styles/` 조합

### 주요 폴더

| 폴더 | 설명 |
|------|------|
| `src/components/` | 업로드, 페이지 리스트, 에디터, 정렬 UI 컴포넌트 |
| `src/contexts/` | 프로젝트/세션 상태 컨텍스트 |
| `src/hooks/` | 업로드, 폴링, 페이지 상태 훅 |
| `src/services/` | API 클라이언트 (Axios 인스턴스) |
| `src/types/` | DTO/응답 타입 정의 |
| `src/__tests__/` | Vitest + Testing Library 스펙 |

### 환경 변수 & 설정

- `.env` 대신 Vite 런타임 변수를 사용. Docker 빌드시 `VITE_API_BASE_URL` ARG가 주입되며 기본값은 `/api`.
- 로컬 개발 시 `Frontend/.env` 또는 `vite.config.ts`에서 `VITE_API_BASE_URL=http://localhost:8000/api` 등으로 지정.

### 로컬 실행 (개발 모드)

```bash
cd Frontend
npm install
npm run dev -- --host 0.0.0.0 --port 5173
```

테스트/빌드:

```bash
npm run lint
npm run test        # Vitest
npm run build       # dist/ 생성
```

### Docker 이미지

- `Frontend/Dockerfile`은 **node:18-alpine** 빌더 단계에서 `npm ci --legacy-peer-deps`로 의존성을 설치하고 `npm run build` 실행 후, **nginx:alpine**에 산출물을 복사합니다.
- `default.conf`는 `/api/` 요청을 백엔드 컨테이너로 프록시하고 `/uploads/`, `/docs`, `/health` 등을 라우팅합니다.
- Certbot HTTP-01 챌린지를 위해 `/var/www/certbot`를 마운트하고, HTTPS(443)와 HTTP(80)을 모두 리슨합니다.

---

## 🧠 백엔드 구성

### 기술 요약

- FastAPI + SQLAlchemy + Pydantic v2
- MySQL 8.0 (UTF8MB4), PyMySQL 드라이버, Alembic 기반 마이그레이션 준비
- DocLayout-YOLO, Tesseract OCR, OpenAI API를 묶은 서비스 레이어
- Gunicorn + UvicornWorker 로 프로덕션 서빙

### 모듈 구조 (`Backend/app/`)

| 파일/폴더 | 역할 |
|-----------|------|
| `main.py` | FastAPI 엔트리포인트, 라우터/미들웨어 등록 |
| `database.py` | 세션/엔진, MySQL 연결 설정 |
| `models.py` | SQLAlchemy ORM 엔티티 |
| `schemas.py` | Pydantic 스키마 |
| `crud.py` | DB 접근 함수 |
| `routers/` | 프로젝트, 페이지, 분석, 다운로드 등 API 엔드포인트 |
| `services/` | OCR, 레이아웃 분석, 정렬, AI 설명 생성 모듈 |

### 환경 변수

1. `Backend/.env.example`을 `.env`로 복사  
   ```bash
   cd Backend
   cp .env.example .env
   ```
2. `DB_HOST`, `DB_PASSWORD`, `OPENAI_API_KEY`, `UPLOAD_DIR` 등을 실제 값으로 교체  
3. Docker Compose는 `.env`를 로드하되 `DB_HOST=mysql`, `DB_PORT=3306`, `ENVIRONMENT=production`을 override 합니다.

### 로컬 실행

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r Backend/requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

추가 모델 의존성(예: DocLayout-YOLO) 설치는 `requirements.txt`에서 자동 처리됩니다.

### Docker 이미지 특징

- `Backend/Dockerfile`은 **python:3.9-slim** 기반 멀티 스테이지 이미지입니다.
- Builder 단계에서 Tesseract(ko/eng), OpenCV 의존 패키지, `doclayout-yolo` 및 `requirements.txt`를 설치하고 Runtime 단계로 복사합니다.
- Locale 설정(`ko_KR.UTF-8`) 및 `uploads`, `static`, `test_pipeline_outputs` 디렉터리 생성/권한 부여.
- 기본 CMD는
  ```bash
  gunicorn app.main:app \
    --workers 1 \
    --worker-class uvicorn.workers.UvicornWorker \
    --bind 0.0.0.0:8000 \
    --timeout 300
  ```
- Healthcheck는 `http://localhost:8000/health`를 폴링합니다.

### 데이터 & 업로드

- `Backend/uploads`, `Backend/static`은 호스트 볼륨으로 마운트되어 컨테이너 재시작 시에도 파일이 유지됩니다.
- MySQL 초기 스키마는 `Backend/scripts/init_db_complete.sql`이 `docker-entrypoint-initdb.d`에 read-only로 주입됩니다.

---

## 🐳 Docker & 배포 전략

### docker-compose.prod.yml 서비스 요약

| 서비스 | 이미지/컨텍스트 | 주요 포트 | 볼륨/환경 | 비고 |
|--------|----------------|-----------|-----------|------|
| `mysql` | `mysql:8.0` | 3306 | `mysql_data`, `Backend/scripts/init_db_complete.sql` | 헬스체크 후 백엔드가 의존 |
| `backend` | `./Backend` Dockerfile | 내부 8000 | `Backend/.env`, `./Backend/uploads`, `./Backend/static` | DB 호스트를 `mysql`로 override |
| `frontend` | `./Frontend` Dockerfile | 80, 443 | `./certbot/conf`, `./certbot/www` | Nginx가 `/api`를 backend로 프록시 |
| `certbot` | `certbot/certbot` | - | `./certbot/conf`, `./certbot/www` | 12시간마다 자동 갱신 루프 |

기본 네트워크는 `smarteyessen_network` (bridge), DB 데이터는 `mysql_data` 볼륨에 저장됩니다.

### 배포 브랜치 & 절차

- **운영 기본 브랜치: `main`**  
  DigitalOcean Droplet 또는 CI/CD 스크립트에서 아래 순서를 권장합니다.

```bash
ssh <user>@<droplet-ip>
cd /home/<user>/SmartEye-OCR-v0.1
git fetch origin --prune
git checkout main
git pull --ff-only origin main

# 최신 코드 기준으로 이미지 재빌드
docker compose -f docker-compose.prod.yml build --pull backend frontend
docker compose -f docker-compose.prod.yml up -d --force-recreate backend frontend
```

- MySQL 및 업로드 볼륨은 그대로 유지되므로 데이터 손실 없이 컨테이너만 교체됩니다.
- 배포 후
  ```bash
  docker compose -f docker-compose.prod.yml ps
  docker logs -f smarteyessen_backend
  docker logs -f smarteyessen_frontend
  ```
  로 상태를 확인하고, `https://smart-eye.live/health` 헬스체크가 200을 반환하는지 확인합니다.

### SSL & Certbot

- 최초 발급:
  ```bash
  docker compose -f docker-compose.prod.yml run --rm certbot certonly \
    --webroot --webroot-path=/var/www/certbot \
    --email admin@smart-eye.live --agree-tos --no-eff-email \
    -d smart-eye.live -d www.smart-eye.live
  ```
- 자동 갱신 컨테이너는 12시간 주기로 `certbot renew`를 실행하므로, `certbot` 서비스를 항상 `up -d` 상태로 유지하십시오.

### 유용한 명령어

```bash
# 전체 스택 기동
docker compose -f docker-compose.prod.yml up -d

# 로그
docker compose -f docker-compose.prod.yml logs -f backend

# 정리
docker compose -f docker-compose.prod.yml down         # 컨테이너만 중지
docker compose -f docker-compose.prod.yml down -v      # 볼륨까지 삭제 (주의)
```

---

## 🧪 로컬 개발 & 테스트

### 필수 도구

- Docker 20.10+, Docker Compose 2.x
- Python 3.9+, Node.js 18+, npm 9+
- (선택) OpenAI API Key

### 수동 실행 플로우

```bash
# 백엔드
python -m venv .venv && source .venv/bin/activate
pip install -r Backend/requirements.txt
uvicorn app.main:app --reload

# 프론트엔드 (다른 터미널)
cd Frontend
npm install
VITE_API_BASE_URL=http://localhost:8000/api npm run dev
```

### 테스트 & 품질

- **백엔드**: `pytest -c Project/pytest.ini [-m regression]`
- **프론트엔드**: `npm run test -- --coverage`, `npm run lint`
- **CI 준비**: `start_backend.sh`, `start_frontend.sh`는 의존성 체크 후 각각 uvicorn, Vite 서버를 실행하도록 작성되어 있습니다.

---

## 🔌 주요 API 엔드포인트

| 카테고리 | Method | Endpoint | 설명 |
|----------|--------|----------|------|
| 프로젝트 | `POST` | `/api/projects` | 프로젝트 생성 |
|          | `GET`  | `/api/projects` | 프로젝트 목록 |
|          | `GET`  | `/api/projects/{project_id}` | 상세 조회 |
|          | `PATCH`| `/api/projects/{project_id}` | 수정 |
|          | `DELETE`| `/api/projects/{project_id}` | 삭제 |
| 페이지   | `POST` | `/api/pages/upload` | PDF/이미지 업로드 |
|          | `GET`  | `/api/pages/{page_id}` | 페이지 상세 |
|          | `GET`  | `/api/pages/{page_id}/text` | OCR/편집본 조회 |
|          | `POST` | `/api/pages/{page_id}/text` | 텍스트 저장 |
| 분석     | `POST` | `/api/projects/{project_id}/analyze` | 전체 분석 실행 |
|          | `POST` | `/api/pages/{page_id}/analyze` | 단일 페이지 분석 |
| 결과     | `GET`  | `/api/projects/{project_id}/combined-text` | 통합 텍스트 |
|          | `POST` | `/api/projects/{project_id}/download` | DOCX 다운로드 |

전체 스펙은 `Backend/docs/Backend API 문서/` 디렉터리를 참고하세요.

---

## 📄 문서 & 참고 자료

- `AGENTS.md`: 레포 지침 및 작업 규칙
- `CODING_CONVENTIONS.md`: 커밋 메시지 및 스타일 가이드
- `Backend/docs/` & `Backend/scripts/DB/`: API 상세 문서, ERD, 초기화 SQL
- `Frontend/REACT_FRONTEND_ROADMAP.md`: UI 개선 로드맵
- `Project/tests/` + `Project/pytest.ini`: 회귀 테스트 설정
- `OpenMP_Duplicate_Library_Error_Guide.md`: 멀티스레드 설정 주의사항

---

## 🤝 기여 & 라이선스

- 이슈 또는 기능 제안은 GitHub Issues를 통해 남겨주세요.
- 작업 플로우:
  1. 레포지토리 Fork
  2. 브랜치 생성 `feat/<scope>` 혹은 `fix/<scope>`
  3. 커밋 메시지는 `<type>(<scope>): <subject>` 포맷(`CODING_CONVENTIONS.md` 참고)
  4. PR에 변경 요약, 테스트 결과, UI 변경 시 스크린샷 첨부
- 라이선스: [MIT License](LICENSE)

---

## 📌 프로젝트 메타

- **프로덕션 URL**: https://smart-eye.live
- **API 문서**: https://smart-eye.live/docs
- **ReDoc**: https://smart-eye.live/redoc
- **Health Check**: https://smart-eye.live/health
- **문의**: support@smart-eye.live
- **최종 업데이트**: 2025-11-10
- **버전**: 0.1.2