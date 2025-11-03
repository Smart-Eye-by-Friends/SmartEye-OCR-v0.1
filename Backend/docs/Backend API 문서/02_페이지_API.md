# 페이지 API

페이지는 프로젝트를 구성하는 개별 문서 페이지입니다. 이미지 또는 PDF 형식으로 업로드할 수 있으며, 각 페이지는 레이아웃 분석 및 OCR 처리 대상이 됩니다.

## 📖 목차

- [엔드포인트 목록](#엔드포인트-목록)
- [1. 페이지 업로드 (이미지/PDF)](#1-페이지-업로드-이미지pdf)
- [2. 페이지 상세 조회](#2-페이지-상세-조회)
- [3. 프로젝트 페이지 목록 조회](#3-프로젝트-페이지-목록-조회)
- [4. 페이지 텍스트 조회](#4-페이지-텍스트-조회)
- [5. 페이지 텍스트 저장 (사용자 편집)](#5-페이지-텍스트-저장-사용자-편집)

---

## 엔드포인트 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/pages/upload` | 이미지 또는 PDF 업로드 |
| GET | `/api/pages/{page_id}` | 페이지 상세 조회 |
| GET | `/api/pages/project/{project_id}` | 프로젝트의 모든 페이지 조회 |
| GET | `/api/pages/{page_id}/text` | 페이지 텍스트 조회 (최신 버전) |
| POST | `/api/pages/{page_id}/text` | 사용자 편집 텍스트 저장 |

---

## 1. 페이지 업로드 (이미지/PDF)

이미지 파일 또는 PDF 파일을 업로드하여 페이지를 생성합니다.

### Endpoint

```http
POST /api/pages/upload
```

### Request

**Content-Type**: `multipart/form-data`

#### 이미지 업로드 (단일 페이지)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `project_id` | integer | ✅ | 프로젝트 ID |
| `page_number` | integer | ✅ | 페이지 번호 (1부터 시작) |
| `file` | file | ✅ | 이미지 파일 (PNG, JPG, JPEG) |

#### PDF 업로드 (다중 페이지 자동 생성)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `project_id` | integer | ✅ | 프로젝트 ID |
| `file` | file | ✅ | PDF 파일 |
| `page_number` | integer | ❌ | 시작 페이지 번호 (선택, 기본값: 자동 계산) |

### Response

#### 이미지 업로드 응답

**HTTP 201 Created**

```json
{
  "page_id": 1,
  "project_id": 1,
  "page_number": 1,
  "image_path": "uploads/project_1_page_1_abc123.png",
  "image_width": 2480,
  "image_height": 3508,
  "analysis_status": "pending",
  "processing_time": null,
  "created_at": "2025-01-22T10:31:00",
  "analyzed_at": null
}
```

#### PDF 업로드 응답

**HTTP 201 Created**

```json
{
  "project_id": 1,
  "total_created": 5,
  "source_type": "pdf",
  "pages": [
    {
      "page_id": 1,
      "project_id": 1,
      "page_number": 1,
      "image_path": "uploads/3/page_1.png",
      "image_width": 2480,
      "image_height": 3508,
      "analysis_status": "pending",
      "processing_time": null,
      "created_at": "2025-01-22T10:31:00",
      "analyzed_at": null
    },
    {
      "page_id": 2,
      "project_id": 1,
      "page_number": 2,
      "image_path": "uploads/3/page_2.png",
      "image_width": 2480,
      "image_height": 3508,
      "analysis_status": "pending",
      "processing_time": null,
      "created_at": "2025-01-22T10:31:01",
      "analyzed_at": null
    }
    // ... 나머지 페이지들
  ]
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `page_id` | integer | 생성된 페이지 고유 ID |
| `project_id` | integer | 소속 프로젝트 ID |
| `page_number` | integer | 페이지 번호 |
| `image_path` | string | 저장된 이미지 파일 경로 |
| `image_width` | integer | 이미지 너비 (픽셀) |
| `image_height` | integer | 이미지 높이 (픽셀) |
| `analysis_status` | string | 분석 상태 (`pending`, `processing`, `completed`, `error`) |
| `processing_time` | float | 처리 시간 (초, 분석 완료 후 설정) |
| `created_at` | datetime | 페이지 생성일시 |
| `analyzed_at` | datetime | 분석 완료일시 |

### 예제 코드

#### JavaScript - 이미지 업로드

```javascript
const uploadImage = async (projectId, pageNumber, imageFile) => {
  const formData = new FormData();
  formData.append('project_id', projectId);
  formData.append('page_number', pageNumber);
  formData.append('file', imageFile);
  
  const response = await fetch('http://localhost:8000/api/pages/upload', {
    method: 'POST',
    body: formData
  });
  
  if (!response.ok) {
    throw new Error(`이미지 업로드 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
const fileInput = document.getElementById('imageFile');
const imageFile = fileInput.files[0];

uploadImage(1, 1, imageFile)
  .then(page => {
    console.log('페이지 생성됨:', page.page_id);
    console.log('이미지 크기:', page.image_width, 'x', page.image_height);
  })
  .catch(error => console.error('업로드 실패:', error));
```

#### JavaScript - PDF 업로드

```javascript
const uploadPDF = async (projectId, pdfFile) => {
  const formData = new FormData();
  formData.append('project_id', projectId);
  formData.append('file', pdfFile);
  
  const response = await fetch('http://localhost:8000/api/pages/upload', {
    method: 'POST',
    body: formData
  });
  
  if (!response.ok) {
    throw new Error(`PDF 업로드 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
const fileInput = document.getElementById('pdfFile');
const pdfFile = fileInput.files[0];

uploadPDF(1, pdfFile)
  .then(result => {
    console.log(`PDF 업로드 완료: ${result.total_created}개 페이지 생성됨`);
    result.pages.forEach((page, index) => {
      console.log(`  페이지 ${index + 1}: ${page.image_path}`);
    });
  })
  .catch(error => console.error('업로드 실패:', error));
```

#### React Component - 파일 업로드

```jsx
import React, { useState } from 'react';
import axios from 'axios';

function FileUploader({ projectId, onUploadComplete }) {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  
  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    
    const formData = new FormData();
    formData.append('project_id', projectId);
    formData.append('file', file);
    
    // 이미지 파일인 경우 page_number 필요
    if (file.type.startsWith('image/')) {
      const pageNumber = prompt('페이지 번호를 입력하세요:');
      if (!pageNumber) return;
      formData.append('page_number', pageNumber);
    }
    
    setUploading(true);
    
    try {
      const response = await axios.post(
        'http://localhost:8000/api/pages/upload',
        formData,
        {
          headers: { 'Content-Type': 'multipart/form-data' },
          onUploadProgress: (progressEvent) => {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            setProgress(percentCompleted);
          }
        }
      );
      
      if (response.data.source_type === 'pdf') {
        alert(`${response.data.total_created}개 페이지가 생성되었습니다.`);
      } else {
        alert('페이지가 생성되었습니다.');
      }
      
      if (onUploadComplete) onUploadComplete(response.data);
      
    } catch (error) {
      console.error('업로드 실패:', error);
      alert('파일 업로드 실패: ' + error.message);
    } finally {
      setUploading(false);
      setProgress(0);
    }
  };
  
  return (
    <div>
      <input
        type="file"
        accept=".pdf,.png,.jpg,.jpeg"
        onChange={handleFileUpload}
        disabled={uploading}
      />
      {uploading && (
        <div>
          <progress value={progress} max="100" />
          <span>{progress}%</span>
        </div>
      )}
    </div>
  );
}
```

---

## 2. 페이지 상세 조회

특정 페이지의 상세 정보를 조회합니다.

### Endpoint

```http
GET /api/pages/{page_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page_id` | integer | 조회할 페이지 ID |

### Response

**HTTP 200 OK**

```json
{
  "page_id": 1,
  "project_id": 1,
  "page_number": 1,
  "image_path": "uploads/project_1_page_1_abc123.png",
  "image_width": 2480,
  "image_height": 3508,
  "analysis_status": "completed",
  "processing_time": 5.23,
  "created_at": "2025-01-22T10:31:00",
  "analyzed_at": "2025-01-22T10:35:00"
}
```

### 예제 코드

```javascript
const getPageDetail = async (pageId) => {
  const response = await fetch(`http://localhost:8000/api/pages/${pageId}`);
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('페이지를 찾을 수 없습니다.');
    }
    throw new Error(`페이지 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getPageDetail(1).then(page => {
  console.log('페이지:', page.page_number);
  console.log('분석 상태:', page.analysis_status);
  console.log('처리 시간:', page.processing_time, '초');
});
```

---

## 3. 프로젝트 페이지 목록 조회

프로젝트에 속한 모든 페이지를 조회합니다.

### Endpoint

```http
GET /api/pages/project/{project_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 프로젝트 ID |

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `include_error` | boolean | ❌ | 에러 상태 페이지 포함 여부 (기본값: false) |

### Response

**HTTP 200 OK**

```json
[
  {
    "page_id": 1,
    "project_id": 1,
    "page_number": 1,
    "image_path": "uploads/project_1_page_1_abc123.png",
    "image_width": 2480,
    "image_height": 3508,
    "analysis_status": "completed",
    "processing_time": 5.23,
    "created_at": "2025-01-22T10:31:00",
    "analyzed_at": "2025-01-22T10:35:00"
  },
  {
    "page_id": 2,
    "project_id": 1,
    "page_number": 2,
    "image_path": "uploads/project_1_page_2_def456.png",
    "image_width": 2480,
    "image_height": 3508,
    "analysis_status": "completed",
    "processing_time": 4.87,
    "created_at": "2025-01-22T10:32:00",
    "analyzed_at": "2025-01-22T10:36:00"
  }
]
```

### 예제 코드

```javascript
const getProjectPages = async (projectId, includeError = false) => {
  const params = new URLSearchParams({ include_error: includeError });
  const response = await fetch(
    `http://localhost:8000/api/pages/project/${projectId}?${params}`
  );
  
  if (!response.ok) {
    throw new Error(`페이지 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getProjectPages(1, false).then(pages => {
  console.log(`총 ${pages.length}개 페이지`);
  
  const completedPages = pages.filter(p => p.analysis_status === 'completed');
  const pendingPages = pages.filter(p => p.analysis_status === 'pending');
  
  console.log(`완료: ${completedPages.length}, 대기: ${pendingPages.length}`);
});
```

---

## 4. 페이지 텍스트 조회

페이지의 최신 텍스트 버전을 조회합니다. `is_current=True`인 버전이 반환됩니다.

### Endpoint

```http
GET /api/pages/{page_id}/text
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page_id` | integer | 페이지 ID |

### Response

**HTTP 200 OK**

```json
{
  "page_id": 1,
  "version_id": 3,
  "version_type": "user_edited",
  "is_current": true,
  "content": "<h2>1. 다음 식을 계산하시오.</h2>\n<p>(1) 3 + 5 = ?</p>\n<p>답: 8</p>",
  "created_at": "2025-01-22T11:00:00"
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `page_id` | integer | 페이지 ID |
| `version_id` | integer | 텍스트 버전 ID |
| `version_type` | string | 버전 유형<br>- `original`: 원본 OCR 결과<br>- `auto_formatted`: 자동 포맷팅 적용<br>- `user_edited`: 사용자 편집 |
| `is_current` | boolean | 현재 버전 여부 (항상 true) |
| `content` | string | HTML 형식의 텍스트 내용 |
| `created_at` | datetime | 버전 생성일시 |

### 예제 코드

```javascript
const getPageText = async (pageId) => {
  const response = await fetch(`http://localhost:8000/api/pages/${pageId}/text`);
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('페이지 텍스트를 찾을 수 없습니다.');
    }
    throw new Error(`텍스트 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getPageText(1).then(textData => {
  console.log('버전:', textData.version_type);
  console.log('내용:', textData.content);
  
  // HTML 표시
  document.getElementById('textEditor').innerHTML = textData.content;
});
```

#### React Component - 텍스트 뷰어

```jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function PageTextViewer({ pageId }) {
  const [textData, setTextData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    const fetchText = async () => {
      try {
        const response = await axios.get(
          `http://localhost:8000/api/pages/${pageId}/text`
        );
        setTextData(response.data);
      } catch (error) {
        console.error('텍스트 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    
    fetchText();
  }, [pageId]);
  
  if (loading) return <div>로딩 중...</div>;
  if (!textData) return <div>텍스트를 찾을 수 없습니다.</div>;
  
  return (
    <div>
      <div className="text-meta">
        <span>버전: {textData.version_type}</span>
        <span>생성: {new Date(textData.created_at).toLocaleString()}</span>
      </div>
      <div 
        className="text-content"
        dangerouslySetInnerHTML={{ __html: textData.content }}
      />
    </div>
  );
}
```

---

## 5. 페이지 텍스트 저장 (사용자 편집)

사용자가 편집한 텍스트를 새로운 버전으로 저장합니다.

### Endpoint

```http
POST /api/pages/{page_id}/text
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page_id` | integer | 페이지 ID |

### Request Body

```json
{
  "content": "<h2>1. 다음 식을 계산하시오.</h2>\n<p>(1) 3 + 5 = ?</p>\n<p>답: 8</p>",
  "user_id": 1
}
```

**필드 설명**:

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `content` | string | ✅ | 저장할 텍스트 내용 (HTML 형식) |
| `user_id` | integer | ❌ | 수정한 사용자 ID (선택) |

### Response

**HTTP 200 OK**

```json
{
  "page_id": 1,
  "version_id": 4,
  "version_type": "user_edited",
  "is_current": true,
  "content": "<h2>1. 다음 식을 계산하시오.</h2>\n<p>(1) 3 + 5 = ?</p>\n<p>답: 8</p>",
  "created_at": "2025-01-22T11:10:00"
}
```

### 동작 방식

1. 새로운 텍스트 버전을 생성 (`version_type="user_edited"`)
2. 기존의 `is_current=True` 버전을 `False`로 변경
3. 새 버전을 `is_current=True`로 설정
4. 버전 번호 자동 증가

### 예제 코드

```javascript
const savePageText = async (pageId, content, userId = null) => {
  const response = await fetch(`http://localhost:8000/api/pages/${pageId}/text`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      content: content,
      user_id: userId
    })
  });
  
  if (!response.ok) {
    throw new Error(`텍스트 저장 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
const editorContent = document.getElementById('textEditor').innerHTML;

savePageText(1, editorContent, 1)
  .then(result => {
    console.log('텍스트 저장 완료');
    console.log('새 버전 ID:', result.version_id);
    alert('저장되었습니다.');
  })
  .catch(error => {
    console.error('저장 실패:', error);
    alert('저장 실패: ' + error.message);
  });
```

#### React Component - TinyMCE 편집기

```jsx
import React, { useState, useEffect } from 'react';
import { Editor } from '@tinymce/tinymce-react';
import axios from 'axios';

function PageTextEditor({ pageId, userId }) {
  const [content, setContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [versionId, setVersionId] = useState(null);
  
  useEffect(() => {
    const fetchText = async () => {
      try {
        const response = await axios.get(
          `http://localhost:8000/api/pages/${pageId}/text`
        );
        setContent(response.data.content);
        setVersionId(response.data.version_id);
      } catch (error) {
        console.error('텍스트 로드 실패:', error);
      }
    };
    
    fetchText();
  }, [pageId]);
  
  const handleSave = async () => {
    setSaving(true);
    
    try {
      const response = await axios.post(
        `http://localhost:8000/api/pages/${pageId}/text`,
        {
          content: content,
          user_id: userId
        }
      );
      
      setVersionId(response.data.version_id);
      alert('저장되었습니다.');
      
    } catch (error) {
      console.error('저장 실패:', error);
      alert('저장 실패: ' + error.message);
    } finally {
      setSaving(false);
    }
  };
  
  return (
    <div>
      <div className="editor-header">
        <span>버전 ID: {versionId}</span>
        <button onClick={handleSave} disabled={saving}>
          {saving ? '저장 중...' : '저장'}
        </button>
      </div>
      
      <Editor
        apiKey="your-tinymce-api-key"
        value={content}
        onEditorChange={setContent}
        init={{
          height: 500,
          menubar: false,
          plugins: [
            'advlist', 'autolink', 'lists', 'link', 'image',
            'charmap', 'preview', 'anchor', 'searchreplace',
            'visualblocks', 'code', 'fullscreen',
            'insertdatetime', 'media', 'table', 'help', 'wordcount'
          ],
          toolbar: 'undo redo | formatselect | bold italic | ' +
            'alignleft aligncenter alignright | ' +
            'bullist numlist outdent indent | help'
        }}
      />
    </div>
  );
}
```

---

## 에러 응답

### 400 Bad Request

요청 데이터가 유효하지 않은 경우

```json
{
  "error": "이미지 업로드 시 page_number는 필수입니다.",
  "status_code": 400
}
```

### 404 Not Found

페이지를 찾을 수 없는 경우

```json
{
  "error": "페이지를 찾을 수 없습니다.",
  "status_code": 404
}
```

### 413 Payload Too Large

파일 크기가 너무 큰 경우 (일반적으로 50MB 제한)

```json
{
  "error": "파일 크기가 너무 큽니다.",
  "detail": "최대 50MB까지 업로드 가능합니다.",
  "status_code": 413
}
```

### 500 Internal Server Error

서버 내부 오류

```json
{
  "error": "Internal Server Error",
  "detail": "이미지 처리 중 오류가 발생했습니다.",
  "status_code": 500
}
```

---

## 다음 단계

- **[분석 API](./03_분석_API.md)**: 업로드한 페이지 분석하기
- **[다운로드 API](./04_다운로드_API.md)**: 전체 문서 다운로드
- **[데이터 모델](./05_데이터_모델.md)**: 페이지 스키마 상세 정보
