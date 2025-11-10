# **SmartEye Backend**

## **1. 개요**

**SmartEye** 프로젝트의 백엔드 시스템입니다. 이 시스템은 AI를 기반으로 아동 학습지를 분석하여 시각 장애 아동을 위한 접근성 높은 학습 자료를 생성하는 것을 목표로 합니다. 백엔드는 FastAPI를 사용하여 구현되었으며, 이미지 분석, 텍스트 추출, 레이아웃 정렬, 결과 저장 및 통합 다운로드 기능을 API 형태로 제공합니다.

본 README는 특히 **Phase 2 (단일 페이지 분석)** 와 **Phase 3 (다중 페이지 처리)** 기능 구현에 중점을 둡니다. 현재 개발 단계에서는 실제 데이터베이스 대신 **Mock DB**를 사용하여 핵심 로직을 구현하고 테스트합니다.

## **2. 주요 기능 (Phase 2 & 3)**

### **Phase 2: 단일 페이지 분석 파이프라인**

* **레이아웃 분석**: 이미지 내 텍스트 블록, 그림, 표 등의 위치와 종류 식별 (`analysis_service.py`)  
* **OCR**: 식별된 요소에서 텍스트 추출 (`analysis_service.py`)  
* **AI 설명**: 그림, 표 등에 대한 AI 기반 설명 생성 (선택 사항, `analysis_service.py`)  
* **레이아웃 정렬**: 문제지 구조(앵커-자식 관계) 또는 읽기 순서에 따라 요소 정렬 (`sorter.py`, v.LayoutDetect.2.4 알고리즘 적용)  
* **결과 저장**: 정렬된 결과를 v2.1 스키마 기준 Mock DB에 저장 (`db_saver.py`)  
  * `layout_elements` (탐지 결과, 불변)는 수정하지 않음  
  * `question_groups` (그룹 메타데이터) 및 `question_elements` (정렬 정보) 테이블에 저장  
* **포맷팅**: 정렬된 텍스트에 스타일(들여쓰기, 접두사 등) 적용 (`formatter.py`) 및 text_versions Mock DB에 저장

### **Phase 3: 다중 페이지 처리 및 통합**

* **프로젝트 관리**: 여러 페이지를 하나의 프로젝트 단위로 생성 및 관리 (`project_service.py`, `API`)  
* **배치 분석**: 프로젝트 내 모든 페이지를 순차적으로 자동 분석 (`batch_analysis.py`)  
  * 동기 (`/analyze`) 및 비동기 (`/analyze-async`) API 제공  
  * 비동기 작업 상태 폴링 API (`/status`) 제공  
* **통합 다운로드**: 프로젝트 내 모든 페이지의 최신 텍스트 버전을 하나의 .docx 파일로 통합하여 다운로드 (`download_service.py`, `API`)  
* **텍스트 수정 지원**: 사용자가 수정한 텍스트를 새 버전(user_edited)으로 저장하는 API 제공 (`routes.py`, `batch_analysis.py`)

## **3. 기술 스택**

* **웹 프레임워크**: FastAPI  
* **데이터 검증**: Pydantic (`schemas.py`)  
* **이미지 처리**: OpenCV, Pillow  
* **OCR**: Pytesseract (Tesseract OCR 래퍼)
* **AI 모델 (Mock)**: DocLayout-YOLO, GPT-4o-turbo 시뮬레이션  
* **문서 생성**: python-docx  
* **로깅**: Loguru  
* **데이터 저장**: Mock DB (Python Dict/List, `batch_analysis.py`에서 관리)  
* **개발 환경**: Python 3.9+

## **4. 아키텍처**

백엔드는 크게 **API 계층**과 **서비스 계층**으로 나뉩니다.

* **API 계층 (`app/api/routes.py`)**:  
  * FastAPI 라우터를 통해 HTTP 요청 처리  
  * Pydantic 스키마(`app/schemas.py`)를 이용한 요청/응답 데이터 검증  
  * 해당 서비스 함수 호출  
* **서비스 계층 (`app/services/`)**:  
  * **오케스트레이터 (`batch_analysis.py`)**: 6단계 분석 파이프라인 실행 조율 및 Mock DB 데이터/함수 제공  
  * **핵심 로직 서비스**:  
    * `project_service.py`: 프로젝트/페이지 CRUD  
    * `analysis_service.py`: 레이아웃, OCR, AI 설명  
    * `sorter.py`: 레이아웃 정렬  
    * `db_saver.py`: 정렬 결과 Mock DB 저장 (v2.1 스키마)  
    * `formatter.py`: 텍스트 포맷팅  
    * `download_service.py`: 통합 및 다운로드  
  * **데이터 구조 (`mock_models.py`)**: Pydantic 또는 Dataclass 기반 Mock 데이터 객체 정의

