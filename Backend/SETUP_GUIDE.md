# 🎯 SmartEye 백엔드 실행 가이드

**마이크로서비스 아키텍처 기반 학습지 분석 시스템 설정 가이드**

## 🚀 빠른 실행

### 🛠️ 개발환경 (권장 - 70% 빠름!)

**루트 디렉토리 스크립트 사용:**
```bash
# 1. 개발 환경 시작 (PostgreSQL + LAM Service + 가이드)
./start_dev.sh

# 2. 별도 터미널에서 Backend 시작
📟 터미널 1: Backend 시작
cd Backend/smarteye-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

📱 터미널 2: Frontend 시작 (옵션)
cd Frontend && npm start

# 3. 개발 환경 중지
./stop_dev.sh
```

**Backend 디렉토리 직접 사용:**
```bash
cd Backend
docker-compose -f docker-compose-dev.yml up -d
```

### 🏭 프로덕션 환경 (전체 시스템)

**루트 디렉토리 스크립트 사용:**
```bash
# 전체 시스템 시작
./start_system.sh

# 시스템 상태 확인
./check_system.sh

# 전체 시스템 중지
./stop_system.sh
```

**Backend 디렉토리 직접 사용:**
```bash
cd Backend
docker-compose up -d
```

## 🔧 환경 설정

### 🏗️ 마이크로서비스 구성

**서비스 포트 정보:**
- **SmartEye Backend (Java)**: 8080
- **LAM Service (Python)**: 8001
- **PostgreSQL Database**: 5433
- **Nginx Proxy**: 80/443 (프로덕션)

**Docker 컨테이너 이름:**
- `smarteye-backend`: Java Spring Boot 애플리케이션
- `smarteye-lam-service`: Python FastAPI LAM 서비스
- `smarteye-postgres`: PostgreSQL 데이터베이스
- `smarteye-nginx`: Nginx 리버스 프록시

### 🔤 Tesseract OCR 설정
모든 환경에서 Tesseract OCR이 올바르게 작동하도록 다음 환경변수가 자동 설정됩니다:

**Linux/WSL 환경:**
- `TESSERACT_DATAPATH=/usr/share/tesseract-ocr/5/tessdata`
- `TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata`

**Docker 환경:**
- `TESSERACT_DATAPATH=/usr/share/tessdata`
- `TESSDATA_PREFIX=/usr/share/tessdata`

### ⚙️ 개발자 커스텀 설정

**Backend 설정 (smarteye-backend/.env.example):**
```bash
# Database
DB_URL=jdbc:postgresql://localhost:5433/smarteye_db
DB_USERNAME=smarteye
DB_PASSWORD=smarteye_password

# LAM Service
LAM_SERVICE_URL=http://localhost:8001
LAM_SERVICE_ENABLED=true

# File Storage
UPLOAD_DIR=./dev-uploads
STATIC_DIR=./static
TEMP_DIR=./dev-temp

# Tesseract OCR
TESSERACT_PATH=/usr/bin/tesseract
TESSERACT_DATAPATH=/usr/share/tesseract-ocr/5/tessdata

# Spring Profiles
SPRING_PROFILES_ACTIVE=dev
```

**환경변수 로드 및 실행:**
```bash
cd Backend/smarteye-backend

# .env 파일 생성
cp .env.example .env

# 필요시 설정 수정
vim .env

# 환경변수 로드 후 실행
source .env
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 🐛 문제 해결

### 🔤 Tesseract OCR 오류
만약 Tesseract 관련 오류가 발생하면:

1. **시스템 확인**
   ```bash
   # 설치된 언어 확인
   tesseract --list-langs

   # 언어 데이터 파일 확인
   ls -la /usr/share/tesseract-ocr/5/tessdata/

   # 한국어 팩이 없으면 설치
   sudo apt-get install tesseract-ocr-kor
   ```

2. **수동 환경변수 설정**
   ```bash
   export TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
   cd Backend/smarteye-backend
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

3. **Docker 컨테이너에서 확인**
   ```bash
   # 컨테이너 내부 접속
   docker exec -it smarteye-backend sh

   # Tesseract 설정 확인
   ls -la /usr/share/tessdata/
   echo $TESSDATA_PREFIX
   tesseract --list-langs
   ```

### 🐳 Docker 관련 문제

1. **포트 충돌 문제**
   ```bash
   # 사용 중인 포트 확인
   sudo lsof -i :8080
   sudo lsof -i :8001
   sudo lsof -i :5433

   # 프로세스 강제 종료
   sudo kill -9 <PID>
   ```

2. **Docker 볼륨 권한 문제**
   ```bash
   # 업로드 디렉토리 권한 설정
   sudo chown -R $USER:$USER Backend/smarteye-backend/dev-uploads
   sudo chown -R $USER:$USER Backend/smarteye-backend/static
   ```

3. **LAM Service 연결 문제**
   ```bash
   # LAM Service 상태 확인
   curl http://localhost:8001/health

   # LAM Service 로그 확인
   docker logs -f smarteye-lam-service

   # LAM Service 재시작
   docker restart smarteye-lam-service
   ```

### 🤖 LAM Service 문제

1. **ML 모델 다운로드 실패**
   ```bash
   # LAM Service 로그 확인
   docker logs smarteye-lam-service

   # 모델 캐시 초기화
   docker exec -it smarteye-lam-service rm -rf /app/models/*
   docker restart smarteye-lam-service
   ```

