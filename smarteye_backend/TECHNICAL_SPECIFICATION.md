# SmartEye Backend 기술 명세서

## 📋 시스템 개요

**SmartEye Backend**는 교육 문서를 AI 기반으로 분석하여 시각장애인을 위한 접근 가능한 형태로 변환하는 Django REST API 시스템입니다.

### 핵심 기능
- **LAM (Layout Analysis Module)**: DocLayout-YOLO 기반 문서 레이아웃 분석
- **TSPM (Text & Scene Processing Module)**: OCR 텍스트 추출 및 이미지 설명 생성
- **CIM (Content Integration Module)**: 결과 통합 및 다양한 형식 출력

## 🏗️ 시스템 아키텍처

### 기술 스택

| 레이어 | 기술 | 버전 | 용도 |
|--------|------|------|------|
| **웹 프레임워크** | Django | 4.2.7 | REST API 서버 |
| **API 프레임워크** | Django REST Framework | 3.14.0 | API 엔드포인트 |
| **데이터베이스** | PostgreSQL | 15 | 데이터 저장 (32개 테이블) |
| **캐시/큐** | Redis | 7 | 캐싱 및 메시지 브로커 |
| **작업 큐** | Celery | 5.3.4 | 비동기 작업 처리 |
| **AI/ML** | PyTorch | 2.2.0 | 딥러닝 모델 실행 |
| **YOLO** | Ultralytics | 8.0.200 | 문서 레이아웃 탐지 |
| **OCR** | Tesseract | 4+ | 텍스트 추출 |
| **컨테이너** | Docker + Compose | 20.10+ | 서비스 컨테이너화 |
| **웹서버** | Gunicorn + Nginx | - | 프로덕션 서빙 |

### 서비스 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client Apps   │───▶│   Load Balancer │───▶│   Django Web    │
│ (Frontend/API)  │    │     (Nginx)     │    │   (Gunicorn)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   PostgreSQL    │◀───│   Redis Cache   │◀───│  Celery Workers │
│   (Database)    │    │   (Session)     │    │   (AI Tasks)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📊 데이터베이스 스키마

### 주요 테이블 구조

#### 분석 작업 관리
- **analysis_jobs**: 분석 작업 정보 및 상태 관리
- **processed_images**: 처리된 이미지 메타데이터
- **analysis_results**: 통합 분석 결과

#### AI 모듈별 결과
- **lam_layout_detections**: LAM 레이아웃 탐지 결과
- **tspm_ocr_results**: TSPM OCR 텍스트 추출 결과  
- **tspm_image_descriptions**: TSPM 이미지 설명 결과
- **cim_outputs**: CIM 최종 통합 결과
- **cim_integrated_results**: CIM 페이지별 통합 결과

#### 부가 데이터
- **document_structure**: 문서 구조 분석 결과
- **files_uploadedfile**: 업로드된 파일 관리
- **auth_user**: 사용자 인증 정보

### 인덱스 전략
```sql
-- 성능 최적화를 위한 주요 인덱스
CREATE INDEX analysis_jobs_user_status_idx ON analysis_jobs (user_id, status, created_at);
CREATE INDEX analysis_jobs_status_priority_idx ON analysis_jobs (status, priority, created_at);
CREATE INDEX analysis_results_job_idx ON analysis_results (job_id);
CREATE INDEX lam_detections_image_idx ON lam_layout_detections (image_id);
```

## 🔄 처리 워크플로우

### 1. 파일 업로드 및 전처리
```python
POST /api/v1/analysis/jobs/upload_and_analyze/
├── 파일 형식 검증 (.jpg, .jpeg, .png, .pdf, .bmp, .tiff)
├── 파일 크기 검증 (최대 50MB)
├── 이미지 변환 (PDF → 이미지 페이지별 분할)
└── 분석 작업 큐 등록
```

### 2. LAM (Layout Analysis Module)
```python
# DocLayout-YOLO 모델을 사용한 레이아웃 탐지
Model: yolo11n-doclay
Classes: text, title, list, table, figure, caption 등
Output: 바운딩박스, 신뢰도, 클래스 정보
```

### 3. TSPM (Text & Scene Processing Module)
```python
# OCR 텍스트 추출
Engine: Tesseract OCR
Languages: kor, eng
Config: --psm 6 --oem 3

# 이미지 설명 생성
API: OpenAI GPT-4 Vision
Model: gpt-4-turbo
Prompt: 한국어 교육 문서 이미지 설명 생성
```

### 4. CIM (Content Integration Module)
```python
# 결과 통합 및 구조화
Integration: LAM + TSPM 결과 병합
Structure: 문서 계층 구조 생성
Output: JSON, 점자, PDF 형식
```

## 🚀 API 엔드포인트

