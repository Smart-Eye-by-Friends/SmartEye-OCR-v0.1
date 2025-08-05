# SmartEye Backend 프로젝트 문서

## 📋 프로젝트 개요

**SmartEye Backend**는 AI/ML 기술을 활용한 문서 분석 및 접근성 변환 서비스입니다. 시각 장애인과 학습자를 위해 문서를 자동으로 분석하고 접근 가능한 형태로 변환하는 Django 기반 백엔드 시스템입니다.

### 🎯 주요 목표
- 시각 장애인을 위한 문서 접근성 향상
- 교육 콘텐츠의 디지털화 및 자동화
- AI 기반 문서 구조 분석 및 텍스트 추출
- 다양한 형식의 출력 제공 (텍스트, 점자, 음성용 등)

## 🏗️ 시스템 아키텍처

### 3단계 처리 파이프라인

```
📄 문서 입력 → 🔍 LAM → 📝 TSPM → 🔗 CIM → 📤 결과 출력
              (레이아웃)  (텍스트)   (통합)
```

#### 1. LAM (Layout Analysis Module) - 레이아웃 분석
- **기술**: DocLayout-YOLO 모델
- **기능**: 문서 요소 감지 및 분류 (텍스트, 이미지, 표, 제목 등)
- **출력**: 바운딩 박스, 신뢰도, 요소 분류 정보

#### 2. TSPM (Text & Scene Processing Module) - 텍스트 및 장면 처리
- **OCR**: Tesseract 엔진으로 한국어/영어 텍스트 추출
- **이미지 설명**: OpenAI GPT-4 Vision API로 이미지 설명 생성
- **출력**: 구조화된 텍스트 및 이미지 설명

#### 3. CIM (Content Integration Module) - 콘텐츠 통합
- **기능**: 모든 결과 통합 및 구조화
- **처리**: 읽기 순서 최적화, 접근성 형식 변환
- **출력**: JSON, XML, 텍스트, 점자, PDF 보고서

## 📁 프로젝트 구조

```
smarteye_backend/
├── 🚀 smarteye/                    # Django 프로젝트 루트
│   ├── settings/                   # 환경별 설정 파일
│   │   ├── base.py                # 공통 설정
│   │   ├── development.py         # 개발 환경
│   │   └── production.py          # 운영 환경
│   ├── urls.py                    # 메인 URL 라우팅
│   ├── celery.py                  # 비동기 작업 설정
│   └── asgi.py                    # WebSocket 설정
├── 📱 apps/                        # Django 앱들
│   ├── users/                     # 사용자 관리
│   ├── analysis/                  # 분석 작업 관리
│   ├── files/                     # 파일 업로드/관리
│   └── api/                       # API 통합
├── 🧠 core/                        # 핵심 AI 서비스 모듈
│   ├── lam/                       # Layout Analysis Module
│   ├── tspm/                      # Text & Scene Processing Module
│   └── cim/                       # Content Integration Module
├── 📂 media/                       # 업로드된 파일
├── 📊 static/                      # 정적 파일
├── 📋 logs/                        # 로그 파일
├── 🐳 docker-compose.yml           # Docker 컴포즈 설정
└── 📄 requirements.txt             # 패키지 의존성
```

## 🗄️ 데이터베이스 모델

### 핵심 모델 관계도

```
👤 User (사용자)
├── 📋 AnalysisJob (분석 작업)
│   ├── 🖼️ ProcessedImage (처리된 이미지)
│   │   └── 🔍 LAMLayoutDetection (레이아웃 감지)
│   │       ├── 📝 TSPMOCRResult (OCR 결과)
│   │       └── 🖼️ TSPMImageDescription (이미지 설명)
│   ├── 📊 AnalysisResult (통합 결과)
│   ├── 🔗 CIMIntegratedResult (CIM 최종 결과)
│   └── 📖 DocumentStructure (문서 구조)
├── 📁 SourceFile (원본 파일)
├── 🔐 UserSession (사용자 세션)
└── ⚙️ UserPreference (사용자 설정)
```

### 주요 모델 설명

- **AnalysisJob**: 분석 작업의 메타데이터, 상태, 진행률 관리
- **ProcessedImage**: 페이지별 이미지 처리 정보 및 상태
- **LAMLayoutDetection**: YOLO 모델의 객체 감지 결과 (바운딩 박스, 클래스, 신뢰도)
- **TSPMOCRResult**: Tesseract OCR 텍스트 추출 결과
- **TSPMImageDescription**: OpenAI API를 통한 이미지 설명
- **CIMIntegratedResult**: 모든 단계의 결과를 통합한 최종 출력

## 🌐 API 엔드포인트

### 인증 관련 (`/api/v1/auth/`)
```http
POST /api/v1/auth/users/               # 회원가입
POST /api/v1/auth/jwt/create/          # JWT 토큰 생성
POST /api/v1/auth/jwt/refresh/         # 토큰 갱신
```

