package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "구조화된 CIM 분석 응답")
public class StructuredCIMResponse {
    
    @JsonProperty("success")
    @Schema(description = "성공 여부", example = "true")
    private boolean success;
    
    @JsonProperty("structured_result")
    @Schema(description = "구조화된 분석 결과 (Python과 동일한 형식)")
    private Map<String, Object> structuredResult;
    
    @JsonProperty("structured_text")
    @Schema(description = "읽기 쉬운 형태의 구조화된 텍스트")
    private String structuredText;
    
    @JsonProperty("stats")
    @Schema(description = "분석 통계 정보")
    private CIMStats stats;
    
    @JsonProperty("timestamp")
    @Schema(description = "응답 생성 시각")
    private Long timestamp;
    
    @JsonProperty("analysis_type")
    @Schema(description = "분석 타입", example = "structured")
    private String analysisType;
    
    @JsonProperty("is_multi_page")
    @Schema(description = "다중 페이지 여부")
    private Boolean isMultiPage;
    
    @JsonProperty("page_count")
    @Schema(description = "페이지 수")
    private Integer pageCount;
    
    @JsonProperty("job_id")
    @Schema(description = "분석 작업 ID")
    private Long jobId;
    
    @JsonProperty("processing_time_ms")
    @Schema(description = "처리 시간 (밀리초)")
    private Long processingTimeMs;
    
    @JsonProperty("error_message")
    @Schema(description = "오류 메시지 (실패 시)")
    private String errorMessage;
    
    // Constructors
    public StructuredCIMResponse() {}
    
