# 🚀 SmartEye v0.4 - Java/Spring Backend

Python FastAPI 백엔드를 Java/Spring Boot로 완전 변환한 SmartEye OCR 분석 시스템입니다.

## 📋 프로젝트 개요

SmartEye는 시각 장애 아동을 위한 AI 기반 학습지 분석 및 텍스트 변환 시스템입니다. 이 버전은 기존 Python FastAPI 백엔드를 Java/Spring Boot + 마이크로서비스 아키텍처로 완전히 재구현한 버전입니다.

### 🎯 주요 기능

- **문서 레이아웃 분석**: DocLayout-YOLO 모델을 이용한 레이아웃 구조 분석
- **OCR 텍스트 추출**: Tesseract를 이용한 한국어+영어 텍스트 추출
- **AI 설명 생성**: OpenAI Vision API를 통한 그림/표 자동 설명
- **결과 시각화**: 레이아웃 바운딩 박스가 표시된 분석 결과 이미지
- **텍스트 포맷팅**: 구조화된 텍스트 자동 포맷팅
- **워드 문서 생성**: 분석 결과를 MS Word 문서로 자동 생성
- **PDF 처리**: 멀티페이지 PDF 문서 분석 지원

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
# 서비스 시작 (자동화 스크립트)
./start_services.sh
```

## 📡 API 엔드포인트

### 메인 분석 API
```
POST /api/analysis/analyze
- 단일 이미지 분석

POST /api/analysis/analyze-pdf  
- PDF 문서 분석
```

### 문서 처리 API
```
POST /api/document/format-text
- JSON 결과를 포맷팅된 텍스트로 변환

POST /api/document/save-as-word
- 텍스트를 워드 문서로 저장

GET /api/document/download/{filename}
- 생성된 파일 다운로드
```

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

- **Java Backend API**: http://localhost:8080
- **LAM Service API**: http://localhost:8001  
- **PostgreSQL**: localhost:5432
- **API 문서**: http://localhost:8080/swagger-ui/index.html

---

**SmartEye v0.4** - 시각 장애 아동을 위한 AI 기반 학습 도구 🎓✨