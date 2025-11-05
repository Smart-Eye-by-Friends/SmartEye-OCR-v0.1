# SmartEye OCR - Docker MySQL 완전 설정 가이드

> 💡 **한 번의 명령어로 MySQL 환경을 완전하게 구축하는 가이드**  
> 모든 테이블, 초기 데이터, 포맷팅 규칙이 자동으로 설정됩니다.

---

## 📌 개요

### 목적
- 다른 개발자가 프로젝트를 클론한 후 **최소한의 명령어로** MySQL 환경을 구축
- 수동 SQL 실행 없이 **완전 자동화된 DB 초기화**
- 일관된 개발 환경 보장

### 전제 조건
- ✅ Docker & Docker Compose 설치
- ✅ Git 저장소 클론 완료
- ✅ 포트 3308 사용 가능 (변경 가능)

### 주요 특징
| 항목 | 내용 |
|------|------|
| **DB 엔진** | MySQL 8.0 |
| **문자셋** | utf8mb4 (이모지, 다국어 지원) |
| **데이터베이스명** | `smarteyessen_db` |
| **포트** | 3308 (호스트) → 3306 (컨테이너) |
| **테이블 수** | 12개 (users, projects, pages, ...) |
| **초기 데이터** | document_types (2개), formatting_rules (25개+) |
| **combined_text** | LONGTEXT (최대 4GB) |

---

## 🚀 빠른 시작 (3분 완성)

### 방법 1: Docker Compose 자동 초기화 (권장)

```bash
# 1. Backend 디렉토리로 이동
cd Backend

# 2. MySQL 컨테이너 시작 (자동 초기화)
docker-compose up -d

# 3. 초기화 완료 대기 (30초)
sleep 30

# 4. 검증
docker exec -it smart_mysql mysql -u root -p1q2w3e4r -e "USE smarteyessen_db; SHOW TABLES;"
```

**자동 실행 내용:**
- ✅ MySQL 8.0 컨테이너 생성 및 시작
- ✅ `smarteyessen_db` 데이터베이스 생성
- ✅ 12개 테이블 자동 생성
- ✅ 초기 데이터 자동 삽입 (document_types, formatting_rules)
- ✅ 트리거 2개 자동 생성 (total_pages 자동 계산)

---

### 방법 2: 스크립트 사용 (기존 DB 초기화)

```bash
# 1. Backend 디렉토리로 이동
cd Backend

# 2. 초기화 스크립트 실행
bash scripts/reset_db.sh

# 3. 확인 프롬프트에 'yes' 입력
Database: smarteyessen_db
Continue? (yes/no): yes

# ✅ Database reset completed!
```

---

## 📋 상세 가이드

### 1. Docker Compose 구성

#### 파일 위치
```
Backend/
├── docker-compose.yml       # Docker Compose 설정
├── .env.docker             # 환경 변수 (선택사항)
└── scripts/
    └── init_db_complete.sql # 초기화 SQL 스크립트
```

#### docker-compose.yml 주요 설정

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: smart_mysql
    
    environment:
      MYSQL_ROOT_PASSWORD: 1q2w3e4r
      MYSQL_DATABASE: smarteyessen_db
    
    ports:
      - "3308:3306"  # 호스트:컨테이너
    
    volumes:
      - smart_mysql_data:/var/lib/mysql
      # 🔑 핵심: 초기화 스크립트 자동 실행
      - ./scripts/init_db_complete.sql:/docker-entrypoint-initdb.d/01_init.sql:ro
```

**핵심 포인트:**
- `docker-entrypoint-initdb.d/`: MySQL 컨테이너가 **최초 실행 시** 이 디렉토리의 `.sql` 파일을 자동 실행
- `:ro` (read-only): 컨테이너에서 파일 수정 방지

---

### 2. 데이터베이스 초기화

#### init_db_complete.sql 구성

```sql
-- 1. 데이터베이스 생성
DROP DATABASE IF EXISTS smarteyessen_db;
CREATE DATABASE smarteyessen_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smarteyessen_db;

-- 2. 12개 테이블 생성
CREATE TABLE users (...);
CREATE TABLE document_types (...);
CREATE TABLE projects (...);
CREATE TABLE pages (...);
CREATE TABLE layout_elements (...);
CREATE TABLE text_contents (...);
CREATE TABLE ai_descriptions (...);
CREATE TABLE question_groups (...);
CREATE TABLE question_elements (...);
CREATE TABLE text_versions (...);
CREATE TABLE formatting_rules (...);
CREATE TABLE combined_results (
    combined_text LONGTEXT NOT NULL COMMENT '통합된 전체 텍스트 (최대 4GB)',
    ...
);

