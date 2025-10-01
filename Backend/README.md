# SmartEye OCR - Spring Boot Backend

이 폴더는 Java Spring Boot 기반 백엔드 개발을 위한 공간입니다.

## 🎯 개발 목표

기존 Python FastAPI 백엔드를 Java Spring Boot로 완전 포팅하여 팀 협업을 위한 통합된 백엔드 시스템을 구축합니다.

## 📁 예상 프로젝트 구조

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/smarteye/ocr/
│   │   │       ├── SmartEyeOcrApplication.java
│   │   │       ├── controller/
│   │   │       │   ├── AnalysisController.java
│   │   │       │   └── HealthController.java
│   │   │       ├── service/
│   │   │       │   ├── OCRService.java
│   │   │       │   ├── LayoutAnalysisService.java
│   │   │       │   └── StructuredAnalysisService.java
│   │   │       ├── dto/
│   │   │       │   ├── AnalysisRequest.java
│   │   │       │   ├── AnalysisResponse.java
│   │   │       │   └── StructuredResult.java
│   │   │       └── config/
│   │   │           ├── CorsConfig.java
│   │   │           └── FileUploadConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/
│           └── com/smarteye/ocr/
├── pom.xml (또는 build.gradle)
└── README.md
```

## 🔌 필수 API 엔드포인트

### 1. 기본 분석 API
```
POST /api/analyze
Content-Type: multipart/form-data

Request:
- image: File (JPG, PNG, GIF)
- modelChoice: String (SmartEyeSsen, DocStructBench, etc.)
- apiKey: String (optional, OpenAI API key)

Response:
{
  "success": boolean,
  "layout_image_url": String,
  "json_url": String,
  "stats": Object,
  "ocr_results": Array,
  "ai_results": Array,
  "formatted_text": String
}
```

### 2. 구조화된 분석 API
```
POST /api/analyze-structured
Content-Type: multipart/form-data

Request: (동일)

Response:
{
  "success": boolean,
  "layout_image_url": String,
  "json_url": String,
  "stats": Object,
  "ocr_results": Array,
  "ai_results": Array,
  "formatted_text": String,
  "structured_result": Object  // 추가
}
```

### 3. 워드 문서 저장 API
```
POST /api/save-as-word
Content-Type: application/json

Request:
{
  "content": String,
  "filename": String
}

Response:
{
  "success": boolean,
  "download_url": String
}
```

### 4. 헬스 체크 API
```
GET /api/health

Response:
{
  "status": "UP",
  "timestamp": "2024-09-04T15:30:00Z"
}
```

## 🛠️ 필수 의존성

### Maven (pom.xml)
```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- File Upload -->
    <dependency>
        <groupId>commons-fileupload</groupId>
        <artifactId>commons-fileupload</artifactId>
    </dependency>
    
    <!-- Apache POI (for Word documents) -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## ⚙️ 환경 설정

### application.yml
```yaml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  
  web:
    cors:
      allowed-origins: 
        - http://localhost:3000  # React frontend
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
      allowed-headers: "*"
      allow-credentials: true

logging:
  level:
    com.smarteye.ocr: DEBUG
    org.springframework.web: DEBUG
```

## 🔄 기존 Python 코드 포팅 가이드

### 1. OCR 및 레이아웃 분석
- `layout_analyzer_enhanced.py` → `LayoutAnalysisService.java`
- `structured_json_generator.py` → `StructuredAnalysisService.java`

### 2. API 엔드포인트
- `api_server.py`의 FastAPI 라우트 → Spring Boot Controller

### 3. 파일 처리
- Python의 파일 처리 로직 → Spring Boot의 MultipartFile 처리

## 🧪 개발 및 테스트

### 개발 서버 실행
```bash
./mvnw spring-boot:run
```

### 테스트 실행
```bash
./mvnw test
```

### 프로덕션 빌드
```bash
./mvnw clean package
```

## 🔗 프론트엔드 연동

React 프론트엔드는 `http://localhost:3000`에서 실행되며, 백엔드 API를 `http://localhost:8080`으로 호출합니다.

CORS 설정이 이미 적용되어 있어 별도 설정 없이 연동 가능합니다.

## 📝 개발 시작하기

1. Spring Initializr에서 프로젝트 생성
2. 위의 의존성들 추가
3. `legacy/` 폴더의 Python 코드 참고하여 Java로 포팅
4. React 프론트엔드와 연동 테스트

---

**개발자**: 백엔드 팀원
**프론트엔드 연동**: React (frontend/ 폴더)
**참고 구현체**: legacy/ 폴더의 Python 코드
