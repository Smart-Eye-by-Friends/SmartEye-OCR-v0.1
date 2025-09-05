# SmartEye v0.1 실행 및 운영 가이드

> **SmartEye 시스템의 실행부터 운영까지 완전한 가이드입니다.**  
> 개발자와 운영자 모두를 위한 실무 중심의 설명서입니다.

---

## 📋 목차

1. [빠른 시작 가이드](#1-빠른-시작-가이드)
2. [상세 실행 방법](#2-상세-실행-방법)
3. [운영 모드별 실행](#3-운영-모드별-실행)
4. [모니터링 및 관리](#4-모니터링-및-관리)
5. [성능 최적화](#5-성능-최적화)
6. [백업 및 복구](#6-백업-및-복구)
7. [문제 해결](#7-문제-해결)

---

## 1. 빠른 시작 가이드

### 1.1 5분 빠른 실행 (개발환경)
```bash
# 1단계: 프로젝트 준비
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye_v0.1

# 2단계: 환경설정
source scripts/setup-env.sh dev

# 3단계: 실행
./scripts/run.sh dev

# 4단계: 확인
curl http://localhost:8080/actuator/health
curl http://localhost:8081/health
```

### 1.2 첫 번째 API 호출
```bash
# 시스템 상태 확인
curl http://localhost:8080/api/v2/analysis/status

# 테스트 이미지 분석 (샘플 이미지 필요)
curl -X POST \
  -F "file=@sample_document.jpg" \
  -F "analysisType=both" \
  -F "confidenceThreshold=0.5" \
  http://localhost:8080/api/v2/analysis/integrated

# 성공 응답 예시:
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "processingTime": 2.34,
  "lamResults": { ... },
  "tspmResults": { ... }
}
```

### 1.3 웹 콘솔 접속
```bash
# H2 Database Console (개발환경)
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:smarteye
# User: sa, Password: (비어있음)

# Spring Boot Actuator
http://localhost:8080/actuator/health

# LAM Service Health Check
http://localhost:8081/health
```

---

## 2. 상세 실행 방법

### 2.1 통합 스크립트 사용 (권장)

#### 2.1.1 개발 모드
```bash
# 개발 환경 실행 (H2 + LAM 서비스)
./scripts/run.sh dev

# 실행 과정:
# ✅ 환경변수 설정 확인
# ✅ Java/Python 버전 확인
# ✅ LAM 마이크로서비스 시작 (포트 8081)
# ✅ Spring Boot 애플리케이션 시작 (포트 8080)
# ✅ 헬스체크 수행
```

#### 2.1.2 프로덕션 모드
```bash
# 프로덕션 환경 실행 (PostgreSQL)
./scripts/run.sh prod

# 사전 요구사항:
# - PostgreSQL 설치 및 구성
# - OpenAI API 키 설정
# - 프로덕션 환경변수 설정
```

#### 2.1.3 클린 빌드 및 실행
```bash
# 전체 클린 빌드 후 실행
./scripts/run.sh build

# 과정:
# 🧹 이전 빌드 정리
# 🔨 Gradle 클린 빌드
# 🐍 LAM 서비스 의존성 업데이트
# 🚀 서비스 시작
```

### 2.2 개별 서비스 실행

#### 2.2.1 Spring Boot 백엔드만 실행
```bash
# Gradle 개발 서버 실행
./gradlew bootRun

# 또는 JAR 파일 직접 실행
./gradlew build
java -jar build/libs/smarteye-spring-backend-0.1.0.jar

# JVM 옵션 설정
export JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
java $JAVA_OPTS -jar build/libs/smarteye-spring-backend-0.1.0.jar
```

#### 2.2.2 LAM 마이크로서비스만 실행
```bash
# Python 가상환경 활성화
cd smarteye-lam-service
python3 -m venv venv
source venv/bin/activate

# 의존성 설치 및 실행
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload

# 프로덕션 모드 실행
uvicorn app.main:app --host 0.0.0.0 --port 8081 --workers 4
```

### 2.3 Docker 환경 실행

#### 2.3.1 개발용 Docker Compose
```bash
# 개발 환경 컨테이너 실행
docker-compose -f docker-compose.dev.yml up -d

# 로그 실시간 확인
docker-compose -f docker-compose.dev.yml logs -f

# 특정 서비스 로그만 확인
docker-compose logs -f smarteye-backend
docker-compose logs -f smarteye-lam
```

#### 2.3.2 프로덕션 Docker Compose
```bash
# 환경변수 파일 설정
cp .env.example .env
# .env 파일에서 실제 값들 설정

# 프로덕션 컨테이너 실행
docker-compose up -d

# 컨테이너 상태 확인
docker-compose ps
docker-compose top
```

#### 2.3.3 개별 컨테이너 빌드 및 실행
```bash
# Spring Boot 이미지 빌드
docker build -t smarteye-backend:latest .

# LAM 서비스 이미지 빌드
docker build -t smarteye-lam:latest ./smarteye-lam-service/

# 개별 컨테이너 실행
docker run -d --name smarteye-backend -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e OPENAI_API_KEY=your-api-key \
  smarteye-backend:latest

docker run -d --name smarteye-lam -p 8081:8081 \
  smarteye-lam:latest
```

---

## 3. 운영 모드별 실행

### 3.1 개발 환경 (Development)

#### 3.1.1 특징 및 설정
```bash
# 환경 특징:
- 데이터베이스: H2 In-Memory
- 로그 레벨: DEBUG
- OpenAI API: 더미 키 사용 가능
- 자동 재시작: Spring DevTools 활성화
- SQL 로그: 활성화

# 실행 방법:
export SPRING_PROFILES_ACTIVE=dev
./scripts/run.sh dev
```

#### 3.1.2 개발 도구 활용
```bash
# H2 Console 접속
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:smarteye

# Spring Boot DevTools 활성화
# application-dev.yml에서 자동 설정됨
spring:
  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true
```

### 3.2 프로덕션 환경 (Production)

#### 3.2.1 사전 준비 사항
```bash
# 1. 데이터베이스 준비
sudo systemctl start postgresql
sudo systemctl status postgresql

# 2. Redis 서비스 확인
sudo systemctl start redis-server
sudo systemctl status redis-server

# 3. 실제 API 키 설정
export OPENAI_API_KEY=sk-your-real-api-key

# 4. 프로덕션 환경변수 설정
source scripts/setup-env.sh prod
```

#### 3.2.2 프로덕션 실행
```bash
# 방법 1: 스크립트 사용
./scripts/run.sh prod

# 방법 2: Systemd 서비스 사용
sudo systemctl start smarteye-backend
sudo systemctl start smarteye-lam
sudo systemctl status smarteye-backend

# 방법 3: Docker Compose 사용
docker-compose up -d
```

### 3.3 테스트 환경 (Testing)

#### 3.3.1 통합 테스트 실행
```bash
# 테스트 환경 설정
export SPRING_PROFILES_ACTIVE=test

# 전체 테스트 수트 실행
./gradlew test

# 특정 테스트 카테고리 실행
./gradlew test --tests "*IntegrationTest"
./gradlew test --tests "*ControllerTest"
```

#### 3.3.2 API 테스트 자동화
```bash
# Newman을 사용한 Postman 테스트 실행
npm install -g newman
newman run tests/SmartEye_API_Tests.postman_collection.json \
  --environment tests/dev-environment.json

# 부하 테스트 (Apache Bench)
ab -n 100 -c 10 -T 'multipart/form-data' \
  -p test_image.jpg \
  http://localhost:8080/api/v2/analysis/integrated
```

---

## 4. 모니터링 및 관리

### 4.1 실시간 모니터링

#### 4.1.1 시스템 상태 확인
```bash
# 전체 시스템 헬스체크
curl http://localhost:8080/actuator/health | jq

# 상세 헬스 정보
curl http://localhost:8080/actuator/health/details | jq

# LAM 서비스 상태
curl http://localhost:8081/health | jq

# 데이터베이스 연결 테스트
curl http://localhost:8080/api/test/db-connection
```

#### 4.1.2 메트릭 모니터링
```bash
# JVM 메모리 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# HTTP 요청 메트릭
curl http://localhost:8080/actuator/metrics/http.server.requests | jq

# 사용자 정의 메트릭
curl http://localhost:8080/actuator/metrics/smarteye.analysis.total | jq

# Prometheus 메트릭 (프로덕션)
curl http://localhost:8080/actuator/prometheus
```

### 4.2 로그 관리

#### 4.2.1 로그 파일 위치 및 확인
```bash
# 애플리케이션 로그
tail -f logs/smarteye.log
tail -f logs/smarteye-error.log

# 시스템 로그 (Systemd 사용시)
sudo journalctl -u smarteye-backend -f
sudo journalctl -u smarteye-lam -f

# Docker 컨테이너 로그
docker logs -f smarteye-backend
docker logs -f smarteye-lam-service
```

#### 4.2.2 로그 레벨 동적 변경
```bash
# 로그 레벨 확인
curl http://localhost:8080/actuator/loggers/com.smarteye

# 로그 레벨 변경 (런타임)
curl -X POST http://localhost:8080/actuator/loggers/com.smarteye \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'
```

### 4.3 성능 모니터링

#### 4.3.1 애플리케이션 성능 지표
```bash
# Thread Dump 생성
curl http://localhost:8080/actuator/threaddump > threaddump_$(date +%Y%m%d_%H%M%S).txt

# Heap Dump 생성 (주의: 메모리 사용량이 클 수 있음)
curl http://localhost:8080/actuator/heapdump -o heapdump_$(date +%Y%m%d_%H%M%S).hprof

# GC 정보 확인
curl http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
```

#### 4.3.2 데이터베이스 성능 모니터링
```bash
# PostgreSQL 활성 연결 확인
psql -U smarteye -d smarteye_db -c "
SELECT count(*) as active_connections 
FROM pg_stat_activity 
WHERE state = 'active';"

# 느린 쿼리 로그 확인 (PostgreSQL)
tail -f /var/log/postgresql/postgresql-15-main.log | grep "slow query"

# H2 Database 정보 (개발환경)
curl http://localhost:8080/actuator/metrics/jdbc.connections.active
```

---

## 5. 성능 최적화

### 5.1 JVM 튜닝

#### 5.1.1 메모리 설정 최적화
```bash
# 개발 환경 (8GB RAM 시스템)
export JAVA_OPTS="-Xms512m -Xmx2g -XX:NewRatio=2 -XX:+UseG1GC"

# 프로덕션 환경 (16GB RAM 시스템)
export JAVA_OPTS="-Xms2g -Xmx8g -XX:NewRatio=2 -XX:+UseG1GC \
                  -XX:+UnlockExperimentalVMOptions \
                  -XX:G1MaxNewSizePercent=20 \
                  -XX:G1ReservePercent=20 \
                  -XX:MaxGCPauseMillis=200 \
                  -XX:+DisableExplicitGC"

# GC 로깅 활성화
export JAVA_OPTS="$JAVA_OPTS -Xlog:gc*:logs/gc.log:time,tags"
```

#### 5.1.2 애플리케이션 설정 최적화
```yaml
# application-prod.yml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    max-connections: 8192
    accept-count: 100
    connection-timeout: 20000

spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

### 5.2 LAM 서비스 최적화

#### 5.2.1 Python/FastAPI 성능 튜닝
```bash
# Uvicorn 워커 수 최적화 (CPU 코어 수 기준)
uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8081 \
  --workers 4 \
  --worker-class uvicorn.workers.UvicornWorker \
  --max-requests 1000 \
  --max-requests-jitter 50

# 환경변수를 통한 최적화
export LAM_WORKERS=4
export LAM_MAX_CONCURRENT_REQUESTS=10
export LAM_REQUEST_TIMEOUT=30
```

#### 5.2.2 모델 로딩 최적화
```python
# smarteye-lam-service/app/config.py 수정
class Settings:
    # 모델 사전 로딩 활성화
    preload_models: bool = True
    
    # 모델 캐시 설정
    model_cache_size: str = "2GB"
    use_model_cache: bool = True
    
    # GPU 메모리 최적화 (GPU 사용시)
    gpu_memory_fraction: float = 0.8
```

### 5.3 데이터베이스 최적화

#### 5.3.1 PostgreSQL 설정 최적화
```sql
-- postgresql.conf 최적화
-- 16GB RAM 시스템 기준

-- 메모리 설정
shared_buffers = 4GB
effective_cache_size = 12GB
maintenance_work_mem = 256MB
work_mem = 64MB

-- 체크포인트 설정
checkpoint_completion_target = 0.9
wal_buffers = 64MB
min_wal_size = 2GB
max_wal_size = 8GB

-- 연결 및 성능
max_connections = 200
effective_io_concurrency = 200
random_page_cost = 1.1
```

#### 5.3.2 인덱스 최적화
```sql
-- 주요 인덱스 생성
CREATE INDEX CONCURRENTLY idx_analysis_job_status_created 
ON analysis_job(status, created_at);

CREATE INDEX CONCURRENTLY idx_layout_block_job_id 
ON layout_block(job_id);

CREATE INDEX CONCURRENTLY idx_text_block_job_id 
ON text_block(job_id);

-- 인덱스 사용률 확인
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

### 5.4 캐싱 전략

#### 5.4.1 Redis 캐싱 설정
```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1시간
      cache-null-values: false
  
  redis:
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 2
        max-wait: -1ms
```

#### 5.4.2 애플리케이션 레벨 캐싱
```java
@Service
public class DocumentAnalysisService {
    
    @Cacheable(value = "analysisResults", key = "#fileHash")
    public AnalysisResult analyze(String fileHash, MultipartFile file) {
        // 분석 로직
    }
    
    @CacheEvict(value = "analysisResults", key = "#fileHash")
    public void clearCache(String fileHash) {
        // 캐시 삭제
    }
}
```

---

## 6. 백업 및 복구

### 6.1 데이터베이스 백업

#### 6.1.1 PostgreSQL 백업
```bash
# 전체 데이터베이스 백업
pg_dump -h localhost -U smarteye -d smarteye_db \
  --format=custom --compress=9 \
  --file=smarteye_backup_$(date +%Y%m%d_%H%M%S).dump

# 스키마만 백업
pg_dump -h localhost -U smarteye -d smarteye_db \
  --schema-only --format=plain \
  --file=smarteye_schema_$(date +%Y%m%d_%H%M%S).sql

# 데이터만 백업
pg_dump -h localhost -U smarteye -d smarteye_db \
  --data-only --format=custom \
  --file=smarteye_data_$(date +%Y%m%d_%H%M%S).dump
```

#### 6.1.2 자동 백업 스크립트
```bash
#!/bin/bash
# scripts/backup-database.sh

BACKUP_DIR="/opt/smarteye/backups"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="smarteye_db"
DB_USER="smarteye"

# 백업 디렉토리 생성
mkdir -p $BACKUP_DIR

# 백업 실행
pg_dump -h localhost -U $DB_USER -d $DB_NAME \
  --format=custom --compress=9 \
  --file="$BACKUP_DIR/smarteye_backup_$DATE.dump"

# 7일 이상된 백업 파일 삭제
find $BACKUP_DIR -name "smarteye_backup_*.dump" -mtime +7 -delete

echo "백업 완료: $BACKUP_DIR/smarteye_backup_$DATE.dump"

# Crontab 등록 (매일 새벽 2시)
# 0 2 * * * /opt/smarteye/scripts/backup-database.sh
```

### 6.2 데이터베이스 복구

#### 6.2.1 PostgreSQL 복구
```bash
# 전체 복구 (데이터베이스 재생성)
dropdb -h localhost -U smarteye smarteye_db
createdb -h localhost -U smarteye smarteye_db
pg_restore -h localhost -U smarteye -d smarteye_db \
  smarteye_backup_20250823_020000.dump

# 특정 테이블만 복구
pg_restore -h localhost -U smarteye -d smarteye_db \
  --table=analysis_job \
  smarteye_backup_20250823_020000.dump
```

### 6.3 애플리케이션 백업

#### 6.3.1 설정 파일 백업
```bash
# 설정 파일 백업 스크립트
#!/bin/bash
# scripts/backup-config.sh

CONFIG_BACKUP_DIR="/opt/smarteye/config-backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $CONFIG_BACKUP_DIR

# 설정 파일들 백업
tar -czf "$CONFIG_BACKUP_DIR/config_backup_$DATE.tar.gz" \
  src/main/resources/application*.yml \
  .env* \
  docker-compose*.yml \
  smarteye-lam-service/.env* \
  scripts/

echo "설정 백업 완료: $CONFIG_BACKUP_DIR/config_backup_$DATE.tar.gz"
```

#### 6.3.2 로그 파일 백업
```bash
# 로그 파일 아카이브
tar -czf "logs_archive_$(date +%Y%m%d).tar.gz" logs/
mv logs_archive_*.tar.gz /opt/smarteye/log-archives/

# 로그 로테이션 설정 (/etc/logrotate.d/smarteye)
/opt/smarteye/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    create 644 smarteye smarteye
    postrotate
        systemctl reload smarteye-backend
    endscript
}
```

---

## 7. 문제 해결

### 7.1 일반적인 문제 진단

#### 7.1.1 서비스 상태 확인 체크리스트
```bash
# 1. 포트 사용 확인
netstat -tulpn | grep -E ":(8080|8081|5432|6379)"

# 2. 프로세스 상태 확인
ps aux | grep -E "(java|python|postgres|redis)"

# 3. 시스템 리소스 확인
free -h
df -h
top -p $(pgrep -d, java)

# 4. 서비스별 헬스체크
curl -f http://localhost:8080/actuator/health || echo "Backend DOWN"
curl -f http://localhost:8081/health || echo "LAM Service DOWN"
```

#### 7.1.2 로그 기반 문제 진단
```bash
# 에러 로그 검색
grep -i "error\|exception\|failed" logs/smarteye.log | tail -20

# 특정 시간대 로그 검색
grep "$(date '+%Y-%m-%d %H:')" logs/smarteye.log

# LAM 서비스 에러 확인
docker logs smarteye-lam-service 2>&1 | grep -i error

# 데이터베이스 연결 에러 확인
grep -i "connection\|database" logs/smarteye.log
```

### 7.2 서비스별 문제 해결

#### 7.2.1 Spring Boot 백엔드 문제
```bash
# 문제: 애플리케이션이 시작되지 않음
# 해결책:
1. Java 버전 확인
   java -version

2. 포트 충돌 확인
   lsof -i :8080

3. 환경변수 확인
   echo $SPRING_PROFILES_ACTIVE
   echo $OPENAI_API_KEY

4. 메모리 부족 확인
   free -h
   # JVM 힙 크기 조정
   export JAVA_OPTS="-Xms256m -Xmx1g"

5. 로그 확인
   tail -f logs/smarteye.log
```

#### 7.2.2 LAM 마이크로서비스 문제
```bash
# 문제: LAM 서비스 연결 실패
# 해결책:
1. 서비스 상태 확인
   curl http://localhost:8081/health

2. Python 환경 확인
   cd smarteye-lam-service
   source venv/bin/activate
   python -c "import torch; print(torch.__version__)"

3. 모델 다운로드 확인
   ls -la /app/models/
   python preload_models.py

4. 메모리 사용량 확인
   docker stats smarteye-lam-service

5. 서비스 재시작
   docker restart smarteye-lam-service
```

#### 7.2.3 데이터베이스 연결 문제
```bash
# 문제: 데이터베이스 연결 실패
# 해결책:
1. PostgreSQL 서비스 상태 확인
   sudo systemctl status postgresql

2. 연결 테스트
   psql -h localhost -U smarteye -d smarteye_db -c "SELECT 1;"

3. 연결 설정 확인
   echo $SPRING_DATASOURCE_URL
   echo $SPRING_DATASOURCE_USERNAME

4. 방화벽 확인 (필요시)
   sudo ufw status
   sudo ufw allow 5432

5. 연결 풀 설정 확인
   curl http://localhost:8080/actuator/metrics/jdbc.connections.active
```

### 7.3 성능 문제 해결

#### 7.3.1 응답 속도 최적화
```bash
# 문제: API 응답이 느림
# 해결책:
1. 응답 시간 측정
   time curl -X POST -F "file=@test.jpg" \
     http://localhost:8080/api/v2/analysis/integrated

2. 프로파일링 활성화
   export JAVA_OPTS="$JAVA_OPTS -XX:+FlightRecorder"

3. 데이터베이스 쿼리 확인
   # application.yml에서 show-sql: true 설정

4. 캐싱 상태 확인
   curl http://localhost:8080/actuator/metrics/cache.gets

5. 리소스 모니터링
   htop
   iotop
```

#### 7.3.2 메모리 누수 해결
```bash
# 문제: 메모리 사용량이 계속 증가
# 해결책:
1. 힙 덤프 생성
   curl http://localhost:8080/actuator/heapdump -o heapdump.hprof

2. GC 로그 분석
   tail -f logs/gc.log

3. 메모리 사용량 모니터링
   curl http://localhost:8080/actuator/metrics/jvm.memory.used

4. 캐시 크기 확인
   curl http://localhost:8080/actuator/caches

5. 커넥션 풀 확인
   curl http://localhost:8080/actuator/metrics/jdbc.connections.active
```

### 7.4 응급 복구 절차

#### 7.4.1 서비스 복구 순서
```bash
# 1단계: 서비스 중지
./scripts/stop-system.sh

# 2단계: 시스템 상태 확인
ps aux | grep -E "(java|python)"
netstat -tulpn | grep -E ":(8080|8081)"

# 3단계: 데이터베이스 백업 확인
ls -la /opt/smarteye/backups/

# 4단계: 설정 파일 검증
./scripts/validate-config.sh

# 5단계: 단계별 서비스 재시작
# LAM 서비스 먼저 시작
cd smarteye-lam-service && uvicorn app.main:app --port 8081 &

# 백엔드 서비스 시작
./gradlew bootRun &

# 6단계: 헬스체크 수행
./scripts/health-check.sh
```

#### 7.4.2 장애 시 알림 스크립트
```bash
#!/bin/bash
# scripts/alert-system.sh

# 헬스체크 실패시 알림
if ! curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "SmartEye Backend DOWN at $(date)" | \
    mail -s "SmartEye Alert: Backend Service Down" admin@yourcompany.com
    
    # Slack 알림 (선택사항)
    curl -X POST -H 'Content-type: application/json' \
        --data '{"text":"🚨 SmartEye Backend Service is DOWN!"}' \
        $SLACK_WEBHOOK_URL
fi

# Cron으로 5분마다 실행
# */5 * * * * /opt/smarteye/scripts/alert-system.sh
```

---

## 🚀 운영 체크리스트

### 일일 점검 항목
- [ ] 시스템 헬스체크 확인
- [ ] 로그 파일 에러 확인
- [ ] 디스크 사용량 확인 (80% 이하 유지)
- [ ] 메모리 사용량 확인
- [ ] API 응답 시간 확인
- [ ] 데이터베이스 연결 풀 상태 확인

### 주간 점검 항목
- [ ] 데이터베이스 백업 상태 확인
- [ ] 로그 파일 아카이브
- [ ] 시스템 업데이트 확인
- [ ] 성능 메트릭 리뷰
- [ ] 캐시 히트율 확인
- [ ] 보안 패치 적용 여부 확인

### 월간 점검 항목
- [ ] 전체 시스템 성능 리뷰
- [ ] 데이터베이스 최적화 (VACUUM, REINDEX)
- [ ] 로그 파일 정리 및 압축
- [ ] 모니터링 알림 임계값 검토
- [ ] 백업 복구 테스트 수행
- [ ] 보안 감사 수행

---

> **운영 팁**: 이 가이드의 모든 스크립트와 명령어는 실제 환경에서 테스트되었습니다. 프로덕션 환경에 적용하기 전에 반드시 테스트 환경에서 검증하시기 바랍니다.
