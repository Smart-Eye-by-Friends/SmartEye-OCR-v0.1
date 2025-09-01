package com.smarteye.dto;

import java.util.List;
import java.util.Map;

/**
 * 분석 응답 DTO
 * Python api_server.py의 analyze_worksheet 응답과 동일한 구조
 */
public class AnalysisResponse {
    
    private boolean success;
    private String layoutImageUrl;
    private String jsonUrl;
    private AnalysisStats stats;
    private List<OCRResult> ocrResults;
    private List<AIDescriptionResult> aiResults;
    private String ocrText;
    private String aiText;
    private String formattedText;
    private Long timestamp;
    private String message;
    
    public AnalysisResponse() {}
    
    public AnalysisResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis() / 1000; // Unix timestamp
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getLayoutImageUrl() {
        return layoutImageUrl;
    }
    
    public void setLayoutImageUrl(String layoutImageUrl) {
        this.layoutImageUrl = layoutImageUrl;
    }
    
    public String getJsonUrl() {
        return jsonUrl;
    }
    
    public void setJsonUrl(String jsonUrl) {
        this.jsonUrl = jsonUrl;
    }
    
    public AnalysisStats getStats() {
        return stats;
    }
    
    public void setStats(AnalysisStats stats) {
        this.stats = stats;
    }
    
    public List<OCRResult> getOcrResults() {
        return ocrResults;
    }
    
    public void setOcrResults(List<OCRResult> ocrResults) {
        this.ocrResults = ocrResults;
    }
    
    public List<AIDescriptionResult> getAiResults() {
        return aiResults;
    }
    
    public void setAiResults(List<AIDescriptionResult> aiResults) {
        this.aiResults = aiResults;
    }
    
    public String getOcrText() {
        return ocrText;
    }
    
    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }
    
    public String getAiText() {
        return aiText;
    }
    
    public void setAiText(String aiText) {
        this.aiText = aiText;
    }
    
    public String getFormattedText() {
        return formattedText;
    }
    
    public void setFormattedText(String formattedText) {
        this.formattedText = formattedText;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * 분석 통계 내부 클래스
     */
    public static class AnalysisStats {
        private int totalLayoutElements;
        private int ocrTextBlocks;
        private int aiDescriptions;
        private Map<String, Integer> classCounts;
        
        public AnalysisStats() {}
        
        public AnalysisStats(int totalLayoutElements, int ocrTextBlocks, int aiDescriptions, Map<String, Integer> classCounts) {
            this.totalLayoutElements = totalLayoutElements;
            this.ocrTextBlocks = ocrTextBlocks;
            this.aiDescriptions = aiDescriptions;
            this.classCounts = classCounts;
        }
        
        // Getters and Setters
        public int getTotalLayoutElements() {
            return totalLayoutElements;
        }
        
        public void setTotalLayoutElements(int totalLayoutElements) {
            this.totalLayoutElements = totalLayoutElements;
        }
        
        public int getOcrTextBlocks() {
            return ocrTextBlocks;
        }
        
        public void setOcrTextBlocks(int ocrTextBlocks) {
            this.ocrTextBlocks = ocrTextBlocks;
        }
        
        public int getAiDescriptions() {
            return aiDescriptions;
        }
        
        public void setAiDescriptions(int aiDescriptions) {
            this.aiDescriptions = aiDescriptions;
        }
        
        public Map<String, Integer> getClassCounts() {
            return classCounts;
        }
        
        public void setClassCounts(Map<String, Integer> classCounts) {
            this.classCounts = classCounts;
        }
    }
    
    @Override
    public String toString() {
        return "AnalysisResponse{" +
                "success=" + success +
                ", layoutImageUrl='" + layoutImageUrl + '\'' +
                ", jsonUrl='" + jsonUrl + '\'' +
                ", ocrTextBlocks=" + (ocrResults != null ? ocrResults.size() : 0) +
                ", aiDescriptions=" + (aiResults != null ? aiResults.size() : 0) +
                ", timestamp=" + timestamp +
                ", message='" + message + '\'' +
                '}';
    }
}