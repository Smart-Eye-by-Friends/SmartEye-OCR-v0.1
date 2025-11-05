# 🚀 SmartEyeSsen Backend 설정 가이드

팀원을 위한 개발 환경 설정 가이드입니다.

---

## 📋 **목차**

1. [시스템 요구사항](#1-시스템-요구사항)
2. [MySQL 설치 및 설정](#2-mysql-설치-및-설정)
3. [프로젝트 클론 및 설정](#3-프로젝트-클론-및-설정)
4. [데이터베이스 생성](#4-데이터베이스-생성)
5. [Python 환경 설정](#5-python-환경-설정)
6. [서버 실행](#6-서버-실행)
7. [트러블슈팅](#7-트러블슈팅)

---

## **1. 시스템 요구사항**

### **필수 프로그램**

- Python 3.9 이상
- MySQL 8.0 이상
- Git

### **운영체제별 지원**

- ✅ Windows 10/11
- ✅ Windows + WSL2 (Ubuntu 20.04/22.04)
- ✅ macOS
- ✅ Linux (Ubuntu/Debian)

---

## **2. MySQL 설치 및 설정**

#### **MySQL 서비스 시작**

```powershell
# PowerShell (관리자 권한)
Start-Service MySQL80
```

#### **MySQL 접속 테스트**

```powershell
mysql -u root -p
# 비밀번호 입력 후 접속되면 성공
```

---

### **2-2. Windows + WSL2 (Ubuntu)**

#### **WSL2 설치 (처음 사용하는 경우)**

```powershell
# PowerShell (관리자 권한)
wsl --install -d Ubuntu-22.04
```

재부팅 후 Ubuntu 사용자명/비밀번호 설정

#### **MySQL 설치 (WSL 내부)**

```bash
# WSL Ubuntu 터미널
sudo apt update
sudo apt install mysql-server -y

# MySQL 서비스 시작
sudo service mysql start

# Root 비밀번호 설정
sudo mysql
```

```sql
-- MySQL 콘솔에서
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'your_password';
FLUSH PRIVILEGES;
EXIT;
```

#### **MySQL 접속 테스트**

```bash
mysql -u root -p
# 비밀번호 입력 후 접속되면 성공
```

---

## **3. 프로젝트 클론 및 설정**

### **3-2. 환경 변수 설정**

```bash
# .env 파일 생성 (Backend 폴더 내)
cd Backend
cp .env.example .env
```

#### **`.env` 파일 수정**

텍스트 에디터로 `.env` 파일을 열고 다음 항목을 수정:

```ini
# 데이터베이스 설정
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_actual_password  # ⚠️ 여기에 실제 MySQL 비밀번호 입력
DB_NAME=smarteyessen_db

# OpenAI API
OPENAI_API_KEY=  # 이건 바꿔야돼!

# 서버 설정
API_HOST=0.0.0.0
API_PORT=8000
```

---

## **4. 데이터베이스 생성**

### **4-1. 스키마 적용 (데이터베이스 자동 생성)**

`erd_schema.sql` 파일에는 데이터베이스 생성부터 테이블 생성, 초기 데이터 삽입까지 모든 작업이 포함되어 있습니다.

```bash
# 프로젝트 루트 디렉토리에서 실행
# (SmartEye-FrontWeb 폴더)
mysql -u root -p < Project/DB/erd_schema.sql
```

### **4-4. 테이블 생성 확인**

```bash
mysql -u root -p smarteyessen_db
```

```sql
-- 12개 테이블 확인
SHOW TABLES;

-- 예상 결과:
-- +------------------------------+
-- | Tables_in_smarteyessen_db    |
-- +------------------------------+
-- | ai_descriptions              |
-- | combined_results             |
-- | document_types               |
-- | formatting_rules             |
-- | layout_elements              |
-- | pages                        |
-- | projects                     |
-- | question_elements            |
-- | question_groups              |
-- | text_contents                |
-- | text_versions                |
-- | users                        |
-- +------------------------------+

-- 초기 데이터 확인
SELECT * FROM document_types;
-- 2개 행이 조회되어야 함 (worksheet, document)

EXIT;
```

---

````

### **5-2. 의존성 설치**

```bash
# Backend 폴더로 이동
cd Backend

# 패키지 설치
pip install -r requirements.txt
````

---

## **6. 서버 실행**

### **6-1. 서버 시작**

#### **Windows (PowerShell) 관학 실행 방법**

```powershell
# pytorch 환경 활성화
conda activate pytorch

# Backend 폴더로 이동
cd Backend

# 서버 실행
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### **WSL/macOS/Linux 종영 실행방법**

# Backend 폴더로 이동

cd Backend

# 서버 실행

uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

```

### **6-2. 서버 실행 확인**

다음과 같은 출력이 나타나면 성공:

```

INFO: Will watch for changes in these directories: ['C:\\git\\Smart-Eye-OCR\\SmartEye-FrontWeb\\Backend']
INFO: Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO: Started reloader process [12345] using WatchFiles
INFO: Started server process [67890]
INFO: Waiting for application startup.
============================================================
🚀 SmartEyeSsen Backend Starting...
============================================================
✅ Database connection successful!
✅ Database connection successful
✅ Database tables created successfully!
✅ Database tables initialized
============================================================
✅ SmartEyeSsen Backend Ready!
📖 API Docs: http://localhost:8000/docs
============================================================
INFO: Application startup complete.

````

### **6-3. API 문서 접속**

브라우저에서 다음 URL로 접속:

- 🏠 **메인**: http://localhost:8000
- 📖 **API 문서 (Swagger UI)**: http://localhost:8000/docs
- 📚 **API 문서 (ReDoc)**: http://localhost:8000/redoc
- ❤️ **헬스 체크**: http://localhost:8000/health

**헬스 체크 응답 예시:**

```json
{
  "status": "healthy",
  "message": "SmartEyeSsen Backend is running",
  "database": "connected",
  "timestamp": "2025-01-22T15:30:00.123456"
}
````
