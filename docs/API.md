# SmartEye OCR - API Documentation

## 📊 API 엔드포인트 명세

### Base URL
- **Development**: `http://localhost:8080`
- **Production**: `https://api.smarteye-ocr.com` (배포 후 설정)

### 공통 헤더
```http
Content-Type: multipart/form-data (파일 업로드 시)
Content-Type: application/json (JSON 요청 시)
Accept: application/json
```

---

## 🔍 1. 기본 분석 API

### POST `/api/analyze`

일반적인 OCR 및 레이아웃 분석을 수행합니다.

#### Request
```http
POST /api/analyze
Content-Type: multipart/form-data

Form Data:
- image: File (required) - 분석할 이미지 파일 (JPG, PNG, GIF)
- modelChoice: String (required) - AI 모델 선택
  - "SmartEyeSsen" (권장 - 한국어 학습지 최적화)
  - "DocStructBench" (일반 문서 구조)
  - "DocLayNet-DocSynth" (복잡한 레이아웃)
  - "DocSynth300K" (대용량 학습 데이터)
- apiKey: String (optional) - OpenAI API 키 (AI 분석용)
```

#### Response
```json
{
  "success": true,
  "layout_image_url": "/static/layout_viz_1234567890.png",
  "json_url": "/static/analysis_result_20240904_153000.json",
  "stats": {
    "total_elements": 25,
    "text_elements": 18,
    "image_elements": 3,
    "table_elements": 2,
    "question_numbers": 2,
    "processing_time": 3.45
  },
  "ocr_results": [
    {
      "text": "문제 1. 다음 중 옳은 것은?",
      "coordinates": [100, 150, 400, 180],
      "class_name": "question_text",
      "confidence": 0.95
    }
  ],
  "ai_results": [
    {
      "class_name": "figure",
      "coordinates": [450, 200, 650, 350],
      "description": "그래프가 포함된 이미지...",
      "confidence": 0.88
    }
  ],
  "formatted_text": "문제 1. 다음 중 옳은 것은?\n(1) 선택지 1\n(2) 선택지 2..."
}
```

#### Error Response
```json
{
  "success": false,
  "error": "파일 크기가 10MB를 초과합니다.",
  "error_code": "FILE_SIZE_EXCEEDED"
}
```

---

## 📋 2. 구조화된 분석 API

### POST `/api/analyze-structured`

문제별로 정렬된 상세 구조화 분석을 수행합니다.

#### Request
기본 분석 API와 동일

#### Response
```json
{
  "success": true,
  "layout_image_url": "/static/layout_viz_1234567890.png",
  "json_url": "/static/structured_analysis_20240904_153000.json",
  "stats": {
    "total_elements": 25,
    "total_questions": 5,
    "sections": ["A", "B"],
    "processing_time": 4.12
  },
  "ocr_results": [...],
  "ai_results": [...],
  "formatted_text": "...",
  "structured_result": {
    "document_info": {
      "total_questions": 5,
      "layout_type": "sectioned",
      "sections": {
        "A": {
          "name": "A",
          "bbox": [100, 200, 800, 1000],
          "y_position": 200
        }
      }
    },
    "questions": [
      {
        "question_number": "1",
        "section": "A",
        "question_content": {
          "main_question": "다음 중 옳은 것은?",
          "passage": "지문 내용...",
          "choices": [
            {
              "choice_number": "1",
              "choice_text": "(1) 선택지 1",
              "bbox": [100, 300, 400, 330]
            }
          ],
          "images": [
            {
              "bbox": [450, 200, 650, 350],
              "description": "AI가 분석한 이미지 설명",
              "confidence": 0.88
            }
          ],
          "tables": [],
          "explanations": "해설 내용..."
        },
        "ai_analysis": {
          "image_descriptions": [...],
          "table_analysis": [...],
          "problem_analysis": [...]
        }
      }
    ]
  }
}
```

---

## 💾 3. 워드 문서 저장 API

### POST `/api/save-as-word`

분석 결과를 워드 문서로 저장합니다.

#### Request
```json
{
  "content": "분석 결과 텍스트 내용...",
  "filename": "smarteye_analysis_20240904",
  "format": "structured" // optional: "simple" | "structured"
}
```

#### Response
```json
{
  "success": true,
  "download_url": "/static/smarteye_analysis_20240904.docx",
  "file_size": 245760,
  "created_at": "2024-09-04T15:30:00Z"
}
```

---

## ❤️ 4. 헬스 체크 API

### GET `/api/health`

서버 상태를 확인합니다.

#### Response
```json
{
  "status": "UP",
  "timestamp": "2024-09-04T15:30:00Z",
  "version": "1.0.0",
  "uptime": "2 days, 3 hours, 45 minutes"
}
```

---

## 🚨 에러 코드

| 코드 | 설명 | HTTP 상태 |
|------|------|-----------|
| `INVALID_FILE_FORMAT` | 지원하지 않는 파일 형식 | 400 |
| `FILE_SIZE_EXCEEDED` | 파일 크기 초과 (10MB) | 400 |
| `MISSING_REQUIRED_FIELD` | 필수 필드 누락 | 400 |
| `INVALID_MODEL_CHOICE` | 잘못된 모델 선택 | 400 |
| `OCR_PROCESSING_FAILED` | OCR 처리 실패 | 500 |
| `AI_ANALYSIS_FAILED` | AI 분석 실패 | 500 |
| `FILE_SAVE_FAILED` | 파일 저장 실패 | 500 |
| `INTERNAL_SERVER_ERROR` | 내부 서버 오류 | 500 |

---

## 📤 파일 업로드 제한

- **최대 파일 크기**: 10MB
- **지원 형식**: JPG, JPEG, PNG, GIF
- **동시 업로드**: 1개 파일
- **타임아웃**: 5분

---

## 🔗 CORS 설정

프론트엔드 연동을 위한 CORS 설정:

```yaml
allowed-origins:
  - http://localhost:3000    # React 개발 서버
  - https://smarteye-ocr.com # 프로덕션 도메인
allowed-methods:
  - GET
  - POST
  - PUT
  - DELETE
  - OPTIONS
allowed-headers: "*"
allow-credentials: true
```

---

## 🧪 테스트 예시

### cURL을 이용한 기본 분석 요청
```bash
curl -X POST http://localhost:8080/api/analyze \
  -F "image=@test_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  -F "apiKey=sk-..."
```

### JavaScript fetch를 이용한 요청
```javascript
const formData = new FormData();
formData.append('image', imageFile);
formData.append('modelChoice', 'SmartEyeSsen');
formData.append('apiKey', 'sk-...');

const response = await fetch('http://localhost:8080/api/analyze', {
  method: 'POST',
  body: formData
});

const result = await response.json();
```

---

**업데이트**: 2024년 9월 4일
**버전**: 1.0.0
