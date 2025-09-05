package com.smarteye.controller;

import com.smarteye.service.PerformanceMonitoringService;
import com.smarteye.service.LAMService;
import com.smarteye.service.TSPMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 3단계: 시스템 최적화 및 성능 모니터링 컨트롤러
 */
@RestController
@RequestMapping("/api/v3/monitoring")
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringController {
    
    private final PerformanceMonitoringService performanceService;
    private final LAMService lamService;
    private final TSPMService tspmService;
    
    /**
     * 전체 시스템 성능 요약
     */
    @GetMapping("/performance/summary")
    public ResponseEntity<Map<String, Object>> getPerformanceSummary() {
        log.info("시스템 성능 요약 조회 요청");
        Map<String, Object> summary = performanceService.getSystemPerformanceSummary();
        return ResponseEntity.ok(summary);
    }
    
    /**
     * 특정 서비스 성능 조회
     */
    @GetMapping("/performance/service/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServicePerformance(@PathVariable String serviceName) {
        log.info("서비스 성능 조회 요청 - 서비스: {}", serviceName);
        Map<String, Object> performance = performanceService.getServicePerformance(serviceName);
        return ResponseEntity.ok(performance);
    }
    
    /**
     * 성능 임계값 알림
     */
    @GetMapping("/performance/alerts")
    public ResponseEntity<Map<String, Object>> getPerformanceAlerts() {
        log.info("성능 임계값 알림 조회 요청");
        Map<String, Object> alerts = performanceService.checkPerformanceThresholds();
        
        Map<String, Object> response = new HashMap<>();
        response.put("alerts", alerts);
        response.put("alert_count", alerts.size());
        response.put("status", alerts.isEmpty() ? "healthy" : "warning");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 시스템 상태 대시보드
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getSystemDashboard() {
        log.info("시스템 대시보드 조회 요청");
        
        Map<String, Object> dashboard = new HashMap<>();
        
        // 전체 성능 요약
        dashboard.put("performance", performanceService.getSystemPerformanceSummary());
        
        // 서비스별 상태
        Map<String, Object> serviceStatus = new HashMap<>();
        
        try {
            // LAM 마이크로서비스 상태
            var lamHealth = lamService.checkMicroserviceHealth();
            serviceStatus.put("lam_microservice", Map.of(
                "status", lamHealth.getStatus(),
                "message", lamHealth.getMessage() != null ? lamHealth.getMessage() : "OK"
            ));
        } catch (Exception e) {
            serviceStatus.put("lam_microservice", Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
        
        try {
            // TSPM Java 네이티브 상태
            boolean tspmAvailable = tspmService.isJavaTSPMAvailable();
            serviceStatus.put("tspm_java_native", Map.of(
                "status", tspmAvailable ? "healthy" : "unavailable",
                "message", tspmAvailable ? "Java TSPM 사용 가능" : "Java TSPM 사용 불가"
            ));
        } catch (Exception e) {
            serviceStatus.put("tspm_java_native", Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
        
        dashboard.put("services", serviceStatus);
        
        // 성능 알림
        dashboard.put("alerts", performanceService.checkPerformanceThresholds());
        
        return ResponseEntity.ok(dashboard);
    }
    
    /**
     * 성능 메트릭 초기화
     */
    @PostMapping("/performance/reset")
    public ResponseEntity<Map<String, Object>> resetPerformanceMetrics() {
        log.info("성능 메트릭 초기화 요청");
        performanceService.resetMetrics();
        
        return ResponseEntity.ok(Map.of(
            "message", "성능 메트릭이 초기화되었습니다",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * LAM 모델 정보 조회
     */
    @GetMapping("/lam/model-info")
    public ResponseEntity<Map<String, Object>> getLAMModelInfo() {
        log.info("LAM 모델 정보 조회 요청");
        
        try {
            var modelInfo = lamService.getMicroserviceModelInfo();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "model_info", modelInfo
            ));
        } catch (Exception e) {
            log.error("LAM 모델 정보 조회 실패", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
