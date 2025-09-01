# 🚀 SmartEye Backend Implementation Roadmap

## 📋 실행 계획 요약

### 🎯 목표
Python FastAPI 백엔드를 Java/Spring Boot로 완전 변환하여 PostgreSQL 기반의 확장 가능한 OCR 분석 시스템 구축

### ⏱️ 전체 일정: 5주 (35일)
- **Week 1**: 기본 인프라 구축
- **Week 2**: 데이터베이스 + 핵심 서비스 구현 
- **Week 3**: AI/OCR 서비스 + API 구현
- **Week 4**: 문서 처리 + 최적화
- **Week 5**: 마이크로서비스 + 통합 테스트

---

## 🗓️ 주차별 실행 계획

### 📅 Week 1: Foundation Setup (기반 구축)
**목표**: Spring Boot 기본 구조 완성 및 개발 환경 구축

#### 🔴 Critical Tasks (반드시 완료)
```
Day 1-2: 프로젝트 초기 설정
├── build.gradle 의존성 추가 (2h)
├── 패키지 구조 재구성 (1h)
└── application.yml 기본 설정 (1h)

Day 3-4: 인프라 구성
├── 예외 처리 시스템 구축 (3h)
├── 공통 유틸리티 클래스 (2h)
└── 기본 웹 설정 (1h)

Day 5: 테스트 및 검증
├── 헬스체크 API 구현 (1h)
├── 기본 통합 테스트 (2h)
└── PostgreSQL 연결 테스트 (1h)
```

#### 🎯 Week 1 완료 기준 ✅ **달성 완료**
- ✅ Spring Boot 애플리케이션 정상 구동
- ✅ PostgreSQL 데이터베이스 연결 성공  
- ✅ `/api/health` 엔드포인트 동작
- ✅ 기본 예외 처리 동작

#### 📈 Week 1 실제 성과 (2025-08-28)
- ✅ **31개 의존성 라이브러리** 통합 완료
- ✅ **7개 패키지** 구조 완성
- ✅ **3개 환경 프로파일** 설정 완료  
- ✅ **4개 커스텀 예외** 클래스 구현
- ✅ **3개 유틸리티** 클래스 완성
- ✅ **3개 헬스체크** API 구현
- ✅ **모든 테스트 통과** (5개 테스트 케이스)

---

### 📅 Week 2: Database & Core Services (DB + 핵심 서비스)
**목표**: 데이터 모델링 완성 및 기본 서비스 레이어 구축

#### 🔴 Critical Tasks
```
Day 1-3: 데이터베이스 모델링
├── 핵심 엔티티 생성 (6h)
│   ├── User.java
│   ├── AnalysisJob.java  
│   ├── DocumentPage.java
│   └── LayoutBlock.java
├── Repository 인터페이스 생성 (2h)
└── JPA 관계 설정 및 테스트 (2h)

Day 4-5: 기본 서비스 구현
├── FileService.java (4h)
├── ImageProcessingService.java (3h)
├── PDFService.java (3h)
└── 서비스 단위 테스트 (2h)
```

#### 🎯 Week 2 완료 기준
- ✅ 모든 엔티티 및 Repository 구현 완료
- ✅ 파일 업로드/저장 기능 동작
- ✅ PDF → 이미지 변환 기능 동작
- ✅ 데이터베이스 CRUD 테스트 성공

---

### 📅 Week 3: AI/OCR Services & API (AI 서비스 + API)
**목표**: OCR 및 LAM 서비스 구현, 메인 API 구축

#### 🔴 Critical Tasks
```
Day 1-2: OCR 서비스 구현
├── OCRService.java 구현 (4h)
├── Tesseract 설정 및 최적화 (2h)
└── OCR 단독 테스트 (1h)

Day 3-4: LAM 서비스 구현
├── LAMServiceClient.java (4h)
├── 마이크로서비스 통신 로직 (3h)
└── 서킷 브레이커 패턴 적용 (2h)

Day 5: 메인 분석 API
├── DocumentAnalysisController.java (4h)
├── 요청/응답 DTO 생성 (2h)
└── 단일 이미지 분석 API 테스트 (2h)
```

