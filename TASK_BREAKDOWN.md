# SmartEye Python to Java/Spring Backend - 상세 작업 분해

## 📋 프로젝트 타임라인 (5주 계획)

### 🔄 작업 우선순위 분류
- 🔴 **Critical**: 핵심 기능, 블로커 작업
- 🟡 **High**: 중요 기능
- 🟢 **Medium**: 부가 기능
- 🔵 **Low**: 최적화, 개선사항

---

## 📅 Phase 1: 기본 Spring Boot 구조 설정 (1주차: 1-5일)

### Day 1: 프로젝트 초기 설정
#### Task 1.1: build.gradle 의존성 설정 🔴
- [ ] 기존 build.gradle 백업
- [ ] Spring Boot 3.x 의존성 추가
  - spring-boot-starter-data-jpa
  - spring-boot-starter-web
  - spring-boot-starter-webflux
  - spring-boot-starter-validation
- [ ] 이미지/PDF 처리 의존성 추가
  - apache-pdfbox:3.0.0
  - apache-tika:2.9.0
  - bytedeco-javacv:1.5.8
  - bytedeco-opencv:4.6.0-1.5.8
- [ ] OCR 의존성 추가
  - tess4j:5.8.0
- [ ] 문서 처리 의존성 추가
  - apache-poi-ooxml:5.2.4
- [ ] 데이터베이스 의존성 추가
  - postgresql
- [ ] 빌드 테스트 실행

#### Task 1.2: 패키지 구조 재구성 🔴
- [ ] 기존 패키지 구조 분석
- [ ] 새로운 패키지 구조 생성
  ```
  com.smarteye.smarteye_backend/
  ├── SmarteyeBackendApplication.java
  ├── config/
  ├── controller/
  ├── dto/
  ├── entity/
  ├── repository/
  ├── service/
  ├── util/
  └── exception/
  ```
- [ ] 기존 SmarteyeBackendApplication.java 수정

### Day 2: 설정 파일 구성
#### Task 1.3: application.yml 설정 🔴
- [ ] application.properties → application.yml 변환
- [ ] 데이터베이스 연결 설정
  ```yaml
  spring:
    datasource:
      url: jdbc:postgresql://localhost:5432/smarteye_db
      username: ${DB_USERNAME:smarteye}
      password: ${DB_PASSWORD:password}
  ```
- [ ] JPA/Hibernate 설정
- [ ] 파일 업로드 설정 (최대 50MB)
- [ ] CORS 설정

#### Task 1.4: 프로파일별 설정 파일 🟡
- [ ] application-dev.yml 생성
- [ ] application-prod.yml 생성
- [ ] 환경별 설정 분리

#### Task 1.5: SmartEye 커스텀 설정 클래스 🟡
- [ ] SmartEyeProperties.java 생성
- [ ] 파일 경로, OCR 설정, API 키 등 관리
- [ ] @ConfigurationProperties 어노테이션 적용

### Day 3: 기본 인프라 구성
#### Task 1.6: 예외 처리 시스템 🔴
- [ ] GlobalExceptionHandler.java 생성
- [ ] 커스텀 예외 클래스들 생성
  - DocumentAnalysisException
  - FileProcessingException
  - LAMServiceException
- [ ] REST API 표준 에러 응답 형태 정의

#### Task 1.7: 공통 유틸리티 클래스 🟡
- [ ] FileUtils.java 생성 (파일 저장/삭제/변환)
- [ ] ImageUtils.java 생성 (이미지 처리 유틸리티)
- [ ] JsonUtils.java 생성 (JSON 변환 유틸리티)

#### Task 1.8: 로깅 설정 🟢
- [ ] logback-spring.xml 설정
- [ ] 개발/운영 환경별 로그 레벨 설정
- [ ] 파일 로깅 설정

### Day 4-5: 기본 웹 설정 및 테스트
#### Task 1.9: 웹 설정 🔴
- [ ] WebConfig.java 생성
- [ ] CORS 설정
- [ ] 정적 파일 서빙 설정
- [ ] MultipartResolver 설정

#### Task 1.10: 헬스체크 API 🟡
- [ ] HealthController.java 생성
- [ ] /api/health 엔드포인트 구현
- [ ] 시스템 상태 확인 기능

#### Task 1.11: 기본 테스트 환경 구성 🟡
- [ ] 테스트용 H2 데이터베이스 설정
- [ ] 기본 통합 테스트 작성
- [ ] 애플리케이션 구동 테스트

---

## 📅 Phase 2: 데이터베이스 모델링 (1주차: 6-7일 + 2주차: 1-3일)

