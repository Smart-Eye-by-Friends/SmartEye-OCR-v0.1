# 🏗️ SmartEye OCR 아키텍처 다이어그램

## Mermaid 다이어그램 (PPT에 삽입 가능)

---

## 1. 전체 시스템 아키텍처

```mermaid
graph TB
    subgraph "Client Layer"
        A[📱 React Frontend<br/>Port 3000<br/>- Image Upload<br/>- Result Display<br/>- Text Editor]
    end

    subgraph "Presentation Layer"
        B[🌐 Nginx Reverse Proxy<br/>Port 80/443<br/>- SSL Termination<br/>- Load Balancing<br/>- Static Files]
    end

    subgraph "Application Layer"
        C[☕ Spring Boot Backend<br/>Port 8080<br/>- REST API<br/>- Business Logic<br/>- Security & Validation<br/>- Async Processing]
    end

    subgraph "AI Services"
        D[🧠 LAM Service<br/>Port 8001<br/>- YOLO Models<br/>- Layout Analysis<br/>- Structured Generation]
    end

    subgraph "Data Layer"
        E[🗄️ PostgreSQL<br/>Port 5433<br/>- User Data<br/>- Job Queue<br/>- Analysis Results]
    end

    subgraph "External APIs"
        F[🤖 OpenAI API<br/>- GPT-4 Turbo<br/>- Image Analysis<br/>- Text Interpretation]
    end

    A -->|HTTP/REST API| B
    B -->|Proxy Pass| C
    C -->|WebClient| D
    C -->|JPA/JDBC| E
    C -->|HTTP Client| F

    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#e8f5e8
    style D fill:#fff3e0
    style E fill:#fce4ec
    style F fill:#f1f8e9
```

---

## 2. 마이크로서비스 상세 구조

```mermaid
graph LR
    subgraph "Frontend Service"
        A1[🎨 UI Components<br/>- ImageLoader<br/>- ResultTabs<br/>- AnalysisProgress]
        A2[🔧 Custom Hooks<br/>- useAnalysis<br/>- useTextEditor]
        A3[🌐 API Service<br/>- Axios Client<br/>- Request/Response]
    end

    subgraph "Backend Service"
        B1[🌐 Controllers<br/>- DocumentAnalysis<br/>- Health<br/>- User]
        B2[🏗️ Services<br/>- LAMClient<br/>- OCR<br/>- AI<br/>- File]
        B3[🗄️ Data Layer<br/>- Entities<br/>- Repositories<br/>- DTOs]
    end

    subgraph "LAM Service"
        C1[🧠 AI Models<br/>- YOLO<br/>- DocLayout-YOLO<br/>- SmartEyeSsen]
        C2[📊 Analysis<br/>- Layout Analyzer<br/>- JSON Generator]
        C3[🔄 FastAPI<br/>- Endpoints<br/>- Health Checks]
    end

    A1 --> A2 --> A3
    A3 -->|REST API| B1
    B1 --> B2 --> B3
    B2 -->|HTTP Client| C3
    C3 --> C2 --> C1

    style A1 fill:#e3f2fd
    style A2 fill:#e3f2fd
    style A3 fill:#e3f2fd
    style B1 fill:#e8f5e8
    style B2 fill:#e8f5e8
    style B3 fill:#e8f5e8
    style C1 fill:#fff8e1
    style C2 fill:#fff8e1
    style C3 fill:#fff8e1
```

---

## 3. AI 분석 파이프라인

```mermaid
flowchart TD
    A[📸 Image Upload] --> B{🔍 Image Validation}
    B -->|Valid| C[📁 File Storage & Job Creation]
    B -->|Invalid| X[❌ Error Response]

    C --> D[🧠 LAM Service Call]
    D --> E[👁️ YOLO Object Detection]
    E --> F[📊 Layout Analysis]
    F --> G[📋 Structured Generation]

    G --> H[📝 OCR Processing<br/>Tesseract]
    H --> I[🤖 AI Analysis<br/>OpenAI GPT-4]

    I --> J[💾 Database Storage]
    J --> K[📋 JSON Response Generation]
    K --> L[✅ Final Result]

    subgraph "Performance Metrics"
        M[⚡ 2-5 seconds<br/>Average Processing Time]
        N[🔄 3 concurrent jobs<br/>Parallel Processing]
        O[📊 95%+ accuracy<br/>AI Model Performance]
    end

    style A fill:#e3f2fd
    style E fill:#fff3e0
    style F fill:#fff3e0
    style G fill:#fff3e0
    style H fill:#e8f5e8
    style I fill:#f1f8e9
    style L fill:#c8e6c9
```

