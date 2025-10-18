# LAM 서비스 장애 분석 및 복구 계획서

**작성일**: 2025-10-17
**장애 발생 시간**: 2025-10-17 09:32:40
**보고서 번호**: SRE-LAM-001
**심각도**: 🔴 CRITICAL

---

## 📋 Executive Summary

LAM 모델 교체 후 Swagger UI를 통한 테스트 실행 시, LAM 서비스가 **모델 로드 실패**(500 Internal Server Error)로 인해 응답하지 못하고, 백엔드 시스템이 Circuit Breaker Fallback 모드로 전환되어 빈 분석 결과를 반환하는 심각한 장애가 발생했습니다.

**핵심 원인**: LAM 서비스의 **DocLayout-YOLO 모델 로딩 실패**

**영향 범위**:
- ✅ 백엔드 서비스: 정상 동작 (Fallback 메커니즘 작동)
- ❌ LAM 서비스: 완전 장애 (모델 로드 실패)
- ⚠️ 분석 결과: `total_questions: 0` (빈 결과)

---

## 1. 장애 현상 요약

### 1.1 발생 시점 및 경로
- **일시**: 2025-10-17 09:32:40 - 09:32:50 (약 10초 소요)
- **트리거**: Swagger UI (`http://localhost:8080/swagger-ui/index.html`)를 통한 `/api/analysis/cim-only` 엔드포인트 테스트
- **입력 파일**: `쎈 수학1-1_페이지_016.jpg` (3445x4736 해상도)

### 1.2 관찰된 증상
1. **Symptom 1 (최종 결과)**: `response_1760661170868.json`
   ```json
   {
     "success": true,  // ⚠️ Fallback 성공으로 표시되었으나 실제로는 장애
     "total_questions": 0,  // ❌ 문제 탐지 실패
     "total_elements": 0,   // ❌ 레이아웃 요소 탐지 실패
     "formattedText": "=== 분석 결과 ===\n\n분석된 문제가 없습니다.\n"
   }
   ```

2. **Symptom 2 (시각 증거)**: `layout_viz_*.png`
   - **관찰 내용**: 원본 이미지에 아무런 경계 상자(bounding box)가 그려지지 않음
   - **예상 행동**: 7개 문제(001~007)의 경계 상자가 표시되어야 함
   - **실제 행동**: 빈 이미지 (레이아웃 탐지 0건)

3. **Symptom 3 (백엔드 로그)**: **Line 27193-27195** (가장 중요한 증거)
   ```
   2025-10-17 09:32:47 - LAM 서비스 HTTP 오류: 500 INTERNAL_SERVER_ERROR
   2025-10-17 09:32:47 - LAM 서비스 호출 실패: LAM 서비스 오류 [500 INTERNAL_SERVER_ERROR]:
       {"detail":"모델 로드에 실패했습니다."}
   ```

---

## 2. 근본 원인 분석 (Root Cause Analysis)

### 2.1 장애 지점 식별

**백엔드 로그 Line 27189-27258**에서 발견한 **구체적인 오류 메시지**:

```
Line 27189: LAM 서비스 호출 시작 - URL: http://localhost:8001/analyze-layout
Line 27192: [40d6fa31] [348d9422-1] Response 500 INTERNAL_SERVER_ERROR
Line 27193: LAM 서비스 HTTP 오류: 500 INTERNAL_SERVER_ERROR
Line 27195: LAM 서비스 호출 실패: LAM 서비스 오류 [500 INTERNAL_SERVER_ERROR]:
             {"detail":"모델 로드에 실패했습니다."}
```

**오류 전파 경로**:
```
LAM Service (FastAPI)
    ↓ 모델 로드 실패 (best.pt)
    ↓ 500 Internal Server Error 반환
Backend (Spring Boot - LAMServiceClient.java:111)
    ↓ Exception 포착
    ↓ Circuit Breaker 작동 (Line 27258)
Fallback 메커니즘
    ↓ 빈 레이아웃 데이터 생성 (Line 27260)
최종 결과
    ↓ total_questions: 0
```

### 2.2 근본 원인 (Root Cause)

**가설 검증 결과**:

