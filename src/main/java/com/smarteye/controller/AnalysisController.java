package com.smarteye.controller;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.LayoutBlock;
import com.smarteye.model.entity.TextBlock;
import com.smarteye.service.LAMService;
import com.smarteye.service.TSPMService;
import com.smarteye.service.AnalysisJobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 문서 분석 통합 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
public class AnalysisController {
    
    private final LAMService lamService;
    private final TSPMService tspmService;
    private final AnalysisJobService analysisJobService;
    
    /**
     * 전체 분석 파이프라인 실행 (LAM + TSPM)
     */
    @PostMapping("/complete")
    public ResponseEntity<?> analyzeComplete(@RequestParam("file") MultipartFile file) {
        log.info("전체 분석 요청 - 파일: {}", file.getOriginalFilename());
        
        try {
            // 1. LAM 분석 시작
            AnalysisJob job = lamService.analyzeLayout(file);
            String jobId = job.getJobId();
            
            // 2. 비동기로 TSPM 처리
            CompletableFuture.runAsync(() -> {
                try {
                    // LAM 완료 대기
                    while (!"COMPLETED".equals(job.getStatus()) && !"FAILED".equals(job.getStatus())) {
                        Thread.sleep(1000);
                        AnalysisJob currentJob = analysisJobService.getAnalysisJob(jobId);
                        if (currentJob != null) {
                            if ("COMPLETED".equals(currentJob.getStatus())) {
                                break;
                            }
                            if ("FAILED".equals(currentJob.getStatus())) {
                                log.error("LAM 분석 실패로 TSPM 처리를 중단합니다: {}", jobId);
                                return;
                            }
                        }
                    }
                    
                    // TSPM 실행
                    log.info("TSPM 처리 시작: {}", jobId);
                    tspmService.processTextAndSemantic(jobId);
                    log.info("전체 분석 완료: {}", jobId);
                    
                } catch (Exception e) {
                    log.error("TSPM 처리 실패: {}", jobId, e);
                }
            });
            
        return ResponseEntity.ok(Map.of(
            "jobId", job.getJobId(),
            "status", job.getStatus(),
            "progress", job.getProgress(),
            "filename", job.getOriginalFilename(),
            "createdAt", job.getCreatedAt(),
            "updatedAt", job.getUpdatedAt(),
            "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : ""
        ));        } catch (Exception e) {
            log.error("분석 요청 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "분석 요청 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * LAM 분석만 실행
     */
    @PostMapping("/layout")
    public ResponseEntity<?> analyzeLayout(@RequestParam("file") MultipartFile file) {
        log.info("LAM 분석 요청 - 파일: {}", file.getOriginalFilename());
        
        try {
            AnalysisJob job = lamService.analyzeLayout(file);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "LAM 분석이 시작되었습니다",
                "jobId", job.getJobId(),
                "progress", job.getProgress()
            ));
            
        } catch (Exception e) {
            log.error("LAM 분석 요청 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "LAM 분석 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * TSPM 분석 실행 (LAM 완료 후)
     */
    @PostMapping("/text/{jobId}")
    public ResponseEntity<?> analyzeText(@PathVariable String jobId) {
        log.info("TSPM 분석 요청 - JobId: {}", jobId);
        
        try {
            AnalysisJob job = tspmService.processTextAndSemantic(jobId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "TSPM 분석이 완료되었습니다",
                "jobId", jobId,
                "progress", job.getProgress()
            ));
            
        } catch (Exception e) {
            log.error("TSPM 분석 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "TSPM 분석 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 분석 작업 상태 조회
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getAnalysisStatus(@PathVariable String jobId) {
        try {
            AnalysisJob job = analysisJobService.getAnalysisJob(jobId);
            
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "status", job.getStatus(),
                "progress", job.getProgress(),
                "filename", job.getOriginalFilename(),
                "createdAt", job.getCreatedAt(),
                "updatedAt", job.getUpdatedAt(),
                "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : ""
            ));
            
        } catch (Exception e) {
            log.error("상태 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "상태 조회 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 레이아웃 블록 결과 조회
     */
    @GetMapping("/layout/{jobId}")
    public ResponseEntity<?> getLayoutBlocks(@PathVariable String jobId) {
        try {
            List<LayoutBlock> blocks = lamService.getLayoutBlocks(jobId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "jobId", jobId,
                "blocks", blocks,
                "count", blocks.size()
            ));
            
        } catch (Exception e) {
            log.error("레이아웃 블록 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "레이아웃 블록 조회 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 텍스트 블록 결과 조회
     */
    @GetMapping("/text/{jobId}")
    public ResponseEntity<?> getTextBlocks(@PathVariable String jobId) {
        try {
            List<TextBlock> blocks = tspmService.getTextBlocks(jobId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "jobId", jobId,
                "blocks", blocks,
                "count", blocks.size()
            ));
            
        } catch (Exception e) {
            log.error("텍스트 블록 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "텍스트 블록 조회 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 전체 분석 결과 조회
     */
    @GetMapping("/results/{jobId}")
    public ResponseEntity<?> getAnalysisResults(@PathVariable String jobId) {
        try {
            AnalysisJob job = analysisJobService.getAnalysisJob(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            List<LayoutBlock> layoutBlocks = lamService.getLayoutBlocks(jobId);
            List<TextBlock> textBlocks = tspmService.getTextBlocks(jobId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "job", Map.of(
                    "jobId", job.getJobId(),
                    "status", job.getStatus(),
                    "progress", job.getProgress(),
                    "filename", job.getOriginalFilename(),
                    "createdAt", job.getCreatedAt(),
                    "updatedAt", job.getUpdatedAt()
                ),
                "layoutBlocks", layoutBlocks,
                "textBlocks", textBlocks,
                "summary", Map.of(
                    "layoutBlockCount", layoutBlocks.size(),
                    "textBlockCount", textBlocks.size(),
                    "hasErrors", job.getErrorMessage() != null
                )
            ));
            
        } catch (Exception e) {
            log.error("분석 결과 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "분석 결과 조회 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 모든 분석 작업 목록 조회
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> getAllAnalysisJobs() {
        try {
            List<AnalysisJob> jobs = analysisJobService.getAllAnalysisJobs();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "jobs", jobs,
                "count", jobs.size()
            ));
            
        } catch (Exception e) {
            log.error("분석 작업 목록 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "분석 작업 목록 조회 실패: " + e.getMessage()
            ));
        }
    }
}
