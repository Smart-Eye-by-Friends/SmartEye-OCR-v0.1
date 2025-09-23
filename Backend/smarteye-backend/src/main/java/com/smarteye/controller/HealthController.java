package com.smarteye.controller;

import com.smarteye.config.SmartEyeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    @Autowired
    private SmartEyeProperties smartEyeProperties;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.debug("Health check requested");
        
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("application", "SmartEye Backend");
        healthInfo.put("version", "0.0.1-SNAPSHOT");
        
        // System information
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("availableProcessors", runtime.availableProcessors());
        systemInfo.put("freeMemory", runtime.freeMemory());
        systemInfo.put("totalMemory", runtime.totalMemory());
        systemInfo.put("maxMemory", runtime.maxMemory());
        
        healthInfo.put("system", systemInfo);
        
        // Configuration check
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("uploadDirectory", smartEyeProperties.getUpload().getDirectory());
        configInfo.put("tempDirectory", smartEyeProperties.getUpload().getTempDirectory());
        configInfo.put("maxConcurrentJobs", smartEyeProperties.getProcessing().getMaxConcurrentJobs());
        configInfo.put("lamServiceUrl", smartEyeProperties.getApi().getLamService().getBaseUrl());
        
        healthInfo.put("configuration", configInfo);
        
        return ResponseEntity.ok(healthInfo);
    }
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        logger.debug("Application info requested");
        
        Map<String, Object> info = new HashMap<>();
        info.put("application", Map.of(
            "name", "SmartEye Backend",
            "description", "AI-powered OCR and document analysis system for visually impaired students",
            "version", "0.0.1-SNAPSHOT"
        ));
        
        info.put("features", Map.of(
            "ocrSupport", true,
            "layoutAnalysis", true,
            "aiDescription", true,
            "documentGeneration", true,
            "multipleImageProcessing", true,
            "pdfSupport", true
        ));
        
        info.put("supportedFormats", Map.of(
            "images", new String[]{"jpg", "jpeg", "png", "bmp", "tiff", "tif", "webp"},
            "documents", new String[]{"pdf"}
        ));
        
        return ResponseEntity.ok(info);
    }
    
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        logger.debug("Readiness check requested");
        
        Map<String, Object> readiness = new HashMap<>();
        boolean isReady = true;
        
        // Check upload directory
        try {
            java.io.File uploadDir = new java.io.File(smartEyeProperties.getUpload().getDirectory());
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            readiness.put("uploadDirectory", "OK");
        } catch (Exception e) {
            logger.warn("Upload directory check failed: {}", e.getMessage());
            readiness.put("uploadDirectory", "FAIL");
            isReady = false;
        }
        
        // Check temp directory
        try {
            java.io.File tempDir = new java.io.File(smartEyeProperties.getUpload().getTempDirectory());
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            readiness.put("tempDirectory", "OK");
        } catch (Exception e) {
            logger.warn("Temp directory check failed: {}", e.getMessage());
            readiness.put("tempDirectory", "FAIL");
            isReady = false;
        }
        
        readiness.put("status", isReady ? "READY" : "NOT_READY");
        readiness.put("timestamp", LocalDateTime.now());
        
        if (isReady) {
            return ResponseEntity.ok(readiness);
        } else {
            return ResponseEntity.status(503).body(readiness);
        }
    }

    /**
     * CORS 테스트를 위한 엔드포인트
     * 프론트엔드에서 CORS 설정이 올바르게 작동하는지 확인
     */
    @GetMapping("/cors-test")
    public ResponseEntity<Map<String, Object>> corsTest(HttpServletRequest request) {
        logger.info("CORS 테스트 요청 - Origin: {}, Method: {}",
                   request.getHeader("Origin"), request.getMethod());

        Map<String, Object> corsTestResult = new HashMap<>();
        corsTestResult.put("status", "success");
        corsTestResult.put("message", "CORS 설정이 올바르게 작동합니다");
        corsTestResult.put("timestamp", LocalDateTime.now());
        corsTestResult.put("requestInfo", Map.of(
            "origin", request.getHeader("Origin"),
            "method", request.getMethod(),
            "userAgent", request.getHeader("User-Agent"),
            "remoteAddr", request.getRemoteAddr()
        ));

        return ResponseEntity.ok(corsTestResult);
    }

    /**
     * POST 요청 CORS 테스트
     */
    @PostMapping("/cors-test")
    public ResponseEntity<Map<String, Object>> corsTestPost(
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request) {

        logger.info("CORS POST 테스트 요청 - Origin: {}", request.getHeader("Origin"));

        Map<String, Object> corsTestResult = new HashMap<>();
        corsTestResult.put("status", "success");
        corsTestResult.put("message", "POST 요청 CORS 설정이 올바르게 작동합니다");
        corsTestResult.put("timestamp", LocalDateTime.now());
        corsTestResult.put("receivedData", requestBody);
        corsTestResult.put("requestInfo", Map.of(
            "origin", request.getHeader("Origin"),
            "method", request.getMethod(),
            "contentType", request.getContentType()
        ));

        return ResponseEntity.ok(corsTestResult);
    }
}