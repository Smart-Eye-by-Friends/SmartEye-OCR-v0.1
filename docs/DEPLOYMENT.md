# SmartEye OCR - Deployment Guide

## 🚀 배포 가이드

이 문서는 SmartEye OCR 프로젝트의 프론트엔드와 백엔드를 배포하는 방법을 설명합니다.

---

## 📋 배포 전 체크리스트

### 개발 환경 요구사항

- **Node.js**: 18.x 이상 (프론트엔드)
- **Java**: 17 이상 (백엔드)
- **Maven**: 3.8 이상 또는 Gradle 7.x
- **Git**: 2.x 이상

### 환경별 설정

- **Development**: `localhost:3000` (Frontend), `localhost:8080` (Backend)
- **Staging**: `staging.smarteye-ocr.com`
- **Production**: `smarteye-ocr.com`

---

## 🎯 프론트엔드 배포

### 1. 로컬 빌드

```bash
# 프로젝트 루트에서
cd frontend

# 의존성 설치
npm ci

# 환경 변수 설정
cp .env.example .env.production
# .env.production 파일을 수정하여 프로덕션 API URL 설정

# 프로덕션 빌드
npm run build

# 빌드 결과 확인
ls -la build/
```

### 2. 정적 파일 서버 배포

#### Nginx 설정 예시

```nginx
server {
    listen 80;
    server_name smarteye-ocr.com;

    root /var/www/smarteye-frontend/build;
    index index.html;

    # React Router 지원을 위한 fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 정적 파일 캐싱
    location /static/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API 프록시 (optional)
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Apache 설정 예시

```apache
<VirtualHost *:80>
    ServerName smarteye-ocr.com
    DocumentRoot /var/www/smarteye-frontend/build

    # React Router 지원
    <Directory "/var/www/smarteye-frontend/build">
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted

        # .htaccess for SPA routing
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>
</VirtualHost>
```

### 3. CDN 배포 (AWS CloudFront)

```bash
# AWS CLI를 이용한 S3 업로드
aws s3 sync build/ s3://smarteye-frontend-bucket --delete

# CloudFront 캐시 무효화
aws cloudfront create-invalidation \
  --distribution-id E1234567890 \
  --paths "/*"
```

### 4. Vercel 배포 (추천)

```bash
# Vercel CLI 설치
npm i -g vercel

# 프로젝트 배포
cd frontend
vercel

# 프로덕션 배포
vercel --prod
```

`vercel.json` 설정:

```json
{
  "buildCommand": "npm run build",
  "outputDirectory": "build",
  "framework": "create-react-app",
  "rewrites": [
    {
      "source": "/((?!api/).*)",
      "destination": "/index.html"
    }
  ],
  "env": {
    "REACT_APP_API_URL": "https://api.smarteye-ocr.com"
  }
}
```

---

## ⚙️ 백엔드 배포

### 1. 로컬 빌드

```bash
# 프로젝트 루트에서
cd backend

# Maven 빌드
./mvnw clean package -DskipTests

# 또는 Gradle 빌드
./gradlew build

