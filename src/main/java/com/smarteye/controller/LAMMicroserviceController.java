package com.smarteye.controller;

import com.smarteye.dto.lam.*;
import com.smarteye.service.LAMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * LAM 마이크로서비스 컨트롤러 (2단계)
 */
@RestController
@RequestMapping("/api/v2/lam")
@RequiredArgsConstructor
@Slf4j
public class LAMMicroserviceController {
    
    private final LAMService lamService;
    
    /**
     * 마이크로서비스를 사용한 레이아웃 분석
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeMicroservice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confidenceThreshold", defaultValue = "0.5") double confidenceThreshold,
            @RequestParam(value = "maxBlocks", defaultValue = "100") int maxBlocks,
            @RequestParam(value = "detectText", defaultValue = "true") boolean detectText,
            @RequestParam(value = "detectTables", defaultValue = "true") boolean detectTables,
            @RequestParam(value = "detectFigures", defaultValue = "true") boolean detectFigures) {
        
        try {
            log.info("마이크로서비스 레이아웃 분석 요청 - 파일: {}", file.getOriginalFilename());
            
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
            result.put("message", "마이크로서비스를 사용한 레이아웃 분석이 시작되었습니다");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("마이크로서비스 레이아웃 분석 실패: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("message", "마이크로서비스 레이아웃 분석 중 오류가 발생했습니다");
            
            return ResponseEntity.badRequest().body(errorResult);
        }
    }
    
    /**
     * LAM 마이크로서비스 상태 확인
     */
    @GetMapping("/health")
    public ResponseEntity<LAMHealthResponse> checkMicroserviceHealth() {
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
    @GetMapping("/model/info")
    public ResponseEntity<LAMModelInfo> getModelInfo() {
        try {
            LAMModelInfo modelInfo = lamService.getMicroserviceModelInfo();
            return ResponseEntity.ok(modelInfo);
        } catch (Exception e) {
            log.error("LAM 모델 정보 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 마이크로서비스 연결 테스트
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testMicroserviceConnection() {
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
            log.error("마이크로서비스 연결 테스트 실패: {}", e.getMessage());
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "LAM 마이크로서비스 연결 실패");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(result);
        }
    }
}
