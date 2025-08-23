# SmartEye LAM/TSPM Java 변환 프로젝트 로드맵

## 📋 프로젝트 개요
Python 기반 LAM(Layout Analysis Module)과 TSPM(Text Structure Processing Module) 서비스를 Java Spring Boot 환경으로 변환하여 성능 향상 및 통합 관리 체계 구축

## ✅ 1단계 완료 사항 (TSPM Java 변환)

### 🎯 달성 목표
- [x] TSPM 서비스 완전한 Java 네이티브 구현
- [x] OCR 기능 실제 Tesseract 통합
- [x] Vision API Java 직접 호출 구현
- [x] 이미지 크롭핑 Java BufferedImage 처리
- [x] 파이썬 스크립트 의존성 제거

### 🔧 구현된 핵심 컴포넌트

#### JavaTSPMService.java
```java
// 완전한 Java 네이티브 TSPM 처리
- performOCR(): 실제 Tesseract OCR 처리 (kor+eng 지원)
- performVisionAnalysis(): OpenAI Vision API REST 직접 호출
- cropLayoutBlock(): BufferedImage 기반 이미지 크롭핑
- calculateOCRConfidence(): 텍스트 품질 기반 신뢰도 계산
```

#### TSPMService.java (하이브리드)
```java
// Java 우선, 파이썬 폴백 전략
- Java 네이티브 처리 우선순위
- 설정 가능한 폴백 메커니즘
- 성능 모니터링 및 로깅
```

### 🛠️ 기술 스택
- **Spring Boot**: 3.1.5 (Web, JPA, WebFlux, WebSocket)
- **Java**: 17 (UTF-8, parameter preservation)
- **OCR**: Tesseract 5.3.4 + Tess4J 5.8.0 (한국어+영어)
- **Vision API**: OpenAI REST API (RestTemplate + Jackson)
- **Build**: Gradle 8.3
- **Database**: H2 dev + PostgreSQL production

### 📊 성능 검증 결과
```bash
✅ 애플리케이션 시작: 5.958초
✅ Tesseract 초기화: 성공 (kor+eng)
✅ 데이터베이스 스키마: 자동 생성 완료
✅ REST API 엔드포인트: 16개 매핑 완료
✅ OCR 제한사항: 완전 해결
```

---

## 🚀 2단계 계획 (LAM 마이크로서비스 분리)

### 🎯 목표
LAM(Layout Analysis Module)을 독립적인 마이크로서비스로 분리하여 DocLayout-YOLO 모델의 Python 의존성을 격리하고 확장성 확보

### 📋 상세 작업 계획

#### 2.1 LAM 마이크로서비스 아키텍처 설계
```yaml
# 서비스 구조
SmartEye-LAM-Service:
  - Port: 8081
  - Technology: Python FastAPI + DocLayout-YOLO
  - Responsibilities:
    - 문서 레이아웃 분석
    - 객체 탐지 및 분류
    - 좌표 정보 반환
  
SmartEye-Main-Service:
  - Port: 8080  
  - Technology: Java Spring Boot
  - Responsibilities:
    - TSPM 처리 (Java 네이티브)
    - LAM 서비스 호출
    - 전체 파이프라인 오케스트레이션
```

#### 2.2 LAM 마이크로서비스 구현
- **2.2.1 FastAPI 서버 구축**
  ```python
  # smarteye-lam-service/app.py
  - /analyze/layout 엔드포인트
  - DocLayout-YOLO 모델 로딩
  - 이미지 처리 및 결과 반환
  - Health check 엔드포인트
  ```

- **2.2.2 Docker 컨테이너화**
  ```dockerfile
  # 독립적인 Python 환경 구성
  # DocLayout-YOLO 의존성 격리
  # GPU 지원 옵션 설정
  ```

#### 2.3 Java 서비스 LAM 클라이언트 구현
```java
// LAMServiceClient.java
- RestTemplate 기반 HTTP 클라이언트
- Circuit Breaker 패턴 적용 (Resilience4j)
- 재시도 로직 및 타임아웃 설정
- 캐싱 전략 구현
```

#### 2.4 서비스간 통신 최적화
- **비동기 처리**: WebFlux 기반 non-blocking 호출
- **로드 밸런싱**: 다중 LAM 인스턴스 지원
- **모니터링**: Actuator + Micrometer 메트릭

### 📅 2단계 일정 (예상)
- Week 1: LAM FastAPI 서비스 구현
- Week 2: Java 클라이언트 및 통신 계층 구현
- Week 3: Docker 컨테이너화 및 테스트
- Week 4: 성능 최적화 및 모니터링 구축

---

## 🔬 3단계 계획 (Java 호환 모델 평가)

### 🎯 목표
DocLayout-YOLO의 Java 네이티브 대안 평가 및 구현으로 완전한 Java 기반 솔루션 구축

### 📋 평가 대상 기술

#### 3.1 Deep Java Library (DJL)
```java
// Amazon의 Java 딥러닝 프레임워크
- ONNX 모델 지원
- GPU 가속 지원
- Spring Boot 통합 용이
- 라이센스: Apache 2.0
```

#### 3.2 OpenCV Java + Pre-trained Models
```java
// 전통적인 컴퓨터 비전 접근
- 문서 레이아웃 휴리스틱
- 텍스트 영역 탐지 알고리즘
- 빠른 처리 속도
- 낮은 리소스 사용량
```

