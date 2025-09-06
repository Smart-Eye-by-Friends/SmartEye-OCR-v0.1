package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "다중 페이지 CIM 통합 요청")
public class IntegrateRequest {
    
    @JsonProperty("job_ids")
    @NotNull(message = "작업 ID 목록은 필수입니다")
    @NotEmpty(message = "작업 ID 목록이 비어있을 수 없습니다")
    @Size(min = 2, max = 50, message = "작업 ID는 2개 이상 50개 이하여야 합니다")
    @Schema(description = "통합할 분석 작업 ID 목록", example = "[1, 2, 3]")
    private List<Long> jobIds;
    
    @JsonProperty("integration_method")
    @Schema(description = "통합 방법", example = "sequential", allowableValues = {"sequential", "parallel", "smart"})
    private String integrationMethod = "sequential";
    
    @JsonProperty("preserve_page_order")
    @Schema(description = "페이지 순서 유지 여부", example = "true")
    private Boolean preservePageOrder = true;
    
    @JsonProperty("merge_duplicate_questions")
    @Schema(description = "중복 문제 병합 여부", example = "false")
    private Boolean mergeDuplicateQuestions = false;
    
    @JsonProperty("output_format")
    @Schema(description = "출력 형식", example = "combined", allowableValues = {"combined", "separated", "both"})
    private String outputFormat = "combined";
    
    @JsonProperty("include_page_metadata")
    @Schema(description = "페이지 메타데이터 포함 여부", example = "true")
    private Boolean includePageMetadata = true;
    
    @JsonProperty("quality_threshold")
    @Schema(description = "품질 임계값 (0.0-1.0)", example = "0.7")
    private Double qualityThreshold = 0.7;
    
    // Constructors
    public IntegrateRequest() {}
    
    public IntegrateRequest(List<Long> jobIds) {
        this.jobIds = jobIds;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private IntegrateRequest request = new IntegrateRequest();
        
        public Builder jobIds(List<Long> jobIds) {
            request.jobIds = jobIds;
            return this;
        }
        
        public Builder integrationMethod(String integrationMethod) {
            request.integrationMethod = integrationMethod;
            return this;
        }
        
        public Builder preservePageOrder(Boolean preservePageOrder) {
            request.preservePageOrder = preservePageOrder;
            return this;
        }
        
        public Builder mergeDuplicateQuestions(Boolean mergeDuplicateQuestions) {
            request.mergeDuplicateQuestions = mergeDuplicateQuestions;
            return this;
        }
        
        public Builder outputFormat(String outputFormat) {
            request.outputFormat = outputFormat;
            return this;
        }
        
        public Builder includePageMetadata(Boolean includePageMetadata) {
            request.includePageMetadata = includePageMetadata;
            return this;
        }
        
        public Builder qualityThreshold(Double qualityThreshold) {
            request.qualityThreshold = qualityThreshold;
            return this;
        }
        
        public IntegrateRequest build() {
            return request;
        }
    }
    
    // Getters and Setters
    public List<Long> getJobIds() { return jobIds; }
    public void setJobIds(List<Long> jobIds) { this.jobIds = jobIds; }
    
    public String getIntegrationMethod() { return integrationMethod; }
    public void setIntegrationMethod(String integrationMethod) { this.integrationMethod = integrationMethod; }
    
    public Boolean getPreservePageOrder() { return preservePageOrder; }
    public void setPreservePageOrder(Boolean preservePageOrder) { this.preservePageOrder = preservePageOrder; }
    
    public Boolean getMergeDuplicateQuestions() { return mergeDuplicateQuestions; }
    public void setMergeDuplicateQuestions(Boolean mergeDuplicateQuestions) { this.mergeDuplicateQuestions = mergeDuplicateQuestions; }
    
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    
    public Boolean getIncludePageMetadata() { return includePageMetadata; }
    public void setIncludePageMetadata(Boolean includePageMetadata) { this.includePageMetadata = includePageMetadata; }
    
    public Double getQualityThreshold() { return qualityThreshold; }
    public void setQualityThreshold(Double qualityThreshold) { this.qualityThreshold = qualityThreshold; }
    
    // Helper methods
    public boolean isSequentialIntegration() {
        return "sequential".equals(integrationMethod);
    }
    
    public boolean isParallelIntegration() {
        return "parallel".equals(integrationMethod);
    }
    
    public boolean isSmartIntegration() {
        return "smart".equals(integrationMethod);
    }
    
    public boolean isCombinedOutput() {
        return "combined".equals(outputFormat);
    }
    
    public boolean isSeparatedOutput() {
        return "separated".equals(outputFormat);
    }
    
    public boolean isBothOutput() {
        return "both".equals(outputFormat);
    }
    
    public int getJobCount() {
        return jobIds != null ? jobIds.size() : 0;
    }
    
    public boolean isValidJobCount() {
        return getJobCount() >= 2 && getJobCount() <= 50;
    }
    
    public boolean isValidQualityThreshold() {
        return qualityThreshold != null && qualityThreshold >= 0.0 && qualityThreshold <= 1.0;
    }
    
    @Override
    public String toString() {
        return "IntegrateRequest{" +
                "jobIds=" + jobIds +
                ", integrationMethod='" + integrationMethod + '\'' +
                ", preservePageOrder=" + preservePageOrder +
                ", mergeDuplicateQuestions=" + mergeDuplicateQuestions +
                ", outputFormat='" + outputFormat + '\'' +
                ", includePageMetadata=" + includePageMetadata +
                ", qualityThreshold=" + qualityThreshold +
                '}';
    }
}