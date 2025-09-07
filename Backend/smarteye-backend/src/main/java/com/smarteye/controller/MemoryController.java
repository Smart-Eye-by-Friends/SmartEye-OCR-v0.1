package com.smarteye.controller;

import com.smarteye.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Level 4: 메모리 관리 REST API 컨트롤러
 * 시스템 메모리 모니터링 및 최적화 제어
 */
@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
@Tag(name = "Memory Management", description = "시스템 메모리 관리 API")
public class MemoryController {

    @Autowired
    private MemoryService memoryService;

    /**
     * JVM 메모리 사용량 조회
     */
    @GetMapping("/jvm")
    @Operation(summary = "JVM 메모리 사용량 조회", description = "현재 JVM의 Heap/Non-Heap 메모리 사용량을 조회합니다")
    public ResponseEntity<Map<String, Object>> getJVMMemoryUsage() {
        Map<String, Object> memoryInfo = memoryService.getJVMMemoryUsage();
        return ResponseEntity.ok(memoryInfo);
    }

    /**
     * LAM 서비스 메모리 사용량 조회
     */
    @GetMapping("/lam")
    @Operation(summary = "LAM 서비스 메모리 사용량 조회", description = "LAM 서비스의 메모리 사용량과 모델 캐시 정보를 조회합니다")
    public ResponseEntity<Map<String, Object>> getLAMMemoryUsage() {
        Map<String, Object> lamMemoryInfo = memoryService.getLAMMemoryUsage();
        return ResponseEntity.ok(lamMemoryInfo);
    }

    /**
     * 전체 시스템 메모리 사용량 조회
     */
    @GetMapping("/system")
    @Operation(summary = "전체 시스템 메모리 사용량 조회", description = "JVM과 LAM 서비스를 포함한 전체 시스템의 메모리 사용량을 조회합니다")
    public ResponseEntity<Map<String, Object>> getSystemMemoryUsage() {
        Map<String, Object> systemMemoryInfo = memoryService.getSystemMemoryUsage();
        return ResponseEntity.ok(systemMemoryInfo);
    }

    /**
     * 메모리 설정 조회
     */
    @GetMapping("/config")
    @Operation(summary = "메모리 관리 설정 조회", description = "현재 적용된 메모리 최적화 설정을 조회합니다")
    public ResponseEntity<Map<String, Object>> getMemoryConfig() {
        Map<String, Object> config = memoryService.getMemoryConfig();
        return ResponseEntity.ok(config);
    }

    /**
     * JVM 메모리 정리
     */
    @PostMapping("/cleanup/jvm")
    @Operation(summary = "JVM 메모리 정리", description = "JVM 가비지 컬렉터를 강제 실행하여 메모리를 정리합니다")
    public ResponseEntity<Map<String, Object>> cleanupJVMMemory() {
        Map<String, Object> result = memoryService.cleanupJVMMemory();
        return ResponseEntity.ok(result);
    }

    /**
     * LAM 서비스 메모리 정리
     */
    @PostMapping("/cleanup/lam")
    @Operation(summary = "LAM 서비스 메모리 정리", description = "LAM 서비스의 메모리와 모델 캐시를 정리합니다")
    public ResponseEntity<Map<String, Object>> cleanupLAMMemory() {
        Map<String, Object> result = memoryService.cleanupLAMMemory();
        return ResponseEntity.ok(result);
    }

    /**
     * 전체 시스템 메모리 정리
     */
    @PostMapping("/cleanup/system")
    @Operation(summary = "전체 시스템 메모리 정리", description = "JVM과 LAM 서비스의 메모리를 모두 정리합니다")
    public ResponseEntity<Map<String, Object>> cleanupSystemMemory() {
        Map<String, Object> result = memoryService.cleanupSystemMemory();
        return ResponseEntity.ok(result);
    }

