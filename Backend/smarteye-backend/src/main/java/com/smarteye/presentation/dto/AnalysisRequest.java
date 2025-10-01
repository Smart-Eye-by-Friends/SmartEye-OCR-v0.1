package com.smarteye.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 분석 요청 DTO
 * Python api_server.py의 analyze_worksheet 파라미터와 동일
 */
public class AnalysisRequest {
    
    @Pattern(regexp = "SmartEyeSsen|docstructbench|doclaynet_docsynth|docsynth300k", 
             message = "지원하지 않는 모델입니다. 사용 가능한 모델: SmartEyeSsen, docstructbench, doclaynet_docsynth, docsynth300k")
    private String modelChoice = "SmartEyeSsen";
    
    private String apiKey; // OpenAI API 키 (선택사항)
    
    public AnalysisRequest() {}
    
    public AnalysisRequest(String modelChoice, String apiKey) {
        this.modelChoice = modelChoice;
        this.apiKey = apiKey;
    }
    
    // Getters and Setters
    public String getModelChoice() {
        return modelChoice;
    }
    
    public void setModelChoice(String modelChoice) {
        this.modelChoice = modelChoice;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "AnalysisRequest{" +
                "modelChoice='" + modelChoice + '\'' +
                ", hasApiKey=" + hasApiKey() +
                '}';
    }
}