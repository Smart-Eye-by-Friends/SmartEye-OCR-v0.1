# 프로젝트 API

프로젝트는 문서 처리의 최상위 단위입니다. 하나의 프로젝트는 여러 페이지를 포함할 수 있으며, 각 프로젝트는 문서 타입(worksheet 또는 document)을 가집니다.

## 📖 목차

- [엔드포인트 목록](#엔드포인트-목록)
- [1. 프로젝트 생성](#1-프로젝트-생성)
- [2. 프로젝트 목록 조회](#2-프로젝트-목록-조회)
- [3. 프로젝트 상세 조회](#3-프로젝트-상세-조회)
- [4. 프로젝트 수정](#4-프로젝트-수정)
- [5. 프로젝트 삭제](#5-프로젝트-삭제)

---

## 엔드포인트 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/projects` | 새 프로젝트 생성 |
| GET | `/api/projects` | 프로젝트 목록 조회 (페이지네이션 지원) |
| GET | `/api/projects/{project_id}` | 프로젝트 상세 조회 (페이지 포함) |
| PATCH | `/api/projects/{project_id}` | 프로젝트 정보 수정 |
| DELETE | `/api/projects/{project_id}` | 프로젝트 삭제 (cascade) |

---

## 1. 프로젝트 생성

새로운 프로젝트를 생성합니다.

### Endpoint

```
POST /api/projects
```

### Request Body

```json
{
  "project_name": "수학 문제집 1단원",
  "doc_type_id": 1,
  "analysis_mode": "auto",
  "user_id": 1
}
```

**필드 설명**:

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `project_name` | string | ✅ | 프로젝트 이름 (1~255자) |
| `doc_type_id` | integer | ✅ | 문서 타입 ID<br>- `1`: worksheet (문제지)<br>- `2`: document (일반 문서) |
| `analysis_mode` | string | ❌ | 분석 모드 (기본값: `auto`)<br>- `auto`: 자동 분석<br>- `manual`: 수동 분석<br>- `hybrid`: 하이브리드 |
| `user_id` | integer | ✅ | 사용자 ID |

### Response

**HTTP 201 Created**

```json
{
  "project_id": 1,
  "user_id": 1,
  "doc_type_id": 1,
  "project_name": "수학 문제집 1단원",
  "total_pages": 0,
  "analysis_mode": "auto",
  "status": "created",
  "created_at": "2025-01-22T10:30:00",
  "updated_at": "2025-01-22T10:30:00"
}
```

**응답 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `project_id` | integer | 생성된 프로젝트 고유 ID |
| `user_id` | integer | 소유자 사용자 ID |
| `doc_type_id` | integer | 문서 타입 ID |
| `project_name` | string | 프로젝트 이름 |
| `total_pages` | integer | 총 페이지 수 (초기값: 0) |
| `analysis_mode` | string | 분석 모드 |
| `status` | string | 프로젝트 상태<br>- `created`: 생성됨<br>- `in_progress`: 진행 중<br>- `completed`: 완료<br>- `error`: 오류 |
| `created_at` | datetime | 생성일시 |
| `updated_at` | datetime | 수정일시 |

### 예제 코드

**JavaScript (fetch)**:

```javascript
const createProject = async (projectData) => {
  const response = await fetch('http://localhost:8000/api/projects', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      project_name: projectData.name,
      doc_type_id: projectData.docType,
      analysis_mode: 'auto',
      user_id: projectData.userId
    })
  });
  
  if (!response.ok) {
    throw new Error(`프로젝트 생성 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
createProject({
  name: '수학 문제집 1단원',
  docType: 1,
  userId: 1
}).then(project => {
  console.log('프로젝트 생성 완료:', project.project_id);
});
```

**React + Axios**:

```javascript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'http://localhost:8000',
});

const createProject = async (name, docTypeId, userId) => {
  try {
    const response = await apiClient.post('/api/projects', {
      project_name: name,
      doc_type_id: docTypeId,
      analysis_mode: 'auto',
      user_id: userId
    });
    return response.data;
  } catch (error) {
    console.error('프로젝트 생성 실패:', error.response?.data);
    throw error;
  }
};

// 사용 예시
createProject('수학 문제집 1단원', 1, 1)
  .then(project => console.log('생성됨:', project))
  .catch(error => console.error('에러:', error));
```

---

## 2. 프로젝트 목록 조회

사용자의 프로젝트 목록을 조회합니다. 페이지네이션을 지원합니다.

### Endpoint

```
GET /api/projects
```

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `user_id` | integer | ❌ | 특정 사용자의 프로젝트만 필터링 |
| `skip` | integer | ❌ | 건너뛸 개수 (기본값: 0) |
| `limit` | integer | ❌ | 조회할 개수 (기본값: 100, 최대: 1000) |

### Response

**HTTP 200 OK**

```json
[
  {
    "project_id": 1,
    "user_id": 1,
    "doc_type_id": 1,
    "project_name": "수학 문제집 1단원",
    "total_pages": 5,
    "analysis_mode": "auto",
    "status": "completed",
    "created_at": "2025-01-22T10:30:00",
    "updated_at": "2025-01-22T10:35:00"
  },
  {
    "project_id": 2,
    "user_id": 1,
    "doc_type_id": 2,
    "project_name": "역사 교과서",
    "total_pages": 10,
    "analysis_mode": "auto",
    "status": "in_progress",
    "created_at": "2025-01-22T11:00:00",
    "updated_at": "2025-01-22T11:05:00"
  }
]
```

### 예제 코드

**JavaScript (fetch)**:

```javascript
const getProjects = async (userId = null, skip = 0, limit = 100) => {
  const params = new URLSearchParams();
  if (userId) params.append('user_id', userId);
  params.append('skip', skip);
  params.append('limit', limit);
  
  const response = await fetch(`http://localhost:8000/api/projects?${params}`);
  
  if (!response.ok) {
    throw new Error(`프로젝트 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getProjects(1, 0, 20).then(projects => {
  console.log(`${projects.length}개 프로젝트 조회됨`);
  projects.forEach(p => {
    console.log(`- ${p.project_name} (${p.total_pages}페이지)`);
  });
});
```

**React Component 예제**:

```jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function ProjectList({ userId }) {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    const fetchProjects = async () => {
      try {
        const response = await axios.get('http://localhost:8000/api/projects', {
          params: { user_id: userId, limit: 50 }
        });
        setProjects(response.data);
      } catch (error) {
        console.error('프로젝트 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    
    fetchProjects();
  }, [userId]);
  
  if (loading) return <div>로딩 중...</div>;
  
  return (
    <div>
      <h2>내 프로젝트 ({projects.length}개)</h2>
      <ul>
        {projects.map(project => (
          <li key={project.project_id}>
            {project.project_name} - {project.total_pages}페이지
            <span className={`status-${project.status}`}>
              {project.status}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

---

## 3. 프로젝트 상세 조회

프로젝트의 상세 정보를 페이지 목록과 함께 조회합니다.

### Endpoint

```
GET /api/projects/{project_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 조회할 프로젝트 ID |

### Response

**HTTP 200 OK**

```json
{
  "project_id": 1,
  "user_id": 1,
  "doc_type_id": 1,
  "project_name": "수학 문제집 1단원",
  "total_pages": 3,
  "analysis_mode": "auto",
  "status": "completed",
  "created_at": "2025-01-22T10:30:00",
  "updated_at": "2025-01-22T10:35:00",
  "pages": [
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
}
```

### 예제 코드

**JavaScript (fetch)**:

```javascript
const getProjectDetail = async (projectId) => {
  const response = await fetch(`http://localhost:8000/api/projects/${projectId}`);
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('프로젝트를 찾을 수 없습니다.');
    }
    throw new Error(`프로젝트 조회 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
getProjectDetail(1).then(project => {
  console.log(`프로젝트: ${project.project_name}`);
  console.log(`페이지 수: ${project.pages.length}`);
  project.pages.forEach(page => {
    console.log(`- 페이지 ${page.page_number}: ${page.analysis_status}`);
  });
});
```

---

## 4. 프로젝트 수정

프로젝트 정보를 수정합니다.

### Endpoint

```
PATCH /api/projects/{project_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 수정할 프로젝트 ID |

### Request Body

모든 필드는 선택사항(optional)입니다. 수정하려는 필드만 포함하세요.

```json
{
  "project_name": "수학 문제집 1단원 (수정본)",
  "status": "completed"
}
```

**수정 가능한 필드**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `project_name` | string | 프로젝트 이름 (1~255자) |
| `doc_type_id` | integer | 문서 타입 ID |
| `analysis_mode` | string | 분석 모드 (`auto`, `manual`, `hybrid`) |
| `status` | string | 프로젝트 상태 (`created`, `in_progress`, `completed`, `error`) |

### Response

**HTTP 200 OK**

```json
{
  "project_id": 1,
  "user_id": 1,
  "doc_type_id": 1,
  "project_name": "수학 문제집 1단원 (수정본)",
  "total_pages": 3,
  "analysis_mode": "auto",
  "status": "completed",
  "created_at": "2025-01-22T10:30:00",
  "updated_at": "2025-01-22T14:20:00"
}
```

### 예제 코드

**JavaScript (fetch)**:

```javascript
const updateProject = async (projectId, updates) => {
  const response = await fetch(`http://localhost:8000/api/projects/${projectId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(updates)
  });
  
  if (!response.ok) {
    throw new Error(`프로젝트 수정 실패: ${response.status}`);
  }
  
  return await response.json();
};

// 사용 예시
updateProject(1, {
  project_name: '수학 문제집 1단원 (최종)',
  status: 'completed'
}).then(project => {
  console.log('프로젝트 수정 완료:', project);
});
```

---

## 5. 프로젝트 삭제

프로젝트를 삭제합니다. **프로젝트 삭제 시 관련된 모든 페이지, 레이아웃 요소, 텍스트 등이 함께 삭제됩니다 (CASCADE).**

### Endpoint

```
DELETE /api/projects/{project_id}
```

### Path Parameters

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `project_id` | integer | 삭제할 프로젝트 ID |

### Response

**HTTP 204 No Content**

응답 본문(body)이 없습니다.

### 예제 코드

**JavaScript (fetch)**:

```javascript
const deleteProject = async (projectId) => {
  const response = await fetch(`http://localhost:8000/api/projects/${projectId}`, {
    method: 'DELETE'
  });
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('프로젝트를 찾을 수 없습니다.');
    }
    throw new Error(`프로젝트 삭제 실패: ${response.status}`);
  }
  
  return true;
};

// 사용 예시 (확인 다이얼로그 포함)
const handleDeleteProject = async (projectId, projectName) => {
  const confirmed = confirm(`"${projectName}" 프로젝트를 삭제하시겠습니까?\n모든 페이지와 데이터가 함께 삭제됩니다.`);
  
  if (confirmed) {
    try {
      await deleteProject(projectId);
      alert('프로젝트가 삭제되었습니다.');
      // 목록 새로고침 등
    } catch (error) {
      alert('프로젝트 삭제 실패: ' + error.message);
    }
  }
};
```

**React Component 예제**:

```jsx
import React, { useState } from 'react';
import axios from 'axios';

function ProjectDeleteButton({ projectId, projectName, onDeleted }) {
  const [deleting, setDeleting] = useState(false);
  
  const handleDelete = async () => {
    if (!confirm(`"${projectName}"을(를) 삭제하시겠습니까?`)) {
      return;
    }
    
    setDeleting(true);
    
    try {
      await axios.delete(`http://localhost:8000/api/projects/${projectId}`);
      alert('프로젝트가 삭제되었습니다.');
      if (onDeleted) onDeleted(projectId);
    } catch (error) {
      console.error('삭제 실패:', error);
      alert('프로젝트 삭제 실패: ' + error.message);
    } finally {
      setDeleting(false);
    }
  };
  
  return (
    <button 
      onClick={handleDelete}
      disabled={deleting}
      className="btn btn-danger"
    >
      {deleting ? '삭제 중...' : '삭제'}
    </button>
  );
}
```

---

## 에러 응답

모든 프로젝트 API는 다음과 같은 에러 응답을 반환할 수 있습니다:

### 400 Bad Request

요청 데이터가 유효하지 않은 경우

```json
{
  "error": "Validation Error",
  "detail": "project_name은 1자 이상이어야 합니다.",
  "status_code": 400
}
```

### 404 Not Found

프로젝트를 찾을 수 없는 경우

```json
{
  "error": "프로젝트를 찾을 수 없습니다.",
  "status_code": 404
}
```

### 500 Internal Server Error

서버 내부 오류

```json
{
  "error": "Internal Server Error",
  "detail": "데이터베이스 연결 실패",
  "status_code": 500
}
```

자세한 에러 처리 방법은 [에러 처리 문서](./06_에러_처리.md)를 참고하세요.

---

## 다음 단계

- **[페이지 API](./02_페이지_API.md)**: 페이지 업로드 및 관리
- **[분석 API](./03_분석_API.md)**: 프로젝트 분석 실행
- **[데이터 모델](./05_데이터_모델.md)**: 프로젝트 스키마 상세 정보
