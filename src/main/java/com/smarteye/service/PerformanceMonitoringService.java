package com.smarteye.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 시스템 성능 모니터링 서비스
 * 분석 성능 추적 및 벤치마킹
 */
@Service
@Slf4j
public class PerformanceMonitoringService {
    
    private final Map<String, PerformanceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    /**
     * 성능 메트릭 클래스
     */
    public static class PerformanceMetrics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private volatile long minProcessingTime = Long.MAX_VALUE;
        private volatile long maxProcessingTime = 0;
        private volatile LocalDateTime lastRequest;
        
        public void recordRequest(long processingTimeMs, boolean success) {
            requestCount.incrementAndGet();
            totalProcessingTime.addAndGet(processingTimeMs);
            
            if (processingTimeMs < minProcessingTime) {
                minProcessingTime = processingTimeMs;
            }
            if (processingTimeMs > maxProcessingTime) {
                maxProcessingTime = processingTimeMs;
            }
            
            if (!success) {
                errorCount.incrementAndGet();
            }
            
            lastRequest = LocalDateTime.now();
        }
        
        public double getAverageProcessingTime() {
            long count = requestCount.get();
            return count > 0 ? (double) totalProcessingTime.get() / count : 0.0;
        }
        
        public double getErrorRate() {
            long count = requestCount.get();
            return count > 0 ? (double) errorCount.get() / count * 100.0 : 0.0;
        }
        
        // Getters
        public long getRequestCount() { return requestCount.get(); }
        public long getTotalProcessingTime() { return totalProcessingTime.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public long getMinProcessingTime() { return minProcessingTime == Long.MAX_VALUE ? 0 : minProcessingTime; }
        public long getMaxProcessingTime() { return maxProcessingTime; }
        public LocalDateTime getLastRequest() { return lastRequest; }
    }
    
    /**
     * 서비스 성능 기록
     */
    public void recordServicePerformance(String serviceName, long processingTimeMs, boolean success) {
        serviceMetrics.computeIfAbsent(serviceName, k -> new PerformanceMetrics())
                    .recordRequest(processingTimeMs, success);
        
        totalRequests.incrementAndGet();
        if (!success) {
            totalErrors.incrementAndGet();
        }
        
        log.debug("성능 기록 - 서비스: {}, 처리시간: {}ms, 성공: {}", 
                serviceName, processingTimeMs, success);
    }
    
    /**
     * 전체 시스템 성능 요약
     */
    public Map<String, Object> getSystemPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // 전체 통계
        summary.put("total_requests", totalRequests.get());
        summary.put("total_errors", totalErrors.get());
        summary.put("overall_error_rate", 
                totalRequests.get() > 0 ? 
                (double) totalErrors.get() / totalRequests.get() * 100.0 : 0.0);
        
        // 서비스별 통계
        Map<String, Map<String, Object>> serviceStats = new HashMap<>();
        for (Map.Entry<String, PerformanceMetrics> entry : serviceMetrics.entrySet()) {
            String serviceName = entry.getKey();
            PerformanceMetrics metrics = entry.getValue();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("request_count", metrics.getRequestCount());
            stats.put("error_count", metrics.getErrorCount());
            stats.put("error_rate", metrics.getErrorRate());
            stats.put("avg_processing_time_ms", metrics.getAverageProcessingTime());
            stats.put("min_processing_time_ms", metrics.getMinProcessingTime());
            stats.put("max_processing_time_ms", metrics.getMaxProcessingTime());
            stats.put("last_request", metrics.getLastRequest());
            
            serviceStats.put(serviceName, stats);
        }
        
        summary.put("services", serviceStats);
        summary.put("generated_at", LocalDateTime.now());
        
        return summary;
    }
    
    /**
     * 특정 서비스 성능 조회
     */
    public Map<String, Object> getServicePerformance(String serviceName) {
        PerformanceMetrics metrics = serviceMetrics.get(serviceName);
        if (metrics == null) {
            return Map.of("error", "Service not found: " + serviceName);
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("service_name", serviceName);
        stats.put("request_count", metrics.getRequestCount());
        stats.put("error_count", metrics.getErrorCount());
        stats.put("error_rate", metrics.getErrorRate());
        stats.put("avg_processing_time_ms", metrics.getAverageProcessingTime());
        stats.put("min_processing_time_ms", metrics.getMinProcessingTime());
        stats.put("max_processing_time_ms", metrics.getMaxProcessingTime());
        stats.put("total_processing_time_ms", metrics.getTotalProcessingTime());
        stats.put("last_request", metrics.getLastRequest());
        stats.put("generated_at", LocalDateTime.now());
        
        return stats;
    }
    
    /**
     * 성능 메트릭 초기화
     */
    public void resetMetrics() {
        serviceMetrics.clear();
        totalRequests.set(0);
        totalErrors.set(0);
        log.info("성능 메트릭이 초기화되었습니다");
    }
    
    /**
     * 성능 임계값 확인
     */
    public Map<String, Object> checkPerformanceThresholds() {
        Map<String, Object> alerts = new HashMap<>();
        
        for (Map.Entry<String, PerformanceMetrics> entry : serviceMetrics.entrySet()) {
            String serviceName = entry.getKey();
            PerformanceMetrics metrics = entry.getValue();
            
            // 에러율 임계값 확인 (10% 이상)
            if (metrics.getErrorRate() > 10.0) {
                alerts.put(serviceName + "_high_error_rate", 
                    String.format("%.2f%% (임계값: 10%%)", metrics.getErrorRate()));
            }
            
            // 평균 응답 시간 임계값 확인 (30초 이상)
            if (metrics.getAverageProcessingTime() > 30000) {
                alerts.put(serviceName + "_slow_response", 
                    String.format("%.2f초 (임계값: 30초)", metrics.getAverageProcessingTime() / 1000.0));
            }
        }
        
        return alerts;
    }
}