### Day 6-7: 핵심 엔티티 설계
#### Task 2.1: 사용자 관리 엔티티 🔴
- [ ] User.java 엔티티 생성
  - id, username, email, createdAt, updatedAt
  - @Entity, @Table, @Id, @GeneratedValue
- [ ] UserRepository.java 인터페이스 생성

#### Task 2.2: 분석 작업 엔티티 🔴
- [ ] AnalysisJob.java 엔티티 생성
  - id, jobId(UUID), originalFilename, status, createdAt, completedAt
  - User와의 @ManyToOne 관계 설정
- [ ] AnalysisJobRepository.java 생성

#### Task 2.3: 문서 페이지 엔티티 🔴
- [ ] DocumentPage.java 엔티티 생성
  - id, imagePath, pageNumber, analysisResult(JSON)
  - AnalysisJob과의 @ManyToOne 관계
- [ ] DocumentPageRepository.java 생성

### Day 1-3 (2주차): 레이아웃 분석 엔티티
#### Task 2.4: 레이아웃 블록 엔티티 🔴
- [ ] LayoutBlock.java 엔티티 생성
  - id, className, confidence, x1, y1, x2, y2
  - ocrText, aiDescription
  - DocumentPage와의 @ManyToOne 관계
- [ ] LayoutBlockRepository.java 생성

#### Task 2.5: 텍스트 블록 엔티티 🟡
- [ ] TextBlock.java 엔티티 생성 (OCR 결과)
  - id, extractedText, coordinates, confidence
  - LayoutBlock과의 @OneToOne 관계
- [ ] TextBlockRepository.java 생성

#### Task 2.6: CIM 출력 엔티티 🟡
- [ ] CIMOutput.java 엔티티 생성
  - id, cimData(JSON), formattedText, wordDocumentPath
  - AnalysisJob과의 @OneToOne 관계
- [ ] CIMOutputRepository.java 생성

#### Task 2.7: 처리 로그 엔티티 🟢
- [ ] ProcessingLog.java 엔티티 생성 (디버깅용)
  - id, jobId, step, message, timestamp, level
- [ ] ProcessingLogRepository.java 생성

#### Task 2.8: 데이터베이스 초기화 스크립트 🟡
- [ ] schema.sql 생성 (테이블 스키마)
- [ ] data.sql 생성 (초기 데이터)
- [ ] Flyway 또는 Liquibase 마이그레이션 설정

---

## 📅 Phase 3: 핵심 서비스 구현 (2주차: 4-7일 + 3주차: 1-4일)

### Day 4-7 (2주차): 기본 서비스 구현
#### Task 3.1: 파일 처리 서비스 🔴
- [ ] FileService.java 생성
- [ ] 파일 업로드/저장 메서드
  ```java
  public String saveUploadedFile(MultipartFile file, String jobId)
  public void deleteFile(String filepath)
  public List<String> listFiles(String directory)
  ```
- [ ] 임시 파일 정리 메서드
- [ ] 파일 유효성 검사 (크기, 형식)

#### Task 3.2: 이미지 처리 서비스 🔴
- [ ] ImageProcessingService.java 생성
- [ ] BufferedImage 변환 메서드
- [ ] 이미지 리사이징/회전 기능
- [ ] 이미지 형식 변환 (PNG, JPG 등)

#### Task 3.3: PDF 처리 서비스 🔴
- [ ] PDFService.java 생성
- [ ] PDF → 이미지 변환 메서드
  ```java
  public List<BufferedImage> convertPDFToImages(InputStream pdfStream)
  public BufferedImage convertPDFPageToImage(PDDocument doc, int pageNum)
  ```
- [ ] 멀티페이지 처리 로직
- [ ] PDF 메타데이터 추출

### Day 1-4 (3주차): AI/OCR 서비스 구현
#### Task 3.4: OCR 서비스 🔴
- [ ] OCRService.java 생성
- [ ] Tesseract 초기화 (@PostConstruct)
- [ ] 텍스트 추출 메서드
  ```java
  public String extractText(BufferedImage image)
  public List<TextResult> extractTextWithCoordinates(BufferedImage image)
  ```
- [ ] 언어 설정 (한국어+영어)
- [ ] OCR 설정 최적화

#### Task 3.5: LAM 서비스 인터페이스 🔴
- [ ] LAMService.java 인터페이스 생성
- [ ] 레이아웃 분석 메서드 정의
  ```java
  public CompletableFuture<LayoutAnalysisResult> analyzeLayout(BufferedImage image, String modelChoice)
  public List<LayoutBlock> processLayoutResults(Object lamResponse)
  ```

