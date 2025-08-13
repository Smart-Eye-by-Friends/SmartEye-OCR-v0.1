# SmartEye Backend 개발자 환경설정 가이드 🚀

이 문서는 SmartEye Backend 프로젝트를 처음 설정하는 개발자를 위한 상세한 가이드입니다.

## 🎉 테스트 완료 상태 (2025-08-11)

✅ **환경설정 및 테스트 성공적으로 완료됨**

### 검증된 구성요소:
- ✅ Docker 환경 구성 및 모든 서비스 시작
- ✅ PostgreSQL 데이터베이스 연결 (포트 5433)
- ✅ Redis 캐시 서버 연결
- ✅ Django 웹서버 (포트 8000)
- ✅ Celery 워커 및 Flower 모니터링
- ✅ JWT 인증 시스템 
- ✅ 파일 업로드 및 SourceFile 모델
- ✅ LAM (Layout Analysis Module) 서비스
- ⚠️ TSPM (Text Structure Processing Module) - OpenAI 클라이언트 이슈
- ✅ CIM (Content Integration Module) 서비스
- ✅ 전체 파이프라인 워크플로우 테스트

### 데이터베이스 현황:
- 32개 테이블 생성 완료
- 2명 사용자 생성 (admin, pipeline_test_user)
- 4개 분석 작업 생성 (2개 완료)
- 3개 파일 업로드 테스트

## 📋 목차

