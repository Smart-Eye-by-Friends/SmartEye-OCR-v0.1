# SmartEye v0.1 - 지능형 문서 분석 시스템 (리팩토링 완료)

## 개요
SmartEye는 AI 기반 문서 레이아웃 분석과 OCR을 통합한 지능형 문서 분석 시스템입니다. Spring Boot 백엔드와 Python 마이크로서비스가 결합된 하이브리드 아키텍처로 구성되어 있습니다.

## v0.1 리팩토링 주요 개선사항
- ✅ **레거시 코드 제거**: 3개의 deprecated 컨트롤러/서비스 제거
- ✅ **통합 아키텍처**: `IntegratedAnalysisController`로 모든 분석 API 통합
- ✅ **구조 개선**: DTO 패키지 정리 및 의미있는 구조화
- ✅ **서비스 통합**: `DocumentAnalysisService`로 중앙 집중식 분석 관리
- ✅ **예외 처리 개선**: 도메인별 전용 Exception 클래스 추가
- ✅ **코드 품질**: 중복 제거, 명확한 책임 분리, 확장성 향상

## 아키텍처 구성
- **Spring Boot 백엔드**: 메인 API 서버 및 TSPM (텍스트 처리 모듈)
- **LAM 마이크로서비스**: DocLayout-YOLO 기반 레이아웃 분석 (Python/FastAPI)
- **통합 처리 파이프라인**: LAM → TSPM → CIM 순차 처리
- **중앙 집중식 관리**: `DocumentAnalysisService`를 통한 모든 분석 로직 통합

## 기술 스택

### 백엔드 (Spring Boot)
- Java 17
- Spring Boot 3.1.5
- Gradle 8.3
- **PostgreSQL** (운영환경) / H2 Database (개발환경)
- Redis (캐싱)
- Tesseract OCR
- OpenAI Vision API
- **Spring Boot Actuator** (모니터링)

### LAM 마이크로서비스
- Python 3.9+
- FastAPI
- DocLayout-YOLO
- OpenCV
- PyTorch
- Uvicorn

### DevOps
- Docker & Docker Compose
- Nginx (리버스 프록시)
- **PostgreSQL 16** (데이터베이스)
- 성능 모니터링 시스템

## 데이터베이스 설정

### PostgreSQL 설정 (운영환경)

#### 1. PostgreSQL 설치 및 초기 설정
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql postgresql-contrib

# 서비스 시작
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

#### 2. 데이터베이스 및 사용자 생성
```bash
sudo -u postgres psql

-- PostgreSQL 콘솔에서 실행
CREATE USER smarteye WITH PASSWORD 'smarteye123';
CREATE DATABASE smarteye_db;
GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;
GRANT CREATE ON SCHEMA public TO smarteye;
GRANT USAGE ON SCHEMA public TO smarteye;
GRANT ALL ON ALL TABLES IN SCHEMA public TO smarteye;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO smarteye;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO smarteye;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO smarteye;
\q
```

#### 3. 인증 설정 (pg_hba.conf)
```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf

# 다음 라인 추가/수정
local   smarteye_db     smarteye                                md5
host    smarteye_db     smarteye        127.0.0.1/32            md5

# PostgreSQL 재시작
sudo systemctl restart postgresql
```

#### 4. 연결 테스트
```bash
psql -h localhost -U smarteye -d smarteye_db
```