#### Task 3.6: LAM 마이크로서비스 클라이언트 🔴
- [ ] LAMServiceClient.java 생성
- [ ] WebClient를 이용한 HTTP 통신
- [ ] 서킷 브레이커 패턴 적용 (@CircuitBreaker)
- [ ] 재시도 로직 구현
- [ ] 헬스체크 기능

#### Task 3.7: AI 설명 서비스 🟡
- [ ] AIDescriptionService.java 생성
- [ ] OpenAI Vision API 클라이언트
- [ ] 이미지 → Base64 변환
- [ ] 프롬프트 템플릿 관리
- [ ] API 키 보안 처리

#### Task 3.8: 결과 시각화 서비스 🟡
- [ ] VisualizationService.java 생성
- [ ] 바운딩 박스 그리기
- [ ] 클래스별 색상 매핑
- [ ] 결과 이미지 생성

---

## 📅 Phase 4: REST API 컨트롤러 구현 (3주차: 5-7일 + 4주차: 1-3일)

### Day 5-7 (3주차): 메인 분석 API
#### Task 4.1: DTO 클래스 생성 🔴
- [ ] 요청 DTO 생성
  - AnalysisRequest.java
  - PDFAnalysisRequest.java
- [ ] 응답 DTO 생성
  - AnalysisResponse.java
  - LayoutAnalysisResult.java
  - OCRResult.java
  - AIDescriptionResult.java

#### Task 4.2: 메인 분석 컨트롤러 🔴
- [ ] DocumentAnalysisController.java 생성
- [ ] 단일 이미지 분석 엔드포인트
  ```java
  @PostMapping("/api/analysis/analyze")
  public ResponseEntity<AnalysisResponse> analyzeDocument(@RequestParam MultipartFile image, ...)
  ```
- [ ] 배치 이미지 분석 엔드포인트
- [ ] PDF 분석 엔드포인트
- [ ] 요청 유효성 검사 (@Valid)

#### Task 4.3: 비동기 분석 처리 🟡
- [ ] @Async 어노테이션 활용
- [ ] CompletableFuture 반환
- [ ] 작업 상태 추적 기능
- [ ] 진행률 업데이트 API

### Day 1-3 (4주차): 문서 처리 API
#### Task 4.4: 문서 처리 컨트롤러 🔴
- [ ] DocumentProcessingController.java 생성
- [ ] JSON → 포맷팅된 텍스트 변환 API
  ```java
  @PostMapping("/api/document/format-text")
  public ResponseEntity<FormatTextResponse> formatText(@RequestParam MultipartFile jsonFile)
  ```
- [ ] 워드 문서 생성 API
- [ ] 파일 다운로드 API

#### Task 4.5: 작업 관리 컨트롤러 🟡
- [ ] JobManagementController.java 생성
- [ ] 작업 상태 조회 API
- [ ] 작업 목록 조회 API (페이징)
- [ ] 작업 삭제 API

#### Task 4.6: 사용자 관리 컨트롤러 🟢
- [ ] UserController.java 생성
- [ ] 사용자 등록/조회 API
- [ ] 사용자별 작업 이력 조회

---

## 📅 Phase 5: 추가 기능 구현 (4주차: 4-7일)

### Day 4-5: 워드 문서 생성 서비스
#### Task 5.1: 문서 생성 서비스 🔴
- [ ] DocumentGenerationService.java 생성
- [ ] Apache POI를 이용한 워드 문서 생성
- [ ] 텍스트 포맷팅 규칙 적용
- [ ] 이미지 삽입 기능
- [ ] 템플릿 기반 문서 생성

#### Task 5.2: 텍스트 포맷팅 서비스 🟡
- [ ] TextFormattingService.java 생성
- [ ] CIM JSON → 구조화된 텍스트 변환
- [ ] 클래스별 포맷팅 규칙 적용
- [ ] 마크다운 형식 지원

### Day 6-7: 캐싱 및 성능 최적화
#### Task 5.3: 캐싱 시스템 🟡
- [ ] @Cacheable 어노테이션 적용
- [ ] Redis 캐시 설정 (선택사항)
- [ ] OCR 결과 캐싱
- [ ] 이미지 처리 결과 캐싱

#### Task 5.4: 배치 처리 최적화 🟡
- [ ] Spring Batch 설정
- [ ] 대용량 이미지 배치 처리
- [ ] 병렬 처리 최적화
- [ ] 메모리 사용량 최적화

---

## 📅 Phase 6: LAM 마이크로서비스 구현 (5주차: 1-3일)