#### 🟡 High Priority Tasks  
```
Day 6-7: AI 및 시각화 서비스
├── AIDescriptionService.java (3h)
├── VisualizationService.java (3h)
└── 결과 시각화 기능 테스트 (2h)
```

#### 🎯 Week 3 완료 기준
- ✅ OCR 기능 완전 동작
- ✅ LAM 마이크로서비스와 통신 성공
- ✅ 단일 이미지 분석 API 완전 동작
- ✅ 분석 결과 시각화 생성

---

### 📅 Week 4: Document Processing & Optimization (문서 처리 + 최적화)
**목표**: 문서 생성 기능 완성 및 성능 최적화

#### 🔴 Critical Tasks
```
Day 1-2: 문서 처리 API
├── DocumentProcessingController.java (3h)
├── DocumentGenerationService.java (4h)
├── Apache POI 워드 문서 생성 (3h)
└── 텍스트 포맷팅 로직 (2h)

Day 3-4: 배치 처리 및 최적화
├── 다중 이미지 배치 처리 (4h)
├── 비동기 처리 (@Async) (2h)
├── 메모리 최적화 (2h)
└── 성능 테스트 (2h)
```

#### 🟡 High Priority Tasks
```
Day 5-7: 추가 기능 구현
├── 캐싱 시스템 (@Cacheable) (3h)
├── 작업 관리 API (3h)  
├── 사용자별 작업 이력 (2h)
└── PDF 멀티페이지 처리 (2h)
```

#### 🎯 Week 4 완료 기준
- ✅ 워드 문서 생성 기능 완료
- ✅ 배치 이미지 처리 기능 구현
- ✅ PDF 멀티페이지 처리 완료
- ✅ 기본 성능 최적화 완료

---

### 📅 Week 5: Microservice & Integration (마이크로서비스 + 통합)
**목표**: LAM 마이크로서비스 완성 및 전체 시스템 통합

#### 🔴 Critical Tasks
```
Day 1-2: LAM 마이크로서비스 구현
├── FastAPI 기반 LAM 서비스 (4h)
├── DocLayout-YOLO 모델 통합 (3h)
├── Docker 컨테이너화 (2h)
└── 서비스 간 통신 테스트 (1h)

Day 3-4: 통합 테스트
├── 전체 워크플로우 테스트 (4h)
├── 데이터베이스 통합 테스트 (2h)
├── 성능 테스트 (JMeter) (2h)
└── 메모리 프로파일링 (2h)
```

#### 🟡 High Priority Tasks
```
Day 5-7: 배포 및 모니터링
├── Docker Compose 운영 설정 (2h)
├── 모니터링 대시보드 (Actuator) (2h)
├── API 문서화 (Swagger) (2h)
└── 배포 스크립트 작성 (2h)
```

#### 🎯 Week 5 완료 기준
- ✅ LAM 마이크로서비스 완전 동작
- ✅ 전체 시스템 통합 테스트 완료
- ✅ 성능 최적화 완료
- ✅ 배포 준비 완료

---

## 🏗️ 구현 아키텍처 

### 📊 시스템 구성도
```
┌─────────────────────────────────────────────────────────────┐
│                    SmartEye Backend System                  │
├─────────────────────────────────────────────────────────────┤
│                      Frontend (Vue.js)                     │
├─────────────────────────────────────────────────────────────┤
│                  Java/Spring Boot Backend                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Web API   │  │   Service   │  │     Repository     │ │
│  │ Controllers │◄─┤    Layer    │◄─┤       Layer        │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                    External Services                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │    LAM      │  │   OpenAI    │  │    PostgreSQL      │ │
│  │ Microservice│  │   Vision    │  │     Database       │ │
│  │  (Python)   │  │     API     │  │                    │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 🔄 데이터 플로우
```
1. 이미지/PDF 업로드 
   ↓
2. 파일 저장 및 검증
   ↓
3. PDF → 이미지 변환 (필요시)
   ↓
4. LAM 서비스 호출 (레이아웃 분석)
   ↓
5. OCR 처리 (텍스트 추출)
   ↓
6. OpenAI API 호출 (그림/표 설명)
   ↓