1. [사전 준비사항](#-사전-준비사항)
2. [환경 설정](#-환경-설정)
3. [Docker 환경 구축](#-docker-환경-구축)
4. [데이터베이스 연결 테스트](#-데이터베이스-연결-테스트)
5. [LAM→TSPM→CIM 파이프라인 테스트](#-lamtspencim-파이프라인-테스트)
6. [데이터베이스 저장 확인](#-데이터베이스-저장-확인)
7. [트러블슈팅](#-트러블슈팅)

---

## 🛠 사전 준비사항

### 필수 소프트웨어 설치

1. **Git** (2.30+)
```bash
# 설치 확인
git --version

# Ubuntu/Debian
sudo apt update && sudo apt install git

# CentOS/RHEL
sudo yum install git
```

2. **Docker** (20.10+)
```bash
# Docker 설치 (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# 설치 확인
docker --version
docker info
```

3. **Docker Compose** (2.0+)
```bash
# Docker Compose V2는 Docker와 함께 설치됨
docker compose version
```

### 시스템 요구사항 확인

```bash
# 메모리 확인 (최소 8GB 권장)
free -h

# 디스크 공간 확인 (최소 10GB 필요)
df -h

# CPU 코어 수 확인
nproc
```

---

## ⚙️ 환경 설정

### 1. 소스 코드 클론

```bash
# 프로젝트 클론
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye-OCR-v0.1/smarteye_backend

# 브랜치 확인
git branch -a
git checkout 4-feature-smarteye-백엔드-api-서버-구축
```

### 2. 환경 변수 파일 설정

```bash
# Docker 환경 설정 파일 복사
cp .env.docker.example .env.docker

# 환경 변수 파일 편집
nano .env.docker  # 또는 vim, code 등 선호하는 에디터 사용
```

### 3. 필수 환경 변수 설정

`.env.docker` 파일에서 다음 항목들을 **반드시** 수정하세요:

```bash
# 🔐 보안 설정 (운영 환경에서는 반드시 변경!)
SECRET_KEY=your-unique-secret-key-here-change-in-production-$(openssl rand -hex 32)
DATABASE_PASSWORD=smarteye_secure_password_2024

# 🤖 OpenAI API 설정 (AI 이미지 설명 기능 사용 시 필요)
OPENAI_API_KEY=your-openai-api-key-here

# 👤 관리자 계정 설정
DJANGO_SUPERUSER_USERNAME=admin
DJANGO_SUPERUSER_EMAIL=admin@smarteye.com
DJANGO_SUPERUSER_PASSWORD=SmartEye2024!

# 🌐 네트워크 설정
ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0
CORS_ALLOW_ALL_ORIGINS=True

# 🎛️ AI/ML 성능 튜닝 (시스템 사양에 따라 조정)
SMARTEYE_BATCH_SIZE=1
SMARTEYE_MAX_WORKERS=2
SMARTEYE_MEMORY_LIMIT_MB=1024

# 📊 개발/디버그 설정
DEBUG=True
SMARTEYE_DEBUG_MODE=True
SMARTEYE_SAVE_DEBUG_IMAGES=True
```

### 4. 디렉토리 권한 설정

```bash
# 로그 디렉토리 생성 및 권한 설정
mkdir -p logs media static staticfiles
chmod 755 logs media static staticfiles

# Docker 스크립트 실행 권한 부여
chmod +x docker-entrypoint.sh healthcheck.sh docker-manage.sh setup.sh
```

---

## 🐳 Docker 환경 구축

### 1. Docker 이미지 빌드 및 서비스 시작

```bash
# 개발 환경 모든 서비스 빌드 및 시작
docker compose -f docker-compose.dev.yml up --build -d

# 빌드 진행 상황 확인
docker compose -f docker-compose.dev.yml logs -f
```

### 2. 서비스 상태 확인

```bash
# 모든 서비스 상태 확인
docker compose -f docker-compose.dev.yml ps

# 예상 출력:
# NAME               IMAGE              COMMAND                  SERVICE       CREATED         STATUS                   PORTS
# smarteye-web       smarteye_web       "/app/docker-entrypo…"   web           2 minutes ago   Up 2 minutes (healthy)   0.0.0.0:8000->8000/tcp
# smarteye-db        postgres:15-alpine "docker-entrypoint.s…"   db            2 minutes ago   Up 2 minutes (healthy)   5432/tcp
# smarteye-redis     redis:7-alpine     "docker-entrypoint.s…"   redis         2 minutes ago   Up 2 minutes (healthy)   6379/tcp
# smarteye-celery    smarteye_web       "/app/docker-entrypo…"   celery-worker 2 minutes ago   Up 2 minutes (healthy)
# smarteye-flower    smarteye_web       "/app/docker-entrypo…"   flower        2 minutes ago   Up 2 minutes (healthy)   0.0.0.0:5555->5555/tcp
```

### 3. 서비스별 개별 확인

```bash
# 웹 서버 로그 확인
docker compose -f docker-compose.dev.yml logs web

# Celery 워커 로그 확인
docker compose -f docker-compose.dev.yml logs celery-worker

# 데이터베이스 로그 확인
docker compose -f docker-compose.dev.yml logs db
```

---

## 🗄️ 데이터베이스 연결 테스트

### 1. 기본 연결 테스트

```bash
# Django 컨테이너에 접속하여 데이터베이스 연결 테스트
docker compose -f docker-compose.dev.yml exec web python manage.py check --database default

# 예상 출력:
# System check identified no issues (0 silenced).
```

### 2. 마이그레이션 상태 확인

```bash
# 마이그레이션 적용 상태 확인
docker compose -f docker-compose.dev.yml exec web python manage.py showmigrations

# 예상 출력 (모든 마이그레이션이 [X]로 표시되어야 함):
# analysis
#  [X] 0001_initial
#  [X] 0002_initial
#  [X] 0003_initial
# files
#  [X] 0001_initial
#  [X] 0002_initial
# users
#  [X] 0001_initial
```

### 3. 데이터베이스 직접 접속 테스트

```bash
# PostgreSQL 컨테이너에 직접 접속
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db

# SQL 명령어로 테이블 확인
\dt

# 예상 출력:
#                     List of relations
#  Schema |            Name             | Type  |    Owner     
# --------+-----------------------------+-------+--------------
#  public | analysis_analysisjob       | table | smarteye_user
#  public | analysis_analysisresult    | table | smarteye_user
#  public | analysis_processedimage    | table | smarteye_user
#  public | files_sourcefile           | table | smarteye_user
#  public | users_user                 | table | smarteye_user

# 데이터베이스 접속 종료
\q
```

### 4. API 연결 테스트

```bash
# 헬스체크 API 테스트
curl -s http://localhost:8000/api/v1/health/ | jq

# 예상 응답:
# {
#   "status": "healthy",
#   "database": "healthy",
#   "redis": "healthy",
#   "debug": true,
#   "version": "1.0.0",
#   "timestamp": "2024-08-11T12:00:00Z"
# }
```

---

## 🔄 LAM→TSPM→CIM 파이프라인 테스트

### 1. 테스트 사용자 및 인증 토큰 생성

```bash
# Django 컨테이너 접속하여 테스트 스크립트 실행
docker compose -f docker-compose.dev.yml exec web python -c "
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from django.contrib.auth import get_user_model
from rest_framework_simplejwt.tokens import RefreshToken

User = get_user_model()

# 테스트 사용자 생성
user, created = User.objects.get_or_create(
    username='testuser',
    defaults={
        'email': 'test@smarteye.com',
        'first_name': 'Test',
        'last_name': 'User'
    }
)

if created:
    user.set_password('testpassword123')
    user.save()
    print(f'✅ 테스트 사용자 생성됨: {user.username}')
else:
    print(f'✅ 테스트 사용자 존재함: {user.username}')

# JWT 토큰 생성
refresh = RefreshToken.for_user(user)
access_token = str(refresh.access_token)

print(f'🔑 Access Token: {access_token}')
print(f'🔑 Token 길이: {len(access_token)} 문자')
"
```

### 2. 파이프라인 테스트용 샘플 이미지 생성

```bash
# 테스트 이미지 생성 (Python으로 간단한 문서 이미지 생성)
docker compose -f docker-compose.dev.yml exec web python -c "
import os
from PIL import Image, ImageDraw, ImageFont
import io

# 간단한 테스트 문서 이미지 생성
img = Image.new('RGB', (800, 600), color='white')
draw = ImageDraw.Draw(img)

# 텍스트 추가
try:
    # 기본 폰트 사용
    font = ImageFont.load_default()
except:
    font = None

# 문서 제목
draw.text((50, 50), 'SmartEye Test Document', fill='black', font=font)
draw.text((50, 100), 'This is a test document for pipeline testing.', fill='black', font=font)
draw.text((50, 150), 'LAM → TSPM → CIM Pipeline Test', fill='blue', font=font)

# 간단한 도형 추가 (레이아웃 테스트용)
draw.rectangle([50, 200, 750, 300], outline='red', width=2)
draw.text((60, 220), 'Layout Detection Area', fill='red', font=font)

draw.rectangle([50, 350, 350, 450], outline='green', width=2)
draw.text((60, 370), 'Text Block 1', fill='green', font=font)

draw.rectangle([400, 350, 700, 450], outline='blue', width=2)
draw.text((410, 370), 'Text Block 2', fill='blue', font=font)

# 이미지 저장
test_image_path = '/tmp/smarteye_test_document.jpg'
img.save(test_image_path, 'JPEG', quality=95)
print(f'✅ 테스트 이미지 생성됨: {test_image_path}')

# 파일 크기 확인
import os
size = os.path.getsize(test_image_path)
print(f'📊 이미지 크기: {size:,} bytes ({size/1024:.1f} KB)')
"
```

### 3. 전체 파이프라인 실행 테스트

```bash
# 위에서 생성한 토큰을 변수에 저장 (실제 토큰으로 교체)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  # 위에서 출력된 실제 토큰 사용

# 파일 업로드 및 분석 시작
curl -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
     -H "Authorization: Bearer $TOKEN" \
     -F "files=@/tmp/smarteye_test_document.jpg" \
     -F "job_name=Pipeline Test Job" \
     -F "enable_ocr=true" \
     -F "enable_description=true" \
     -F "analysis_type=full"

# 예상 응답:
# {
#     "job_id": 1,
#     "task_id": "abc123-def456-ghi789",
#     "status": "processing",
#     "message": "SmartEye 완전 분석이 시작되었습니다.",
#     "total_images": 1,
#     "estimated_time": "30-60초"
# }
```

### 4. 작업 진행 상태 모니터링

```bash
# 작업 진행 상태 확인 (job_id는 위에서 받은 값 사용)
JOB_ID=1

curl -s -X GET "http://localhost:8000/api/v1/analysis/jobs/$JOB_ID/status/" \
     -H "Authorization: Bearer $TOKEN" | jq

# 진행 중인 작업의 로그 확인
docker compose -f docker-compose.dev.yml logs -f celery-worker

# Flower 모니터링 도구 접속 (브라우저)
echo "🌸 Flower 모니터링: http://localhost:5555"
echo "   ID/PW: admin/admin"
```

### 5. 파이프라인 단계별 진행 확인

```bash
# 실시간 로그 모니터링
docker compose -f docker-compose.dev.yml logs -f celery-worker | grep -E "(LAM|TSPM|CIM|Pipeline)"

# 예상 로그 출력:
# celery-worker | 🔍 LAM 모듈 시작: Layout Analysis
# celery-worker | ✅ LAM 완료: 4개 요소 탐지 (소요시간: 3.2초)
# celery-worker | 🔍 TSPM 모듈 시작: Text & Scene Processing
# celery-worker | ✅ TSPM 완료: OCR 및 설명 생성 (소요시간: 4.7초)
# celery-worker | 🔍 CIM 모듈 시작: Content Integration
# celery-worker | ✅ CIM 완료: 최종 결과 생성 (소요시간: 2.1초)
# celery-worker | 🎉 Pipeline 완료: 총 소요시간 10.0초
```

---

## 💾 데이터베이스 저장 확인

### 1. 직접 데이터베이스 확인

```bash
# PostgreSQL 접속하여 데이터 확인
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db -c "
-- 전체 데이터 개수 확인
SELECT 
    '사용자' as 구분, COUNT(*) as 개수 FROM users_user
UNION ALL
SELECT 
    '업로드된 파일' as 구분, COUNT(*) as 개수 FROM files_sourcefile  
UNION ALL
SELECT 
    '분석 작업' as 구분, COUNT(*) as 개수 FROM analysis_analysisjob
UNION ALL
SELECT 
    '처리된 이미지' as 구분, COUNT(*) as 개수 FROM analysis_processedimage
UNION ALL
SELECT 
    '분석 결과' as 구분, COUNT(*) as 개수 FROM analysis_analysisresult;
"
```

### 2. 파이프라인 단계별 데이터 확인

```bash
# LAM 단계 결과 확인
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db -c "
SELECT 
    aj.id,
    aj.job_name,
    aj.status,
    pi.stage,
    pi.processing_status,
    pi.lam_results IS NOT NULL as lam_완료,
    pi.created_at
FROM analysis_analysisjob aj
JOIN analysis_processedimage pi ON aj.id = pi.job_id
WHERE aj.job_name = 'Pipeline Test Job'
ORDER BY pi.created_at DESC;
"

# TSPM 단계 결과 확인
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db -c "
SELECT 
    pi.id,
    pi.stage,
    pi.ocr_text IS NOT NULL as ocr_완료,
    pi.ai_description IS NOT NULL as ai_설명_완료,
    LENGTH(pi.ocr_text) as ocr_텍스트_길이,
    LENGTH(pi.ai_description) as ai_설명_길이,
    pi.updated_at
FROM analysis_processedimage pi
JOIN analysis_analysisjob aj ON pi.job_id = aj.id
WHERE aj.job_name = 'Pipeline Test Job'
ORDER BY pi.updated_at DESC;
"

# CIM 단계 결과 확인
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db -c "
SELECT 
    ar.id,
    ar.confidence_score,
    ar.total_detected_elements,
    ar.text_content IS NOT NULL as 텍스트_결과,
    ar.braille_content IS NOT NULL as 점자_결과,
    ar.pdf_path IS NOT NULL as pdf_결과,
    ar.created_at
FROM analysis_analysisresult ar
JOIN analysis_analysisjob aj ON ar.job_id = aj.id
WHERE aj.job_name = 'Pipeline Test Job'
ORDER BY ar.created_at DESC;
"
```

### 3. 상세 결과 내용 확인

```bash
# 최신 분석 결과의 상세 내용 확인
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db -c "
SELECT 
    aj.job_name,
    ar.confidence_score,
    ar.total_detected_elements,
    LEFT(ar.text_content, 200) as 텍스트_미리보기,
    LEFT(ar.braille_content, 100) as 점자_미리보기,
    ar.processing_time_seconds as 처리_시간_초
FROM analysis_analysisresult ar
JOIN analysis_analysisjob aj ON ar.job_id = aj.id
ORDER BY ar.created_at DESC
LIMIT 1;
"
```

### 4. 파이프라인 성능 분석

```bash
# 각 단계별 처리 시간 분석
docker compose -f docker-compose.dev.yml exec web python -c "
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from apps.analysis.models import AnalysisJob, ProcessedImage, AnalysisResult
from django.db.models import Avg, Count, Sum

print('📊 파이프라인 성능 분석 리포트')
print('=' * 50)

# 전체 통계
total_jobs = AnalysisJob.objects.count()
total_images = ProcessedImage.objects.count()
total_results = AnalysisResult.objects.count()

print(f'전체 분석 작업: {total_jobs}개')
print(f'처리된 이미지: {total_images}개')
print(f'완료된 결과: {total_results}개')
print()

# 최근 작업 상태
recent_job = AnalysisJob.objects.order_by('-created_at').first()
if recent_job:
    print(f'최근 작업: {recent_job.job_name}')
    print(f'상태: {recent_job.status}')
    print(f'진행률: {recent_job.progress}%')
    print()

# 단계별 성공률
stages = ['lam', 'tspm', 'cim']
for stage in stages:
    total = ProcessedImage.objects.filter(stage=stage).count()
    completed = ProcessedImage.objects.filter(
        stage=stage, 
        processing_status='completed'
    ).count()
    
    if total > 0:
        success_rate = (completed / total) * 100
        print(f'{stage.upper()} 단계: {completed}/{total} ({success_rate:.1f}% 성공)')

print()

# 평균 처리 시간
avg_time = AnalysisResult.objects.aggregate(
    avg_time=Avg('processing_time_seconds')
)['avg_time']

if avg_time:
    print(f'평균 처리 시간: {avg_time:.1f}초')

# 평균 탐지 요소 수
avg_elements = AnalysisResult.objects.aggregate(
    avg_elements=Avg('total_detected_elements')
)['avg_elements']

if avg_elements:
    print(f'평균 탐지 요소: {avg_elements:.1f}개')

# 평균 신뢰도 점수
avg_confidence = AnalysisResult.objects.aggregate(
    avg_confidence=Avg('confidence_score')
)['avg_confidence']

if avg_confidence:
    print(f'평균 신뢰도: {avg_confidence:.3f}')

print('\\n✅ 데이터베이스 저장 확인 완료!')
"
```

---

## 🔧 트러블슈팅

### 일반적인 문제들

#### 1. Docker 빌드 실패
```bash
# 문제: 패키지 설치 오류
# 해결: Docker 캐시 초기화 후 재빌드
docker compose -f docker-compose.dev.yml down -v
docker system prune -f
docker compose -f docker-compose.dev.yml up --build -d
```

#### 2. 데이터베이스 연결 오류
```bash
# 문제: "could not connect to server"
# 해결: 데이터베이스 서비스 상태 확인
docker compose -f docker-compose.dev.yml logs db
docker compose -f docker-compose.dev.yml restart db

# 포트 충돌 확인
netstat -tulpn | grep :5432
```

#### 3. Celery 워커 오류
```bash
# 문제: Celery 작업이 실행되지 않음
# 해결: Redis 연결 및 Celery 상태 확인
docker compose -f docker-compose.dev.yml logs redis
docker compose -f docker-compose.dev.yml logs celery-worker
docker compose -f docker-compose.dev.yml restart celery-worker
```

#### 4. 메모리 부족 오류
```bash
# 문제: "Out of memory" 오류
# 해결: 환경 변수 조정
# .env.docker 파일에서:
SMARTEYE_BATCH_SIZE=1
SMARTEYE_MEMORY_LIMIT_MB=512
SMARTEYE_MAX_WORKERS=1

# 재시작
docker compose -f docker-compose.dev.yml restart web celery-worker
```

#### 5. AI 모델 다운로드 실패
```bash
# 문제: YOLO 모델 다운로드 오류
# 해결: 수동 모델 다운로드
docker compose -f docker-compose.dev.yml exec web python -c "
from ultralytics import YOLO
model = YOLO('yolo11n.pt')
print('✅ YOLO 모델 다운로드 완료')
"
```

### 고급 디버깅

#### 1. 상세 로그 활성화
```bash
# .env.docker에서 디버그 모드 활성화
DEBUG=True
SMARTEYE_DEBUG_MODE=True
SMARTEYE_SAVE_DEBUG_IMAGES=True

# 로그 레벨 변경
DJANGO_LOG_LEVEL=DEBUG
CELERY_LOG_LEVEL=DEBUG
```

#### 2. 개별 서비스 테스트
```bash
# LAM 서비스 개별 테스트
docker compose -f docker-compose.dev.yml exec web python -c "
from core.lam.service import LAMService
service = LAMService()
print('✅ LAM 서비스 초기화 성공')
"

# TSPM 서비스 개별 테스트
docker compose -f docker-compose.dev.yml exec web python -c "
from core.tspm.service import TSPMService
service = TSPMService()
print('✅ TSPM 서비스 초기화 성공')
"

# CIM 서비스 개별 테스트
docker compose -f docker-compose.dev.yml exec web python -c "
from core.cim.service import CIMService
service = CIMService()
print('✅ CIM 서비스 초기화 성공')
"
```

#### 3. 성능 모니터링
```bash
# 실시간 리소스 사용량 모니터링
docker stats

# 특정 컨테이너 메모리 사용량
docker stats smarteye-web smarteye-celery --no-stream

# 디스크 사용량 확인
docker compose -f docker-compose.dev.yml exec web df -h
```

---

## ✅ 체크리스트

개발 환경 설정이 완료되었는지 확인하세요:

### 기본 환경
- [ ] Git, Docker, Docker Compose 설치 완료
- [ ] 소스 코드 클론 완료
- [ ] `.env.docker` 파일 설정 완료
- [ ] 디렉토리 권한 설정 완료

### Docker 환경
- [ ] 모든 서비스가 `healthy` 상태
- [ ] 웹 서버 접속 가능 (http://localhost:8000)
- [ ] API 문서 접속 가능 (http://localhost:8000/api/docs/)
- [ ] Flower 모니터링 접속 가능 (http://localhost:5555)

### 데이터베이스
- [ ] 데이터베이스 연결 테스트 통과
- [ ] 모든 마이그레이션 적용 완료
- [ ] 테이블 생성 확인
- [ ] API 헬스체크 통과

### 파이프라인
- [ ] 테스트 사용자 생성 완료
- [ ] JWT 토큰 발급 성공
- [ ] 파일 업로드 및 분석 시작 성공
- [ ] LAM → TSPM → CIM 파이프라인 완료
- [ ] 모든 단계의 결과가 데이터베이스에 저장 확인

---

## 📞 추가 지원

문제가 해결되지 않는 경우:

1. **GitHub Issues**: [프로젝트 이슈 페이지](https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1/issues)
2. **로그 수집**: 오류 발생 시 관련 로그를 수집하여 이슈에 첨부
3. **환경 정보**: OS, Docker 버전, 시스템 사양 정보 제공

---

**🎉 축하합니다! SmartEye Backend 개발 환경 설정이 완료되었습니다.**

*이제 AI 기반 문서 분석 기능을 개발하고 테스트할 수 있습니다.*
