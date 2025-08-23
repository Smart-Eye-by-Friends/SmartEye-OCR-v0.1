# SmartEye v0.1 백엔드 개발자 가이드

> **SmartEye는 하이브리드 문서 분석 시스템입니다.**  
> Java Spring Boot 백엔드와 Python FastAPI 마이크로서비스가 결합된 3단계 파이프라인으로 구성되어 있습니다.

---

## 📋 목차

1. [시스템 개요](#1-시스템-개요)
2. [환경 설정](#2-환경-설정)
3. [실행 방법](#3-실행-방법)
4. [테스트 방법](#4-테스트-방법)
5. [API 가이드](#5-api-가이드)
6. [아키텍처 상세](#6-아키텍처-상세)
7. [트러블슈팅](#7-트러블슈팅)
8. [개발 가이드](#8-개발-가이드)

---

## 1. 시스템 개요

### 1.1 프로젝트 정보
- **프로젝트명**: SmartEye v0.1
- **기술 스택**: Java 17, Spring Boot 3.1.5, Python 3.9+, FastAPI
- **데이터베이스**: PostgreSQL (프로덕션), H2 (개발)
- **컨테이너**: Docker & Docker Compose

### 1.2 아키텍처 구성
SmartEye는 **마이크로서비스 아키텍처**로 구성된 문서 분석 시스템입니다:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   LAM Service   │    │   TSPM Service  │    │   CIM Service   │
│  (Python:8081)  │    │  (Java Native)  │    │  (Java Native)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                    ┌─────────────────┐
                    │  Spring Boot    │
                    │  Backend:8080   │
                    └─────────────────┘
                                │
                    ┌─────────────────┐    ┌─────────────────┐
                    │   PostgreSQL    │    │     Redis       │
                    │     :5432       │    │     :6379       │
                    └─────────────────┘    └─────────────────┘
```

### 1.3 핵심 모듈
- **LAM** (Layout Analysis Module): DocLayout-YOLO 기반 레이아웃 분석 (Python)
- **TSPM** (Text & Semantic Processing Module): Tesseract OCR + OpenAI Vision API (Java)
- **CIM** (Content Integration Module): 결과 통합 및 후처리 (Java)

---

## 2. 환경 설정

### 2.1 필수 요구사항
```bash
# 시스템 요구사항
Java 17+
Python 3.9+
Docker & Docker Compose
Git
```

### 2.2 프로젝트 클론 및 기본 설정
```bash
# 저장소 클론
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye_v0.1

# 환경설정 파일 복사
cp .env.example .env
cp smarteye-lam-service/.env.example smarteye-lam-service/.env

# 실행 권한 부여
chmod +x scripts/*.sh
```

### 2.3 환경변수 설정

#### 2.3.1 개발 환경 (H2 Database)
```bash
# 개발 환경 설정
source scripts/setup-env.sh dev

# 또는 수동 설정
export SPRING_PROFILES_ACTIVE=dev
export OPENAI_API_KEY=dummy-dev-key  # 개발용
export LAM_SERVICE_URL=http://localhost:8081
```

#### 2.3.2 프로덕션 환경 (PostgreSQL)
```bash
# 프로덕션 환경 설정
source scripts/setup-env.sh prod

# 필수 환경변수 설정
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smarteye_db
export SPRING_DATASOURCE_USERNAME=smarteye
export SPRING_DATASOURCE_PASSWORD=your_secure_password
export OPENAI_API_KEY=your_openai_api_key
```

### 2.4 데이터베이스 설정

#### 2.4.1 PostgreSQL 설치 및 설정 (Ubuntu)
```bash
# PostgreSQL 설치
sudo apt update
sudo apt install postgresql postgresql-contrib

# 데이터베이스 및 사용자 생성
sudo -u postgres createuser smarteye
sudo -u postgres createdb smarteye_db
sudo -u postgres psql -c "ALTER USER smarteye PASSWORD 'smarteye123';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;"
```

#### 2.4.2 연결 테스트
```bash
# PostgreSQL 연결 테스트
psql -h localhost -p 5432 -U smarteye -d smarteye_db
```

### 2.5 OpenAI API 설정
```bash
# OpenAI API 키 설정 (TSPM Vision API용)
export OPENAI_API_KEY=sk-your-openai-api-key-here

# 또는 .env 파일에 추가
echo "OPENAI_API_KEY=sk-your-openai-api-key-here" >> .env
```

---

## 3. 실행 방법

### 3.1 개발 모드 실행 (권장)
```bash
# 통합 개발 환경 실행 (LAM 서비스 포함)
./scripts/run.sh dev

# 실행 결과 확인
# ✅ SmartEye Backend: http://localhost:8080
# ✅ LAM Service: http://localhost:8081
# ✅ H2 Console: http://localhost:8080/h2-console
```

### 3.2 프로덕션 모드 실행
```bash
# 프로덕션 환경 실행
./scripts/run.sh prod

# Docker Compose 사용
docker-compose up -d
```

### 3.3 개별 서비스 실행

#### 3.3.1 Spring Boot 백엔드만 실행
```bash
# Gradle 사용
./gradlew bootRun

# 또는 JAR 파일 실행
./gradlew build
java -jar build/libs/smarteye-spring-backend-0.1.0.jar
```

#### 3.3.2 LAM 마이크로서비스만 실행
```bash
cd smarteye-lam-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload
```

### 3.4 서비스 상태 확인
```bash
# 백엔드 상태 확인
curl http://localhost:8080/actuator/health

# LAM 서비스 상태 확인
curl http://localhost:8081/health

# 데이터베이스 연결 테스트
curl http://localhost:8080/api/test/db-connection
```

---

## 4. 테스트 방법

### 4.1 유닛 테스트 실행
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.smarteye.service.TSPMServiceTest"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

### 4.2 통합 테스트

#### 4.2.1 시스템 헬스체크
```bash
# 전체 시스템 상태 확인
curl http://localhost:8080/api/monitoring/health

# 데이터베이스 연결 테스트
curl http://localhost:8080/api/test/db-connection

# LAM 서비스 연결 테스트
curl http://localhost:8081/health
```

#### 4.2.2 API 기능 테스트
```bash
# 1. 통합 분석 테스트
curl -X POST \
  -F "file=@test_image.jpg" \
  -F "analysisType=both" \
  -F "confidenceThreshold=0.5" \
  http://localhost:8080/api/v2/analysis/integrated

# 2. LAM 전용 분석 테스트
curl -X POST \
  -F "file=@test_image.jpg" \
  http://localhost:8080/api/v2/analysis/lam/analyze

# 3. TSPM Java 네이티브 테스트
curl http://localhost:8080/api/test/tspm-java
```

### 4.3 성능 테스트
```bash
# JMeter를 사용한 부하 테스트 (선택사항)
# 동시 사용자 10명, 1분간 테스트
jmeter -n -t performance_test.jmx -l results.jtl

# AB 테스트 도구 사용
ab -n 100 -c 10 http://localhost:8080/api/v2/analysis/status
```

### 4.4 Docker 환경 테스트
```bash
# Docker Compose 통합 테스트
docker-compose -f docker-compose.dev.yml up -d

# 컨테이너 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs smarteye-backend
docker-compose logs smarteye-lam
```

---

## 5. API 가이드

### 5.1 인증 및 헤더
```bash
# Content-Type 설정 (multipart/form-data for file uploads)
Content-Type: multipart/form-data

# JSON 응답의 경우
Accept: application/json
```

### 5.2 주요 API 엔드포인트

#### 5.2.1 통합 분석 API (권장)
```http
POST /api/v2/analysis/integrated
Content-Type: multipart/form-data

Parameters:
- file: 분석할 이미지/PDF 파일 (required)
- analysisType: "lam", "tspm", "both" (default: "both")
- confidenceThreshold: 0.0-1.0 (default: 0.5)

Response:
{
  "jobId": "uuid-string",
  "status": "COMPLETED",
  "lamResults": { ... },
  "tspmResults": { ... },
  "integratedResults": { ... },
  "processingTime": 2.5,
  "confidence": 0.85
}
```

#### 5.2.2 LAM 분석 API
```http
POST /api/v2/analysis/lam/analyze
Content-Type: multipart/form-data

Parameters:
- file: 분석할 이미지 파일 (required)
- confidenceThreshold: 0.0-1.0 (default: 0.5)

Response:
{
  "jobId": "uuid-string",
  "layoutBlocks": [
    {
      "type": "title",
      "bbox": [x1, y1, x2, y2],
      "confidence": 0.95
    }
  ]
}
```

#### 5.2.3 TSPM 분석 API
```http
POST /api/v2/analysis/tspm/analyze
Content-Type: multipart/form-data

Parameters:
- file: 분석할 이미지 파일 (required)
- useJavaNative: true/false (default: true)

Response:
{
  "jobId": "uuid-string",
  "extractedText": "문서 내용...",
  "semanticAnalysis": { ... },
  "textBlocks": [ ... ]
}
```

### 5.3 상태 확인 API
```http
# 작업 상태 조회
GET /api/v2/analysis/status?jobId={jobId}

# 시스템 상태 확인
GET /api/monitoring/health

# 데이터베이스 연결 테스트
GET /api/test/db-connection
```

### 5.4 에러 응답 형식
```json
{
  "error": "INVALID_FILE_FORMAT",
  "message": "지원되지 않는 파일 형식입니다.",
  "timestamp": "2025-08-23T12:34:56Z",
  "path": "/api/v2/analysis/integrated"
}
```

---

## 6. 아키텍처 상세

### 6.1 프로젝트 구조
```
SmartEye_v0.1/
├── src/main/java/com/smarteye/
│   ├── controller/           # REST API 컨트롤러
│   │   ├── IntegratedAnalysisController.java
│   │   ├── TSPMTestController.java
│   │   └── DatabaseTestController.java
│   ├── service/             # 비즈니스 로직
│   │   ├── LAMService.java
│   │   ├── TSPMService.java
│   │   ├── CIMService.java
│   │   └── DocumentAnalysisService.java
│   ├── model/entity/        # JPA 엔티티
│   │   ├── AnalysisJob.java
│   │   ├── LayoutBlock.java
│   │   └── TextBlock.java
│   ├── config/              # 설정 클래스
│   │   └── SmartEyeProperties.java
│   └── client/              # 외부 서비스 클라이언트
├── smarteye-lam-service/    # Python LAM 마이크로서비스
│   ├── app/
│   │   ├── main.py          # FastAPI 메인 애플리케이션
│   │   ├── config.py        # 설정 및 모델 관리
│   │   └── layout_analyzer.py
│   └── requirements.txt
├── scripts/                 # 관리 스크립트
│   ├── run.sh              # 통합 실행 스크립트
│   ├── setup-env.sh        # 환경변수 설정
│   └── system-manager.sh   # 시스템 관리
└── docker-compose.yml      # 컨테이너 오케스트레이션
```

### 6.2 데이터 플로우
```
1. 클라이언트 요청 → Spring Boot Controller
2. DocumentAnalysisService → 작업 생성 (AnalysisJob)
3. LAMService → Python 마이크로서비스 호출 (HTTP)
4. TSPMService → Java 네이티브 OCR + OpenAI Vision API
5. CIMService → 결과 통합 및 후처리
6. 최종 결과 반환 → 클라이언트
```

### 6.3 핵심 설정 파일

#### 6.3.1 application.yml
```yaml
smarteye:
  upload:
    temp-dir: ./temp
    max-file-size: 50MB
    allowed-extensions: jpg,jpeg,png,pdf,tiff,bmp
  
  lam:
    service:
      url: ${LAM_SERVICE_URL:http://localhost:8081}
      timeout: 30
      retries: 3
      confidence-threshold: 0.5
  
  tspm:
    use-java-native: true
  
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-vision-preview
    max-tokens: 4096
```

#### 6.3.2 LAM 서비스 설정
```python
# smarteye-lam-service/app/config.py
class Settings:
    model_choice = "docstructbench"  # 학습지/교과서 최적화
    confidence_threshold = 0.5
    max_image_size = 4096
    use_gpu = False
```

---

## 7. 트러블슈팅

### 7.1 일반적인 문제 해결

#### 7.1.1 LAM 서비스 연결 실패
```bash
# 문제: LAM 서비스에 연결할 수 없음
# 해결책:
1. LAM 서비스 상태 확인
   curl http://localhost:8081/health
   
2. 포트 사용 여부 확인
   netstat -an | grep 8081
   
3. LAM 서비스 재시작
   cd smarteye-lam-service
   uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload
```

#### 7.1.2 OpenAI API 오류
```bash
# 문제: OpenAI API 키 오류
# 해결책:
1. API 키 확인
   echo $OPENAI_API_KEY
   
2. 환경변수 재설정
   export OPENAI_API_KEY=sk-your-real-api-key
   
3. 개발 모드에서는 더미 키 사용 가능
   export OPENAI_API_KEY=dummy-dev-key
```

#### 7.1.3 데이터베이스 연결 문제
```bash
# 문제: PostgreSQL 연결 실패
# 해결책:
1. PostgreSQL 서비스 상태 확인
   sudo systemctl status postgresql
   
2. 데이터베이스 연결 테스트
   psql -h localhost -p 5432 -U smarteye -d smarteye_db
   
3. 환경변수 확인
   echo $SPRING_DATASOURCE_URL
```

### 7.2 성능 최적화

#### 7.2.1 메모리 사용량 최적화
```bash
# JVM 힙 메모리 설정
export JAVA_OPTS="-Xms512m -Xmx2g"

# LAM 서비스 워커 수 조정
export LAM_WORKERS=2  # CPU 코어 수에 맞게 조정
```

#### 7.2.2 동시 처리 최적화
```yaml
# application.yml
smarteye:
  processing:
    max-parallel-tasks: 4  # CPU 코어 수에 맞게 조정
    timeout: 300s
```

### 7.3 로그 확인 및 디버깅
```bash
# 애플리케이션 로그 확인
tail -f logs/smarteye.log

# Docker 컨테이너 로그
docker logs smarteye-backend
docker logs smarteye-lam-service

# 상세 디버그 로그 활성화
export LOGGING_LEVEL_COM_SMARTEYE=DEBUG
```

---

## 8. 개발 가이드

### 8.1 개발 환경 설정
```bash
# 개발 브랜치 생성
git checkout -b feature/your-feature-name

# 개발 모드로 실행
./scripts/run.sh dev

# 코드 변경 후 자동 재시작 (Spring Boot DevTools)
# application-dev.yml에서 활성화됨
```

### 8.2 새로운 API 추가하기

#### 8.2.1 Controller 생성
```java
@RestController
@RequestMapping("/api/v2/your-module")
@RequiredArgsConstructor
@Slf4j
public class YourController {
    
    private final YourService yourService;
    
    @PostMapping("/analyze")
    public ResponseEntity<YourResponse> analyze(
            @RequestParam("file") MultipartFile file) {
        // 구현 로직
    }
}
```

#### 8.2.2 Service 클래스 생성
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class YourService {
    
    private final AnalysisJobService analysisJobService;
    
    public YourResponse processAnalysis(MultipartFile file) {
        // 비즈니스 로직 구현
    }
}
```

### 8.3 테스트 코드 작성
```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class YourServiceTest {
    
    @Autowired
    private YourService yourService;
    
    @Test
    void testYourMethod() {
        // 테스트 로직
    }
}
```

### 8.4 코드 스타일 가이드
```java
// 1. Lombok 활용
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YourDto {
    private String field1;
    private Integer field2;
}

// 2. 로깅 패턴
log.info("작업 시작 - 파일: {}, 크기: {}", filename, fileSize);
log.debug("중간 처리 결과: {}", intermediateResult);
log.error("오류 발생: {}", e.getMessage(), e);

// 3. 예외 처리
@Service
public class YourService {
    public Result process() {
        try {
            // 비즈니스 로직
        } catch (Exception e) {
            log.error("처리 중 오류 발생: {}", e.getMessage(), e);
            throw new SmartEyeException("처리 실패", e);
        }
    }
}
```

### 8.5 배포 가이드

#### 8.5.1 프로덕션 빌드
```bash
# Gradle 빌드
./gradlew clean build

# Docker 이미지 빌드
docker build -t smarteye-backend:latest .
docker build -t smarteye-lam:latest ./smarteye-lam-service/
```

#### 8.5.2 환경별 배포
```bash
# 개발 환경 배포
docker-compose -f docker-compose.dev.yml up -d

# 프로덕션 환경 배포
docker-compose -f docker-compose.yml up -d
```

---

## 🔗 추가 자료

- **GitHub Repository**: [SmartEye-OCR-v0.1](https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1)
- **API 문서**: http://localhost:8080/swagger-ui/index.html (개발 중)
- **H2 Console**: http://localhost:8080/h2-console (개발 환경)
- **Actuator**: http://localhost:8080/actuator/health

---

## 📞 지원 및 문의

개발 중 문제가 발생하거나 추가 설명이 필요한 경우:

1. **이슈 트래킹**: GitHub Issues 활용
2. **로그 분석**: `logs/smarteye.log` 파일 확인
3. **헬스체크**: `/actuator/health` 엔드포인트 활용
4. **디버그 모드**: `SPRING_PROFILES_ACTIVE=dev` 설정

---

> **주의사항**: 이 문서는 SmartEye v0.1 기준으로 작성되었습니다. 버전 업데이트 시 내용이 변경될 수 있습니다.
