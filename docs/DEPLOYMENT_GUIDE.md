# SmartEye v0.4 - 배포 가이드

이 문서는 SmartEye v0.4 시스템의 프로덕션 배포를 위한 완전한 가이드입니다.

## 📋 목차

1. [배포 개요](#배포-개요)
2. [시스템 요구사항](#시스템-요구사항)
3. [환경 설정](#환경-설정)
4. [보안 구성](#보안-구성)
5. [Docker 배포](#docker-배포)
6. [모니터링 설정](#모니터링-설정)
7. [성능 최적화](#성능-최적화)
8. [백업 및 복구](#백업-및-복구)
9. [운영 및 유지보수](#운영-및-유지보수)
10. [문제 해결](#문제-해결)

---

## 🎯 배포 개요

### 아키텍처 구성도
```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │    (Optional)   │
                    └─────────┬───────┘
                              │
                    ┌─────────▼───────┐
                    │      Nginx      │
                    │  (Reverse Proxy)│
                    └─────────┬───────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
    ┌─────────▼───────┐ ┌─────▼─────┐ ┌─────▼─────┐
    │ React Frontend │ │   Java    │ │  Python   │
    │   (Port 80)    │ │  Backend  │ │    LAM    │
    │                │ │(Port 8080)│ │(Port 8001)│
    └─────────────────┘ └─────┬─────┘ └───────────┘
                              │
                    ┌─────────▼───────┐
                    │   PostgreSQL    │
                    │   (Port 5433)   │
                    └─────────────────┘
```

### 배포 환경
- **개발**: Docker Compose 로컬 개발 환경
- **스테이징**: 단일 서버 Docker 배포
- **프로덕션**: 고가용성 클러스터 (권장)

---

## 💻 시스템 요구사항

### 최소 요구사항
- **CPU**: 4 Core (8 Thread)
- **RAM**: 8GB 
- **Storage**: 100GB SSD
- **Network**: 100Mbps

### 권장 요구사항  
- **CPU**: 8 Core (16 Thread)
- **RAM**: 16GB
- **Storage**: 500GB NVMe SSD
- **Network**: 1Gbps

### 소프트웨어 요구사항
```bash
# Operating System
Ubuntu 20.04+ / CentOS 8+ / RHEL 8+

# Docker
Docker Engine 20.10+
Docker Compose 2.0+

# Optional
Kubernetes 1.20+ (고가용성 배포)
```

### 포트 요구사항
| 서비스 | 포트 | 프로토콜 | 외부 노출 |
|--------|------|---------|-----------|
| Nginx | 80, 443 | HTTP/HTTPS | ✅ |
| Java Backend | 8080 | HTTP | ❌ |
| Python LAM | 8001 | HTTP | ❌ |
| PostgreSQL | 5433 | TCP | ❌ |
| Prometheus | 9090 | HTTP | ❌ |
| Grafana | 3001 | HTTP | ❌ |

---

## ⚙️ 환경 설정

### 1. 프로덕션 환경변수 설정

**필수 환경변수 준비:**
```bash
# 보안 키들
export OPENAI_API_KEY="sk-your-actual-openai-api-key"
export POSTGRES_PASSWORD="your-secure-database-password"

# 도메인 설정
export SMARTEYE_DOMAIN="smarteye.yourdomain.com"
export CORS_ALLOWED_ORIGINS="https://smarteye.yourdomain.com"

# SSL 인증서 경로 (Let's Encrypt 권장)
export SSL_CERT_PATH="/etc/ssl/certs/smarteye.crt"
export SSL_KEY_PATH="/etc/ssl/private/smarteye.key"
```

**환경 설정 스크립트 실행:**
```bash
cd /home/jongyoung3/SmartEye_v0.4

# 프로덕션 환경으로 설정
./scripts/setup-env.sh production

# 보안 검증
./scripts/setup-env.sh check
```

### 2. 도메인 및 DNS 설정

**DNS 레코드 추가:**
```
A     smarteye.yourdomain.com     → 서버_IP_주소
CNAME api.smarteye.yourdomain.com → smarteye.yourdomain.com
```

**서브도메인 구성 (권장):**
```
https://smarteye.yourdomain.com      # 프론트엔드
https://api.smarteye.yourdomain.com  # API 백엔드
https://monitor.smarteye.yourdomain.com # 모니터링
```

---

## 🔐 보안 구성

### 1. SSL/TLS 인증서 설정

**Let's Encrypt 인증서 발급:**
```bash
# Certbot 설치
sudo apt update
sudo apt install certbot python3-certbot-nginx

# 인증서 발급
sudo certbot certonly --nginx -d smarteye.yourdomain.com -d api.smarteye.yourdomain.com

# 자동 갱신 설정
sudo crontab -e
# 추가: 0 12 * * * /usr/bin/certbot renew --quiet
```

**인증서 파일 위치 확인:**
```bash
ls -la /etc/letsencrypt/live/smarteye.yourdomain.com/
# fullchain.pem -> SSL_CERT_PATH
# privkey.pem -> SSL_KEY_PATH
```

### 2. 방화벽 설정

**UFW 방화벽 구성:**
```bash
# UFW 활성화
sudo ufw enable

# 기본 정책
sudo ufw default deny incoming
sudo ufw default allow outgoing

# 필요한 포트만 개방
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS

# 방화벽 상태 확인
sudo ufw status verbose
```

### 3. Docker 보안 강화

**Docker 데몬 보안 설정:**
```bash
# Docker 그룹에 사용자 추가 (신중히)
sudo usermod -aG docker $USER

# Docker 소켓 보안
sudo chmod 660 /var/run/docker.sock

# 불필요한 권한 제거
docker run --user 1001:1001 --read-only ...
```

### 4. 네트워크 보안

**Nginx 보안 헤더 설정:**
```nginx
# /etc/nginx/nginx.conf
server {
    # Security Headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Rate Limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req zone=api burst=20 nodelay;
}
```

---

## 🐳 Docker 배포

### 1. 프로덕션 Docker Compose 파일

**docker-compose.prod.yml 생성:**
```yaml
version: '3.8'

services:
  # Nginx Reverse Proxy
  nginx:
    image: nginx:alpine
    container_name: smarteye-nginx-prod
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.prod.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/ssl:ro
      - frontend-build:/usr/share/nginx/html:ro
    depends_on:
      - backend
      - frontend
    networks:
      - smarteye-network
    restart: unless-stopped

  # Java Backend
  backend:
    build:
      context: ./Backend/smarteye-backend
      dockerfile: Dockerfile
    container_name: smarteye-backend-prod
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseContainerSupport
    volumes:
      - backend-uploads:/app/uploads
      - backend-logs:/app/logs
    depends_on:
      - postgres
      - lam-service
    networks:
      - smarteye-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

  # Python LAM Service
  lam-service:
    build:
      context: ./Backend/smarteye-lam-service
      dockerfile: Dockerfile
    container_name: smarteye-lam-service-prod
    volumes:
      - lam-models:/app/models
      - lam-logs:/app/logs
    networks:
      - smarteye-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8001/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  # React Frontend
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        - REACT_APP_API_URL=/api
        - REACT_APP_ENV=production
    container_name: smarteye-frontend-prod
    volumes:
      - frontend-build:/app/build
    networks:
      - smarteye-network
    restart: unless-stopped

  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: smarteye-postgres-prod
    environment:
      - POSTGRES_DB=${POSTGRES_DB}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    networks:
      - smarteye-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

networks:
  smarteye-network:
    driver: bridge

volumes:
  postgres-data:
    driver: local
  backend-uploads:
    driver: local
  backend-logs:
    driver: local
  lam-models:
    driver: local
  lam-logs:
    driver: local
  frontend-build:
    driver: local
```

### 2. 프로덕션 Nginx 설정

**nginx/nginx.prod.conf:**
```nginx
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    # Logging
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';
    access_log /var/log/nginx/access.log main;

    # Performance
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;

    # Gzip Compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/xml+rss application/json;

    # Rate Limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=upload:10m rate=2r/s;

    # Upstream Backend Services
    upstream smarteye_backend {
        server backend:8080 max_fails=3 fail_timeout=30s;
        keepalive 32;
    }

    upstream smarteye_lam {
        server lam-service:8001 max_fails=3 fail_timeout=30s;
        keepalive 8;
    }

    # HTTP to HTTPS Redirect
    server {
        listen 80;
        server_name smarteye.yourdomain.com api.smarteye.yourdomain.com;
        return 301 https://$server_name$request_uri;
    }

    # Main Frontend Server
    server {
        listen 443 ssl http2;
        server_name smarteye.yourdomain.com;

        # SSL Configuration
        ssl_certificate /etc/ssl/fullchain.pem;
        ssl_certificate_key /etc/ssl/privkey.pem;
        ssl_session_timeout 1d;
        ssl_session_cache shared:MozTLS:10m;
        ssl_session_tickets off;

        # Modern SSL Configuration
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
        ssl_prefer_server_ciphers off;

        # Security Headers
        add_header Strict-Transport-Security "max-age=63072000" always;
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-XSS-Protection "1; mode=block" always;

        # Frontend Static Files
        location / {
            root /usr/share/nginx/html;
            try_files $uri $uri/ /index.html;
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # API Proxy to Backend
        location /api/ {
            limit_req zone=api burst=20 nodelay;
            
            proxy_pass http://smarteye_backend/;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_cache_bypass $http_upgrade;
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 300s;
        }

        # Upload Endpoints (Special handling)
        location ~* /api/document/(analyze|analyze-pdf) {
            limit_req zone=upload burst=5 nodelay;
            
            client_max_body_size 50M;
            proxy_request_buffering off;
            
            proxy_pass http://smarteye_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 300s;
            proxy_read_timeout 600s;
        }
    }

    # API Only Server (Optional)
    server {
        listen 443 ssl http2;
        server_name api.smarteye.yourdomain.com;

        # SSL Configuration (Same as above)
        ssl_certificate /etc/ssl/fullchain.pem;
        ssl_certificate_key /etc/ssl/privkey.pem;

        # API Routes
        location / {
            limit_req zone=api burst=20 nodelay;
            
            proxy_pass http://smarteye_backend/api/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

### 3. 배포 스크립트

**scripts/deploy-production.sh:**
```bash
#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }

# 배포 전 검증
validate_environment() {
    log_info "환경 검증을 시작합니다..."
    
    # 필수 환경변수 확인
    if [ -z "$OPENAI_API_KEY" ]; then
        log_error "OPENAI_API_KEY 환경변수가 설정되지 않았습니다."
        exit 1
    fi
    
    if [ -z "$POSTGRES_PASSWORD" ]; then
        log_error "POSTGRES_PASSWORD 환경변수가 설정되지 않았습니다."
        exit 1
    fi

    # SSL 인증서 확인
    if [ ! -f "$SSL_CERT_PATH" ] || [ ! -f "$SSL_KEY_PATH" ]; then
        log_error "SSL 인증서가 없습니다. Let's Encrypt 인증서를 발급하세요."
        exit 1
    fi

    log_success "환경 검증 완료"
}

# 백업 생성
create_backup() {
    log_info "배포 전 백업을 생성합니다..."
    
    BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
    BACKUP_DIR="./backups/pre_deploy_$BACKUP_DATE"
    
    mkdir -p $BACKUP_DIR
    
    # 데이터베이스 백업
    if docker ps | grep -q smarteye-postgres; then
        docker exec smarteye-postgres pg_dump -U smarteye smarteye_db > $BACKUP_DIR/database.sql
        log_success "데이터베이스 백업 완료"
    fi
    
    # 파일 백업
    if docker ps | grep -q smarteye-backend; then
        docker cp smarteye-backend:/app/uploads $BACKUP_DIR/uploads 2>/dev/null || true
        log_success "파일 백업 완료"
    fi
    
    echo $BACKUP_DATE > $BACKUP_DIR/backup_info.txt
    log_success "백업 생성 완료: $BACKUP_DIR"
}

# 프로덕션 배포
deploy_production() {
    log_info "프로덕션 배포를 시작합니다..."
    
    # 환경 설정
    ./scripts/setup-env.sh production
    
    # 기존 서비스 중지 (Graceful Shutdown)
    if [ -f "docker-compose.prod.yml" ]; then
        log_info "기존 서비스를 중지합니다..."
        docker-compose -f docker-compose.prod.yml down --timeout 60
    fi
    
    # 이미지 빌드
    log_info "이미지를 빌드합니다..."
    docker-compose -f docker-compose.prod.yml build --no-cache
    
    # 서비스 시작
    log_info "서비스를 시작합니다..."
    docker-compose -f docker-compose.prod.yml up -d
    
    # 헬스체크 대기
    log_info "서비스 시작을 대기합니다..."
    sleep 30
    
    # 서비스 상태 확인
    check_services
    
    log_success "프로덕션 배포 완료!"
}

# 서비스 상태 확인
check_services() {
    log_info "서비스 상태를 확인합니다..."
    
    # Backend 헬스체크
    if curl -f http://localhost:8080/api/health &>/dev/null; then
        log_success "Backend 서비스 정상"
    else
        log_error "Backend 서비스 오류"
        return 1
    fi
    
    # LAM 서비스 헬스체크
    if curl -f http://localhost:8001/health &>/dev/null; then
        log_success "LAM 서비스 정상"
    else
        log_warning "LAM 서비스 오류 (확인 필요)"
    fi
    
    # HTTPS 연결 확인
    if curl -f -k https://localhost/api/health &>/dev/null; then
        log_success "HTTPS 연결 정상"
    else
        log_warning "HTTPS 연결 확인 필요"
    fi
}

# 롤백 함수
rollback() {
    local backup_date=$1
    
    if [ -z "$backup_date" ]; then
        log_error "롤백할 백업 날짜를 지정하세요."
        echo "사용법: $0 rollback YYYYMMDD_HHMMSS"
        exit 1
    fi
    
    BACKUP_DIR="./backups/pre_deploy_$backup_date"
    
    if [ ! -d "$BACKUP_DIR" ]; then
        log_error "백업 디렉토리를 찾을 수 없습니다: $BACKUP_DIR"
        exit 1
    fi
    
    log_warning "롤백을 시작합니다: $backup_date"
    
    # 현재 서비스 중지
    docker-compose -f docker-compose.prod.yml down
    
    # 데이터베이스 복원
    if [ -f "$BACKUP_DIR/database.sql" ]; then
        log_info "데이터베이스를 복원합니다..."
        cat $BACKUP_DIR/database.sql | docker exec -i smarteye-postgres psql -U smarteye -d smarteye_db
    fi
    
    # 파일 복원
    if [ -d "$BACKUP_DIR/uploads" ]; then
        log_info "업로드 파일을 복원합니다..."
        docker cp $BACKUP_DIR/uploads smarteye-backend:/app/uploads
    fi
    
    log_success "롤백 완료"
}

# 메인 실행 로직
case "${1:-deploy}" in
    "deploy")
        validate_environment
        create_backup
        deploy_production
        ;;
    "backup")
        create_backup
        ;;
    "rollback")
        rollback $2
        ;;
    "check")
        check_services
        ;;
    *)
        echo "SmartEye v0.4 프로덕션 배포 스크립트"
        echo ""
        echo "사용법:"
        echo "  $0 deploy                    # 프로덕션 배포"
        echo "  $0 backup                    # 백업 생성"
        echo "  $0 rollback YYYYMMDD_HHMMSS  # 롤백"
        echo "  $0 check                     # 서비스 상태 확인"
        exit 1
        ;;
esac
```

**스크립트 실행 권한 부여:**
```bash
chmod +x scripts/deploy-production.sh
```

### 4. 배포 실행

**프로덕션 배포:**
```bash
# 환경변수 설정
export OPENAI_API_KEY="your-actual-api-key"
export POSTGRES_PASSWORD="secure-database-password"
export SSL_CERT_PATH="/etc/letsencrypt/live/smarteye.yourdomain.com/fullchain.pem"
export SSL_KEY_PATH="/etc/letsencrypt/live/smarteye.yourdomain.com/privkey.pem"

# 배포 실행
./scripts/deploy-production.sh deploy
```

**배포 확인:**
```bash
# 서비스 상태 확인
./scripts/deploy-production.sh check

# 웹 브라우저에서 확인
# https://smarteye.yourdomain.com
# https://api.smarteye.yourdomain.com/health
```

---

## 📊 모니터링 설정

### 1. 프로덕션 모니터링 배포

**monitoring/docker-compose.monitoring.prod.yml:**
```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v2.45.0
    container_name: smarteye-prometheus-prod
    ports:
      - "127.0.0.1:9090:9090"  # 로컬에서만 접근
    volumes:
      - ./monitoring/prometheus/prometheus.prod.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=30d'
      - '--storage.tsdb.retention.size=50GB'
      - '--web.enable-lifecycle'
    restart: unless-stopped
    networks:
      - smarteye-network

  grafana:
    image: grafana/grafana:10.0.0
    container_name: smarteye-grafana-prod
    ports:
      - "127.0.0.1:3001:3000"  # 로컬에서만 접근
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-smarteye2024}
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_INSTALL_PLUGINS=grafana-piechart-panel,grafana-worldmap-panel
      - GF_SERVER_DOMAIN=monitor.smarteye.yourdomain.com
      - GF_SERVER_ROOT_URL=https://monitor.smarteye.yourdomain.com/
    restart: unless-stopped
    networks:
      - smarteye-network

  # Node Exporter for System Metrics
  node-exporter:
    image: prom/node-exporter:v1.6.0
    container_name: smarteye-node-exporter
    ports:
      - "127.0.0.1:9100:9100"
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - '--path.procfs=/host/proc'
      - '--path.rootfs=/rootfs'
      - '--path.sysfs=/host/sys'
      - '--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)'
    restart: unless-stopped
    networks:
      - smarteye-network

  # cAdvisor for Container Metrics
  cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.47.0
    container_name: smarteye-cadvisor-prod
    ports:
      - "127.0.0.1:8080:8080"
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
      - /dev/disk/:/dev/disk:ro
    privileged: true
    devices:
      - /dev/kmsg
    restart: unless-stopped
    networks:
      - smarteye-network

  # AlertManager for Alerts
  alertmanager:
    image: prom/alertmanager:v0.25.0
    container_name: smarteye-alertmanager
    ports:
      - "127.0.0.1:9093:9093"
    volumes:
      - ./monitoring/alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - alertmanager-data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
      - '--web.external-url=http://localhost:9093'
    restart: unless-stopped
    networks:
      - smarteye-network

networks:
  smarteye-network:
    external: true

volumes:
  prometheus-data:
    driver: local
  grafana-data:
    driver: local
  alertmanager-data:
    driver: local
```

### 2. 알림 설정

**monitoring/alertmanager/alertmanager.yml:**
```yaml
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alerts@yourdomain.com'
  smtp_auth_username: 'alerts@yourdomain.com'
  smtp_auth_password: 'your-smtp-password'

templates:
  - '/etc/alertmanager/templates/*.tmpl'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'

receivers:
  - name: 'web.hook'
    email_configs:
      - to: 'admin@yourdomain.com'
        subject: 'SmartEye Alert: {{ .GroupLabels.alertname }}'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          Instance: {{ .Labels.instance }}
          Severity: {{ .Labels.severity }}
          {{ end }}

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'dev', 'instance']
```

### 3. 모니터링 배포

```bash
# 모니터링 스택 배포
docker-compose -f monitoring/docker-compose.monitoring.prod.yml up -d

# Nginx에 모니터링 라우트 추가 (선택사항)
# monitor.smarteye.yourdomain.com -> Grafana
```

---

## 🚀 성능 최적화

### 1. 시스템 레벨 최적화

**커널 파라미터 튜닝:**
```bash
# /etc/sysctl.conf
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_fin_timeout = 30
vm.swappiness = 10
vm.max_map_count = 262144

# 적용
sudo sysctl -p
```

**파일 디스크립터 제한 증가:**
```bash
# /etc/security/limits.conf
* soft nofile 65535
* hard nofile 65535

# 재로그인 후 확인
ulimit -n
```

### 2. Docker 최적화

**Docker 데몬 설정:**
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 65536,
      "Soft": 65536
    }
  },
  "max-concurrent-downloads": 10,
  "max-concurrent-uploads": 5
}
```

### 3. 애플리케이션 최적화

**Java 백엔드 JVM 튜닝:**
```bash
# .env.production
JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+UseCompressedOops -Djava.security.egd=file:/dev/./urandom"

# 연결 풀 최적화
DB_POOL_SIZE=30
DB_MIN_IDLE=10
DB_MAX_LIFETIME=1800000

# 스레드 풀 최적화  
MAX_THREADS=500
MIN_SPARE_THREADS=50
```

**PostgreSQL 튜닝:**
```sql
-- postgresql.conf 추가 설정
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB
maintenance_work_mem = 64MB
max_connections = 200
```

### 4. 네트워크 최적화

**Nginx 성능 튜닝:**
```nginx
# nginx.conf
worker_processes auto;
worker_rlimit_nofile 65535;
worker_connections 4096;

# HTTP/2 활성화
listen 443 ssl http2;

# Keep-alive 최적화
keepalive_timeout 120s;
keepalive_requests 10000;

# 버퍼 크기 최적화
client_body_buffer_size 128k;
client_max_body_size 50m;
proxy_buffers 16 32k;
proxy_buffer_size 64k;
```

---

## 💾 백업 및 복구

### 1. 자동화된 백업 시스템

**scripts/backup-automated.sh:**
```bash
#!/bin/bash

BACKUP_BASE_DIR="/var/backups/smarteye"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE_DIR/$DATE"

mkdir -p $BACKUP_DIR

# 데이터베이스 백업 (압축)
docker exec smarteye-postgres-prod pg_dump -U smarteye smarteye_db | gzip > $BACKUP_DIR/database.sql.gz

# 파일 백업
docker cp smarteye-backend-prod:/app/uploads $BACKUP_DIR/uploads
tar -czf $BACKUP_DIR/uploads.tar.gz -C $BACKUP_DIR uploads && rm -rf $BACKUP_DIR/uploads

# 설정 파일 백업
cp -r ./monitoring $BACKUP_DIR/
cp docker-compose.prod.yml $BACKUP_DIR/
cp -r nginx $BACKUP_DIR/

# 백업 정보 파일
cat > $BACKUP_DIR/backup_info.txt << EOF
Backup Date: $DATE
System: $(uname -a)
Docker Version: $(docker --version)
SmartEye Version: $(cat VERSION 2>/dev/null || echo "Unknown")
Backup Size: $(du -sh $BACKUP_DIR | cut -f1)
EOF

# 오래된 백업 삭제 (30일 이상)
find $BACKUP_BASE_DIR -type d -name "20*" -mtime +30 -exec rm -rf {} +

echo "Backup completed: $BACKUP_DIR"

# S3나 다른 클라우드 저장소에 업로드 (선택사항)
# aws s3 sync $BACKUP_DIR s3://your-backup-bucket/smarteye/$DATE
```

### 2. 크론탭 백업 설정

```bash
# 크론탭 편집
crontab -e

# 매일 오전 2시 백업
0 2 * * * /home/ubuntu/SmartEye_v0.4/scripts/backup-automated.sh >> /var/log/smarteye-backup.log 2>&1

# 매주 일요일 오전 3시 전체 시스템 백업
0 3 * * 0 /home/ubuntu/SmartEye_v0.4/scripts/full-system-backup.sh >> /var/log/smarteye-full-backup.log 2>&1
```

### 3. 복구 절차

**전체 시스템 복구:**
```bash
# 백업에서 복구
BACKUP_DATE="20241201_020000"
BACKUP_DIR="/var/backups/smarteye/$BACKUP_DATE"

# 서비스 중지
docker-compose -f docker-compose.prod.yml down

# 데이터베이스 복구
zcat $BACKUP_DIR/database.sql.gz | docker exec -i smarteye-postgres-prod psql -U smarteye -d smarteye_db

# 파일 복구
tar -xzf $BACKUP_DIR/uploads.tar.gz -C /tmp
docker cp /tmp/uploads smarteye-backend-prod:/app/

# 서비스 시작
docker-compose -f docker-compose.prod.yml up -d
```

---

## 🔧 운영 및 유지보수

### 1. 로그 관리

**중앙 집중식 로깅 설정:**
```yaml
# docker-compose.prod.yml에 추가
  fluentd:
    image: fluent/fluentd:v1.16-1
    container_name: smarteye-fluentd
    volumes:
      - ./logging/fluent.conf:/fluentd/etc/fluent.conf
    ports:
      - "127.0.0.1:24224:24224"
    restart: unless-stopped

# 각 서비스에 로그 드라이버 추가
    logging:
      driver: fluentd
      options:
        fluentd-address: localhost:24224
        tag: smarteye.{{.Name}}
```

### 2. 업데이트 절차

**무중단 업데이트 스크립트:**
```bash
#!/bin/bash
# scripts/rolling-update.sh

# 새 버전 이미지 빌드
docker-compose -f docker-compose.prod.yml build

# 서비스별 순차 업데이트
services=("lam-service" "backend" "frontend")

for service in "${services[@]}"; do
    echo "Updating $service..."
    
    # 새 컨테이너 시작
    docker-compose -f docker-compose.prod.yml up -d --no-deps --force-recreate $service
    
    # 헬스체크 대기
    sleep 30
    
    # 상태 확인
    if ! docker-compose -f docker-compose.prod.yml ps $service | grep -q "Up"; then
        echo "Update failed for $service"
        exit 1
    fi
done

echo "Rolling update completed successfully"
```

### 3. 모니터링 대시보드

**주요 메트릭 모니터링:**
- **시스템 메트릭**: CPU, 메모리, 디스크, 네트워크
- **애플리케이션 메트릭**: 응답 시간, 처리량, 에러율
- **비즈니스 메트릭**: 분석 작업 수, 성공률, 사용자 활동

**알림 설정:**
```yaml
# prometheus/alerts.yml
groups:
  - name: smarteye
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 5m
        annotations:
          summary: High error rate detected

      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 2
        for: 5m
        annotations:
          summary: High response time detected
```

### 4. 보안 유지보수

**정기 보안 점검:**
```bash
#!/bin/bash
# scripts/security-audit.sh

# 컨테이너 보안 스캔
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image smarteye-backend-prod

# SSL 인증서 만료 확인
openssl x509 -in /etc/letsencrypt/live/smarteye.yourdomain.com/cert.pem -noout -dates

# 포트 스캔 (외부에서)
nmap -p 80,443 smarteye.yourdomain.com

# 로그 이상 패턴 감지
grep -E "(failed|error|attack|unauthorized)" /var/log/nginx/access.log | tail -100
```

---

## 🆘 문제 해결

### 1. 일반적인 배포 문제

**서비스가 시작되지 않는 경우:**
```bash
# 컨테이너 상태 확인
docker-compose -f docker-compose.prod.yml ps

# 로그 확인
docker-compose -f docker-compose.prod.yml logs backend

# 네트워크 확인
docker network inspect smarteye-network

# 포트 충돌 확인
sudo lsof -i :80 -i :443 -i :8080
```

**데이터베이스 연결 문제:**
```bash
# PostgreSQL 연결 테스트
docker exec smarteye-postgres-prod psql -U smarteye -d smarteye_db -c "SELECT version();"

# 연결 수 확인
docker exec smarteye-postgres-prod psql -U smarteye -d smarteye_db -c "SELECT count(*) FROM pg_stat_activity;"

# 데이터베이스 재시작
docker-compose -f docker-compose.prod.yml restart postgres
```

### 2. 성능 문제 진단

**리소스 사용량 모니터링:**
```bash
# 시스템 리소스
htop
iotop
nethogs

# Docker 리소스
docker stats
docker system df
```

**병목 지점 분석:**
```bash
# Java 백엔드 스레드 덤프
docker exec smarteye-backend-prod jstack 1

# PostgreSQL 활성 쿼리
docker exec smarteye-postgres-prod psql -U smarteye -d smarteye_db -c "SELECT * FROM pg_stat_activity WHERE state = 'active';"
```

### 3. 복구 절차

**긴급 복구:**
```bash
#!/bin/bash
# scripts/emergency-recovery.sh

# 최신 백업으로 즉시 복구
LATEST_BACKUP=$(ls -t /var/backups/smarteye/ | head -n1)

echo "Emergency recovery using backup: $LATEST_BACKUP"

# 서비스 중지
docker-compose -f docker-compose.prod.yml down

# 데이터베이스 복구
zcat /var/backups/smarteye/$LATEST_BACKUP/database.sql.gz | docker exec -i smarteye-postgres-prod psql -U smarteye -d smarteye_db

# 서비스 시작
docker-compose -f docker-compose.prod.yml up -d

echo "Emergency recovery completed"
```

### 4. 24/7 모니터링 설정

**Uptime 모니터링:**
```bash
# 외부 서비스 모니터링 (예: UptimeRobot, Pingdom)
# 또는 자체 헬스체크 스크립트

#!/bin/bash
# scripts/health-monitor.sh

ENDPOINTS=(
  "https://smarteye.yourdomain.com"
  "https://api.smarteye.yourdomain.com/health"
)

for endpoint in "${ENDPOINTS[@]}"; do
  if ! curl -f -s "$endpoint" > /dev/null; then
    echo "ALERT: $endpoint is down" | mail -s "SmartEye Service Alert" admin@yourdomain.com
  fi
done
```

---

## 📚 추가 자료

### 체크리스트

**배포 전 체크리스트:**
- [ ] SSL 인증서 발급 및 설치
- [ ] 환경변수 설정 (OPENAI_API_KEY, POSTGRES_PASSWORD)
- [ ] 방화벽 규칙 설정
- [ ] DNS 레코드 설정
- [ ] 백업 시스템 구성
- [ ] 모니터링 설정
- [ ] 로그 로테이션 설정

**배포 후 체크리스트:**
- [ ] 모든 서비스 헬스체크 통과
- [ ] HTTPS 접속 확인
- [ ] API 기능 테스트
- [ ] 백업 시스템 테스트
- [ ] 모니터링 대시보드 확인
- [ ] 알림 시스템 테스트

### 연락처 및 지원

- **기술 지원**: tech-support@yourdomain.com
- **긴급 장애**: emergency@yourdomain.com
- **문서 업데이트**: docs@yourdomain.com

---

이 배포 가이드를 통해 SmartEye v0.4를 안전하고 확장 가능한 프로덕션 환경에 성공적으로 배포하시기 바랍니다. 추가적인 지원이 필요하시면 언제든 연락주세요! 🚀