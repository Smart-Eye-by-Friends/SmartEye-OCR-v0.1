# 🛡️ SmartEye CORS 보안 강화 권장사항

## 📋 환경별 보안 정책 매트릭스

### 🔴 **프로덕션 환경 (운영)**
```yaml
security_level: MAXIMUM
allowed_origins:
  - https://smarteye.company.com
  - https://app.smarteye.kr
allowed_methods: [GET, POST, PUT, DELETE]  # OPTIONS 제외
allowed_headers: [Content-Type, Authorization, X-Requested-With]
allow_credentials: false  # CSRF 공격 방지
max_age: 1800  # 30분
```

### 🟡 **스테이징 환경 (테스트)**
```yaml
security_level: HIGH
allowed_origins:
  - https://staging.smarteye.com
  - https://test.smarteye.kr
  - http://localhost:3000  # 개발자 테스트용
allowed_methods: [GET, POST, PUT, DELETE, OPTIONS]
allowed_headers: [Content-Type, Authorization, X-Requested-With, X-Debug-Token]
allow_credentials: false
max_age: 900  # 15분
```

### 🟢 **개발 환경 (로컬)**
```yaml
security_level: MEDIUM
allowed_origins:
  - http://localhost:3000
  - http://localhost:3001
  - http://127.0.0.1:3000
allowed_methods: [GET, POST, PUT, DELETE, OPTIONS, PATCH]
allowed_headers: [*]  # 개발 편의성
allow_credentials: true  # 개발 디버깅용
max_age: 600  # 10분
```

## 🏗️ 구현 권장 아키텍처

### **1. CorsSecurityConfig.java 생성**

```java
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsSecurityConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;
    private final Environment environment;

    public CorsSecurityConfig(CorsProperties corsProperties, Environment environment) {
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] activeProfiles = environment.getActiveProfiles();

        if (Arrays.asList(activeProfiles).contains("prod")) {
            configureProductionCors(registry);
        } else if (Arrays.asList(activeProfiles).contains("staging")) {
            configureStagingCors(registry);
        } else {
            configureDevelopmentCors(registry);
        }
    }

    private void configureProductionCors(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getProduction().getAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                .allowCredentials(false)
                .maxAge(1800);

        // 정적 리소스는 더 제한적
        registry.addMapping("/static/**")
                .allowedOrigins(corsProperties.getProduction().getAllowedOrigins())
                .allowedMethods("GET")
                .maxAge(3600);
    }

    private void configureStagingCors(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getStaging().getAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With", "X-Debug-Token")
                .allowCredentials(false)
                .maxAge(900);
    }

    private void configureDevelopmentCors(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(600);
    }
}
```

### **2. CorsProperties.java (Configuration Properties)**

```java
@ConfigurationProperties(prefix = "smarteye.cors")
@Data
public class CorsProperties {

    private Production production = new Production();
    private Staging staging = new Staging();
    private Development development = new Development();

    @Data
    public static class Production {
        private String[] allowedOrigins = {"https://smarteye.company.com"};
        private String[] allowedMethods = {"GET", "POST", "PUT", "DELETE"};
        private String[] allowedHeaders = {"Content-Type", "Authorization", "X-Requested-With"};
        private boolean allowCredentials = false;
        private long maxAge = 1800;
    }

    @Data
    public static class Staging {
        private String[] allowedOrigins = {"https://staging.smarteye.com", "http://localhost:3000"};
        private String[] allowedMethods = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
        private String[] allowedHeaders = {"Content-Type", "Authorization", "X-Requested-With", "X-Debug-Token"};
        private boolean allowCredentials = false;
        private long maxAge = 900;
    }

    @Data
    public static class Development {
        private String[] allowedOrigins = {"http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000"};
        private String[] allowedMethods = {"*"};
        private String[] allowedHeaders = {"*"};
        private boolean allowCredentials = true;
        private long maxAge = 600;
    }
}
```

### **3. 환경변수 기반 설정 (application-prod.yml)**

```yaml
smarteye:
  cors:
    production:
      allowed-origins:
        - ${CORS_ALLOWED_ORIGINS:https://smarteye.company.com}
        - ${CORS_ALLOWED_ORIGINS_SECONDARY:https://app.smarteye.kr}
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
      allowed-headers:
        - Content-Type
        - Authorization
        - X-Requested-With
      allow-credentials: false
      max-age: 1800
```

## 🔒 추가 보안 헤더 권장사항

### **SecurityHeadersFilter.java**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.tiny.cloud; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "connect-src 'self' https://api.openai.com; " +
            "frame-ancestors 'none'"
        );

        // X-Frame-Options
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // X-Content-Type-Options
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Referrer-Policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // X-XSS-Protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Strict-Transport-Security (HTTPS 환경에서만)
        if (request.getScheme().equals("https")) {
            httpResponse.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(request, response);
    }
}
```

## 🏭 LAM Service CORS 보안 강화

### **main.py 수정**

```python
import os
from fastapi.middleware.cors import CORSMiddleware

# 환경변수에서 허용된 Origin 읽기
ALLOWED_ORIGINS = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:8080").split(",")
ENVIRONMENT = os.getenv("ENVIRONMENT", "development")

# 환경별 CORS 설정
if ENVIRONMENT == "production":
    cors_settings = {
        "allow_origins": ALLOWED_ORIGINS,
        "allow_credentials": False,
        "allow_methods": ["GET", "POST"],
        "allow_headers": ["Content-Type", "Authorization"]
    }
elif ENVIRONMENT == "staging":
    cors_settings = {
        "allow_origins": ALLOWED_ORIGINS + ["http://localhost:3000"],
        "allow_credentials": False,
        "allow_methods": ["GET", "POST", "OPTIONS"],
        "allow_headers": ["Content-Type", "Authorization", "X-Debug-Token"]
    }
