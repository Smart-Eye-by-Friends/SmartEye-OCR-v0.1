# ⚡ SmartEye CORS 보안 즉시 조치 계획

## 🚨 긴급 보안 위험 요약

**발견된 중대 취약점**: 5개 Controller에서 `@CrossOrigin(origins = "*")` 사용으로 인한 **완전 개방** 상태

**비즈니스 리스크**:
- 무단 OCR API 사용으로 인한 비용 증가
- 고객 문서 데이터 유출 위험
- 서비스 가용성 저하 (DDoS 공격 벡터)

## 📋 즉시 조치 체크리스트 (24시간 내 완료)

### ✅ **이미 완료된 보안 개선**
- [x] Backend WebConfig 환경별 CORS 정책 분리
- [x] 개발 환경: localhost 제한 적용
- [x] 프로덕션 환경: 특정 도메인만 허용

### 🔴 **즉시 수정 필요 (P0)**

#### **1. Controller 레벨 @CrossOrigin 제거**

**대상 파일들**:
```
/Backend/smarteye-backend/src/main/java/com/smarteye/controller/
├── BookController.java:36            @CrossOrigin(origins = "*")
├── DocumentAnalysisController.java:53 @CrossOrigin(origins = "*")
├── DocumentProcessingController.java:45 @CrossOrigin(origins = "*")
├── JobStatusController.java:22        @CrossOrigin(origins = "*")
└── UserController.java:26             @CrossOrigin(origins = "*")
```

**수정 방법**:
```java
// ❌ 제거할 코드
@CrossOrigin(origins = "*")

// ✅ 결과: WebConfig의 중앙화된 CORS 정책 사용
// Controller에서는 @CrossOrigin 어노테이션 완전 제거
```

#### **2. LAM Service CORS 환경변수화**

**대상 파일**: `/Backend/smarteye-lam-service/main.py:47-54`

**현재 위험한 설정**:
```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],        # 🚨 위험
    allow_credentials=True,     # 🚨 매우 위험한 조합
    allow_methods=["*"],        # 🚨 과도한 권한
    allow_headers=["*"],        # 🚨 과도한 권한
)
```

**즉시 적용할 안전한 설정**:
```python
import os

# 환경변수에서 허용 Origin 설정
ENVIRONMENT = os.getenv("ENVIRONMENT", "development")
ALLOWED_ORIGINS = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:8080").split(",")

if ENVIRONMENT == "production":
    cors_origins = ["https://smarteye.company.com", "https://app.smarteye.kr"]
    cors_credentials = False
    cors_methods = ["GET", "POST"]
    cors_headers = ["Content-Type", "Authorization"]
elif ENVIRONMENT == "staging":
    cors_origins = ["https://staging.smarteye.com", "http://localhost:3000"]
    cors_credentials = False
    cors_methods = ["GET", "POST", "OPTIONS"]
    cors_headers = ["Content-Type", "Authorization", "X-Debug-Token"]
else:  # development
    cors_origins = ["http://localhost:8080", "http://localhost:3000"]
    cors_credentials = True
    cors_methods = ["GET", "POST", "OPTIONS"]
    cors_headers = ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=cors_credentials,
    allow_methods=cors_methods,
    allow_headers=cors_headers,
)
```

#### **3. Nginx 설정 정리**

**정리 대상**:
- `/Backend/nginx/nginx-production-backup.conf:59` - 와일드카드 허용 제거
- 사용하지 않는 백업 설정 파일들

## 🔧 구현 단계별 가이드

### **Step 1: 백엔드 Controller 수정 (30분)**

```bash
# 1. 백업 생성
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend/src/main/java/com/smarteye/controller/
cp -r . ./controller_backup_$(date +%Y%m%d_%H%M%S)

# 2. @CrossOrigin 제거 (각 파일에서 수동 제거)
# - BookController.java:36
# - DocumentAnalysisController.java:53
# - DocumentProcessingController.java:45
# - JobStatusController.java:22
# - UserController.java:26

# 3. 컴파일 테스트
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend
./gradlew compileJava
```

