# SmartEye Spring Boot Backend

## 개요
SmartEye OCR 시스템의 Java/Spring Boot 백엔드 구현입니다.

## 기술 스택
- Java 17
- Spring Boot 3.1.5
- Gradle 8.3
- OpenCV (이미지 처리)
- Tesseract OCR
- OpenAI Vision API

## 프로젝트 구조
```
src/
├── main/
│   ├── java/com/smarteye/
│   │   ├── SmartEyeApplication.java
│   │   ├── config/          # 설정 클래스
│   │   ├── controller/      # REST 컨트롤러
│   │   ├── service/         # 비즈니스 로직
│   │   ├── model/           # 데이터 모델
│   │   └── util/            # 유틸리티 클래스
│   └── resources/
│       ├── application.yml  # 메인 설정
│       ├── application-dev.yml
│       └── application-prod.yml
└── test/                    # 테스트 코드
```

## 빌드 및 실행

### 개발 환경 실행
```bash
./gradlew bootRun
```

### 개발 프로필로 실행
```bash
./gradlew bootRunDev
```

### JAR 파일 빌드
```bash
./gradlew bootJar
```

### 테스트 실행
```bash
./gradlew test
```

## API 엔드포인트

### 문서 분석
- **POST** `/api/analysis/upload` - 동기 문서 분석
- **POST** `/api/analysis/upload/async` - 비동기 문서 분석
- **GET** `/api/analysis/health` - 서비스 상태 확인

### 요청 파라미터
- `file`: 분석할 문서 파일 (MultipartFile)
- `analysisType`: 분석 타입 (기본값: "FULL")
- `language`: OCR 언어 설정 (기본값: "kor+eng")

## 설정

### 환경변수
```bash
export OPENAI_API_KEY=your_openai_api_key
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
```

### 주요 설정 (application.yml)
```yaml
smarteye:
  upload:
    max-file-size: 50MB
  ocr:
    tesseract:
      language: kor+eng
  api:
    openai:
      model: gpt-4-vision-preview
```

## 모듈 구조

### LAM (Layout Analysis Module)
- DocLayout-YOLO 기반 레이아웃 분석
- Python 스크립트와의 브릿지 연동

### TSPM (Text & Semantic Processing Module)
- Tesseract OCR 텍스트 추출
- OpenAI Vision API 의미 분석

### CIM (Content Integration Module)
- 분석 결과 통합 및 최종 출력

## 개발 가이드

### 의존성 추가
```gradle
implementation 'new-dependency:version'
```

### 새로운 서비스 추가
1. `service` 패키지에 서비스 클래스 생성
2. `@Service` 어노테이션 추가
3. 필요한 의존성 주입

### API 엔드포인트 추가
1. `controller` 패키지에 컨트롤러 생성
2. `@RestController` 어노테이션 추가
3. 적절한 매핑 어노테이션 사용

## 모니터링
- H2 Console: http://localhost:8080/h2-console (개발 환경)
- Health Check: http://localhost:8080/api/analysis/health
- Actuator Endpoints: http://localhost:8080/actuator

## 라이센스
[라이센스 정보]
