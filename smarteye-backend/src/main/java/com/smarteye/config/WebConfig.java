package com.smarteye.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${smarteye.upload.directory}")
    private String uploadDirectory;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
                
        registry.addMapping("/static/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET")
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
    }
}