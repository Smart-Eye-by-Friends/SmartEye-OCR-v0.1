package com.smarteye.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "processing_logs")
@EntityListeners(AuditingEntityListener.class)
public class ProcessingLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "step", nullable = false, length = 100)
    private String step;
    
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private LogLevel level = LogLevel.INFO;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Column(name = "memory_usage_mb")
    private Long memoryUsageMb;
    
    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData; // JSON format for extra data
    
    @Column(name = "exception_trace", columnDefinition = "TEXT")
    private String exceptionTrace;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }
    
    // Constructors
    public ProcessingLog() {}
    
    public ProcessingLog(String step, String message) {
        this.step = step;
        this.message = message;
    }
    
    public ProcessingLog(String step, String message, LogLevel level) {
        this.step = step;
        this.message = message;
        this.level = level;
    }
    
    public ProcessingLog(String step, String message, LogLevel level, AnalysisJob analysisJob) {
        this.step = step;
        this.message = message;
        this.level = level;
        this.analysisJob = analysisJob;
    }
    
    // Static factory methods for common log types
    public static ProcessingLog info(String step, String message) {
        return new ProcessingLog(step, message, LogLevel.INFO);
    }
    
    public static ProcessingLog warn(String step, String message) {
        return new ProcessingLog(step, message, LogLevel.WARN);
    }
    
    public static ProcessingLog error(String step, String message) {
        return new ProcessingLog(step, message, LogLevel.ERROR);
    }
    
    public static ProcessingLog debug(String step, String message) {
        return new ProcessingLog(step, message, LogLevel.DEBUG);
    }
    
    public static ProcessingLog trace(String step, String message) {
        return new ProcessingLog(step, message, LogLevel.TRACE);
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LogLevel getLevel() { return level; }
    public void setLevel(LogLevel level) { this.level = level; }
    
    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    
    public Long getMemoryUsageMb() { return memoryUsageMb; }
    public void setMemoryUsageMb(Long memoryUsageMb) { this.memoryUsageMb = memoryUsageMb; }
    
    public String getAdditionalData() { return additionalData; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    
    public String getExceptionTrace() { return exceptionTrace; }
    public void setExceptionTrace(String exceptionTrace) { this.exceptionTrace = exceptionTrace; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public AnalysisJob getAnalysisJob() { return analysisJob; }
    public void setAnalysisJob(AnalysisJob analysisJob) { this.analysisJob = analysisJob; }
    
    // Helper methods
    public boolean isErrorLevel() {
        return level == LogLevel.ERROR || level == LogLevel.FATAL;
    }
    
    public boolean isWarnLevel() {
        return level == LogLevel.WARN;
    }
    
    public boolean isInfoLevel() {
        return level == LogLevel.INFO;
    }
    
    public boolean isDebugLevel() {
        return level == LogLevel.DEBUG || level == LogLevel.TRACE;
    }
    
    public void recordExecutionTime(long startTimeMs) {
        this.executionTimeMs = System.currentTimeMillis() - startTimeMs;
    }
    
    public void recordMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        this.memoryUsageMb = memoryUsage;
    }
    
    public void setException(Exception exception) {
        if (exception != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(exception.getClass().getSimpleName()).append(": ");
            sb.append(exception.getMessage()).append("\n");
            
            StackTraceElement[] stackTrace = exception.getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 10); i++) { // Limit to 10 lines
                sb.append("\t").append(stackTrace[i].toString()).append("\n");
            }
            
            if (stackTrace.length > 10) {
                sb.append("\t... ").append(stackTrace.length - 10).append(" more lines");
            }
            
            this.exceptionTrace = sb.toString();
        }
    }
    
    @Override
    public String toString() {
        return "ProcessingLog{" +
                "id=" + id +
                ", step='" + step + '\'' +
                ", level=" + level +
                ", message='" + (message.length() > 100 ? message.substring(0, 100) + "..." : message) + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", createdAt=" + createdAt +
                '}';
    }
}