# JAR 파일 확인
ls -la target/*.jar
```

### 2. 직접 서버 배포

#### systemd 서비스 설정

```ini
# /etc/systemd/system/smarteye-backend.service
[Unit]
Description=SmartEye OCR Backend
After=network.target

[Service]
Type=simple
User=smarteye
WorkingDirectory=/opt/smarteye-backend
ExecStart=/usr/bin/java -jar smarteye-backend-1.0.0.jar
Restart=always
RestartSec=10

Environment=SPRING_PROFILES_ACTIVE=prod
Environment=SERVER_PORT=8080

[Install]
WantedBy=multi-user.target
```

```bash
# 서비스 등록 및 시작
sudo systemctl daemon-reload
sudo systemctl enable smarteye-backend
sudo systemctl start smarteye-backend

# 상태 확인
sudo systemctl status smarteye-backend
```

#### Docker 배포

`Dockerfile`:

```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app

COPY target/smarteye-backend-1.0.0.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
```

`docker-compose.yml`:

```yaml
version: "3.8"

services:
  smarteye-backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/smarteye
    volumes:
      - ./uploads:/app/uploads
      - ./static:/app/static
    depends_on:
      - db
    restart: unless-stopped

  db:
    image: postgres:15
    environment:
      - POSTGRES_DB=smarteye
      - POSTGRES_USER=smarteye
      - POSTGRES_PASSWORD=your_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  postgres_data:
```

```bash
# Docker Compose로 배포
docker-compose up -d

# 로그 확인
docker-compose logs -f smarteye-backend
```

### 3. 클라우드 배포

#### AWS Elastic Beanstalk

```bash
# EB CLI 설치 및 초기화
eb init smarteye-backend

# 환경 생성
eb create production

# 배포
eb deploy

# 환경 변수 설정
eb setenv SPRING_PROFILES_ACTIVE=prod
```

#### Google Cloud Run

```bash
# Docker 이미지 빌드
docker build -t gcr.io/your-project/smarteye-backend .

# 이미지 푸시
docker push gcr.io/your-project/smarteye-backend

# Cloud Run 배포
gcloud run deploy smarteye-backend \
  --image gcr.io/your-project/smarteye-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 8080
```

#### Heroku

```bash
# Heroku CLI로 앱 생성
heroku create smarteye-backend

# 빌드팩 설정
heroku buildpacks:set heroku/java

# 환경 변수 설정
heroku config:set SPRING_PROFILES_ACTIVE=prod

# 배포
git push heroku main
```

---

## 🔧 환경 설정

### 프론트엔드 환경 변수

#### `.env.production`

```env
# API 엔드포인트
REACT_APP_API_URL=https://api.smarteye-ocr.com

# 앱 정보
REACT_APP_VERSION=1.0.0
REACT_APP_ENVIRONMENT=production

# 기능 플래그
REACT_APP_ENABLE_ANALYTICS=true
REACT_APP_ENABLE_ERROR_REPORTING=true

# CDN 설정
REACT_APP_CDN_URL=https://cdn.smarteye-ocr.com
```

### 백엔드 환경 설정

#### `application-prod.yml`

```yaml
server:
  port: 8080
  compression:
    enabled: true
  http2:
    enabled: true

spring:
  profiles:
    active: prod

  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

  web:
    cors:
      allowed-origins:
        - https://smarteye-ocr.com
        - https://www.smarteye-ocr.com
      allowed-methods: "*"
      allowed-headers: "*"

  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/smarteye}
    username: ${DATABASE_USERNAME:smarteye}
    password: ${DATABASE_PASSWORD}

logging:
  level:
    com.smarteye.ocr: INFO
    root: WARN
  file:
    name: /var/log/smarteye/application.log
```

---

## 📊 모니터링 및 로깅

### 1. 애플리케이션 모니터링

#### Prometheus + Grafana

`application.yml`에 추가:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

#### New Relic

```xml
<!-- pom.xml에 추가 -->
<dependency>
    <groupId>com.newrelic.agent.java</groupId>
    <artifactId>newrelic-api</artifactId>
    <version>7.11.0</version>
</dependency>
```

### 2. 로그 수집

#### ELK Stack

```yaml
# docker-compose.yml에 추가
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.5.0
  environment:
    - discovery.type=single-node
  ports:
    - "9200:9200"

logstash:
  image: docker.elastic.co/logstash/logstash:8.5.0
  volumes:
    - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf

kibana:
  image: docker.elastic.co/kibana/kibana:8.5.0
  ports:
    - "5601:5601"
```

---

## 🔐 보안 설정

### 1. HTTPS 설정

#### Let's Encrypt (Certbot)

```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx

# SSL 인증서 발급
sudo certbot --nginx -d smarteye-ocr.com

# 자동 갱신 설정
sudo crontab -e
# 0 12 * * * /usr/bin/certbot renew --quiet
```

### 2. 방화벽 설정

```bash
# UFW 설정
sudo ufw allow 22    # SSH
sudo ufw allow 80    # HTTP
sudo ufw allow 443   # HTTPS
sudo ufw allow 8080  # Backend (필요시)
sudo ufw enable
```

### 3. 백엔드 보안

```yaml
# application-prod.yml
spring:
  security:
    headers:
      frame-options: DENY
      content-type: nosniff
      xss-protection: 1; mode=block

server:
  error:
    include-stacktrace: never
    include-message: never
```

---

## 🚨 트러블슈팅

### 일반적인 문제들

#### 프론트엔드 배포 실패

```bash
# 빌드 에러 확인
npm run build 2>&1 | tee build.log

# 메모리 부족 시
NODE_OPTIONS="--max-old-space-size=4096" npm run build
```

#### 백엔드 메모리 부족

```bash
# JVM 옵션 추가
java -Xms512m -Xmx2g -jar smarteye-backend.jar
```

#### CORS 에러

```yaml
# application.yml에서 확인
spring:
  web:
    cors:
      allowed-origins: "https://your-frontend-domain.com"
```

### 로그 모니터링

```bash
# 실시간 로그 확인
tail -f /var/log/smarteye/application.log

# 에러 로그 필터링
grep ERROR /var/log/smarteye/application.log

# 시스템 리소스 확인
htop
df -h
free -m
```

---

## 📝 배포 체크리스트

### 프론트엔드

- [ ] 프로덕션 빌드 성공
- [ ] 환경 변수 설정 완료
- [ ] API 엔드포인트 연결 확인
- [ ] 브라우저 호환성 테스트
- [ ] 모바일 반응형 확인
- [ ] 성능 최적화 확인

### 백엔드

- [ ] JAR 빌드 성공
- [ ] 데이터베이스 연결 확인
- [ ] API 엔드포인트 테스트
- [ ] CORS 설정 확인
- [ ] 파일 업로드 테스트
- [ ] 로그 설정 확인
- [ ] 모니터링 설정 완료

### 인프라

- [ ] 도메인 설정 완료
- [ ] SSL 인증서 적용
- [ ] 방화벽 설정 완료
- [ ] 백업 설정 완료
- [ ] 모니터링 알림 설정

---

**마지막 업데이트**: 2024년 9월 4일
**담당자**: DevOps 팀
