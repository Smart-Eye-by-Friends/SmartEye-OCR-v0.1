# 🚀 SmartEye v0.4 - Java/Spring Backend

Python FastAPI 백엔드를 Java/Spring Boot로 완전 변환한 SmartEye OCR 분석 시스템입니다.

## 📋 프로젝트 개요

SmartEye는 시각 장애 아동을 위한 AI 기반 학습지 분석 및 텍스트 변환 시스템입니다. 이 버전은 기존 Python FastAPI 백엔드를 Java/Spring Boot + 마이크로서비스 아키텍처로 완전히 재구현한 버전입니다.

**🎉 배포 상태**: ✅ **완전 구동 중** - 모든 서비스 정상 작동  
**📅 테스트 완료**: 2025-09-01 - 전체 API 워크플로우 검증 완료

### 🎯 주요 기능

- **문서 레이아웃 분석**: DocLayout-YOLO 모델을 이용한 레이아웃 구조 분석 (33개 요소 검출)
- **OCR 텍스트 추출**: Tesseract를 이용한 한국어+영어 텍스트 추출 (21개 텍스트 블록)
- **AI 설명 생성**: OpenAI Vision API를 통한 그림/표 자동 설명
- **결과 시각화**: 레이아웃 바운딩 박스가 표시된 분석 결과 이미지
- **데이터베이스 연동**: PostgreSQL 기반 분석 작업 및 결과 저장
- **익명 분석 지원**: 사용자 등록 없이 바로 문서 분석 가능
- **실시간 상태 추적**: 분석 작업 ID 기반 진행 상태 모니터링

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────┐
│                Frontend                     │
├─────────────────────────────────────────────┤
│           Java Spring Boot Backend          │
│  ┌─────────────┐  ┌─────────────────────┐   │
│  │     API     │  │      Services       │   │
│  │ Controllers │◄─┤  OCR / File / PDF   │   │
│  └─────────────┘  └─────────────────────┘   │
├─────────────────────────────────────────────┤
│              Microservices                  │
│  ┌─────────────┐  ┌─────────────────────┐   │
│  │ LAM Service │  │    OpenAI Vision    │   │
│  │ (Python)    │  │        API          │   │
│  └─────────────┘  └─────────────────────┘   │
├─────────────────────────────────────────────┤
│             Infrastructure                  │
│  ┌─────────────┐  ┌─────────────────────┐   │
│  │ PostgreSQL  │  │      Docker         │   │
│  │  Database   │  │    Containers       │   │
│  └─────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────┘
```

## 🛠️ 기술 스택

### Backend (Java/Spring Boot)
- **Framework**: Spring Boot 3.5.5
- **Language**: Java 21
- **Database**: PostgreSQL 15+
- **Build Tool**: Gradle 8.x
- **Dependencies**:
  - Spring Data JPA
  - Spring WebFlux (비동기 처리)
  - Apache PDFBox 3.0 (PDF 처리)
  - Tess4J (OCR)
  - Apache POI (Word 문서 생성)
  - Resilience4j (Circuit Breaker)

### Microservice (LAM)
- **Framework**: Python FastAPI
- **AI Model**: DocLayout-YOLO
- **Dependencies**: PyTorch, Transformers, OpenCV

### Infrastructure
- **Database**: PostgreSQL 15
- **Containerization**: Docker + Docker Compose
- **Monitoring**: Spring Boot Actuator + Prometheus

## 🚀 빠른 시작

### 전체 시스템 실행 (Docker Compose)

```bash
# 1. 프로젝트 디렉토리로 이동
cd /home/jongyoung3/SmartEye_v0.4

# 2. 서비스 시작 (자동화 스크립트)
./start_services.sh

# 3. 서비스 상태 확인
docker ps

# 4. API 테스트
curl -X POST \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  http://localhost:8080/api/document/analyze
```

### 개별 서비스 상태 확인
```bash
# Backend 로그 확인
docker-compose logs smarteye-backend

# LAM Service 로그 확인  
docker-compose logs smarteye-lam-service

# Database 연결 확인
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db
```

## 📡 API 엔드포인트

### ✅ 검증 완료 - 메인 분석 API
```bash
# 단일 이미지 분석 (✅ 정상 작동 확인)
POST /api/document/analyze
Content-Type: multipart/form-data

# 필수 파라미터:
- image: 분석할 이미지 파일 (JPG, PNG, PDF 지원)
- modelChoice: 사용할 모델 선택 (SmartEyeSsen, Tesseract, OpenAI)

# 테스트 예시:
curl -X POST \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  http://localhost:8080/api/document/analyze
```

### 응답 형식
```json
{
  "success": true,
  "layoutImageUrl": "/static/layout_viz_1756723030.png",
  "jsonUrl": "/static/analysis_result_20250901_103711.json",
  "stats": {
    "totalLayoutElements": 33,
    "ocrTextBlocks": 21,
    "aiDescriptions": 0,
    "classCounts": {
      "unit": 2, "figure": 5, "plain_text": 13,
      "parenthesis_blank": 3, "page": 2, "title": 1, "question_number": 7
    }
  },
  "jobId": "d588945a-459d-42e6-84c7-9b635cf2b8c7",
  "timestamp": 1756723030,
  "message": "분석이 성공적으로 완료되었습니다."
}
```

### 🔧 지원 모델
- **SmartEyeSsen**: DocLayout-YOLO 기반 레이아웃 분석 (기본값)
- **Tesseract**: OCR 전용 텍스트 추출
- **OpenAI**: GPT-4 Turbo 기반 AI 분석

## 🔧 환경 설정

### 환경 변수
```bash
DB_USERNAME=smarteye
DB_PASSWORD=smarteye_password
LAM_SERVICE_URL=http://localhost:8001
UPLOAD_DIR=./uploads
STATIC_DIR=./static
```

## 📊 서비스 접속 정보

**✅ 모든 서비스 정상 구동 중**

- **Java Backend API**: http://localhost:8080 (✅ 정상)
- **LAM Service API**: http://localhost:8001 (✅ 정상)
- **PostgreSQL**: localhost:5433 (외부), 5432 (내부) (✅ 정상)
- **Nginx Proxy**: http://localhost:80 (✅ 정상)

### 🔍 분석 결과 확인
- **레이아웃 시각화**: http://localhost:8080/static/layout_viz_[timestamp].png
- **JSON 결과**: http://localhost:8080/static/analysis_result_[timestamp].json

### 🏥 헬스체크
```bash
# Backend 상태 확인
curl http://localhost:8080/actuator/health

# LAM Service 상태 확인
curl http://localhost:8001/health
```

---

**SmartEye v0.4** - 시각 장애 아동을 위한 AI 기반 학습 도구 🎓✨

**마지막 업데이트**: 2025-09-01  
**상태**: 🟢 완전 운영 중