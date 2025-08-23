package com.smarteye.controller;

import com.smarteye.dto.lam.*;
import com.smarteye.service.LAMService;
import com.smarteye.service.TSPMService;
import com.smarteye.service.DocumentAnalysisService;
import com.smarteye.service.PerformanceMonitoringService;
import com.smarteye.model.entity.AnalysisJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 3단계 최적화된 통합 분석 컨트롤러
 * TSPM (Java 네이티브) + LAM (마이크로서비스) 통합 + 성능 모니터링
 */
@RestController
@RequestMapping("/api/v2/analysis")
@RequiredArgsConstructor
@Slf4j
public class IntegratedAnalysisController {
    
    private final TSPMService tspmService;
    private final LAMService lamService;
    private final DocumentAnalysisService documentAnalysisService;
    private final PerformanceMonitoringService performanceService;
    
    /**
     * 3단계 최적화된 통합 분석: LAM 마이크로서비스 + TSPM Java 네이티브 + 성능 모니터링
     */
    @PostMapping("/integrated")
    public ResponseEntity<Map<String, Object>> integratedAnalysis(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "analysisType", defaultValue = "both") String analysisType,
            @RequestParam(value = "confidenceThreshold", defaultValue = "0.5") double confidenceThreshold) {
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("3단계 최적화된 통합 분석 시작 - 파일: {}, 분석 타입: {}", 
                    file.getOriginalFilename(), analysisType);
            
            // 1. LAM 분석 (마이크로서비스) + 성능 모니터링
            AnalysisJob lamJob = null;
            if ("lam".equals(analysisType) || "both".equals(analysisType)) {
                long lamStartTime = System.currentTimeMillis();
                try {
                    lamJob = lamService.analyzeLayoutWithMicroservice(file);
                    long lamProcessingTime = System.currentTimeMillis() - lamStartTime;
                    
                    // LAM 성능 기록
                    performanceService.recordServicePerformance("LAM_MICROSERVICE", lamProcessingTime, true);
                    
                    result.put("lam", Map.of(
                        "success", true,
                        "jobId", lamJob.getJobId(),
                        "status", lamJob.getStatus(),
                        "message", "LAM 마이크로서비스 분석 완료",
                        "processingTimeMs", lamProcessingTime
                    ));
                } catch (Exception e) {
                    long lamProcessingTime = System.currentTimeMillis() - lamStartTime;
                    
                    // LAM 실패 성능 기록
                    performanceService.recordServicePerformance("LAM_MICROSERVICE", lamProcessingTime, false);
                    
                    log.error("LAM 마이크로서비스 분석 실패: {}", e.getMessage());
                    result.put("lam", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "message", "LAM 마이크로서비스 분석 실패",
                        "processingTimeMs", lamProcessingTime
                    ));
                }
            }
            
            // 2. TSPM 분석 (Java 네이티브) + 성능 모니터링
            AnalysisJob tspmJob = null;
            if ("tspm".equals(analysisType) || "both".equals(analysisType)) {
                long tspmStartTime = System.currentTimeMillis();
                try {
                    tspmJob = tspmService.performTSPMAnalysis(file);
                    long tspmProcessingTime = System.currentTimeMillis() - tspmStartTime;
                    
                    // TSPM 성능 기록
                    performanceService.recordServicePerformance("TSPM_JAVA_NATIVE", tspmProcessingTime, true);
                    
                    result.put("tspm", Map.of(
                        "success", true,
                        "jobId", tspmJob.getJobId(),
                        "status", tspmJob.getStatus(),
                        "message", "TSPM Java 네이티브 분석 완료",
                        "processingTimeMs", tspmProcessingTime
                    ));
                } catch (Exception e) {
                    long tspmProcessingTime = System.currentTimeMillis() - tspmStartTime;
                    
                    // TSPM 실패 성능 기록
                    performanceService.recordServicePerformance("TSPM_JAVA_NATIVE", tspmProcessingTime, false);
                    
                    log.error("TSPM Java 네이티브 분석 실패: {}", e.getMessage());
                    result.put("tspm", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "message", "TSPM Java 네이티브 분석 실패",
                        "processingTimeMs", tspmProcessingTime
                    ));
                }
            }
            
            // 전체 결과 처리
            boolean overallSuccess = true;
            if (result.containsKey("lam")) {
                overallSuccess &= (Boolean) ((Map<?, ?>) result.get("lam")).get("success");
            }
            if (result.containsKey("tspm")) {
                overallSuccess &= (Boolean) ((Map<?, ?>) result.get("tspm")).get("success");
            }
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            
            // 전체 성능 기록
            performanceService.recordServicePerformance("INTEGRATED_ANALYSIS", totalProcessingTime, overallSuccess);
            
            result.put("success", overallSuccess);
            result.put("analysisType", analysisType);
            result.put("filename", file.getOriginalFilename());
            result.put("timestamp", System.currentTimeMillis());
            result.put("totalProcessingTimeMs", totalProcessingTime);
            result.put("message", overallSuccess ? 
                "2단계 통합 분석이 성공적으로 완료되었습니다" : 
                "2단계 통합 분석 중 일부 오류가 발생했습니다");
            
            log.info("2단계 통합 분석 완료 - 전체 성공: {}", overallSuccess);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("2단계 통합 분석 실패: {}", e.getMessage(), e);
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "2단계 통합 분석 중 예상치 못한 오류가 발생했습니다");
            
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 시스템 상태 확인 (2단계)
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // LAM 마이크로서비스 상태 확인
            Map<String, Object> lamStatus = new HashMap<>();
            try {
                LAMHealthResponse lamHealth = lamService.checkMicroserviceHealth();
                lamStatus.put("available", true);
                lamStatus.put("status", lamHealth.getStatus());
                lamStatus.put("message", lamHealth.getMessage());
            } catch (Exception e) {
                lamStatus.put("available", false);
                lamStatus.put("error", e.getMessage());
            }
            
            // TSPM Java 네이티브 상태 확인
            Map<String, Object> tspmStatus = new HashMap<>();
            try {
                boolean tspmAvailable = tspmService.isJavaTSPMAvailable();
                tspmStatus.put("available", tspmAvailable);
                tspmStatus.put("message", tspmAvailable ? 
                    "TSPM Java 네이티브 서비스 정상" : 
                    "TSPM Java 네이티브 서비스 오류");
            } catch (Exception e) {
                tspmStatus.put("available", false);
                tspmStatus.put("error", e.getMessage());
            }
            
            status.put("lam", lamStatus);
            status.put("tspm", tspmStatus);
            status.put("phase", "2단계");
            status.put("description", "LAM 마이크로서비스 + TSPM Java 네이티브");
            status.put("timestamp", System.currentTimeMillis());
            
            boolean overallHealthy = (Boolean) lamStatus.get("available") && 
                                   (Boolean) tspmStatus.get("available");
            status.put("healthy", overallHealthy);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("시스템 상태 확인 실패: {}", e.getMessage());
            
            status.put("healthy", false);
            status.put("error", e.getMessage());
            status.put("message", "시스템 상태 확인 중 오류 발생");
            
            return ResponseEntity.badRequest().body(status);
        }
    }
    
    /**
     * 분석 성능 비교 (1단계 vs 2단계)
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareAnalysisMethods(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> comparison = new HashMap<>();
        
        try {
            log.info("분석 성능 비교 시작 - 파일: {}", file.getOriginalFilename());
            
            // 1단계 방식 (Python 스크립트)
            Map<String, Object> phase1Result = new HashMap<>();
            long phase1Start = System.currentTimeMillis();
            try {
                var phase1Job = lamService.analyzeLayout(file);
                long phase1Duration = System.currentTimeMillis() - phase1Start;
                
                phase1Result.put("success", true);
                phase1Result.put("duration", phase1Duration);
                phase1Result.put("method", "Python 스크립트 직접 호출");
                phase1Result.put("jobId", phase1Job.getJobId());
            } catch (Exception e) {
                long phase1Duration = System.currentTimeMillis() - phase1Start;
                phase1Result.put("success", false);
                phase1Result.put("duration", phase1Duration);
                phase1Result.put("error", e.getMessage());
            }
            
            // 2단계 방식 (마이크로서비스)
            Map<String, Object> phase2Result = new HashMap<>();
            long phase2Start = System.currentTimeMillis();
            try {
                var phase2Job = lamService.analyzeLayoutWithMicroservice(file);
                long phase2Duration = System.currentTimeMillis() - phase2Start;
                
                phase2Result.put("success", true);
                phase2Result.put("duration", phase2Duration);
                phase2Result.put("method", "LAM 마이크로서비스");
                phase2Result.put("jobId", phase2Job.getJobId());
            } catch (Exception e) {
                long phase2Duration = System.currentTimeMillis() - phase2Start;
                phase2Result.put("success", false);
                phase2Result.put("duration", phase2Duration);
                phase2Result.put("error", e.getMessage());
            }
            
            comparison.put("phase1", phase1Result);
            comparison.put("phase2", phase2Result);
            comparison.put("filename", file.getOriginalFilename());
            comparison.put("timestamp", System.currentTimeMillis());
            
            // 성능 분석
            if ((Boolean) phase1Result.get("success") && (Boolean) phase2Result.get("success")) {
                long phase1Duration = (Long) phase1Result.get("duration");
                long phase2Duration = (Long) phase2Result.get("duration");
                double improvement = ((double) (phase1Duration - phase2Duration) / phase1Duration) * 100;
                
                comparison.put("performanceImprovement", improvement);
                comparison.put("recommendation", improvement > 0 ? 
                    "2단계 마이크로서비스 방식이 더 효율적입니다" : 
                    "1단계 Python 스크립트 방식이 더 빠릅니다");
            }
            
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            log.error("분석 성능 비교 실패: {}", e.getMessage(), e);
            
            comparison.put("success", false);
            comparison.put("error", e.getMessage());
            comparison.put("message", "분석 성능 비교 중 오류 발생");
            
            return ResponseEntity.badRequest().body(comparison);
        }
    }
    
    /**
     * LAM 전용 분석 (마이크로서비스)
     */
    @PostMapping("/lam/analyze")
    public ResponseEntity<Map<String, Object>> analyzeLAMOnly(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confidenceThreshold", defaultValue = "0.5") double confidenceThreshold,
            @RequestParam(value = "maxBlocks", defaultValue = "100") int maxBlocks,
            @RequestParam(value = "detectText", defaultValue = "true") boolean detectText,
            @RequestParam(value = "detectTables", defaultValue = "true") boolean detectTables,
            @RequestParam(value = "detectFigures", defaultValue = "true") boolean detectFigures) {
        
        try {
            log.info("LAM 전용 분석 요청 - 파일: {}", file.getOriginalFilename());
            
            // 분석 옵션 설정
            LAMAnalysisOptions options = new LAMAnalysisOptions();
            options.setConfidenceThreshold(confidenceThreshold);
            options.setMaxBlocks(maxBlocks);
            options.setDetectText(detectText);
            options.setDetectTables(detectTables);
            options.setDetectFigures(detectFigures);
            
            // 마이크로서비스를 사용한 분석 실행
            var analysisJob = lamService.analyzeLayoutWithMicroservice(file);
            
            // 결과 생성
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jobId", analysisJob.getJobId());
            result.put("status", analysisJob.getStatus());
            result.put("progress", analysisJob.getProgress());
            result.put("message", "LAM 전용 분석이 시작되었습니다");
            result.put("analysisType", "lam-only");
            result.put("filename", file.getOriginalFilename());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("LAM 전용 분석 실패: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("message", "LAM 전용 분석 중 오류가 발생했습니다");
            errorResult.put("analysisType", "lam-only");
            
            return ResponseEntity.badRequest().body(errorResult);
        }
    }
    
    /**
     * TSPM 전용 분석 (Java 네이티브)
     */
    @PostMapping("/tspm/analyze")
    public ResponseEntity<Map<String, Object>> analyzeTSPMOnly(
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("TSPM 전용 분석 요청 - 파일: {}", file.getOriginalFilename());
            
            // TSPM 분석 실행
            AnalysisJob analysisJob = tspmService.performTSPMAnalysis(file);
            
            // 결과 생성
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jobId", analysisJob.getJobId());
            result.put("status", analysisJob.getStatus());
            result.put("progress", analysisJob.getProgress());
            result.put("message", "TSPM 전용 분석이 시작되었습니다");
            result.put("analysisType", "tsmp-only");
            result.put("filename", file.getOriginalFilename());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("TSPM 전용 분석 실패: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("message", "TSPM 전용 분석 중 오류가 발생했습니다");
            errorResult.put("analysisType", "tspm-only");
            
            return ResponseEntity.badRequest().body(errorResult);
        }
    }
    
    /**
     * LAM 마이크로서비스 상태 확인
     */
    @GetMapping("/lam/health")
    public ResponseEntity<LAMHealthResponse> checkLAMHealth() {
        try {
            LAMHealthResponse health = lamService.checkMicroserviceHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("LAM 마이크로서비스 상태 확인 실패: {}", e.getMessage());
            
            LAMHealthResponse errorResponse = new LAMHealthResponse();
            errorResponse.setStatus("error");
            errorResponse.setMessage("마이크로서비스 연결 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * LAM 모델 정보 조회
     */
    @GetMapping("/lam/model/info")
    public ResponseEntity<LAMModelInfo> getLAMModelInfo() {
        try {
            LAMModelInfo modelInfo = lamService.getMicroserviceModelInfo();
            return ResponseEntity.ok(modelInfo);
        } catch (Exception e) {
            log.error("LAM 모델 정보 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * LAM 마이크로서비스 연결 테스트
     */
    @GetMapping("/lam/test")
    public ResponseEntity<Map<String, Object>> testLAMConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 상태 확인
            LAMHealthResponse health = lamService.checkMicroserviceHealth();
            
            result.put("success", true);
            result.put("microserviceStatus", health.getStatus());
            result.put("message", "LAM 마이크로서비스 연결 성공");
            result.put("timestamp", System.currentTimeMillis());
            
            // 모델 정보도 함께 반환
            try {
                LAMModelInfo modelInfo = lamService.getMicroserviceModelInfo();
                result.put("modelInfo", modelInfo);
            } catch (Exception modelException) {
                result.put("modelInfoError", modelException.getMessage());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("LAM 마이크로서비스 연결 테스트 실패: {}", e.getMessage());
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "LAM 마이크로서비스 연결 실패");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(result);
        }
    }
}
