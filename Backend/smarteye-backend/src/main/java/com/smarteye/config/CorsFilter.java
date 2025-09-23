package com.smarteye.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CORS 필터 - 추가적인 헤더 보안 설정
 * Spring Boot 3.5.5 호환성을 위한 Jakarta Servlet API 사용
 */
@Component
public class CorsFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorsFilter.class);

    private final Environment environment;

    public CorsFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String origin = httpRequest.getHeader("Origin");
        String method = httpRequest.getMethod();
        String requestUri = httpRequest.getRequestURI();

        // 환경별 CORS 정책 확인
        boolean isDevelopment = environment.acceptsProfiles("dev", "development");

        // API 엔드포인트만 처리
        if (requestUri.startsWith("/api/")) {

            // Preflight 요청 처리
            if ("OPTIONS".equalsIgnoreCase(method)) {
                logger.debug("CORS Preflight 요청 처리 - Origin: {}, URI: {}", origin, requestUri);

                if (isDevelopment && isAllowedOrigin(origin, true)) {
                    setDevelopmentCorsHeaders(httpResponse, origin);
                } else if (!isDevelopment && isAllowedOrigin(origin, false)) {
                    setProductionCorsHeaders(httpResponse, origin);
                } else {
                    logger.warn("허용되지 않은 Origin에서의 CORS 요청: {}", origin);
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                httpResponse.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            // 실제 요청 처리
            if (isDevelopment && isAllowedOrigin(origin, true)) {
                setDevelopmentCorsHeaders(httpResponse, origin);
                logger.debug("개발 환경 CORS 헤더 설정 완료 - Origin: {}", origin);
            } else if (!isDevelopment && isAllowedOrigin(origin, false)) {
                setProductionCorsHeaders(httpResponse, origin);
                logger.debug("프로덕션 환경 CORS 헤더 설정 완료 - Origin: {}", origin);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 허용된 Origin인지 확인
     */
    private boolean isAllowedOrigin(String origin, boolean isDevelopment) {
        if (origin == null) {
            return false;
        }

        if (isDevelopment) {
            // 개발 환경: localhost 허용
            return origin.equals("http://localhost:3000") ||
                   origin.equals("http://127.0.0.1:3000") ||
                   origin.equals("http://localhost:3001");
        } else {
            // 프로덕션 환경: 특정 도메인만 허용
            return origin.equals("https://smarteye.example.com") ||
                   origin.equals("https://app.smarteye.com");
        }
    }

    /**
     * 개발 환경 CORS 헤더 설정
     */
    private void setDevelopmentCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods",
            "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
        response.setHeader("Access-Control-Allow-Headers",
            "Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control");
        response.setHeader("Access-Control-Expose-Headers",
            "Access-Control-Allow-Origin, Access-Control-Allow-Credentials");
        response.setHeader("Access-Control-Max-Age", "3600");

        // 추가 보안 헤더
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
    }

    /**
     * 프로덕션 환경 CORS 헤더 설정
     */
    private void setProductionCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods",
            "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        response.setHeader("Access-Control-Allow-Headers",
            "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        response.setHeader("Access-Control-Expose-Headers",
            "Content-Type, Authorization");
        response.setHeader("Access-Control-Max-Age", "7200");

        // 프로덕션 보안 헤더
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("CORS 필터 초기화 완료 - 환경: {}",
            environment.acceptsProfiles("dev") ? "개발" : "프로덕션");
    }

    @Override
    public void destroy() {
        logger.info("CORS 필터 종료");
    }
}