### 분석 관련 (`/api/v1/analysis/`)
```http
POST /api/v1/analysis/jobs/upload_and_analyze/  # 파일 업로드 및 완전 분석
GET  /api/v1/analysis/jobs/                     # 분석 작업 목록
GET  /api/v1/analysis/jobs/{id}/                # 특정 작업 상세 조회
POST /api/v1/analysis/jobs/{id}/start_analysis/ # 개별 분석 시작
GET  /api/v1/analysis/jobs/{id}/results/        # 분석 결과 조회
```

### 파일 관리 (`/api/v1/files/`)
```http
POST /api/v1/files/upload/             # 파일 업로드
GET  /api/v1/files/                    # 업로드된 파일 목록
GET  /api/v1/files/{id}/               # 특정 파일 정보
```

### 사용자 관리 (`/api/v1/users/`)
```http
GET  /api/v1/users/profile/            # 사용자 프로필
GET  /api/v1/users/quota/              # API 할당량 조회
PUT  /api/v1/users/preferences/        # 사용자 설정 변경
```

## 🔧 기술 스택

### 백엔드 프레임워크
- **Django 5.2.4**: 웹 프레임워크
- **Django REST Framework**: RESTful API
- **Djoser**: JWT 기반 인증 시스템
- **Channels**: WebSocket 실시간 통신

### AI/ML 라이브러리
- **PyTorch 2.2.0**: 딥러닝 프레임워크
- **Ultralytics YOLO**: 객체 감지 모델
- **OpenCV**: 이미지 처리
- **Pytesseract**: OCR 엔진
- **OpenAI API**: 이미지 설명 생성

### 데이터베이스 및 캐시
- **PostgreSQL**: 메인 데이터베이스
- **Redis**: 캐시 및 작업 큐 브로커

### 비동기 처리
- **Celery**: 백그라운드 작업 처리
- **Celery Beat**: 주기적 작업 스케줄링

### 기타 라이브러리
- **ReportLab**: PDF 보고서 생성
- **Pandas**: 데이터 분석
- **Matplotlib**: 시각화

## ⚙️ 설치 및 설정

### 1. 환경 준비
```bash
# 저장소 클론
git clone <repository-url>
cd smarteye_backend

# 가상환경 생성 및 활성화
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# 의존성 설치
pip install -r requirements.txt
```

### 2. 환경 변수 설정
```bash
# .env 파일 생성
cp .env.example .env

# .env 파일 편집 (필수 설정)
DATABASE_NAME=smarteye_dev
DATABASE_USER=smarteye_user
DATABASE_PASSWORD=password
OPENAI_API_KEY=your_openai_api_key_here
SECRET_KEY=your_secret_key_here
```

### 3. 데이터베이스 설정
```bash
# 마이그레이션 생성 및 적용
python manage.py makemigrations
python manage.py migrate

# 관리자 계정 생성
python manage.py createsuperuser
```

### 4. 서비스 실행

#### 개발 환경
```bash
# Django 개발 서버
python manage.py runserver

# Celery 워커 (별도 터미널)
celery -A smarteye worker --loglevel=info

# Celery Beat (별도 터미널)
celery -A smarteye beat --loglevel=info
```

#### Docker 환경
```bash
# 모든 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 중지
docker-compose down
```

## 🔄 워크플로우

### 완전 분석 프로세스

1. **📤 파일 업로드**
   ```
   사용자 → Django API → SourceFile 저장 → ProcessedImage 생성
   ```

2. **🔍 LAM 단계 (레이아웃 분석)**
   ```
   이미지 → YOLO 모델 → 객체 감지 → LAMLayoutDetection 저장
   ```

3. **📝 TSPM 단계 (텍스트 & 장면 처리)**
   ```
   감지된 영역별 처리:
   ├── 텍스트 영역 → Tesseract OCR → TSPMOCRResult
   └── 이미지 영역 → OpenAI API → TSPMImageDescription
   ```

4. **🔗 CIM 단계 (콘텐츠 통합)**
   ```
   모든 결과 → 구조화 → 접근성 변환 → CIMIntegratedResult
   ```

5. **📊 결과 제공**
   ```
   통합 결과 → 다양한 형식 → 사용자에게 전달
   ├── JSON (구조화된 데이터)
   ├── XML (표준 형식)
   ├── TXT (일반 텍스트)
   ├── 점자 (시각 장애인용)
   └── PDF (보고서)
   ```

### 실시간 상태 업데이트
- **WebSocket 연결**을 통해 실시간 진행률 전송
- **Celery 작업**의 각 단계별 상태 업데이트
- **에러 발생 시** 즉시 알림 및 재시도 메커니즘

## 📊 모니터링 및 로깅

### 로그 시스템
```python
# 로그 레벨 및 출력
├── DEBUG: 개발용 상세 정보
├── INFO: 일반적인 작업 진행 상황
├── WARNING: 주의가 필요한 상황
└── ERROR: 오류 발생 시

# 로그 저장 위치
├── logs/django.log        # Django 애플리케이션 로그
├── logs/celery.log        # Celery 작업 로그
└── Console Output         # 개발 환경 콘솔 출력
```

