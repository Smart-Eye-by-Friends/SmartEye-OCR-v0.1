# SmartEyeSsen 백엔드 파이프라인 개요

## 1. 시스템 개요
- **프레임워크**: FastAPI 기반 비동기 REST API (`Backend/app/main.py`), CORS 및 예외 처리 포함.
- **데이터 계층**: SQLAlchemy ORM (`Backend/app/database.py`, `Backend/app/models.py`)와 Pydantic 스키마(`Backend/app/schemas.py`)로 스키마 일관성을 유지.
- **서비스 계층**: `Backend/app/services/` 모듈들이 분석, 정렬, 포맷팅, 다운로드 등 핵심 도메인 로직을 담당.
- **스토리지**: MySQL 8.0, 업로드 자산은 로컬 `uploads/` 디렉터리에 저장 후 경로만 DB에 유지.
- **비동기 처리**: FastAPI 백그라운드 작업과 내부 `asyncio` 루틴을 조합해 레이아웃 분석 및 OpenAI 호출을 병렬화.

## 2. 계층 구조
| 레이어 | 주요 모듈 | 책임 |
| --- | --- | --- |
| API 엔드포인트 | `routers/projects.py`, `routers/pages.py`, `routers/analysis.py`, `routers/downloads.py` | 요청 검증, 서비스 호출, 응답 직렬화 |
| 서비스 | `services/batch_analysis.py`, `services/analysis_service.py`, `services/sorter.py`, `services/formatter.py`, `services/text_version_service.py`, `services/download_service.py` | OCR·정렬 파이프라인, 포맷팅, 다운로드, 버전 관리 |
| 데이터 | `database.py`, `models.py`, `crud.py`, `schemas.py` | 세션 관리, ORM 모델 정의, CRUD 추상화 |
| 유틸/헬퍼 | `services/pdf_processor.py`, `services/formatter_utils.py`, `services/mock_models.py` | PDF 분해, 렌더링 규칙, 테스트용 래퍼 |

## 3. 주요 데이터 흐름
### 3.1 프로젝트 생성
1. 클라이언트가 `/api/projects` POST 호출 → `routers/projects.py`가 `schemas.ProjectCreate` 검증.
2. `crud.create_project()`가 DB에 프로젝트·기본 메타데이터 저장.
3. FastAPI 응답은 `schemas.ProjectResponse`로 직렬화.

### 3.2 페이지 업로드 (이미지/PDF)
1. `/api/pages/upload` 엔드포인트가 파일 수신, `UploadFile` 메타데이터를 활용.
2. PDF는 `services/pdf_processor.pdf_processor.convert_pdf_to_images()`를 통해 비동기 변환, 이미지 파일은 즉시 디스크에 기록.
3. 변환 결과를 `crud.create_page()`로 저장하면서 이미지 폭/높이 정보를 업데이트.
4. 초기 `analysis_status`는 `pending`으로 설정되며, 후속 배치 분석의 대상이 된다.

### 3.3 배치 분석 파이프라인
1. `/api/projects/{project_id}/analyze` 요청 → `routers/analysis.py`가 프로젝트 존재 여부 확인.
2. `services.batch_analysis.analyze_project_batch_async()` 호출:
   - 프로젝트와 페이지 ORM 객체를 selectinload로 로딩.
   - `pending`/`error` 상태 페이지를 순차 처리하며 상태를 갱신.
3. `_process_single_page_async()` 내부 단계:
   1. 이미지 로드 및 해상도 갱신 (`_load_page_image`).
   2. `AnalysisService.analyze_layout()` → DocLayout-YOLO 감지 결과를 `layout_elements` 테이블에 upsert 후 ORM 객체 반환.
   3. `AnalysisService.perform_ocr()` → Tesseract OCR 결과를 `text_contents`에 upsert.
   4. (옵션) `AnalysisService.call_openai_api_async()` → figure/table/flowchart 설명을 `ai_descriptions`에 upsert.
   5. `services.sorter.sort_layout_elements()` → MockElement 기반 정렬 수행, 그룹/순서를 계산.
   6. `_sync_layout_runtime_fields()`로 정렬 결과를 ORM 필드(order_in_question 등)에 반영.
   7. `save_sorting_results_to_db()`가 `question_groups`, `question_elements` 테이블을 채움.
   8. `TextFormatter.format_page()`가 정렬된 요소와 OCR 텍스트/AI 설명을 조합해 완성본 문자열 생성.
   9. `create_text_version()`이 `text_versions` 테이블에 자동 포맷 버전을 생성하고 최신 버전을 갱신.
   10. 페이지 상태(`analysis_status`)와 처리 시간 업데이트.
