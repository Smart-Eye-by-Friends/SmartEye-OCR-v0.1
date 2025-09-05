package com.smarteye.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.time.Duration;

/**
 * LAM 서비스 클라이언트 설정
 */
@Configuration
public class LAMClientConfig {
    
    /**
     * LAM 서비스 통신용 RestTemplate
     */
    @Bean
    @Qualifier("lamRestTemplate")
    public RestTemplate lamRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
