# SmartEye v0.4 - API 레퍼런스

SmartEye v0.4 시스템의 REST API 완전한 사용 가이드입니다.

## 📋 목차

1. [API 개요](#api-개요)
2. [인증 및 보안](#인증-및-보안)
3. [문서 분석 API](#문서-분석-api)
4. [사용자 관리 API](#사용자-관리-api)
5. [작업 상태 API](#작업-상태-api)
6. [헬스체크 API](#헬스체크-api)
7. [모니터링 API](#모니터링-api)
8. [에러 코드](#에러-코드)
9. [SDK 및 예시](#sdk-및-예시)

## 🌐 API 개요

### 베이스 URL
- **개발 환경**: `http://localhost:8080/api`
- **프로덕션**: `https://yourdomain.com/api`

### 지원 형식
- **요청**: `multipart/form-data`, `application/json`
- **응답**: `application/json`

### 버전 정보
- **API 버전**: v1
- **OpenAPI 스펙**: http://localhost:8080/v3/api-docs
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html

---

## 🔐 인증 및 보안

### API 키 인증
OpenAI API를 사용하는 기능에 대해서만 API 키가 필요합니다.

**방법 1: 요청 파라미터로 전달**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "apiKey=your-openai-api-key"
```

**방법 2: 환경변수 사용 (권장)**
```bash
export OPENAI_API_KEY="your-openai-api-key"
# 서버 재시작 후 자동으로 사용됨
```

### CORS 정책
```yaml
개발 환경:
  - http://localhost:3000
  - http://localhost:8080

프로덕션 환경:
  - https://yourdomain.com
  - https://www.yourdomain.com
```

---

## 📄 문서 분석 API

### 1. 이미지 분석

**엔드포인트**: `POST /api/document/analyze`

**설명**: 단일 이미지에서 레이아웃 분석, OCR, AI 설명을 수행합니다.

**요청 형식**:
```http
POST /api/document/analyze HTTP/1.1
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="image"; filename="worksheet.jpg"
Content-Type: image/jpeg

[이미지 바이너리 데이터]
--boundary
Content-Disposition: form-data; name="modelChoice"

SmartEyeSsen
--boundary
Content-Disposition: form-data; name="apiKey"

sk-your-openai-api-key (선택사항)
--boundary--
```

**파라미터**:
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `image` | file | ✅ | 분석할 이미지 파일 (JPG, PNG, GIF) |
| `modelChoice` | string | ❌ | 분석 모델 (기본값: SmartEyeSsen) |
| `apiKey` | string | ❌ | OpenAI API 키 (환경변수 우선) |

**지원 파일 형식**:
- `image/jpeg`, `image/jpg`
- `image/png`  
- `image/gif`

**최대 파일 크기**: 50MB

**응답 예시**:
```json
{
  "success": true,
  "jobId": "job_1234567890abcdef",
  "message": "Analysis completed successfully",
  "data": {
    "analysisJob": {
      "id": 123,
      "jobId": "job_1234567890abcdef",
      "originalFilename": "worksheet.jpg",
      "status": "COMPLETED",
      "createdAt": "2024-12-01T10:30:00Z",
      "completedAt": "2024-12-01T10:32:30Z"
    },
    "documentPages": [
      {
        "id": 456,
        "pageNumber": 1,
        "imagePath": "/uploads/job_1234567890abcdef/page_1.jpg",
        "layoutBlocks": [
          {
            "id": 789,
            "className": "title",
            "confidence": 0.95,
            "coordinates": {
              "x1": 100,
              "y1": 50,
              "x2": 500,
              "y2": 100
            },
            "ocrText": "수학 문제집 1단원",
            "aiDescription": "이 영역은 문제집의 제목 부분으로..."
          }
        ],
        "textBlocks": [
          {
            "id": 101112,
            "text": "수학 문제집 1단원",
            "coordinates": {
              "x": 100,
              "y": 50,
              "width": 400,
              "height": 50
            },
            "confidence": 0.98
          }
        ]
      }
    ],
    "summary": {
      "totalLayoutBlocks": 33,
      "totalTextBlocks": 21,
      "processingTimeSeconds": 150,
      "aiDescriptionsGenerated": 15
    }
  }
}
```

### 2. PDF 분석

**엔드포인트**: `POST /api/document/analyze-pdf`

**설명**: PDF 파일의 모든 페이지를 순차적으로 분석합니다.

**요청 형식**:
```http
POST /api/document/analyze-pdf HTTP/1.1
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="file"; filename="exam.pdf"
Content-Type: application/pdf

[PDF 바이너리 데이터]
--boundary
Content-Disposition: form-data; name="modelChoice"

SmartEyeSsen
--boundary--
```

**파라미터**:
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `file` | file | ✅ | 분석할 PDF 파일 |
| `modelChoice` | string | ❌ | 분석 모델 (기본값: SmartEyeSsen) |
| `apiKey` | string | ❌ | OpenAI API 키 |

**지원 파일**: `application/pdf`
**최대 파일 크기**: 50MB
**최대 페이지 수**: 100페이지

**응답 예시**:
```json
{
  "success": true,
  "jobId": "job_pdf_abcdef123456",
  "message": "PDF analysis completed successfully",
  "data": {
    "analysisJob": {
      "id": 124,
      "jobId": "job_pdf_abcdef123456",
      "originalFilename": "exam.pdf",
      "status": "COMPLETED",
      "totalPages": 5,
      "createdAt": "2024-12-01T11:00:00Z",
      "completedAt": "2024-12-01T11:08:45Z"
    },
    "documentPages": [
      {
        "id": 457,
        "pageNumber": 1,
        "imagePath": "/uploads/job_pdf_abcdef123456/page_1.jpg",
        "layoutBlocks": [...],
        "textBlocks": [...]
      },
      {
        "id": 458, 
        "pageNumber": 2,
        "imagePath": "/uploads/job_pdf_abcdef123456/page_2.jpg",
        "layoutBlocks": [...],
        "textBlocks": [...]
      }
    ],
    "summary": {
      "totalPages": 5,
      "totalLayoutBlocks": 165,
      "totalTextBlocks": 105,
      "processingTimeSeconds": 525,
      "aiDescriptionsGenerated": 75
    }
  }
}
```

### 3. 텍스트 포맷팅

**엔드포인트**: `POST /api/document/format-text`

**설명**: 분석 결과 JSON을 읽기 쉬운 텍스트로 포맷팅합니다.

**요청**:
```http
POST /api/document/format-text HTTP/1.1
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="jsonFile"; filename="result.json"
Content-Type: application/json

[JSON 파일 내용]
--boundary--
```

**응답**:
```json
{
  "success": true,
  "data": {
    "formattedText": "=== 수학 문제집 1단원 ===\n\n문제 1: 다음 식을 계산하시오.\n2 + 3 = ?\n\n답: 5\n\n문제 2: ...",
    "wordCount": 1247,
    "characterCount": 5823
  }
}
```

### 4. Word 문서 생성

**엔드포인트**: `POST /api/document/save-as-word`

**설명**: 포맷된 텍스트를 Word 문서로 생성합니다.

**요청**:
```http
POST /api/document/save-as-word HTTP/1.1
Content-Type: application/x-www-form-urlencoded

text=포맷된 텍스트 내용&filename=smarteye_document
```

**응답**:
```json
{
  "success": true,
  "data": {
    "filename": "smarteye_document.docx",
    "downloadUrl": "/api/document/download/smarteye_document.docx",
    "fileSize": 23456
  }
}
```

### 5. 파일 다운로드

**엔드포인트**: `GET /api/document/download/{filename}`

**설명**: 생성된 문서 파일을 다운로드합니다.

**요청**:
```http
GET /api/document/download/smarteye_document.docx HTTP/1.1
```

**응답**: 파일 바이너리 데이터

---

## 👥 사용자 관리 API

### 1. 사용자 생성

**엔드포인트**: `POST /api/users`

**요청**:
```json
{
  "username": "testuser",
  "email": "test@example.com"
}
```

**응답**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "testuser", 
    "email": "test@example.com",
    "createdAt": "2024-12-01T10:00:00Z"
  }
}
```

### 2. 사용자 조회

**엔드포인트**: `GET /api/users/{userId}`

**응답**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com", 
    "createdAt": "2024-12-01T10:00:00Z",
    "analysisCount": 15,
    "lastActivityAt": "2024-12-01T15:30:00Z"
  }
}
```

### 3. 사용자 목록

**엔드포인트**: `GET /api/users`

**쿼리 파라미터**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `page` | int | 페이지 번호 (기본값: 0) |
| `size` | int | 페이지 크기 (기본값: 20) |
| `sort` | string | 정렬 기준 (기본값: createdAt,desc) |

**응답**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "username": "testuser1",
        "email": "test1@example.com",
        "createdAt": "2024-12-01T10:00:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": {
        "sorted": true,
        "orders": [{"property": "createdAt", "direction": "DESC"}]
      }
    },
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 📊 작업 상태 API

### 1. 분석 작업 상태 조회

**엔드포인트**: `GET /api/analysis/job/{jobId}`

**응답**:
```json
{
  "success": true,
  "data": {
    "id": 123,
    "jobId": "job_1234567890abcdef",
    "status": "PROCESSING", // PENDING, PROCESSING, COMPLETED, FAILED
    "progress": 65,
    "currentStep": "AI 설명 생성 중...",
    "originalFilename": "worksheet.jpg",
    "createdAt": "2024-12-01T10:30:00Z",
    "estimatedCompletionTime": "2024-12-01T10:32:00Z",
    "errorMessage": null
  }
}
```

### 2. 사용자별 작업 목록

**엔드포인트**: `GET /api/analysis/jobs`

**쿼리 파라미터**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `userId` | long | 사용자 ID (선택사항) |
| `status` | string | 상태 필터 (PENDING, PROCESSING, COMPLETED, FAILED) |
| `page` | int | 페이지 번호 |
| `size` | int | 페이지 크기 |

**응답**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 123,
        "jobId": "job_1234567890abcdef",
        "status": "COMPLETED",
        "originalFilename": "worksheet.jpg",
        "createdAt": "2024-12-01T10:30:00Z",
        "completedAt": "2024-12-01T10:32:30Z"
      }
    ],
    "totalElements": 50,
    "totalPages": 3
  }
}
```

---

## 💓 헬스체크 API

### 1. 전체 시스템 헬스체크

**엔드포인트**: `GET /api/health`

**응답**:
```json
{
  "status": "UP",
  "timestamp": "2024-12-01T10:00:00Z",
  "services": {
    "database": {
      "status": "UP",
      "responseTime": "15ms",
      "activeConnections": 5
    },
    "lamService": {
      "status": "UP", 
      "responseTime": "250ms",
      "url": "http://localhost:8001"
    },
    "storage": {
      "status": "UP",
      "availableSpace": "85.5 GB",
      "uploadDir": "/app/uploads"
    }
  },
  "version": "0.4.0",
  "uptime": "2 days, 5 hours, 30 minutes"
}
```

### 2. 개별 서비스 헬스체크

**데이터베이스**: `GET /api/health/db`
```json
{
  "status": "UP",
  "database": "PostgreSQL",
  "version": "15.4",
  "activeConnections": 5,
  "maxConnections": 100
}
```

**LAM 서비스**: `GET /api/health/lam`
```json
{
  "status": "UP",
  "service": "LAM Service",
  "url": "http://localhost:8001",
  "responseTime": "245ms",
  "lastCheck": "2024-12-01T10:00:00Z"
}
```

---

## 📈 모니터링 API

### 1. Prometheus 메트릭

**엔드포인트**: `GET /actuator/prometheus`

**응답**: Prometheus 형식의 메트릭 데이터
```
# HELP http_requests_total Total HTTP requests
# TYPE http_requests_total counter
http_requests_total{method="POST",uri="/api/document/analyze",status="200"} 1250.0

# HELP jvm_memory_used_bytes Used JVM memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.073741824E9

# HELP analysis_duration_seconds Time spent on document analysis
# TYPE analysis_duration_seconds histogram
analysis_duration_seconds_bucket{le="10.0"} 45.0
analysis_duration_seconds_bucket{le="30.0"} 123.0
analysis_duration_seconds_bucket{le="60.0"} 200.0
```

### 2. 시스템 메트릭

**엔드포인트**: `GET /actuator/metrics`

**응답**:
```json
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max", 
    "http.server.requests",
    "analysis.jobs.total",
    "analysis.jobs.completed",
    "analysis.jobs.failed",
    "ocr.processing.time",
    "ai.generation.time"
  ]
}
```

### 3. 특정 메트릭 조회

**엔드포인트**: `GET /actuator/metrics/{metricName}`

**예시**: `GET /actuator/metrics/analysis.jobs.total`
```json
{
  "name": "analysis.jobs.total",
  "description": "Total number of analysis jobs processed",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1250.0
    }
  ],
  "availableTags": [
    {
      "tag": "status",
      "values": ["completed", "failed", "processing"]
    }
  ]
}
```

---

## ❌ 에러 코드

### HTTP 상태 코드

| 코드 | 상태 | 설명 |
|------|------|------|
| 200 | OK | 요청 성공 |
| 201 | Created | 리소스 생성 성공 |
| 400 | Bad Request | 잘못된 요청 |
| 401 | Unauthorized | 인증 실패 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 409 | Conflict | 리소스 충돌 |
| 413 | Payload Too Large | 파일 크기 초과 |
| 415 | Unsupported Media Type | 지원하지 않는 파일 형식 |
| 429 | Too Many Requests | 요청 제한 초과 |
| 500 | Internal Server Error | 내부 서버 오류 |
| 503 | Service Unavailable | 서비스 일시 불가 |

### 애플리케이션 에러 코드

| 코드 | 메시지 | 설명 |
|------|--------|------|
| E001 | INVALID_FILE_FORMAT | 지원하지 않는 파일 형식 |
| E002 | FILE_TOO_LARGE | 파일 크기 제한 초과 |
| E003 | PROCESSING_FAILED | 문서 처리 실패 |
| E004 | OCR_SERVICE_ERROR | OCR 서비스 오류 |
| E005 | LAM_SERVICE_UNAVAILABLE | LAM 서비스 연결 실패 |
| E006 | AI_SERVICE_QUOTA_EXCEEDED | AI API 할당량 초과 |
| E007 | DATABASE_CONNECTION_ERROR | 데이터베이스 연결 오류 |
| E008 | INVALID_API_KEY | 잘못된 API 키 |
| E009 | JOB_NOT_FOUND | 작업을 찾을 수 없음 |
| E010 | CONCURRENT_LIMIT_EXCEEDED | 동시 처리 한도 초과 |

### 에러 응답 형식

```json
{
  "success": false,
  "error": {
    "code": "E001",
    "message": "지원하지 않는 파일 형식입니다.",
    "details": "허용된 형식: JPG, PNG, GIF, PDF",
    "timestamp": "2024-12-01T10:00:00Z",
    "path": "/api/document/analyze"
  },
  "supportedFormats": ["image/jpeg", "image/png", "image/gif", "application/pdf"]
}
```

---

## 💻 SDK 및 예시

### cURL 예시

**이미지 분석:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -H "Content-Type: multipart/form-data" \
  -F "image=@worksheet.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  -F "apiKey=sk-your-openai-api-key"
```

**PDF 분석:**
```bash
curl -X POST http://localhost:8080/api/document/analyze-pdf \
  -H "Content-Type: multipart/form-data" \
  -F "file=@exam.pdf" \
  -F "modelChoice=SmartEyeSsen"
```

**작업 상태 확인:**
```bash
curl -X GET http://localhost:8080/api/analysis/job/job_1234567890abcdef
```

### Python SDK

```python
import requests
import json
from typing import Optional, Dict, Any

class SmartEyeClient:
    def __init__(self, base_url: str = "http://localhost:8080", api_key: Optional[str] = None):
        self.base_url = base_url.rstrip('/')
        self.api_key = api_key
        
    def analyze_image(self, image_path: str, model_choice: str = "SmartEyeSsen") -> Dict[str, Any]:
        """이미지 분석"""
        url = f"{self.base_url}/api/document/analyze"
        
        with open(image_path, 'rb') as f:
            files = {"image": f}
            data = {"modelChoice": model_choice}
            
            if self.api_key:
                data["apiKey"] = self.api_key
                
            response = requests.post(url, files=files, data=data)
            response.raise_for_status()
            return response.json()
    
    def analyze_pdf(self, pdf_path: str, model_choice: str = "SmartEyeSsen") -> Dict[str, Any]:
        """PDF 분석"""
        url = f"{self.base_url}/api/document/analyze-pdf"
        
        with open(pdf_path, 'rb') as f:
            files = {"file": f}
            data = {"modelChoice": model_choice}
            
            if self.api_key:
                data["apiKey"] = self.api_key
                
            response = requests.post(url, files=files, data=data)
            response.raise_for_status()
            return response.json()
    
    def get_job_status(self, job_id: str) -> Dict[str, Any]:
        """작업 상태 조회"""
        url = f"{self.base_url}/api/analysis/job/{job_id}"
        response = requests.get(url)
        response.raise_for_status()
        return response.json()
    
    def create_user(self, username: str, email: str) -> Dict[str, Any]:
        """사용자 생성"""
        url = f"{self.base_url}/api/users"
        data = {"username": username, "email": email}
        response = requests.post(url, json=data)
        response.raise_for_status()
        return response.json()

# 사용 예시
client = SmartEyeClient(api_key="your-api-key")

# 이미지 분석
result = client.analyze_image("worksheet.jpg")
print(f"Job ID: {result['jobId']}")

# 작업 상태 확인
status = client.get_job_status(result['jobId'])
print(f"Status: {status['data']['status']}")
```

### JavaScript SDK

```javascript
class SmartEyeClient {
  constructor(baseUrl = 'http://localhost:8080', apiKey = null) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.apiKey = apiKey;
  }

  async analyzeImage(imageFile, modelChoice = 'SmartEyeSsen') {
    const formData = new FormData();
    formData.append('image', imageFile);
    formData.append('modelChoice', modelChoice);
    
    if (this.apiKey) {
      formData.append('apiKey', this.apiKey);
    }

    const response = await fetch(`${this.baseUrl}/api/document/analyze`, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async analyzePdf(pdfFile, modelChoice = 'SmartEyeSsen') {
    const formData = new FormData();
    formData.append('file', pdfFile);
    formData.append('modelChoice', modelChoice);
    
    if (this.apiKey) {
      formData.append('apiKey', this.apiKey);
    }

    const response = await fetch(`${this.baseUrl}/api/document/analyze-pdf`, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getJobStatus(jobId) {
    const response = await fetch(`${this.baseUrl}/api/analysis/job/${jobId}`);
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async createUser(username, email) {
    const response = await fetch(`${this.baseUrl}/api/users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ username, email })
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }
}

// 사용 예시
const client = new SmartEyeClient('http://localhost:8080', 'your-api-key');

// 파일 입력에서 이미지 분석
document.getElementById('fileInput').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (file) {
    try {
      const result = await client.analyzeImage(file);
      console.log('Analysis result:', result);
    } catch (error) {
      console.error('Error:', error);
    }
  }
});
```

### Java SDK

```java
import java.io.*;
import java.net.http.*;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SmartEyeClient {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SmartEyeClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyzeImage(Path imagePath, String modelChoice) throws Exception {
        String boundary = "----FormBoundary" + System.currentTimeMillis();
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/document/analyze"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary);

        // Multipart body 구성
        String multipartBody = buildMultipartBody(imagePath, modelChoice, boundary);
        
        HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), AnalysisResult.class);
    }

    private String buildMultipartBody(Path filePath, String modelChoice, String boundary) 
            throws IOException {
        StringBuilder body = new StringBuilder();
        
        // 파일 파트
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"")
            .append(filePath.getFileName()).append("\"\r\n");
        body.append("Content-Type: ").append(Files.probeContentType(filePath)).append("\r\n\r\n");
        body.append(new String(Files.readAllBytes(filePath))).append("\r\n");
        
        // 모델 선택 파트
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"modelChoice\"\r\n\r\n");
        body.append(modelChoice).append("\r\n");
        
        // API 키 파트 (옵션)
        if (apiKey != null) {
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"apiKey\"\r\n\r\n");
            body.append(apiKey).append("\r\n");
        }
        
        body.append("--").append(boundary).append("--\r\n");
        return body.toString();
    }

    // DTO 클래스들
    public static class AnalysisResult {
        public boolean success;
        public String jobId;
        public String message;
        public AnalysisData data;
    }

    public static class AnalysisData {
        public AnalysisJob analysisJob;
        public List<DocumentPage> documentPages;
        public AnalysisSummary summary;
    }

    // 사용 예시
    public static void main(String[] args) throws Exception {
        SmartEyeClient client = new SmartEyeClient("http://localhost:8080", "your-api-key");
        
        Path imagePath = Paths.get("worksheet.jpg");
        AnalysisResult result = client.analyzeImage(imagePath, "SmartEyeSsen");
        
        System.out.println("Job ID: " + result.jobId);
        System.out.println("Status: " + result.data.analysisJob.status);
    }
}
```

---

## 📚 추가 자료

### OpenAPI 스펙
- **전체 API 스펙**: http://localhost:8080/v3/api-docs
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html

### 예제 파일
- **Postman 컬렉션**: `docs/smarteye-api.postman_collection.json`
- **테스트 이미지**: `test/resources/sample_images/`
- **샘플 PDF**: `test/resources/sample_pdfs/`

### 성능 가이드
- **최적 이미지 크기**: 2048x1536 픽셀 이하
- **권장 파일 크기**: 5MB 이하
- **동시 요청 한도**: 사용자당 5개
- **API 속도 제한**: 분당 100 요청

---

이 API 레퍼런스가 SmartEye v0.4 시스템과의 통합에 도움이 되기를 바랍니다. 추가적인 질문이나 지원이 필요하시면 언제든 문의해 주세요! 🚀