## 프로젝트 구조 (v0.1 리팩토링 완료)
```
SmartEye_v0.1/
├── src/main/java/com/smarteye/          # Spring Boot 애플리케이션
│   ├── controller/                       # REST API 컨트롤러
│   │   ├── IntegratedAnalysisController.java    # ✅ 통합된 메인 분석 API
│   │   ├── PerformanceMonitoringController.java # 성능 모니터링
│   │   ├── ProgressController.java              # 진행률 관리
│   │   ├── DatabaseTestController.java          # 관리자 테스트
│   │   └── TSPMTestController.java              # 개발/테스트용
│   ├── service/                         # 비즈니스 로직 서비스
│   │   ├── DocumentAnalysisService.java         # ✅ 새로운 통합 분석 서비스
│   │   ├── LAMService.java              # 레이아웃 분석 서비스 (마이크로서비스 연동)
│   │   ├── TSPMService.java             # 텍스트 처리 서비스 (Java 네이티브)
│   │   ├── JavaTSPMService.java         # Java 네이티브 TSPM 구현
│   │   ├── CIMService.java              # 콘텐츠 통합 서비스
│   │   ├── AnalysisJobService.java      # 작업 관리 서비스
│   │   ├── PerformanceMonitoringService.java # 성능 모니터링
│   │   └── ProgressTrackingService.java # 진행률 추적
│   ├── dto/                             # ✅ 정리된 데이터 전송 객체
│   │   ├── request/                     # 요청 DTO
│   │   │   └── AnalysisRequest.java
│   │   ├── response/                    # 응답 DTO
│   │   │   ├── AnalysisResponse.java
│   │   │   ├── AnalysisResult.java
│   │   │   └── ApiResponse.java
│   │   └── lam/                         # LAM 전용 DTO
│   │       ├── LAMAnalysisRequest.java
│   │       ├── LAMAnalysisResponse.java
│   │       └── LAMAnalysisOptions.java
│   ├── model/entity/                    # JPA 엔티티
│   │   ├── AnalysisJob.java
│   │   ├── LayoutBlock.java
│   │   ├── TextBlock.java
│   │   ├── CIMOutput.java
│   │   ├── ProcessingLog.java
│   │   └── User.java
│   ├── repository/                      # 데이터 접근 계층
│   ├── exception/                       # ✅ 개선된 예외 처리
│   │   ├── DocumentAnalysisException.java
│   │   ├── TSPMAnalysisException.java
│   │   ├── FileProcessingException.java
│   │   ├── LAMServiceException.java
│   │   └── GlobalExceptionHandler.java
│   ├── config/                          # 설정 클래스
│   └── client/                          # 외부 서비스 클라이언트
├── smarteye-lam-service/                # LAM 마이크로서비스
│   ├── app/
│   │   ├── main.py                      # FastAPI 메인
│   │   ├── layout_analyzer.py           # 레이아웃 분석기
│   │   ├── layout_analyzer_optimized.py # 최적화된 분석기
│   │   ├── model_manager.py             # 모델 관리
│   │   └── models.py                    # Pydantic 모델
│   ├── Dockerfile.optimized
│   └── requirements.txt
├── scripts/                             # 배포 및 실행 스크립트
├── docker-compose.yml                   # 프로덕션 환경
├── docker-compose.dev.yml               # 개발 환경
└── docs/                                # 프로젝트 문서
```

## 빌드 및 실행

### 환경 설정

#### 1. 환경변수 설정 (권장)
```bash
# 환경변수 스크립트 실행
source scripts/setup-env.sh

# 또는 수동 설정
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smarteye_db
export SPRING_DATASOURCE_USERNAME=smarteye
export SPRING_DATASOURCE_PASSWORD=smarteye123
export OPENAI_API_KEY=your_openai_api_key
```

#### 2. 개발환경 (H2 데이터베이스)
```bash
# 개발 프로필로 실행 (H2 인메모리 DB 사용)
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

#### 3. 운영환경 (PostgreSQL)
```bash
# PostgreSQL 설정 후 실행
source scripts/setup-env.sh
./gradlew bootRun
```

### 전체 시스템 실행 (Docker Compose)
```bash
# 개발 환경
docker-compose -f docker-compose.dev.yml up -d

# 프로덕션 환경
docker-compose up -d
```

### Spring Boot 단독 실행
```bash
# 개발 환경 실행 (H2)
./gradlew bootRun

# 운영 환경 실행 (PostgreSQL)
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun

# 또는 스크립트 사용
./scripts/run.sh dev
```

### 데이터베이스 연결 테스트
```bash
# 개발환경 (H2) 테스트
curl http://localhost:8080/api/test/db-connection

# 운영환경 (PostgreSQL) 테스트  
curl http://localhost:8080/api/test/db-info
curl http://localhost:8080/api/test/db-entities