-- 3. 트리거 생성 (total_pages 자동 계산)
CREATE TRIGGER trg_update_total_pages ...;
CREATE TRIGGER trg_update_total_pages_on_delete ...;

-- 4. 초기 데이터 삽입
INSERT INTO document_types (type_name, sorting_method, description) VALUES
('worksheet', 'question_based', '시험 문제지'),
('document', 'reading_order', '일반 문서');

INSERT INTO formatting_rules (...) VALUES
-- question_based용 규칙 17개
-- reading_order용 규칙 10개
(...);
```

**특징:**
- ✅ 모든 테이블 정의 포함
- ✅ 외래 키 제약조건 설정
- ✅ 인덱스 최적화
- ✅ 초기 데이터 자동 삽입
- ✅ LONGTEXT 적용 (combined_text)

---

### 3. 검증 방법

#### 테이블 생성 확인

```bash
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db -e "SHOW TABLES;"
```

**예상 결과:**
```
+----------------------------+
| Tables_in_smarteyessen_db  |
+----------------------------+
| ai_descriptions            |
| combined_results           |
| document_types             |
| formatting_rules           |
| layout_elements            |
| pages                      |
| projects                   |
| question_elements          |
| question_groups            |
| text_contents              |
| text_versions              |
| users                      |
+----------------------------+
12 rows in set
```

#### 초기 데이터 확인

```bash
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db -e "
SELECT 'Document Types' as Category, COUNT(*) as Count FROM document_types
UNION ALL
SELECT 'Formatting Rules', COUNT(*) FROM formatting_rules;
"
```

**예상 결과:**
```
+-------------------+-------+
| Category          | Count |
+-------------------+-------+
| Document Types    |     2 |
| Formatting Rules  |    27 |
+-------------------+-------+
```

#### combined_text 컬럼 타입 확인

```bash
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db -e "
SELECT COLUMN_TYPE 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'combined_results' AND COLUMN_NAME = 'combined_text';
"
```

**예상 결과:**
```
+-------------+
| COLUMN_TYPE |
+-------------+
| longtext    |
+-------------+
```

#### 백엔드 연결 테스트

```bash
# 1. Backend 디렉토리로 이동
cd Backend

# 2. .env 파일 생성 (없는 경우)
cp .env.example .env

# 3. .env 파일 수정
DB_HOST=localhost
DB_PORT=3308
DB_USER=root
DB_PASSWORD=1q2w3e4r
DB_NAME=smarteyessen_db

# 4. 백엔드 서버 시작
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 5. Health Check
curl http://localhost:8000/health
```

**예상 응답:**
```json
{
  "status": "healthy",
  "database": "connected",
  "timestamp": "2025-11-05T14:30:00"
}
```

---

## 🔧 고급 사용법

### DB 완전 초기화 (모든 데이터 삭제)

#### 방법 1: 스크립트 사용 (권장)

```bash
cd Backend
bash scripts/reset_db.sh
```

**장점:**
- ✅ 안전 확인 프롬프트
- ✅ 자동 검증
- ✅ 상세한 로그

#### 방법 2: Docker 볼륨 재생성

```bash
# 1. 컨테이너 및 볼륨 삭제
docker-compose down -v

# 2. 재시작 (자동 초기화)
docker-compose up -d

# 3. 초기화 완료 대기
sleep 30
```

**주의:**
- ⚠️ `-v` 옵션은 **모든 데이터**를 삭제합니다
- ⚠️ 백업 없이 실행하지 마세요

#### 방법 3: MySQL Workbench 사용

```sql
-- MySQL Workbench에서 실행
SOURCE /home/jongyoung3/SmartEye-OCR-v0.1/Backend/scripts/init_db_complete.sql;
```

---

### 수동 초기화 (세밀한 제어)

#### 테이블만 생성 (데이터 제외)

```bash
# erd_schema.sql에서 INSERT 문을 제외하고 실행
docker exec -i smart_mysql mysql -u root -p1q2w3e4r < Backend/scripts/DB/erd_schema.sql
```

#### 테스트 사용자 추가

```bash
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db -e "
INSERT INTO users (email, name, role, password_hash) VALUES
('test@smarteyessen.com', '테스트 사용자', 'user', 'dummy_hash_for_test'),
('admin@smarteyessen.com', '관리자', 'admin', 'dummy_hash_for_admin')
ON DUPLICATE KEY UPDATE email=VALUES(email);
"
```

#### 특정 테이블만 초기화

```sql
-- formatting_rules만 재생성
TRUNCATE TABLE formatting_rules;

