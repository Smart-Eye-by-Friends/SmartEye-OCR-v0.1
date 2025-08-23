# SmartEye 백엔드 시스템 가이드

> 이 문서는 SmartEye 백엔드 시스템의 아키텍처, 설정, 실행 및 테스트 방법을 안내하는 기술 문서입니다. 새로운 팀원이 프로젝트를 이해하고 개발에 참여하는 것을 돕는 것을 목표로 합니다.

---

## 1. 시스템 개요

SmartEye 시스템은 문서 이미지의 내용을 분석하고 이해하기 위한 AI 기반 백엔드입니다. 최신 기술 스택을 활용하여 **마이크로서비스 아키텍처(MSA)**로 설계되었습니다.

- **주요 기능**: 문서 레이아웃 분석, 텍스트 추출(OCR), 의미적 이해 및 콘텐츠 통합
- **핵심 아키텍처**:
    1.  **SmartEye Backend (Java/Spring Boot)**: 핵심 비즈니스 로직, API 엔드포인트, 외부 서비스 연동을 담당하는 메인 서버입니다.
    2.  **LAM Microservice (Python/FastAPI)**: `DocLayout-YOLO` 모델을 사용하여 문서의 레이아웃 분석을 전문적으로 처리하는 독립적인 AI 마이크로서비스입니다.

이 시스템은 Docker를 통해 컨테이너 환경에서 실행되도록 설계되었으며, `system-manager.sh` 스크립트를 통해 전체 시스템의 생명주기를 손쉽게 관리할 수 있습니다.

---

## 2. 기술 스택

### 💻 Backend (SmartEye Backend)
- **언어**: Java 17
- **프레임워크**: Spring Boot 3.1.5
- **빌드 도구**: Gradle 8.3
- **주요 라이브러리**:
    - `Spring Data JPA`: 데이터베이스 연동
    - `Spring Web` & `WebFlux`: REST API 및 비동기 HTTP 통신
    - `Spring Cache`: Redis 기반 캐싱
    - `Tess4J`: Tesseract OCR 엔진 연동
    - `OpenAI GPT-3 Java`: OpenAI Vision API 연동
    - `TwelveMonkeys ImageIO`: 이미지 처리

### 🧠 AI/ML (LAM Microservice)
- **언어**: Python 3.9+
- **프레임워크**: FastAPI, Uvicorn (ASGI 서버)
- **핵심 모델**: `DocLayout-YOLO`
- **주요 라이브러리**:
    - `PyTorch`: 딥러닝 프레임워크
    - `HuggingFace Hub`: AI 모델 로딩
    - `OpenCV`: 이미지 전처리
    - `Loguru`: 로깅

### 📦 인프라 (Infrastructure)
- **데이터베이스**: PostgreSQL (프로덕션), H2 (개발)
- **캐시**: Redis
- **컨테이너**: Docker, Docker Compose
- **웹 서버**: Nginx (리버스 프록시)

---

## 3. 프로젝트 구조

```
/
├── build.gradle                # Java 백엔드 빌드 스크립트
├── docker-compose.yml          # 전체 서비스 오케스트레이션
├── Dockerfile                  # Java 백엔드 Docker 이미지 빌드 파일
├── scripts/                    # 📜 시스템 관리 스크립트
│   ├── system-manager.sh       # (중요) 시스템 관리 마스터 스크립트
│   ├── run.sh                  # 실행 헬퍼 스크립트
│   └── setup-env.sh            # 환경 설정 헬퍼 스크립트
├── smarteye-lam-service/       # 🧠 Python LAM 마이크로서비스
│   ├── Dockerfile.optimized    # 최적화된 LAM 서비스 Docker 이미지 빌드 파일
│   ├── README.md               # LAM 서비스 상세 가이드
│   └── app/                    # FastAPI 소스 코드
│       └── main.py             # API 엔드포인트 정의
└── src/                        # ☕ Java 백엔드 소스 코드
    ├── main/
    │   ├── java/com/smarteye/  # 메인 소스
    │   │   ├── service/        # 비즈니스 로직
    │   │   │   └── DocumentAnalysisService.java # 분석 파이프라인 총괄 서비스
    │   │   └── client/         # 외부 서비스 클라이언트
    │   │       └── LAMServiceClient.java # LAM 마이크로서비스 통신 클라이언트
    │   └── resources/
    │       ├── application.yml # 공통 설정
    │       ├── application-prod.yml # 프로덕션 환경 설정
    │       └── schema.sql      # DB 스키마 정의
    └── test/
```

