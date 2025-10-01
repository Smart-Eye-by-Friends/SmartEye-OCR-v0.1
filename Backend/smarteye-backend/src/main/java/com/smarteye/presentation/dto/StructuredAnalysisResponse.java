package com.smarteye.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 구조화된 분석 응답 DTO - 클라이언트로 반환되는 최종 응답
 */
public class StructuredAnalysisResponse extends AnalysisResponse {
    
    @JsonProperty("structured_result")
    private StructuredAnalysisResult structuredResult;
    
    @JsonProperty("structured_text")
    private String structuredText;
    
    @JsonProperty("total_questions")
    private Integer totalQuestions;

    @JsonProperty("total_elements")
    private Integer totalElements;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
    
    public StructuredAnalysisResponse() {
        super();
    }
    
    public StructuredAnalysisResponse(boolean success, String message) {
        super(success, message);
    }
    
    public StructuredAnalysisResponse(boolean success, String message, 
                                    StructuredAnalysisResult structuredResult, 
                                    String structuredText, Integer totalQuestions) {
        super(success, message);
        this.structuredResult = structuredResult;
        this.structuredText = structuredText;
        this.totalQuestions = totalQuestions;
    }
    
    // Getters and Setters
    public StructuredAnalysisResult getStructuredResult() {
        return structuredResult;
    }
    
    public void setStructuredResult(StructuredAnalysisResult structuredResult) {
        this.structuredResult = structuredResult;
    }
    
    public String getStructuredText() {
        return structuredText;
    }
    
    public void setStructuredText(String structuredText) {
        this.structuredText = structuredText;
    }
    
    public Integer getTotalQuestions() {
        return totalQuestions;
    }
    
    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public Integer getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Integer totalElements) {
        this.totalElements = totalElements;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    @Override
    public String toString() {
        return "StructuredAnalysisResponse{" +
                "structuredResult=" + structuredResult +
                ", structuredText='" + structuredText + '\'' +
                ", totalQuestions=" + totalQuestions +
                ", " + super.toString() +
                '}';
    }
}