| 가설 | 증거 | 결론 |
|------|------|------|
| **가설 A**: 네트워크/서비스 다운 | ❌ "Connection refused" 없음 | **기각** |
| **가설 B**: 모델 로딩 실패 | ✅ **"모델 로드에 실패했습니다."** | **✅ 확정** |
| **가설 C**: 타임아웃 | ❌ "Timeout" 명시 없음 | **부분 기각** (500 에러가 먼저 발생) |

**확정된 근본 원인**:
```
🎯 ROOT CAUSE: LAM 서비스의 DocLayout-YOLO 모델 (best.pt) 로딩 실패
```

**가능한 하위 원인 (Sub-causes)**:
1. **모델 파일 손상**: 교체된 `best.pt` 파일이 손상되었거나 불완전
2. **모델 버전 불일치**: 새 `best.pt`가 기존 DocLayout-YOLO 코드베이스와 호환되지 않음
3. **메모리 부족**: YOLO 모델 로드 시 필요한 메모리 (예상: 1-2GB)가 부족
4. **파일 권한 문제**: Docker 컨테이너 내에서 `best.pt` 파일 읽기 권한 없음
5. **경로 문제**: `main.py`에서 `best.pt` 파일 경로가 잘못 설정됨

### 2.3 시각적 증거와 결과의 연관성

**논리적 인과 관계**:

```
1. LAM 서비스 모델 로드 실패
   ↓
2. LAM 서비스 → 백엔드에 500 에러 반환
   ↓
3. 백엔드 LAMServiceClient → Exception 포착
   ↓
4. Circuit Breaker 작동 (Line 27258)
   "LAM 서비스 Circuit Breaker 작동 - Fallback 실행"
   ↓
5. Fallback 메커니즘 실행 (Line 27260)
   "LAM 서비스 실패 - 개선된 Fallback 결과 생성: 4개의 다양한 레이아웃 영역"
   (주의: 실제로는 의미 없는 더미 데이터)
   ↓
6. 빈 레이아웃 분석 결과 → CIM 프로세서
   ↓
7. 최종 JSON 응답
   - total_questions: 0
   - total_elements: 0
   - formattedText: "분석된 문제가 없습니다."
   ↓
8. 시각화 이미지 생성
   - 빈 경계 상자 (레이아웃 요소 0건)
   - Fallback 더미 데이터는 시각화에 반영되지 않음
```

**핵심 로직**:
- LAM 서비스로부터 **응답을 받지 못하자** (500 Error), 백엔드의 **Circuit Breaker Fallback 메커니즘이 동작**
- Fallback은 **빈 레이아웃 분석 결과**를 기반으로 후속 작업 진행
- 최종적으로 **Fallback 시각화 이미지**와 **실패한 JSON**을 생성

---

## 3. 단계별 복구 계획 (Action Plan)

### 3.1 1단계: 즉시 조치 (Immediate Actions) - 5분

**목표**: LAM 서비스 상태 진단 및 컨테이너 로그 확인

#### 3.1.1 LAM 서비스 컨테이너 로그 확인
```bash
# LAM 서비스 로그 확인 (최근 100줄)
docker logs smarteye-lam-service --tail 100

# 실시간 로그 모니터링 (별도 터미널)
docker logs -f smarteye-lam-service
```

**찾아야 할 핵심 키워드**:
- `모델 로드에 실패했습니다.`
- `FileNotFoundError`
- `PermissionError`
- `RuntimeError: CUDA out of memory` (메모리 부족)
- `model loading error`
- `best.pt`

#### 3.1.2 LAM 서비스 컨테이너 상태 확인
```bash
# 컨테이너 실행 상태 확인
docker ps -a | grep smarteye-lam-service

# 컨테이너 리소스 사용량 확인
docker stats smarteye-lam-service --no-stream

# 컨테이너 내부 파일 시스템 확인
docker exec smarteye-lam-service ls -lah /app/models/best.pt
```

**예상 결과**:
- ✅ 정상: `-rw-r--r-- 1 root root 123M ... best.pt`
- ❌ 이상: `No such file or directory` 또는 `Permission denied`

