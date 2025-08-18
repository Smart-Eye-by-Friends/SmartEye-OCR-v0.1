package com.smarteye.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ProgressTrackingService {
    
    private final Map<String, TaskProgress> progressMap = new ConcurrentHashMap<>();
    
    public void startTask(String taskId, String taskType) {
        TaskProgress progress = TaskProgress.builder()
                .taskId(taskId)
                .taskType(taskType)
                .status("STARTED")
                .progress(0)
                .startTime(System.currentTimeMillis())
                .build();
        
        progressMap.put(taskId, progress);
        log.info("Task started: {}", taskId);
    }
    
    public void updateProgress(String taskId, int progress, String message) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setProgress(progress);
            taskProgress.setMessage(message);
            taskProgress.setLastUpdate(System.currentTimeMillis());
            log.debug("Progress updated for task {}: {}%", taskId, progress);
        }
    }
    
    public void completeTask(String taskId, Object result) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setStatus("COMPLETED");
            taskProgress.setProgress(100);
            taskProgress.setResult(result);
            taskProgress.setEndTime(System.currentTimeMillis());
            log.info("Task completed: {}", taskId);
        }
    }
    
    public void failTask(String taskId, String errorMessage) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setStatus("FAILED");
            taskProgress.setMessage(errorMessage);
            taskProgress.setEndTime(System.currentTimeMillis());
            log.error("Task failed: {} - {}", taskId, errorMessage);
        }
    }
    
    public Map<String, Object> getProgress(String taskId) {
        TaskProgress progress = progressMap.get(taskId);
        if (progress == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        
        return Map.of(
            "taskId", progress.getTaskId(),
            "status", progress.getStatus(),
            "progress", progress.getProgress(),
            "message", progress.getMessage() != null ? progress.getMessage() : "",
            "startTime", progress.getStartTime(),
            "lastUpdate", progress.getLastUpdate()
        );
    }
    
    public void cancelTask(String taskId) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setStatus("CANCELLED");
            taskProgress.setEndTime(System.currentTimeMillis());
            log.info("Task cancelled: {}", taskId);
        }
    }
    
    public void cleanupCompletedTasks() {
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24시간
        progressMap.entrySet().removeIf(entry -> {
            TaskProgress progress = entry.getValue();
            return progress.getEndTime() != null && progress.getEndTime() < cutoff;
        });
    }
    
    @lombok.Data
    @lombok.Builder
    private static class TaskProgress {
        private String taskId;
        private String taskType;
        private String status;
        private int progress;
        private String message;
        private long startTime;
        private Long lastUpdate;
        private Long endTime;
        private Object result;
    }
}
