# SmartEye Backend 🤖👁️

[![Docker](https://img.shields.io/badge/Docker-20.10+-blue.svg)](https://docker.com/)
[![Python](https://img.shields.io/badge/Python-3.12-green.svg)](https://python.org/)
[![Django](https://img.shields.io/badge/Django-4.2.7-darkgreen.svg)](https://djangoproject.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791.svg)](https://postgresql.org/)

**SmartEye Backend**은 AI 기반 문서 분석 및 접근성 향상을 위한 Django REST API 서버입니다. 이미지와 PDF 문서를 3단계 처리 파이프라인(LAM → TSPM → CIM)을 통해 시각적으로 접근 가능한 형태로 변환합니다.

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   LAM Module    │───▶│   TSPM Module    │───▶│   CIM Module    │
│ (Layout Analysis)│    │ (Text & Scene    │    │ (Content        │
│                 │    │  Processing)     │    │  Integration)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
   YOLO 기반 레이아웃       OCR + AI 이미지          텍스트, 점자, PDF
   요소 탐지 및 분할       설명 생성                통합 결과 생성
```

## ✅ 사전 준비 사항

프로젝트를 실행하기 위해 다음 프로그램들이 **반드시 설치되어 있어야 합니다**:

### 필수 설치 프로그램
- **Git** (2.30+): 소스 코드 클론용
  ```bash
  # 설치 확인
  git --version
  ```

- **Docker** (20.10+): 컨테이너 실행 환경
  ```bash
  # 설치 확인
  docker --version
  docker info
  ```

- **Docker Compose** (2.0+): 멀티 컨테이너 관리
  ```bash
  # 설치 확인
  docker compose version
  ```

### 시스템 요구사항
- **운영체제**: Linux, macOS, Windows 10/11 (WSL2 권장)
- **메모리**: 최소 8GB RAM (16GB 권장)
- **디스크**: 최소 10GB 여유 공간
- **네트워크**: 인터넷 연결 (AI 모델 다운로드 및 API 사용)

## 🚀 설치 및 실행 방법

### 1. 소스 코드 클론
GitHub 리포지토리를 로컬 환경으로 복제합니다:

```bash
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye-OCR-v0.1/smarteye_backend
```

### 2. 환경 변수 설정
Docker 환경용 설정 파일을 복사하고 필요에 따라 수정합니다:

```bash
# Docker용 환경 변수 파일 복사
cp .env.docker.example .env.docker
```

#### 🔧 주요 환경 변수 설정

반드시 수정해야 할 환경 변수들:

```bash
# .env.docker 파일을 텍스트 에디터로 열어 다음 항목들을 수정하세요

# 🔐 보안 설정 (운영 환경에서는 반드시 변경!)
SECRET_KEY=your-unique-secret-key-here-change-in-production
DATABASE_PASSWORD=your-secure-database-password

# 🤖 OpenAI API 설정 (AI 이미지 설명 기능 사용 시 필요)
OPENAI_API_KEY=your-openai-api-key-here

# 👤 관리자 계정 설정
DJANGO_SUPERUSER_USERNAME=admin
DJANGO_SUPERUSER_EMAIL=admin@yourcompany.com
DJANGO_SUPERUSER_PASSWORD=your-admin-password

# 🌐 네트워크 설정 (필요 시)
ALLOWED_HOSTS=localhost,127.0.0.1,your-domain.com
```

#### 📝 선택적 설정 항목

```bash
# 🎛️ AI/ML 성능 튜닝
SMARTEYE_BATCH_SIZE=1          # 배치 크기 (메모리에 따라 조정)
SMARTEYE_MAX_WORKERS=2         # Celery 워커 수
SMARTEYE_MEMORY_LIMIT_MB=512   # 메모리 제한

# 📊 개발/디버그 설정
DEBUG=True                     # 개발 모드 (운영 시 False)
SMARTEYE_DEBUG_MODE=True       # 디버그 이미지 저장
SMARTEYE_SAVE_DEBUG_IMAGES=True
```

### 3. Docker 컨테이너 빌드 및 실행

프로덕션 환경에서 모든 서비스를 한 번에 실행:

```bash
# 모든 서비스 빌드 및 백그라운드 실행
docker compose up --build -d

# 특정 서비스만 실행하는 경우
docker compose up -d db redis web celery-worker
```

#### 개발 환경에서 실행 (코드 변경 실시간 반영):

```bash
# 개발용 설정으로 실행
docker compose -f docker-compose.dev.yml up --build -d
```

#### 모니터링 도구 포함 실행:

```bash
# Flower(Celery 모니터링) 포함 실행
docker compose --profile monitoring up --build -d
```

### 4. 실행 상태 확인

```bash
# 모든 서비스 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f

# 특정 서비스 로그 확인
docker compose logs -f web
docker compose logs -f celery-worker
```

## 🩺 실행 확인

### 1. 웹 서비스 동작 확인

**헬스체크 API 테스트:**
```bash
# 기본 헬스체크
curl http://localhost:8000/api/v1/health/

# 예상 응답:
{
    "status": "healthy",
    "database": "healthy", 
    "debug": true,
    "version": "1.0.0"
}
```

### 2. 웹 브라우저 접속 확인

다음 URL들에 접속하여 서비스가 정상 동작하는지 확인하세요:

#### 🌐 메인 서비스
- **API 메인**: http://localhost:8000/api/v1/
- **헬스체크**: http://localhost:8000/api/v1/health/
- **Django Admin**: http://localhost:8000/admin/
  - ID/PW: `.env.docker` 파일의 `DJANGO_SUPERUSER_*` 값 사용

#### 📚 API 문서 
- **Swagger UI**: http://localhost:8000/api/v1/docs/
- **ReDoc**: http://localhost:8000/api/v1/redoc/
- **OpenAPI 스키마**: http://localhost:8000/api/schema/

#### 🔍 모니터링 (선택사항)
- **Flower (Celery 모니터링)**: http://localhost:5555/
  - ID/PW: `.env.docker` 파일의 `FLOWER_USER/FLOWER_PASSWORD` 값 사용

### 3. API 엔드포인트 테스트

#### 회원가입 테스트:
```bash
curl -X POST http://localhost:8000/api/v1/auth/users/ \
     -H "Content-Type: application/json" \
     -d '{
         "username": "testuser",
         "email": "test@example.com", 
         "password": "testpassword123"
     }'
```

#### JWT 토큰 발급:
```bash
curl -X POST http://localhost:8000/api/v1/auth/jwt/create/ \
     -H "Content-Type: application/json" \
     -d '{
         "username": "testuser",
         "password": "testpassword123"
     }'
```

#### 시스템 상태 확인 (인증 필요):
```bash
curl -X GET http://localhost:8000/api/v1/status/ \
     -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

### 4. 파일 업로드 및 분석 테스트

```bash
# 파일 업로드 및 분석 시작
curl -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
     -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
     -F "files=@/path/to/your/document.pdf" \
     -F "job_name=Test Analysis" \
     -F "enable_ocr=true" \
     -F "enable_description=true"

# 예상 응답:
{
    "job_id": 1,
    "task_id": "abc123-def456",
    "status": "processing",
    "message": "SmartEye 완전 분석이 시작되었습니다.",
    "total_images": 5
}
```

## ⏹️ 중지 방법

### 일반 중지 (데이터 보존):
```bash
# 모든 서비스 중지 (데이터 볼륨은 보존)
docker compose down

# 개발 환경 중지
docker compose -f docker-compose.dev.yml down
```

### 완전 정리 (데이터 삭제):
```bash
# 볼륨과 네트워크까지 모두 삭제
docker compose down -v --remove-orphans

# 빌드 이미지까지 삭제
docker compose down --rmi all -v --remove-orphans
```

### 특정 서비스만 재시작:
```bash
# 웹 서비스만 재시작
docker compose restart web

# 특정 서비스 재빌드 후 재시작  
docker compose up --build -d web
```

## 🔧 주요 서비스 구성

| 서비스 | 포트 | 설명 | 헬스체크 |
|--------|------|------|----------|
| **web** | 8000 | Django API 서버 | http://localhost:8000/api/v1/health/ |
| **db** | 5432 | PostgreSQL 데이터베이스 | 자동 (Docker 내부) |
| **redis** | 6379 | Redis (캐시/큐) | 자동 (Docker 내부) |
| **celery-worker** | - | AI/ML 작업 처리 | 자동 (Celery ping) |
| **celery-beat** | - | 주기적 작업 스케줄러 | 자동 |
| **flower** | 5555 | Celery 모니터링 (선택) | http://localhost:5555/ |
| **nginx** | 80/443 | 리버스 프록시 (운영용) | 자동 |

## 📁 주요 디렉토리 구조

```
smarteye_backend/
├── 🐳 Docker 설정
│   ├── Dockerfile                 # 최적화된 멀티스테이지 빌드
│   ├── docker-compose.yml         # 운영환경 설정
│   ├── docker-compose.dev.yml     # 개발환경 설정
│   ├── docker-entrypoint.sh       # 컨테이너 초기화 스크립트
│   └── healthcheck.sh            # 종합적 헬스체크
├── 📱 Django 애플리케이션
│   ├── apps/
│   │   ├── analysis/             # 🤖 AI 분석 모듈
│   │   ├── files/                # 📁 파일 관리
│   │   ├── users/                # 👤 사용자 관리  
│   │   └── api/                  # 🌐 공통 API
│   ├── core/                     # 🧠 AI/ML 코어 모듈
│   │   ├── lam/                  # Layout Analysis Module
│   │   ├── tspm/                 # Text & Scene Processing Module
│   │   └── cim/                  # Content Integration Module
│   └── utils/                    # 🛠️ 공통 유틸리티
├── ⚙️ 설정 파일
│   ├── .env.docker              # Docker 환경변수
│   ├── .env.example             # 환경변수 예시
│   └── requirements.txt         # Python 의존성
└── 📚 문서
    ├── README.md                # 이 파일
    ├── IMPROVEMENTS_SUMMARY.md  # 개선사항 요약
    └── CLAUDE.md               # 개발자 참조
```

## 🧪 개발 및 디버깅

### 로그 확인:
```bash
# 실시간 로그 확인
docker compose logs -f web celery-worker

# Django 로그 파일 확인
tail -f logs/django.log

# 특정 서비스 로그만
docker compose logs web | head -100
```

### 컨테이너 내부 접속:
```bash
# Django 컨테이너 셸 접속
docker compose exec web bash

# Django 관리 명령어 실행
docker compose exec web python manage.py shell
docker compose exec web python manage.py collectstatic
docker compose exec web python manage.py migrate
```

### 데이터베이스 접속:
```bash
# PostgreSQL 컨테이너 접속
docker compose exec db psql -U smarteye_user smarteye_db
```

## 🚨 문제 해결

### 자주 발생하는 문제들:

#### 1. 포트 충돌 오류
```bash
# 사용 중인 포트 확인
netstat -tulpn | grep :8000

# Docker compose 포트 변경 후 재실행
docker compose down
docker compose up -d
```

#### 2. 메모리 부족 오류
```bash
# .env.docker에서 설정 조정
SMARTEYE_BATCH_SIZE=1
SMARTEYE_MEMORY_LIMIT_MB=256
SMARTEYE_MAX_WORKERS=1
```

#### 3. AI 모델 다운로드 실패
```bash
# 컨테이너 내부에서 수동 다운로드
docker compose exec web python -c "
from ultralytics import YOLO
model = YOLO('yolo11n.pt')
print('Model downloaded successfully')
"
```

#### 4. 권한 오류
```bash
# 로그 디렉토리 권한 설정
sudo chown -R $USER:$USER logs/
chmod 755 logs/
```

## 📊 성능 최적화

### 리소스 모니터링:
```bash
# Docker 컨테이너 리소스 사용량
docker stats

# 시스템 리소스 확인
docker compose exec web python -c "
import psutil
print(f'CPU: {psutil.cpu_percent()}%')
print(f'Memory: {psutil.virtual_memory().percent}%')
"
```

### 성능 튜닝 옵션:
- **메모리 제한**: `SMARTEYE_MEMORY_LIMIT_MB` 조정
- **워커 수**: `SMARTEYE_MAX_WORKERS` CPU 코어 수에 맞게 설정
- **배치 크기**: `SMARTEYE_BATCH_SIZE` 메모리에 따라 조정
- **Gunicorn 워커**: Dockerfile의 `--workers` 옵션 조정

## 🔒 보안 고려사항

### 운영 환경 배포 시:
```bash
# .env.docker에서 보안 설정 강화
DEBUG=False
SECRET_KEY=your-very-long-and-random-secret-key
ALLOWED_HOSTS=your-domain.com
CORS_ALLOW_ALL_ORIGINS=False
SECURE_SSL_REDIRECT=True
```

### 방화벽 설정 (선택):
```bash
# 필요한 포트만 열기 (예: Ubuntu/Debian)
sudo ufw allow 8000/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

## 📞 지원 및 문의

### 추가 도움이 필요한 경우:
- **이슈 리포트**: [GitHub Issues](https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1/issues)
- **개발 문서**: `CLAUDE.md` 파일 참조
- **개선사항 내역**: `IMPROVEMENTS_SUMMARY.md` 파일 참조

### 개발 참여:
1. 이 저장소를 Fork
2. 기능 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 변경사항 커밋 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 Push (`git push origin feature/amazing-feature`)
5. Pull Request 생성

---

**🎉 SmartEye Backend를 사용해 주셔서 감사합니다!**

*더 나은 접근성과 포용적인 디지털 환경을 만들어가는 여정에 함께해 주세요.*