---

## 4. 환경 설정

### 사전 요구사항
- `Java 17` (JDK)
- `Docker` & `Docker Compose`
- `Git`

### 단계별 설정 가이드

1.  **프로젝트 클론**
    ```bash
    git clone <repository_url>
    cd SmartEye_v0.1
    ```

2.  **환경 변수 설정**
    - 프로젝트 루트에서 `.env.example` 파일을 복사하여 `.env` 파일을 생성합니다.
    - 또는, 아래 스크립트를 실행하여 자동으로 생성합니다. 이 스크립트는 개발에 필요한 기본 환경 변수 파일을 생성해줍니다.

    ```bash
    ./scripts/setup-env.sh
    ```

3.  **`.env` 파일 수정**
    - 생성된 `.env` 파일을 열고, `OPENAI_API_KEY` 등 필요한 값을 실제 키로 교체합니다.
    - 다른 값들은 대부분 기본값으로 사용 가능합니다.

    ```dotenv
    # .env
    OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
    DB_NAME=smarteye
    DB_USERNAME=smarteye
    DB_PASSWORD=password
    # ... 기타 설정
    ```

4.  **LAM 서비스 모델 준비 (선택사항)**
    - LAM 서비스는 첫 실행 시 필요한 AI 모델을 자동으로 다운로드합니다.
    - 만약 수동으로 모델을 미리 다운로드하고 싶다면 `smarteye-lam-service/preload_models.py` 스크립트를 사용할 수 있습니다.

---

## 5. 시스템 실행 방법

**핵심 실행 스크립트**: `./scripts/system-manager.sh`

이 스크립트는 전체 시스템의 생명주기를 관리하는 중앙 제어 스크립트입니다.

### 🔹 개발 모드 (Recommended for Backend Development)
- **Java 백엔드**: 로컬에서 Gradle을 통해 직접 실행 (Hot Reload 지원)
- **나머지 서비스 (LAM, DB, Redis)**: Docker 컨테이너로 실행

```bash
# 개발 모드로 시스템 시작
./scripts/system-manager.sh start dev
```

### 🔹 Docker 모드 (Recommended for Frontend/Full-stack Development)
- **모든 서비스**: Docker 컨테이너로 실행
- 실제 운영 환경과 가장 유사한 환경입니다.

```bash
# Docker 모드로 시스템 시작
./scripts/system-manager.sh start docker
```

### 기타 유용한 명령어

```bash
# 시스템 상태 확인
./scripts/system-manager.sh status

# 시스템 중지
./scripts/system-manager.sh stop

# 시스템 재시작 (개발 모드)
./scripts/system-manager.sh restart dev

# 시스템 로그 확인 (전체 서비스)
./scripts/system-manager.sh logs

# LAM 서비스 로그만 확인
./scripts/system-manager.sh logs lam

# 사용 가능한 모든 명령어 확인
./scripts/system-manager.sh help
```

---

## 6. 시스템 테스트 방법

### 1. Java 백엔드 단위/통합 테스트
- 프로젝트 루트에서 아래 Gradle 명령어를 실행하여 Java 백엔드의 모든 테스트를 수행합니다.

```bash
./gradlew test
```

### 2. 서비스 헬스 체크
- 시스템이 실행 중일 때, `system-manager.sh`를 통해 각 서비스의 상태를 확인할 수 있습니다.

```bash
# 전체 시스템 헬스 체크
./scripts/system-manager.sh health
```
- 위 명령어는 아래 작업을 자동으로 수행합니다.
    - **Java Backend Health**: `http://localhost:8080/actuator/health`
    - **LAM Service Health**: `http://localhost:8081/health`

### 3. API 직접 호출 (cURL 예시)
- 파일 업로드 및 분석 API를 직접 테스트할 수 있습니다.

```bash
# 'sample.png' 파일을 업로드하여 통합 분석 실행
curl -X POST -F "file=@/path/to/sample.png" http://localhost:8080/api/v1/analysis/integrated
```

---

## 7. 핵심 아키텍처 및 모듈 설명

