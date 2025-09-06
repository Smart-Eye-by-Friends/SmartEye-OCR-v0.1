package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smarteye.entity.CIMOutput;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "CIM 생성 상태 응답")
public class CIMStatusResponse {
    
    @JsonProperty("job_id")
    @Schema(description = "분석 작업 ID")
    private Long jobId;
    
    @JsonProperty("status")
    @Schema(description = "CIM 생성 상태", example = "COMPLETED")
    private String status;
    
    @JsonProperty("has_structured_data")
    @Schema(description = "구조화된 데이터 보유 여부")
    private Boolean hasStructuredData;
    
    @JsonProperty("total_questions")
    @Schema(description = "총 문제 수")
    private Integer totalQuestions;
    
    @JsonProperty("layout_type")
    @Schema(description = "레이아웃 타입")
    private String layoutType;
    
    @JsonProperty("created_at")
    @Schema(description = "생성 시간")
    private LocalDateTime createdAt;
    
    @JsonProperty("updated_at")
    @Schema(description = "최종 업데이트 시간")
    private LocalDateTime updatedAt;
    
    @JsonProperty("has_structured_text")
    @Schema(description = "구조화된 텍스트 보유 여부")
    private Boolean hasStructuredText;
    
    @JsonProperty("has_question_structure")
    @Schema(description = "문제 구조 정보 보유 여부")
    private Boolean hasQuestionStructure;
    
    @JsonProperty("question_structures_count")
    @Schema(description = "문제 구조 엔티티 수")
    private Integer questionStructuresCount;
    
    @JsonProperty("ai_mappings_count")
    @Schema(description = "AI 매핑 수")
    private Integer aiMappingsCount;
    
    @JsonProperty("error_message")
    @Schema(description = "오류 메시지 (실패 시)")
    private String errorMessage;
    
    @JsonProperty("processing_time_ms")
    @Schema(description = "처리 시간 (밀리초)")
    private Long processingTimeMs;
    
    // Constructors
    public CIMStatusResponse() {}
    
    public CIMStatusResponse(Long jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private CIMStatusResponse response = new CIMStatusResponse();
        
        public Builder jobId(Long jobId) {
            response.jobId = jobId;
            return this;
        }
        
        public Builder status(String status) {
            response.status = status;
            return this;
        }
        
        public Builder hasStructuredData(Boolean hasStructuredData) {
            response.hasStructuredData = hasStructuredData;
            return this;
        }
        
        public Builder totalQuestions(Integer totalQuestions) {
            response.totalQuestions = totalQuestions;
            return this;
        }
        
        public Builder layoutType(String layoutType) {
            response.layoutType = layoutType;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            response.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            response.updatedAt = updatedAt;
            return this;
        }
        
        public Builder hasStructuredText(Boolean hasStructuredText) {
            response.hasStructuredText = hasStructuredText;
            return this;
        }
        
        public Builder hasQuestionStructure(Boolean hasQuestionStructure) {
            response.hasQuestionStructure = hasQuestionStructure;
            return this;
        }
        
        public Builder questionStructuresCount(Integer questionStructuresCount) {
            response.questionStructuresCount = questionStructuresCount;
            return this;
        }
        
        public Builder aiMappingsCount(Integer aiMappingsCount) {
            response.aiMappingsCount = aiMappingsCount;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            response.errorMessage = errorMessage;
            return this;
        }
        
        public Builder processingTimeMs(Long processingTimeMs) {
            response.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public CIMStatusResponse build() {
            return response;
        }
    }
    
    // Static factory method from CIMOutput
    public static CIMStatusResponse fromCIMOutput(CIMOutput cimOutput) {
        if (cimOutput == null) {
            return null;
        }
        
        return CIMStatusResponse.builder()
            .jobId(cimOutput.getAnalysisJob() != null ? cimOutput.getAnalysisJob().getId() : null)
            .status(cimOutput.getGenerationStatus() != null ? cimOutput.getGenerationStatus().name() : "UNKNOWN")
            .hasStructuredData(cimOutput.hasStructuredData())
            .totalQuestions(cimOutput.getTotalQuestions())
            .layoutType(cimOutput.getLayoutType())
            .createdAt(cimOutput.getCreatedAt())
            .updatedAt(cimOutput.getUpdatedAt())
            .hasStructuredText(cimOutput.hasStructuredText())
            .hasQuestionStructure(cimOutput.hasQuestionStructure())
            .questionStructuresCount(cimOutput.getQuestionStructures() != null ? cimOutput.getQuestionStructures().size() : 0)
            .aiMappingsCount(cimOutput.getAiQuestionMappings() != null ? cimOutput.getAiQuestionMappings().size() : 0)
            .errorMessage(cimOutput.getErrorMessage())
            .processingTimeMs(cimOutput.getProcessingTimeMs())
            .build();
    }
    
    // Getters and Setters
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Boolean getHasStructuredData() { return hasStructuredData; }
    public void setHasStructuredData(Boolean hasStructuredData) { this.hasStructuredData = hasStructuredData; }
    
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
    
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Boolean getHasStructuredText() { return hasStructuredText; }
    public void setHasStructuredText(Boolean hasStructuredText) { this.hasStructuredText = hasStructuredText; }
    
    public Boolean getHasQuestionStructure() { return hasQuestionStructure; }
    public void setHasQuestionStructure(Boolean hasQuestionStructure) { this.hasQuestionStructure = hasQuestionStructure; }
    
    public Integer getQuestionStructuresCount() { return questionStructuresCount; }
    public void setQuestionStructuresCount(Integer questionStructuresCount) { this.questionStructuresCount = questionStructuresCount; }
    
    public Integer getAiMappingsCount() { return aiMappingsCount; }
    public void setAiMappingsCount(Integer aiMappingsCount) { this.aiMappingsCount = aiMappingsCount; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    // Helper methods
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean isGenerating() {
        return status != null && (
            status.equals("GENERATING_JSON") ||
            status.equals("GENERATING_FORMATTED_TEXT") ||
            status.equals("GENERATING_WORD_DOCUMENT")
        );
    }
    
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    public boolean hasQuestions() {
        return totalQuestions != null && totalQuestions > 0;
    }
    
    public boolean hasAnyStructuredData() {
        return Boolean.TRUE.equals(hasStructuredData) || 
               Boolean.TRUE.equals(hasStructuredText) || 
               Boolean.TRUE.equals(hasQuestionStructure);
    }
    
    @Override
    public String toString() {
        return "CIMStatusResponse{" +
                "jobId=" + jobId +
                ", status='" + status + '\'' +
                ", hasStructuredData=" + hasStructuredData +
                ", totalQuestions=" + totalQuestions +
                ", layoutType='" + layoutType + '\'' +
                ", questionStructuresCount=" + questionStructuresCount +
                ", aiMappingsCount=" + aiMappingsCount +
                ", createdAt=" + createdAt +
                '}';
    }
}