# 테스트 데이터 생성
curl -X POST http://localhost:8080/api/test/create-test-data
```

### LAM 마이크로서비스 단독 실행
```bash
cd smarteye-lam-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

### JAR 파일 빌드
```bash
./gradlew bootJar
# 결과: build/libs/smarteye-spring-backend-0.1.0.jar
```

### 테스트 실행
```bash
./gradlew test
```

## API 엔드포인트 (v0.1 리팩토링 완료)

### 🎯 메인 통합 분석 API (`/api/v2/analysis`)
**IntegratedAnalysisController** - 모든 분석 기능이 통합된 메인 컨트롤러

#### 통합 분석
- **POST** `/api/v2/analysis/integrated` - 완전한 통합 분석 (LAM + TSPM)
  - Parameters: `file`, `analysisType`(lam/tspm/both), `confidenceThreshold`
  - 성능 모니터링 및 비교 기능 포함

#### 개별 서비스 분석
- **POST** `/api/v2/analysis/lam/analyze` - LAM 전용 분석 (마이크로서비스)
  - Parameters: `file`, `confidenceThreshold`, `maxBlocks`, `detectText`, `detectTables`, `detectFigures`
- **POST** `/api/v2/analysis/tspm/analyze` - TSPM 전용 분석 (Java 네이티브)
  - Parameters: `file`

#### 시스템 상태 및 헬스체크
- **GET** `/api/v2/analysis/status` - 전체 시스템 상태 확인
- **GET** `/api/v2/analysis/lam/health` - LAM 마이크로서비스 상태
- **GET** `/api/v2/analysis/lam/model/info` - LAM 모델 정보
- **GET** `/api/v2/analysis/lam/test` - LAM 연결 테스트

#### 성능 비교
- **POST** `/api/v2/analysis/compare` - 분석 방법별 성능 비교

### 성능 모니터링 API (`/api/monitoring`)
- **GET** `/api/monitoring/performance` - 성능 메트릭 조회
- **GET** `/api/monitoring/health` - 전체 시스템 헬스체크
- **GET** `/api/monitoring/summary` - 성능 요약

### 진행률 추적 API (`/api/progress`)
- **GET** `/api/progress/{jobId}` - 작업 진행률 조회
- **GET** `/api/progress/jobs/active` - 활성 작업 목록

### 개발/테스트 API
#### TSPM 테스트 (`/api/test`)
- **POST** `/api/test/tspm-java` - Java 네이티브 TSPM 테스트
- **GET** `/api/test/tspm-status` - TSPM 서비스 상태

#### 데이터베이스 테스트 (`/api/test`)  
- **GET** `/api/test/db-connection` - 데이터베이스 연결 테스트
- **GET** `/api/test/db-info` - 데이터베이스 정보
- **GET** `/api/test/db-entities` - 엔티티 상태 확인
- **POST** `/api/test/create-test-data` - 테스트 데이터 생성

### 진행 상황 추적
- **GET** `/api/progress/{jobId}` - 분석 작업 진행 상황
- **WebSocket** `/ws/progress` - 실시간 진행 상황

### 요청 파라미터
- `file`: 분석할 문서 파일 (MultipartFile)
- `analysisType`: 분석 타입 (FULL, LAYOUT_ONLY, TEXT_ONLY)
- `language`: OCR 언어 설정 (기본값: "kor+eng")
- `confidence`: 신뢰도 임계값 (기본값: 0.5)

## 설정

### 환경변수
```bash
# 필수 환경변수
export SPRING_PROFILES_ACTIVE=prod  # dev 또는 prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smarteye_db
export SPRING_DATASOURCE_USERNAME=smarteye
export SPRING_DATASOURCE_PASSWORD=smarteye123
export OPENAI_API_KEY=your_openai_api_key

# 선택 환경변수
export LAM_SERVICE_URL=http://localhost:8081
export TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
export TESSERACT_LANGUAGE=kor+eng
```