### Day 1-2: LAM 서비스 분리
#### Task 6.1: LAM 마이크로서비스 프로젝트 생성 🔴
- [ ] smarteye-lam-service/ 디렉토리 생성
- [ ] FastAPI 기반 서비스 구현
- [ ] DocLayout-YOLO 모델 로딩
- [ ] REST API 엔드포인트 구현

#### Task 6.2: Docker 컨테이너화 🔴
- [ ] Dockerfile 생성
- [ ] requirements.txt 정의
- [ ] Docker Compose 설정
- [ ] 개발 환경 컨테이너 설정

#### Task 6.3: 서비스 간 통신 🔴
- [ ] Java → Python 서비스 HTTP 통신
- [ ] 에러 핸들링 및 재시도 로직
- [ ] 헬스체크 API 구현

### Day 3: 모니터링 및 로깅
#### Task 6.4: 서비스 모니터링 🟡
- [ ] Actuator 엔드포인트 활성화
- [ ] 메트릭스 수집 설정
- [ ] LAM 서비스 상태 모니터링

---

## 📅 Phase 7: 통합 테스트 및 배포 (5주차: 4-7일)

### Day 4-5: 통합 테스트
#### Task 7.1: 단위 테스트 작성 🔴
- [ ] Service 계층 단위 테스트
- [ ] Repository 계층 테스트
- [ ] Controller 계층 테스트 (@WebMvcTest)

#### Task 7.2: 통합 테스트 작성 🔴
- [ ] 전체 워크플로우 테스트
- [ ] 데이터베이스 통합 테스트
- [ ] 마이크로서비스 간 통신 테스트

#### Task 7.3: 성능 테스트 🟡
- [ ] JMeter 또는 Gatling 테스트
- [ ] 동시 접속자 테스트
- [ ] 메모리 사용량 프로파일링

### Day 6-7: 배포 및 문서화
#### Task 7.4: 배포 스크립트 작성 🟡
- [ ] Docker Compose 운영 설정
- [ ] 환경 변수 관리
- [ ] 데이터베이스 마이그레이션 스크립트

#### Task 7.5: API 문서화 🟢
- [ ] Swagger/OpenAPI 설정
- [ ] API 명세서 자동 생성
- [ ] 사용자 가이드 작성

#### Task 7.6: 모니터링 대시보드 🟢
- [ ] 애플리케이션 메트릭스 시각화
- [ ] 로그 분석 도구 설정
- [ ] 알림 시스템 구성

---

## 📊 작업 우선순위 매트릭스

### 🔴 Critical (반드시 완료, 블로커)
- build.gradle 의존성 설정
- 패키지 구조 재구성
- 핵심 엔티티 생성 (User, AnalysisJob, DocumentPage, LayoutBlock)
- 파일 처리 서비스
- OCR 서비스
- LAM 서비스 클라이언트
- 메인 분석 컨트롤러
- 문서 생성 서비스

### 🟡 High (중요 기능)
- 설정 파일 및 프로파일
- 예외 처리 시스템
- PDF 처리 서비스
- AI 설명 서비스
- 비동기 처리
- 텍스트 포맷팅

### 🟢 Medium (부가 기능)
- 로깅 설정
- 캐싱 시스템
- 배치 처리
- 작업 관리 API
- 성능 테스트

### 🔵 Low (최적화)
- 사용자 관리 시스템
- 모니터링 대시보드
- API 문서화
- 배포 자동화

---

## 🎯 마일스톤 체크포인트

### Week 1 완료 기준
- [ ] Spring Boot 애플리케이션 정상 구동
- [ ] PostgreSQL 데이터베이스 연결 성공
- [ ] 기본 엔티티 생성 및 테이블 자동 생성 확인
- [ ] 파일 업로드 API 동작 테스트

### Week 2 완료 기준
- [ ] 모든 엔티티 및 Repository 구현 완료
- [ ] 기본 서비스 계층 구현 완료
- [ ] OCR 기능 단독 테스트 성공

### Week 3 완료 기준
- [ ] LAM 마이크로서비스와 통신 성공
- [ ] 단일 이미지 분석 API 완전 동작
- [ ] 결과 시각화 기능 구현

### Week 4 완료 기준
- [ ] PDF 멀티페이지 처리 완료
- [ ] 워드 문서 생성 기능 완료
- [ ] 배치 이미지 처리 기능 구현

### Week 5 완료 기준
- [ ] 전체 시스템 통합 테스트 완료
- [ ] 성능 최적화 완료
- [ ] 배포 준비 완료

이 작업 분해서를 바탕으로 체계적이고 단계적인 구현을 진행할 수 있습니다.