### 7.1. 전체 시스템 아키텍처
SmartEye는 요청을 받아 여러 모듈을 순차적 또는 병렬적으로 실행하여 최종 분석 결과를 생성합니다.

`Client -> Nginx -> SmartEye Backend -> (LAM Service, TSPM, CIM) -> DB`

1.  **요청 접수**: 클라이언트가 문서를 업로드하면 `SmartEye Backend`가 요청을 받습니다.
2.  **분석 오케스트레이션**: `DocumentAnalysisService`가 분석 파이프라인을 시작합니다.
3.  **레이아웃 분석**: `LAMServiceClient`를 통해 `LAM Microservice`에 HTTP 요청을 보내 레이아웃 정보를 받아옵니다.
4.  **텍스트/의미 분석**: `JavaTSPMService`가 OCR(Tess4J) 및 OpenAI Vision API를 통해 텍스트와 의미 정보를 추출합니다.
5.  **결과 통합**: `CIMService`가 LAM과 TSPM의 결과를 종합하여 최종 분석 결과를 생성하고 데이터베이스에 저장합니다.

### 7.2. SmartEye Backend (Java) 모듈

- **`controller`**: 외부 요청을 받는 API 엔드포인트를 정의합니다.
    - `IntegratedAnalysisController`: 메인 분석 요청을 처리합니다.
- **`service`**: 핵심 비즈니스 로직을 포함합니다.
    - `DocumentAnalysisService`: 전체 분석 흐름을 총괄하는 오케스트레이터 서비스입니다.
    - `LAMService`: `LAMServiceClient`를 사용하여 LAM 마이크로서비스와 통신하고 결과를 처리합니다.
    - `JavaTSPMService`: Tesseract, OpenAI API 연동 등 복잡한 TSPM 로직을 수행합니다.
    - `CIMService`: 분석된 데이터를 통합하고 DB에 저장합니다.
- **`client`**: 외부 서비스와 통신하는 클라이언트입니다.
    - `LAMServiceClient`: `WebClient`를 사용하여 비동기 방식으로 LAM 서비스와 통신합니다. 재시도 및 타임아웃 로직이 포함되어 있습니다.
- **`model/entity`**: JPA를 통해 데이터베이스 테이블과 매핑되는 객체들을 정의합니다.
- **`repository`**: `Spring Data JPA`를 사용하여 DB CRUD 연산을 수행합니다.

### 7.3. LAM Microservice (Python) 모듈

- **`app/main.py`**: FastAPI 애플리케이션의 메인 파일로, API 엔드포인트를 정의합니다.
    - `POST /analyze/layout`: 이미지 경로를 받아 레이아웃 분석을 수행하는 핵심 엔드포인트입니다.
    - `GET /health`: 서비스의 상태(모델 로딩 여부, GPU 사용 가능 여부 등)를 반환합니다.
- **`app/layout_analyzer.py`**: 실제 AI 모델을 로드하고 추론을 수행하는 핵심 로직이 담겨있습니다.
- **`app/config.py`**: 모델 경로, 신뢰도 임계값 등 서비스 설정을 관리합니다.

---

## 8. 주요 관리 스크립트 상세 설명

`scripts/` 디렉토리의 스크립트들은 복잡한 Docker 및 Gradle 명령어들을 추상화하여 개발 편의성을 높입니다.

- **`system-manager.sh`**:
    - `start [mode]`: `dev`, `docker`, `prod` 모드를 받아 `run.sh`를 실행합니다.
    - `stop`: 실행중인 모든 관련 프로세스(gradle)와 컨테이너(docker-compose)를 중지합니다.
    - `restart [mode]`: `stop` 후 `start`를 실행합니다.
    - `health`: 각 서비스의 헬스 체크 엔드포인트를 호출하여 상태를 보여줍니다.
    - `logs [service]`: `docker logs` 또는 로그 파일을 `tail`하여 실시간 로그를 보여줍니다.
    - `reset`: **(주의!)** 모든 Docker 볼륨, 이미지, 임시 파일, 로그를 삭제하여 시스템을 초기 상태로 되돌립니다.

- **`setup-env.sh`**:
    - `.env.example` 파일을 기반으로 `.env` 및 `.env.dev` 파일을 생성하여 초기 설정을 돕습니다.
