# 🎓 SmartEyeSsen 무료/저비용 배포 전략 (대학 프로젝트용)

**작성일**: 2025-11-06
**대상**: 대학교 졸업 프로젝트 (비용 최소화)

---

## 🎯 목표

- **비용**: 월 $0-5 또는 일정 기간 완전 무료
- **기간**: 3-6개월 (프로젝트 발표 및 시연용)
- **성능**: 데모 및 소규모 사용자 수용 가능

---

## 📋 무료/저비용 옵션 비교

| 옵션 | 월 비용 | 제약사항 | 기간 | 추천도 |
|------|---------|---------|------|--------|
| **1. Oracle Cloud (Always Free)** | **$0** | CPU 제한, 네트워크 제한 | **영구 무료** | ⭐⭐⭐⭐⭐ |
| **2. GitHub Student Pack** | **$0** | 학생 인증 필요 | **재학 기간** | ⭐⭐⭐⭐⭐ |
| **3. Railway (무료 티어)** | **$0-5** | 500시간/월, 슬립 모드 | **영구** | ⭐⭐⭐⭐ |
| **4. Render (무료 티어)** | **$0** | 15분 비활성 슬립, PostgreSQL 전환 필요 | **영구** | ⭐⭐⭐ |
| **5. Azure for Students** | **$0** | $100 크레딧 (12개월) | **12개월** | ⭐⭐⭐⭐ |
| **6. AWS Educate** | **$0** | $50-100 크레딧 | **12개월** | ⭐⭐⭐ |

---

## 🏆 최종 추천: 조합 전략

### ✅ 전략 1: **Oracle Cloud Always Free + GitHub Pages (완전 무료)** ⭐⭐⭐⭐⭐

**구성**:
```
┌─────────────────────────────────────────────────────────┐
│  GitHub Pages (Frontend)                                 │
│  ├─ React 정적 빌드                                       │
│  └─ 무료, 무제한                                          │
└─────────────────────────────────────────────────────────┘
         │
         │ API 호출
         ▼
┌─────────────────────────────────────────────────────────┐
│  Oracle Cloud Always Free                                │
│  ├─ VM.Standard.E2.1.Micro (1 vCPU, 1GB RAM)            │
│  ├─ Docker: Backend + MySQL                             │
│  └─ 200GB 스토리지, 10TB 아웃바운드/월                   │
└─────────────────────────────────────────────────────────┘
```

**특징**:
- ✅ **완전 무료**: 영구적으로 $0
- ✅ **제한 없음**: 슬립 모드 없음, 24시간 실행
- ✅ **충분한 성능**: 졸업 프로젝트 데모용으로 적합
- ⚠️ **성능 제한**: 1GB RAM (AI 모델 추론 속도 느림)

**예상 비용**: **$0/월** (영구 무료)

---

### ✅ 전략 2: **GitHub Student Pack (최고 옵션)** ⭐⭐⭐⭐⭐

**GitHub Student Developer Pack 혜택**:

| 서비스 | 제공 혜택 | 가치 |
|--------|----------|------|
| **DigitalOcean** | $200 크레딧 (1년) | $200 |
| **Azure** | $100 크레딧 (12개월) | $100 |
| **Heroku** | $13/월 크레딧 (2년) | $312 |
| **Educative** | 6개월 무료 | $60 |
| **GitKraken** | 1년 무료 Pro | $50 |
| **Name.com** | 1년 무료 .live 도메인 | $15 |
| **합계** | | **$737+** |

**신청 방법**:
```bash
# 1. GitHub Student Pack 신청
https://education.github.com/pack

# 필요 서류:
# - 학교 이메일 (@university.edu)
# - 학생증 또는 재학증명서

# 승인 시간: 1-7일
```

