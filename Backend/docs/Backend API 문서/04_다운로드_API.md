# 다운로드 API

분석이 완료된 프로젝트의 텍스트를 통합하여 조회하거나 Word 문서로 다운로드할 수 있습니다.

## 📖 목차

- [엔드포인트 목록](#엔드포인트-목록)
- [1. 통합 텍스트 조회](#1-통합-텍스트-조회)
- [2. Word 문서 다운로드](#2-word-문서-다운로드)

---

## 엔드포인트 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/projects/{project_id}/combined-text` | 프로젝트 통합 텍스트 조회 (JSON) |
| POST | `/api/projects/{project_id}/download` | Word 문서 다운로드 (DOCX) |

---

## 1. 통합 텍스트 조회

프로젝트의 모든 페이지 텍스트를 통합하여 조회합니다. 캐시를 사용하므로 빠르게 응답합니다.

### Endpoint

```http
GET /api/projects/{project_id}/combined-text
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 프로젝트 ID |

### Response

**HTTP 200 OK**

```json
{
  "project_id": 1,
  "project_name": "수학 문제집 1단원",
  "combined_text": "<h2>1. 다음 식을 계산하시오.</h2>\n<p>(1) 3 + 5 = ?</p>\n<p>답: 8</p>\n\n<h2>2. 다음 그림을 보고 답하시오.</h2>\n<p>[그림 설명] 세 개의 사과가 그려져 있는 그림입니다...</p>",
  "stats": {
    "total_pages": 3,
    "total_words": 450,
    "total_characters": 2340
  },
  "generated_at": "2025-01-22T11:00:00"
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `project_id` | integer | 프로젝트 ID |
| `project_name` | string | 프로젝트 이름 |
| `combined_text` | string | 전체 페이지의 텍스트를 통합한 HTML 문자열 |
| `stats` | object | 통계 정보 |
| `stats.total_pages` | integer | 총 페이지 수 |
| `stats.total_words` | integer | 총 단어 수 |
| `stats.total_characters` | integer | 총 문자 수 |
| `generated_at` | datetime | 통합 텍스트 생성 일시 |

### 캐시 동작

- 첫 번째 호출 시 모든 페이지의 최신 텍스트 버전을 수집하여 통합
- 결과를 `combined_results` 테이블에 캐시
- 이후 호출 시 캐시된 데이터 반환 (빠른 응답)
- 페이지 텍스트가 수정되면 자동으로 캐시 갱신

### 예제 코드

**JavaScript (fetch)**:

```javascript
const getCombinedText = async (projectId) => {
  const response = await fetch(
    `http://localhost:8000/api/projects/${projectId}/combined-text`
  );
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('프로젝트를 찾을 수 없습니다.');
    }
    throw new Error(`통합 텍스트 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getCombinedText(1).then(data => {
  console.log('프로젝트:', data.project_name);
  console.log('페이지 수:', data.stats.total_pages);
  console.log('단어 수:', data.stats.total_words);
  
  // HTML 표시
  document.getElementById('combined-content').innerHTML = data.combined_text;
});
```

**React Component**:

```jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function CombinedTextViewer({ projectId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    const fetchCombinedText = async () => {
      try {
        const response = await axios.get(
          `http://localhost:8000/api/projects/${projectId}/combined-text`
        );
        setData(response.data);
      } catch (error) {
        console.error('통합 텍스트 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    
    fetchCombinedText();
  }, [projectId]);
  
  if (loading) return <div>로딩 중...</div>;
  if (!data) return <div>데이터를 찾을 수 없습니다.</div>;
  
  return (
    <div className="combined-text-viewer">
      <header>
        <h1>{data.project_name}</h1>
        <div className="stats">
          <span>페이지: {data.stats.total_pages}</span>
          <span>단어: {data.stats.total_words}</span>
          <span>문자: {data.stats.total_characters}</span>
        </div>
      </header>
      
      <div 
        className="content"
        dangerouslySetInnerHTML={{ __html: data.combined_text }}
      />
      
      <footer>
        <small>생성 일시: {new Date(data.generated_at).toLocaleString()}</small>
      </footer>
    </div>
  );
}
```

---

## 2. Word 문서 다운로드

프로젝트의 통합 텍스트를 Word 문서(DOCX) 형식으로 다운로드합니다.

### Endpoint

```http
POST /api/projects/{project_id}/download
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 프로젝트 ID |

### Response

**HTTP 200 OK**

**Content-Type**: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`

**Content-Disposition**: `attachment; filename="project_1_수학_문제집_1단원.docx"`

응답은 바이너리 스트림으로 Word 문서 파일을 반환합니다.

### 문서 구조

생성되는 Word 문서는 다음과 같은 구조를 가집니다:

1. **제목**: 프로젝트 이름 (Heading 1)
2. **메타정보**: 총 페이지 수, 생성 일시 등
3. **본문**: 페이지별로 구분된 내용
   - 페이지 번호 (Heading 2)
   - 페이지 내용 (HTML을 Word 형식으로 변환)
4. **푸터**: 생성 정보

### 예제 코드

**JavaScript (fetch)**:

```javascript
const downloadDocument = async (projectId) => {
  const response = await fetch(
    `http://localhost:8000/api/projects/${projectId}/download`,
    {
      method: 'POST'
    }
  );
  
  if (!response.ok) {
    throw new Error(`문서 다운로드 실패: ${response.status}`);
  }
  
  // Blob으로 변환
  const blob = await response.blob();
  
  // 파일명 추출
  const contentDisposition = response.headers.get('Content-Disposition');
  let filename = `project_${projectId}.docx`;
  
  if (contentDisposition) {
    const match = contentDisposition.match(/filename="(.+)"/);
    if (match) {
      filename = match[1];
    }
  }
  
  // 다운로드 트리거
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
  
  console.log('다운로드 완료:', filename);
};

