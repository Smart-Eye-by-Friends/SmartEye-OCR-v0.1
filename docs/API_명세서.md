# 📄 SmartEye API 명세서

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

## 🔍 1. 문서 분석 API

### `POST /api/document/analyze`

이미지 또는 PDF 파일의 레이아웃 분석과 OCR을 수행합니다.

#### 요청 (Request)
```http
POST /api/document/analyze
Content-Type: multipart/form-data

Form Data:
- image: File (required) - 분석할 이미지 파일 (JPG, PNG, PDF)
- modelChoice: String (required) - AI 모델 선택
  - "SmartEyeSsen" (권장 - 학습지 최적화)
  - "Tesseract" (OCR 전용)
  - "OpenAI" (GPT-4 Turbo 기반, API 키 필요)
- apiKey: String (optional) - OpenAI API 키
```

#### 성공 응답 (Success Response)
```json
{
  "success": true,
  "layoutImageUrl": "/static/layout_viz_1756723030.png",
  "jsonUrl": "/static/analysis_result_20250901_103711.json",
  "stats": {
    "totalLayoutElements": 33,
    "ocrTextBlocks": 21,
    "classCounts": { "figure": 5, "plain_text": 13, ... }
  },
  "ocrResults": [
    {
      "id": 0,
      "className": "plain_text",
      "coordinates": [1914, 576, 3093, 816],
      "text": "빨 간 색 구슬 4 개 와 파 란 색 구슬 2 개..."
    }
  ],
  "jobId": "d588945a-459d-42e6-84c7-9b635cf2b8c7",
  "message": "분석이 성공적으로 완료되었습니다."
}
```

#### 실패 응답 (Error Response)
```json
{
  "success": false,
  "message": "에러 메시지",
  "error": "FILE_SIZE_EXCEEDED"
}
```

---

## ❤️ 2. 헬스 체크 API

### `GET /api/health`

서버의 현재 상태를 확인합니다.

#### 응답 (Response)
```json
{
    "status": "UP",
    "message": "Backend service is running."
}
```

### `GET /api/health/detailed`

서버의 상세 정보(메모리, 디스크 공간 등)를 확인합니다.

#### 응답 (Response)
```json
{
    "status": "UP",
    "details": {
        "diskSpace": {
            "status": "UP",
            "details": { ... }
        },
        "memory": {
            "status": "UP",
            "details": { ... }
        }
    }
}
```

---

## 🚨 에러 코드

| 코드 | 설명 | HTTP 상태 코드 |
| :--- | :--- | :--- |
| `INVALID_FILE_FORMAT` | 지원하지 않는 파일 형식입니다. | 400 |
| `FILE_SIZE_EXCEEDED` | 파일 크기가 50MB를 초과했습니다. | 400 |
| `MISSING_REQUIRED_FIELD` | 필수 파라미터가 누락되었습니다. | 400 |
| `INVALID_MODEL_CHOICE` | 잘못된 AI 모델을 선택했습니다. | 400 |
| `OCR_PROCESSING_FAILED` | OCR 처리 중 오류가 발생했습니다. | 500 |
| `AI_ANALYSIS_FAILED` | AI 분석 중 오류가 발생했습니다. | 500 |
| `INTERNAL_SERVER_ERROR` | 내부 서버 오류가 발생했습니다. | 500 |

---

## 🧪 테스트 예시

### cURL을 이용한 분석 요청
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  -F "apiKey=sk-..." # OpenAI 모델 사용 시
```

### JavaScript fetch를 이용한 요청
```javascript
const formData = new FormData();
formData.append('image', imageFile);
formData.append('modelChoice', 'SmartEyeSsen');

const response = await fetch('http://localhost:8080/api/document/analyze', {
  method: 'POST',
  body: formData
});

const result = await response.json();
console.log(result);
```

---

**최종 업데이트**: 2025년 9월 9일

```