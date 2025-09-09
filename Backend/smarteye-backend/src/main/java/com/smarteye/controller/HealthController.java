package com.smarteye.controller;

import com.smarteye.config.SmartEyeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    
    @GetMapping("/health/ping")
    public ResponseEntity<Map<String, String>> ping() {
        logger.debug("Ping check requested");
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "pong");
        response.put("message", "SmartEye Backend is alive");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
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
}