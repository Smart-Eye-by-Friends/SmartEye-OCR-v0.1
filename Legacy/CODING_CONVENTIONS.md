# SmartEye-OCR 팀 코딩 컨벤션

## 📁 폴더/파일 네이밍 규칙

### ✅ 대문자로 시작하는 폴더

```
Backend/        # 백엔드 FastAPI 애플리케이션
Frontend/       # 프론트엔드 React 애플리케이션
Project/        # 프로젝트 설정 및 DB 스키마
Initial Commit/ # 초기 커밋 자료
```

### ✅ 소문자 폴더 (하위 디렉토리)

```
Backend/app/           # 애플리케이션 코드
Backend/app/routers/   # API 라우터
Frontend/src/          # 소스 코드
Frontend/src/components/ # React 컴포넌트
```

### ✅ 파일명 규칙

```python
# Python: snake_case
models.py
database.py
api_server.py

# JavaScript/React: PascalCase (컴포넌트), camelCase (유틸)
App.jsx
ImageLoader.jsx
apiService.js
dataUtils.js

# SQL: snake_case
erd_schema.sql
```

---

## 🔧 Git 사용 규칙

### 커밋 전 확인사항

```bash
# 1. 올바른 경로 확인
git status

# 2. 대문자 폴더명 확인
Backend/app/models.py  # ✅
backend/app/models.py  # ❌

# 3. 스테이징
git add Backend/app/models.py
git add Frontend/src/App.jsx

# 4. 커밋
git commit -m "feat: ERD v2 기준 models.py 재작성"
```

---

## 🐍 Python 코딩 스타일

### PEP 8 준수

```python
# 함수/변수: snake_case
def get_user_by_id(user_id: int):
    pass

# 클래스: PascalCase
class UserModel:
    pass

# 상수: UPPER_SNAKE_CASE
MAX_RETRY_COUNT = 3
```

---

## 🗄️ 데이터베이스 네이밍

### 테이블명: snake_case 소문자

```sql
users
document_types
layout_elements
question_groups
```

### 컬럼명: snake_case 소문자

```sql
user_id
created_at
analysis_status
anchor_element_id
```

---

## 📝 커밋 메시지 규칙

### 형식

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `docs`: 문서 변경
- `style`: 코드 스타일 (포맷팅, 세미콜론 등)
- `refactor`: 리팩토링
- `test`: 테스트 추가
- `chore`: 빌드/설정 변경

### 예시

```bash
git commit -m "feat(models): ERD v2 기준 SQLAlchemy 모델 재작성"
git commit -m "fix(api): 페이지 분석 상태 업데이트 오류 수정"
git commit -m "docs(readme): 설치 가이드 추가"
```

---

## 🚀 브랜치 전략

### 브랜치명 규칙

```
main                    # 운영 브랜치
develop                 # 개발 통합 브랜치
feature/<기능명>        # 기능 개발
fix/<버그명>           # 버그 수정
hotfix/<긴급수정명>    # 긴급 수정
```

### 예시

```bash
git checkout -b feature/anchor-based-sorting
git checkout -b fix/layout-detection-error
git checkout -b hotfix/db-connection-timeout
```

---

## 📦 의존성 관리

### Python (Backend)

```bash
# 패키지 설치 후 requirements.txt 업데이트
pip freeze > Backend/requirements.txt
```

### JavaScript (Frontend)

```bash
# package.json 자동 업데이트
npm install <package-name>
```

---

## 🔒 보안 규칙

### 환경 변수 사용

```python
# ✅ 올바른 방법
from dotenv import load_dotenv
import os

load_dotenv()
api_key = os.getenv("OPENAI_API_KEY")

# ❌ 잘못된 방법 (하드코딩)
api_key = "sk-abc123..."  # 절대 금지!
```

### .env 파일은 Git 추적 제외

```bash
# .gitignore에 포함
.env
.env.local
*.key
```

---

## 📚 문서화 규칙

### Python Docstring (Google Style)

```python
def analyze_page(page_id: int, mode: str = "auto") -> dict:
    """페이지 레이아웃 분석

    Args:
        page_id (int): 분석할 페이지 ID
        mode (str): 분석 모드 ('auto', 'manual', 'hybrid')

    Returns:
        dict: 분석 결과
        {
            'status': 'completed',
            'elements': [...],
            'processing_time': 1.23
        }

    Raises:
        ValueError: 유효하지 않은 page_id
        DatabaseError: DB 연결 실패
    """
    pass
```

---

## ✅ 체크리스트 (PR 전)

- [ ] 코드 스타일 확인 (PEP 8, ESLint)
- [ ] 테스트 작성/통과
- [ ] 문서화 업데이트
- [ ] 의존성 파일 업데이트
- [ ] `.env.example` 업데이트 (새 환경변수 추가 시)
- [ ] 커밋 메시지 규칙 준수
- [ ] 대문자 폴더명 확인 (`Backend/`, `Frontend/`)

---

**최종 업데이트**: 2025-01-22  
**작성자**: SmartEye-OCR Team
