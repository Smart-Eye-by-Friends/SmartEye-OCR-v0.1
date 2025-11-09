# 분석 API

분석 API는 업로드된 페이지에 대해 AI 레이아웃 분석, OCR 텍스트 추출, 정렬, 포맷팅을 수행합니다.

## 📖 목차

- [엔드포인트 목록](#엔드포인트-목록)
- [1. 프로젝트 배치 분석 (동기)](#1-프로젝트-배치-분석-동기)
- [2. 단일 페이지 비동기 분석](#2-단일-페이지-비동기-분석)
- [3. 분석 작업 상태 조회](#3-분석-작업-상태-조회)
- [분석 파이프라인 상세](#분석-파이프라인-상세)

---

## 엔드포인트 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/projects/{project_id}/analyze` | 프로젝트 전체 배치 분석 (동기) |
| POST | `/api/pages/{page_id}/analyze/async` | 단일 페이지 비동기 분석 |
| GET | `/api/analysis/jobs/{job_id}` | 비동기 작업 상태 조회 |

---

## 1. 프로젝트 배치 분석 (동기)

프로젝트 내 모든 `pending` 상태 페이지를 순차적으로 분석합니다.

### Endpoint

```http
POST /api/projects/{project_id}/analyze
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 분석할 프로젝트 ID |

### Request Body

```json
{
  "use_ai_descriptions": true,
  "api_key": "sk-..."
}
```

**필드 설명**:

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `use_ai_descriptions` | boolean | ❌ | AI 설명 생성 여부 (기본값: true)<br>figure, table, flowchart에 대한 GPT-4 설명 생성 |
| `api_key` | string | ❌ | OpenAI API 키 (선택)<br>제공하지 않으면 서버 환경 변수 사용 |

### Response

**HTTP 202 Accepted**

```json
{
  "project_id": 1,
  "status": "completed",
  "total_pages": 3,
  "completed_pages": 3,
  "failed_pages": 0,
  "total_time": 15.67,
  "pages": [
    {
      "page_id": 1,
      "page_number": 1,
      "status": "completed",
      "layout_count": 12,
      "ocr_count": 10,
      "ai_description_count": 2,
      "processing_time": 5.23,
      "message": "페이지 분석 완료"
    },
    {
      "page_id": 2,
      "page_number": 2,
      "status": "completed",
      "layout_count": 15,
      "ocr_count": 13,
      "ai_description_count": 2,
      "processing_time": 5.12,
      "message": "페이지 분석 완료"
    },
    {
      "page_id": 3,
      "page_number": 3,
      "status": "completed",
      "layout_count": 10,
      "ocr_count": 8,
      "ai_description_count": 2,
      "processing_time": 5.32,
      "message": "페이지 분석 완료"
    }
  ]
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `project_id` | integer | 프로젝트 ID |
| `status` | string | 전체 분석 상태 (`completed`, `partial`, `failed`) |
| `total_pages` | integer | 분석 대상 페이지 수 |
| `completed_pages` | integer | 성공한 페이지 수 |
| `failed_pages` | integer | 실패한 페이지 수 |
| `total_time` | float | 전체 처리 시간 (초) |
| `pages` | array | 페이지별 분석 결과 |

**페이지별 결과**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `page_id` | integer | 페이지 ID |
| `page_number` | integer | 페이지 번호 |
| `status` | string | 분석 상태 (`completed`, `failed`) |
| `layout_count` | integer | 감지된 레이아웃 요소 수 |
| `ocr_count` | integer | OCR 수행된 요소 수 |
| `ai_description_count` | integer | AI 설명 생성된 요소 수 |
| `processing_time` | float | 페이지 처리 시간 (초) |
| `message` | string | 상태 메시지 |

### 예제 코드

**JavaScript (fetch)**:

```javascript
const analyzeProject = async (projectId, useAI = true, apiKey = null) => {
  const response = await fetch(
    `http://localhost:8000/api/projects/${projectId}/analyze`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        use_ai_descriptions: useAI,
        api_key: apiKey
      })
    }
  );
  
  if (!response.ok) {
    throw new Error(`분석 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
analyzeProject(1, true, 'sk-...')
  .then(result => {
    console.log(`분석 완료: ${result.completed_pages}/${result.total_pages} 페이지`);
    console.log(`총 소요 시간: ${result.total_time.toFixed(2)}초`);
    
    result.pages.forEach(page => {
      console.log(`페이지 ${page.page_number}:`);
      console.log(`  - 레이아웃: ${page.layout_count}개`);
      console.log(`  - OCR: ${page.ocr_count}개`);
      console.log(`  - AI 설명: ${page.ai_description_count}개`);
      console.log(`  - 시간: ${page.processing_time.toFixed(2)}초`);
    });
  })
  .catch(error => console.error('분석 실패:', error));
```

**React Component - 진행률 표시**:

```jsx
import React, { useState } from 'react';
import axios from 'axios';

function ProjectAnalyzer({ projectId, onComplete }) {
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  
  const handleAnalyze = async () => {
    setAnalyzing(true);
    
    try {
      const response = await axios.post(
        `http://localhost:8000/api/projects/${projectId}/analyze`,
        {
          use_ai_descriptions: true,
          api_key: localStorage.getItem('openai_api_key') // 사용자 API 키 사용
        }
      );
      
      setResult(response.data);
      
      if (response.data.status === 'completed') {
        alert(`분석 완료: ${response.data.completed_pages}/${response.data.total_pages} 페이지`);
      } else {
        alert(`일부 실패: ${response.data.failed_pages}개 페이지 실패`);
      }
      
      if (onComplete) onComplete(response.data);
      
    } catch (error) {
      console.error('분석 실패:', error);
      alert('분석 실패: ' + error.message);
    } finally {
      setAnalyzing(false);
    }
  };
  
  return (
    <div>
      <button onClick={handleAnalyze} disabled={analyzing}>
        {analyzing ? '분석 중...' : 'AI 분석 시작'}
      </button>
      
      {analyzing && (
        <div className="analyzing-indicator">
          <div className="spinner"></div>
          <p>페이지를 분석하고 있습니다. 잠시만 기다려주세요...</p>
        </div>
      )}
      
      {result && (
        <div className="analysis-result">
          <h3>분석 결과</h3>
          <p>전체 페이지: {result.total_pages}</p>
          <p>완료: {result.completed_pages}</p>
          <p>실패: {result.failed_pages}</p>
          <p>소요 시간: {result.total_time.toFixed(2)}초</p>
          
          <table>
            <thead>
              <tr>
                <th>페이지</th>
                <th>상태</th>
                <th>레이아웃</th>
                <th>OCR</th>
                <th>AI 설명</th>
                <th>시간</th>
              </tr>
            </thead>
            <tbody>
              {result.pages.map(page => (
                <tr key={page.page_id}>
                  <td>{page.page_number}</td>
                  <td>{page.status}</td>
                  <td>{page.layout_count}</td>
                  <td>{page.ocr_count}</td>
                  <td>{page.ai_description_count}</td>
                  <td>{page.processing_time.toFixed(2)}s</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
```

---

## 2. 단일 페이지 비동기 분석

단일 페이지를 백그라운드에서 비동기로 분석합니다. 작업 ID를 즉시 반환하고, 작업 상태는 별도로 조회할 수 있습니다.

### Endpoint

```http
POST /api/pages/{page_id}/analyze/async
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page_id` | integer | 분석할 페이지 ID |

### Request Body

```json
{
  "use_ai_descriptions": true,
  "api_key": "sk-..."
}
```

### Response

**HTTP 202 Accepted**

```json
{
  "job_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "pending",
  "message": "페이지 분석 작업이 시작되었습니다.",
  "page_id": 1,
  "status_check_url": "/api/analysis/jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `job_id` | string | 작업 고유 ID (UUID) |
| `status` | string | 작업 상태 (`pending`) |
| `message` | string | 상태 메시지 |
| `page_id` | integer | 분석 중인 페이지 ID |
| `status_check_url` | string | 작업 상태 조회 URL |

### 예제 코드

```javascript
const analyzePageAsync = async (pageId, useAI = true, apiKey = null) => {
  const response = await fetch(
    `http://localhost:8000/api/pages/${pageId}/analyze/async`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        use_ai_descriptions: useAI,
        api_key: apiKey
      })
    }
  );
  
  if (!response.ok) {
    throw new Error(`비동기 분석 시작 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
analyzePageAsync(1, true)
  .then(job => {
    console.log('작업 시작됨:', job.job_id);
    console.log('상태 조회 URL:', job.status_check_url);
    
    // 주기적으로 상태 확인
    checkJobStatus(job.job_id);
  })
  .catch(error => console.error('작업 시작 실패:', error));
```

---

## 3. 분석 작업 상태 조회

비동기 분석 작업의 현재 상태를 조회합니다.

### Endpoint

```http
GET /api/analysis/jobs/{job_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `job_id` | string | 작업 ID (UUID) |

### Response

#### 작업 대기 중

**HTTP 200 OK**

```json
{
  "job_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "pending",
  "page_id": 1,
  "page_number": 1,
  "project_id": 1,
  "result": null,
  "error": null,
  "progress": "작업 대기 중..."
}
```

#### 작업 진행 중

```json
{
  "job_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "processing",
  "page_id": 1,
  "page_number": 1,
  "project_id": 1,
  "result": null,
  "error": null,
  "progress": "레이아웃 분석 및 OCR 수행 중..."
}
```

#### 작업 완료

```json
{
  "job_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "completed",
  "page_id": 1,
  "page_number": 1,
  "project_id": 1,
  "result": {
    "page_id": 1,
    "page_number": 1,
    "layout_count": 12,
    "ocr_count": 10,
    "ai_description_count": 2,
    "processing_time": 5.23,
    "message": "페이지 분석이 성공적으로 완료되었습니다."
  },
  "error": null,
  "progress": "분석 완료"
}
```

#### 작업 실패

```json
{
  "job_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "failed",
  "page_id": 1,
  "page_number": 1,
  "project_id": 1,
  "result": null,
  "error": "이미지 파일을 찾을 수 없습니다.",
  "progress": "분석 실패"
}
```

### 예제 코드

**JavaScript - 폴링(Polling)**:

```javascript
const checkJobStatus = async (jobId) => {
  const response = await fetch(
    `http://localhost:8000/api/analysis/jobs/${jobId}`
  );
  
  if (!response.ok) {
    throw new Error(`작업 상태 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 주기적으로 상태 확인 (폴링)
const pollJobStatus = async (jobId, interval = 2000, maxAttempts = 60) => {
  let attempts = 0;
  
  const poll = async () => {
    if (attempts >= maxAttempts) {
      throw new Error('작업 조회 시간 초과');
    }
    
    attempts++;
    const status = await checkJobStatus(jobId);
    
    console.log(`[${attempts}] 상태: ${status.status} - ${status.progress}`);
    
    if (status.status === 'completed') {
      console.log('작업 완료!', status.result);
      return status.result;
    }
    
    if (status.status === 'failed') {
      throw new Error(`작업 실패: ${status.error}`);
    }
    
    // 계속 대기 중이면 재시도
    await new Promise(resolve => setTimeout(resolve, interval));
    return poll();
  };
  
  return poll();
};

// 사용 예시
analyzePageAsync(1, true)
  .then(job => {
    console.log('비동기 작업 시작:', job.job_id);
    return pollJobStatus(job.job_id);
  })
  .then(result => {
    console.log('분석 완료:', result);
    alert(`분석 완료: 레이아웃 ${result.layout_count}개, OCR ${result.ocr_count}개`);
  })
  .catch(error => {
    console.error('에러:', error);
    alert('분석 실패: ' + error.message);
  });
```

**React Component - 실시간 상태 표시**:

```jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function AsyncAnalyzer({ pageId, onComplete }) {
  const [jobId, setJobId] = useState(null);
  const [status, setStatus] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  
  useEffect(() => {
    if (!jobId) return;
    
    // 2초마다 상태 확인
    const interval = setInterval(async () => {
      try {
        const response = await axios.get(
          `http://localhost:8000/api/analysis/jobs/${jobId}`
        );
        setStatus(response.data);
        
        if (response.data.status === 'completed') {
          clearInterval(interval);
          setAnalyzing(false);
          if (onComplete) onComplete(response.data.result);
        }
        
        if (response.data.status === 'failed') {
          clearInterval(interval);
          setAnalyzing(false);
          alert('분석 실패: ' + response.data.error);
        }
      } catch (error) {
        console.error('상태 조회 실패:', error);
      }
    }, 2000);
    
    return () => clearInterval(interval);
  }, [jobId, onComplete]);
  
  const handleStartAnalysis = async () => {
    setAnalyzing(true);
    
    try {
      const response = await axios.post(
        `http://localhost:8000/api/pages/${pageId}/analyze/async`,
        { use_ai_descriptions: true }
      );
      setJobId(response.data.job_id);
      setStatus(response.data);
    } catch (error) {
      console.error('분석 시작 실패:', error);
      alert('분석 시작 실패: ' + error.message);
      setAnalyzing(false);
    }
  };
  
  return (
    <div>
      <button onClick={handleStartAnalysis} disabled={analyzing}>
        {analyzing ? '분석 중...' : '비동기 분석 시작'}
      </button>
      
      {status && (
        <div className="status-display">
          <p><strong>작업 ID:</strong> {status.job_id}</p>
          <p><strong>상태:</strong> {status.status}</p>
          <p><strong>진행상황:</strong> {status.progress}</p>
          
          {status.status === 'completed' && status.result && (
            <div className="result">
              <h4>분석 결과</h4>
              <p>레이아웃: {status.result.layout_count}개</p>
              <p>OCR: {status.result.ocr_count}개</p>
              <p>AI 설명: {status.result.ai_description_count}개</p>
              <p>처리 시간: {status.result.processing_time.toFixed(2)}초</p>
            </div>
          )}
          
          {status.status === 'failed' && (
            <div className="error">
              <p style={{color: 'red'}}>에러: {status.error}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

---

## 분석 파이프라인 상세

각 페이지 분석은 다음 단계로 진행됩니다:

### 1단계: 레이아웃 분석 (Layout Detection)

- **모델**: DocLayout-YOLO
- **감지 클래스**: 
  - `question_number`: 문제 번호 (worksheet 전용)
  - `text`: 본문 텍스트
  - `figure`: 그림/도표
  - `table`: 표
  - `flowchart`: 순서도
  - 등

### 2단계: OCR 텍스트 추출

- **엔진**: PaddleOCR
- **대상**: `text`, `question_number` 등 텍스트 요소
- **언어**: 한국어, 영어, 중국어, 일본어 지원

### 3단계: AI 설명 생성 (선택)

- **모델**: GPT-4-turbo
- **대상**: `figure`, `table`, `flowchart`
- **조건**: `use_ai_descriptions=true` 일 때만 수행

### 4단계: 정렬 (Sorting)

#### Worksheet (문제지)
- 문제 번호 기반 그룹화
- 앵커 요소(문제 번호) 중심으로 자식 요소 수집
- Y좌표 기준 정렬

#### Document (일반 문서)
- 좌표 기반 읽기 순서 정렬
- Y좌표 우선, X좌표 보조

### 5단계: 포맷팅 (Formatting)

- 데이터베이스 포맷팅 규칙 적용
- 클래스별 접두사/접미사, 들여쓰기 적용
- HTML 형식으로 변환

### 6단계: 버전 저장

- `version_type="auto_formatted"` 버전 생성
- `is_current=true` 설정

---

## 에러 응답

### 404 Not Found

프로젝트 또는 페이지를 찾을 수 없음

```json
{
  "error": "프로젝트를 찾을 수 없습니다.",
  "status_code": 404
}
```

### 500 Internal Server Error

분석 중 오류 발생

```json
{
  "error": "Internal Server Error",
  "detail": "레이아웃 분석 중 오류가 발생했습니다.",
  "status_code": 500
}
```

---

## 다음 단계

- **[다운로드 API](./04_다운로드_API.md)**: 분석 결과를 Word 문서로 다운로드
- **[데이터 모델](./05_데이터_모델.md)**: 분석 관련 스키마 상세 정보
