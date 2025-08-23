package com.smarteye.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import com.smarteye.dto.response.ApiResponse;
import com.smarteye.service.ProgressTrackingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProgressController {

    private final ProgressTrackingService progressTrackingService;

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProgress(@PathVariable String taskId) {
        log.info("Progress request for task: {}", taskId);
        
        try {
            Map<String, Object> progress = progressTrackingService.getProgress(taskId);
            return ResponseEntity.ok(ApiResponse.success(progress, "진행 상황을 조회했습니다."));
        } catch (Exception e) {
            log.error("Error getting progress for task: {}", taskId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("진행 상황 조회 중 오류가 발생했습니다.", "PROGRESS_ERROR", 500));
        }
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<String>> cancelTask(@PathVariable String taskId) {
        log.info("Cancel request for task: {}", taskId);
        
        try {
            progressTrackingService.cancelTask(taskId);
            return ResponseEntity.ok(ApiResponse.success("작업이 취소되었습니다."));
        } catch (Exception e) {
            log.error("Error canceling task: {}", taskId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("작업 취소 중 오류가 발생했습니다.", "CANCEL_ERROR", 500));
        }
    }
}