4. 프로젝트 레벨 상태(`status`)는 성공/부분 성공/실패에 따라 `completed`, `partial`, `error`로 마감.

### 3.4 단일 페이지 비동기 분석
- `/api/pages/{page_id}/analyze/async`가 호출되면 즉시 작업 ID를 발급하고 FastAPI `BackgroundTasks`로 `_run_async_page_analysis()`를 예약.
- 백그라운드 컨텍스트에서는 새로운 DB 세션을 열어 `_process_single_page_async()` 로직을 재사용하며 진행 상황을 `async_jobs` 인메모리 맵에 기록.
- `/api/analysis/jobs/{job_id}`로 상태/결과를 조회.

### 3.5 통합 다운로드
1. `/api/downloads/{project_id}/combined-text` 또는 `/docx`가 호출되면 `services.download_service`가 실행.
2. 각 페이지의 최신(`text_versions.is_current=True`) 텍스트를 모아서:
   - 텍스트 통합 후 `combined_results` 캐시에 upsert.
   - DOCX 요청 시 python-docx로 문서를 생성해 스트림을 반환.

## 4. 핵심 구성 요소와 책임
- **AnalysisService (`services/analysis_service.py`)**: 모델 로딩, 중복 감지 필터링, OCR, OpenAI 호출을 캡슐화. GPU/CPU 환경 차이에 대응하기 위해 HuggingFace Hub 캐시 및 pytesseract 설정 포함.
- **Sorter (`services/sorter.py`, `services/sorter_strategies.py`)**: 레이아웃 유형 감지, 재귀적 영역 분할, 앵커-자식 그룹핑, 전략 패턴 기반 선택(Adaptive 플래그).
- **Formatter (`services/formatter.py`)**: 정렬 결과+OCR 텍스트로 최종 문장을 조립. DB 규칙(`formatting_rules` 테이블)과 기본 규칙을 통합.
- **Text Version Service (`services/text_version_service.py`)**: 텍스트 버전 저장/조회, 사용자 편집본을 새로운 버전으로 적재.
- **Download Service (`services/download_service.py`)**: 텍스트 통합, Word 문서 생성, 캐시 유효성 판단.
- **PDF Processor (`services/pdf_processor.py`)**: Sync/Async 변환 파이프라인, OpenCV를 활용한 해상도 측정.

## 5. 데이터베이스 상호 작용
- **세션 관리**: `get_db()` (FastAPI Depends)로 요청 스코프 세션을 주입, 비동기 배치/백그라운드에서는 `SessionLocal()`을 직접 생성.
- **주요 테이블 관계**:
  - `projects` ↔ `pages` (1:N)
  - `pages` ↔ `layout_elements` / `text_contents` / `ai_descriptions` (1:N)
  - `pages` ↔ `question_groups` ↔ `question_elements` → 앵커/자식 구조를 정규화.
  - `pages` ↔ `text_versions` → 버전 히스토리 관리.
  - `projects` ↔ `combined_results` → 통합 문서 캐시.
- **CRUD 유틸 (`crud.py`)**은 비즈니스 로직에서 직접 ORM 세부 사항을 다루지 않도록 캡슐화.

## 6. 환경 변수 및 설정 포인트
- `.env` / 환경 변수:
  - DB 접속 (`DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_PORT`)
  - CORS 허용 목록 (`CORS_ORIGINS`)
  - API 서버 설정 (`API_HOST`, `API_PORT`, `API_RELOAD`)
  - OpenAI API 키 (`OPENAI_API_KEY`), 최대 동시 호출 수 (`OPENAI_MAX_CONCURRENCY`)
  - 정렬 전략 전환 플래그 (`USE_ADAPTIVE_SORTER`) 등
- 개발 환경에서는 `init_db()`가 테이블 자동 생성, 운영 환경에서는 Alembic 등 마이그레이션 도구 사용 권장.
- 이미지 업로드 경로는 `UPLOAD_DIR`로 오버라이드 가능.

## 7. 관찰 및 유지보수 팁
- **로그**: loguru를 전역 사용. 파이프라인 단계별 `info/trace` 로그를 통해 문제 구간을 빠르게 식별 가능.
- **테스트**: `Project/tests/backend` Pytest 스위트가 기본 회귀 테스트를 제공 (`pytest -m "not visual"` 권장).
- **확장**: 새로운 문서 타입 추가 시 `document_types` 레코드, 정렬 전략, 포맷 규칙을 함께 확장해야 일관성이 유지된다.
- **성능 고려**: 대량 PDF 변환이나 AI 설명 생성 시 동시성 제어(`asyncio.Semaphore`)와 캐시(CombinedResult, HuggingFace 모델 캐시)를 적극 활용.

