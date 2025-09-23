package com.smarteye.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Environment environment;

    @Value("${smarteye.upload.directory}")
    private String uploadDirectory;

    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;

    public WebConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 환경별 CORS 정책 설정
        boolean isDevelopment = environment.acceptsProfiles("dev", "development");
        boolean isProduction = environment.acceptsProfiles("prod", "production");

        if (isDevelopment) {
            // 개발 환경: localhost:3000 허용
            registry.addMapping("/api/**")
                    .allowedOrigins(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:3001"  // 추가 개발 포트
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                    .allowedHeaders("*")
                    .exposedHeaders(
                        "Access-Control-Allow-Origin",
                        "Access-Control-Allow-Credentials",
                        "Access-Control-Allow-Methods",
                        "Access-Control-Allow-Headers",
                        "Access-Control-Max-Age",
                        "Content-Type",
                        "Authorization",
                        "X-Requested-With"
                    )
                    .allowCredentials(true)
                    .maxAge(3600);
        } else if (isProduction) {
            // 프로덕션 환경: 특정 도메인만 허용
            registry.addMapping("/api/**")
                    .allowedOrigins(
                        "https://smarteye.example.com",
                        "https://app.smarteye.com"
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                    .allowedHeaders(
                        "Content-Type",
                        "Authorization",
                        "X-Requested-With",
                        "Accept",
                        "Origin"
                    )
                    .exposedHeaders(
                        "Content-Type",
                        "Authorization"
                    )
                    .allowCredentials(true)
                    .maxAge(7200);
        } else {
            // 기본 환경 (테스트 등): 모든 Origin 허용
            registry.addMapping("/api/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .allowCredentials(false)  // 패턴 사용 시 credentials false
                    .maxAge(3600);
        }

        // 정적 파일 CORS 설정 (모든 환경 공통)
        registry.addMapping("/static/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(86400);

        // Swagger UI CORS 설정
        registry.addMapping("/swagger-ui/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(86400);
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static files serving for analysis results (images, JSON, Word docs)
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + staticDirectory + "/")
                .setCachePeriod(86400); // 1 day cache
                
        // Uploaded files serving
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDirectory + "/")
                .setCachePeriod(3600); // 1 hour cache
                
        // Swagger UI resources
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/")
                .setCachePeriod(86400);
                
        // Default static resource handler should not interfere with API endpoints
        // Only handle root path and common static resources
        registry.addResourceHandler("/", "/index.html", "/favicon.ico", "/robots.txt")
                .addResourceLocations("classpath:/static/", "classpath:/public/")
                .setCachePeriod(86400);
    }
}