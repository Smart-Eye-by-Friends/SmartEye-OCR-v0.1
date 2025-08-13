# SmartEye Backend API 사용 예시

## 📋 개요

이 문서는 SmartEye Backend API의 주요 기능들을 실제로 사용하는 방법을 예시와 함께 설명합니다.

---

## 🔐 인증

### JWT 토큰 발급
```bash
curl -X POST http://localhost:8000/api/v1/auth/jwt/create/ \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin"
  }'
```

**응답 예시:**
```json
{
  "access": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
  "refresh": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

---

## 📁 파일 업로드 및 분석

### 1. 개선된 이미지 업로드 및 분석 시작
```bash
curl -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "files=@test_document.pdf" \
  -F "files=@test_image.jpg" \
  -F "job_name=한국어 교육 문서 분석" \
  -F "enable_ocr=true" \
  -F "enable_description=true" \
  -F "model_choice=yolo11n-doclay"
```

**새로 추가된 파일 검증 기능:**
- **지원 형식**: `.jpg`, `.jpeg`, `.png`, `.pdf`, `.bmp`, `.tiff`
- **최대 파일 크기**: 50MB
- **자동 에러 처리**: 형식/크기 초과 시 명확한 오류 메시지

**응답 예시:**
```json
{
  "job_id": 1,
  "task_id": "abc123-def456-ghi789",
  "status": "processing",
  "message": "SmartEye 완전 분석이 시작되었습니다.",
  "total_images": 5,
  "processing_options": {
    "model_choice": "yolo11n-doclay",
    "enable_ocr": true,
    "enable_description": true,
    "visualization_type": "comparison"
  }
}
```

### 2. 분석 진행 상태 확인
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8000/api/v1/analysis/jobs/1/progress/
```

**응답 예시:**
```json
{
  "job_id": 1,
  "job_name": "한국어 교육 문서 분석",
  "status": "processing",
  "progress": 60.0,
  "processed_images": 3,
  "total_images": 5,
  "failed_images": 0,
  "started_at": "2025-08-13T12:00:00Z",
  "estimated_completion": "2025-08-13T12:05:00Z"
}
```

### 3. 분석 결과 조회
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8000/api/v1/analysis/jobs/1/results/
```

---

## 📖 페이지별 JSON 병합 기능 (신규)

### 사용자별 분석 결과를 책 단위로 병합

이 기능은 여러 개의 분석 작업 결과를 하나의 책으로 통합하여 체계적으로 관리할 수 있게 해줍니다.

```bash
curl -X POST http://localhost:8000/api/v1/analysis/images/merge_book_results/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "book_name": "초등학교 국어 교과서 1학년",
    "job_ids": [1, 2, 3, 4, 5],
    "save_to_file": true
  }'
```

**파라미터 설명:**
- `book_name`: 병합할 책의 이름
- `job_ids`: 병합할 작업 ID 목록 (비어있으면 모든 작업 병합)
- `save_to_file`: JSON 파일로 저장 여부 (선택사항)

**응답 예시:**
```json
{
  "success": true,
  "message": "5개 페이지가 성공적으로 병합되었습니다.",
  "merged_book": {
    "book_info": {
      "book_name": "초등학교 국어 교과서 1학년",
      "user_id": 1,
      "total_pages": 5,
      "created_at": "2025-08-13T12:00:00Z",
      "merged_at": "2025-08-13T12:30:00Z",
      "analysis_summary": {
        "total_jobs": 5,
        "total_processing_time": 125.5,
        "average_confidence": 0.892,
        "total_detections": 47
      }
    },
    "pages": [
      {
        "page_number": 1,
        "job_id": 1,
        "job_name": "표지 분석",
        "processing_time": 25.2,
        "confidence_score": 0.95,
        "detection_count": 12,
        "content": {
          "text_content": "초등학교 국어 1학년 교과서",
          "braille_content": "⠃⠮⠎⠮⠍⠮⠋⠮...",
          "layout_analysis": {...},
          "ocr_results": {...},
          "image_descriptions": {...},
          "integrated_content": {...}
        },
        "file_info": {
          "pdf_path": "/media/results/page1.pdf",
          "json_path": "/media/results/page1.json",
          "xml_path": "/media/results/page1.xml"
        }
      }
      // ... 나머지 페이지들
    ],
    "content_summary": {
      "text_blocks": [
        {
          "page": 1,
          "content": "초등학교 국어 1학년 교과서..."
        }
      ],
      "images": [],
      "tables": [],
      "other_elements": []
    }
  },
  "file_path": "/media/merged_results/merged_book_1_초등학교_국어_교과서_1학년_20250813_123000.json",
  "statistics": {
    "total_pages": 5,
    "total_jobs": 5,
    "total_processing_time": 125.5,
    "average_confidence": 0.892,
    "total_detections": 47
  }
}
```

---

## 🔍 고급 기능들

### 1. 특정 이미지의 레이아웃 감지 결과 조회
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8000/api/v1/analysis/images/1/detections/
```