**추천 구성** (DigitalOcean $200 크레딧 사용):
```
┌─────────────────────────────────────────────────────────┐
│  DigitalOcean Droplet (4GB RAM, 2 vCPU)                 │
│  ├─ Docker Compose (Backend + MySQL + Nginx)            │
│  ├─ Frontend 정적 파일 (Nginx 서빙)                      │
│  └─ $24/월 → $200 크레딧으로 8개월 무료                  │
└─────────────────────────────────────────────────────────┘
```

**예상 비용**: **$0/월** (8개월 무료, 졸업 프로젝트 충분)

---

### ✅ 전략 3: **Railway 무료 티어** ⭐⭐⭐⭐

**구성**:
```
Railway.app (무료 플랜):
├─ Backend (FastAPI)
├─ MySQL (또는 PostgreSQL)
└─ Frontend (정적 파일)

제약:
- 500 실행 시간/월
- 8GB RAM 제한
- 슬립 모드 (비활성 시)
```

**특징**:
- ✅ **쉬운 배포**: Git push 자동 배포
- ✅ **MySQL 지원**: PostgreSQL 전환 불필요
- ⚠️ **슬립 모드**: 비활성 시 자동 슬립 (첫 요청 시 웨이크업)
- ⚠️ **시간 제한**: 월 500시간 (약 20일)

**예상 비용**: **$0-5/월**

---

## 📋 상세 가이드

### Option 1: Oracle Cloud Always Free 배포 (100% 무료)

#### Step 1: Oracle Cloud 계정 생성

```bash
# 1. Oracle Cloud 가입
https://www.oracle.com/cloud/free/

# 필요 정보:
# - 이메일
# - 신용카드 (인증용, 청구 안 됨)
# - 국가: South Korea

# 2. Always Free Tier 확인
# VM.Standard.E2.1.Micro:
# - 1 vCPU, 1GB RAM
# - 200GB 블록 볼륨 스토리지
# - 10TB 아웃바운드 트래픽/월
```

#### Step 2: VM 인스턴스 생성

```bash
# Oracle Cloud Console → Compute → Instances → Create Instance

# 설정:
Name: smarteyessen-server
Image: Ubuntu 22.04 (Minimal)
Shape: VM.Standard.E2.1.Micro (Always Free)
Boot Volume: 50GB
VCN: 기본 VCN
Subnet: Public Subnet
Public IP: 자동 할당

# SSH 키 생성 (로컬)
ssh-keygen -t rsa -b 4096 -f ~/.ssh/oracle_cloud_key

# 공개 키 업로드
cat ~/.ssh/oracle_cloud_key.pub
# → Oracle Cloud에 복사
```

#### Step 3: 방화벽 설정

```bash
# Oracle Cloud Console → Networking → Virtual Cloud Networks
# → Security Lists → Default Security List

# Ingress Rules 추가:
Source CIDR: 0.0.0.0/0
IP Protocol: TCP
Destination Port: 80, 443

# VM 내부 방화벽도 열기
ssh -i ~/.ssh/oracle_cloud_key ubuntu@<PUBLIC_IP>

sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

#### Step 4: Docker 설치 및 배포

```bash
# Docker 설치
sudo apt-get update
sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker ubuntu

# 프로젝트 클론
git clone https://github.com/yourusername/smarteyessen.git
cd smarteyessen

# 환경 변수 설정 (메모리 최적화)
cp Backend/.env.production Backend/.env
nano Backend/.env

# 메모리 절약 설정:
OPENAI_MAX_CONCURRENCY=5  # 30 → 5
MAX_CONCURRENT_PAGES=2     # 8 → 2

# Docker Compose 수정 (메모리 제한)
nano docker-compose.production.yml
# backend:
#   deploy:
#     resources:
#       limits:
#         memory: 700M

# 배포
docker-compose -f docker-compose.production.yml up -d

# 로그 확인
docker-compose -f docker-compose.production.yml logs -f
```

#### Step 5: 도메인 연결 (선택적)

```bash
# 무료 도메인 옵션:
1. Freenom (무료 .tk, .ml, .ga 도메인)
   https://www.freenom.com