## **5. 디렉토리 구조**
```
backend/  
├── app/  
│   ├── api/  
│   │   └── routes.py         # FastAPI 엔드포인트 정의  
│   ├── services/  
│   │   ├── analysis_service.py # 1-3단계: 레이아웃, OCR, AI  
│   │   ├── sorter.py         # 4단계: 정렬  
│   │   ├── db_saver.py       # 5단계: DB 저장 (v2.1)  
│   │   ├── formatter.py      # 6단계: 포맷팅  
│   │   ├── batch_analysis.py # 배치 처리 오케스트레이터, Mock DB 호스트  
│   │   ├── project_service.py  # Phase 3.1: 프로젝트/페이지 관리  
│   │   ├── download_service.py # Phase 3.3: 통합 다운로드  
│   │   └── mock_models.py      # Mock 데이터 클래스  
│   ├── schemas.py          # Pydantic 스키마  
│   └── config.py           # 환경 설정 로딩  
├── migrations/             # DB 마이그레이션 스크립트 (Mock, MySQL v3->v2.1)  
├── uploads/                # 업로드된 이미지 저장 위치 (테스트용)  
├── .env                    # 실제 환경 변수 (API 키 등)  
└── .env.example            # 환경 변수 템플릿
```

## **6. 설치 및 실행**

### **1. 가상 환경 설정**
```bash
python3 -m venv .venv  
source .venv/bin/activate
```
### **2. 의존성 설치**

```bash
pip install -r requirements.txt
pip install -r requirements.txt
```
**참고**: `requirements.txt`에 명시된 doclayout-yolo는 별도 설치가 필요할 수 있습니다.

### **3. 환경 변수 설정**

`.env.example` 파일을 `.env`로 복사하고 필요한 값(예: OPENAI_API_KEY)을 채웁니다.

### **4. 개발 서버 실행**

# backend/ 디렉토리에서 실행  
```bash
python -m uvicorn app.api.routes:app --reload --port 8000
```
서버 시작 시 테스트용 Mock 프로젝트와 페이지가 자동으로 생성됩니다 (`routes.py`의 `startup_event`).

### **5. API 문서 확인**

서버 실행 후 브라우저에서 아래 주소로 접속합니다.

* **Swagger UI**: http://localhost:8000/docs  
* **ReDoc**: http://localhost:8000/redoc

## **7. 주요 API 엔드포인트**

### **프로젝트 관리 (Phase 3.1)**

* `POST /api/projects`: 새 프로젝트 생성  
* `GET /api/projects`: 모든 프로젝트 목록 조회  
* `GET /api/projects/{project_id}`: 특정 프로젝트 상세 조회

### **페이지 관리 (Phase 3.1)**

* `POST /api/pages/upload`: 프로젝트에 이미지 페이지 추가  
* `GET /api/projects/{project_id}/pages`: 특정 프로젝트의 페이지 목록 조회  
* `GET /api/pages/{page_id}`: 특정 페이지 상세 조회  
* `GET /api/pages/{page_id}/text`: 페이지의 현재 텍스트 버전 조회 (에디터 로딩용)  
* `POST /api/pages/{page_id}/text`: 사용자 수정 텍스트 저장

### **분석 (Phase 3.2)**

* `POST /api/projects/{project_id}/analyze`: 동기 배치 분석 실행  
* `POST /api/projects/{project_id}/analyze-async`: 비동기 배치 분석 실행 요청  
* `GET /api/projects/{project_id}/status`: 비동기 분석 상태 폴링

### **다운로드 (Phase 3.3)**

* `GET /api/projects/{project_id}/combined-text`: 통합 텍스트 (JSON) 조회  
* `POST /api/projects/{project_id}/download`: 통합 Word (.docx) 파일 다운로드

## **8. 데이터 흐름: 6단계 배치 분석 파이프라인**

`POST /api/projects/{id}/analyze` 또는 `analyze-async` 호출 시 `batch_analysis.py`의 `analyze_project_batch` 함수가 실행되며, 각 페이지에 대해 다음 단계를 수행합니다:

1. **레이아웃 분석**: Mock YOLO 함수 (`_mock_layout_detection`) 호출 → `List[MockElement]` 반환  
2. **OCR 처리**: Mock OCR 함수 (`_mock_ocr_processing`) 호출 → `List[MockTextContent]` 반환  
3. **AI 설명**: Mock AI 함수 (`_mock_ai_description_generation`) 호출 → `Dict[int, str]` 반환 (figure, table, flowchart 대상)  
4. **정렬**: `sorter.sort_layout_elements()` 호출 → `List[MockElement]` 반환 (정렬 속성 추가됨)  
5. **DB 저장**: `db_saver.save_sorted_elements_to_mock_db()` 호출 → `mock_question_groups`, `mock_question_elements` Mock DB에 저장 (v2.1 스키마)  
6. **포맷팅**: `formatter.format_page()` 호출 → 포맷팅된 텍스트 반환 → `_save_text_version()` 호출하여 `mock_text_versions`에 저장

## **9. 데이터베이스 스키마 (v2.1 기준)**

백엔드는 공식 v2.1 E-R 다이어그램 스키마를 기준으로 데이터를 처리하고 저장합니다.

**핵심 원칙**:

* **단일 책임 원칙(SRP)**: 테이블 역할 분리  
  * `layout_elements`: YOLO 탐지 결과 (불변)  
  * `question_groups`: 그룹 메타데이터 (가변)  
  * `question_elements`: 정렬 정보 (가변)  
* **정규화**: 데이터 중복 최소화 (`anchor_element_id` 사용)  
* **확장성**: 다양한 앵커 타입 및 고아 그룹 지원