---

## 4. 데이터 흐름 다이어그램

```mermaid
sequenceDiagram
    participant U as 📱 User
    participant F as ⚛️ Frontend
    participant B as ☕ Backend
    participant L as 🧠 LAM Service
    participant D as 🗄️ Database
    participant A as 🤖 OpenAI API

    U->>F: 1. Upload Image
    F->>B: 2. POST /api/document/analyze-structured
    B->>B: 3. Validate & Save File
    B->>D: 4. Create Job Record

    B->>L: 5. POST /analyze-structured
    L->>L: 6. YOLO Object Detection
    L->>L: 7. Layout Analysis
    L->>L: 8. Structured Generation
    L->>B: 9. Return Analysis Result

    B->>B: 10. OCR Processing (Tesseract)
    B->>A: 11. AI Analysis Request
    A->>B: 12. AI Analysis Response

    B->>D: 13. Save Complete Results
    B->>F: 14. Return Final JSON
    F->>U: 15. Display Results

    Note over U,A: Total Processing Time: 2-5 seconds
```

---

## 5. Docker 배포 아키텍처

```mermaid
graph TB
    subgraph "Docker Host"
        subgraph "smarteye-network (Bridge)"
            subgraph "Web Tier"
                N[🌐 nginx<br/>Port 80/443<br/>2 CPU, 512MB RAM]
            end

            subgraph "Application Tier"
                B[☕ smarteye-backend<br/>Port 8080<br/>2 CPU, 2GB RAM]
                L[🐍 smarteye-lam-service<br/>Port 8001<br/>4 CPU, 4GB RAM]
            end

            subgraph "Data Tier"
                P[🐘 smarteye-postgres<br/>Port 5433<br/>2 CPU, 1GB RAM]
            end
        end

        subgraph "Volumes"
            V1[📁 postgres_data]
            V2[📁 lam_models]
            V3[📁 backend_uploads]
            V4[📁 backend_static]
        end
    end

    subgraph "External"
        U[👤 Users]
        API[🤖 OpenAI API]
    end

    U -->|HTTPS| N
    N --> B
    B --> L
    B --> P
    B --> API

    P -.-> V1
    L -.-> V2
    B -.-> V3
    B -.-> V4

    style N fill:#e1f5fe
    style B fill:#e8f5e8
    style L fill:#fff3e0
    style P fill:#fce4ec
    style V1 fill:#f5f5f5
    style V2 fill:#f5f5f5
    style V3 fill:#f5f5f5
    style V4 fill:#f5f5f5
```

---

## 6. 기술 스택 구성도

```mermaid
mindmap
  root((🎯 SmartEye OCR<br/>Tech Stack))
    (📱 Frontend)
      React 18
        Component Based
        Hooks & Context
        Responsive UI
      Axios
        REST Client
        Interceptors
        Error Handling
      React Router
        SPA Navigation
        Route Guards
    (☕ Backend)
      Spring Boot 3.5
        RESTful API
        Auto Configuration
        Actuator Monitoring
      Spring Data JPA
        ORM Mapping
        Repository Pattern
        Transaction Management
      WebFlux
        Async HTTP Client
        Non-blocking I/O
      Resilience4j
        Circuit Breaker
        Retry Pattern
        Bulkhead
    (🧠 AI Services)
      Python 3.9+
        Data Processing
        AI/ML Libraries
      FastAPI
        High Performance
        Async Support
        Auto Documentation
      PyTorch
        Deep Learning
        Model Loading
        GPU Support
      YOLO
        Object Detection
        Layout Analysis
        Real-time Processing
    (🏗️ Infrastructure)
      Docker
        Containerization
        Multi-stage Build
        Resource Limits
      PostgreSQL 15
        ACID Compliance
        JSON Support
        Full-text Search
      Nginx
        Reverse Proxy
        SSL Termination
        Load Balancing
```