### 인증
```http
POST /api/v1/auth/jwt/create/        # JWT 토큰 발급
POST /api/v1/auth/jwt/refresh/       # 토큰 갱신
POST /api/v1/auth/users/             # 사용자 등록
```

### 파일 관리
```http
GET    /api/v1/files/                # 파일 목록
POST   /api/v1/files/upload/         # 파일 업로드
DELETE /api/v1/files/{id}/           # 파일 삭제
```

### 분석 작업
```http
POST /api/v1/analysis/jobs/upload_and_analyze/  # 분석 시작
GET  /api/v1/analysis/jobs/                     # 작업 목록
GET  /api/v1/analysis/jobs/{id}/                # 작업 상세
GET  /api/v1/analysis/jobs/{id}/progress/       # 진행률
GET  /api/v1/analysis/jobs/{id}/results/        # 결과 조회
POST /api/v1/analysis/jobs/{id}/cancel/         # 작업 취소
```

### 결과 관리
```http
GET /api/v1/analysis/results/                   # 결과 목록
GET /api/v1/analysis/results/{id}/              # 결과 상세
GET /api/v1/analysis/results/{id}/download/     # 결과 다운로드
```

## ⚙️ 배포 및 운영

### Docker 서비스 구성
```yaml
services:
  web:           # Django API 서버 (포트 8000)
  db:            # PostgreSQL 데이터베이스 (포트 5433)  
  redis:         # Redis 캐시/브로커 (포트 6379)
  celery-worker: # Celery 워커 (백그라운드 작업)
  flower:        # Celery 모니터링 (포트 5555)
```

### 환경 설정
```bash
# 필수 환경 변수
SECRET_KEY=django-secret-key
DATABASE_PASSWORD=db-password
OPENAI_API_KEY=openai-api-key

# 성능 튜닝
SMARTEYE_BATCH_SIZE=2
SMARTEYE_MAX_WORKERS=2
SMARTEYE_MEMORY_LIMIT_MB=512
```

### 모니터링
- **헬스체크**: `/api/v1/health/`
- **Celery 모니터링**: http://localhost:5555
- **Django Admin**: http://localhost:8000/admin
- **API 문서**: http://localhost:8000/api/v1/docs/

## 🔒 보안 고려사항

### 인증 및 권한
- **JWT 기반 인증**: 토큰 기반 API 접근 제어
- **사용자 권한**: Django 권한 시스템 활용
- **CORS 설정**: 허용된 도메인만 API 접근

### 데이터 보안
- **파일 업로드 검증**: 파일 형식, 크기 제한
- **API 레이트 리미팅**: DRF 쓰로틀링 적용
- **데이터베이스 암호화**: PostgreSQL TLS 연결

### 운영 보안
```bash
# 프로덕션 환경 설정
DEBUG=False
SECURE_SSL_REDIRECT=True
SECURE_HSTS_SECONDS=31536000
SECURE_CONTENT_TYPE_NOSNIFF=True
```

## 📈 성능 최적화

### 데이터베이스 최적화
- **연결 풀링**: pgbouncer 사용
- **인덱스 최적화**: 쿼리 패턴 기반 인덱스 설계
- **N+1 쿼리 방지**: select_related, prefetch_related 활용

### 캐싱 전략
```python
# Redis 캐싱 레이어
CACHES = {
    'default': {
        'BACKEND': 'django_redis.cache.RedisCache',
        'LOCATION': 'redis://redis:6379/1',
        'TIMEOUT': 300,
    }
}
```

### AI 모델 최적화
- **모델 캐싱**: 메모리에 모델 로드 유지
- **배치 처리**: 여러 이미지 동시 처리
- **GPU 활용**: CUDA 지원 환경에서 GPU 가속

## 🧪 테스트 전략

### 테스트 구조
```bash
tests/
├── test_api_endpoints.py      # API 엔드포인트 테스트
├── test_file_processors.py    # 파일 처리 테스트
├── test_pipeline_integration.py # 파이프라인 통합 테스트
└── test_models.py             # 모델 단위 테스트
```

### CI/CD
- **자동 테스트**: GitHub Actions
- **코드 품질**: flake8, black, isort
- **커버리지**: coverage.py (>90% 목표)

## 📞 지원 및 유지보수

### 문제 해결
- **로그 위치**: `/app/logs/django.log`
- **Celery 모니터링**: Flower 대시보드
- **데이터베이스 상태**: `python verify_database.py`

### 백업 및 복구
```bash
# 데이터베이스 백업
docker compose exec db pg_dump -U smarteye_user smarteye_db > backup.sql

# 파일 백업
docker volume create backup_vol
docker run --rm -v smarteye_media:/source -v backup_vol:/backup alpine tar czf /backup/media_backup.tar.gz -C /source .
```

---

**최종 업데이트**: 2025-08-13  
**문서 버전**: 1.0.0