#### 3.1.3 모델 파일 무결성 검증
```bash
# 모델 파일 크기 확인
docker exec smarteye-lam-service stat /app/models/best.pt

# 모델 파일 MD5 체크섬 확인 (교체 전과 비교)
docker exec smarteye-lam-service md5sum /app/models/best.pt
```

---

### 3.2 2단계: 원인 해결 (Root Cause Fix) - 30분

**진단 결과에 따른 분기 처리**:

#### 케이스 A: 모델 파일이 존재하지 않거나 손상된 경우

**복구 방법**:
```bash
# 1. 백업된 원본 모델 파일 확인
ls -lh Backend/smarteye-lam-service/models/

# 2. LAM 서비스 컨테이너 재빌드 (모델 파일 재복사)
cd Backend
docker-compose stop smarteye-lam-service
docker-compose rm -f smarteye-lam-service

# 3. 이미지 재빌드 (--no-cache 옵션으로 완전 재빌드)
docker-compose build --no-cache smarteye-lam-service

# 4. 서비스 재시작
docker-compose up -d smarteye-lam-service

# 5. 로그 모니터링
docker logs -f smarteye-lam-service
```

**검증 기준**:
- ✅ 성공: `모델 로드 완료` 또는 `Model loaded successfully` 로그 출력
- ❌ 실패: 다시 `모델 로드에 실패했습니다.` 출력

#### 케이스 B: 메모리 부족 (CUDA/RAM 부족)

**복구 방법**:

1. **Docker 메모리 할당 증가** (`docker-compose.yml` 수정)
   ```yaml
   # Backend/docker-compose.yml
   smarteye-lam-service:
     image: smarteye-lam-service:latest
     deploy:
       resources:
         limits:
           memory: 4G  # 2G → 4G로 증가
           cpus: '2.0'
         reservations:
           memory: 2G  # 1G → 2G로 증가
   ```

2. **서비스 재시작**
   ```bash
   cd Backend
   docker-compose down
   docker-compose up -d
   ```

#### 케이스 C: 모델 버전 불일치 (호환성 문제)

**복구 방법**:

1. **LAM 서비스 Python 의존성 확인**
   ```bash
   docker exec smarteye-lam-service pip list | grep -E "torch|ultralytics|yolo"
   ```

2. **`main.py` 모델 로딩 코드 검증**
   ```bash
   # main.py의 모델 로딩 섹션 확인
   docker exec smarteye-lam-service cat /app/main.py | grep -A 20 "load.*model"
   ```

3. **필요시 `requirements.txt` 버전 명시 및 재설치**
   ```bash
   # 예시: ultralytics 버전 고정
   # Backend/smarteye-lam-service/requirements.txt
   ultralytics==8.0.196  # 특정 버전 명시

   # 컨테이너 재빌드
   docker-compose build --no-cache smarteye-lam-service
   docker-compose up -d smarteye-lam-service
   ```

#### 케이스 D: Python 코드 예외 처리 부족

**복구 방법**: `main.py`에 상세한 예외 처리 로직 추가

```python
# Backend/smarteye-lam-service/main.py (모델 로딩 섹션)

import logging
from ultralytics import YOLO

logger = logging.getLogger(__name__)

try:
    logger.info("모델 로드 시작: /app/models/best.pt")
    model = YOLO("/app/models/best.pt")
    logger.info(f"모델 로드 성공 - 모델 타입: {type(model)}, 클래스 수: {len(model.names)}")
except FileNotFoundError as e:
    logger.error(f"❌ 모델 파일을 찾을 수 없습니다: {e}")
    raise RuntimeError("모델 파일이 존재하지 않습니다. /app/models/best.pt 경로를 확인하세요.")
except PermissionError as e:
    logger.error(f"❌ 모델 파일 읽기 권한이 없습니다: {e}")
    raise RuntimeError("모델 파일 권한을 확인하세요.")
except RuntimeError as e:
    if "CUDA out of memory" in str(e):
        logger.error(f"❌ GPU 메모리 부족: {e}")
        raise RuntimeError("CUDA 메모리 부족. CPU 모드로 전환하거나 메모리를 증설하세요.")
    else:
        logger.error(f"❌ 모델 로딩 중 런타임 에러: {e}")
        raise
except Exception as e:
    logger.error(f"❌ 예기치 않은 에러 발생: {type(e).__name__} - {e}")
    raise RuntimeError(f"모델 로드에 실패했습니다: {e}")
```

