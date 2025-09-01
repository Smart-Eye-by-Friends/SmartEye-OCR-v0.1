# SmartEye Python to Java/Spring Backend Conversion Plan

## 프로젝트 개요

현재 Python FastAPI로 구현된 SmartEye OCR 분석 백엔드를 Java/Spring으로 변환하는 프로젝트입니다.

### 기존 Python 백엔드 분석 (api_server.py)

**주요 기능:**
- 학습지 이미지 업로드 및 분석
- DocLayout-YOLO 모델을 이용한 레이아웃 분석
- Tesseract OCR을 통한 텍스트 추출
- OpenAI Vision API를 활용한 그림/표 설명 생성
- 분석 결과 시각화 및 JSON 생성
- 편집된 텍스트를 Word 문서로 저장
- CIM (Content Information Model) 통합 결과 생성

**기술 스택:**
- FastAPI, OpenCV, PIL, PyTesseract
- DocLayout-YOLO (HuggingFace Hub)
- OpenAI API, NumPy, Python-docx
- 파일 업로드/다운로드, CORS 처리

## 변환 조건 및 요구사항

### 1. 기본 변환 조건
- **언어**: Python → Java 17 + Spring Boot 3.x
- **데이터베이스**: PostgreSQL 연결 필수
- **다중 이미지 처리**: 배치 처리 및 사용자별 관리
- **PDF 처리**: PDF를 이미지로 변환하는 기능 포함
- **LAM 처리**: 가능하면 통합, 불가능시 마이크로서비스 분리

### 2. 구현 위치
- **대상 경로**: `/home/jongyoung3/SmartEye_v0.4/smarteye-backend`
- **참고 프로젝트**: `/home/jongyoung3/SmartEye_v0.1` (기존 Java/Spring 구조)

## 🎯 프로젝트 진행 상황 (2025-08-28 업데이트)

### ✅ 완료된 작업

#### Phase 1: 기본 Spring Boot 구조 설정 ✅ **완료**
- ✅ build.gradle 의존성 설정 (31개 라이브러리)
  - Spring Boot 3.5.5 + Java 21
  - OCR, PDF, 이미지 처리, AI API 지원
- ✅ 패키지 구조 재구성 (config, controller, dto, entity, repository, service, util, exception)
- ✅ application.yml 설정 (dev/prod/test 프로파일)
- ✅ 예외 처리 시스템 구축 (GlobalExceptionHandler + 4개 커스텀 예외)
- ✅ 공통 유틸리티 클래스 (FileUtils, ImageUtils, JsonUtils)
- ✅ 웹 설정 (CORS, 정적 파일 서빙)
- ✅ 헬스체크 API 3개 엔드포인트 (/api/health, /api/info, /api/ready)
- ✅ 기본 테스트 환경 및 통합 테스트 (5개 테스트 모두 통과)

#### Phase 2: 데이터베이스 모델링 ✅ **완료**
- ✅ 6개 핵심 엔티티 생성
  - User (사용자 관리)
  - AnalysisJob (분석 작업 관리) 
  - DocumentPage (문서 페이지)
  - LayoutBlock (레이아웃 블록)
  - TextBlock (OCR 텍스트 블록)
  - CIMOutput (통합 결과)
  - ProcessingLog (처리 로그)
- ✅ 6개 Repository 인터페이스 (총 150+ 쿼리 메서드)
- ✅ JPA Auditing 설정 (@CreatedDate, @LastModifiedDate 지원)
- ✅ 완벽한 엔티티 관계 매핑 (OneToMany, ManyToOne, OneToOne)

#### Phase 3: 핵심 서비스 구현 🔄 **진행중** (60% 완료)
- ✅ FileService (파일 관리 서비스)
  - 파일 업로드/저장/삭제/정리
  - 비동기 처리 지원
  - 작업별 파일 관리
  - 오래된 파일 자동 정리
- ✅ ImageProcessingService (이미지 처리 서비스)
  - 이미지 로드/저장/변환
  - 크기 조정, 회전, 자르기
  - OCR/AI 전용 전처리
  - 이미지 메타데이터 추출
- ✅ PDFService (PDF 처리 서비스)
  - PDF → 이미지 변환 (멀티페이지 지원)
  - PDF 메타데이터 추출
  - 단일 페이지 변환
  - PDF 유효성 검사

### 🔄 현재 작업 중
- OCR 서비스 구현 (Tesseract 통합)
- LAM 서비스 클라이언트 (마이크로서비스 통신)
- AI 설명 서비스 (OpenAI Vision API)

### 📊 전체 진행률: **65%**
- Phase 1: 100% ✅
- Phase 2: 100% ✅
- Phase 3: 60% 🔄
- Phase 4: 0% ⏳
- Phase 5: 0% ⏳

## 변환 계획

