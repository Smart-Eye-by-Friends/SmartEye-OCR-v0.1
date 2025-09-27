package com.smarteye.presentation.controller;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.application.analysis.AnalysisJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 작업 상태 조회 컨트롤러
 */
@RestController
@RequestMapping("/api/jobs")
@Validated
public class JobStatusController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobStatusController.class);
    
    @Autowired
    private AnalysisJobService analysisJobService;
    
    /**
     * 작업 상태 조회
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        logger.info("작업 상태 조회 - Job ID: {}", jobId);
        
        Optional<AnalysisJob> jobOpt = analysisJobService.getAnalysisJobByJobId(jobId);
        if (jobOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "작업을 찾을 수 없습니다");
            
            return ResponseEntity.notFound().build();
        }
        
        AnalysisJob job = jobOpt.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus().name());
        response.put("progress", job.getProgressPercentage());
        response.put("originalFilename", job.getOriginalFilename());
        response.put("createdAt", job.getCreatedAt());
        response.put("completedAt", job.getCompletedAt());
        
        if (job.getErrorMessage() != null) {
            response.put("errorMessage", job.getErrorMessage());
        }
        
        // 상세 정보
        Map<String, Object> details = new HashMap<>();
        details.put("fileSize", job.getFileSize());
        details.put("fileType", job.getFileType());
        details.put("modelChoice", job.getModelChoice());
        details.put("userId", job.getUser().getId());
        
        response.put("details", details);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 상태의 모든 작업 조회 (관리용)
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Map<String, Object>> getJobsByStatus(@PathVariable String status) {
        logger.info("상태별 작업 조회 - Status: {}", status);
        
        try {
            AnalysisJob.JobStatus jobStatus = AnalysisJob.JobStatus.valueOf(status.toUpperCase());
            List<AnalysisJob> jobs = analysisJobService.getAnalysisJobsByStatus(jobStatus);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", status.toUpperCase());
            response.put("count", jobs.size());
            response.put("jobs", jobs.stream().map(job -> Map.of(
                "jobId", job.getJobId(),
                "originalFilename", job.getOriginalFilename(),
                "progress", job.getProgressPercentage(),
                "createdAt", job.getCreatedAt(),
                "userId", job.getUser().getId()
            )).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "잘못된 상태 값입니다: " + status);
            
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("상태별 작업 조회 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "작업 조회 중 오류가 발생했습니다: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 작업 진행률 업데이트 (내부 API)
     */
    @PutMapping("/{jobId}/progress")
    public ResponseEntity<Map<String, Object>> updateJobProgress(
            @PathVariable String jobId,
            @RequestParam("progress") int progress,
            @RequestParam(value = "message", required = false) String message) {
        
        logger.info("작업 진행률 업데이트 - Job ID: {}, Progress: {}%", jobId, progress);
        
        try {
            analysisJobService.updateJobProgress(jobId, progress, message);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "진행률이 업데이트되었습니다");
            response.put("jobId", jobId);
            response.put("progress", progress);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("작업 진행률 업데이트 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 작업 상태 강제 업데이트 (관리용)
     */
    @PutMapping("/{jobId}/status")
    public ResponseEntity<Map<String, Object>> updateJobStatus(
            @PathVariable String jobId,
            @RequestParam("status") String status,
            @RequestParam(value = "errorMessage", required = false) String errorMessage) {
        
        logger.info("작업 상태 업데이트 - Job ID: {}, Status: {}", jobId, status);
        
        try {
            AnalysisJob.JobStatus jobStatus = AnalysisJob.JobStatus.valueOf(status.toUpperCase());
            AnalysisJob updatedJob = analysisJobService.updateJobStatus(jobId, jobStatus, null, errorMessage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "작업 상태가 업데이트되었습니다");
            response.put("jobId", jobId);
            response.put("status", updatedJob.getStatus().name());
            response.put("completedAt", updatedJob.getCompletedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "잘못된 상태 값입니다: " + status);
            
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("작업 상태 업데이트 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 작업 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getJobStats() {
        logger.info("작업 통계 조회");
        
        try {
            Map<String, Long> statusCounts = new HashMap<>();
            
            for (AnalysisJob.JobStatus status : AnalysisJob.JobStatus.values()) {
                List<AnalysisJob> jobs = analysisJobService.getAnalysisJobsByStatus(status);
                statusCounts.put(status.name(), (long) jobs.size());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statusCounts", statusCounts);
            response.put("totalJobs", statusCounts.values().stream().mapToLong(Long::longValue).sum());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("작업 통계 조회 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "통계 조회 중 오류가 발생했습니다: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}