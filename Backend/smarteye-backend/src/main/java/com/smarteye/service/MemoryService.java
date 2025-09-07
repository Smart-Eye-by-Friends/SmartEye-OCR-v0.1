package com.smarteye.service;

import com.smarteye.config.SmartEyeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Level 4: 설정 기반 메모리 관리 서비스
 * 시스템 전반의 메모리 사용량을 모니터링하고 최적화
 */
@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);

    @Autowired
    private SmartEyeProperties properties;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${smarteye.services.lam.base-url}")
    private String lamServiceUrl;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final AtomicLong gcCallCount = new AtomicLong(0);
    private final AtomicLong memoryCleanupCount = new AtomicLong(0);

    // 메모리 설정 캐시
    private MemoryConfig memoryConfig;

    @PostConstruct
    public void initialize() {
        loadMemoryConfig();
        logger.info("메모리 관리 서비스 초기화 완료");
        logger.info("메모리 설정: {}", memoryConfig);
    }

    /**
     * application.yml에서 메모리 설정 로드
     */
    private void loadMemoryConfig() {
        SmartEyeProperties.Processing processing = properties.getProcessing();
        
        memoryConfig = new MemoryConfig();
        
        // PDF 스트리밍 설정 (기본값 설정)
        memoryConfig.pdfStreamingThreshold = 50;
        memoryConfig.pdfBatchSize = 5;
        
        // 메모리 최적화 설정
        memoryConfig.forceMemoryOptimization = false;
        memoryConfig.enableUnifiedCIM = true;
        
        // 메모리 임계값 (MB)
        memoryConfig.warningThreshold = 2048;
        memoryConfig.criticalThreshold = 4096;
        
        // 자동 정리 설정
        memoryConfig.autoCleanup = true;
        memoryConfig.gcInterval = 300;
        
        // LAM 서비스 메모리 제한
        memoryConfig.lamMemoryLimit = 3072;
        memoryConfig.lamModelCacheSize = 2;
        
        // LAM 서비스 메모리 관리
        memoryConfig.lamAutoCleanupEnabled = true;
        memoryConfig.lamCleanupAfterAnalysis = true;
        memoryConfig.lamUnloadModelsThreshold = 4096;
        memoryConfig.lamHealthCheckInterval = 30;
    }

    /**
     * 현재 JVM 메모리 사용량 조회
     */
    public Map<String, Object> getJVMMemoryUsage() {
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();
        
        Map<String, Object> memoryInfo = new HashMap<>();
        
        // Heap 메모리 정보 (MB 단위)
        memoryInfo.put("heap_used_mb", heapMemory.getUsed() / 1024 / 1024);
        memoryInfo.put("heap_max_mb", heapMemory.getMax() / 1024 / 1024);
        memoryInfo.put("heap_usage_percent", 
            Math.round((double) heapMemory.getUsed() / heapMemory.getMax() * 100));
        
        // Non-Heap 메모리 정보
        memoryInfo.put("non_heap_used_mb", nonHeapMemory.getUsed() / 1024 / 1024);
        memoryInfo.put("non_heap_max_mb", nonHeapMemory.getMax() / 1024 / 1024);
        
        // 전체 메모리 사용량
        long totalUsedMb = (heapMemory.getUsed() + nonHeapMemory.getUsed()) / 1024 / 1024;
        memoryInfo.put("total_used_mb", totalUsedMb);
        
        // 메모리 상태 평가
        String status = "normal";
        if (totalUsedMb > memoryConfig.criticalThreshold) {
            status = "critical";
        } else if (totalUsedMb > memoryConfig.warningThreshold) {
            status = "warning";
        }
        memoryInfo.put("status", status);
        
        // 통계 정보
        memoryInfo.put("gc_call_count", gcCallCount.get());
        memoryInfo.put("memory_cleanup_count", memoryCleanupCount.get());
        
        return memoryInfo;
    }

    /**
     * LAM 서비스 메모리 사용량 조회
     */
    public Map<String, Object> getLAMMemoryUsage() {
        try {
            String url = lamServiceUrl + "/memory/stats";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                logger.warn("LAM 서비스 메모리 정보 조회 실패: {}", response.getStatusCode());
                return createErrorResponse("LAM 서비스 응답 오류");
            }
            
        } catch (Exception e) {
            logger.error("LAM 서비스 메모리 정보 조회 중 오류: {}", e.getMessage());
            return createErrorResponse("LAM 서비스 연결 실패: " + e.getMessage());
        }
    }

    /**
     * 전체 시스템 메모리 사용량 조회
     */
    public Map<String, Object> getSystemMemoryUsage() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        // JVM 메모리 정보
        systemInfo.put("jvm_memory", getJVMMemoryUsage());
        
        // LAM 서비스 메모리 정보
        systemInfo.put("lam_service", getLAMMemoryUsage());
        
        // 메모리 설정 정보
        systemInfo.put("memory_config", memoryConfig.toMap());
        
        // 전체 시스템 상태 평가
        Map<String, Object> jvmMemory = (Map<String, Object>) systemInfo.get("jvm_memory");
        String overallStatus = (String) jvmMemory.get("status");
        systemInfo.put("overall_status", overallStatus);
        
        return systemInfo;
    }

    /**
     * JVM 메모리 정리 (가비지 컬렉터 강제 실행)
     */
    public Map<String, Object> cleanupJVMMemory() {
        logger.info("JVM 메모리 정리 시작");
        
        Map<String, Object> beforeMemory = getJVMMemoryUsage();
        long beforeUsed = (Long) beforeMemory.get("total_used_mb");
        
        // 가비지 컬렉터 실행
        System.gc();
        gcCallCount.incrementAndGet();
        
        // 정리 후 메모리 사용량 확인 (약간의 딜레이 후)
        try {
            Thread.sleep(1000); // 1초 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> afterMemory = getJVMMemoryUsage();
        long afterUsed = (Long) afterMemory.get("total_used_mb");
        long memoryFreed = beforeUsed - afterUsed;
        
        memoryCleanupCount.incrementAndGet();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("memory_before", beforeMemory);
        result.put("memory_after", afterMemory);
        result.put("memory_freed_mb", memoryFreed);
        result.put("message", String.format("%.2fMB 메모리 해제", (double) memoryFreed));
        
        logger.info("JVM 메모리 정리 완료: {}MB 해제", memoryFreed);
        return result;
    }

    /**
     * LAM 서비스 메모리 정리 요청
     */
    public Map<String, Object> cleanupLAMMemory() {
        try {
            String url = lamServiceUrl + "/memory/cleanup";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("LAM 서비스 메모리 정리 완료");
                return response.getBody();
            } else {
                logger.warn("LAM 서비스 메모리 정리 실패: {}", response.getStatusCode());
                return createErrorResponse("LAM 서비스 메모리 정리 실패");
            }
            
        } catch (Exception e) {
            logger.error("LAM 서비스 메모리 정리 중 오류: {}", e.getMessage());
            return createErrorResponse("LAM 서비스 연결 실패: " + e.getMessage());
        }
    }

    /**
     * 전체 시스템 메모리 정리
     */
    public Map<String, Object> cleanupSystemMemory() {
        logger.info("전체 시스템 메모리 정리 시작");
        
        Map<String, Object> result = new HashMap<>();
        
        // JVM 메모리 정리
        Map<String, Object> jvmResult = cleanupJVMMemory();
        result.put("jvm_cleanup", jvmResult);
        
        // LAM 서비스 메모리 정리
        Map<String, Object> lamResult = cleanupLAMMemory();
        result.put("lam_cleanup", lamResult);
        
        // 전체 결과
        boolean success = (Boolean) jvmResult.get("success") && 
                         (Boolean) lamResult.getOrDefault("success", false);
        result.put("overall_success", success);
        result.put("message", "전체 시스템 메모리 정리 " + (success ? "성공" : "부분 실패"));
        
        logger.info("전체 시스템 메모리 정리 완료: {}", success ? "성공" : "부분 실패");
        return result;
    }

    /**
     * 메모리 임계값 체크 및 자동 정리
     */
    public boolean checkMemoryThresholdAndCleanup() {
        Map<String, Object> memoryInfo = getJVMMemoryUsage();
        long totalUsedMb = (Long) memoryInfo.get("total_used_mb");
        String status = (String) memoryInfo.get("status");
        
        if ("critical".equals(status) && memoryConfig.autoCleanup) {
            logger.warn("메모리 사용량이 임계치를 초과했습니다 ({}MB > {}MB). 자동 정리를 시작합니다.",
                totalUsedMb, memoryConfig.criticalThreshold);
            
            cleanupSystemMemory();
            return true;
            
        } else if ("warning".equals(status)) {
            logger.info("메모리 사용량 경고 수준 ({}MB > {}MB)",
                totalUsedMb, memoryConfig.warningThreshold);
        }
        
        return false;
    }

    /**
     * 스케줄링된 메모리 모니터링 및 정리 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    public void scheduledMemoryCheck() {
        if (memoryConfig.autoCleanup) {
            logger.debug("스케줄링된 메모리 체크 실행");
            checkMemoryThresholdAndCleanup();
        }
    }

    /**
     * PDF 처리를 위한 메모리 최적화 설정 조회
     */
    public boolean shouldUsePDFStreaming(int pageCount) {
        return memoryConfig.forceMemoryOptimization || 
               pageCount > memoryConfig.pdfStreamingThreshold;
    }

    /**
     * PDF 배치 크기 조회
     */
    public int getPDFBatchSize() {
        return memoryConfig.pdfBatchSize;
    }

    /**
     * 통합 CIM 사용 여부 조회
     */
    public boolean isUnifiedCIMEnabled() {
        return memoryConfig.enableUnifiedCIM;
    }

    /**
     * 메모리 설정 조회
     */
    public Map<String, Object> getMemoryConfig() {
        return memoryConfig.toMap();
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        return errorResponse;
    }

    /**
     * 메모리 설정을 담는 내부 클래스
     */
    private static class MemoryConfig {
        // PDF 스트리밍 설정
        int pdfStreamingThreshold;
        int pdfBatchSize;
        
        // CIM 처리 메모리 설정
        boolean forceMemoryOptimization;
        boolean enableUnifiedCIM;
        
        // 메모리 임계값 (MB)
        long warningThreshold;
        long criticalThreshold;
        
        // 메모리 정리 설정
        boolean autoCleanup;
        int gcInterval;
        
        // LAM 서비스 메모리 제한
        long lamMemoryLimit;
        int lamModelCacheSize;
        
        // LAM 서비스 메모리 관리
        boolean lamAutoCleanupEnabled;
        boolean lamCleanupAfterAnalysis;
        long lamUnloadModelsThreshold;
        int lamHealthCheckInterval;
        
        public Map<String, Object> toMap() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("pdf_streaming_threshold", pdfStreamingThreshold);
            configMap.put("pdf_batch_size", pdfBatchSize);
            configMap.put("force_memory_optimization", forceMemoryOptimization);
            configMap.put("enable_unified_cim", enableUnifiedCIM);
            configMap.put("warning_threshold_mb", warningThreshold);
            configMap.put("critical_threshold_mb", criticalThreshold);
            configMap.put("auto_cleanup", autoCleanup);
            configMap.put("gc_interval_sec", gcInterval);
            configMap.put("lam_memory_limit_mb", lamMemoryLimit);
            configMap.put("lam_model_cache_size", lamModelCacheSize);
            configMap.put("lam_auto_cleanup_enabled", lamAutoCleanupEnabled);
            configMap.put("lam_cleanup_after_analysis", lamCleanupAfterAnalysis);
            configMap.put("lam_unload_models_threshold_mb", lamUnloadModelsThreshold);
            configMap.put("lam_health_check_interval_sec", lamHealthCheckInterval);
            return configMap;
        }

        @Override
        public String toString() {
            return String.format("MemoryConfig{PDF임계값=%d, CIM통합=%s, 경고임계값=%dMB, 위험임계값=%dMB}",
                pdfStreamingThreshold, enableUnifiedCIM, warningThreshold, criticalThreshold);
        }
    }
}