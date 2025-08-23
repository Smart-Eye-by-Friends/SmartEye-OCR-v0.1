# SmartEye v0.1 - 지능형 문서 분석 시스템

## 개요
SmartEye는 AI 기반 문서 레이아웃 분석과 OCR을 통합한 지능형 문서 분석 시스템입니다. Spring Boot 백엔드와 Python 마이크로서비스가 결합된 하이브리드 아키텍처로 구성되어 있습니다.

## 아키텍처 구성
- **Spring Boot 백엔드**: 메인 API 서버 및 TSPM (텍스트 처리 모듈)
- **LAM 마이크로서비스**: DocLayout-YOLO 기반 레이아웃 분석 (Python/FastAPI)
- **통합 처리 파이프라인**: LAM → TSPM → CIM 순차 처리

## 기술 스택

### 백엔드 (Spring Boot)
- Java 17
- Spring Boot 3.1.5
- Gradle 8.3
- PostgreSQL / H2 Database
- Redis (캐싱)
- Tesseract OCR
- OpenAI Vision API

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
- 성능 모니터링 시스템

## 프로젝트 구조
```
SmartEye_v0.1/
├── src/main/java/com/smarteye/          # Spring Boot 애플리케이션
│   ├── controller/                       # REST API 컨트롤러
│   │   ├── AnalysisController.java      # 통합 분석 API
│   │   ├── IntegratedAnalysisController.java
│   │   ├── LAMMicroserviceController.java
│   │   ├── PerformanceMonitoringController.java
│   │   └── TSPMTestController.java
│   ├── service/                         # 비즈니스 로직 서비스
│   │   ├── AnalysisService.java         # 메인 분석 서비스
│   │   ├── LAMService.java              # 레이아웃 분석 서비스
│   │   ├── TSPMService.java             # 텍스트 처리 서비스
│   │   ├── JavaTSPMService.java         # Java 네이티브 TSPM
│   │   ├── CIMService.java              # 콘텐츠 통합 서비스
│   │   └── PerformanceMonitoringService.java
│   ├── model/                           # 데이터 모델 및 엔티티
│   ├── config/                          # 설정 클래스
│   └── dto/                             # 데이터 전송 객체
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

### 전체 시스템 실행 (Docker Compose)
```bash
# 개발 환경
docker-compose -f docker-compose.dev.yml up -d

# 프로덕션 환경
docker-compose up -d
```

### Spring Boot 단독 실행
```bash
# 개발 환경 실행
./gradlew bootRun

# 개발 프로필로 실행
./gradlew bootRunDev

# 또는 스크립트 사용
./scripts/run.sh dev
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

## API 엔드포인트

### 통합 분석 API
- **POST** `/api/analysis/complete` - 전체 분석 파이프라인 (LAM + TSPM + CIM)
- **POST** `/api/analysis/upload` - 동기 문서 분석  
- **POST** `/api/analysis/upload/async` - 비동기 문서 분석
- **GET** `/api/analysis/health` - 서비스 상태 확인

### LAM (Layout Analysis Module)
- **POST** `/api/lam/analyze` - 레이아웃 분석
- **GET** `/api/lam/health` - LAM 서비스 상태

### TSPM (Text & Semantic Processing Module)
- **POST** `/api/tspm/extract-text` - 텍스트 추출
- **POST** `/api/tspm/analyze-semantic` - 의미 분석

### 성능 모니터링
- **GET** `/api/monitoring/performance` - 성능 메트릭 조회
- **GET** `/api/monitoring/health` - 전체 시스템 헬스체크
- **GET** `/api/monitoring/summary` - 성능 요약

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
export OPENAI_API_KEY=your_openai_api_key
export LAM_SERVICE_URL=http://localhost:8081
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export SPRING_PROFILES_ACTIVE=dev
export TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
export TESSERACT_LANGUAGE=kor+eng
```

### 주요 설정 (application.yml)
```yaml
smarteye:
  upload:
    max-file-size: 50MB
    temp-dir: ./temp
    allowed-extensions: jpg,jpeg,png,pdf,tiff,bmp
  
  # LAM 마이크로서비스 설정
  lam:
    service:
      url: http://localhost:8081
      timeout: 30
      retries: 3
      confidence-threshold: 0.5
  
  # TSPM 설정  
  tspm:
    use-java-native: true
  
  # Tesseract OCR 설정
  tesseract:
    data-path: /usr/share/tesseract-ocr/5/tessdata
    language: kor+eng
  
  # OpenAI API 설정
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-vision-preview
    max-tokens: 4096
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

## 개발 가이드

### 새로운 기능 추가
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
- **현재 버전**: v0.1.0
- **Spring Boot**: 3.1.5
- **Java**: 17
- **Gradle**: 8.3
- **Python**: 3.9+

### 라이센스
MIT License

### 기여 가이드
1. Fork 프로젝트
2. Feature 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 변경사항 커밋 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 Push (`git push origin feature/amazing-feature`)
5. Pull Request 생성

### 문서
- **Phase 1**: 기본 아키텍처 구현 (완료)
- **Phase 2**: LAM 마이크로서비스 통합 (완료)  
- **Phase 3**: 시스템 최적화 및 성능 모니터링 (완료)
- **상세 문서**: `docs/` 폴더 참조

### 연락처
SmartEye 개발팀
