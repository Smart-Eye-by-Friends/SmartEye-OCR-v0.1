package com.smarteye.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import com.smarteye.model.dto.AnalysisRequest;
import com.smarteye.model.response.AnalysisResponse;
import com.smarteye.service.AnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResponse> analyzeDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "analysisType", defaultValue = "FULL") String analysisType,
            @RequestParam(value = "language", defaultValue = "kor+eng") String language) {
        
        log.info("Document analysis request received: filename={}, size={}, type={}", 
                file.getOriginalFilename(), file.getSize(), analysisType);

        try {
            AnalysisRequest request = AnalysisRequest.builder()
                    .file(file)
                    .analysisType(analysisType)
                    .language(language)
                    .build();

            AnalysisResponse response = analysisService.analyzeDocument(request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during document analysis", e);
            return ResponseEntity.internalServerError()
                    .body(AnalysisResponse.error("분석 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> analyzeDocumentAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "analysisType", defaultValue = "FULL") String analysisType,
            @RequestParam(value = "language", defaultValue = "kor+eng") String language) {
        
        log.info("Async document analysis request received: filename={}", file.getOriginalFilename());

        try {
            AnalysisRequest request = AnalysisRequest.builder()
                    .file(file)
                    .analysisType(analysisType)
                    .language(language)
                    .build();

            CompletableFuture<AnalysisResponse> futureResponse = analysisService.analyzeDocumentAsync(request);
            
            // 비동기 처리 ID 반환 (실제로는 더 복잡한 작업 관리 시스템 필요)
            String taskId = java.util.UUID.randomUUID().toString();
            
            return ResponseEntity.accepted()
                    .body("분석이 시작되었습니다. 작업 ID: " + taskId);
            
        } catch (Exception e) {
            log.error("Error starting async document analysis", e);
            return ResponseEntity.internalServerError()
                    .body("비동기 분석 시작 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SmartEye Analysis Service is running");
    }
}
