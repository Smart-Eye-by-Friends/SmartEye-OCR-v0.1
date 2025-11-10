# MySQL 통합 테스트 가이드

본 문서는 `Project/tests/backend_mysql/`에 추가된 실제 MySQL 기반 통합 테스트의 구성과 실행 방법을 정리한다. 기존 Mock DB 회귀 테스트(`Project/tests/backend/`)와 병행하여, 실제 스키마에 대한 정렬·포맷팅 파이프라인을 검증하는 용도로 사용한다.

## 1. 환경 준비
- `Backend/.env` 파일을 기준으로 환경 변수를 설정한다. (이미 `USE_ADAPTIVE_SORTER=true`, MySQL 접속 정보, OpenAI 키 등이 포함되어 있다.)
- 테스트 실행 호스트에서 다음 선행 조건을 만족해야 한다.
  - MySQL 서버 접근 가능 (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`).
  - `mysql+pymysql` 드라이버 설치되어 있을 것.
  - (선택) `OPENAI_API_KEY`가 설정되어 있으면 TextFormatter 이후 단계에서 AI 설명도 활용 가능.
- 테스트는 DB 스키마를 **TRUNCATE** 하므로, 개발/실험용 데이터베이스를 사용해야 한다.

## 2. 디렉터리 / 파일 구조
```
Project/tests/
 ├─ backend/                     # 기존 Mock DB 기반 통합 테스트
 ├─ backend_mysql/               # ✅ 실 MySQL 통합 테스트 (신규)
 │   ├─ __init__.py
 │   ├─ conftest.py              # 세션/스키마/정리 픽스처 및 truncate 헬퍼
 │   └─ test_real_db_pipeline.py # 메인 통합 테스트 모음
 └─ mysql_integration_tests.md   # (현재 문서)
```

## 3. 주요 픽스처 (`backend_mysql/conftest.py`)
- `ensure_schema`: 세션 시작 시 `Base.metadata.create_all()`로 스키마를 보장.
- `real_db_session`: 실제 MySQL 연결 세션을 제공.
- `clean_db`: 각 테스트 앞뒤로 `truncate_database()`를 호출하여 깨끗한 상태를 유지.

다음 테이블이 테스트마다 초기화된다.
```
question_elements, question_groups, text_versions,
layout_elements, pages, projects, document_types, users
```

## 4. 테스트 시나리오 (`test_real_db_pipeline.py`)

### 4.1 `test_sorter_results_are_persisted`
- `GLOBAL_FIRST`, `LOCAL_FIRST`, `HYBRID` 세 전략을 각각 강제 적용.
- 공통 유저/문서 유형/프로젝트/페이지를 생성한 뒤, 네 개의 `LayoutElement`를 삽입.
- `sort_layout_elements_adaptive()`로 정렬 → `_sync_layout_runtime_fields()` → `save_sorting_results_to_db()`.
- `question_groups`·`question_elements`·`text_versions`가 올바르게 생성됐는지 검증.
- `TextFormatter`로 OCR 결과(테스트용 문자열)까지 포맷팅하여 실제 DB의 `text_versions` 상태를 확인.

### 4.2 `test_visual_artifacts_saved`
- 동일한 MockElement 결과를 활용해 `Project/tests/backend/test_utils.py`의 `save_visual_artifacts()` 헬퍼가 제대로 작동하는지 확인.
- 실제 이미지(`tests/test_images/쎈 수학1-1_페이지_014.jpg`)를 로드하여 시각화/JSON/TXT 파일을 임시 디렉터리에 생성.

## 5. 실행 방법
1. MySQL 서버와 `.env` 환경 변수가 준비된 상태에서, 리포지터리 루트 또는 `Project/` 디렉터리에서 다음 명령을 실행한다.
   ```bash
   pytest Project/tests/backend_mysql/test_real_db_pipeline.py -m mysql_integration -s
   ```
2. 전략별 시나리오만 빠르게 확인하고 싶다면 `-k sorter_results` 옵션을 추가한다.
3. 테스트 중에는 데이터베이스가 반복적으로 `TRUNCATE` 되므로 다른 작업과 격리된 환경에서 실행한다.

## 6. 확장 아이디어
- **PDF 파이프라인 추가**: `test_integration_real_analysis_pdf.py`의 로직을 참고하여, `PDFProcessor`로 변환한 이미지를 순회하며 동일한 DB 저장 검증을 수행할 수 있다.
- **AI 설명 통합**: `OPENAI_API_KEY`가 유효하다면 `AnalysisService.call_openai_api()`를 호출하여 `ai_descriptions`까지 DB에 기록하는 테스트를 작성할 수 있다.
- **골든 검증 병행**: 실제 DB 테스트 결과를 별도 캐시에 저장해 회귀 비교를 수행하거나, UI 포맷팅 결과를 데이터베이스에서 직접 꺼내어 기존 골든 텍스트와 비교하는 추가 시나리오를 구축할 수 있다.

## 7. 주의 사항
- 테스트가 실패하더라도 `clean_db` 픽스처가 종료 시점에 다시 TRUNCATE를 수행하므로, DB가 오염된 채로 남지 않는다.
- 분석 모델(`AnalysisService`)을 직접 실행할 경우 GPU/CPU 자원이 많이 필요하므로, 현재 시나리오처럼 간단한 레이아웃 샘플을 직접 생성해 사용하는 방법을 권장한다.
- 로깅 수준을 조정하고 싶다면 `.env`의 `DEBUG`, `API_LOG_LEVEL` 값을 수정하거나, pytest 실행 시 `--log-cli-level` 옵션을 활용한다.

이 문서를 기반으로 실제 MySQL을 활용한 테스트 전략을 팀 내 표준에 맞게 발전시켜 나가길 권장한다.