    public StructuredCIMResponse(boolean success) {
        this.success = success;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private StructuredCIMResponse response = new StructuredCIMResponse();
        
        public Builder success(boolean success) {
            response.success = success;
            return this;
        }
        
        public Builder structuredResult(Map<String, Object> structuredResult) {
            response.structuredResult = structuredResult;
            return this;
        }
        
        public Builder structuredText(String structuredText) {
            response.structuredText = structuredText;
            return this;
        }
        
        public Builder stats(CIMStats stats) {
            response.stats = stats;
            return this;
        }
        
        public Builder timestamp(Long timestamp) {
            response.timestamp = timestamp;
            return this;
        }
        
        public Builder analysisType(String analysisType) {
            response.analysisType = analysisType;
            return this;
        }
        
        public Builder isMultiPage(Boolean isMultiPage) {
            response.isMultiPage = isMultiPage;
            return this;
        }
        
        public Builder pageCount(Integer pageCount) {
            response.pageCount = pageCount;
            return this;
        }
        
        public Builder jobId(Long jobId) {
            response.jobId = jobId;
            return this;
        }
        
        public Builder processingTimeMs(Long processingTimeMs) {
            response.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            response.errorMessage = errorMessage;
            return this;
        }
        
        public StructuredCIMResponse build() {
            if (response.timestamp == null) {
                response.timestamp = System.currentTimeMillis();
            }
            return response;
        }
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public Map<String, Object> getStructuredResult() { return structuredResult; }
    public void setStructuredResult(Map<String, Object> structuredResult) { this.structuredResult = structuredResult; }
    
    public String getStructuredText() { return structuredText; }
    public void setStructuredText(String structuredText) { this.structuredText = structuredText; }
    
    public CIMStats getStats() { return stats; }
    public void setStats(CIMStats stats) { this.stats = stats; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    
    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
    
    public Boolean getIsMultiPage() { return isMultiPage; }
    public void setIsMultiPage(Boolean isMultiPage) { this.isMultiPage = isMultiPage; }
    
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    // Helper methods
    public boolean hasError() {
        return !success && errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    public boolean hasStructuredResult() {
        return structuredResult != null && !structuredResult.isEmpty();
    }
    
    public boolean hasStats() {
        return stats != null;
    }
    
    @Override
    public String toString() {
        return "StructuredCIMResponse{" +
                "success=" + success +
                ", analysisType='" + analysisType + '\'' +
                ", jobId=" + jobId +
                ", isMultiPage=" + isMultiPage +
                ", pageCount=" + pageCount +
                ", processingTimeMs=" + processingTimeMs +
                ", timestamp=" + timestamp +
                '}';
    }
    
    // Inner class for statistics
    @Schema(description = "CIM 분석 통계 정보")
    public static class CIMStats {
        
        @JsonProperty("total_questions")
        @Schema(description = "총 문제 수")
        private Integer totalQuestions;
        
        @JsonProperty("layout_type")
        @Schema(description = "레이아웃 타입")
        private String layoutType;
        
        @JsonProperty("total_elements")
        @Schema(description = "총 요소 수")
        private Integer totalElements;
        
        @JsonProperty("total_text_blocks")
        @Schema(description = "총 텍스트 블록 수")
        private Integer totalTextBlocks;
        
        @JsonProperty("questions_with_choices")
        @Schema(description = "선택지가 있는 문제 수")
        private Integer questionsWithChoices;
        
        @JsonProperty("questions_with_images")
        @Schema(description = "이미지가 있는 문제 수")
        private Integer questionsWithImages;
        
        @JsonProperty("questions_with_tables")
        @Schema(description = "테이블이 있는 문제 수")
        private Integer questionsWithTables;
        
        @JsonProperty("ai_mappings_count")
        @Schema(description = "AI 매핑 수")
        private Integer aiMappingsCount;
        
        @JsonProperty("high_confidence_mappings")
        @Schema(description = "높은 신뢰도 매핑 수")
        private Integer highConfidenceMappings;
        
        @JsonProperty("sections_count")
        @Schema(description = "섹션 수")
        private Integer sectionsCount;
        
        // Builder pattern for CIMStats
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private CIMStats stats = new CIMStats();
            
            public Builder totalQuestions(Integer totalQuestions) { stats.totalQuestions = totalQuestions; return this; }
            public Builder layoutType(String layoutType) { stats.layoutType = layoutType; return this; }
            public Builder totalElements(Integer totalElements) { stats.totalElements = totalElements; return this; }
            public Builder totalTextBlocks(Integer totalTextBlocks) { stats.totalTextBlocks = totalTextBlocks; return this; }
            public Builder questionsWithChoices(Integer questionsWithChoices) { stats.questionsWithChoices = questionsWithChoices; return this; }
            public Builder questionsWithImages(Integer questionsWithImages) { stats.questionsWithImages = questionsWithImages; return this; }
            public Builder questionsWithTables(Integer questionsWithTables) { stats.questionsWithTables = questionsWithTables; return this; }
            public Builder aiMappingsCount(Integer aiMappingsCount) { stats.aiMappingsCount = aiMappingsCount; return this; }
            public Builder highConfidenceMappings(Integer highConfidenceMappings) { stats.highConfidenceMappings = highConfidenceMappings; return this; }
            public Builder sectionsCount(Integer sectionsCount) { stats.sectionsCount = sectionsCount; return this; }
            
            public CIMStats build() {
                return stats;
            }
        }
        
        // Getters and Setters
        public Integer getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
        
        public String getLayoutType() { return layoutType; }
        public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
        
        public Integer getTotalElements() { return totalElements; }
        public void setTotalElements(Integer totalElements) { this.totalElements = totalElements; }
        
        public Integer getTotalTextBlocks() { return totalTextBlocks; }
        public void setTotalTextBlocks(Integer totalTextBlocks) { this.totalTextBlocks = totalTextBlocks; }
        
        public Integer getQuestionsWithChoices() { return questionsWithChoices; }
        public void setQuestionsWithChoices(Integer questionsWithChoices) { this.questionsWithChoices = questionsWithChoices; }
        
        public Integer getQuestionsWithImages() { return questionsWithImages; }
        public void setQuestionsWithImages(Integer questionsWithImages) { this.questionsWithImages = questionsWithImages; }
        
        public Integer getQuestionsWithTables() { return questionsWithTables; }
        public void setQuestionsWithTables(Integer questionsWithTables) { this.questionsWithTables = questionsWithTables; }
        
        public Integer getAiMappingsCount() { return aiMappingsCount; }
        public void setAiMappingsCount(Integer aiMappingsCount) { this.aiMappingsCount = aiMappingsCount; }
        
        public Integer getHighConfidenceMappings() { return highConfidenceMappings; }
        public void setHighConfidenceMappings(Integer highConfidenceMappings) { this.highConfidenceMappings = highConfidenceMappings; }
        
        public Integer getSectionsCount() { return sectionsCount; }
        public void setSectionsCount(Integer sectionsCount) { this.sectionsCount = sectionsCount; }
    }
}