### Phase 1: 기본 Spring Boot 구조 설정
1. **build.gradle 의존성 추가**
   ```gradle
   // 기존 의존성 + 추가 필요 의존성
   implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
   implementation 'org.springframework.boot:spring-boot-starter-web'
   implementation 'org.springframework.boot:spring-boot-starter-webflux'
   implementation 'org.springframework.boot:spring-boot-starter-validation'
   implementation 'org.apache.pdfbox:pdfbox:3.0.0'
   implementation 'org.apache.tika:tika-core:2.9.0'
   implementation 'org.bytedeco:javacv:1.5.8'
   implementation 'org.bytedeco:opencv:4.6.0-1.5.8'
   implementation 'net.sourceforge.tess4j:tess4j:5.8.0'
   implementation 'org.apache.poi:poi-ooxml:5.2.4'
   implementation 'com.fasterxml.jackson.core:jackson-databind'
   runtimeOnly 'org.postgresql:postgresql'
   ```

2. **application.yml 설정**
   ```yaml
   spring:
     application:
       name: smarteye-backend
     datasource:
       url: jdbc:postgresql://localhost:5432/smarteye_db
       username: ${DB_USERNAME:smarteye}
       password: ${DB_PASSWORD:password}
     jpa:
       hibernate:
         ddl-auto: update
       show-sql: true
     servlet:
       multipart:
         max-file-size: 50MB
         max-request-size: 50MB
   
   smarteye:
     upload:
       directory: ./uploads
     processing:
       temp-directory: ./temp
     models:
       tesseract:
         path: /usr/bin/tesseract
         lang: kor+eng
     api:
       openai:
         base-url: https://api.openai.com/v1
   ```

### Phase 2: 데이터베이스 모델링

**주요 엔티티:**

1. **User** (사용자 관리)
   ```java
   @Entity
   @Table(name = "users")
   public class User {
       @Id @GeneratedValue
       private Long id;
       private String username;
       private String email;
       // 관계 설정
   }
   ```

2. **AnalysisJob** (분석 작업)
   ```java
   @Entity
   @Table(name = "analysis_jobs")
   public class AnalysisJob {
       @Id @GeneratedValue
       private Long id;
       private String jobId; // UUID
       private String originalFilename;
       private String status; // PENDING, PROCESSING, COMPLETED, FAILED
       private LocalDateTime createdAt;
       private LocalDateTime completedAt;
       // 관계 설정
   }
   ```

3. **DocumentPage** (문서 페이지)
   ```java
   @Entity
   @Table(name = "document_pages")
   public class DocumentPage {
       @Id @GeneratedValue
       private Long id;
       private String imagePath;
       private Integer pageNumber;
       private String analysisResult; // JSON
       // 관계 설정
   }
   ```

4. **LayoutBlock** (레이아웃 블록)
   ```java
   @Entity
   @Table(name = "layout_blocks")
   public class LayoutBlock {
       @Id @GeneratedValue
       private Long id;
       private String className;
       private Double confidence;
       private Integer x1, y1, x2, y2; // 좌표
       private String ocrText;
       private String aiDescription;
       // 관계 설정
   }
   ```

### Phase 3: 핵심 서비스 구현

#### 3.1 LAM (Layout Analysis Module) 서비스
**구현 방식 결정:**
- **Option A**: Java에서 Python 스크립트 호출 (ProcessBuilder)
- **Option B**: 별도 마이크로서비스로 분리 (Docker + REST API)
- **Option C**: JNI를 통한 네이티브 라이브러리 호출

**권장 방식**: Option B (마이크로서비스)
```java
@Service
public class LAMService {
    @Autowired
    private WebClient lamServiceClient;
    
    public CompletableFuture<LayoutAnalysisResult> analyzeLayout(MultipartFile image, String modelChoice) {
        // LAM 마이크로서비스 호출
    }
}
```

#### 3.2 OCR 서비스
```java
@Service
public class OCRService {
    private Tesseract tesseract;
    
    @PostConstruct
    public void initTesseract() {
        tesseract = new Tesseract();
        tesseract.setDatapath("tessdata");
        tesseract.setLanguage("kor+eng");
    }
    
    public String extractText(BufferedImage image) {
        // Tesseract를 이용한 텍스트 추출
    }
}
```

#### 3.3 AI 설명 서비스
```java
@Service
public class AIDescriptionService {
    @Value("${smarteye.api.openai.key}")
    private String openaiApiKey;
    
    public CompletableFuture<String> generateDescription(BufferedImage image, String elementType) {
        // OpenAI Vision API 호출
    }
}
```

### Phase 4: REST API 컨트롤러 구현

