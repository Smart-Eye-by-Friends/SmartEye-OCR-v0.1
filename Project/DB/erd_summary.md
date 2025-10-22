# SmartEyeSsen Database Schema Summary

## 📊 Overview

- **총 테이블 수:** 12개
- **핵심 관계:** 11개
- **데이터베이스 엔진:** MySQL 8.0+
- **문자셋:** utf8mb4
- **최종 수정일:** 2025-01-XX

---

## 📋 Table List

| 번호 | 테이블명              | Primary Key       | Foreign Keys                  | 주요 속성                                 | 설명             |
| ---- | --------------------- | ----------------- | ----------------------------- | ----------------------------------------- | ---------------- |
| 1    | **users**             | user_id           | -                             | email(UK), name, role, api_key            | 사용자 계정      |
| 2    | **document_types**    | doc_type_id       | -                             | type_name(UK), model_name, sorting_method | 문서 타입 정의   |
| 3    | **projects**          | project_id        | user_id, doc_type_id          | project_name, total_pages, status         | 프로젝트/세션    |
| 4    | **pages**             | page_id           | project_id                    | page_number, image_path, analysis_status  | 페이지 정보      |
| 5    | **layout_elements**   | element_id        | page_id                       | class*name, bbox*\*, confidence           | 레이아웃 요소    |
| 6    | **text_contents**     | text_id           | element_id(UK)                | ocr_text, ocr_confidence                  | OCR 결과         |
| 7    | **ai_descriptions**   | ai_desc_id        | element_id(UK)                | description, ai_model                     | AI 설명          |
| 8    | **question_groups**   | question_group_id | page_id                       | question_number, start_y, end_y           | 문제 그룹        |
| 9    | **question_elements** | qe_id             | question_group_id, element_id | order_in_question                         | 문제-요소 매핑   |
| 10   | **text_versions**     | version_id        | page_id, user_id              | content, version_type, is_current         | 텍스트 버전 관리 |
| 11   | **formatting_rules**  | rule_id           | doc_type_id                   | class_name, prefix, suffix, indent_level  | 포맷팅 규칙      |
| 12   | **combined_results**  | combined_id       | project_id(UK)                | combined_text, combined_stats             | 통합 문서 캐시   |

---

## 🔗 Key Relationships

### 1:N Relationships

```
users → projects (한 사용자 → 여러 프로젝트)
document_types → projects (한 타입 → 여러 프로젝트)
projects → pages (한 프로젝트 → 여러 페이지)
pages → layout_elements (한 페이지 → 여러 요소)
pages → question_groups (한 페이지 → 여러 문제)
pages → text_versions (한 페이지 → 여러 버전)
question_groups → question_elements (한 문제 → 여러 요소)
document_types → formatting_rules (한 타입 → 여러 규칙)
```

### 1:1 Relationships

```
layout_elements → text_contents (한 요소 → 하나의 OCR 결과)
layout_elements → ai_descriptions (한 요소 → 하나의 AI 설명)
projects → combined_results (한 프로젝트 → 하나의 통합 결과)
```

---

## 🎯 Key Design Decisions

1. **문서 타입 구분:** `document_types` 테이블로 worksheet/document 구분
2. **버전 관리:** `text_versions`로 original/auto_formatted/user_edited 관리
3. **문제 구조:** `question_groups` + `question_elements`로 N:N 해결
4. **OCR/AI 분리:** `text_contents`, `ai_descriptions` 별도 테이블 (정규화)
5. **캐싱:** `combined_results`로 통합 문서 캐시 (성능 최적화)

---

## 📌 Important Constraints

- **UNIQUE:** email, type_name, (project_id, page_number), (element_id), (page_id, question_number)
- **ON DELETE CASCADE:** 대부분의 FK (상위 삭제 시 하위도 삭제)
- **ON DELETE RESTRICT:** document_types (사용 중이면 삭제 불가)
- **GENERATED COLUMNS:** area, y_position, x_position (자동 계산)
- **TRIGGERS:** trg_update_total_pages (페이지 추가 시 total_pages 자동 증가)

---

## 🔍 Quick Reference

### 주요 쿼리 패턴

**1. 프로젝트 생성**

```sql
INSERT INTO projects (user_id, doc_type_id, project_name)
VALUES (1, 1, '수학 문제지 분석');
```

**2. 페이지 분석 결과 저장**

```sql
-- layout_elements 삽입
INSERT INTO layout_elements (...) VALUES (...);
-- text_contents 삽입
INSERT INTO text_contents (element_id, ocr_text) VALUES (LAST_INSERT_ID(), '...text...');
```

**3. 최신 텍스트 버전 조회**

```sql
SELECT content
FROM text_versions
WHERE page_id = 1 AND is_current = TRUE;
```

**4. 통합 문서 생성**

```sql
SELECT tv.content
FROM pages p
JOIN text_versions tv ON p.page_id = tv.page_id
WHERE p.project_id = 1 AND tv.is_current = TRUE
ORDER BY p.page_number;
```
