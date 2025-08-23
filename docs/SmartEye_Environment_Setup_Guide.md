# SmartEye v0.1 환경설정 가이드

> **SmartEye 시스템의 완전한 환경설정 가이드입니다.**  
> 개발환경부터 프로덕션 배포까지 단계별로 설명합니다.

---

## 📋 목차

1. [시스템 요구사항](#1-시스템-요구사항)
2. [개발 환경 설정](#2-개발-환경-설정)
3. [프로덕션 환경 설정](#3-프로덕션-환경-설정)
4. [Docker 환경 설정](#4-docker-환경-설정)
5. [환경변수 상세 가이드](#5-환경변수-상세-가이드)
6. [데이터베이스 설정](#6-데이터베이스-설정)
7. [외부 서비스 연동](#7-외부-서비스-연동)

---

## 1. 시스템 요구사항

### 1.1 하드웨어 요구사항
```
최소 사양:
- CPU: 4 Core (Intel i5 또는 AMD Ryzen 5 이상)
- Memory: 8GB RAM
- Storage: 20GB 여유 공간
- Network: 1Gbps 이상

권장 사양:
- CPU: 8 Core (Intel i7 또는 AMD Ryzen 7 이상)
- Memory: 16GB RAM
- Storage: SSD 50GB 여유 공간
- GPU: CUDA 지원 GPU (선택사항, LAM 성능 향상)
```

### 1.2 소프트웨어 요구사항
```bash
필수 설치 항목:
- Java 17+ (OpenJDK 권장)
- Python 3.9+
- Git 2.25+
- Docker 20.10+
- Docker Compose 2.0+

선택 설치 항목:
- PostgreSQL 15+ (프로덕션 환경)
- Redis 6.0+ (캐싱 및 세션 관리)
- Nginx (리버스 프록시)
```

### 1.3 운영체제 지원
```
공식 지원:
- Ubuntu 20.04 LTS / 22.04 LTS
- CentOS 8+ / Rocky Linux 8+
- macOS 12+ (개발 환경)

테스트 완료:
- Windows 11 + WSL2
- Amazon Linux 2
- Docker 환경 (플랫폼 무관)
```

---

## 2. 개발 환경 설정

### 2.1 기본 환경 준비
```bash
# 1. Java 17 설치 확인
java -version
# java version "17.0.x" 출력 확인

# 2. Python 3.9+ 설치 확인
python3 --version
# Python 3.9.x 이상 출력 확인

# 3. Git 설정
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### 2.2 프로젝트 클론 및 초기 설정
```bash
# 저장소 클론
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye_v0.1

# 브랜치 확인 및 전환
git checkout feature/backendWeb  # 현재 개발 브랜치

# 환경설정 파일 복사
cp .env.dev .env
cp smarteye-lam-service/.env.example smarteye-lam-service/.env

# 스크립트 실행 권한 부여
chmod +x scripts/*.sh
```

### 2.3 개발 환경 변수 설정
```bash
# 자동 환경설정 (권장)
source scripts/setup-env.sh dev

# 수동 환경설정
export SPRING_PROFILES_ACTIVE=dev
export SPRING_DATASOURCE_URL=jdbc:h2:mem:smarteye;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_DATASOURCE_USERNAME=sa
export SPRING_DATASOURCE_PASSWORD=
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
export SPRING_JPA_SHOW_SQL=true
export LOGGING_LEVEL_COM_SMARTEYE=DEBUG
export OPENAI_API_KEY=dummy-dev-key  # 개발용 더미 키
export LAM_SERVICE_URL=http://localhost:8081
```

### 2.4 Python 가상환경 설정 (LAM 서비스)
```bash
# LAM 서비스 디렉토리로 이동
cd smarteye-lam-service

# 가상환경 생성 및 활성화
python3 -m venv venv
source venv/bin/activate  # Linux/macOS
# 또는 venv\Scripts\activate  # Windows

# 의존성 설치
pip install -r requirements.txt

# 모델 사전 다운로드 (선택사항)
python preload_models.py
```

### 2.5 IDE 설정 (IntelliJ IDEA / VS Code)

#### IntelliJ IDEA 설정
```
1. Project Structure > Project Settings > Project
   - Project SDK: Java 17
   - Project language level: 17

2. Build, Execution, Deployment > Build Tools > Gradle
   - Use Gradle from: 'gradle-wrapper.properties' file
   - Gradle JVM: Project SDK (Java 17)

3. Run/Debug Configurations
   - Name: SmartEye Dev
   - Main class: com.smarteye.SmartEyeApplication
   - VM options: -Dspring.profiles.active=dev
   - Environment variables: OPENAI_API_KEY=dummy-dev-key
```

#### VS Code 설정
```json
// .vscode/launch.json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "SmartEye Dev",
            "request": "launch",
            "mainClass": "com.smarteye.SmartEyeApplication",
            "projectName": "smarteye-spring-backend",
            "env": {
                "SPRING_PROFILES_ACTIVE": "dev",
                "OPENAI_API_KEY": "dummy-dev-key"
            }
        }
    ]
}
```

---

## 3. 프로덕션 환경 설정

### 3.1 서버 환경 준비
```bash
# Ubuntu 22.04 LTS 기준

# 1. 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# 2. 필수 패키지 설치
sudo apt install -y curl wget git unzip

# 3. Java 17 설치
sudo apt install -y openjdk-17-jdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc

# 4. Python 3.9+ 설치 확인
python3 --version
sudo apt install -y python3-pip python3-venv
```

### 3.2 PostgreSQL 설치 및 설정
```bash
# PostgreSQL 15 설치
sudo apt install -y postgresql-15 postgresql-client-15 postgresql-contrib-15

# PostgreSQL 서비스 시작 및 활성화
sudo systemctl start postgresql
sudo systemctl enable postgresql

# 데이터베이스 및 사용자 생성
sudo -u postgres psql << EOF
CREATE USER smarteye WITH PASSWORD 'your_secure_password';
CREATE DATABASE smarteye_db OWNER smarteye;
GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;
\q
EOF

# 연결 테스트
psql -h localhost -U smarteye -d smarteye_db -c "SELECT version();"
```

### 3.3 Redis 설치 및 설정
```bash
# Redis 설치
sudo apt install -y redis-server

# Redis 설정 수정
sudo nano /etc/redis/redis.conf
# 다음 설정 변경:
# bind 127.0.0.1 ::1  # 로컬 접속만 허용
# requirepass your_redis_password  # 패스워드 설정

# Redis 서비스 재시작
sudo systemctl restart redis-server
sudo systemctl enable redis-server

# 연결 테스트
redis-cli ping
```

### 3.4 프로덕션 환경변수 설정
```bash
# /etc/environment 파일에 추가
sudo tee -a /etc/environment > /dev/null << EOF
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smarteye_db
SPRING_DATASOURCE_USERNAME=smarteye
SPRING_DATASOURCE_PASSWORD=your_secure_password
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false
OPENAI_API_KEY=your_openai_api_key
LAM_SERVICE_URL=http://localhost:8081
LOGGING_LEVEL_COM_SMARTEYE=INFO
EOF

# 환경변수 적용
source /etc/environment
```

### 3.5 Systemd 서비스 등록
```bash
# Spring Boot 서비스 등록
sudo tee /etc/systemd/system/smarteye-backend.service > /dev/null << EOF
[Unit]
Description=SmartEye Backend Service
After=network.target postgresql.service

[Service]
Type=simple
User=smarteye
WorkingDirectory=/opt/smarteye
ExecStart=/usr/bin/java -jar smarteye-backend.jar
Restart=always
RestartSec=10
Environment=SPRING_PROFILES_ACTIVE=prod

[Install]
WantedBy=multi-user.target
EOF

# LAM 서비스 등록
sudo tee /etc/systemd/system/smarteye-lam.service > /dev/null << EOF
[Unit]
Description=SmartEye LAM Service
After=network.target

[Service]
Type=simple
User=smarteye
WorkingDirectory=/opt/smarteye/lam-service
ExecStart=/opt/smarteye/lam-service/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8081
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 서비스 활성화
sudo systemctl daemon-reload
sudo systemctl enable smarteye-backend
sudo systemctl enable smarteye-lam
```

---

## 4. Docker 환경 설정

### 4.1 Docker 및 Docker Compose 설치
```bash
# Docker 설치 (Ubuntu)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER
newgrp docker

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 설치 확인
docker --version
docker-compose --version
```

### 4.2 Docker 환경 설정
```bash
# .env 파일 설정 (Docker용)
cat > .env << EOF
# Docker 환경 설정
SPRING_PROFILES_ACTIVE=docker
OPENAI_API_KEY=your_openai_api_key

# 데이터베이스 설정
DB_NAME=smarteye
DB_USERNAME=smarteye
DB_PASSWORD=secure_password
DB_PORT=5432

# Redis 설정
REDIS_PORT=6379

# LAM 서비스 리소스 설정
LAM_WORKERS=4
LAM_MEMORY_LIMIT=4G
LAM_CPU_LIMIT=2.0
LAM_MEMORY_RESERVATION=2G
LAM_CPU_RESERVATION=1.0

# 보안 설정
JWT_SECRET=your_jwt_secret_key
ENCRYPTION_KEY=your_encryption_key

# 로그 레벨
LOG_LEVEL=INFO
EOF
```

### 4.3 Docker Compose 실행
```bash
# 개발 환경 실행
docker-compose -f docker-compose.dev.yml up -d

# 프로덕션 환경 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f smarteye-backend
docker-compose logs -f smarteye-lam

# 상태 확인
docker-compose ps
```

### 4.4 Docker 최적화 설정
```bash
# Docker 데몬 설정 (/etc/docker/daemon.json)
sudo tee /etc/docker/daemon.json > /dev/null << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 64000,
      "Soft": 64000
    }
  }
}
EOF

# Docker 서비스 재시작
sudo systemctl restart docker
```

---

## 5. 환경변수 상세 가이드

### 5.1 Spring Boot 환경변수
```bash
# 필수 환경변수
SPRING_PROFILES_ACTIVE=dev|prod|docker    # 실행 프로파일
SPRING_DATASOURCE_URL=jdbc:...             # 데이터베이스 URL
SPRING_DATASOURCE_USERNAME=username        # DB 사용자명
SPRING_DATASOURCE_PASSWORD=password        # DB 패스워드

# 선택 환경변수
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop|validate  # DDL 모드
SPRING_JPA_SHOW_SQL=true|false            # SQL 로그 출력
LOGGING_LEVEL_COM_SMARTEYE=DEBUG|INFO|WARN # 로그 레벨
```

### 5.2 SmartEye 애플리케이션 환경변수
```bash
# OpenAI API 설정
OPENAI_API_KEY=sk-your-api-key            # OpenAI API 키 (필수)

# LAM 서비스 설정
LAM_SERVICE_URL=http://localhost:8081     # LAM 서비스 URL
LAM_SERVICE_TIMEOUT=30                    # 서비스 타임아웃(초)
LAM_SERVICE_RETRIES=3                     # 재시도 횟수

# Tesseract OCR 설정
TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
TESSERACT_LANGUAGE=kor+eng                # 인식 언어

# 파일 업로드 설정
UPLOAD_TEMP_DIR=./temp                    # 임시 파일 디렉토리
UPLOAD_MAX_FILE_SIZE=50MB                 # 최대 파일 크기
```

### 5.3 LAM 서비스 환경변수
```bash
# 서버 설정
LAM_HOST=0.0.0.0                         # 바인드 호스트
LAM_PORT=8081                            # 서비스 포트
LAM_DEBUG=false                          # 디버그 모드

# 모델 설정
LAM_MODEL_CHOICE=docstructbench          # 사용할 모델
LAM_MODEL_CACHE_DIR=/app/models          # 모델 캐시 디렉토리
LAM_CONFIDENCE_THRESHOLD=0.5             # 신뢰도 임계값
LAM_MAX_IMAGE_SIZE=4096                  # 최대 이미지 크기

# GPU 설정
LAM_USE_GPU=false                        # GPU 사용 여부
LAM_GPU_DEVICE=0                         # GPU 디바이스 번호

# 성능 설정
LAM_MAX_CONCURRENT_REQUESTS=10           # 최대 동시 요청 수
LAM_REQUEST_TIMEOUT=30                   # 요청 타임아웃(초)
```

### 5.4 환경별 설정 예시

#### 개발 환경 (.env.dev)
```bash
SPRING_PROFILES_ACTIVE=dev
OPENAI_API_KEY=dummy-dev-key
LAM_SERVICE_URL=http://localhost:8081
DB_NAME=smarteye_dev
LOG_LEVEL=DEBUG
LAM_WORKERS=2
LAM_DEBUG=true
```

#### 프로덕션 환경 (.env.prod)
```bash
SPRING_PROFILES_ACTIVE=prod
OPENAI_API_KEY=sk-real-api-key-here
LAM_SERVICE_URL=http://smarteye-lam:8081
DB_NAME=smarteye
DB_PASSWORD=secure_production_password
LOG_LEVEL=INFO
LAM_WORKERS=4
LAM_DEBUG=false
```

---

## 6. 데이터베이스 설정

### 6.1 H2 Database (개발 환경)
```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:h2:mem:smarteye;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### 6.2 PostgreSQL (프로덕션 환경)

#### 6.2.1 데이터베이스 생성 스크립트
```sql
-- PostgreSQL 관리자로 실행
CREATE USER smarteye WITH PASSWORD 'your_secure_password';
CREATE DATABASE smarteye_db 
    WITH OWNER smarteye 
    ENCODING 'UTF8' 
    LC_COLLATE='en_US.UTF-8' 
    LC_CTYPE='en_US.UTF-8';

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;
GRANT CREATE ON SCHEMA public TO smarteye;

-- 연결 제한 설정 (선택사항)
ALTER USER smarteye CONNECTION LIMIT 50;
```

#### 6.2.2 PostgreSQL 최적화 설정
```bash
# postgresql.conf 주요 설정
max_connections = 100
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200

# pg_hba.conf 인증 설정
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all             postgres                                peer
local   all             all                                     peer
host    all             all             127.0.0.1/32            md5
host    all             all             ::1/128                 md5
host    smarteye_db     smarteye        127.0.0.1/32            md5
```

### 6.3 데이터베이스 마이그레이션

#### 6.3.1 Flyway 설정 (선택사항)
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

#### 6.3.2 마이그레이션 스크립트 예시
```sql
-- src/main/resources/db/migration/V1__init_schema.sql
CREATE TABLE analysis_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_analysis_job_job_id ON analysis_job(job_id);
CREATE INDEX idx_analysis_job_status ON analysis_job(status);
CREATE INDEX idx_analysis_job_created_at ON analysis_job(created_at);
```

---

## 7. 외부 서비스 연동

### 7.1 OpenAI API 설정

#### 7.1.1 API 키 발급 및 설정
```bash
# 1. OpenAI 플랫폼에서 API 키 발급
# https://platform.openai.com/api-keys

# 2. 환경변수 설정
export OPENAI_API_KEY=sk-your-api-key-here

# 3. API 키 테스트
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

#### 7.1.2 사용량 모니터링 설정
```yaml
# application.yml
smarteye:
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-vision-preview
    max-tokens: 4096
    timeout: 30s
    rate-limit:
      requests-per-minute: 60
      tokens-per-minute: 150000
```

### 7.2 Redis 캐싱 설정

#### 7.2.1 Redis 연결 설정
```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
```

#### 7.2.2 캐시 설정
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

### 7.3 모니터링 설정

#### 7.3.1 Actuator 설정
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 7.3.2 로그 설정
```yaml
# logback-spring.xml
<configuration>
    <springProfile name="dev">
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

---

## 🔧 환경설정 체크리스트

### 개발 환경
- [ ] Java 17+ 설치 및 JAVA_HOME 설정
- [ ] Python 3.9+ 설치 및 가상환경 구성
- [ ] Git 클론 및 브랜치 확인
- [ ] 환경변수 설정 (SPRING_PROFILES_ACTIVE=dev)
- [ ] OpenAI API 키 설정 (개발용 더미 키 가능)
- [ ] LAM 서비스 Python 의존성 설치
- [ ] H2 Console 접속 확인

### 프로덕션 환경
- [ ] 서버 리소스 확인 (CPU, Memory, Storage)
- [ ] PostgreSQL 설치 및 데이터베이스 생성
- [ ] Redis 설치 및 설정
- [ ] 실제 OpenAI API 키 설정
- [ ] 보안 설정 (방화벽, SSL 인증서)
- [ ] 시스템 서비스 등록 및 자동 시작 설정
- [ ] 백업 및 모니터링 설정

### Docker 환경
- [ ] Docker 및 Docker Compose 설치
- [ ] .env 파일 설정
- [ ] 컨테이너 리소스 제한 설정
- [ ] 볼륨 마운트 및 데이터 영속성 확인
- [ ] 네트워크 설정 및 포트 노출 확인
- [ ] 컨테이너 간 통신 테스트

---

> **참고**: 이 가이드는 SmartEye v0.1 기준으로 작성되었습니다. 환경설정 중 문제가 발생하면 로그 파일을 확인하고 GitHub Issues에 문의해주세요.