**적용 방법**:
```bash
# 1. main.py 수정 (위 코드 적용)
# 2. LAM 서비스 재빌드 및 재시작
cd Backend
docker-compose build smarteye-lam-service
docker-compose restart smarteye-lam-service

# 3. 로그 확인
docker logs -f smarteye-lam-service
```

---

### 3.3 3단계: 검증 (Verification) - 10분

#### 3.3.1 LAM 서비스 직접 테스트

**목적**: 백엔드를 거치지 않고 LAM 서비스 단독으로 정상 동작 확인

```bash
# 1. 테스트 이미지를 Docker 컨테이너에 복사
docker cp "쎈 수학1-1_페이지_016.jpg" smarteye-lam-service:/tmp/test.jpg

# 2. curl로 LAM 서비스 직접 호출
curl -X POST "http://localhost:8001/analyze-layout" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@쎈 수학1-1_페이지_016.jpg" \
  -o lam_direct_test_result.json

# 3. 결과 확인
cat lam_direct_test_result.json | jq .
```

**예상 정상 결과**:
```json
{
  "layout_elements": [
    {
      "class_name": "question_number",
      "confidence": 0.95,
      "bbox": [x1, y1, x2, y2]
    },
    // ... (20-30개의 레이아웃 요소)
  ],
  "image_width": 3445,
  "image_height": 4736,
  "total_elements": 25
}
```

**실패 시 응답**:
```json
{
  "detail": "모델 로드에 실패했습니다."
}
```

#### 3.3.2 백엔드 통합 테스트

**목적**: 백엔드 → LAM 서비스 → 백엔드 전체 파이프라인 검증

```bash
# 1. Swagger UI 접속
# http://localhost:8080/swagger-ui/index.html

# 2. POST /api/analysis/cim-only 엔드포인트 테스트
#    - 파일: 쎈 수학1-1_페이지_016.jpg 업로드
#    - 응답 확인

# 3. 또는 curl로 테스트
curl -X POST "http://localhost:8080/api/analysis/cim-only" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@쎈 수학1-1_페이지_016.jpg" \
  -o backend_integration_test_result.json

# 4. 결과 검증
cat backend_integration_test_result.json | jq '.stats.total_questions'
```

**검증 기준**:
- ✅ **성공**: `total_questions > 0` (예: 7)
- ❌ **실패**: `total_questions == 0`

#### 3.3.3 Circuit Breaker 상태 확인

**목적**: Circuit Breaker가 정상 동작하는지 확인

```bash
# 백엔드 로그에서 Circuit Breaker 이벤트 확인
docker logs smarteye-backend 2>&1 | grep -i "circuit"

# 예상 로그 (정상 복구 시):
# "Circuit Breaker CLOSED - LAM 서비스 정상 응답"
# "LAM 서비스 호출 성공 - 레이아웃 요소 25건"
```

---

### 3.4 4단계: 모니터링 및 재발 방지 (Monitoring & Prevention) - 장기

#### 3.4.1 즉시 적용 가능한 개선사항

1. **LAM 서비스 Health Check 엔드포인트 강화**
   ```python
   # Backend/smarteye-lam-service/main.py
   @app.get("/health")
   async def health_check():
       """
       LAM 서비스 헬스 체크 - 모델 로드 상태 포함
       """
       try:
           # 모델 로드 상태 확인
           if model is None:
               return {
                   "status": "unhealthy",
                   "reason": "모델이 로드되지 않음",
                   "timestamp": datetime.now().isoformat()
               }

           # 간단한 추론 테스트 (더미 이미지)
           # ... (생략)

           return {
               "status": "healthy",
               "model_loaded": True,
               "model_classes": len(model.names),
               "timestamp": datetime.now().isoformat()
           }
       except Exception as e:
           return {
               "status": "unhealthy",
               "reason": str(e),
               "timestamp": datetime.now().isoformat()
           }
   ```