---

## 7. 보안 아키텍처

```mermaid
graph TB
    subgraph "External Threats"
        T1[🔥 DDoS Attacks]
        T2[🎭 XSS/CSRF]
        T3[💉 SQL Injection]
        T4[👤 Unauthorized Access]
    end

    subgraph "Security Layers"
        subgraph "Network Security"
            S1[🌐 Nginx Rate Limiting<br/>- Request throttling<br/>- IP blacklisting]
            S2[🔒 SSL/TLS Encryption<br/>- HTTPS enforced<br/>- Certificate management]
        end

        subgraph "Application Security"
            S3[🛡️ CORS Policy<br/>- Origin validation<br/>- Preflight requests]
            S4[✅ Input Validation<br/>- Schema validation<br/>- File type checking]
            S5[⚠️ Exception Handling<br/>- Error sanitization<br/>- Log masking]
        end

        subgraph "Data Security"
            S6[🔐 Environment Variables<br/>- Secret management<br/>- API key protection]
            S7[💾 Database Security<br/>- Connection encryption<br/>- Access control]
        end
    end

    subgraph "Internal Services"
        I1[☕ Backend Service]
        I2[🧠 LAM Service]
        I3[🗄️ Database]
    end

    T1 --> S1
    T2 --> S3
    T3 --> S4
    T4 --> S6

    S1 --> S2 --> S3 --> S4 --> S5
    S5 --> S6 --> S7
    S7 --> I1 --> I2 --> I3

    style T1 fill:#ffebee
    style T2 fill:#ffebee
    style T3 fill:#ffebee
    style T4 fill:#ffebee
    style S1 fill:#e8f5e8
    style S2 fill:#e8f5e8
    style S3 fill:#e8f5e8
    style S4 fill:#e8f5e8
    style S5 fill:#e8f5e8
    style S6 fill:#e3f2fd
    style S7 fill:#e3f2fd
    style I1 fill:#f1f8e9
    style I2 fill:#f1f8e9
    style I3 fill:#f1f8e9
```

---

## 8. 성능 모니터링 대시보드

```mermaid
graph LR
    subgraph "Metrics Collection"
        M1[📊 Application Metrics<br/>- Response Time<br/>- Throughput<br/>- Error Rate]
        M2[💻 System Metrics<br/>- CPU Usage<br/>- Memory Usage<br/>- Disk I/O]
        M3[🧠 AI Metrics<br/>- Model Accuracy<br/>- Processing Time<br/>- Queue Length]
    end

    subgraph "Health Monitoring"
        H1[🏥 Health Endpoints<br/>- /api/health<br/>- /health<br/>- pg_isready]
        H2[🔄 Auto Restart<br/>- Container restart<br/>- Dependency checks<br/>- Graceful shutdown]
    end

    subgraph "Alerting System"
        A1[🚨 Alert Rules<br/>- Threshold based<br/>- Anomaly detection<br/>- Service down]
        A2[📧 Notifications<br/>- Email alerts<br/>- Slack integration<br/>- Dashboard updates]
    end

    M1 --> H1
    M2 --> H1
    M3 --> H1
    H1 --> H2
    H2 --> A1
    A1 --> A2

    style M1 fill:#e3f2fd
    style M2 fill:#e3f2fd
    style M3 fill:#e3f2fd
    style H1 fill:#e8f5e8
    style H2 fill:#e8f5e8
    style A1 fill:#fff3e0
    style A2 fill:#fff3e0
```

---

## 사용법

### PowerPoint에서 사용하기

1. **Mermaid Live Editor** (https://mermaid.live) 방문
2. 위의 다이어그램 코드 복사하여 붙여넣기
3. **PNG/SVG로 내보내기**
4. PowerPoint에 이미지로 삽입

### Markdown 문서에서 사용하기

- GitHub, GitLab에서 자동 렌더링
- VSCode Mermaid 확장프로그램 사용
- Notion, Obsidian 등에서 지원

### 온라인 도구

- **Mermaid Live**: https://mermaid.live
- **Diagrams.net**: https://app.diagrams.net
- **Lucidchart**: Mermaid 임포트 지원
