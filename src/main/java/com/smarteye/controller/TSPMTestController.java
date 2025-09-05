package com.smarteye.controller;

import com.smarteye.service.JavaTSPMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Java TSPM 서비스 테스트를 위한 컨트롤러 (확장됨)
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TSPMTestController {
    
    private final JavaTSPMService javaTSPMService;
    
    /**
     * Java TSPM 서비스 상태 확인
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        try {
            Map<String, Object> status = javaTSPMService.getServiceStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("서비스 상태 확인 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "서비스 상태 확인 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 테스트용 OCR 처리
     */
    @PostMapping("/ocr")
    public ResponseEntity<Map<String, Object>> testOCR(@RequestParam("imagePath") String imagePath) {
        try {
            log.info("테스트 OCR 요청 - 이미지: {}", imagePath);
            Map<String, Object> result = javaTSPMService.performOCR(imagePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("OCR 테스트 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "OCR 처리 실패: " + e.getMessage()));
        }
    }
    
    /**
     * Java 네이티브 TSPM 서비스 테스트 (GET)
     */
    @GetMapping("/tspm-java")
    public ResponseEntity<Map<String, Object>> testJavaTSPM() {
        log.info("Java 네이티브 TSPM 서비스 테스트 시작");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Tesseract 가용성 확인
            boolean tesseractAvailable = javaTSPMService.isTesseractAvailable();
            result.put("tesseractAvailable", tesseractAvailable);
            
            if (tesseractAvailable) {
                // 간단한 OCR 신뢰도 테스트
                String testText = "Hello World 안녕하세요 123";
                double confidence = javaTSPMService.calculateOCRConfidence(testText);
                result.put("ocrConfidenceTest", confidence);
                result.put("testText", testText);
            }
            
            result.put("status", "success");
            result.put("message", "Java TSPM 서비스가 정상적으로 작동합니다");
            
        } catch (Exception e) {
            log.error("Java TSPM 서비스 테스트 중 오류 발생", e);
            result.put("status", "error");
            result.put("message", "테스트 중 오류: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /**
     * TSPM 서비스 상태 확인 (기존 메서드 확장)
     */
    @GetMapping("/tspm/status")
    public ResponseEntity<Map<String, Object>> getTSPMStatus() {
        try {
            Map<String, Object> status = javaTSPMService.getServiceStatus();
            
            // 추가 정보
            status.put("tesseractAvailable", javaTSPMService.isTesseractAvailable());
            status.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("서비스 상태 확인 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "서비스 상태 확인 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 시스템 정보 확인
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        log.info("시스템 정보 확인 요청");
        
        Map<String, Object> info = new HashMap<>();
        
        // JVM 정보
        Runtime runtime = Runtime.getRuntime();
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        
        // 메모리 정보
        Map<String, Object> memory = new HashMap<>();
        memory.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memory.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memory.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memory.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        info.put("memory", memory);
        
        // 프로세서 정보
        info.put("availableProcessors", runtime.availableProcessors());
        
        info.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * 테스트용 Vision API 처리
     */
    @PostMapping("/vision")
    public ResponseEntity<Map<String, Object>> testVisionAPI(@RequestParam("imagePath") String imagePath) {
        try {
            log.info("테스트 Vision API 요청 - 이미지: {}", imagePath);
            Map<String, Object> result = javaTSPMService.performVisionAnalysis(imagePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Vision API 테스트 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Vision API 처리 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 테스트용 통합 분석
     */
    @PostMapping("/combined")
    public ResponseEntity<Map<String, Object>> testCombinedAnalysis(@RequestParam("imagePath") String imagePath) {
        try {
            log.info("테스트 통합 분석 요청 - 이미지: {}", imagePath);
            Map<String, Object> result = javaTSPMService.performCombinedAnalysis(imagePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("통합 분석 테스트 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "통합 분석 실패: " + e.getMessage()));
        }
    }
}