-- init_db_complete.sql에서 해당 INSERT 문만 복사하여 실행
INSERT INTO formatting_rules (...) VALUES (...);
```

---

## 🐛 문제 해결

### 1. 컨테이너가 시작되지 않음

**증상:**
```bash
docker ps -a
# smart_mysql 상태가 "Exited" 또는 보이지 않음
```

**원인:**
- 포트 충돌 (3308 이미 사용 중)
- 볼륨 권한 문제

**해결:**

```bash
# 1. 포트 사용 확인
lsof -i :3308

# 2. 다른 포트 사용
# docker-compose.yml 수정: "3309:3306"
docker-compose down
docker-compose up -d

# 3. 로그 확인
docker-compose logs mysql
```

---

### 2. 테이블이 생성되지 않음

**증상:**
```bash
SHOW TABLES;
# Empty set
```

**원인:**
- 볼륨이 이미 존재 (이전 데이터)
- init_db_complete.sql이 실행되지 않음

**해결:**

```bash
# 1. 볼륨 삭제 후 재시작
docker-compose down -v
docker-compose up -d

# 2. 수동 초기화
bash scripts/reset_db.sh

# 3. 로그 확인
docker logs smart_mysql | grep "init_db_complete.sql"
```

---

### 3. 백엔드 연결 실패

**증상:**
```
sqlalchemy.exc.OperationalError: (2003, "Can't connect to MySQL server")
```

**원인:**
- .env 파일 설정 오류
- 컨테이너가 실행 중이지 않음

**해결:**

```bash
# 1. 컨테이너 상태 확인
docker ps | grep smart_mysql

# 2. .env 파일 확인
cat .env | grep DB_

# 3. MySQL 접속 테스트
docker exec -it smart_mysql mysql -u root -p1q2w3e4r -e "SELECT 1;"
```

---

### 4. combined_text 크기 초과 오류

**증상:**
```
DataError: (1406, "Data too long for column 'combined_text' at row 1")
```

**원인:**
- 이전 버전에서 TEXT 타입 사용 (65KB 제한)
- init_db_complete.sql이 적용되지 않음

**해결:**

```bash
# 1. 컬럼 타입 확인
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db -e "
SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'combined_results' AND COLUMN_NAME = 'combined_text';
"

# 2. LONGTEXT가 아니면 DB 재초기화
bash scripts/reset_db.sh
```

---

### 5. 포맷팅 규칙이 없음

**증상:**
```sql
SELECT COUNT(*) FROM formatting_rules;
-- 0
```

**원인:**
- init_db_complete.sql의 INSERT 문이 실행되지 않음

**해결:**

```bash
# 1. 스크립트 재실행
bash scripts/reset_db.sh

# 2. 수동 삽입 (임시)
docker exec -i smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db < Backend/scripts/init_db_complete.sql
```

---

## 📚 참고 자료

### 파일 구조

```
Backend/
├── docker-compose.yml              # Docker 설정
├── .env.example                    # 환경 변수 템플릿
├── .env                           # 실제 환경 변수 (git 제외)
│
├── scripts/
│   ├── init_db_complete.sql       # 🔑 통합 초기화 스크립트
│   ├── reset_db.sh                # 초기화 스크립트 (Docker 기반)
│   ├── init_db.sql                # 기존 초기화 (deprecated)
│   ├── fix_combined_text_column.sql # 마이그레이션 전용
│   └── DB/
│       └── erd_schema.sql         # 원본 스키마 (참고용)
│
└── docs/
    └── 백엔드 환경 설정/
        └── Docker_MySQL_완전_설정_가이드.md  # 이 문서
