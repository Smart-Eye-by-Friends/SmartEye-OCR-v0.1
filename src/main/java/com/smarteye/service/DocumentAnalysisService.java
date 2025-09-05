package com.smarteye.service;

import com.smarteye.dto.lam.LAMAnalysisOptions;
import com.smarteye.model.entity.AnalysisJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 통합 문서 분석 서비스
 * LAM, TSPM, CIM 서비스들을 통합적으로 관리하는 중앙 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisService {
    
    private final LAMService lamService;
    private final TSPMService tspmService;
    private final CIMService cimService;
    private final PerformanceMonitoringService performanceService;
    
    /**
     * 완전한 문서 분석 파이프라인 수행
     * LAM (레이아웃 분석) -> TSPM (텍스트 처리) -> CIM (내용 통합)
     */
    public Map<String, Object> performCompleteAnalysis(MultipartFile file, String analysisType, double confidenceThreshold) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("완전한 문서 분석 시작 - 파일: {}, 분석 타입: {}", file.getOriginalFilename(), analysisType);
            
            // 1. LAM 분석 (마이크로서비스)
            AnalysisJob lamJob = null;
            if ("lam".equals(analysisType) || "both".equals(analysisType)) {
                try {
                    long lamStartTime = System.currentTimeMillis();
                    lamJob = lamService.analyzeLayoutWithMicroservice(file);
                    long lamProcessingTime = System.currentTimeMillis() - lamStartTime;
                    
                    Map<String, Object> lamResult = new HashMap<>();
                    lamResult.put("success", true);
                    lamResult.put("jobId", lamJob.getJobId());
                    lamResult.put("status", lamJob.getStatus());
                    lamResult.put("progress", lamJob.getProgress());
                    lamResult.put("processingTimeMs", lamProcessingTime);
                    
                    result.put("lam", lamResult);
                    performanceService.recordServicePerformance("LAM_MICROSERVICE", lamProcessingTime, true);
                    
                    log.info("LAM 분석 완료 - Job ID: {}", lamJob.getJobId());
                    
                } catch (Exception e) {
                    log.error("LAM 분석 실패: {}", e.getMessage(), e);
                    Map<String, Object> lamError = new HashMap<>();
                    lamError.put("success", false);
                    lamError.put("error", e.getMessage());
                    result.put("lam", lamError);
                    performanceService.recordServicePerformance("LAM_MICROSERVICE", 0, false);
                }
            }
            
            // 2. TSPM 분석 (Java 네이티브)
            AnalysisJob tspmJob = null;
            if ("tspm".equals(analysisType) || "both".equals(analysisType)) {
                try {
                    long tspmStartTime = System.currentTimeMillis();
                    tspmJob = tspmService.performTSPMAnalysis(file);
                    long tspmProcessingTime = System.currentTimeMillis() - tspmStartTime;
                    
                    Map<String, Object> tspmResult = new HashMap<>();
                    tspmResult.put("success", true);
                    tspmResult.put("jobId", tspmJob.getJobId());
                    tspmResult.put("status", tspmJob.getStatus());
                    tspmResult.put("progress", tspmJob.getProgress());
                    tspmResult.put("processingTimeMs", tspmProcessingTime);
                    
                    result.put("tspm", tspmResult);
                    performanceService.recordServicePerformance("TSPM_JAVA_NATIVE", tspmProcessingTime, true);
                    
                    log.info("TSPM 분석 완료 - Job ID: {}", tspmJob.getJobId());
                    
                } catch (Exception e) {
                    log.error("TSPM 분석 실패: {}", e.getMessage(), e);
                    Map<String, Object> tspmError = new HashMap<>();
                    tspmError.put("success", false);
                    tspmError.put("error", e.getMessage());
                    result.put("tspm", tspmError);
                    performanceService.recordServicePerformance("TSPM_JAVA_NATIVE", 0, false);
                }
            }
            
            // 전체 결과 처리
            boolean overallSuccess = true;
            if (result.containsKey("lam")) {
                Map<String, Object> lamResult = (Map<String, Object>) result.get("lam");
                overallSuccess &= (Boolean) lamResult.get("success");
            }
            if (result.containsKey("tspm")) {
                Map<String, Object> tspmResult = (Map<String, Object>) result.get("tspm");
                overallSuccess &= (Boolean) tspmResult.get("success");
            }
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            
            result.put("success", overallSuccess);
            result.put("analysisType", analysisType);
            result.put("filename", file.getOriginalFilename());
            result.put("timestamp", System.currentTimeMillis());
            result.put("totalProcessingTimeMs", totalProcessingTime);
            result.put("message", overallSuccess ? 
                "통합 분석이 성공적으로 완료되었습니다" : 
                "통합 분석 중 일부 오류가 발생했습니다");
            
            // 전체 성능 기록
            performanceService.recordServicePerformance("COMPLETE_DOCUMENT_ANALYSIS", totalProcessingTime, overallSuccess);
            
            log.info("완전한 문서 분석 완료 - 전체 성공: {}, 소요시간: {}ms", overallSuccess, totalProcessingTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("문서 분석 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "문서 분석 중 시스템 오류가 발생했습니다");
            result.put("timestamp", System.currentTimeMillis());
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            performanceService.recordServicePerformance("COMPLETE_DOCUMENT_ANALYSIS", totalProcessingTime, false);
            
            return result;
        }
    }
    
    /**
     * LAM 전용 분석 수행
     */
    public Map<String, Object> performLAMAnalysis(MultipartFile file, LAMAnalysisOptions options) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("LAM 전용 분석 시작 - 파일: {}", file.getOriginalFilename());
            
            AnalysisJob analysisJob = lamService.analyzeLayoutWithMicroservice(file);
            long processingTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jobId", analysisJob.getJobId());
            result.put("status", analysisJob.getStatus());
            result.put("progress", analysisJob.getProgress());
            result.put("message", "LAM 전용 분석이 완료되었습니다");
            result.put("processingTimeMs", processingTime);
            result.put("timestamp", System.currentTimeMillis());
            
            performanceService.recordServicePerformance("LAM_ONLY_ANALYSIS", processingTime, true);
            
            return result;
            
        } catch (Exception e) {
            log.error("LAM 전용 분석 실패: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "LAM 전용 분석 중 오류가 발생했습니다");
            result.put("timestamp", System.currentTimeMillis());
            
            long processingTime = System.currentTimeMillis() - startTime;
            performanceService.recordServicePerformance("LAM_ONLY_ANALYSIS", processingTime, false);
            
            return result;
        }
    }
    
    /**
     * TSPM 전용 분석 수행
     */
    public Map<String, Object> performTSPMAnalysis(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("TSPM 전용 분석 시작 - 파일: {}", file.getOriginalFilename());
            
            AnalysisJob analysisJob = tspmService.performTSPMAnalysis(file);
            long processingTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jobId", analysisJob.getJobId());
            result.put("status", analysisJob.getStatus());
            result.put("progress", analysisJob.getProgress());
            result.put("message", "TSPM 전용 분석이 완료되었습니다");
            result.put("processingTimeMs", processingTime);
            result.put("timestamp", System.currentTimeMillis());
            
            performanceService.recordServicePerformance("TSPM_ONLY_ANALYSIS", processingTime, true);
            
            return result;
            
        } catch (Exception e) {
            log.error("TSPM 전용 분석 실패: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "TSPM 전용 분석 중 오류가 발생했습니다");
            result.put("timestamp", System.currentTimeMillis());
            
            long processingTime = System.currentTimeMillis() - startTime;
            performanceService.recordServicePerformance("TSPM_ONLY_ANALYSIS", processingTime, false);
            
            return result;
        }
    }
    
    /**
     * 시스템 상태 확인
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // LAM 마이크로서비스 상태 확인
            Map<String, Object> lamStatus = new HashMap<>();
            try {
                var lamHealth = lamService.checkMicroserviceHealth();
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
            status.put("phase", "통합 분석 시스템");
            status.put("description", "LAM 마이크로서비스 + TSPM Java 네이티브");
            status.put("timestamp", System.currentTimeMillis());
            
            boolean overallHealthy = (Boolean) lamStatus.get("available") && 
                                   (Boolean) tspmStatus.get("available");
            status.put("healthy", overallHealthy);
            
            return status;
            
        } catch (Exception e) {
            log.error("시스템 상태 확인 실패: {}", e.getMessage());
            
            status.put("healthy", false);
            status.put("error", e.getMessage());
            status.put("message", "시스템 상태 확인 중 오류가 발생했습니다");
            status.put("timestamp", System.currentTimeMillis());
            
            return status;
        }
    }
}
