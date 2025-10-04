package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 분석 응답 DTO
 * Python api_server.py의 analyze_worksheet 응답과 동일한 구조
 */
@Schema(description = "문서 분석 응답 데이터")
public class AnalysisResponse {
    
    private boolean success;
    private String layoutImageUrl;
    private String jsonUrl;
    private AnalysisStats stats;
    private List<OCRResult> ocrResults;
    private List<AIDescriptionResult> aiResults;
    @Schema(description = "OCR 추출 텍스트 (원시 데이터)", example = "1번 다음 중 옳은 것은?")
    private String ocrText;

    @Schema(description = "AI 생성 설명 (이미지/차트 분석)", example = "이 그래프는 선형 증가 패턴을 보입니다.")
    private String aiText;

    @Schema(
        description = """
                포맷팅된 HTML 텍스트 (기본 분석 결과)

                **특징:**
                - LAM 레이아웃 + OCR 텍스트 조합
                - 기본 HTML 구조화
                - 구조화된 분석(/analyze-cim)을 사용하면 다단 레이아웃 지원

                **더 나은 결과를 원하시면:**
                `/api/document/analyze-cim` 엔드포인트를 사용하세요.
                (다단 레이아웃 지원 + XSS 방지 + 2D 공간 정렬)
                """,
        example = "<h2>1번</h2><p>다음 중 옳은 것은?</p>"
    )
    private String formattedText;

    @Schema(description = "분석 완료 시간 (Unix timestamp)", example = "1640995200")
    private Long timestamp;
    private String message;
    private String jobId;
    
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
    
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    /**
     * 분석 통계 내부 클래스
     */
    public static class AnalysisStats {
        private int totalLayoutElements;
        private int ocrTextBlocks;
        private int aiDescriptions;
        private Map<String, Integer> classCounts;

        // 프론트엔드 호환성을 위한 추가 필드
        private int totalCharacters;
        private double averageConfidence;
        private double processingTime; // 초 단위

        public AnalysisStats() {}

        public AnalysisStats(int totalLayoutElements, int ocrTextBlocks, int aiDescriptions, Map<String, Integer> classCounts) {
            this.totalLayoutElements = totalLayoutElements;
            this.ocrTextBlocks = ocrTextBlocks;
            this.aiDescriptions = aiDescriptions;
            this.classCounts = classCounts;
            this.totalCharacters = 0;
            this.averageConfidence = 0.0;
            this.processingTime = 0.0;
        }

        public AnalysisStats(int totalLayoutElements, int ocrTextBlocks, int aiDescriptions,
                           Map<String, Integer> classCounts, int totalCharacters,
                           double averageConfidence, double processingTime) {
            this.totalLayoutElements = totalLayoutElements;
            this.ocrTextBlocks = ocrTextBlocks;
            this.aiDescriptions = aiDescriptions;
            this.classCounts = classCounts;
            this.totalCharacters = totalCharacters;
            this.averageConfidence = averageConfidence;
            this.processingTime = processingTime;
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

        // 프론트엔드 호환을 위한 새로운 getter/setter
        public int getTotalCharacters() {
            return totalCharacters;
        }

        public void setTotalCharacters(int totalCharacters) {
            this.totalCharacters = totalCharacters;
        }

        public double getAverageConfidence() {
            return averageConfidence;
        }

        public void setAverageConfidence(double averageConfidence) {
            this.averageConfidence = averageConfidence;
        }

        public double getProcessingTime() {
            return processingTime;
        }

        public void setProcessingTime(double processingTime) {
            this.processingTime = processingTime;
        }

        // 프론트엔드에서 기대하는 snake_case 필드들을 위한 별칭 getter
        @com.fasterxml.jackson.annotation.JsonProperty("total_elements")
        public int getTotal_elements() {
            return totalLayoutElements;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("total_characters")
        public int getTotal_characters() {
            return totalCharacters;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("average_confidence")
        public double getAverage_confidence() {
            return averageConfidence;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("processing_time")
        public double getProcessing_time() {
            return processingTime;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("element_counts")
        public Map<String, Integer> getElement_counts() {
            return classCounts;
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