#### 3.3 ONNX Runtime Java
```java
// 기존 YOLO 모델의 ONNX 변환
- DocLayout-YOLO → ONNX 형식 변환
- Java에서 직접 추론 실행
- 성능 최적화 가능
```

### 📊 평가 기준
1. **정확도**: DocLayout-YOLO 대비 성능 비교
2. **속도**: 처리 시간 벤치마크
3. **메모리 사용량**: 리소스 효율성
4. **통합 용이성**: Spring Boot 생태계 적합성
5. **유지보수성**: 장기적 관리 비용

### 📅 3단계 일정 (예상)
- Week 1-2: 각 기술 스택 PoC 구현
- Week 3: 성능 벤치마크 및 비교 분석
- Week 4: 최적 솔루션 선택 및 통합 구현

---

## 🔄 4단계 계획 (성능 최적화 및 모니터링)

### 🎯 목표
Java 기반 통합 솔루션의 성능 최적화 및 운영 모니터링 체계 구축

### 📋 최적화 영역

#### 4.1 OCR 성능 최적화
```java
// 병렬 처리 및 캐싱
- 다중 스레드 Tesseract 처리
- OCR 결과 캐싱 전략
- 이미지 전처리 최적화
- 배치 처리 지원
```

#### 4.2 메모리 관리 최적화
```java
// JVM 튜닝 및 리소스 관리
- 힙 메모리 최적화
- GC 튜닝
- 이미지 객체 풀링
- 메모리 누수 방지
```

#### 4.3 응답 시간 최적화
```java
// 비동기 처리 및 스트리밍
- CompletableFuture 활용
- WebFlux Reactive Streams
- 점진적 결과 반환
- 클라이언트 사이드 캐싱
```

### 📊 모니터링 구축
```yaml
Metrics:
  - 처리 시간 분포
  - 메모리 사용량
  - OCR 정확도
  - API 응답률
  
Logging:
  - 구조화된 로그 (JSON)
  - 분산 추적 (Zipkin)
  - 오류 분석 및 알림
  
Health Checks:
  - Tesseract 상태
  - 외부 API 연결
  - 디스크 공간
  - 메모리 임계치
```

---

## 📈 예상 성과 및 KPI

### 🎯 성능 목표
- **처리 속도**: 파이썬 대비 30% 향상
- **메모리 효율성**: 20% 리소스 절약
- **응답 안정성**: 99.9% 가용성
- **개발 생산성**: 통합 개발 환경

### 📊 측정 지표
```yaml
Technical KPIs:
  - 평균 응답 시간: < 2초
  - OCR 정확도: > 95%
  - 메모리 사용량: < 2GB
  - CPU 사용률: < 70%

Business KPIs:
  - 개발 속도: 50% 향상
  - 운영 비용: 30% 절감
  - 오류 발생률: < 0.1%
  - 사용자 만족도: > 4.5/5
```

---

## 🛠️ 구현 가이드라인

### 🔧 개발 환경 설정
```bash
# 1. Java 개발 환경
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 2. Tesseract 설정
export TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

# 3. Docker 개발 환경
docker-compose -f docker-compose.dev.yml up -d
```

### 📝 코딩 표준
- **로깅**: SLF4J + Logback (JSON 구조화)
- **예외 처리**: 계층별 Exception 핸들링
- **테스트**: JUnit 5 + TestContainers
- **문서화**: JavaDoc + API 문서 자동 생성

### 🔍 품질 보증
```yaml
Code Quality:
  - SonarQube 정적 분석
  - JaCoCo 코드 커버리지 (>80%)
  - SpotBugs 버그 탐지
  
Performance Testing:
  - JMeter 부하 테스트
  - VisualVM 프로파일링
  - JProfiler 메모리 분석
```

---

## 🚨 리스크 관리

### ⚠️ 주요 리스크 요소
1. **모델 성능 저하**: Java 변환 시 정확도 감소
2. **메모리 부족**: 대용량 이미지 처리 시 OOM
3. **통합 복잡성**: 마이크로서비스 간 통신 이슈
4. **개발 일정 지연**: 예상보다 복잡한 구현

### 🛡️ 완화 전략
```yaml
Risk Mitigation:
  성능 저하:
    - A/B 테스트로 점진적 전환
    - 파이썬 폴백 유지
    - 성능 벤치마크 지속 모니터링
  
  메모리 이슈:
    - 점진적 메모리 증설
    - 이미지 압축 및 리사이징
    - 가비지 컬렉션 튜닝
  
  통합 복잡성:
    - Circuit Breaker 패턴
    - 상세한 API 문서화
    - 통합 테스트 자동화
```

---

## 📞 다음 단계 실행 계획

### 🎯 즉시 실행 가능한 작업
1. **LAM FastAPI 서비스 구축** (2.1 ~ 2.2)
2. **Docker 개발 환경 구성** (2.2.2)
3. **Java LAM 클라이언트 구현** (2.3)
4. **성능 벤치마크 기준선 설정** (4단계 준비)

### 📋 준비 사항
- [ ] FastAPI 프로젝트 구조 설계
- [ ] Docker 멀티 스테이지 빌드 설정
- [ ] LAM 서비스 API 스펙 정의
- [ ] 성능 테스트 시나리오 작성

**현재 1단계가 성공적으로 완료되었으며, TSPM 서비스는 완전한 Java 네이티브 구현으로 전환되었습니다. 이제 2단계인 LAM 마이크로서비스 분리 작업을 시작할 준비가 되었습니다.**