2. **GPU 메모리 부족 (CPU 모드로 강제 실행)**
   ```bash
   # CUDA 비활성화
   export CUDA_VISIBLE_DEVICES=""
   docker restart smarteye-lam-service
   ```

### 💾 데이터베이스 연결 문제

1. **PostgreSQL 연결 테스트**
   ```bash
   # 컨테이너 내부 접속
   docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db

   # 테이블 확인
   \dt
   ```

2. **데이터베이스 재초기화**
   ```bash
   # 모든 데이터 삭제 후 재시작
   docker-compose down -v
   docker-compose up -d
   ```

### 📊 환경별 차이점
| 구분 | 개발환경 | 프로덕션 |
|------|----------|----------|
| **실행방식** | 네이티브 (./gradlew bootRun) | Docker 컨테이너 |
| **데이터베이스** | localhost:5433 | postgres:5432 |
| **LAM Service** | localhost:8001 | lam-service:8001 |
| **Tesseract 경로** | 시스템 기본값 | Docker 컨테이너 내부 |
| **업로드 디렉토리** | ./dev-uploads | /app/uploads |
| **로그 레벨** | DEBUG | INFO |
| **Spring Profile** | dev | prod |

## ✅ 검증 방법

### 1. 📊 시스템 상태 확인
```bash
# 전체 시스템 상태
curl http://localhost:8080/api/health

# 데이터베이스 연결 상태
curl http://localhost:8080/api/health/database

# LAM Service 연결 상태
curl http://localhost:8080/api/health/lam-service

# LAM Service 직접 확인
curl http://localhost:8001/health
```

### 2. 🔍 Docker 서비스 상태 확인
```bash
# 컨테이너 상태 확인
docker-compose ps

# 각 서비스 헬스체크
docker exec smarteye-backend curl -f http://localhost:8080/api/health
docker exec smarteye-lam-service curl -f http://localhost:8001/health
```

### 3. 🧪 기능 테스트

**이미지 분석 테스트:**
```bash
curl -X POST "http://localhost:8080/api/document/analyze" \
  -H "Content-Type: multipart/form-data" \
  -F "image=@test_image.jpg" \
  -F "modelChoice=SmartEyeSsen"
```

**Tesseract OCR 테스트:**
```bash
# Backend 컨테이너에서 직접 테스트
docker exec -it smarteye-backend \
  tesseract --list-langs
```

### 4. 📋 로그 모니터링
```bash
# 개발환경 로그
tail -f Backend/smarteye-backend/logs/smarteye.log

# 프로덕션 (Docker) 로그
docker logs -f smarteye-backend
docker logs -f smarteye-lam-service
docker logs -f smarteye-postgres

# 전체 로그 실시간 확인
docker-compose logs -f
```

### 5. 🌐 웹 인터페이스 접속 확인
- **🏠 메인 API**: http://localhost:8080
- **📚 API 문서**: http://localhost:8080/swagger-ui/index.html
- **🔬 LAM Service**: http://localhost:8001
- **📖 LAM API 문서**: http://localhost:8001/docs
- **📊 시스템 메트릭**: http://localhost:8080/actuator/metrics

## 📋 체크리스트

### 🛠️ 개발환경 실행 전 확인사항:
- [ ] **Java 21** 설치됨 (`java -version`)
- [ ] **Tesseract OCR** 설치됨 (`sudo apt-get install tesseract-ocr tesseract-ocr-kor`)
- [ ] **Docker & Docker Compose** 설치됨 (`docker --version`, `docker-compose --version`)
- [ ] **Git** 설치됨 (소스코드 클론용)
- [ ] **포트 사용 가능**: 8080 (Backend), 8001 (LAM), 5433 (PostgreSQL)
- [ ] **충분한 디스크 공간**: 최소 4GB (ML 모델 캐시 포함)
- [ ] **메모리**: 최소 4GB RAM (LAM Service ML 모델 로딩용)

### 🏭 프로덕션 환경 실행 전 확인사항:
- [ ] **Docker & Docker Compose** 설치됨
- [ ] **포트 사용 가능**: 80/443 (Nginx), 8080 (Backend), 8001 (LAM), 5433 (PostgreSQL)
- [ ] **충분한 리소스**: 최소 4GB RAM, 10GB 디스크
- [ ] **환경 변수 설정**: OpenAI API 키 등 (선택사항)
- [ ] **네트워크 설정**: 방화벽에서 필요한 포트 개방

### 🔧 선택사항 (고급 사용자):
- [ ] **OpenAI API Key** (AI 이미지 설명 생성용)
- [ ] **GPU 지원** (CUDA 설치 시 LAM Service 성능 향상)
- [ ] **SSL 인증서** (HTTPS 사용 시)

---

## 🎉 성공적인 설치 완료!

위의 모든 단계를 완료하면 **SmartEye 백엔드 시스템**이 성공적으로 설치됩니다.

✅ **마이크로서비스 아키텍처** 기반으로 안정적이고 확장 가능한 시스템
✅ **모든 환경에서 동일한 작동** 보장
✅ **새로운 개발자나 고객도 쉽게 실행** 가능
✅ **프로덕션 레벨의 성능과 안정성** 제공