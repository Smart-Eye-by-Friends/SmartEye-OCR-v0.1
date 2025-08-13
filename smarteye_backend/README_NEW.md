# SmartEye Backend 🤖👁️

[![Docker](https://img.shields.io/badge/Docker-20.10+-blue.svg)](https://docker.com/)
[![Python](https://img.shields.io/badge/Python-3.12-green.svg)](https://python.org/)
[![Django](https://img.shields.io/badge/Django-4.2.7-darkgreen.svg)](https://djangoproject.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791.svg)](https://postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

**SmartEye Backend**은 AI 기반 문서 분석 및 접근성 향상을 위한 Django REST API 서버입니다. 교육 문서(이미지, PDF)를 3단계 처리 파이프라인을 통해 시각장애인을 위한 접근 가능한 형태로 변환합니다.

## ✨ 주요 기능

- **🔍 지능형 레이아웃 분석**: DocLayout-YOLO 기반 문서 구조 인식
- **📝 텍스트 추출**: Tesseract OCR 및 OpenAI 기반 이미지 설명
- **♿ 접근성 향상**: 점자, 음성, 구조화된 텍스트 변환
- **🚀 비동기 처리**: Celery 기반 대용량 문서 처리
- **📊 실시간 모니터링**: 처리 진행률 및 상태 추적

## 🏗️ 시스템 아키텍처

### 3단계 처리 파이프라인

```
📄 입력 문서 (이미지/PDF)
           ↓
🔍 LAM (Layout Analysis Module)
   • DocLayout-YOLO 기반 레이아웃 탐지
   • 텍스트 블록, 이미지, 표, 도형 등 식별
           ↓
🔤 TSPM (Text & Scene Processing Module)  
   • Tesseract OCR로 텍스트 추출
   • OpenAI GPT-4 Vision으로 이미지 설명
           ↓
🎨 CIM (Content Integration Module)
   • 모든 결과 통합 및 구조화
   • 점자, JSON, PDF 등 다양한 형식 출력
           ↓
📊 최종 결과 (접근 가능한 형태)
```

### 기술 스택

| 구성요소 | 기술 스택 |
|----------|-----------|
| **웹 프레임워크** | Django 4.2.7 + Django REST Framework |
| **데이터베이스** | PostgreSQL 15 (32개 테이블) |
| **캐시/큐** | Redis 7 + Celery 5.3.4 |
| **AI/ML** | PyTorch 2.2, Ultralytics YOLOv8, Tesseract |
| **컨테이너** | Docker + Docker Compose |
| **모니터링** | Flower (Celery 모니터링) |

### 서비스 구성

| 서비스 | 포트 | 용도 |
|--------|------|------|
| **web** | 8000 | Django API 서버 |
| **db** | 5433 | PostgreSQL 데이터베이스 |
| **redis** | 6379 | 캐시 및 메시지 브로커 |
| **celery-worker** | - | 백그라운드 작업 처리 |
| **flower** | 5555 | Celery 모니터링 (선택사항) |

## ⚡ 빠른 시작

### 1. 프로젝트 클론
```bash
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye-OCR-v0.1/smarteye_backend
```

### 2. 환경 설정
```bash
# 환경 변수 파일 복사
cp .env.docker.example .env.docker

# 필수 환경 변수 설정
nano .env.docker  # 또는 다른 에디터 사용
```

**필수 설정 항목:**
- `SECRET_KEY`: Django 비밀 키
- `OPENAI_API_KEY`: OpenAI API 키 (이미지 설명 기능용)
- `DATABASE_PASSWORD`: 데이터베이스 비밀번호

### 3. 서비스 실행
```bash
# 모든 서비스 빌드 및 실행
docker compose up --build -d

# 실행 상태 확인
docker compose ps
```

### 4. 초기 설정
```bash
# 데이터베이스 마이그레이션
docker compose exec web python manage.py migrate

# 슈퍼유저 생성
docker compose exec web python manage.py createsuperuser
```

### 5. 접속 확인
- **API 서버**: http://localhost:8000
- **관리자 패널**: http://localhost:8000/admin
- **Celery 모니터링**: http://localhost:5555 (선택사항)

> 📖 **상세한 설정은 [개발자 가이드](DEVELOPER_SETUP_GUIDE.md)를 참조하세요**

## 📖 주요 문서

| 문서 | 용도 | 대상 |
|------|------|------|
| **[빠른 시작 가이드](QUICKSTART.md)** | 5분 만에 실행하고 테스트 | 신규 사용자 |
| **[개발자 설정 가이드](DEVELOPER_SETUP_GUIDE.md)** | 상세한 환경 설정 및 개발 도구 | 개발자 |
| **[API 사용 예제](API_USAGE_EXAMPLES.md)** | REST API 사용법 및 예시 코드 | 프론트엔드 개발자 |
| **[상세 문서](DOCUMENTATION.md)** | 전체 시스템 아키텍처 및 기술 명세 | 시스템 관리자 |
| **[문서 인덱스](DOCUMENTATION_INDEX.md)** | 모든 문서의 통합 목록 | 모든 사용자 |

## 🚀 주요 API 엔드포인트

### 인증
- `POST /api/v1/auth/jwt/create/` - JWT 토큰 발급
- `POST /api/v1/auth/jwt/refresh/` - 토큰 갱신

### 파일 관리
- `GET /api/v1/files/` - 파일 목록 조회
- `POST /api/v1/files/upload/` - 파일 업로드

### 분석 작업
- `POST /api/v1/analysis/jobs/upload_and_analyze/` - 파일 업로드 및 분석 시작
- `GET /api/v1/analysis/jobs/{id}/progress/` - 분석 진행률 조회
- `GET /api/v1/analysis/jobs/{id}/results/` - 분석 결과 조회
- `POST /api/v1/analysis/jobs/{id}/cancel/` - 분석 작업 취소

### 결과 관리
- `GET /api/v1/analysis/results/` - 분석 결과 목록
- `GET /api/v1/analysis/results/{id}/download/` - 결과 파일 다운로드

## 🔧 개발 도구

### 로컬 개발
```bash
# 개발용 컨테이너 실행 (코드 변경 실시간 반영)
docker compose -f docker-compose.dev.yml up -d

# 로그 모니터링
docker compose logs -f web

# Celery 워커 상태 확인
docker compose exec celery-worker celery -A smarteye inspect active
```

### 테스트
```bash
# 전체 테스트 실행
docker compose exec web python manage.py test

# 특정 앱 테스트
docker compose exec web python manage.py test apps.analysis

# 코드 커버리지 확인
docker compose exec web coverage run manage.py test
docker compose exec web coverage report
```

### 데이터베이스 관리
```bash
# 마이그레이션 생성
docker compose exec web python manage.py makemigrations

# 마이그레이션 적용
docker compose exec web python manage.py migrate

# 데이터베이스 초기화
docker compose exec web python manage.py flush
```

## 🐛 문제 해결

### 일반적인 문제

1. **포트 충돌**
   ```bash
   # 사용 중인 포트 확인
   sudo netstat -tulpn | grep :8000
   # 또는 다른 포트 사용
   docker compose -f docker-compose.dev.yml up -d
   ```

2. **권한 문제**
   ```bash
   # Docker 권한 추가
   sudo usermod -aG docker $USER
   # 로그아웃 후 재로그인
   ```

3. **메모리 부족**
   ```bash
   # Docker 메모리 할당 증가 (Docker Desktop)
   # Settings > Resources > Advanced > Memory
   ```

### 로그 확인
```bash
# 모든 서비스 로그
docker compose logs -f

# 특정 서비스 로그
docker compose logs -f web
docker compose logs -f celery-worker
docker compose logs -f db
```

## 🤝 기여하기

1. **이슈 리포트**: [GitHub Issues](https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1/issues)
2. **코드 기여**: Fork → Branch → PR
3. **문서 개선**: 문서 수정 후 PR 제출

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 📞 지원

- **GitHub Issues**: 버그 리포트 및 기능 요청
- **이메일**: smart.eye.by.friends@gmail.com
- **문서**: [전체 문서 목록](DOCUMENTATION_INDEX.md)