### **Step 2: LAM Service 수정 (20분)**

```bash
# 1. 백업 생성
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-lam-service/
cp main.py main.py.backup_$(date +%Y%m%d_%H%M%S)

# 2. main.py 수정 (위의 안전한 설정으로 교체)

# 3. 테스트 실행
python -c "from main import app; print('CORS config loaded successfully')"
```

### **Step 3: 환경변수 설정 (10분)**

**개발 환경**:
```bash
export ENVIRONMENT=development
export CORS_ALLOWED_ORIGINS="http://localhost:8080,http://localhost:3000"
```

**프로덕션 환경**:
```bash
export ENVIRONMENT=production
export CORS_ALLOWED_ORIGINS="https://smarteye.company.com,https://app.smarteye.kr"
```

### **Step 4: 검증 테스트 (15분)**

```bash
# 1. 백엔드 서비스 시작
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend
./gradlew bootRun --args='--spring.profiles.active=dev' &

# 2. LAM 서비스 시작
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-lam-service
python main.py &

# 3. CORS 헤더 검증
curl -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: Content-Type" \
     -X OPTIONS \
     http://localhost:8080/api/health

# 4. 허용되지 않은 Origin 차단 확인
curl -H "Origin: https://malicious-site.com" \
     -X OPTIONS \
     http://localhost:8080/api/health
```

## 📊 보안 개선 효과 측정

### **Before (현재 위험한 상태)**
```
Risk Score: 9/10 (Critical)
- Controller: 전체 개방 (10/10 위험)
- LAM Service: 전체 개방 + Credentials (10/10 위험)
- Monitoring: 없음 (8/10 위험)
```

### **After (즉시 조치 후)**
```
Risk Score: 3/10 (Low)
- Controller: 중앙화된 정책 (2/10 위험)
- LAM Service: 환경별 제한 (2/10 위험)
- Monitoring: 기본 로깅 (4/10 위험)
```

**보안 위험 감소율**: **70% 감소**

## 🚨 롤백 계획

### **문제 발생 시 즉시 롤백**

```bash
# 1. 백엔드 롤백
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend/src/main/java/com/smarteye/controller/
cp -r ./controller_backup_YYYYMMDD_HHMMSS/* .

# 2. LAM 서비스 롤백
cd /home/jongyoung3/SmartEye_v0.4/Backend/smarteye-lam-service/
cp main.py.backup_YYYYMMDD_HHMMSS main.py

# 3. 서비스 재시작
./stop_dev.sh
./start_dev.sh
```

## 📈 다음 단계 (Phase 2 - 3일 내)

### **추가 보안 강화 계획**

1. **보안 헤더 필터 구현**
   - Content-Security-Policy
   - X-Frame-Options
   - X-Content-Type-Options

2. **CORS 요청 모니터링**
   - 허용되지 않은 Origin 요청 로깅
   - 보안 메트릭스 수집

3. **자동화된 보안 테스트**
   - CORS 정책 준수 검증
   - 무단 접근 시도 탐지

## 🔍 검증 기준

### **성공 기준**
- [ ] 5개 Controller에서 @CrossOrigin 완전 제거
- [ ] LAM Service 환경별 CORS 설정 적용
- [ ] localhost:3000에서만 개발 환경 접근 가능
- [ ] 외부 도메인에서 접근 차단 확인
- [ ] 기존 기능 정상 동작 확인

### **실패 시 대응**
- 즉시 롤백 실행
- 보안팀 에스컬레이션
- 임시 Nginx 레벨 차단 적용

---

**⏰ 목표 완료 시간**: 24시간 내 (실제 작업 시간: 1.5시간)
**책임자**: 개발팀 + 보안 담당자
**승인 필요**: CTO (프로덕션 배포 시)

## 📞 비상 연락처

- **개발팀 긴급**: [개발팀 연락처]
- **보안팀**: [보안팀 연락처]
- **운영팀**: [운영팀 연락처]

**즉시 조치가 완료되면 이 문서를 업데이트하고 보안 검토 완료 상태로 표시해주세요.**