### 2. 개별 모듈 분석 실행
```bash
curl -X POST http://localhost:8000/api/v1/analysis/jobs/individual_analysis/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "job_id": 1,
    "analysis_type": "lam",
    "model_choice": "yolo11n-doclay"
  }'
```

### 3. 분석 작업 취소
```bash
curl -X POST http://localhost:8000/api/v1/analysis/jobs/1/cancel/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. 사용 가능한 모델 목록 조회
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8000/api/v1/analysis/jobs/models/
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 단일 이미지 분석
```bash
# 1. 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:8000/api/v1/auth/jwt/create/ \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}' | \
  jq -r '.access')

# 2. 이미지 업로드 및 분석
JOB_RESPONSE=$(curl -s -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@test_image.jpg" \
  -F "job_name=테스트 이미지 분석")

JOB_ID=$(echo $JOB_RESPONSE | jq -r '.job_id')

# 3. 진행 상태 확인
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8000/api/v1/analysis/jobs/$JOB_ID/progress/

# 4. 결과 조회
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8000/api/v1/analysis/jobs/$JOB_ID/results/
```

### 시나리오 2: 다중 페이지 PDF 분석 및 병합
```bash
# 1. PDF 업로드 및 분석
curl -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@교과서.pdf" \
  -F "job_name=교과서 전체 분석" \
  -F "enable_ocr=true" \
  -F "enable_description=true"

# 2. 분석 완료 후 페이지 병합
curl -X POST http://localhost:8000/api/v1/analysis/images/merge_book_results/ \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "book_name": "교과서 통합본",
    "save_to_file": true
  }'
```

---

## ⚠️ 에러 처리

### 일반적인 에러 응답들

#### 1. 파일 형식 오류
```json
{
  "error": "지원하지 않는 파일 형식입니다: test.txt. 지원 형식: .jpg, .jpeg, .png, .pdf, .bmp, .tiff"
}
```

#### 2. 파일 크기 초과
```json
{
  "error": "파일 크기가 너무 큽니다: large_file.pdf (75.5MB). 최대 50MB까지 지원합니다."
}
```

#### 3. 권한 오류
```json
{
  "error": "다른 사용자의 결과는 병합할 수 없습니다."
}
```

#### 4. 병합할 데이터 없음
```json
{
  "error": "병합할 분석 결과가 없습니다."
}
```

---

## 📊 API 응답 코드

| 코드 | 의미 | 설명 |
|------|------|------|
| 200 | OK | 성공적인 조회 |
| 201 | Created | 새로운 분석 작업 생성됨 |
| 202 | Accepted | 분석 작업이 비동기적으로 시작됨 |
| 400 | Bad Request | 잘못된 요청 파라미터 |
| 401 | Unauthorized | 인증 토큰 필요 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 500 | Internal Server Error | 서버 내부 오류 |

---

## 🔗 추가 정보

### API 문서
- **Swagger UI**: http://localhost:8000/api/docs/
- **ReDoc**: http://localhost:8000/api/redoc/

### 모니터링
- **Flower (Celery)**: http://localhost:5555/
- **Django Admin**: http://localhost:8000/admin/

---

*마지막 업데이트: 2025-08-13*  
*작성자: SmartEye 개발팀*