```

---

### SQL 스크립트 설명

#### init_db_complete.sql
- **목적:** 한 번에 모든 테이블과 초기 데이터 설정
- **내용:**
  - DROP DATABASE (기존 삭제)
  - CREATE DATABASE
  - 12개 테이블 생성
  - 트리거 2개 생성
  - 초기 데이터 삽입
- **실행 시점:** 
  - Docker 컨테이너 최초 실행 (자동)
  - `reset_db.sh` 실행 (수동)

#### reset_db.sh
- **목적:** Docker 환경에서 DB 재초기화
- **기능:**
  - 컨테이너 상태 확인
  - 사용자 확인 프롬프트
  - init_db_complete.sql 실행
  - 결과 검증
- **사용 시점:** 
  - 개발 중 DB 리셋 필요 시
  - 테스트 데이터 정리

#### fix_combined_text_column.sql
- **목적:** 기존 DB의 combined_text를 LONGTEXT로 변경
- **사용 시점:** 
  - 이미 운영 중인 DB 마이그레이션
  - 데이터 손실 없이 컬럼 타입만 변경

---

### 환경 변수 설명

#### .env 파일 (Backend 연결용)

```bash
# MySQL 연결 정보
DB_HOST=localhost          # Docker 외부에서 접속: localhost
DB_PORT=3308              # Docker Compose에서 매핑한 포트
DB_USER=root              # MySQL 사용자
DB_PASSWORD=1q2w3e4r      # MySQL 비밀번호
DB_NAME=smarteyessen_db   # 데이터베이스 이름

# FastAPI 설정
API_HOST=0.0.0.0
API_PORT=8000
API_RELOAD=True
```

#### .env.docker 파일 (Docker Compose용, 선택사항)

```bash
MYSQL_ROOT_PASSWORD=1q2w3e4r
MYSQL_DATABASE=smarteyessen_db
MYSQL_PORT=3308
```

---

### 데이터베이스 스키마 요약

| 테이블 | 설명 | 주요 컬럼 |
|--------|------|----------|
| users | 사용자 정보 | user_id, email, role |
| document_types | 문서 타입 | doc_type_id, type_name, sorting_method |
| projects | 프로젝트 | project_id, user_id, doc_type_id |
| pages | 페이지 | page_id, project_id, image_path |
| layout_elements | 레이아웃 요소 | element_id, page_id, class_name, bbox |
| text_contents | OCR 텍스트 | text_id, element_id, ocr_text |
| ai_descriptions | AI 설명 | ai_desc_id, element_id, description |
| question_groups | 문제 그룹 | question_group_id, page_id, anchor_element_id |
| question_elements | 문제-요소 매핑 | qe_id, question_group_id, element_id |
| text_versions | 텍스트 버전 | version_id, page_id, version_type, content |
| formatting_rules | 포맷팅 규칙 | rule_id, doc_type_id, class_name, prefix, suffix |
| combined_results | 통합 결과 캐시 | combined_id, project_id, combined_text (LONGTEXT) |

---

### 자주 사용하는 명령어

```bash
# 컨테이너 시작
docker-compose up -d

# 컨테이너 중지
docker-compose down

# 컨테이너 + 볼륨 삭제
docker-compose down -v

# 로그 확인
docker-compose logs -f mysql

# MySQL 접속
docker exec -it smart_mysql mysql -u root -p1q2w3e4r smarteyessen_db

# DB 초기화
bash scripts/reset_db.sh

# 백엔드 서버 시작
uvicorn app.main:app --reload
```

---

## 🎯 체크리스트

개발 환경 설정 완료 확인:

- [ ] Docker & Docker Compose 설치됨
- [ ] `docker-compose up -d` 실행 완료
- [ ] `SHOW TABLES;` 결과 12개 테이블 확인
- [ ] `SELECT COUNT(*) FROM document_types;` 결과: 2
- [ ] `SELECT COUNT(*) FROM formatting_rules;` 결과: 27
- [ ] `combined_text` 컬럼 타입: LONGTEXT
- [ ] `.env` 파일 설정 완료
- [ ] `uvicorn app.main:app --reload` 실행 가능
- [ ] `curl http://localhost:8000/health` 응답 정상

---

## 📞 추가 문의

- **이슈 리포트:** GitHub Issues
- **문서 버전:** v2.1 (2025-11-05)
- **최종 업데이트:** 2025-11-05

---

> ✅ **완료!**  
> 이제 다른 개발자가 `git clone` → `docker-compose up -d` 한 번으로 동일한 환경을 구축할 수 있습니다.