// 사용 예시
document.getElementById('downloadBtn').addEventListener('click', async () => {
  try {
    await downloadDocument(1);
    alert('Word 문서 다운로드가 시작되었습니다.');
  } catch (error) {
    console.error('다운로드 실패:', error);
    alert('다운로드 실패: ' + error.message);
  }
});
```

**React Component**:

```jsx
import React, { useState } from 'react';
import axios from 'axios';

function DocumentDownloader({ projectId, projectName }) {
  const [downloading, setDownloading] = useState(false);
  
  const handleDownload = async () => {
    setDownloading(true);
    
    try {
      const response = await axios.post(
        `http://localhost:8000/api/projects/${projectId}/download`,
        {},
        {
          responseType: 'blob' // 중요: blob 타입으로 응답 받기
        }
      );
      
      // Blob 생성
      const blob = new Blob([response.data], {
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      });
      
      // 파일명 추출
      let filename = `project_${projectId}.docx`;
      const contentDisposition = response.headers['content-disposition'];
      if (contentDisposition) {
        const match = contentDisposition.match(/filename="(.+)"/);
        if (match) {
          filename = decodeURIComponent(match[1]);
        }
      }
      
      // 다운로드
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      
      alert('다운로드가 완료되었습니다.');
      
    } catch (error) {
      console.error('다운로드 실패:', error);
      alert('다운로드 실패: ' + error.message);
    } finally {
      setDownloading(false);
    }
  };
  
  return (
    <div>
      <button 
        onClick={handleDownload}
        disabled={downloading}
        className="btn btn-primary"
      >
        {downloading ? (
          <>
            <span className="spinner"></span>
            다운로드 중...
          </>
        ) : (
          <>
            <i className="icon-download"></i>
            Word 문서 다운로드
          </>
        )}
      </button>
      
      {downloading && (
        <p className="help-text">
          문서를 생성하고 있습니다. 잠시만 기다려주세요...
        </p>
      )}
    </div>
  );
}
```

**Axios 설정 팁**:

```javascript
// Axios 인터셉터를 사용한 다운로드 헬퍼
import axios from 'axios';

const downloadFile = async (url, method = 'GET', data = null) => {
  try {
    const response = await axios({
      url,
      method,
      data,
      responseType: 'blob',
      onDownloadProgress: (progressEvent) => {
        const percentCompleted = Math.round(
          (progressEvent.loaded * 100) / progressEvent.total
        );
        console.log(`다운로드 진행률: ${percentCompleted}%`);
      }
    });
    
    // 파일명 추출
    const contentDisposition = response.headers['content-disposition'];
    let filename = 'download';
    if (contentDisposition) {
      const match = contentDisposition.match(/filename="(.+)"/);
      if (match) {
        filename = decodeURIComponent(match[1]);
      }
    }
    
    // Blob 생성 및 다운로드
    const blob = new Blob([response.data]);
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(downloadUrl);
    
    return filename;
  } catch (error) {
    console.error('다운로드 실패:', error);
    throw error;
  }
};

// 사용 예시
downloadFile(`http://localhost:8000/api/projects/1/download`, 'POST')
  .then(filename => alert(`${filename} 다운로드 완료`))
  .catch(error => alert('다운로드 실패: ' + error.message));
```

---

## 다운로드 플로우 다이어그램

```
사용자 → [다운로드 버튼 클릭]
         ↓
프론트엔드 → POST /api/projects/{id}/download
         ↓
백엔드 → 1. 통합 텍스트 조회 (캐시 우선)
         2. HTML → Word 변환 (python-docx)
         3. 파일 스트림 생성
         ↓
프론트엔드 ← Blob 응답 수신
         ↓
브라우저 → 파일 다운로드 트리거
         ↓
완료!
```

---

## 에러 응답

### 404 Not Found

프로젝트를 찾을 수 없음

```json
{
  "error": "프로젝트를 찾을 수 없습니다.",
  "status_code": 404
}
```

### 500 Internal Server Error

문서 생성 실패

```json
{
  "error": "Internal Server Error",
  "detail": "Word 문서 생성 중 오류가 발생했습니다.",
  "status_code": 500
}
```

### 501 Not Implemented

python-docx 라이브러리 미설치

```json
{
  "error": "python-docx 라이브러리가 설치되지 않았습니다.",
  "status_code": 501
}
```

---

## 주의사항

### 파일명 인코딩

한글 파일명이 포함된 경우 브라우저마다 처리 방식이 다를 수 있습니다. 백엔드에서는 UTF-8로 인코딩된 파일명을 반환하므로, 필요시 `decodeURIComponent()`를 사용하세요.

### 큰 프로젝트 처리

페이지 수가 많은 프로젝트는 문서 생성에 시간이 걸릴 수 있습니다. 프론트엔드에서 로딩 인디케이터를 표시하는 것을 권장합니다.

### 브라우저 호환성

- `Blob` API는 IE10 이상에서 지원됩니다.
- `download` 속성은 IE에서 지원되지 않으므로, 필요시 polyfill을 사용하세요.

---

## 다음 단계

- **[데이터 모델](./05_데이터_모델.md)**: 통합 결과 스키마 상세 정보
- **[에러 처리](./06_에러_처리.md)**: 에러 코드 및 처리 방법
