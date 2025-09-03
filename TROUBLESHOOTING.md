# 🔧 SmartEye v0.4 - 트러블슈팅 가이드

## 📋 개요

SmartEye v0.4 시스템에서 발생할 수 있는 일반적인 문제들과 해결 방법을 정리한 가이드입니다.

## 🚨 API 파라미터 관련 문제

### ❌ 문제: "Required request parameter 'modelChoice' is not present"

**원인**: 필수 파라미터 `modelChoice`가 누락되었습니다.

**잘못된 사용법:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "enableOCR=true" \
  -F "enableAI=true"
```

**✅ 올바른 해결법:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen"
```

### ❌ 문제: "Required part 'image' is not present"

**원인**: 파일 파라미터 이름이 잘못되었습니다.

**잘못된 사용법:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "file=@test_homework_image.jpg"
```

**✅ 올바른 해결법:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen"
```

## 🔧 서비스 연결 문제

### ❌ 문제: "Connection refused" (8080 포트)

**원인**: Backend 서비스가 실행되지 않았거나 포트가 사용 중입니다.

**진단 방법:**
```bash
# 포트 사용 확인
sudo netstat -tulnp | grep 8080

# Docker 컨테이너 상태 확인
docker ps | grep smarteye-backend

# 서비스 로그 확인
docker-compose logs smarteye-backend
```

**✅ 해결법:**
```bash
# 서비스 재시작
docker-compose restart smarteye-backend

# 전체 서비스 재시작
cd /home/jongyoung3/SmartEye_v0.4
./start_services.sh
```

### ❌ 문제: LAM Service 연결 실패

**원인**: Python LAM 서비스가 응답하지 않습니다.

**진단 방법:**
```bash
# LAM 서비스 상태 확인
curl http://localhost:8001/health

# 컨테이너 로그 확인
docker-compose logs smarteye-lam-service
```

**✅ 해결법:**
```bash
# LAM 서비스만 재시작
docker-compose restart smarteye-lam-service

# 컨테이너 완전 재빌드
docker-compose down
docker-compose build --no-cache smarteye-lam-service
docker-compose up -d
```

## 🗃️ 데이터베이스 문제

### ❌ 문제: "Connection to localhost:5433 refused"

**원인**: PostgreSQL 데이터베이스 서비스가 실행되지 않았습니다.

**진단 방법:**
```bash
# PostgreSQL 컨테이너 상태 확인
docker ps | grep smarteye-postgres

# 데이터베이스 연결 테스트
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db -c "SELECT version();"
```

**✅ 해결법:**
```bash
# PostgreSQL 재시작
docker-compose restart smarteye-postgres

# 데이터베이스 볼륨 문제 시 완전 재시작
docker-compose down --volumes
docker-compose up -d postgres
```

### ❌ 문제: "password authentication failed"

**원인**: 데이터베이스 인증 정보가 올바르지 않습니다.

**✅ 해결법:**
```bash
# 환경 변수 확인
echo $DB_USERNAME
echo $DB_PASSWORD

# 기본값으로 재설정
export DB_USERNAME=smarteye
export DB_PASSWORD=smarteye_password
```

## 📁 파일 업로드 문제

### ❌ 문제: "Maximum upload size exceeded"

**원인**: 파일 크기가 50MB 제한을 초과했습니다.

**✅ 해결법:**
1. 파일 크기를 확인하고 50MB 이하로 압축
2. 또는 설정 변경:

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

### ❌ 문제: "Could not parse multipart servlet request"

**원인**: Content-Type 헤더가 잘못되었습니다.

**✅ 해결법:**
```bash
# Content-Type을 명시적으로 설정하지 않고 curl이 자동 설정하도록 함
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen"
```

## 🌐 Swagger UI 접속 문제

### ❌ 문제: Swagger UI 페이지를 찾을 수 없음

**원인**: URL이 잘못되었거나 SpringDoc 설정 문제입니다.

**올바른 URL들:**
- Primary: `http://localhost:8080/swagger-ui/index.html`
- Alternative: `http://localhost:8080/swagger-ui.html`

**✅ 해결법:**
```bash
# OpenAPI JSON 확인
curl http://localhost:8080/v3/api-docs

# Actuator 엔드포인트 확인
curl http://localhost:8080/actuator
```

## ⚡ 성능 및 메모리 문제

### ❌ 문제: "java.lang.OutOfMemoryError"

**원인**: JVM 힙 메모리 부족입니다.

**✅ 해결법:**
```bash
# Docker 메모리 증가
docker-compose down
# docker-compose.yml에서 메모리 설정 증가

# JVM 옵션 설정
export JAVA_OPTS="-Xmx4g -Xms2g"
```

### ❌ 문제: 분석 처리 시간이 너무 오래 걸림

**원인**: 대용량 파일 또는 복잡한 레이아웃입니다.

**✅ 해결법:**
1. 이미지 크기를 줄여서 테스트
2. 타임아웃 설정 확인:

```yaml
# application.yml
smarteye:
  processing:
    job-timeout: 1800  # 30분
```

## 🔄 시스템 전체 재시작

**완전한 시스템 리셋이 필요한 경우:**

```bash
# 1. 모든 컨테이너 중지 및 삭제
cd /home/jongyoung3/SmartEye_v0.4
docker-compose down --volumes --remove-orphans

# 2. 이미지 재빌드
docker-compose build --no-cache

# 3. 서비스 재시작
./start_services.sh

# 4. 상태 확인
./system-validation.sh
```

## 📞 추가 도움

문제가 지속될 경우:

1. **로그 확인**: `docker-compose logs [service-name]`
2. **시스템 검증**: `./system-validation.sh`
3. **API 테스트**: 위의 올바른 명령어 사용
4. **문서 참조**: `API_TESTING.md`, `BACKEND_SETUP_GUIDE.md`

---

**마지막 업데이트**: 2025-09-03  
**SmartEye v0.4** - 트러블슈팅 가이드
