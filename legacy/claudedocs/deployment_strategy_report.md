# SmartEyeSsen 프로젝트 클라우드 배포 전략 종합 보고서

**작성일**: 2025-11-06
**프로젝트**: SmartEyeSsen - AI 기반 워크시트 분석 시스템
**목적**: 로컬 Docker 환경을 클라우드로 배포하여 공개 웹 서비스 제공

---

## 📋 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [Phase 1: 백엔드 의존성 충돌 분석](#2-phase-1-백엔드-의존성-충돌-분석)
3. [Phase 2: 아키텍처 및 통신 검토](#3-phase-2-아키텍처-및-통신-검토)
4. [Phase 3: 클라우드 배포 전략](#4-phase-3-클라우드-배포-전략)
5. [최종 권장 사항 및 실행 계획](#5-최종-권장-사항-및-실행-계획)
6. [비용 분석](#6-비용-분석)
7. [부록: 설정 파일](#7-부록-설정-파일)

---

## 1. 프로젝트 개요

### 1.1 시스템 구성

**SmartEyeSsen**은 시각 장애 학생을 위한 AI 기반 워크시트 분석 시스템입니다.

| 컴포넌트 | 기술 스택 | 포트 |
|---------|----------|------|
| **Frontend** | React 19.1.1 + TypeScript + Vite 7 | 5173 (dev) |
| **Backend** | FastAPI + Python 3.9 | 8000 |
| **Database** | MySQL 8.0 (Docker) | 3308 |
| **AI/ML** | DocLayout-YOLO, Tesseract OCR, OpenAI Vision API | - |

### 1.2 주요 기능

- 📄 다중 페이지 문서 처리 (PDF, 이미지)
- 🤖 AI 레이아웃 분석 (DocLayout-YOLO)
- 🔍 OCR 텍스트 추출 (Tesseract)
- ✏️ 텍스트 편집 및 버전 관리
- 🖼️ AI 설명 생성 (GPT-4 Vision)
- 📊 문제 기반 정렬 (Worksheet)
- 📥 통합 문서 다운로드 (DOCX)

### 1.3 기술적 요구사항

| 항목 | 요구사항 | 근거 |
|------|---------|------|
| **CPU/GPU** | 2-4 vCPU (CPU) 또는 GPU 지원 | DocLayout-YOLO 추론 |
| **메모리** | 최소 4GB, 권장 8GB | PyTorch 모델 로딩 + 이미지 처리 |
| **스토리지** | 20GB+ (확장 가능) | AI 모델(~500MB), 업로드 이미지, DB |
| **네트워크** | 아웃바운드 무제한 | OpenAI API 호출 (최대 30 동시 요청) |
| **데이터베이스** | MySQL 8.0 호환 | 12개 테이블, LONGTEXT 지원 |

---

## 2. Phase 1: 백엔드 의존성 충돌 분석

### 2.1 문제 상황

**에러 메시지 분석**:
```
ERROR: Cannot install -r requirements.txt (line 34), -r requirements.txt (line 37),
-r requirements.txt (line 50), -r requirements.txt (line 58), imageio==2.31.6
and pillow==10.2.0 because these package versions have conflicting dependencies.
```

### 2.2 근본 원인

**하위 의존성(transitive dependencies) 충돌**:

```
Backend/requirements.txt (188개 패키지)
├─ pillow==10.2.0 (고정 버전)
├─ imageio==2.31.6 (고정 버전)
├─ scikit-image==0.22.0 (고정 버전)
├─ ultralytics==8.0.196
│  ├─ 요구: pillow >= 7.1.2 (유연)
│  └─ 요구: opencv-python >= 4.6.0
└─ torchvision==0.15.2
   ├─ 요구: pillow >= 5.3.0, != 8.3.*, !=8.4.0
   └─ 요구: numpy 호환 버전

충돌 발생:
❌ scikit-image==0.22.0이 요구하는 imageio 버전 범위
❌ pillow 10.2.0이 일부 패키지와 비호환
❌ opencv-python과 opencv-python-headless 중복 가능성
```

### 2.3 Backend vs Project requirements.txt 비교

| 항목 | Backend/requirements.txt | Project/requirements.txt | 비고 |
|------|-------------------------|--------------------------|------|
| 총 패키지 수 | **188개** | **198개** | Backend가 더 많지만 충돌 |
| Pillow | `10.2.0` (고정) | `>=8.0.0` (유연) | ✅ Project가 안정적 |
| opencv-python | `4.9.0.80` (고정) | `opencv-python-headless>=4.5.0` | ✅ headless가 서버용 |
| fastapi | `0.109.0` | `0.104.1` | Backend가 최신 |
| cryptography | `42.0.0` | `41.0.7` | ✅ Project가 안정적 |

### 2.4 Project/requirements.txt 성공 이유

1. **버전 범위 유연성**: `>=` 사용으로 pip resolver가 호환 버전 자동 선택
2. **opencv-python-headless 사용**: GUI 불필요 서버 환경에 적합
3. **구버전 사용**: 안정성 검증된 버전 조합 (cryptography 41.0.7)
4. **pymysql 별도 설치**: SQLAlchemy MySQL 커넥터로 필수

### 2.5 권장 해결 방안

#### ✅ 즉시 조치 (High Priority)

**Option 1: Project/requirements.txt 기반 통합 (추천)**

```bash
# Backend/ 디렉토리에서
cp ../Project/requirements.txt requirements.production.txt
echo "pymysql==1.1.0" >> requirements.production.txt

# 개발 도구 제거 (선택적)
# pytest, black, flake8, sphinx 등
```

**Option 2: pip-tools 도입 (장기 전략)**

```bash
# requirements.in 파일 생성 (최상위 의존성만)
pip install pip-tools

# requirements.in 예시
cat > requirements.in <<EOF
fastapi==0.109.0
uvicorn[standard]==0.27.0
sqlalchemy==2.0.25
pymysql==1.1.0
torch==2.0.1
torchvision==0.15.2
ultralytics==8.0.196
openai==1.10.0
# ... (핵심 의존성만)
EOF

# 하위 의존성 자동 해결 및 잠금
pip-compile requirements.in --resolver=backtracking
# → requirements.txt 생성 (모든 버전 고정)
```

#### 🔧 구조 개선 (Medium Priority)

**requirements/ 디렉토리 구조화**:

```
Backend/
├─ requirements/
│  ├─ base.txt           # 공통 의존성
│  ├─ production.txt     # 프로덕션 전용 (base.txt 포함)
│  ├─ development.txt    # 개발 도구 (pytest, black 등)
│  └─ docker.txt         # Docker 환경 전용
└─ docker-compose.yml
```

**base.txt 예시**:
```txt
# requirements/base.txt
fastapi>=0.109.0,<0.110.0
uvicorn[standard]>=0.27.0,<0.28.0
sqlalchemy>=2.0.25,<2.1.0
pymysql>=1.1.0,<1.2.0
torch==2.0.1
torchvision==0.15.2
```

**production.txt 예시**:
```txt
# requirements/production.txt
-r base.txt
gunicorn>=21.0.0
prometheus-client>=0.19.0
```

**development.txt 예시**:
```txt
# requirements/development.txt
-r base.txt
pytest>=7.4.4
black>=23.12.1
flake8>=7.0.0
pytest-cov>=4.1.0
```

#### 📦 pyproject.toml 전환 (Low Priority, 장기)

```toml
[project]
name = "smarteyessen-backend"
version = "0.1.0"
requires-python = ">=3.9,<3.11"
dependencies = [
    "fastapi>=0.109.0,<0.110.0",
    "uvicorn[standard]>=0.27.0,<0.28.0",
    "sqlalchemy>=2.0.25,<2.1.0",
    "pymysql>=1.1.0,<1.2.0",
    "torch==2.0.1",
    "torchvision==0.15.2",
    "ultralytics==8.0.196",
    "openai>=1.10.0,<2.0.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=7.4.4",
    "black>=23.12.1",
    "flake8>=7.0.0",
]
prod = [
    "gunicorn>=21.0.0",
    "prometheus-client>=0.19.0",
]

[build-system]
requires = ["setuptools>=65.0"]
build-backend = "setuptools.build_meta"
```

### 2.6 프로덕션 의존성 최적화

**제외 가능한 패키지 (약 30% 감소)**:

```python
# 개발 도구 (제외 가능)
pytest==7.4.4
pytest-asyncio==0.23.3
pytest-cov==4.1.0
coverage==7.3.2
black==23.12.1
flake8==7.0.0

# 모니터링 (선택적)
prometheus-client==0.19.0
tensorboard==2.15.1

# 문서화 (제외 가능)
sphinx==7.2.6
sphinx-rtd-theme==1.3.0

# 캐싱 (Redis 없으면 불필요)
redis==5.0.1

# 기타 불필요
asyncio==3.4.3  # Python 3.9+에 내장
```

**최적화된 프로덕션 requirements.txt 예시**:

```txt
# Backend/requirements.production.txt
# 웹 프레임워크
fastapi==0.104.1
uvicorn[standard]==0.24.0
python-multipart==0.0.6
aiofiles==23.2.1
gunicorn==21.2.0

# 데이터베이스
sqlalchemy==2.0.23
pymysql==1.1.0
cryptography==41.0.7
alembic==1.13.0

# AI/ML (필수)
torch==2.0.1
torchvision==0.15.2
ultralytics==8.0.196
huggingface-hub>=0.17.0
transformers==4.35.2
openai==1.3.5

# OCR
pytesseract==0.3.10

# 이미지 처리
pillow>=8.0.0
opencv-python-headless>=4.5.0
matplotlib>=3.5.0
scikit-image==0.22.0
imageio==2.31.6
numpy==1.26.4

# 문서 처리
python-docx==1.1.0
PyMuPDF==1.23.8
PyYAML>=6.0

# 데이터 처리
pandas>=1.3.0
scipy>=1.7.0
scikit-learn>=1.0.0

# 로깅 및 유틸리티
loguru==0.7.2
rich==13.7.0
tqdm>=4.60.0

# HTTP 클라이언트
httpx==0.25.2
requests>=2.25.0
urllib3==2.1.0

# 텍스트 처리
textdistance==4.6.0
fuzzywuzzy==0.18.0
python-levenshtein==0.23.0

# 설정 관리
pydantic==2.5.0
pydantic-settings==2.1.0
python-dotenv==1.0.0

# 기타 유틸리티
psutil==5.9.6
python-dateutil==2.8.2
typing-extensions==4.12.0
orjson==3.9.10
joblib==1.3.2
colorama==0.4.6
packaging==23.2
```

---

## 3. Phase 2: 아키텍처 및 통신 검토

### 3.1 현재 통신 아키텍처

```
┌──────────────────────────────────────────────────────────┐
│  Frontend (React + Vite)                                  │
│  Port: 5173 (dev) / Static Files (prod)                  │
│                                                           │
│  api.ts:                                                  │
│  baseURL = VITE_API_BASE_URL || "http://localhost:8000/api" │
│  └─ axios 직접 호출 (CORS 의존)                           │
└──────────────────────┬────────────────────────────────────┘
                       │
                       │ HTTP Request (CORS)
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│  Backend (FastAPI)                                        │
│  Port: 8000                                               │
│                                                           │
│  CORS Middleware:                                         │
│  ├─ allow_origins: localhost:3000,5173,8080              │
│  ├─ allow_credentials: True                               │
│  └─ allow_methods/headers: ["*"]                          │
│                                                           │
│  Endpoints:                                               │
│  ├─ /api/pages/upload     (upload.ts)                    │
│  ├─ /api/projects/*       (projects.ts)                  │
│  └─ /api/analysis/*       (analysis.ts)                  │
└──────────────────────┬────────────────────────────────────┘
                       │
                       │ TCP/IP
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│  Database (MySQL 8.0)                                     │
│  Port: 3308 (Docker)                                      │
│  └─ pymysql connector                                     │
└──────────────────────────────────────────────────────────┘
```

### 3.2 발견된 문제점

#### 🔴 High Priority Issues

**1. API Prefix 불일치 위험**

```typescript
// Frontend: api.ts:4
baseURL: "http://localhost:8000/api"
// ^^^^^^^^^ /api prefix 포함

// Backend: main.py:202-205
app.include_router(projects.router)  # prefix 확인 필요
app.include_router(pages.router)
app.include_router(analysis.router)
app.include_router(downloads.router)
```

**문제점**:
- 각 router에 `/api` prefix가 없으면 Frontend가 `/api/pages/upload` 호출 시 Backend는 `/pages/upload`로 등록되어 404 에러 발생

**해결 방법**:
```python
# Backend/app/main.py
app.include_router(projects.router, prefix="/api")
app.include_router(pages.router, prefix="/api")
app.include_router(analysis.router, prefix="/api")
app.include_router(downloads.router, prefix="/api")
```

**2. 프로덕션 환경 변수 관리**

```typescript
// Frontend: api.ts
baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api"
```

**문제점**:
- 빌드 타임에 결정됨 (런타임 변경 불가)
- 클라우드 배포 시 백엔드 URL 변경되면 프론트엔드 재빌드 필요
- Staging/Production 환경 분리 어려움

**해결 방법** (런타임 로딩):
```typescript
// Frontend/public/config.js (런타임 로드)
window.API_CONFIG = {
  baseURL: "https://api.yourdomain.com/api"
};

// Frontend/src/services/api.ts
const getBaseURL = () => {
  if (typeof window !== 'undefined' && window.API_CONFIG) {
    return window.API_CONFIG.baseURL;
  }
  return import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api";
};

const apiClient = axios.create({
  baseURL: getBaseURL(),
  timeout: 30000,
  headers: {
    "Content-Type": "application/json",
  },
});
```

**3. CORS 보안 취약점**

```python
# Backend: main.py:71-79
allow_origins=CORS_ORIGINS,  # 환경 변수
allow_credentials=True,
allow_methods=["*"],  # 모든 메소드 허용
allow_headers=["*"],  # 모든 헤더 허용
```

**문제점**:
- 프로덕션에서 `allow_methods=["*"]`는 과도한 권한
- `allow_credentials=True` + 와일드카드는 브라우저에서 거부될 수 있음

**해결 방법** (환경별 분리):
```python
# Backend/app/main.py
import os

ENVIRONMENT = os.getenv("ENVIRONMENT", "development")

if ENVIRONMENT == "production":
    # 프로덕션: 엄격한 CORS
    CORS_ORIGINS = os.getenv("CORS_ORIGINS", "https://yourdomain.com").split(",")
    CORS_METHODS = ["GET", "POST", "PUT", "DELETE"]
    CORS_HEADERS = ["Content-Type", "Authorization"]
else:
    # 개발: 유연한 CORS
    CORS_ORIGINS = ["http://localhost:5173", "http://localhost:3000", "http://localhost:8080"]
    CORS_METHODS = ["*"]
    CORS_HEADERS = ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=CORS_METHODS,
    allow_headers=CORS_HEADERS,
)
```

#### 🟡 Medium Priority Issues

**4. Proxy 설정 부재**

```typescript
// Frontend: vite.config.ts
export default defineConfig({
  plugins: [react()],
  // ❌ proxy 설정 없음
});
```

**개선 방안**:
```typescript
// Frontend/vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8000",
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, '/api')
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ["react", "react-dom"],
          utils: ["axios"],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
});
```

**5. 정적 파일 서빙 보안**

```python
# Backend: main.py:82-86
app.mount("/uploads", StaticFiles(directory=str(UPLOAD_DIR)), name="uploads")
```

**문제점**:
- 업로드된 모든 파일 공개 접근 가능
- 인증/권한 체크 없음
- 파일 경로 추측 가능 시 정보 유출 위험

**개선 방안** (선택적 인증):
```python
# Backend/app/main.py
from fastapi import Depends, HTTPException
from fastapi.responses import FileResponse

@app.get("/uploads/{file_path:path}")
async def serve_upload(
    file_path: str,
    # 인증 체크 (선택적)
    # current_user: User = Depends(get_current_user)
):
    """업로드 파일 서빙 (인증 선택)"""
    file_location = UPLOAD_DIR / file_path

    if not file_location.exists():
        raise HTTPException(status_code=404, detail="File not found")

    # 경로 탈출 방지
    if not file_location.resolve().is_relative_to(UPLOAD_DIR.resolve()):
        raise HTTPException(status_code=403, detail="Access denied")

    return FileResponse(file_location)
```

### 3.3 권장 아키텍처: Reverse Proxy 패턴

```
┌─────────────────────────────────────────────────────────┐
│  Nginx / Caddy / AWS ALB                                 │
│  Domain: https://yourdomain.com                          │
│                                                          │
│  Rules:                                                  │
│  ├─ /            → Frontend (Static)                    │
│  ├─ /api/*       → Backend:8000                         │
│  ├─ /uploads/*   → Backend:8000/uploads (선택적 인증)    │
│  └─ /docs        → Backend:8000/docs (선택적)           │
└─────────────────────────────────────────────────────────┘
                     ▲
                     │ Same-Origin (CORS 불필요!)
                     │
    ┌────────────────┴────────────────┐
    │                                  │
┌───▼──────┐                    ┌─────▼────┐
│ Frontend │                    │ Backend  │
│ (Static) │                    │ (FastAPI)│
└──────────┘                    └──────────┘
```

**장점**:
1. ✅ Same-Origin → CORS 문제 완전 해결
2. ✅ SSL 종료 지점 단일화 (Nginx/Caddy에서 처리)
3. ✅ 정적 파일 캐싱 최적화 (Nginx가 더 빠름)
4. ✅ Rate Limiting, IP Filtering 등 보안 기능 추가 용이
5. ✅ 백엔드 URL 변경 시 프론트엔드 재빌드 불필요

---

## 4. Phase 3: 클라우드 배포 전략

### 4.1 배포 옵션 비교 분석

#### 🏆 Option 1: IaaS (VPS) - EC2, GCP Compute Engine, Vultr **(최종 추천)**

**구성**:
```
┌─────────────────────────────────────────────────────────┐
│  단일 VPS (예: AWS EC2 t3.medium, Vultr 4GB)             │
│                                                          │
│  ├─ Docker Compose                                       │
│  │  ├─ Nginx (리버스 프록시 + SSL)                       │
│  │  ├─ Frontend (정적 파일, Nginx 서빙)                  │
│  │  ├─ Backend (FastAPI + Gunicorn)                     │
│  │  └─ MySQL 8.0 (Docker Volume)                        │
│  │                                                       │
│  └─ Let's Encrypt (자동 SSL 갱신)                        │
└─────────────────────────────────────────────────────────┘
```

**장점**:
- ✅ 로컬 환경과 동일: `docker-compose.yml` 거의 그대로 활용
- ✅ 비용 효율: $18-40/월 (Vultr 4GB ~ AWS t3.medium)
- ✅ 완전한 제어: 모든 설정 커스터마이징 가능
- ✅ GPU 옵션: GPU 인스턴스로 쉽게 업그레이드 가능
- ✅ 네트워크 제한 없음: OpenAI API 호출 무제한

**단점**:
- ❌ 관리 부담: OS 패치, 보안 업데이트 직접 관리
- ❌ 스케일링: 수동 스케일링 (로드밸런서 별도 설정)
- ❌ 백업: 자동화 직접 구현 필요

**예상 비용 (월별)**:

| 서비스 | 스펙 | 비용 (USD) |
|--------|------|-----------|
| **Vultr** | 4GB RAM, 2 vCPU, 80GB SSD | $18 |
| **DigitalOcean** | 4GB RAM, 2 vCPU, 80GB SSD | $24 |
| **GCP Compute** | e2-medium (2 vCPU, 4GB) | $25 |
| **AWS EC2** | t3.medium (2 vCPU, 4GB) | $30 |

**권장**: Vultr (가성비) 또는 AWS EC2 (확장성)

---

#### 🥈 Option 2: PaaS (관리형) - Render, Railway, Fly.io

**구성** (Render.com 예시):
```
├─ Web Service (Backend) - Docker 이미지
│  └─ Health Check: /health
├─ Static Site (Frontend) - Vite 빌드 결과
└─ PostgreSQL - Managed Database (MySQL 대신)
```

**장점**:
- ✅ 자동 배포: Git push → 자동 빌드/배포
- ✅ 관리 불필요: OS/보안 패치 자동
- ✅ SSL 자동: HTTPS 기본 제공
- ✅ 확장 용이: 대시보드에서 클릭 몇 번

**단점**:
- ❌ 비용 상승: $30-70/월 (DB 별도)
- ❌ MySQL 미지원: Render는 PostgreSQL만 (마이그레이션 필요)
- ❌ 제약사항:
  - Railway: 512MB 메모리 제한 (무료), 유료 $10/월부터
  - Render: Cold Start (무료 플랜은 15분 비활성 시 슬립)
- ❌ 성능 불확실: AI 모델 추론 성능이 VPS보다 낮을 수 있음

**예상 비용 (월별)**:

| 서비스 | 구성 | 비용 (USD) |
|--------|------|-----------|
| **Render** | Starter (Backend + DB) | $25 + $7 = $32 |
| **Railway** | Pro Plan (8GB RAM) | $20 (사용량 기반) |
| **Fly.io** | 1GB RAM App + Postgres | $15-30 |

**⚠️ 주의사항**:
- MySQL → PostgreSQL 마이그레이션 필요
- AI 모델 성능 테스트 필수

---

#### 🥉 Option 3: Container Orchestration - AWS ECS/Fargate

**구성**:
```
AWS 환경:
├─ CloudFront (CDN) → S3 (Frontend)
├─ Application Load Balancer
│  ├─ ECS Fargate (Backend Container)
│  │  └─ Task: 2 vCPU, 4GB RAM
│  └─ Auto Scaling (2-4 tasks)
└─ RDS for MySQL (db.t3.micro)
```

**장점**:
- ✅ 완전 관리형: 서버 관리 불필요
- ✅ 자동 스케일링: 부하에 따라 자동 확장
- ✅ 고가용성: Multi-AZ 배포 가능
- ✅ AWS 생태계: CloudWatch, IAM, Secrets Manager 통합

**단점**:
- ❌ 높은 비용: $80-150/월 (최소 구성)
- ❌ 복잡도: 초기 설정 학습 곡선 높음
- ❌ 오버엔지니어링: 초기 사용자 10-50명에게 과함

**예상 비용 (월별)**:

| 항목 | 스펙 | 비용 (USD) |
|------|------|-----------|
| ECS Fargate | 2 vCPU, 4GB RAM, 24시간 | $50 |
| ALB | Application Load Balancer | $23 |
| RDS MySQL | db.t3.micro (20GB) | $15 |
| S3 + CloudFront | 50GB 전송 | $5-10 |
| **합계** | | **$93-98/월** |

---

#### 🎨 Option 4: Hybrid - Vercel/Netlify + VPS (권장 대안)

**구성**:
```
┌─────────────────────────────────────────────────────────┐
│  Vercel / Netlify (Frontend Only)                        │
│  ├─ React 정적 빌드                                       │
│  ├─ Global CDN                                           │
│  └─ 무료 플랜 (100GB/월)                                  │
└─────────────────────────────────────────────────────────┘
         │
         │ API 호출 (CORS)
         ▼
┌─────────────────────────────────────────────────────────┐
│  VPS (Backend + DB)                                      │
│  ├─ Docker: FastAPI + MySQL                             │
│  └─ Nginx (API만, SSL)                                   │
└─────────────────────────────────────────────────────────┘
```

**장점**:
- ✅ 최고의 가성비: $18-25/월 (VPS만 유료)
- ✅ Frontend 성능: Global CDN으로 빠른 로딩
- ✅ 자동 배포: Git push → Vercel 자동 배포
- ✅ SSL 무료: Vercel/Netlify 자동 제공

**단점**:
- ❌ CORS 설정 필요: 다른 도메인 간 통신
- ❌ 복잡도 증가: 2개 플랫폼 관리

**예상 비용 (월별)**:
- Vercel/Netlify: **$0** (무료 플랜)
- Vultr VPS (4GB): **$18**
- **합계**: **$18/월**

---

### 4.2 최종 권장 배포 아키텍처

#### 🏆 1순위: **IaaS (VPS) 단일 서버 배포**

**선정 이유**:
1. ✅ **로컬 환경 호환**: Docker Compose 거의 그대로 사용
2. ✅ **최고의 가성비**: $18-30/월
3. ✅ **성능 보장**: AI 모델 추론 성능 예측 가능
4. ✅ **확장 가능**: GPU 인스턴스로 업그레이드 용이
5. ✅ **완전한 제어**: MySQL, Tesseract 등 시스템 의존성 자유롭게 설치

**추천 서비스**:
- **초기 단계**: Vultr 4GB ($18/월) - 가장 저렴
- **확장 고려**: AWS EC2 t3.medium ($30/월) - Auto Scaling, AWS 생태계

---

## 5. 최종 권장 사항 및 실행 계획

### 5.1 즉시 실행 체크리스트

```
□ Phase 1: 의존성 정리
  □ 1. Backend/requirements.production.txt 생성 (Project/requirements.txt 기반)
  □ 2. 개발 도구 제거 (pytest, black, flake8, sphinx 등)
  □ 3. pymysql==1.1.0 명시적 추가

□ Phase 2: 코드 수정
  □ 4. API Router에 /api prefix 추가 (Backend/app/main.py)
  □ 5. CORS 설정 환경별 분리 (production vs development)
  □ 6. Frontend 환경 변수 런타임 로딩 구현 (선택적)

□ Phase 3: 인프라 준비
  □ 7. Vultr/AWS EC2 계정 생성
  □ 8. VPS 인스턴스 생성 (4GB RAM, 2 vCPU)
  □ 9. 도메인 구매 및 DNS 연결
  □ 10. Backend/.env.production 환경 변수 설정

□ Phase 4: Docker 구성
  □ 11. docker-compose.production.yml 작성
  □ 12. Backend/Dockerfile.production 작성
  □ 13. Nginx 설정 파일 작성 (nginx/conf.d/default.conf)

□ Phase 5: 배포 및 테스트
  □ 14. Frontend 빌드 (npm run build)
  □ 15. 서버에 파일 전송 (scp 또는 git clone)
  □ 16. Docker Compose 실행 (docker-compose up -d)
  □ 17. SSL 인증서 발급 (Let's Encrypt)
  □ 18. 동작 테스트 (이미지 업로드, 분석)

□ Phase 6: 모니터링 및 백업 (선택적)
  □ 19. UptimeRobot 설정
  □ 20. 데이터베이스 백업 스크립트 작성
  □ 21. GitHub Actions CI/CD 구성 (선택적)
```

### 5.2 구체적 실행 계획

#### 📋 Phase 1: 준비 작업 (2-3일)

**Step 1.1: 도메인 구매 및 DNS 설정**

```bash
# 도메인 구매 (예: Namecheap, GoDaddy)
# 예상 비용: $10-15/년

# DNS 레코드 설정
Type: A
Name: @
Value: [VPS IP 주소]

Type: A
Name: www
Value: [VPS IP 주소]
```

**Step 1.2: 환경 변수 정리**

```bash
# Backend/.env.production 생성
cat > Backend/.env.production <<EOF
# 데이터베이스 설정
DB_HOST=mysql  # Docker 네트워크 내부
DB_PORT=3306
DB_USER=root
DB_PASSWORD=$(openssl rand -base64 32)  # 강력한 비밀번호
DB_NAME=smarteyessen_db

# FastAPI 설정
API_HOST=0.0.0.0
API_PORT=8000
ENVIRONMENT=production
DEBUG=False

# OpenAI API
OPENAI_API_KEY=sk-your-key
OPENAI_MAX_CONCURRENCY=30
MAX_CONCURRENT_PAGES=8

# CORS 설정
CORS_ORIGINS=https://yourdomain.com,https://www.yourdomain.com

# 보안 설정
SECRET_KEY=$(openssl rand -hex 32)
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30

# 파일 업로드
UPLOAD_DIR=uploads
MAX_FILE_SIZE=104857600
ALLOWED_EXTENSIONS=jpg,jpeg,png,pdf
EOF
```

**Step 1.3: 의존성 최적화**

```bash
# Backend/requirements.production.txt 생성
cp Project/requirements.txt Backend/requirements.production.txt
echo "pymysql==1.1.0" >> Backend/requirements.production.txt
echo "gunicorn==21.2.0" >> Backend/requirements.production.txt

# 개발 도구 제거 (수동 편집)
# pytest, black, flake8, sphinx, coverage 등 제거
```

---

#### 📋 Phase 2: VPS 설정 및 Docker 구성 (1-2일)

**Step 2.1: VPS 프로비저닝**

```bash
# Vultr 또는 AWS EC2에서 인스턴스 생성
# OS: Ubuntu 22.04 LTS
# 스펙: 4GB RAM, 2 vCPU, 80GB SSD

# SSH 접속
ssh root@your-server-ip

# 시스템 업데이트
apt-get update && apt-get upgrade -y

# 방화벽 설정
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 22/tcp
ufw enable
```

**Step 2.2: Docker 설치**

```bash
# Docker 설치
curl -fsSL https://get.docker.com | sh
systemctl enable docker
systemctl start docker

# Docker Compose 설치
curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# 설치 확인
docker --version
docker-compose --version
```

**Step 2.3: 프로젝트 배포**

```bash
# Git 설치 및 클론
apt-get install -y git
cd /opt
git clone https://github.com/yourusername/smarteyessen.git
cd smarteyessen

# 환경 변수 설정
cp Backend/.env.production Backend/.env
nano Backend/.env  # 값 수정 (DB 비밀번호, OpenAI API 키 등)

# Tesseract 설치 (Docker 외부, 성능 최적화)
apt-get install -y tesseract-ocr tesseract-ocr-kor tesseract-ocr-eng
tesseract --version
tesseract --list-langs  # kor, eng 확인
```

---

#### 📋 Phase 3: Frontend 빌드 및 배포 (1일)

**Step 3.1: Frontend 빌드**

```bash
# 로컬 환경에서
cd Frontend

# 환경 변수 설정
echo "VITE_API_BASE_URL=https://yourdomain.com/api" > .env.production

# 의존성 설치 및 빌드
npm install
npm run build

# dist/ 폴더 확인
ls -la dist/
```

**Step 3.2: 서버로 전송**

```bash
# 로컬에서 서버로 전송
scp -r dist root@your-server-ip:/opt/smarteyessen/Frontend/

# 또는 Git을 통한 배포 (권장)
git add .
git commit -m "Add production build"
git push origin main

# 서버에서 pull
ssh root@your-server-ip
cd /opt/smarteyessen
git pull origin main
```

---

#### 📋 Phase 4: SSL 인증서 발급 및 최종 테스트 (1일)

**Step 4.1: Docker Compose 시작**

```bash
# 서버에서 실행
cd /opt/smarteyessen
docker-compose -f docker-compose.production.yml up -d

# 로그 확인
docker-compose -f docker-compose.production.yml logs -f
```

**Step 4.2: SSL 인증서 발급**

```bash
# Nginx가 HTTP로 먼저 시작되어야 함
docker-compose -f docker-compose.production.yml up -d nginx

# SSL 인증서 발급
docker-compose -f docker-compose.production.yml run --rm certbot certonly \
  --webroot --webroot-path=/var/www/certbot \
  -d yourdomain.com -d www.yourdomain.com \
  --email your-email@example.com \
  --agree-tos --no-eff-email

# Nginx 재시작 (HTTPS 활성화)
docker-compose -f docker-compose.production.yml restart nginx
```

**Step 4.3: 동작 테스트**

```bash
# 1. 헬스체크
curl https://yourdomain.com/api/health

# 예상 응답:
# {
#   "status": "healthy",
#   "database": "connected",
#   "api_version": "1.0.0"
# }

# 2. Frontend 접속
# 브라우저: https://yourdomain.com

# 3. 이미지 업로드 테스트
# Frontend에서 이미지 업로드 → 분석 결과 확인

# 4. 로그 확인
docker-compose -f docker-compose.production.yml logs -f backend
docker-compose -f docker-compose.production.yml logs -f nginx
```

---

#### 📋 Phase 5: 모니터링 및 백업 (선택적, 1-2일)

**Step 5.1: 업타임 모니터링**

```bash
# UptimeRobot (무료) 설정: https://uptimerobot.com
# Monitor Type: HTTP(s)
# URL: https://yourdomain.com/api/health
# Monitoring Interval: 5분
# Alert Contacts: 이메일
```

**Step 5.2: 데이터베이스 백업**

```bash
# 백업 스크립트 생성
mkdir -p /opt/smarteyessen/scripts
cat > /opt/smarteyessen/scripts/backup.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# MySQL 백업
docker exec smart_mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD smarteyessen_db \
  > $BACKUP_DIR/db_backup_$DATE.sql

# 업로드 파일 백업
tar -czf $BACKUP_DIR/uploads_$DATE.tar.gz /opt/smarteyessen/Backend/uploads

# 7일 이상 오래된 백업 삭제
find $BACKUP_DIR -type f -mtime +7 -delete

echo "Backup completed: $DATE"
EOF

chmod +x /opt/smarteyessen/scripts/backup.sh

# Cron 설정
crontab -e
# 매일 새벽 2시 백업
0 2 * * * /opt/smarteyessen/scripts/backup.sh >> /var/log/backup.log 2>&1
```

**Step 5.3: GitHub Actions CI/CD (선택적)**

```yaml
# .github/workflows/deploy.yml
name: Deploy to Production

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Build Frontend
        run: |
          cd Frontend
          npm ci
          npm run build

      - name: Deploy to Server
        uses: appleboy/scp-action@master
        with:
          host: ${{ secrets.SERVER_IP }}
          username: root
          key: ${{ secrets.SSH_KEY }}
          source: "Frontend/dist,Backend"
          target: "/opt/smarteyessen"

      - name: Restart Services
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.SERVER_IP }}
          username: root
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd /opt/smarteyessen
            docker-compose -f docker-compose.production.yml pull
            docker-compose -f docker-compose.production.yml up -d
```

---

## 6. 비용 분석

### 6.1 월별 운영 비용 (1순위 아키텍처)

| 항목 | 비용 (USD) | 비고 |
|------|-----------|------|
| **Vultr VPS (4GB)** | $18 | 가장 저렴한 옵션 |
| **도메인** | $1 | 연간 $12 / 12개월 |
| **OpenAI API** | $10-50 | 사용량 기반 (30 동시 요청) |
| **백업 스토리지** | $0-5 | 선택적 (S3, Backblaze) |
| **모니터링** | $0 | UptimeRobot 무료 플랜 |
| **합계** | **$29-74/월** | 평균 **$50/월** |

### 6.2 비용 절감 팁

1. **GPU 불필요 시**: CPU 인스턴스 사용 ($18-30/월)
2. **OpenAI API 최적화**:
   - 캐싱 활성화 (`diskcache==5.6.3`)
   - `OPENAI_MAX_CONCURRENCY` 조정 (30 → 15)
   - 중복 요청 방지
3. **이미지 압축**: 업로드 시 자동 압축으로 스토리지 절약
4. **CDN 활용**: CloudFlare 무료 플랜 (대역폭 절약)

### 6.3 옵션별 비용 비교

| 옵션 | 월별 비용 | 연간 비용 | 비고 |
|------|----------|----------|------|
| **1. Vultr VPS (권장)** | $29-74 | $348-888 | 최고 가성비 |
| **2. AWS EC2 t3.medium** | $41-91 | $492-1092 | AWS 생태계 |
| **3. Hybrid (Vercel + Vultr)** | $28-78 | $336-936 | Frontend CDN |
| **4. Render PaaS** | $40-90 | $480-1080 | 관리 편의성 |
| **5. AWS ECS Fargate** | $93-143 | $1116-1716 | 오버엔지니어링 |

### 6.4 확장 로드맵 및 비용

| Phase | 기간 | 인프라 | 월별 비용 |
|-------|------|--------|----------|
| **Phase 1: 초기 운영** | 1-3개월 | VPS 단일 서버 | $29-74 |
| **Phase 2: 최적화** | 3-6개월 | GPU 인스턴스 + Redis | $60-120 |
| **Phase 3: 확장** | 6-12개월 | 로드밸런서 + 다중 인스턴스 | $150-250 |
| **Phase 4: 엔터프라이즈** | 12개월+ | AWS ECS + RDS | $300-500 |

---

## 7. 부록: 설정 파일

### 7.1 docker-compose.production.yml

```yaml
version: '3.8'

services:
  # MySQL 데이터베이스
  mysql:
    image: mysql:8.0
    container_name: smart_mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
    volumes:
      - mysql_data:/var/lib/mysql
      - ./Backend/scripts/init_db_complete.sql:/docker-entrypoint-initdb.d/01_init.sql:ro
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-authentication-plugin=mysql_native_password
      - --max-connections=200
    networks:
      - app_network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # FastAPI 백엔드
  backend:
    build:
      context: ./Backend
      dockerfile: Dockerfile.production
    container_name: smart_backend
    restart: unless-stopped
    env_file:
      - ./Backend/.env
    ports:
      - "8000:8000"
    volumes:
      - ./Backend/uploads:/app/uploads
      - /usr/share/tesseract-ocr:/usr/share/tesseract-ocr:ro
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - app_network
    command: >
      gunicorn app.main:app
      --workers 2
      --worker-class uvicorn.workers.UvicornWorker
      --bind 0.0.0.0:8000
      --timeout 300
      --access-logfile -
      --error-logfile -
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 30s
      timeout: 10s
      start_period: 40s
      retries: 3

  # Nginx 리버스 프록시
  nginx:
    image: nginx:alpine
    container_name: smart_nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./Frontend/dist:/usr/share/nginx/html:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on:
      - backend
    networks:
      - app_network

  # Certbot (SSL 인증서)
  certbot:
    image: certbot/certbot
    container_name: certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"

volumes:
  mysql_data:
    driver: local

networks:
  app_network:
    driver: bridge
```

### 7.2 Backend/Dockerfile.production

```dockerfile
FROM python:3.9-slim

WORKDIR /app

# 시스템 의존성 설치
RUN apt-get update && apt-get install -y \
    build-essential \
    libgomp1 \
    libgl1-mesa-glx \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Python 의존성 복사 및 설치
COPY requirements.production.txt .
RUN pip install --no-cache-dir -r requirements.production.txt

# DocLayout-YOLO 설치
RUN pip install --no-cache-dir doclayout-yolo || \
    pip install --no-cache-dir git+https://github.com/opendatalab/DocLayout-YOLO.git

# 애플리케이션 코드 복사
COPY . .

# 업로드 디렉토리 생성
RUN mkdir -p uploads

# 포트 노출
EXPOSE 8000

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

# Gunicorn으로 실행 (docker-compose command로 오버라이드)
CMD ["gunicorn", "app.main:app", \
     "--workers", "2", \
     "--worker-class", "uvicorn.workers.UvicornWorker", \
     "--bind", "0.0.0.0:8000", \
     "--timeout", "300"]
```

### 7.3 nginx/conf.d/default.conf

```nginx
# HTTP → HTTPS 리다이렉트
server {
    listen 80;
    server_name yourdomain.com www.yourdomain.com;

    # Certbot challenge
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # 모든 HTTP 요청을 HTTPS로 리다이렉트
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS 서버
server {
    listen 443 ssl http2;
    server_name yourdomain.com www.yourdomain.com;

    # SSL 인증서
    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # SSL 설정
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # 보안 헤더
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Frontend (정적 파일)
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;

        # 캐싱 설정
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # index.html은 캐싱 안 함
        location = /index.html {
            expires -1;
            add_header Cache-Control "no-store, no-cache, must-revalidate";
        }
    }

    # Backend API
    location /api/ {
        proxy_pass http://backend:8000/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 타임아웃 (AI 처리)
        proxy_read_timeout 300s;
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;

        # 업로드 크기
        client_max_body_size 100M;

        # 버퍼링 비활성화 (실시간 스트리밍)
        proxy_buffering off;
    }

    # 업로드 파일 서빙
    location /uploads/ {
        proxy_pass http://backend:8000/uploads/;
        proxy_set_header Host $host;

        # 캐싱 설정
        expires 1d;
        add_header Cache-Control "public";
    }

    # API 문서 (선택적, 프로덕션에서는 비활성화 권장)
    location /docs {
        proxy_pass http://backend:8000/docs;

        # IP 제한 (선택적)
        # allow 1.2.3.4;  # 관리자 IP
        # deny all;
    }

    location /redoc {
        proxy_pass http://backend:8000/redoc;
    }

    location /openapi.json {
        proxy_pass http://backend:8000/openapi.json;
    }
}
```

### 7.4 Backend/.env.production (템플릿)

```bash
# ============================================================================
# SmartEyeSsen Backend - Production Environment Variables
# ============================================================================

# ============================================================================
# 데이터베이스 설정 (MySQL)
# ============================================================================
DB_HOST=mysql  # Docker 네트워크 내부 호스트명
DB_PORT=3306
DB_USER=root
DB_PASSWORD=CHANGE_THIS_TO_STRONG_PASSWORD  # 반드시 변경!
DB_NAME=smarteyessen_db

# ============================================================================
# FastAPI 설정
# ============================================================================
API_HOST=0.0.0.0
API_PORT=8000
API_RELOAD=False
API_LOG_LEVEL=info

# ============================================================================
# 환경 설정
# ============================================================================
ENVIRONMENT=production
DEBUG=False

# ============================================================================
# OpenAI API 설정
# ============================================================================
OPENAI_API_KEY=sk-your-actual-api-key-here  # 반드시 변경!
OPENAI_MAX_CONCURRENCY=30
MAX_CONCURRENT_PAGES=8

# ============================================================================
# 파일 업로드 설정
# ============================================================================
UPLOAD_DIR=uploads
MAX_FILE_SIZE=104857600  # 100MB
ALLOWED_EXTENSIONS=jpg,jpeg,png,pdf

# ============================================================================
# 보안 설정 (JWT)
# ============================================================================
SECRET_KEY=GENERATE_RANDOM_SECRET_KEY_HERE  # openssl rand -hex 32
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30

# ============================================================================
# CORS 설정
# ============================================================================
CORS_ORIGINS=https://yourdomain.com,https://www.yourdomain.com

# ============================================================================
# Adaptive Sorter 설정
# ============================================================================
USE_ADAPTIVE_SORTER=true

# ============================================================================
# 페이지 처리 설정
# ============================================================================
PDF_PROCESSOR_DPI=150

# ============================================================================
# MySQL Docker 설정 (docker-compose.yml에서 사용)
# ============================================================================
MYSQL_ROOT_PASSWORD=SAME_AS_DB_PASSWORD_ABOVE
MYSQL_DATABASE=smarteyessen_db
MYSQL_PORT=3306
```

---

## 8. 성공 지표 및 모니터링

### 8.1 핵심 성능 지표 (KPI)

| 지표 | 목표 | 측정 방법 |
|------|------|----------|
| **응답 시간** | < 30초 | API `/api/analysis/run` 응답 시간 모니터링 |
| **가용성** | > 99.5% | UptimeRobot 월별 리포트 |
| **에러율** | < 1% | Nginx access log 분석 (5xx 에러) |
| **동시 사용자** | 10-50명 | Docker stats, Nginx concurrent connections |
| **월별 비용** | < $75 | 클라우드 청구서 |

### 8.2 모니터링 도구

**무료 도구**:
- **UptimeRobot**: 서버 업타임 모니터링 (https://uptimerobot.com)
- **Docker stats**: 컨테이너 리소스 사용량 (`docker stats`)
- **Nginx access log**: 트래픽 분석 (`/var/log/nginx/access.log`)

**유료 도구** (선택적):
- **DataDog**: 종합 모니터링 ($15/호스트/월)
- **New Relic**: APM 성능 모니터링 ($25/월)
- **Sentry**: 에러 추적 ($26/월)

---

## 9. 트러블슈팅 가이드

### 9.1 자주 발생하는 문제

#### 문제 1: 502 Bad Gateway

**증상**: `https://yourdomain.com` 접속 시 502 에러

**원인**:
- Backend 컨테이너가 시작되지 않음
- Backend와 Nginx 간 네트워크 연결 실패

**해결**:
```bash
# Backend 로그 확인
docker-compose -f docker-compose.production.yml logs backend

# Backend 재시작
docker-compose -f docker-compose.production.yml restart backend

# 네트워크 확인
docker network ls
docker network inspect smarteyessen_app_network
```

#### 문제 2: SSL 인증서 발급 실패

**증상**: Certbot 실행 시 에러

**원인**:
- DNS가 아직 전파되지 않음
- 80번 포트가 열려있지 않음

**해결**:
```bash
# DNS 전파 확인
nslookup yourdomain.com
dig yourdomain.com

# 80번 포트 확인
netstat -tlnp | grep :80

# HTTP로 먼저 시작
docker-compose -f docker-compose.production.yml up -d nginx

# SSL 재시도 (DNS 전파 대기 후)
docker-compose -f docker-compose.production.yml run --rm certbot certonly \
  --webroot --webroot-path=/var/www/certbot \
  -d yourdomain.com \
  --email your-email@example.com \
  --agree-tos
```

#### 문제 3: Database connection refused

**증상**: Backend 로그에 `Can't connect to MySQL server`

**원인**:
- MySQL 컨테이너가 아직 준비되지 않음
- 환경 변수 불일치

**해결**:
```bash
# MySQL 헬스체크 확인
docker-compose -f docker-compose.production.yml ps

# MySQL 로그 확인
docker-compose -f docker-compose.production.yml logs mysql

# 환경 변수 확인
docker-compose -f docker-compose.production.yml exec backend env | grep DB_

# MySQL 수동 연결 테스트
docker-compose -f docker-compose.production.yml exec mysql \
  mysql -u root -p -e "SELECT 1"
```

### 9.2 성능 최적화

#### CPU 사용량 높음

```bash
# 프로세스 확인
docker stats

# Gunicorn worker 수 조절 (docker-compose.yml)
# --workers 2 → --workers 4 (CPU 코어 수에 따라)

# OpenAI API 동시 요청 수 감소 (.env)
OPENAI_MAX_CONCURRENCY=30 → 15
```

#### 메모리 부족

```bash
# 메모리 사용량 확인
free -h
docker stats

# Swap 메모리 추가
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 영구적으로 적용
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 10. 결론 및 다음 단계

### 10.1 요약

본 보고서는 SmartEyeSsen 프로젝트의 클라우드 배포를 위한 종합 전략을 제시했습니다:

1. **의존성 충돌 해결**: `Project/requirements.txt` 기반 프로덕션 환경 구성
2. **아키텍처 개선**: Reverse Proxy 패턴, 환경별 CORS 분리, API Prefix 통일
3. **배포 전략**: VPS 단일 서버 배포 (Vultr/AWS EC2, $18-30/월)
4. **실행 계획**: 5단계 체크리스트 (준비 → VPS 설정 → 배포 → SSL → 모니터링)

### 10.2 예상 성과

- **비용**: 월 $29-74 (평균 $50)
- **성능**: 응답 시간 < 30초, 가용성 > 99.5%
- **확장성**: GPU 인스턴스로 3-5배 성능 향상 가능
- **관리**: Docker Compose 기반 간편한 운영

### 10.3 다음 단계

**즉시 조치** (1주일):
1. Backend/requirements.production.txt 생성
2. API Router에 /api prefix 추가
3. Backend/.env.production 환경 변수 설정

**단기 조치** (2-4주):
4. VPS 프로비저닝 및 Docker 구성
5. Frontend 빌드 및 배포
6. SSL 인증서 발급 및 테스트

**중기 조치** (1-3개월):
7. 모니터링 및 백업 시스템 구축
8. CI/CD 파이프라인 구성
9. 성능 최적화 (캐싱, GPU 등)

### 10.4 추가 지원

배포 과정에서 추가 지원이 필요한 경우:
- 특정 클라우드 서비스별 상세 가이드
- Nginx/SSL 설정 커스터마이징
- GitHub Actions CI/CD 구성
- 성능 최적화 및 GPU 활용
- 데이터베이스 마이그레이션

---

**문서 작성일**: 2025-11-06
**버전**: 1.0
**작성자**: Claude (claude.com/code)
