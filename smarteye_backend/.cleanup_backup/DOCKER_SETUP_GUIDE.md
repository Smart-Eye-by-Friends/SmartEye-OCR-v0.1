# SmartEye Backend - Docker 설정 가이드

## 빠른 시작

### 1. 환경 변수 설정

프로젝트 루트에서 Docker용 환경 파일을 생성:

```bash
cp .env.docker.example .env.docker
```

**필수 환경 변수 설정:**
- `SECRET_KEY`: Django 시크릿 키 (프로덕션에서 반드시 변경)
- `DATABASE_PASSWORD`: PostgreSQL 비밀번호
- `OPENAI_API_KEY`: OpenAI API 키 (AI 기능 사용 시)

### 2. Docker 실행

```bash
# 기본 서비스 실행 (web, db, redis, celery)
docker-compose up -d

# 모니터링 포함 (Flower)
docker-compose --profile monitoring up -d

# 프로덕션 환경 (Nginx 포함)
docker-compose --profile production up -d
```

### 3. 서비스 확인

- **웹 애플리케이션**: http://localhost:8000
- **관리자 페이지**: http://localhost:8000/admin/
- **API 문서**: http://localhost:8000/api/docs/
- **헬스 체크**: http://localhost:8000/api/v1/health/
- **Flower (모니터링)**: http://localhost:5555 (monitoring 프로파일 시)

## 컨테이너 구성

### 핵심 서비스

1. **web** - Django 웹 애플리케이션
   - 포트: 8000
   - Gunicorn으로 실행
   - 정적 파일 서빙

2. **db** - PostgreSQL 데이터베이스
   - 포트: 5432
   - 데이터 영구 저장

3. **redis** - 캐시 및 메시지 브로커
   - 포트: 6379
   - Celery 브로커 역할

4. **celery-worker** - 비동기 작업 처리
   - AI/ML 모델 실행
   - 파일 처리 작업

5. **celery-beat** - 주기적 작업 스케줄러
   - 데이터베이스 기반 스케줄링

### 선택적 서비스

6. **flower** (`--profile monitoring`)
   - Celery 모니터링 대시보드
   - 포트: 5555

7. **nginx** (`--profile production`)
   - 리버스 프록시
   - 정적 파일 서빙
   - 포트: 80, 443

## 환경별 설정

### 개발 환경

```bash
# 개발용 구성으로 실행
docker-compose -f docker-compose.dev.yml up -d
```

**특징:**
- 코드 변경 시 자동 재로드
- 디버그 모드 활성화
- 상세한 로깅

### 프로덕션 환경

```bash
# 프로덕션 구성으로 실행
docker-compose --profile production up -d
```

**특징:**
- Nginx를 통한 정적 파일 서빙
- SSL/TLS 지원 준비
- 보안 설정 강화

## 볼륨 관리

### 영구 데이터

- `postgres_data`: PostgreSQL 데이터
- `redis_data`: Redis 데이터
- `media_files`: 업로드된 파일들
- `static_files`: 정적 파일들

### 백업

```bash
# 데이터베이스 백업
docker-compose exec db pg_dump -U smarteye_user smarteye_db > backup.sql

# 미디어 파일 백업
docker cp smarteye_backend_web_1:/app/media ./media_backup
```

## 로그 확인

```bash
# 전체 서비스 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f web
docker-compose logs -f celery-worker

# 애플리케이션 로그 (호스트에서)
tail -f logs/django.log
```

## 문제 해결

### 일반적인 문제

1. **컨테이너 시작 실패**
```bash
# 컨테이너 상태 확인
docker-compose ps

# 빌드 캐시 제거 후 재빌드
docker-compose build --no-cache
```

2. **데이터베이스 연결 오류**
```bash
# DB 컨테이너 재시작
docker-compose restart db

# 연결 테스트
docker-compose exec web python manage.py dbshell
```

3. **Celery 작업 실패**
```bash
# Celery 워커 상태 확인
docker-compose exec celery-worker celery -A smarteye inspect ping

# 작업 큐 확인
docker-compose exec web python manage.py shell -c "
from apps.analysis.tasks import *
from celery import current_app
print(current_app.control.inspect().registered())
"
```

### 성능 최적화

1. **메모리 사용량 조정**
   - `.env.docker`에서 `SMARTEYE_MAX_WORKERS` 조정
   - `SMARTEYE_MEMORY_LIMIT_MB` 설정

2. **워커 수 조정**
```bash
# docker-compose.yml에서 concurrency 값 조정
command: >
  celery -A smarteye worker 
  --concurrency=4  # CPU 코어 수에 맞게 조정
```

## 보안 고려사항

### 프로덕션 환경

1. **필수 변경 사항**
   - `SECRET_KEY` 변경
   - 강력한 데이터베이스 비밀번호 설정
   - `DEBUG=False` 확인

2. **네트워크 보안**
   - 방화벽 설정
   - HTTPS 설정 (Let's Encrypt 등)
   - 불필요한 포트 차단

3. **정기적인 업데이트**
   - 베이스 이미지 업데이트
   - 의존성 보안 패치 적용

## 개발 워크플로우

### 코드 변경

```bash
# 애플리케이션만 재빌드
docker-compose build web celery-worker

# 특정 서비스만 재시작
docker-compose restart web

# 새로운 의존성 설치 후
docker-compose build --no-cache
```

### 데이터베이스 마이그레이션

```bash
# 마이그레이션 생성
docker-compose exec web python manage.py makemigrations

# 마이그레이션 적용
docker-compose exec web python manage.py migrate
```

### 슈퍼유저 생성

```bash
docker-compose exec web python manage.py createsuperuser
```

## 모니터링

### 애플리케이션 메트릭

- **Flower**: http://localhost:5555
- **헬스 체크**: http://localhost:8000/api/v1/health/
- **시스템 상태**: http://localhost:8000/api/v1/status/

### 리소스 모니터링

```bash
# 컨테이너 리소스 사용량
docker stats

# 특정 컨테이너 메트릭
docker exec smarteye_backend_web_1 top
```

---

**참고**: 더 자세한 설정은 `CLAUDE.md` 파일을 확인하세요.