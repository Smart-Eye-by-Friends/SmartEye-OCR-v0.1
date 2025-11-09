# SmartEyeSsen

> AI 기반 학습지/문서 분석 및 텍스트 변환 플랫폼

**시각장애 학생을 위한 접근 가능한 학습 자료 생성 시스템**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/downloads/)
[![React](https://img.shields.io/badge/react-19.1+-61dafb.svg)](https://react.dev/)
[![FastAPI](https://img.shields.io/badge/fastapi-0.104+-teal.svg)](https://fastapi.tiangolo.com/)

## 📖 목차

- [프로젝트 개요](#-프로젝트-개요)
- [핵심 기능](#-핵심-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [데이터베이스 구조](#-데이터베이스-구조)
- [시작하기](#-시작하기)
- [API 사용법](#-api-사용법)
- [배포 환경](#-배포-환경)
- [주요 API 엔드포인트](#-주요-api-엔드포인트)

---

## 🎯 프로젝트 개요

**SmartEyeSsen**은 PDF 또는 이미지 형태의 학습 자료를 AI 기반으로 분석하여 시각장애 학생이 접근 가능한 텍스트 형태로 변환하는 풀스택 웹 플랫폼입니다.

### 주요 특징

- 📄 **다중 페이지 문서 처리**: 이미지 및 PDF 업로드 지원, 자동 페이지 분할
- 🤖 **AI 레이아웃 분석**: DocLayout-YOLO 기반 자동 레이아웃 감지
- 🔍 **OCR**: Tesseract OCR 한국어 텍스트 인식
- 🖼️ **AI 설명 생성**: GPT-4-turbo를 활용한 도표/표/순서도 설명 자동 생성
- 📊 **지능형 정렬**: 문서 타입별(문제지/일반) 최적화된 정렬 알고리즘
- ✏️ **텍스트 편집**: 실시간 편집 및 버전 관리 (향후 리치 에디터 통합 예정)
- 📥 **통합 다운로드**: DOCX 형식 문서 다운로드

---

## 🚀 핵심 기능

### 1️⃣ 문서 타입별 분석

**문제지 모드 (Worksheet)**
- SmartEyeSsen 파인튜닝 모델 사용
- 문제 번호 기반 계층적 정렬
- 문제 구조 인식 (번호, 지문, 선택지, 그림)
- 문제별 그룹핑 및 순서 정렬

**일반 문서 모드 (Document)**
- DocLayout-YOLO 모델 사용
- 좌표 기반 읽기 순서 정렬
- 제목/소제목/본문 계층 구조 파악
- 그림/표 캡션 인식

### 2️⃣ AI 파이프라인

```
이미지/PDF 업로드
    ↓
📊 LAM (Layout Analysis Module)
    - DocLayout-YOLO 레이아웃 감지
    ↓
🔍 TSPM (Text & Scene Processing Module)
    - Tesseract OCR 텍스트 추출
    - OpenAI Vision API 그림/표 설명
    ↓
📝 CIM (Content Integration Module)
    - 지능형 정렬 (문서 타입별)
    - 자동 포맷팅 (25+ 규칙)
    - 통합 문서 생성
```

### 3️⃣ 텍스트 편집 및 버전 관리

- **원본 (Original)**: OCR 추출 원본 텍스트
- **자동 포맷팅 (Auto Formatted)**: AI 정렬 + 포맷팅 적용
- **사용자 편집 (User Edited)**: 사용자 수정본
- 버전별 이력 관리 및 복원 기능

---

## 🛠 기술 스택

### Frontend

| 기술 | 버전 | 용도 |
|------|------|------|
| **React** | 19.1+ | SPA 프레임워크 |
| **Vite** | 7.1+ | 빌드 도구 |
| **TypeScript** | 5.9+ | 타입 안정성 |
| **Axios** | 1.13+ | HTTP 클라이언트 |
| **Nginx** | Latest | 웹 서버 + Reverse Proxy |

### Backend

| 기술 | 버전 | 용도 |
|------|------|------|
| **FastAPI** | 0.104+ | REST API 프레임워크 |
| **SQLAlchemy** | 2.0+ | ORM |
| **MySQL** | 8.0 | 관계형 데이터베이스 |
| **PyMySQL** | 1.1+ | MySQL 드라이버 |
| **Pydantic** | 2.5+ | 데이터 검증 |
| **Uvicorn** | 0.24+ | ASGI 서버 |
| **Gunicorn** | 21.2+ | WSGI 서버 (프로덕션) |

### AI/ML

| 기술 | 버전 | 용도 |
|------|------|------|
| **DocLayout-YOLO** | 0.0.4 | 레이아웃 분석 |
| **Tesseract OCR** | 4.0+ | 텍스트 인식 (한국어/영어) |
| **PyTorch** | 2.0+ | 딥러닝 프레임워크 |
| **OpenAI API** | 1.3+ | GPT-4-turbo Vision |
| **OpenCV** | 4.5+ | 이미지 처리 |
| **PyMuPDF** | 1.23+ | PDF 처리 |

### DevOps

| 기술 | 용도 |
|------|------|
| **Docker** | 컨테이너화 |
| **Docker Compose** | 다중 컨테이너 오케스트레이션 |
| **Let's Encrypt** | SSL/TLS 인증서 |
| **Certbot** | 자동 인증서 갱신 |
| **DigitalOcean** | 클라우드 호스팅 |

---

## 🏗 시스템 아키텍처

### 프로덕션 환경 (3-Tier Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                   DigitalOcean Droplet                      │
│                    (8GB RAM, 4 vCPU)                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Nginx (Port 80, 443)                             │    │
│  │  - Frontend SPA 서빙                              │    │
│  │  - HTTPS (Let's Encrypt)                          │    │
│  │  - Reverse Proxy (/api → Backend)                │    │
│  └───────────────────────────────────────────────────┘    │
│                         ↓                                   │
│  ┌───────────────────────────────────────────────────┐    │
│  │  FastAPI Backend (Internal: 8000)                │    │
│  │  - Gunicorn (1 Worker)                           │    │
│  │  - REST API                                       │    │
│  │  - AI/ML Pipeline                                 │    │
│  └───────────────────────────────────────────────────┘    │
│                         ↓                                   │
│  ┌───────────────────────────────────────────────────┐    │
│  │  MySQL 8.0 (Port 3306)                           │    │
│  │  - UTF8MB4 Encoding                              │    │
│  │  - 12 Tables                                      │    │
│  └───────────────────────────────────────────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘

External Services:
- OpenAI API (GPT-4-turbo Vision)
- Hugging Face Hub (Model Download)
```

### Docker Compose 서비스 구성

```yaml
services:
  mysql:        # 데이터베이스
  backend:      # FastAPI 애플리케이션
  frontend:     # Nginx + React SPA
  certbot:      # SSL 인증서 자동 갱신
```

---

## 🗄 데이터베이스 구조

### ERD 개요 (12 Tables)

```
users (사용자)
  ↓
projects (프로젝트) ← document_types (문서 타입)
  ↓
pages (페이지)
  ↓
├─ layout_elements (레이아웃 요소)
│   ├─ text_contents (OCR 텍스트)
│   ├─ ai_descriptions (AI 설명)
│   └─ question_groups (문제 그룹)
│       └─ question_elements (문제-요소 매핑)
│
├─ text_versions (텍스트 버전 관리)
│
└─ formatting_rules (포맷팅 규칙)

combined_results (통합 결과 캐시)
```

### 주요 테이블

| 테이블 | 설명 | 주요 컬럼 |
|--------|------|-----------|
| `users` | 사용자 계정 | user_id, email, name, role |
| `projects` | 프로젝트 (문서 단위) | project_id, doc_type_id, status |
| `pages` | 페이지 정보 | page_id, image_path, analysis_status |
| `layout_elements` | 레이아웃 요소 | element_id, class_name, bbox |
| `text_contents` | OCR 텍스트 | text_id, ocr_text, confidence |
| `ai_descriptions` | AI 설명 | ai_desc_id, description, ai_model |
| `text_versions` | 텍스트 버전 | version_id, content, version_type |
| `combined_results` | 통합 문서 캐시 | combined_text (LONGTEXT) |

**상세 스키마**: `Backend/scripts/DB/final E-R Diagram.md` 참조

---

## 🚀 시작하기

### 사전 요구사항

- **Docker** 20.10+
- **Docker Compose** 2.0+
- **Git**
- **(선택) OpenAI API Key** - AI 설명 생성용

### 로컬 개발 환경 (Docker)

```bash
# 1. 저장소 클론
git clone https://github.com/your-org/SmartEye-OCR-v0.1.git
cd SmartEye-OCR-v0.1

# 2. Backend 환경 변수 설정
cd Backend
cp .env.example .env
nano .env  # DB 설정, OpenAI API Key 입력

# 3. Docker Compose로 전체 스택 실행
cd ..
docker compose -f docker-compose.prod.yml up --build -d

# 4. 서비스 확인
docker compose -f docker-compose.prod.yml ps
```

### 서비스 접속

- **Frontend**: http://localhost:80
- **Backend API 문서**: http://localhost:80/docs
- **Health Check**: http://localhost:80/health

### 로그 확인

```bash
# 전체 로그
docker compose -f docker-compose.prod.yml logs -f

# Backend 로그
docker logs smarteyessen_backend -f

# MySQL 로그
docker logs smarteyessen_mysql -f
```

### 서비스 중지

```bash
docker compose -f docker-compose.prod.yml down

# 데이터베이스 볼륨까지 삭제 (주의!)
docker compose -f docker-compose.prod.yml down -v
```

---

## 🌐 배포 환경

### 프로덕션 URL

- **웹사이트**: https://smart-eye.live
- **API 문서**: https://smart-eye.live/docs
- **ReDoc**: https://smart-eye.live/redoc
- **Health Check**: https://smart-eye.live/health

### 배포 플랫폼

- **호스팅**: DigitalOcean Droplet (Ubuntu 24.04 LTS)
- **스펙**: 8GB RAM, 4 vCPU, 160GB SSD
- **SSL**: Let's Encrypt (자동 갱신)
- **도메인**: smart-eye.live

### 배포 명령어

```bash
# 서버 SSH 접속
ssh root@your-server-ip

# 최신 코드 Pull
cd /var/www/SmartEye-OCR-v0.1
git pull origin main

# Backend 재빌드 및 재시작
docker compose -f docker-compose.prod.yml build --no-cache backend
docker compose -f docker-compose.prod.yml up -d backend

# Frontend 재빌드 (필요 시)
docker compose -f docker-compose.prod.yml build --no-cache frontend
docker compose -f docker-compose.prod.yml up -d frontend
```

### SSL 인증서 갱신

```bash
# 수동 갱신
docker compose -f docker-compose.prod.yml run --rm certbot renew

# 자동 갱신 (Certbot 컨테이너가 12시간마다 자동 체크)
docker compose -f docker-compose.prod.yml up -d certbot
```

---

## 📡 API 사용법

### Base URL

- **개발 환경**: `http://localhost:80/api`
- **프로덕션**: `https://smart-eye.live/api`

### 기본 워크플로우

```javascript
// 1. 프로젝트 생성
const project = await fetch('https://smart-eye.live/api/projects', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    project_name: '수학 문제집 1단원',
    doc_type_id: 1,  // 1: worksheet, 2: document
    analysis_mode: 'auto',
    user_id: 1
  })
}).then(res => res.json());

// 2. PDF 업로드
const formData = new FormData();
formData.append('project_id', project.project_id);
formData.append('file', pdfFile);

const uploadResult = await fetch('https://smart-eye.live/api/pages/upload', {
  method: 'POST',
  body: formData
}).then(res => res.json());

// 3. 프로젝트 분석
const analysisResult = await fetch(`https://smart-eye.live/api/projects/${project.project_id}/analyze`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    use_ai_descriptions: true,
    api_key: 'sk-...'  // OpenAI API Key (선택)
  })
}).then(res => res.json());

// 4. 페이지 텍스트 조회
const pageText = await fetch(`https://smart-eye.live/api/pages/${pageId}/text`)
  .then(res => res.json());

// 5. 텍스트 편집 저장
await fetch(`https://smart-eye.live/api/pages/${pageId}/text`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    content: editedContent,
    user_id: 1
  })
});

// 6. Word 문서 다운로드
const response = await fetch(`https://smart-eye.live/api/projects/${project.project_id}/download`, {
  method: 'POST'
});
const blob = await response.blob();
const url = window.URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = `${project.project_name}.docx`;
a.click();
```

---

## 🔌 주요 API 엔드포인트

### 프로젝트 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/projects` | 프로젝트 생성 |
| `GET` | `/api/projects` | 프로젝트 목록 조회 |
| `GET` | `/api/projects/{project_id}` | 프로젝트 상세 조회 |
| `PATCH` | `/api/projects/{project_id}` | 프로젝트 수정 |
| `DELETE` | `/api/projects/{project_id}` | 프로젝트 삭제 |

### 페이지 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/pages/upload` | 이미지/PDF 업로드 |
| `GET` | `/api/pages/{page_id}` | 페이지 상세 조회 |
| `GET` | `/api/pages/{page_id}/text` | 페이지 텍스트 조회 |
| `POST` | `/api/pages/{page_id}/text` | 텍스트 편집 저장 |
| `DELETE` | `/api/pages/{page_id}` | 페이지 삭제 |

### 분석 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/projects/{project_id}/analyze` | 프로젝트 배치 분석 |
| `POST` | `/api/pages/{page_id}/analyze` | 단일 페이지 분석 |

### 다운로드 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/api/projects/{project_id}/combined-text` | 통합 텍스트 조회 |
| `POST` | `/api/projects/{project_id}/download` | Word 문서 다운로드 |

**상세 API 문서**: `Backend/docs/Backend API 문서/` 참조

---

## 📚 문서

- **API 문서**: [Backend/docs/Backend API 문서/](./Backend/docs/Backend%20API%20문서/)
- **ERD**: [Backend/scripts/DB/final E-R Diagram.md](./Backend/scripts/DB/final%20E-R%20Diagram.md)
- **프로젝트 계획**: [Project/project_purpose.md](./Project/project_purpose.md)
- **코딩 규칙**: [CODING_CONVENTIONS.md](./CODING_CONVENTIONS.md)

---

## 🤝 기여

기여를 환영합니다! 다음 절차를 따라주세요:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 라이선스

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 팀

- **개발팀**: Friends
- **연락처**: support@smart-eye.live
- **GitHub**: https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1

---

## 🙏 감사의 말

- [DocLayout-YOLO](https://github.com/opendatalab/DocLayout-YOLO) - 레이아웃 분석 모델
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) - OCR 엔진
- [OpenAI](https://openai.com/) - GPT-4-turbo Vision API
- [FastAPI](https://fastapi.tiangolo.com/) - 백엔드 프레임워크
- [React](https://react.dev/) - 프론트엔드 프레임워크

---

**마지막 업데이트**: 2025-11-09
**버전**: 0.1.0