### 성능 메트릭
- **메모리 사용량**: 적응적 배치 크기 조정
- **처리 시간**: 각 단계별 소요 시간 측정
- **API 호출량**: OpenAI API 사용량 추적
- **에러율**: 각 모듈별 실패율 모니터링

## 🔐 보안 및 권한

### 인증 시스템
- **JWT 토큰**: 액세스 토큰 (60분) + 리프레시 토큰 (7일)
- **사용자 등급**: 무료/기본/프리미엄 구독 모델
- **API 할당량**: 사용자별 월간 API 호출 제한

### 보안 설정
```python
# CORS 설정
CORS_ALLOWED_ORIGINS = ['http://localhost:3000']  # 프론트엔드

# 파일 업로드 제한
MAX_UPLOAD_SIZE = 100MB
SUPPORTED_FORMATS = ['.jpg', '.jpeg', '.png', '.pdf']

# 데이터베이스 보안
DATABASE_SSL = True  # 운영 환경
```

## 📈 확장성 및 성능

### 확장 가능한 아키텍처
- **모듈화된 설계**: LAM, TSPM, CIM 독립적 운영 가능
- **비동기 처리**: Celery를 통한 백그라운드 작업
- **수평 확장**: 워커 노드 추가로 처리량 증대
- **캐시 최적화**: Redis를 통한 성능 향상

### 성능 최적화
```python
# 메모리 관리
BATCH_SIZE = 2                    # 배치 처리 크기
MEMORY_LIMIT_MB = 500            # 메모리 사용 제한
ADAPTIVE_BATCH_SIZING = True     # 동적 배치 크기 조정

# 데이터베이스 최적화
DATABASE_CONNECTION_POOLING = True
INDEX_OPTIMIZATION = True        # 주요 필드 인덱싱
```

## 🚀 배포 가이드

### 운영 환경 설정
```bash
# 환경 변수 설정
export DJANGO_SETTINGS_MODULE=smarteye.settings.production
export SECRET_KEY=production_secret_key
export DEBUG=False

# 정적 파일 수집
python manage.py collectstatic --noinput

# 데이터베이스 마이그레이션
python manage.py migrate

# 서비스 시작
gunicorn smarteye.wsgi:application --bind 0.0.0.0:8000
```

### Docker 운영 배포
```yaml
# docker-compose.prod.yml
version: '3.8'
services:
  web:
    build: .
    environment:
      - DEBUG=False
      - DATABASE_URL=postgresql://user:pass@db:5432/smarteye
    depends_on:
      - db
      - redis
  
  celery:
    build: .
    command: celery -A smarteye worker --loglevel=info
    
  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    ports:
      - "80:80"
```

## 🔮 향후 개발 계획

### 단기 목표 (3개월)
- [ ] 실시간 협업 기능 추가
- [ ] 모바일 앱 지원 API 확장
- [ ] 다국어 OCR 지원 확대
- [ ] 성능 최적화 및 캐싱 개선

### 중기 목표 (6개월)
- [ ] 음성 합성 기능 통합
- [ ] 수식 인식 및 변환 기능
- [ ] 클라우드 스토리지 연동
- [ ] 고급 분석 대시보드

### 장기 목표 (1년)
- [ ] 머신러닝 모델 자체 학습
- [ ] 마이크로서비스 아키텍처 전환
- [ ] 국제 표준 준수 (WCAG 2.1)
- [ ] 엔터프라이즈 기능 확장

## 🤝 기여 방법

### 개발 환경 구성
1. 저장소 포크 및 클론
2. 개발 브랜치 생성
3. 로컬 환경 설정 (위 설치 가이드 참조)
4. 변경사항 구현 및 테스트
5. Pull Request 제출

### 코딩 표준
- **Python**: PEP 8 준수
- **Django**: Django 코딩 스타일 가이드
- **API**: RESTful 설계 원칙
- **문서화**: 함수 및 클래스 Docstring 필수

### 이슈 리포팅
- 버그 리포트: 재현 단계, 예상 결과, 실제 결과
- 기능 제안: 사용 사례, 구현 방안, 우선순위
- 성능 문제: 환경 정보, 벤치마크 결과

## 📞 지원 및 연락처

### 문서 및 도움말
- **API 문서**: `http://localhost:8000/api/docs/` (Swagger UI)
- **관리자 페이지**: `http://localhost:8000/admin/`
- **프로젝트 위키**: 상세 개발 가이드 및 튜토리얼

### 기술 지원
- **GitHub Issues**: 버그 리포트 및 기능 제안
- **이메일**: 기술 문의 및 협업 제안
- **커뮤니티**: 개발자 포럼 및 Discord 채널

---

**SmartEye Backend**는 교육의 접근성을 높이고 모든 학습자가 동등한 기회를 가질 수 있도록 돕는 혁신적인 AI 솔루션입니다. 함께 더 나은 교육 환경을 만들어가실 개발자분들의 참여를 기다립니다! 🚀✨