2. DuckDNS (무료 서브도메인)
   https://www.duckdns.org
   예: smarteyessen.duckdns.org

3. GitHub Student Pack Name.com (.live 도메인 1년 무료)

# DNS 설정:
Type: A
Name: @
Value: <Oracle Cloud Public IP>
```

**장점**:
- ✅ **영구 무료**: 계정 유지 시 영구적
- ✅ **24시간 실행**: 슬립 모드 없음
- ✅ **충분한 리소스**: 10TB 아웃바운드/월

**단점**:
- ❌ **메모리 부족**: 1GB RAM (AI 모델 느림)
- ❌ **CPU 제한**: 1 vCPU (동시 처리 제한)

**해결 방법**:
```bash
# 1. Swap 메모리 추가 (2GB)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 2. Docker 메모리 제한 설정
# docker-compose.production.yml
services:
  backend:
    deploy:
      resources:
        limits:
          memory: 700M
        reservations:
          memory: 500M

# 3. OpenAI API 동시 요청 감소
# Backend/.env
OPENAI_MAX_CONCURRENCY=5
MAX_CONCURRENT_PAGES=2
```

---

### Option 2: GitHub Student Pack 활용 (최고 추천)

#### Step 1: GitHub Student Pack 신청

```bash
# 1. GitHub 계정 준비
https://github.com

# 2. Student Pack 신청
https://education.github.com/pack

# 3. 학교 이메일로 인증
# - 학교 이메일 (@university.edu, @ac.kr)
# - 학생증 또는 재학증명서 업로드

# 4. 승인 대기 (1-7일)
```

#### Step 2: DigitalOcean $200 크레딧 사용

```bash
# 1. Student Pack에서 DigitalOcean 활성화
# → $200 크레딧 (1년 유효)

# 2. Droplet 생성
https://cloud.digitalocean.com/droplets/new

# 설정:
Plan: Basic
CPU: Regular (2 vCPU, 4GB RAM) - $24/월
Region: Singapore (한국과 가까움)
OS: Ubuntu 22.04 LTS
SSH: 공개 키 업로드

# 3. SSH 접속
ssh root@<DROPLET_IP>

# 4. 배포 (위의 Oracle Cloud Step 4와 동일)
# Docker 설치 → 프로젝트 클론 → 배포

# 5. 도메인 연결 (Name.com 무료 도메인 사용)
# GitHub Student Pack → Name.com → .live 도메인 1년 무료
```

**크레딧 사용 계산**:
- $24/월 × 8개월 = $192 (4GB Droplet)
- 또는 $18/월 × 11개월 = $198 (2GB Droplet)

**장점**:
- ✅ **충분한 성능**: 4GB RAM, 2 vCPU
- ✅ **8-11개월 무료**: 졸업 프로젝트 충분
- ✅ **제한 없음**: 슬립 모드 없음
- ✅ **쉬운 설정**: UI 친화적

---

### Option 3: Railway 무료 티어

#### Step 1: Railway 계정 생성

```bash
# 1. Railway 가입
https://railway.app

# GitHub 계정으로 로그인

# 2. 무료 플랜 확인
# - $5 크레딧/월
# - 500 실행 시간/월
# - 8GB RAM 제한
```

#### Step 2: 프로젝트 배포

```bash
# 1. GitHub Repository 연결
# Railway Dashboard → New Project → Deploy from GitHub

# 2. 서비스 추가
# - Backend (Dockerfile 자동 감지)
# - MySQL (Railway 제공)

# 3. 환경 변수 설정 (Railway Dashboard)
DB_HOST=${{MYSQL.RAILWAY_PRIVATE_DOMAIN}}
DB_PORT=3306
DB_USER=root
DB_PASSWORD=${{MYSQL_PASSWORD}}
DB_NAME=smarteyessen_db
OPENAI_API_KEY=sk-your-key
CORS_ORIGINS=https://${{RAILWAY_PUBLIC_DOMAIN}}