7. 결과 통합 및 시각화
   ↓
8. CIM JSON 생성
   ↓
9. 포맷팅된 텍스트 생성
   ↓
10. 워드 문서 생성 (선택사항)
    ↓
11. 결과 반환 및 저장
```

---

## 🚨 리스크 관리 및 대응 계획

### 🔴 High Risk
| 리스크 | 영향도 | 대응 계획 |
|--------|--------|-----------|
| LAM 모델 Java 통합 실패 | High | 마이크로서비스로 분리, Python FastAPI 래핑 |
| OCR 한글 인식률 저하 | Medium | Tesseract 언어팩 최적화, 전처리 개선 |
| PostgreSQL 성능 이슈 | Medium | 인덱스 최적화, 커넥션 풀 튜닝 |
| 메모리 사용량 과다 | Medium | 이미지 처리 최적화, 가비지 컬렉션 튜닝 |

### 🟡 Medium Risk  
| 리스크 | 영향도 | 대응 계획 |
|--------|--------|-----------|
| OpenAI API 호출 한도 | Low | 캐싱 활용, 배치 처리 최적화 |
| 파일 저장 공간 부족 | Low | 정기 정리 스케줄러, 클라우드 스토리지 연동 |
| Docker 컨테이너 설정 | Low | 단계별 검증, 대체 배포 방안 |

---

## 📈 성능 목표 및 지표

### 🎯 성능 KPI
- **응답 시간**: 단일 이미지 분석 < 30초
- **처리량**: 시간당 100개 이미지 처리 가능
- **동시 접속**: 10명 동시 처리
- **메모리 사용**: 힙 메모리 < 2GB
- **CPU 사용률**: 평균 < 70%

### 📊 모니터링 지표
- API 응답 시간 (P95, P99)
- 데이터베이스 커넥션 풀 상태  
- JVM 메모리 사용량
- 디스크 I/O 사용률
- 마이크로서비스 통신 지연시간

---

## 🔧 개발 환경 및 도구

### 💻 개발 환경
- **JDK**: OpenJDK 17
- **IDE**: IntelliJ IDEA / VS Code
- **빌드 도구**: Gradle 8.x
- **데이터베이스**: PostgreSQL 15+
- **컨테이너**: Docker 24+ / Docker Compose

### 🛠️ 개발 도구
- **테스팅**: JUnit 5, Testcontainers
- **API 문서**: Swagger/OpenAPI 3
- **성능 테스트**: JMeter, Gatling
- **모니터링**: Spring Boot Actuator
- **로깅**: Logback, ELK Stack (선택사항)

---

## 📝 체크리스트 템플릿

### 일일 체크리스트 
```
□ 해당 일자 계획된 태스크 완료
□ 단위 테스트 작성 및 통과
□ 코드 리뷰 및 품질 검사
□ 진행 상황 문서 업데이트
□ 다음 일자 작업 계획 수립
```

### 주차별 체크리스트
```  
□ 주차 목표 달성 확인
□ 통합 테스트 실행
□ 성능 벤치마크 측정
□ 배포 환경 테스트
□ 문서화 업데이트
```

---

## 🎉 최종 산출물

### 📦 Main Deliverables
1. **Java/Spring Boot 백엔드 애플리케이션**
   - 완전한 OCR 분석 파이프라인
   - PostgreSQL 기반 데이터 영속성
   - RESTful API 인터페이스

2. **LAM 마이크로서비스**
   - Python/FastAPI 기반 레이아웃 분석 서비스
   - Docker 컨테이너 배포
   - Java 백엔드와의 HTTP 통신

3. **데이터베이스 스키마**
   - 사용자, 작업, 문서, 레이아웃 블록 관리
   - 마이그레이션 스크립트
   - 인덱스 최적화

4. **배포 환경**
   - Docker Compose 설정
   - 환경별 설정 관리
   - 모니터링 대시보드

### 📋 Documentation  
- API 명세서 (Swagger)
- 개발자 가이드
- 배포 매뉴얼
- 운영 가이드

이 로드맵을 따라 체계적이고 안정적인 시스템 변환을 진행할 수 있습니다!