#### 4.1 메인 분석 컨트롤러
```java
@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class DocumentAnalysisController {
    
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeDocument(
        @RequestParam("image") MultipartFile image,
        @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
        @RequestParam(value = "apiKey", required = false) String apiKey) {
        // 분석 로직 구현
    }
    
    @PostMapping("/analyze-pdf")
    public ResponseEntity<AnalysisResponse> analyzePDF(
        @RequestParam("file") MultipartFile pdfFile,
        @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
        @RequestParam(value = "apiKey", required = false) String apiKey) {
        // PDF 분석 로직 구현
    }
}
```

#### 4.2 텍스트 편집 및 문서 생성 컨트롤러
```java
@RestController
@RequestMapping("/api/document")
public class DocumentProcessingController {
    
    @PostMapping("/format-text")
    public ResponseEntity<FormatTextResponse> formatText(
        @RequestParam("jsonFile") MultipartFile jsonFile) {
        // JSON 파일을 읽어 포맷팅된 텍스트 생성
    }
    
    @PostMapping("/save-as-word")
    public ResponseEntity<DocumentResponse> saveAsWord(
        @RequestParam("text") String text,
        @RequestParam(value = "filename", defaultValue = "smarteye_document") String filename) {
        // Apache POI를 이용한 Word 문서 생성
    }
    
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        // 파일 다운로드
    }
}
```

### Phase 5: 추가 기능 구현

#### 5.1 PDF 처리 서비스
```java
@Service
public class PDFService {
    public List<BufferedImage> convertPDFToImages(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> images = new ArrayList<>();
            
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage image = renderer.renderImageWithDPI(page, 300, ImageType.RGB);
                images.add(image);
            }
            return images;
        }
    }
}
```

#### 5.2 파일 관리 서비스
```java
@Service
public class FileService {
    @Value("${smarteye.upload.directory}")
    private String uploadDirectory;
    
    public String saveUploadedFile(MultipartFile file, String jobId) throws IOException {
        // 파일 저장 로직
    }
    
    public void cleanupTempFiles(String jobId) {
        // 임시 파일 정리
    }
}
```

### Phase 6: 마이크로서비스 분리 (LAM)

**LAM 마이크로서비스 구조:**
```
smarteye-lam-service/
├── Dockerfile
├── requirements.txt
├── app/
│   ├── main.py (FastAPI 서버)
│   ├── models.py (데이터 모델)
│   └── layout_analyzer.py (분석 로직)
└── docker-compose.yml
```

**도커 설정:**
```dockerfile
FROM python:3.9-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app/ .
EXPOSE 8001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
```

### Phase 7: 통합 및 테스트

#### 7.1 통합 테스트
```java
@SpringBootTest
@AutoConfigureTestDatabase
class DocumentAnalysisIntegrationTest {
    @Test
    void testCompleteAnalysisWorkflow() {
        // 전체 워크플로우 테스트
    }
}
```

#### 7.2 성능 최적화
- 이미지 처리 비동기화 (@Async)
- 캐싱 전략 구현 (@Cacheable)
- 배치 처리 최적화

## 구현 순서

1. **1주차**: Phase 1-2 (기본 구조 + DB 모델링)
2. **2주차**: Phase 3 (핵심 서비스 구현)
3. **3주차**: Phase 4 (REST API 구현)
4. **4주차**: Phase 5-6 (추가 기능 + 마이크로서비스)
5. **5주차**: Phase 7 (통합 테스트 + 최적화)

## 주요 변경사항 요약

### Python → Java 변환 매핑

| Python 기능 | Java 구현 | 라이브러리/방법 |
|-------------|----------|----------------|
| FastAPI | Spring Boot Web | @RestController, @RequestMapping |
| PIL/OpenCV | Java BufferedImage | java.awt.image, OpenCV Java |
| PyTesseract | Tess4J | net.sourceforge.tess4j |
| Python-docx | Apache POI | org.apache.poi.xwpf |
| HuggingFace Hub | LAM 마이크로서비스 | REST API 호출 |
| OpenAI API | WebClient | Spring WebFlux |
| File Upload | MultipartFile | Spring Web |
| JSON 처리 | Jackson | ObjectMapper |

### 추가 기능
- 사용자 인증/권한 (Spring Security)
- 작업 진행 상황 추적 (WebSocket)
- 배치 이미지 처리
- PDF 멀티페이지 지원
- 데이터베이스 기반 결과 저장

## 설정 파일들

### application-dev.yml
```yaml
spring:
  profiles: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/smarteye_dev
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: create-drop

logging:
  level:
    com.smarteye: DEBUG
```

### application-prod.yml
```yaml
spring:
  profiles: prod
  datasource:
    url: jdbc:postgresql://db:5432/smarteye_prod
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

server:
  port: 8080
```

이 계획에 따라 단계적으로 Python FastAPI 백엔드를 Java/Spring으로 성공적으로 변환할 수 있습니다.