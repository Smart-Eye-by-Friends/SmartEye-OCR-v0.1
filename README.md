# SmartEye v0.4 - 학습지 분석 시스템

SmartEye v0.4는 AI 기반 학습지 이미지 분석을 위한 마이크로서비스 플랫폼입니다. DocLayout-YOLO 모델을 활용한 레이아웃 분석과 Tesseract OCR을 통한 텍스트 추출, OpenAI Vision API를 통한 AI 설명 생성을 제공합니다.

## 🏗️ 시스템 아키텍처

### 마이크로서비스 구성
- **Java Spring Boot 백엔드** (Port 8080) - 메인 API 서버
- **Python LAM 서비스** (Port 8001) - AI/ML 레이아웃 분석 
- **React 프론트엔드** (Port 3000/80) - 웹 사용자 인터페이스
- **PostgreSQL 데이터베이스** (Port 5433) - 데이터 저장소
- **Nginx 프록시** - 리버스 프록시 및 정적 파일 서빙

### 주요 기능
- **33개 레이아웃 요소** 검출 (제목, 문제, 선택지, 답안 등)
- **21개 텍스트 블록** OCR 처리 (한국어/영어 지원)
- **AI 기반 설명** 생성 (OpenAI Vision API)
- **PDF 멀티페이지** 분석 지원
- **실시간 진행 상황** 추적
- **익명 분석** 지원

## 🛠️ 주요 기술 스택

### 백엔드 (Java/Spring Boot)
- **Java 21**, **Spring Boot 3.5.5**
- **Spring Data JPA**, **Spring WebFlux**, **Resilience4j**
- **Apache PDFBox**, **Tess4J**, **Micrometer Prometheus**

### AI/ML 서비스 (Python/FastAPI)  
- **Python 3.9+**, **FastAPI**, **PyTorch 2.0.1**
- **DocLayout-YOLO**, **HuggingFace Transformers**
- **OpenCV**, **PIL**, **Prometheus Client**

### 데이터베이스 및 인프라
- **PostgreSQL 15**, **Docker Compose**
- **Nginx**, **Prometheus + Grafana 모니터링**

## 🚀 빠른 시작

### 1. 환경 설정
```bash
cd /home/jongyoung3/SmartEye_v0.4

# 개발 환경으로 설정
./scripts/setup-env.sh development

# 프로덕션 환경 (API 키 필요)
export OPENAI_API_KEY="your-api-key"
./scripts/setup-env.sh production
```

### 2. 시스템 시작
```bash
# 전체 시스템 시작 (빌드 + 실행)
./manage.sh start

# 상태 확인
./manage.sh status

# 로그 확인
./manage.sh logs
```

### 3. 웹 접속
- **프론트엔드**: http://localhost:3000
- **API 문서**: http://localhost:8080/swagger-ui/index.html
- **백엔드 헬스체크**: http://localhost:8080/api/health

## 📊 모니터링 및 관리

### 모니터링 시작
```bash
# Prometheus + Grafana 모니터링 시작
./scripts/start-monitoring.sh
```

### 모니터링 대시보드
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/smarteye2024)
- **cAdvisor**: http://localhost:8080

### 관리 명령어
```bash
./manage.sh help          # 사용 가능한 명령어 보기
./manage.sh start          # 전체 시스템 시작
./manage.sh stop           # 전체 시스템 중지
./manage.sh restart        # 전체 시스템 재시작
./manage.sh status         # 서비스 상태 확인
./manage.sh logs           # 전체 로그 보기
./manage.sh logs backend   # 특정 서비스 로그
./manage.sh cleanup        # 임시 파일 정리
```

## 🔧 주요 API 엔드포인트

### 문서 분석
```bash
# 이미지 분석
POST /api/document/analyze
Content-Type: multipart/form-data
Body: image=파일, modelChoice=SmartEyeSsen

# PDF 분석  
POST /api/document/analyze-pdf
Content-Type: multipart/form-data
Body: file=PDF파일, modelChoice=SmartEyeSsen

# 분석 결과 조회
GET /api/analysis/job/{jobId}
```

### 모니터링
```bash
# 헬스체크
GET /api/health

# Prometheus 메트릭  
GET /actuator/prometheus

# LAM 서비스 메트릭
GET http://localhost:8001/metrics
```

## 🛡️ 보안 기능

### 구현된 보안 강화
- **non-root 컨테이너** 실행 (모든 서비스)
- **환경변수 기반** API 키 관리
- **CORS 정책** 강화 (구체적 도메인만 허용)
- **환경별 설정** 분리 (개발/프로덕션)

### 환경변수 보안
```bash
# API 키 보안 검증
./scripts/setup-env.sh check

# 프로덕션 배포 시 필수 환경변수
export OPENAI_API_KEY="your-actual-key"
export POSTGRES_PASSWORD="secure-password"
```

## 📁 프로젝트 구조

```
SmartEye_v0.4/
├── Backend/
│   ├── smarteye-backend/          # Java Spring Boot 백엔드
│   ├── smarteye-lam-service/      # Python LAM AI 서비스
│   └── docker-compose.yml         # 메인 Docker Compose
├── frontend/                      # React 프론트엔드
├── monitoring/                    # Prometheus + Grafana 설정
├── scripts/                       # 관리 스크립트
│   ├── setup-env.sh              # 환경 설정 스크립트
│   └── start-monitoring.sh       # 모니터링 시작 스크립트
├── .env.development              # 개발 환경 설정
├── .env.production               # 프로덕션 환경 설정
├── .env.example                  # 환경설정 예시
└── manage.sh                     # 통합 관리 스크립트
```

## 🧪 개발 및 테스트

### API 테스트
```bash
# curl을 이용한 간단한 분석 테스트
curl -X POST \
  http://localhost:8080/api/document/analyze \
  -H 'Content-Type: multipart/form-data' \
  -F 'image=@test.jpg' \
  -F 'modelChoice=SmartEyeSsen'
```

### 데이터베이스 접속
```bash
# PostgreSQL 직접 접속
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db

# 분석 작업 조회
SELECT * FROM analysis_jobs ORDER BY created_at DESC LIMIT 10;
```

## 📋 개발 컨벤션

- **백엔드**: `Backend/smarteye-backend/` - 표준 Spring Boot 구조
- **LAM 서비스**: `Backend/smarteye-lam-service/` - FastAPI 구조  
- **프론트엔드**: `frontend/` - React + Material UI
- **API 문서**: Swagger UI 자동 생성
- **환경 관리**: 스크립트 기반 자동화

## 🔍 문제 해결

### 일반적인 문제
```bash
# 포트 충돌 확인
sudo lsof -i :8080

# Docker 정리 후 재시작
docker system prune -f
./manage.sh restart

# 메모리 사용량 확인
docker stats
```

### 로그 분석
```bash
# 실시간 로그 확인
docker logs -f smarteye-backend
docker logs -f smarteye-lam-service

# 특정 기간 로그
./manage.sh logs backend | tail -100
```

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)  
5. Open a Pull Request

## 📞 지원

문제가 발생하거나 질문이 있으시면 이슈를 등록해 주세요.

---

**SmartEye v0.4** - AI 기반 학습지 분석의 새로운 표준 🚀