package com.smarteye.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 보안 설정
 * 환경변수를 통해 허용된 오리진만 설정 가능
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        List<String> methods = Arrays.asList(allowedMethods.split(","));
        
        if (allowedHeaders.equals("*")) {
            registry.addMapping("/api/**")
                    .allowedOrigins(origins.toArray(new String[0]))
                    .allowedMethods(methods.toArray(new String[0]))
                    .allowedHeaders("*")
                    .allowCredentials(allowCredentials)
                    .maxAge(maxAge);
        } else {
            List<String> headers = Arrays.asList(allowedHeaders.split(","));
            registry.addMapping("/api/**")
                    .allowedOrigins(origins.toArray(new String[0]))
                    .allowedMethods(methods.toArray(new String[0]))
                    .allowedHeaders(headers.toArray(new String[0]))
                    .allowCredentials(allowCredentials)
                    .maxAge(maxAge);
        }
                
        // Swagger UI를 위한 추가 설정
        registry.addMapping("/swagger-ui/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST")
                .allowCredentials(false);
                
        registry.addMapping("/v3/api-docs/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET")
                .allowCredentials(false);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        List<String> methods = Arrays.asList(allowedMethods.split(","));
        
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(methods);
        
        if (allowedHeaders.equals("*")) {
            configuration.addAllowedHeader("*");
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }
        
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}