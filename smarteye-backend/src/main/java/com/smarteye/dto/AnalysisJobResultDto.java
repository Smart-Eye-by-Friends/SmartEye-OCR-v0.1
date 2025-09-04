package com.smarteye.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "개별 분석 작업 결과 DTO")
public class AnalysisJobResultDto {
    
    @Schema(description = "작업 ID", example = "1")
    private Long jobId;
    
    @Schema(description = "작업 UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String jobUuid;
    
    @Schema(description = "원본 파일명", example = "math_worksheet_page1.pdf")
    private String originalFilename;
    
    @Schema(description = "책 내 순서", example = "1")
    private Integer sequenceInBook;
    
    @Schema(description = "분석 성공 여부", example = "true")
    private boolean success;
    
    @Schema(description = "처리 시간 (초)", example = "12.5")
    private Double processingTimeSeconds;
    
    @Schema(description = "분석된 페이지 수", example = "5")
    private Integer analyzedPagesCount;
    
    @Schema(description = "감지된 레이아웃 요소 수", example = "25")
    private Integer totalLayoutElements;
    
    @Schema(description = "추출된 텍스트 블록 수", example = "15")
    private Integer totalTextBlocks;
    
    @Schema(description = "추출된 전체 텍스트 길이", example = "1500")
    private Integer totalTextLength;
    
    @Schema(description = "레이아웃 요소 타입별 개수")
    private Map<String, Integer> layoutElementCounts;
    
    @Schema(description = "텍스트 블록 타입별 개수")
    private Map<String, Integer> textBlockCounts;
    
    @Schema(description = "페이지별 결과 요약")
    private List<PageAnalysisResultDto> pageResults;
    
    @Schema(description = "오류 메시지")
    private String errorMessage;
    
    @Schema(description = "경고 메시지들")
    private List<String> warnings;
    
    @Schema(description = "품질 점수 (0-100)", example = "92.5")
    private Double qualityScore;
    
    @Schema(description = "분석 시작 시간")
    private LocalDateTime analysisStartTime;
    
    @Schema(description = "분석 완료 시간")
    private LocalDateTime analysisEndTime;
    
    @Schema(description = "사용된 모델", example = "SmartEyeSsen")
    private String modelUsed;
    
    @Schema(description = "AI 설명 생성 여부", example = "true")
    private Boolean aiDescriptionGenerated;
    
    @Schema(description = "추가 메타데이터")
    private Map<String, Object> metadata;
    
    // Constructors
    public AnalysisJobResultDto() {}
    
    public AnalysisJobResultDto(Long jobId, String jobUuid, String originalFilename, boolean success) {
        this.jobId = jobId;
        this.jobUuid = jobUuid;
        this.originalFilename = originalFilename;
        this.success = success;
        this.analysisStartTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    
    public String getJobUuid() { return jobUuid; }
    public void setJobUuid(String jobUuid) { this.jobUuid = jobUuid; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public Integer getSequenceInBook() { return sequenceInBook; }
    public void setSequenceInBook(Integer sequenceInBook) { this.sequenceInBook = sequenceInBook; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public Double getProcessingTimeSeconds() { return processingTimeSeconds; }
    public void setProcessingTimeSeconds(Double processingTimeSeconds) { this.processingTimeSeconds = processingTimeSeconds; }
    
    public Integer getAnalyzedPagesCount() { return analyzedPagesCount; }
    public void setAnalyzedPagesCount(Integer analyzedPagesCount) { this.analyzedPagesCount = analyzedPagesCount; }
    
    public Integer getTotalLayoutElements() { return totalLayoutElements; }
    public void setTotalLayoutElements(Integer totalLayoutElements) { this.totalLayoutElements = totalLayoutElements; }
    
    public Integer getTotalTextBlocks() { return totalTextBlocks; }
    public void setTotalTextBlocks(Integer totalTextBlocks) { this.totalTextBlocks = totalTextBlocks; }
    
    public Integer getTotalTextLength() { return totalTextLength; }
    public void setTotalTextLength(Integer totalTextLength) { this.totalTextLength = totalTextLength; }
    
    public Map<String, Integer> getLayoutElementCounts() { return layoutElementCounts; }
    public void setLayoutElementCounts(Map<String, Integer> layoutElementCounts) { this.layoutElementCounts = layoutElementCounts; }
    
    public Map<String, Integer> getTextBlockCounts() { return textBlockCounts; }
    public void setTextBlockCounts(Map<String, Integer> textBlockCounts) { this.textBlockCounts = textBlockCounts; }
    
    public List<PageAnalysisResultDto> getPageResults() { return pageResults; }
    public void setPageResults(List<PageAnalysisResultDto> pageResults) { this.pageResults = pageResults; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    
    public LocalDateTime getAnalysisStartTime() { return analysisStartTime; }
    public void setAnalysisStartTime(LocalDateTime analysisStartTime) { this.analysisStartTime = analysisStartTime; }
    
    public LocalDateTime getAnalysisEndTime() { return analysisEndTime; }
    public void setAnalysisEndTime(LocalDateTime analysisEndTime) { this.analysisEndTime = analysisEndTime; }
    
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    
    public Boolean getAiDescriptionGenerated() { return aiDescriptionGenerated; }
    public void setAiDescriptionGenerated(Boolean aiDescriptionGenerated) { this.aiDescriptionGenerated = aiDescriptionGenerated; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    // Helper methods
    public void markCompleted() {
        this.analysisEndTime = LocalDateTime.now();
        if (this.analysisStartTime != null) {
            long seconds = java.time.Duration.between(analysisStartTime, analysisEndTime).getSeconds();
            this.processingTimeSeconds = (double) seconds;
        }
    }
    
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new java.util.ArrayList<>();
        }
        warnings.add(warning);
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "AnalysisJobResultDto{" +
                "jobId=" + jobId +
                ", originalFilename='" + originalFilename + '\'' +
                ", sequenceInBook=" + sequenceInBook +
                ", success=" + success +
                ", processingTimeSeconds=" + processingTimeSeconds +
                ", analyzedPagesCount=" + analyzedPagesCount +
                ", totalLayoutElements=" + totalLayoutElements +
                ", totalTextBlocks=" + totalTextBlocks +
                ", qualityScore=" + qualityScore +
                '}';
    }
    
    // Nested DTO for page analysis results
    @Schema(description = "페이지 분석 결과")
    public static class PageAnalysisResultDto {
        
        @Schema(description = "페이지 번호", example = "1")
        private Integer pageNumber;
        
        @Schema(description = "분석 성공 여부", example = "true")
        private boolean success;
        
        @Schema(description = "처리 시간 (ms)", example = "2500")
        private Long processingTimeMs;
        
        @Schema(description = "레이아웃 요소 수", example = "5")
        private Integer layoutElementCount;
        
        @Schema(description = "텍스트 블록 수", example = "3")
        private Integer textBlockCount;
        
        @Schema(description = "추출된 텍스트 길이", example = "300")
        private Integer textLength;
        
        @Schema(description = "오류 메시지")
        private String errorMessage;
        
        @Schema(description = "품질 점수 (0-100)", example = "95.0")
        private Double qualityScore;
        
        public PageAnalysisResultDto() {}
        
        public PageAnalysisResultDto(Integer pageNumber, boolean success) {
            this.pageNumber = pageNumber;
            this.success = success;
        }
        
        // Getters and Setters
        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public Integer getLayoutElementCount() { return layoutElementCount; }
        public void setLayoutElementCount(Integer layoutElementCount) { this.layoutElementCount = layoutElementCount; }
        
        public Integer getTextBlockCount() { return textBlockCount; }
        public void setTextBlockCount(Integer textBlockCount) { this.textBlockCount = textBlockCount; }
        
        public Integer getTextLength() { return textLength; }
        public void setTextLength(Integer textLength) { this.textLength = textLength; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public Double getQualityScore() { return qualityScore; }
        public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
        
        @Override
        public String toString() {
            return "PageAnalysisResultDto{" +
                    "pageNumber=" + pageNumber +
                    ", success=" + success +
                    ", layoutElementCount=" + layoutElementCount +
                    ", textBlockCount=" + textBlockCount +
                    ", processingTimeMs=" + processingTimeMs +
                    '}';
        }
    }
}