2. **백엔드 WebClient 타임아웃 설정 증가** (모델 로드가 느릴 경우 대비)
   ```java
   // Backend/smarteye-backend/src/main/java/com/smarteye/infrastructure/config/WebClientConfig.java

   @Bean
   public WebClient lamServiceWebClient() {
       return WebClient.builder()
           .baseUrl(lamServiceUrl)
           .clientConnector(new ReactorClientHttpConnector(
               HttpClient.create()
                   .responseTimeout(Duration.ofSeconds(120))  // 60초 → 120초 증가
                   .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)  // 10초 → 30초
           ))
           .build();
   }
   ```

3. **모델 로드 실패 시 재시도 로직 추가**
   ```python
   # Backend/smarteye-lam-service/main.py

   import time
   from tenacity import retry, stop_after_attempt, wait_exponential

   @retry(
       stop=stop_after_attempt(3),
       wait=wait_exponential(multiplier=1, min=4, max=10)
   )
   def load_model_with_retry():
       logger.info("모델 로드 시도...")
       model = YOLO("/app/models/best.pt")
       logger.info("✅ 모델 로드 성공")
       return model

   # 앱 시작 시
   try:
       model = load_model_with_retry()
   except Exception as e:
       logger.critical(f"❌ 3회 재시도 후에도 모델 로드 실패: {e}")
       raise
   ```

#### 3.4.2 장기 개선 계획

1. **LAM 서비스 메트릭 수집 및 대시보드 구축**
   - Prometheus + Grafana 통합
   - 모델 로드 시간, 추론 시간, 에러율 모니터링

2. **모델 파일 버전 관리**
   - Git LFS 또는 S3를 통한 모델 파일 버전 관리
   - 배포 시 모델 파일 체크섬 자동 검증

3. **자동 복구 메커니즘**
   - LAM 서비스 Health Check 실패 시 자동 재시작
   - Kubernetes Liveness Probe 설정 (프로덕션 환경)

---

## 4. 결론 및 권고사항

### 4.1 핵심 요약

| 항목 | 내용 |
|------|------|
| **근본 원인** | LAM 서비스의 DocLayout-YOLO 모델 (`best.pt`) 로딩 실패 |
| **오류 메시지** | `{"detail":"모델 로드에 실패했습니다."}` (500 Internal Server Error) |
| **영향** | 전체 분석 파이프라인 장애 → Fallback으로 빈 결과 반환 |
| **복구 시간** | **예상 45분** (진단 5분 + 해결 30분 + 검증 10분) |

### 4.2 즉시 실행 권고사항

**우선순위 P0 (즉시 실행)**:
1. ✅ LAM 서비스 로그 확인: `docker logs smarteye-lam-service --tail 100`
2. ✅ 모델 파일 무결성 검증: `docker exec smarteye-lam-service ls -lah /app/models/best.pt`
3. ✅ 케이스별 복구 조치 실행 (3.2 단계 참조)

**우선순위 P1 (복구 후 24시간 내)**:
1. LAM 서비스 Health Check 강화
2. WebClient 타임아웃 설정 증가
3. 모델 로드 재시도 로직 추가

**우선순위 P2 (1주일 내)**:
1. 모델 파일 버전 관리 시스템 구축
2. LAM 서비스 메트릭 수집 및 모니터링 대시보드 구축

### 4.3 재발 방지 체크리스트

- [ ] 모델 파일 교체 시 **반드시** 로컬 테스트 후 배포
- [ ] Health Check 엔드포인트로 모델 로드 상태 사전 확인
- [ ] 배포 전 `docker logs` 모니터링으로 모델 로드 성공 로그 확인
- [ ] 모델 파일 MD5 체크섬을 배포 스크립트에 포함
- [ ] Circuit Breaker Fallback 결과를 프로덕션 환경에서 사용자에게 노출하지 않도록 UI 개선

---

## 5. 첨부 자료

- **로그 파일**: `backend_swagger_test.log` (Line 27189-27258)
- **실패 결과 JSON**: `response_1760661170868.json`
- **시각 증거**: `layout_viz_614d5f0f-9903-4506-84cc-7b5b867e2574_1760661170343.png`
- **입력 이미지**: `쎈 수학1-1_페이지_016.jpg`

---

**작성자**: Claude Code (SRE Agent)
**검토자**: (보고서 검토 후 서명)
**승인자**: (복구 계획 승인 후 서명)
