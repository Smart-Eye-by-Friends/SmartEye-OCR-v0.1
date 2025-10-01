package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "책 전체 분석 결과 응답")
public class BookAnalysisResponse {
    
    @Schema(description = "분석 성공 여부", example = "true")
    private boolean success;
    
    @Schema(description = "책 정보")
    private BookDto book;
    
    @Schema(description = "분석된 작업 수", example = "10")
    private Integer analyzedJobsCount;
    
    @Schema(description = "실패한 작업 수", example = "0")
    private Integer failedJobsCount;
    
    @Schema(description = "전체 처리 시간 (초)", example = "120.5")
    private Double totalProcessingTimeSeconds;
    
    @Schema(description = "사용된 모델", example = "SmartEyeSsen")
    private String modelUsed;
    
    @Schema(description = "분석 시작 시간")
    private LocalDateTime analysisStartTime;
    
    @Schema(description = "분석 완료 시간")
    private LocalDateTime analysisEndTime;
    
    @Schema(description = "개별 작업 결과들")
    private List<AnalysisJobResultDto> jobResults;
    
    @Schema(description = "통합 분석 결과 (전체 책 요약)")
    private BookSummaryDto summary;
    
    @Schema(description = "오류 메시지들")
    private List<String> errors;
    
    @Schema(description = "경고 메시지들")
    private List<String> warnings;
    
    @Schema(description = "추가 메타데이터")
    private Map<String, Object> metadata;
    
    // Constructors
    public BookAnalysisResponse() {}
    
    public BookAnalysisResponse(boolean success, BookDto book) {
        this.success = success;
        this.book = book;
        this.analysisStartTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public BookDto getBook() { return book; }
    public void setBook(BookDto book) { this.book = book; }
    
    public Integer getAnalyzedJobsCount() { return analyzedJobsCount; }
    public void setAnalyzedJobsCount(Integer analyzedJobsCount) { this.analyzedJobsCount = analyzedJobsCount; }
    
    public Integer getFailedJobsCount() { return failedJobsCount; }
    public void setFailedJobsCount(Integer failedJobsCount) { this.failedJobsCount = failedJobsCount; }
    
    public Double getTotalProcessingTimeSeconds() { return totalProcessingTimeSeconds; }
    public void setTotalProcessingTimeSeconds(Double totalProcessingTimeSeconds) { this.totalProcessingTimeSeconds = totalProcessingTimeSeconds; }
    
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    
    public LocalDateTime getAnalysisStartTime() { return analysisStartTime; }
    public void setAnalysisStartTime(LocalDateTime analysisStartTime) { this.analysisStartTime = analysisStartTime; }
    
    public LocalDateTime getAnalysisEndTime() { return analysisEndTime; }
    public void setAnalysisEndTime(LocalDateTime analysisEndTime) { this.analysisEndTime = analysisEndTime; }
    
    public List<AnalysisJobResultDto> getJobResults() { return jobResults; }
    public void setJobResults(List<AnalysisJobResultDto> jobResults) { this.jobResults = jobResults; }
    
    public BookSummaryDto getSummary() { return summary; }
    public void setSummary(BookSummaryDto summary) { this.summary = summary; }
    
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    // Helper methods
    public void markCompleted() {
        this.analysisEndTime = LocalDateTime.now();
        if (this.analysisStartTime != null) {
            long seconds = java.time.Duration.between(analysisStartTime, analysisEndTime).getSeconds();
            this.totalProcessingTimeSeconds = (double) seconds;
        }
    }
    
    public void addError(String error) {
        if (errors == null) {
            errors = new java.util.ArrayList<>();
        }
        errors.add(error);
    }
    
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new java.util.ArrayList<>();
        }
        warnings.add(warning);
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    @Override
    public String toString() {
        return "BookAnalysisResponse{" +
                "success=" + success +
                ", bookId=" + (book != null ? book.getId() : null) +
                ", analyzedJobsCount=" + analyzedJobsCount +
                ", failedJobsCount=" + failedJobsCount +
                ", totalProcessingTimeSeconds=" + totalProcessingTimeSeconds +
                ", modelUsed='" + modelUsed + '\'' +
                ", hasErrors=" + hasErrors() +
                ", hasWarnings=" + hasWarnings() +
                '}';
    }
}