else:  # development
    cors_settings = {
        "allow_origins": ["http://localhost:3000", "http://localhost:8080"],
        "allow_credentials": True,
        "allow_methods": ["*"],
        "allow_headers": ["*"]
    }

app.add_middleware(CORSMiddleware, **cors_settings)
```

## 🔍 모니터링 및 로깅 전략

### **1. CORS 요청 로깅**

```java
@Component
public class CorsRequestLogger implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorsRequestLogger.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String origin = httpRequest.getHeader("Origin");
        String method = httpRequest.getMethod();

        if (origin != null) {
            // 프로덕션에서는 허용되지 않은 Origin 경고 로깅
            if (isProduction() && !isAllowedOrigin(origin)) {
                logger.warn("Blocked CORS request from unauthorized origin: {} for endpoint: {}",
                           origin, httpRequest.getRequestURI());
            } else {
                logger.debug("CORS request from origin: {} for endpoint: {}",
                            origin, httpRequest.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        // 허용된 Origin 목록과 비교
        return corsProperties.getProduction().getAllowedOrigins()
                .stream().anyMatch(allowed -> allowed.equals(origin));
    }
}
```

### **2. 보안 메트릭스 수집**

```java
@Component
public class CorsSecurityMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter unauthorizedCorsRequests;
    private final Counter authorizedCorsRequests;

    public CorsSecurityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.unauthorizedCorsRequests = Counter.builder("cors.requests.unauthorized")
                .description("Unauthorized CORS requests count")
                .register(meterRegistry);
        this.authorizedCorsRequests = Counter.builder("cors.requests.authorized")
                .description("Authorized CORS requests count")
                .register(meterRegistry);
    }

    public void recordUnauthorizedRequest(String origin, String endpoint) {
        unauthorizedCorsRequests.increment(
            Tags.of("origin", origin, "endpoint", endpoint)
        );
    }

    public void recordAuthorizedRequest(String origin, String endpoint) {
        authorizedCorsRequests.increment(
            Tags.of("origin", origin, "endpoint", endpoint)
        );
    }
}
```

## 🧪 보안 테스트 체크리스트

### **자동화된 CORS 보안 테스트**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CorsSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("프로덕션 환경에서 허용되지 않은 Origin 차단 테스트")
    void shouldBlockUnauthorizedOriginInProduction() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "https://malicious-site.com");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/document/analyze",
            HttpMethod.OPTIONS,
            new HttpEntity<>(headers),
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    @DisplayName("허용된 Origin에서의 요청 허용 테스트")
    void shouldAllowAuthorizedOrigin() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "https://smarteye.company.com");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/health",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin"))
                .contains("https://smarteye.company.com");
    }

    @Test
    @DisplayName("Credentials 허용 정책 테스트")
    void shouldControlCredentialsPolicy() {
        // 프로덕션에서는 Credentials 차단, 개발에서는 허용
        // 테스트 로직 구현
    }
}
```

### **수동 보안 검증 체크리스트**

#### 🔴 **프로덕션 배포 전 필수 검증**
- [ ] 와일드카드(`*`) Origin 완전 제거 확인
- [ ] 허용 도메인 목록이 실제 서비스 도메인만 포함
- [ ] `allow_credentials: false` 설정 확인
- [ ] 최소 권한 원칙 적용 (필요한 메소드/헤더만 허용)
- [ ] 환경변수로 Origin 설정 외부화 완료

#### 🟡 **정기 보안 점검 (월 1회)**
- [ ] 불필요한 허용 Origin 제거
- [ ] CORS 요청 로그 분석 및 이상 패턴 감지
- [ ] 보안 헤더 정상 동작 확인
- [ ] 브라우저 개발자 도구로 CORS 헤더 검증

#### 🟢 **개발 환경 보안 가이드라인**
- [ ] 개발 환경에서도 localhost 외 Origin 제한
- [ ] 스테이징 환경은 프로덕션과 유사한 보안 설정
- [ ] CORS 설정 변경 시 보안 팀 리뷰 필수

## 📊 보안 성숙도 로드맵

### **Phase 1: 기본 보안 (즉시 구현)**
- ✅ 와일드카드 Origin 제거
- ✅ 환경별 CORS 정책 분리
- ✅ 최소 권한 원칙 적용

### **Phase 2: 고급 보안 (1개월 내)**
- 🔄 보안 헤더 추가 (CSP, X-Frame-Options 등)
- 🔄 CORS 요청 모니터링 및 로깅
- 🔄 자동화된 보안 테스트 구축

### **Phase 3: 엔터프라이즈 보안 (3개월 내)**
- ⏳ Rate Limiting 및 DDoS 보호
- ⏳ API 키 기반 인증 추가
- ⏳ 실시간 보안 위협 탐지

## 🚨 긴급 대응 방안

### **보안 사고 발생 시 즉시 조치**

1. **즉시 차단**
   ```bash
   # Nginx에서 긴급 차단
   location /api/ {
       add_header Access-Control-Allow-Origin "https://trusted-domain.com" always;
       # 기타 설정...
   }
   ```

2. **로그 분석**
   ```bash
   # 의심스러운 CORS 요청 패턴 분석
   grep "CORS" /app/logs/smarteye.log | grep -E "malicious|suspicious"
   ```

3. **긴급 설정 롤백**
   ```bash
   # 이전 안전한 설정으로 롤백
   kubectl rollout undo deployment/smarteye-backend
   ```

---

**📋 요약**: 현재 SmartEye의 CORS 설정은 와일드카드 허용으로 인한 심각한 보안 취약점이 존재합니다. 환경별 세밀한 정책 분리와 최소 권한 원칙 적용을 통해 보안을 크게 강화할 수 있습니다.