# 4. Frontend 빌드 및 배포
# Railway Dashboard → Static Site
# Build Command: npm run build
# Publish Directory: dist

# 5. 도메인 자동 생성
# https://smarteyessen-production.up.railway.app
```

**장점**:
- ✅ **쉬운 배포**: Git push 자동 배포
- ✅ **MySQL 지원**: PostgreSQL 전환 불필요
- ✅ **무료 티어**: 월 $5 크레딧

**단점**:
- ❌ **시간 제한**: 500시간/월 (약 20일)
- ❌ **슬립 모드**: 비활성 시 슬립

**해결 방법** (슬립 방지):
```bash
# Uptime Robot 설정 (5분마다 ping)
https://uptimerobot.com
Monitor URL: https://smarteyessen-production.up.railway.app/api/health
Interval: 5분

# 또는 GitHub Actions Cron
# .github/workflows/keep-alive.yml
name: Keep Railway Alive
on:
  schedule:
    - cron: '*/10 * * * *'  # 10분마다
jobs:
  ping:
    runs-on: ubuntu-latest
    steps:
      - run: curl https://smarteyessen-production.up.railway.app/api/health
```

---

### Option 4: Render 무료 티어 (PostgreSQL 전환 필요)

#### 주의사항

Render는 **PostgreSQL만 지원**하므로 MySQL → PostgreSQL 마이그레이션 필요:

```python
# Backend/requirements.txt
# pymysql → psycopg2로 변경
# pymysql==1.1.0 제거
psycopg2-binary==2.9.9

# Backend/.env
# DATABASE_URL 변경
DATABASE_URL=postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}
```

**SQLAlchemy 코드는 변경 불필요** (ORM이 자동 처리)

#### 배포 방법

```bash
# 1. Render 가입
https://render.com

# 2. PostgreSQL 생성 (무료)
New → PostgreSQL
Name: smarteyessen-db
Plan: Free

# 3. Web Service 생성
New → Web Service
Connect Repository: GitHub
Build Command: pip install -r requirements.txt
Start Command: gunicorn app.main:app --workers 2 --worker-class uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000

# 4. 환경 변수 설정
DATABASE_URL=${{DATABASE_URL}}  # PostgreSQL 자동 연결
OPENAI_API_KEY=sk-your-key
CORS_ORIGINS=https://smarteyessen.onrender.com

# 5. Static Site (Frontend)
New → Static Site
Build Command: npm run build
Publish Directory: dist
```

**장점**:
- ✅ **완전 무료**: 슬립 모드 허용 시
- ✅ **자동 배포**: Git push 자동

**단점**:
- ❌ **15분 슬립**: 비활성 시 자동 슬립
- ❌ **PostgreSQL 전환**: MySQL 마이그레이션 필요
- ❌ **느린 웨이크업**: 첫 요청 30-60초 소요

---

## 💰 비용 비교표

| 옵션 | 초기 비용 | 월 비용 | 프로젝트 기간 (6개월) | 비고 |
|------|----------|---------|---------------------|------|
| **Oracle Cloud (Always Free)** | $0 | $0 | **$0** | ⭐ 최저 비용 |
| **GitHub Student Pack (DigitalOcean)** | $0 | $0 | **$0** | ⭐ 최고 성능 |
| **Railway 무료** | $0 | $0-5 | $0-30 | 슬립 모드 |
| **Render 무료** | $0 | $0 | $0 | PostgreSQL 전환 |
| **Azure for Students** | $0 | $0 | $0 | $100 크레딧 |
| **Vultr VPS (유료)** | $0 | $18 | $108 | 비교용 |

---

## 🎯 최종 추천

### 🥇 1순위: **GitHub Student Pack (DigitalOcean)**

**이유**:
- ✅ **최고 성능**: 4GB RAM, 2 vCPU
- ✅ **8개월 무료**: $200 크레딧
- ✅ **제한 없음**: 슬립 모드, 시간 제한 없음
- ✅ **추가 혜택**: 무료 도메인 (.live), Heroku 크레딧 등

**적합한 경우**:
- 학교 이메일이 있는 학생
- 프로젝트 발표, 시연, 포트폴리오용
- 높은 안정성과 성능 필요

### 🥈 2순위: **Oracle Cloud Always Free**

**이유**:
- ✅ **영구 무료**: 졸업 후에도 계속 사용
- ✅ **24시간 실행**: 슬립 모드 없음
- ⚠️ **성능 제한**: 1GB RAM (Swap으로 보완)

**적합한 경우**:
- 학생 인증 불가능한 경우
- 장기적으로 포트폴리오 유지
- 비용 절대 불가

### 🥉 3순위: **Railway 무료 티어**

**이유**:
- ✅ **쉬운 배포**: Git push 자동
- ✅ **MySQL 지원**: 마이그레이션 불필요
- ⚠️ **슬립 모드**: Uptime Robot으로 방지

**적합한 경우**:
- 빠른 배포 원하는 경우
- 3-6개월 단기 프로젝트
- MySQL 유지 필요

---

## 📋 실행 체크리스트

### 🎓 GitHub Student Pack 사용 시

```
□ Phase 1: 준비 (1일)
  □ 1. GitHub 계정 생성
  □ 2. 학교 이메일 준비
  □ 3. 학생증/재학증명서 준비
  □ 4. GitHub Student Pack 신청