### 주요 설정 (application.yml)
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
smarteye:
  upload:
    max-file-size: 50MB
    temp-dir: ./temp
    allowed-extensions: jpg,jpeg,png,pdf,tiff,bmp
  
  # LAM 마이크로서비스 설정
  lam:
    service:
      url: ${LAM_SERVICE_URL:http://localhost:8081}
      timeout: 30
      retries: 3
      confidence-threshold: 0.5
  
  # TSPM 설정  
  tspm:
    use-java-native: true
  
  # Tesseract OCR 설정
  tesseract:
    data-path: ${TESSERACT_DATA_PATH:/usr/share/tesseract-ocr/5/tessdata}
    language: ${TESSERACT_LANGUAGE:kor+eng}
  
  # OpenAI API 설정
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-turbo
    max-tokens: 4096
```

## 사용 예제 (v0.1 API)

### 1. 통합 문서 분석 (권장)
```bash
# 완전한 문서 분석 (LAM + TSPM)
curl -X POST \
  -F "file=@document.pdf" \
  -F "analysisType=both" \
  -F "confidenceThreshold=0.7" \
  http://localhost:8080/api/v2/analysis/integrated

# 응답 예시
{
  "success": true,
  "lam": {
    "success": true,
    "jobId": "lam_20240823_123456",
    "status": "COMPLETED",
    "progress": 100,
    "processingTimeMs": 2500
  },
  "tspm": {
    "success": true,
    "jobId": "tspm_20240823_123457",
    "status": "COMPLETED", 
    "progress": 100,
    "processingTimeMs": 1800
  },
  "analysisType": "both",
  "filename": "document.pdf",
  "totalProcessingTimeMs": 4300,
  "message": "통합 분석이 성공적으로 완료되었습니다"
}
```

### 2. LAM 전용 분석
```bash
# 레이아웃 분석만 실행
curl -X POST \
  -F "file=@layout_document.jpg" \
  -F "confidenceThreshold=0.5" \
  -F "detectText=true" \
  -F "detectTables=true" \
  -F "detectFigures=true" \
  http://localhost:8080/api/v2/analysis/lam/analyze
```

### 3. TSPM 전용 분석  
```bash
# 텍스트 추출 및 의미 분석만 실행
curl -X POST \
  -F "file=@text_document.png" \
  http://localhost:8080/api/v2/analysis/tspm/analyze
```

### 4. 시스템 상태 확인
```bash
# 전체 시스템 상태 조회
curl http://localhost:8080/api/v2/analysis/status

# LAM 마이크로서비스 상태
curl http://localhost:8080/api/v2/analysis/lam/health

# 성능 비교 테스트
curl -X POST \
  -F "file=@test_document.jpg" \
  http://localhost:8080/api/v2/analysis/compare
```

### 5. 개발자 테스트 API
```bash
# TSPM Java 네이티브 테스트
curl -X POST \
  -F "file=@test.jpg" \
  http://localhost:8080/api/test/tspm-java

# 데이터베이스 연결 확인
curl http://localhost:8080/api/test/db-connection

# 테스트 데이터 생성
curl -X POST http://localhost:8080/api/test/create-test-data
```

## 모니터링 및 관리

### Spring Boot Actuator 엔드포인트
```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 메트릭스 조회
curl http://localhost:8080/actuator/metrics

# 데이터베이스 상태
curl http://localhost:8080/actuator/datasource

# 애플리케이션 정보
curl http://localhost:8080/actuator/info

# Prometheus 메트릭스 (모니터링 시스템 연동)
curl http://localhost:8080/actuator/prometheus
```

### 데이터베이스 모니터링
```bash
# PostgreSQL 연결 상태 확인
curl http://localhost:8080/api/test/db-connection

# 데이터베이스 정보 조회
curl http://localhost:8080/api/test/db-info

# 엔티티 수 확인
curl http://localhost:8080/api/test/db-entities
```

### 로그 모니터링
```bash
# 애플리케이션 로그
tail -f logs/smarteye-prod.log

# 실시간 로그 확인
./gradlew bootRun | grep -E "(ERROR|WARN|INFO)"
```

## 모듈 구조

### LAM (Layout Analysis Module)
- **기능**: DocLayout-YOLO 기반 문서 레이아웃 분석
- **구현**: Python FastAPI 마이크로서비스
- **특징**: GPU/CPU 자동 감지, Redis 캐싱, 비동기 처리
- **엔드포인트**: `/api/lam/*`

### TSPM (Text & Semantic Processing Module)  
- **기능**: Tesseract OCR 텍스트 추출 + OpenAI Vision API 의미 분석
- **구현**: Java 네이티브 (Spring Boot)
- **특징**: 멀티 언어 OCR, 의미 분석, 텍스트 후처리
- **엔드포인트**: `/api/tspm/*`

### CIM (Content Integration Module)
- **기능**: LAM + TSPM 결과 통합 및 최종 출력
- **구현**: Java (Spring Boot)
- **특징**: 결과 병합, 품질 검증, 최종 포맷팅
- **엔드포인트**: `/api/analysis/*`

### 성능 모니터링 시스템
- **기능**: 실시간 성능 추적, 리소스 모니터링, 알림
- **구현**: Java (Spring Boot) + 메트릭 수집
- **특징**: 임계값 기반 알림, 성능 대시보드
- **엔드포인트**: `/api/monitoring/*`

## 개발 가이드 (v0.1 리팩토링 완료)

### 리팩토링된 아키텍처 패턴

#### 1. 중앙 집중식 분석 서비스
```java
// DocumentAnalysisService: 모든 분석 로직의 중앙 관리
@Service
public class DocumentAnalysisService {
    
    // 완전한 분석 파이프라인
    public Map<String, Object> performCompleteAnalysis(MultipartFile file, String analysisType, double confidenceThreshold)
    
    // LAM 전용 분석
    public Map<String, Object> performLAMAnalysis(MultipartFile file, LAMAnalysisOptions options)
    
    // TSPM 전용 분석  
    public Map<String, Object> performTSPMAnalysis(MultipartFile file)
    
    // 시스템 상태 확인
    public Map<String, Object> getSystemStatus()
}
```

#### 2. 통합 컨트롤러 패턴
```java
// IntegratedAnalysisController: 모든 분석 API의 단일 진입점
@RestController
@RequestMapping("/api/v2/analysis")
public class IntegratedAnalysisController {
    
    private final DocumentAnalysisService documentAnalysisService;
    
    @PostMapping("/integrated")  // 메인 통합 분석
    @PostMapping("/lam/analyze") // LAM 전용
    @PostMapping("/tspm/analyze") // TSPM 전용
    @GetMapping("/status")       // 시스템 상태
}
```

#### 3. 정리된 DTO 구조
```java
// 요청 DTO
src/main/java/com/smarteye/dto/request/
├── AnalysisRequest.java

// 응답 DTO  
src/main/java/com/smarteye/dto/response/
├── AnalysisResponse.java
├── AnalysisResult.java
└── ApiResponse.java

// LAM 전용 DTO
src/main/java/com/smarteye/dto/lam/
├── LAMAnalysisRequest.java
├── LAMAnalysisResponse.java
└── LAMAnalysisOptions.java
```

#### 4. 도메인별 예외 처리
```java
// 새로운 예외 클래스들
public class DocumentAnalysisException extends RuntimeException { }
public class TSPMAnalysisException extends RuntimeException { }
public class FileProcessingException extends RuntimeException { }
public class LAMServiceException extends RuntimeException { } // 기존
```

### 새로운 기능 추가 가이드

#### 1. 새로운 분석 모듈 추가
```java
// 1. 서비스 구현
@Service
public class YourAnalysisService {
    public AnalysisJob performYourAnalysis(MultipartFile file) {
        // 분석 로직 구현
    }
}

// 2. DocumentAnalysisService에 통합
public Map<String, Object> performYourAnalysis(MultipartFile file) {
    return yourAnalysisService.performYourAnalysis(file);
}

// 3. IntegratedAnalysisController에 엔드포인트 추가
@PostMapping("/your-module/analyze")
public ResponseEntity<Map<String, Object>> analyzeYourModule(@RequestParam("file") MultipartFile file) {
    return documentAnalysisService.performYourAnalysis(file);
}
```

#### 2. 새로운 DTO 추가
```java
// 요청 DTO
// src/main/java/com/smarteye/dto/request/YourRequest.java
package com.smarteye.dto.request;

public class YourRequest {
    // 필드 정의
}

// 응답 DTO  
// src/main/java/com/smarteye/dto/response/YourResponse.java
package com.smarteye.dto.response;

public class YourResponse {
    // 필드 정의
}
```

#### 3. 새로운 예외 처리 추가
```java
// src/main/java/com/smarteye/exception/YourModuleException.java
package com.smarteye.exception;

public class YourModuleException extends RuntimeException {
    public YourModuleException(String message) {
        super(message);
    }
    
    public YourModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}

// GlobalExceptionHandler에 추가
@ExceptionHandler(YourModuleException.class)
public ResponseEntity<Map<String, Object>> handleYourModuleException(YourModuleException e) {
    // 예외 처리 로직
}
```

### 의존성 추가
1. **서비스 레이어**: `service` 패키지에 비즈니스 로직 구현
2. **컨트롤러**: `controller` 패키지에 REST API 엔드포인트 추가
3. **모델**: `model/entity` 또는 `dto` 패키지에 데이터 모델 정의

### 의존성 추가
```gradle
// build.gradle에 의존성 추가
implementation 'org.example:new-dependency:version'
```

### 새로운 API 엔드포인트 추가
```java
@RestController
@RequestMapping("/api/your-module")
@RequiredArgsConstructor
public class YourController {
    
    private final YourService yourService;
    
    @PostMapping("/action")
    public ResponseEntity<?> performAction(@RequestBody YourRequest request) {
        // 구현
    }
}
```

### LAM 마이크로서비스 확장
```python
# smarteye-lam-service/app/your_analyzer.py
class YourAnalyzer:
    def analyze(self, image_data):
        # 분석 로직 구현
        pass
```

### 테스트 작성
```java
@SpringBootTest
class YourServiceTest {
    
    @Autowired
    private YourService yourService;
    
    @Test
    void testYourFunction() {
        // 테스트 코드
    }
}
```

## 배포 가이드

### 데이터베이스 마이그레이션 (H2 → PostgreSQL)

#### 완료된 마이그레이션 단계 ✅
1. **PostgreSQL 16 설치 및 구성** - 완료
2. **사용자 및 데이터베이스 생성** - 완료  
3. **권한 설정 및 인증 구성** - 완료
4. **Spring Boot 설정 업데이트** - 완료
5. **엔티티 매핑 및 스키마 생성** - 완료
6. **연결 테스트 및 CRUD 검증** - 완료

#### 마이그레이션 검증 결과
```bash
# ✅ PostgreSQL 연결 성공
curl http://localhost:8080/api/test/db-connection
# Response: {"driver":"PostgreSQL JDBC Driver","success":true,...}

# ✅ 엔티티 매핑 성공 (6개 테이블)
curl http://localhost:8080/api/test/db-entities  
# Response: {"entityCounts":{"analysisJobs":1,"processingLogs":0,...},...}

# ✅ 데이터베이스 정보 확인
curl http://localhost:8080/api/test/db-info
# Response: {"productName":"PostgreSQL","productVersion":"16.9",...}
```

#### 운영 환경 최적화 적용
- **DDL 모드**: `create-drop` → `validate` (운영 안정성)
- **환경변수**: 보안을 위한 외부 설정 적용
- **모니터링**: Spring Boot Actuator 추가
- **연결 풀**: HikariCP 최적화 설정

### Docker Compose를 이용한 전체 시스템 배포
```bash
# 환경 설정
export OPENAI_API_KEY=your_api_key
export SPRING_PROFILES_ACTIVE=prod

# 프로덕션 환경 배포
docker-compose up -d

# 개발 환경 배포  
docker-compose -f docker-compose.dev.yml up -d
```

### 개별 서비스 배포
```bash
# Spring Boot 백엔드만 배포
./scripts/deploy-phase2-complete.sh

# LAM 마이크로서비스만 배포
./scripts/deploy-lam-microservice.sh
```

### 배포 스크립트 활용
```bash
# 전체 시스템 빌드 및 실행
./scripts/run.sh build
./scripts/run.sh run

# 개발/프로덕션 환경별 실행
./scripts/run.sh dev
./scripts/run.sh prod
```

## 모니터링 및 로깅

### 헬스체크 엔드포인트
- **전체 시스템**: http://localhost:8080/api/analysis/health
- **Spring Boot**: http://localhost:8080/actuator/health  
- **LAM 서비스**: http://localhost:8081/health
- **성능 모니터링**: http://localhost:8080/api/monitoring/health

### 데이터베이스 관리
- **H2 Console**: http://localhost:8080/h2-console (개발 환경)
- **PostgreSQL**: docker-compose 환경에서 자동 설정

### 로그 확인
```bash
# Docker 로그 확인
docker-compose logs -f smarteye-backend
docker-compose logs -f smarteye-lam

# 로컬 로그 파일
tail -f logs/smarteye.log
tail -f app.log
```

## 문제 해결

### 일반적인 문제
1. **LAM 서비스 연결 실패**: LAM_SERVICE_URL 환경변수 확인
2. **OpenAI API 오류**: OPENAI_API_KEY 설정 확인  
3. **Tesseract 오류**: Tesseract 설치 및 데이터 경로 확인
4. **메모리 부족**: Docker 메모리 제한 증가

### 디버깅
```bash
# 서비스 상태 확인
curl http://localhost:8080/api/analysis/health
curl http://localhost:8081/health

# 성능 메트릭 확인
curl http://localhost:8080/api/monitoring/performance
```

## 프로젝트 정보

### 버전 정보
- **현재 버전**: v0.1.0 (리팩토링 완료)
- **Spring Boot**: 3.1.5
- **Java**: 17
- **Gradle**: 8.3
- **Python**: 3.9+

## 변경 이력

### v0.1.0 (2024-08-23) - 리팩토링 완료
#### 🔄 Phase 1: 레거시 제거
- ✅ `AnalysisController.java` 제거 (deprecated)
- ✅ `AnalysisService.java` 제거 (deprecated)
- ✅ `LAMMicroserviceController.java` 제거 (기능 통합)

#### 🏗️ Phase 2: 구조 개선
- ✅ DTO 구조 정리: `dto/request/`, `dto/response/` 패키지 분리
- ✅ 패키지 참조 업데이트 및 import 문 정리
- ✅ 빈 디렉토리 정리

#### ⚡ Phase 3: 서비스 통합
- ✅ `DocumentAnalysisService` 생성 - 중앙 집중식 분석 관리
- ✅ `IntegratedAnalysisController`에 LAM 전용 엔드포인트 통합
- ✅ 성능 모니터링 및 에러 처리 통합

#### 🛡️ Phase 4: 예외 처리 개선
- ✅ `DocumentAnalysisException` 추가
- ✅ `TSPMAnalysisException` 추가
- ✅ `FileProcessingException` 추가

### v0.0.x (이전 버전)
- **Phase 1**: 기본 아키텍처 구현 (완료)
- **Phase 2**: LAM 마이크로서비스 통합 (완료)  
- **Phase 3**: 시스템 최적화 및 성능 모니터링 (완료)

## 향후 계획

### v0.2.0 (계획)
- 🎯 도메인 중심 아키텍처 전환
- 📦 패키지 구조 완전 개편
- 🧪 테스트 코드 보강
- 📊 고급 성능 분석 기능

### 라이센스
MIT License

### 기여 가이드
1. Fork 프로젝트
2. Feature 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 변경사항 커밋 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 Push (`git push origin feature/amazing-feature`)
5. Pull Request 생성

### 문서
- **상세 문서**: `docs/` 폴더 참조
- **API 문서**: `/api/v2/analysis` 엔드포인트 참조
- **아키텍처 가이드**: `.github/copilot-instructions.md`

### 연락처
SmartEye 개발팀
