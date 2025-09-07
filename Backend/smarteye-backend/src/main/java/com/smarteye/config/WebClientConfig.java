package com.smarteye.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 설정
 * LAM 서비스 및 OpenAI API 호출을 위한 HTTP 클라이언트 구성
 */
@Configuration
public class WebClientConfig {
    
    /**
     * LAM 서비스용 WebClient
     * 대용량 이미지 처리를 위한 긴 타임아웃 설정
     */
    @Bean(name = "lamWebClientBuilder")
    public WebClient.Builder lamWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30초 연결 타임아웃
            .responseTimeout(Duration.ofSeconds(120)) // 2분 응답 타임아웃
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> 
                configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
            .defaultHeader("User-Agent", "SmartEye-Backend/1.0");
    }
    
    /**
     * OpenAI API용 WebClient  
     * 일반적인 API 호출을 위한 표준 타임아웃 설정
     */
    @Bean(name = "openaiWebClientBuilder")
    public WebClient.Builder openaiWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10초 연결 타임아웃
            .responseTimeout(Duration.ofSeconds(60)) // 1분 응답 타임아웃
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> 
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .defaultHeader("User-Agent", "SmartEye-Backend/1.0")
            .defaultHeader("Content-Type", "application/json");
    }
    
    /**
     * 기본 WebClient (다른 API 호출용)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5초 연결 타임아웃
            .responseTimeout(Duration.ofSeconds(30)) // 30초 응답 타임아웃
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> 
                configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)) // 5MB
            .defaultHeader("User-Agent", "SmartEye-Backend/1.0");
    }
    
    /**
     * RestTemplate Bean 정의
     * MemoryService에서 사용하는 레거시 HTTP 클라이언트
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}