□ Phase 2: 크레딧 활성화 (승인 후)
  □ 5. DigitalOcean $200 크레딧 활성화
  □ 6. Name.com .live 도메인 1년 무료 신청

□ Phase 3: 인프라 설정 (1일)
  □ 7. DigitalOcean Droplet 생성 (4GB, $24/월)
  □ 8. SSH 접속 및 방화벽 설정
  □ 9. Docker 설치

□ Phase 4: 프로젝트 배포 (1일)
  □ 10. Git 클론 및 환경 변수 설정
  □ 11. docker-compose.production.yml 실행
  □ 12. SSL 인증서 발급 (Let's Encrypt)

□ Phase 5: 테스트 (1일)
  □ 13. Frontend 접속 확인
  □ 14. 이미지 업로드 테스트
  □ 15. AI 분석 결과 확인
```

### 🆓 Oracle Cloud Always Free 사용 시

```
□ Phase 1: 계정 생성 (1일)
  □ 1. Oracle Cloud 가입 (신용카드 인증)
  □ 2. VM 인스턴스 생성 (Always Free)
  □ 3. SSH 키 생성 및 업로드

□ Phase 2: 네트워크 설정 (1일)
  □ 4. Security List에 80, 443 포트 추가
  □ 5. VM 내부 iptables 설정
  □ 6. 무료 도메인 연결 (DuckDNS 또는 Freenom)

□ Phase 3: 메모리 최적화 (1일)
  □ 7. Swap 메모리 2GB 추가
  □ 8. Docker 메모리 제한 설정
  □ 9. OpenAI API 동시 요청 감소 (30 → 5)

□ Phase 4: 배포 및 테스트 (1일)
  □ 10. Docker Compose 배포
  □ 11. 로그 확인 및 디버깅
  □ 12. 성능 테스트 (느릴 수 있음)
```

---

## 🚀 성공 사례 및 팁

### 💡 메모리 최적화 팁 (1GB RAM 환경)

```bash
# 1. Swap 메모리 추가 (필수)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 2. Docker 메모리 제한
# docker-compose.production.yml
services:
  backend:
    deploy:
      resources:
        limits:
          memory: 700M

  mysql:
    deploy:
      resources:
        limits:
          memory: 200M

# 3. MySQL 최적화
# docker-compose.yml
command:
  - --max-connections=50  # 200 → 50
  - --innodb-buffer-pool-size=128M  # 기본값 줄임

# 4. OpenAI API 최적화
# Backend/.env
OPENAI_MAX_CONCURRENCY=3  # 30 → 3
MAX_CONCURRENT_PAGES=1     # 8 → 1

# 5. Python 메모리 최적화
# Backend/Dockerfile.production
ENV PYTHONUNBUFFERED=1
ENV MALLOC_TRIM_THRESHOLD_=100000
```

### 💡 슬립 모드 방지 (Railway, Render)

```bash
# 방법 1: Uptime Robot (무료, 5분 간격)
https://uptimerobot.com
Monitor Type: HTTP(s)
URL: https://your-app.railway.app/api/health
Interval: 5분

# 방법 2: GitHub Actions Cron
# .github/workflows/keep-alive.yml
name: Keep Alive
on:
  schedule:
    - cron: '*/10 * * * *'  # 10분마다
jobs:
  ping:
    runs-on: ubuntu-latest
    steps:
      - name: Ping API
        run: curl https://your-app.railway.app/api/health

# 방법 3: 외부 Cron 서비스
https://cron-job.org (무료)
```

### 💡 무료 도메인 옵션

```bash
# 1. DuckDNS (무료 서브도메인)
https://www.duckdns.org
예: smarteyessen.duckdns.org

# 2. Freenom (무료 .tk, .ml, .ga 도메인)
https://www.freenom.com
예: smarteyessen.tk

# 3. GitHub Student Pack - Name.com
.live 도메인 1년 무료
예: smarteyessen.live

# 4. Cloudflare Pages (무료 서브도메인)
예: smarteyessen.pages.dev
```

---

## 📊 프로젝트 발표 준비

### 데모 시나리오

```bash
# 1. Frontend 접속
https://smarteyessen.tk (또는 여러분의 도메인)

# 2. 이미지 업로드
- 샘플 워크시트 이미지 준비 (3-5장)
- 업로드 → 레이아웃 분석 → OCR 추출 → AI 설명

# 3. 결과 확인
- 정렬된 텍스트 확인
- AI 생성 설명 확인 (그림, 표)
- DOCX 다운로드

# 4. 성능 메트릭 보여주기
- 처리 시간: 페이지당 20-30초
- 정확도: OCR 95%+
- 비용: $0/월 (무료 티어 사용)
```

### 발표 자료 포인트

```
1. 문제 정의
   - 시각 장애 학생의 학습 자료 접근성

2. 기술 스택
   - Frontend: React + TypeScript
   - Backend: FastAPI + Python
   - AI/ML: DocLayout-YOLO, Tesseract OCR, OpenAI Vision
   - 배포: Oracle Cloud / GitHub Student Pack

3. 주요 기능
   - 레이아웃 분석
   - OCR 텍스트 추출
   - AI 설명 생성
   - 통합 문서 다운로드

4. 비용 효율성
   - 무료 티어 활용
   - 월 $0 운영 (Oracle Cloud Always Free)
   - 또는 $200 크레딧 8개월 사용 (GitHub Student Pack)

5. 성과 및 향후 계획
   - 데모 영상
   - 사용자 피드백
   - 향후 개선 방향
```

---

## 🎓 결론

대학 졸업 프로젝트로 **GitHub Student Pack** 또는 **Oracle Cloud Always Free**를 사용하면 **완전 무료**로 6개월 이상 운영 가능합니다.

**최종 추천 순위**:
1. 🥇 **GitHub Student Pack** (학생 인증 가능 시) - $0, 최고 성능
2. 🥈 **Oracle Cloud Always Free** (영구 무료) - $0, 성능 제한
3. 🥉 **Railway 무료 티어** (빠른 배포) - $0-5, 시간 제한

**예상 총 비용** (6개월 프로젝트):
- GitHub Student Pack: **$0** (DigitalOcean $200 크레딧)
- Oracle Cloud: **$0** (영구 무료)
- Railway: **$0-30** (무료 티어 + 선택적 추가)

---

**작성일**: 2025-11-06
**버전**: 1.0
**문의**: 배포 과정에서 문제 발생 시 질문 환영
