package com.smarteye.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * 구조화된 분석 결과 DTO - LAM 서비스의 구조화된 분석 응답
 */
public class StructuredAnalysisResult {
    
    @JsonProperty("document_info")
    private DocumentInfo documentInfo;
    
    private List<QuestionResult> questions = new ArrayList<>();
    
    public StructuredAnalysisResult() {}
    
    public StructuredAnalysisResult(DocumentInfo documentInfo, List<QuestionResult> questions) {
        this.documentInfo = documentInfo;
        this.questions = questions != null ? questions : new ArrayList<>();
    }
    
    // Getters and Setters
    public DocumentInfo getDocumentInfo() {
        return documentInfo;
    }
    
    public void setDocumentInfo(DocumentInfo documentInfo) {
        this.documentInfo = documentInfo;
    }
    
    public List<QuestionResult> getQuestions() {
        return questions;
    }
    
    public void setQuestions(List<QuestionResult> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return "StructuredAnalysisResult{" +
                "documentInfo=" + documentInfo +
                ", questions=" + questions +
                '}';
    }
}