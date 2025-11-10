# SmartEyeSsen Frontend

> React 19 + Vite 7 + TypeScript 기반 AI 학습지 분석 UI  

## 📚 목차

- [프로젝트 개요](#-프로젝트-개요)
- [폴더 구조](#-폴더-구조)
- [개발 환경 설정](#-개발-환경-설정)
- [주요 기능 & 아키텍처](#-주요-기능--아키텍처)
- [환경 변수](#-환경-변수)
- [Docker & Nginx 구성](#-docker--nginx-구성)
- [테스트 · 품질 관리](#-테스트--품질-관리)
- [트러블슈팅](#-트러블슈팅)
- [참고 자료](#-참고-자료)

---

## 🎯 프로젝트 개요

- SmartEyeSsen 백엔드(API)와 연동해 **문서 업로드 → 분석 → 편집 → 다운로드** 전 과정을 다루는 SPA입니다.
- React 19.1 + TypeScript 5.9 + Vite 7 조합으로 구성되며, 상태는 Context API + 커스텀 훅으로 관리합니다.
- 프로덕션에서는 `Frontend/Dockerfile`로 빌드한 정적 자산을 **nginx**가 서빙하고, `/api` 요청은 백엔드 컨테이너(포트 8000)로 reverse proxy 됩니다.

---

## 📁 폴더 구조

```
Frontend/
├── src/
│   ├── components/
│   │   ├── layout/            # MainLayout, AppShell
│   │   ├── sidebar/           # 문서 타입/모델 선택, 분석 버튼
│   │   ├── slider/            # 페이지 썸네일 슬라이더
│   │   ├── viewer/            # 이미지 & Bounding Box 오버레이
│   │   └── editor/            # 텍스트 에디터, AI 통계
│   ├── contexts/              # Project/Page/Upload 컨텍스트
│   ├── hooks/                 # useUploadProgress, useBoundingBoxes 등
│   ├── services/              # Axios API 래퍼 (인터셉터 포함)
│   ├── styles/                # CSS Modules + 전역 변수
│   ├── utils/                 # CoordinateScaler, formatters
│   ├── types/                 # 공용 타입 정의
│   └── __tests__/             # Vitest + Testing Library 사양
├── public/                    # 정적 리소스
├── default.conf, nginx.conf   # 프로덕션 Nginx 설정
├── Dockerfile                 # 멀티 스테이지 빌드
├── vite.config.ts, vitest.config.ts
└── package.json
```

---

## 🛠 개발 환경 설정

### 필수 버전

- Node.js 18 LTS 이상 (권장 20.x)
- npm 9 이상

### 설치 & 실행

```bash
cd Frontend
npm install
npm run dev -- --host 0.0.0.0 --port 5173
```

환경 변수(`.env`)에서 `VITE_API_BASE_URL`을 지정하지 않으면 기본값 `/api`가 사용됩니다. 로컬 백엔드를 8000번 포트에서 실행하는 경우 아래처럼 덮어쓸 수 있습니다.

```bash
VITE_API_BASE_URL=http://localhost:8000/api npm run dev
```

빌드 & 배포용 미리보기:

```bash
npm run build          # dist/ 생성
npm run preview        # 빌드 결과 로컬 서빙
```

---

## 🧩 주요 기능 & 아키텍처

- **레이아웃 뷰어**: SVG 기반 Bounding Box 레이어, 클래스별 색상, hover 툴팁, 클릭 시 에디터 스크롤 연동.
- **문서 워크플로우**: 사이드바에서 문서 타입·AI 모델 선택 → 파일 업로드 → 분석 → 결과 편집 → 통합 다운로드.
- **텍스트 편집**: Original / Auto Formatted / User Edited 탭, 자동 저장, AI 통계 카드.
- **페이지 내비게이션**: 썸네일 슬라이더 + 키보드 단축키.
- **상태 관리**: Context + useReducer 조합으로 프로젝트/페이지/분석 상태를 분리.
- **서비스 계층**: `src/services/api.ts`에서 Axios 인스턴스를 생성해 헤더, 에러 핸들링을 중앙 집중화.
- **유틸리티**: `CoordinateScaler`, `formatBoundingBox` 등 재사용 가능한 helpers.

---

## 🔧 환경 변수

Vite 규칙에 따라 `VITE_` prefix가 붙어야 하며 `.env`, `.env.production` 등에 정의할 수 있습니다.

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `VITE_API_BASE_URL` | 백엔드 API 베이스 URL | `/api` (Nginx reverse proxy 기준) |
| `VITE_APP_ENV` (선택) | 빌드 타겟 구분 | `development` |

프로덕션 Docker 빌드 시 `docker-compose.prod.yml`의 `frontend` 서비스가 `VITE_API_BASE_URL=/api` 인자를 전달합니다.

---

## 🐳 Docker & Nginx 구성

### Frontend/Dockerfile

1. **Builder (node:18-alpine)**  
   - `npm ci --legacy-peer-deps` 후 `npm run build`  
   - `ARG VITE_API_BASE_URL` → 빌드 시점 환경 변수 주입
2. **Runtime (nginx:alpine)**  
   - `nginx.conf`, `default.conf`를 복사  
   - `/usr/share/nginx/html`에 `dist/` 결과물 복사  
   - `/var/www/certbot` 디렉터리 생성 (HTTP-01 챌린지)  
   - 헬스체크: `curl -f http://localhost/`

### Nginx 기본 구성 (`default.conf`)

- HTTP 80 → HTTPS 443 리다이렉트
- `/api/` → `http://backend:8000/api/` (reverse proxy)  
  - `proxy_read_timeout 300s`로 AI 추론 대기시간 처리  
  - `client_max_body_size 100M`으로 대용량 업로드 허용
- `/uploads/`, `/docs`, `/health`, `/redoc`, `/openapi.json` 등은 각각 백엔드에 프록시
- `/var/www/certbot` 경로를 Certbot 컨테이너와 공유해 SSL 자동 갱신

### docker-compose.prod.yml 연동

```yaml
frontend:
  build:
    context: ./Frontend
    dockerfile: Dockerfile
    args:
      VITE_API_BASE_URL: /api
  container_name: smarteyessen_frontend
  ports:
    - "80:80"
    - "443:443"
  depends_on:
    - backend
  volumes:
    - ./certbot/conf:/etc/letsencrypt
    - ./certbot/www:/var/www/certbot
```

Nginx는 backend 컨테이너 이름(`backend`)을 DNS로 인식하므로 별도 주소 설정이 필요 없습니다.

---

## ✅ 테스트 · 품질 관리

| 명령 | 설명 |
|------|------|
| `npm run test` | Vitest + Testing Library 단위 테스트 |
| `npm run test -- --watch` | watch 모드 |
| `npm run test -- --coverage` | 커버리지 리포트 |
| `npm run lint` | ESLint (React + TypeScript 설정) |

CI 전 `npm run lint && npm run test` 실행을 권장하며, PR에는 주요 화면 변경 시 스크린샷 또는 동영상을 첨부합니다.

---

## 🩺 트러블슈팅

| 증상 | 원인/조치 |
|------|-----------|
| `npm run dev` 포트 충돌 | `npm run dev -- --port 5174` 등 다른 포트 사용 |
| API 404 또는 CORS | `VITE_API_BASE_URL` 확인, 로컬 모드에서는 `http://localhost:8000/api` 지정 |
| 빌드 시 메모리 부족 | `NODE_OPTIONS=--max-old-space-size=4096 npm run build` |
| Nginx 502 Bad Gateway | 백엔드 컨테이너 상태 (`docker compose logs backend`) 확인, `/api` 프록시 경로 점검 |
| TLS 인증서 오류 | `certbot` 컨테이너가 실행 중인지 확인하고 `docker compose -f docker-compose.prod.yml run --rm certbot renew` 실행 |

---

## 📎 참고 자료

- 루트 `README.md` – 전체 시스템/배포 개요
- `default.conf`, `nginx.conf` – 프로덕션 reverse proxy 설정