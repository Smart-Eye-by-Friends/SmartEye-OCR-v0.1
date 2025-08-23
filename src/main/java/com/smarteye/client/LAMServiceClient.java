package com.smarteye.client;

import com.smarteye.dto.lam.*;
import com.smarteye.exception.LAMServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.CompletableFuture;

/**
 * LAM (Layout Analysis Module) 마이크로서비스 클라이언트
 * RestTemplate 기반 HTTP 클라이언트로 LAM 서비스와 통신
 */
@Component
@Slf4j
public class LAMServiceClient {

    private final RestTemplate restTemplate;

    @Value("${smarteye.lam.service.url:http://localhost:8081}")
    private String lamServiceUrl;

    @Value("${smarteye.lam.service.timeout:30}")
    private int timeoutSeconds;

    @Value("${smarteye.lam.service.retries:3}")
    private int maxRetries;

    @Value("${smarteye.lam.service.confidence-threshold:0.5}")
    private double defaultConfidenceThreshold;

    public LAMServiceClient(@Qualifier("lamRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 레이아웃 분석 요청 (동기)
     */
    public LAMAnalysisResponse analyzeLayout(LAMAnalysisRequest request) {
        log.info("LAM 서비스 레이아웃 분석 요청 시작");
        
        try {
            String url = lamServiceUrl + "/analyze/layout";
            long startTime = System.currentTimeMillis();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<LAMAnalysisRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<LAMAnalysisResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                LAMAnalysisResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                LAMAnalysisResponse responseBody = response.getBody();
                long processingTime = System.currentTimeMillis() - startTime;
                log.info("LAM 분석 완료 - 처리 시간: {}ms, 블록 수: {}", 
                    processingTime,
                    responseBody != null && responseBody.getBlocks() != null ? responseBody.getBlocks().size() : 0);
                return responseBody;
            } else {
                throw new LAMServiceException("LAM 서비스에서 유효하지 않은 응답을 받았습니다");
            }
            
        } catch (HttpClientErrorException e) {
            log.error("LAM 서비스 클라이언트 오류 - Status: {}, Body: {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new LAMServiceException("LAM 서비스 클라이언트 오류: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("LAM 서비스 서버 오류 - Status: {}, Body: {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new LAMServiceException("LAM 서비스 서버 오류: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("LAM 서비스 연결 오류 - Message: {}", e.getMessage());
            throw new LAMServiceException("LAM 서비스에 연결할 수 없습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("LAM 서비스 호출 중 예상치 못한 오류", e);
            throw new LAMServiceException("LAM 서비스 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 레이아웃 분석 요청 (비동기)
     */
    public CompletableFuture<LAMAnalysisResponse> analyzeLayoutAsync(LAMAnalysisRequest request) {
        return CompletableFuture.supplyAsync(() -> analyzeLayout(request));
    }

    /**
     * LAM 서비스 상태 확인
     */
    public LAMHealthResponse getHealth() {
        log.info("LAM 서비스 상태 확인 요청");
        
        try {
            String url = lamServiceUrl + "/health";
            
            ResponseEntity<LAMHealthResponse> response = restTemplate.getForEntity(url, LAMHealthResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                LAMHealthResponse healthResponse = response.getBody();
                log.info("LAM 서비스 상태 확인 완료 - Status: {}", 
                    healthResponse != null ? healthResponse.getStatus() : "UNKNOWN");
                return healthResponse;
            } else {
                throw new LAMServiceException("LAM 서비스에서 유효하지 않은 상태 응답을 받았습니다");
            }
            
        } catch (Exception e) {
            log.error("LAM 서비스 상태 확인 실패: {}", e.getMessage());
            throw new LAMServiceException("LAM 서비스 상태 확인 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * LAM 모델 정보 조회
     */
    public LAMModelInfo getModelInfo() {
        log.info("LAM 모델 정보 조회 요청");
        
        try {
            String url = lamServiceUrl + "/models/info";
            
            ResponseEntity<LAMModelInfo> response = restTemplate.getForEntity(url, LAMModelInfo.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                LAMModelInfo modelInfo = response.getBody();
                log.info("LAM 모델 정보 조회 완료 - Model: {}", 
                    modelInfo != null ? modelInfo.getModelName() : "UNKNOWN");
                return modelInfo;
            } else {
                throw new LAMServiceException("LAM 서비스에서 유효하지 않은 모델 정보 응답을 받았습니다");
            }
            
        } catch (Exception e) {
            log.error("LAM 모델 정보 조회 실패: {}", e.getMessage());
            throw new LAMServiceException("LAM 모델 정보 조회 실패: " + e.getMessage(), e);
        }
    }
}