    /**
     * 메모리 임계값 체크 및 자동 정리
     */
    @PostMapping("/check-threshold")
    @Operation(summary = "메모리 임계값 체크", description = "메모리 사용량이 임계값을 초과하는지 확인하고 필요시 자동 정리를 실행합니다")
    public ResponseEntity<Map<String, Object>> checkMemoryThreshold() {
        boolean cleanupExecuted = memoryService.checkMemoryThresholdAndCleanup();
        
        Map<String, Object> result = Map.of(
            "threshold_check_completed", true,
            "cleanup_executed", cleanupExecuted,
            "message", cleanupExecuted ? "임계값 초과로 자동 정리 실행됨" : "메모리 사용량 정상"
        );
        
        return ResponseEntity.ok(result);
    }

    /**
     * PDF 처리 메모리 최적화 설정 조회
     */
    @GetMapping("/pdf-optimization/{pageCount}")
    @Operation(summary = "PDF 처리 메모리 최적화 설정 조회", 
              description = "지정된 페이지 수에 대해 스트리밍 처리 사용 여부와 배치 크기를 조회합니다")
    public ResponseEntity<Map<String, Object>> getPDFOptimizationSettings(
            @Parameter(description = "PDF 페이지 수", example = "100")
            @PathVariable int pageCount) {
        
        boolean useStreaming = memoryService.shouldUsePDFStreaming(pageCount);
        int batchSize = memoryService.getPDFBatchSize();
        
        Map<String, Object> settings = Map.of(
            "page_count", pageCount,
            "use_streaming", useStreaming,
            "batch_size", batchSize,
            "processing_mode", useStreaming ? "streaming" : "full_load",
            "estimated_batches", useStreaming ? (int) Math.ceil((double) pageCount / batchSize) : 1
        );
        
        return ResponseEntity.ok(settings);
    }

    /**
     * 통합 CIM 사용 여부 조회
     */
    @GetMapping("/cim-integration")
    @Operation(summary = "통합 CIM 사용 설정 조회", description = "통합 CIM 기능 사용 여부를 조회합니다")
    public ResponseEntity<Map<String, Object>> getCIMIntegrationSettings() {
        boolean unifiedCIMEnabled = memoryService.isUnifiedCIMEnabled();
        
        Map<String, Object> settings = Map.of(
            "unified_cim_enabled", unifiedCIMEnabled,
            "integration_mode", unifiedCIMEnabled ? "unified" : "separate",
            "description", unifiedCIMEnabled ? 
                "기본+구조화 분석을 한 번에 수행" : 
                "기본 분석과 구조화 분석을 별도로 수행"
        );
        
        return ResponseEntity.ok(settings);
    }

    /**
     * 메모리 사용량 히스토리 조회 (간단 버전)
     */
    @GetMapping("/status")
    @Operation(summary = "메모리 상태 요약", description = "현재 메모리 상태를 간단히 요약하여 제공합니다")
    public ResponseEntity<Map<String, Object>> getMemoryStatus() {
        Map<String, Object> jvmMemory = memoryService.getJVMMemoryUsage();
        Map<String, Object> lamMemory = memoryService.getLAMMemoryUsage();
        
        String jvmStatus = (String) jvmMemory.get("status");
        boolean lamHealthy = (Boolean) lamMemory.getOrDefault("success", false);
        
        String overallStatus;
        if ("critical".equals(jvmStatus)) {
            overallStatus = "critical";
        } else if ("warning".equals(jvmStatus) || !lamHealthy) {
            overallStatus = "warning";
        } else {
            overallStatus = "healthy";
        }
        
        Map<String, Object> status = Map.of(
            "overall_status", overallStatus,
            "jvm_status", jvmStatus,
            "lam_service_healthy", lamHealthy,
            "jvm_total_used_mb", jvmMemory.get("total_used_mb"),
            "recommendations", getRecommendations(overallStatus)
        );
        
        return ResponseEntity.ok(status);
    }

    /**
     * 메모리 상태에 따른 권장사항 제공
     */
    private String getRecommendations(String status) {
        return switch (status) {
            case "critical" -> "즉시 메모리 정리 실행 권장. 대용량 PDF 처리 시 스트리밍 모드 사용.";
            case "warning" -> "메모리 사용량이 높습니다. 정기적인 메모리 정리 권장.";
            case "healthy" -> "메모리 사용량이 정상 범위입니다.";
            default -> "메모리 상태를 확인할 수 없습니다.";
        };
    }
}