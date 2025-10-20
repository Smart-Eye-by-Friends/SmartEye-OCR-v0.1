package com.smarteye.application.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * E2E 테스트 결과 (Phase 7 - E2E Testing)
 * 
 * <p>종단간 테스트 결과를 저장하는 클래스입니다.
 * 정확도 메트릭과 함께 테스트 결과를 포함합니다.</p>
 * 
 * @version 2.0
 * @since 2025-01-20
 * @see GroundTruth
 * @see AccuracyMetrics
 */
public class E2ETestResult {
    
    /**
     * 테스트 ID
     */
    @JsonProperty("test_id")
    private String testId;
    
    /**
     * 이미지 ID
     */
    @JsonProperty("image_id")
    private String imageId;
    
    /**
     * 레이아웃 타입
     */
    @JsonProperty("layout_type")
    private String layoutType;
    
    /**
     * 테스트 성공 여부
     */
    @JsonProperty("success")
    private boolean success;
    
    /**
     * 정확도 메트릭
     */
    @JsonProperty("accuracy_metrics")
    private AccuracyMetricsResult accuracyMetrics;
    
    /**
     * 처리 시간 (ms)
     */
    @JsonProperty("processing_time_ms")
    private long processingTimeMs;
    
    /**
     * 에러 메시지 (실패 시)
     */
    @JsonProperty("error_message")
    private String errorMessage;
    
    /**
     * 추가 메타데이터
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Constructors
    
    public E2ETestResult() {}
    
    public E2ETestResult(String testId, String imageId, String layoutType, boolean success,
                         AccuracyMetricsResult accuracyMetrics, long processingTimeMs,
                         String errorMessage, Map<String, Object> metadata) {
        this.testId = testId;
        this.imageId = imageId;
        this.layoutType = layoutType;
        this.success = success;
        this.accuracyMetrics = accuracyMetrics;
        this.processingTimeMs = processingTimeMs;
        this.errorMessage = errorMessage;
        this.metadata = metadata;
    }
    
    // Getters and Setters
    
    public String getTestId() {
        return testId;
    }
    
    public void setTestId(String testId) {
        this.testId = testId;
    }
    
    public String getImageId() {
        return imageId;
    }
    
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }
    
    public String getLayoutType() {
        return layoutType;
    }
    
    public void setLayoutType(String layoutType) {
        this.layoutType = layoutType;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public AccuracyMetricsResult getAccuracyMetrics() {
        return accuracyMetrics;
    }
    
    public void setAccuracyMetrics(AccuracyMetricsResult accuracyMetrics) {
        this.accuracyMetrics = accuracyMetrics;
    }
    
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * 정확도 메트릭 결과
     */
    public static class AccuracyMetricsResult {
        
        /**
         * 전체 정확도 (0.0 ~ 1.0)
         */
        @JsonProperty("overall_accuracy")
        private double overallAccuracy;
        
        /**
         * Precision (정밀도)
         * <p>올바르게 할당된 요소 / 할당된 전체 요소</p>
         */
        @JsonProperty("precision")
        private double precision;
        
        /**
         * Recall (재현율)
         * <p>올바르게 할당된 요소 / 할당되어야 할 전체 요소</p>
         */
        @JsonProperty("recall")
        private double recall;
        
        /**
         * F1-Score
         * <p>2 * (Precision * Recall) / (Precision + Recall)</p>
         */
        @JsonProperty("f1_score")
        private double f1Score;
        
        /**
         * 올바르게 할당된 요소 수
         */
        @JsonProperty("correct_assignments")
        private int correctAssignments;
        
        /**
         * 잘못 할당된 요소 수
         */
        @JsonProperty("incorrect_assignments")
        private int incorrectAssignments;
        
        /**
         * 누락된 요소 수 (할당되지 않음)
         */
        @JsonProperty("missing_assignments")
        private int missingAssignments;
        
        /**
         * 전체 요소 수
         */
        @JsonProperty("total_elements")
        private int totalElements;
        
        /**
         * 문제별 정확도
         * <p>Key: 문제 ID, Value: 정확도 (0.0 ~ 1.0)</p>
         */
        @JsonProperty("per_question_accuracy")
        private Map<String, Double> perQuestionAccuracy;
        
        /**
         * 요소 타입별 정확도
         * <p>Key: 요소 타입, Value: 정확도 (0.0 ~ 1.0)</p>
         */
        @JsonProperty("per_element_type_accuracy")
        private Map<String, Double> perElementTypeAccuracy;
        
        // Constructors
        
        public AccuracyMetricsResult() {}
        
        public AccuracyMetricsResult(double overallAccuracy, double precision, double recall, double f1Score,
                                     int correctAssignments, int incorrectAssignments, int missingAssignments,
                                     int totalElements, Map<String, Double> perQuestionAccuracy,
                                     Map<String, Double> perElementTypeAccuracy) {
            this.overallAccuracy = overallAccuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1Score = f1Score;
            this.correctAssignments = correctAssignments;
            this.incorrectAssignments = incorrectAssignments;
            this.missingAssignments = missingAssignments;
            this.totalElements = totalElements;
            this.perQuestionAccuracy = perQuestionAccuracy;
            this.perElementTypeAccuracy = perElementTypeAccuracy;
        }
        
        // Getters and Setters
        
        public double getOverallAccuracy() {
            return overallAccuracy;
        }
        
        public void setOverallAccuracy(double overallAccuracy) {
            this.overallAccuracy = overallAccuracy;
        }
        
        public double getPrecision() {
            return precision;
        }
        
        public void setPrecision(double precision) {
            this.precision = precision;
        }
        
        public double getRecall() {
            return recall;
        }
        
        public void setRecall(double recall) {
            this.recall = recall;
        }
        
        public double getF1Score() {
            return f1Score;
        }
        
        public void setF1Score(double f1Score) {
            this.f1Score = f1Score;
        }
        
        public int getCorrectAssignments() {
            return correctAssignments;
        }
        
        public void setCorrectAssignments(int correctAssignments) {
            this.correctAssignments = correctAssignments;
        }
        
        public int getIncorrectAssignments() {
            return incorrectAssignments;
        }
        
        public void setIncorrectAssignments(int incorrectAssignments) {
            this.incorrectAssignments = incorrectAssignments;
        }
        
        public int getMissingAssignments() {
            return missingAssignments;
        }
        
        public void setMissingAssignments(int missingAssignments) {
            this.missingAssignments = missingAssignments;
        }
        
        public int getTotalElements() {
            return totalElements;
        }
        
        public void setTotalElements(int totalElements) {
            this.totalElements = totalElements;
        }
        
        public Map<String, Double> getPerQuestionAccuracy() {
            return perQuestionAccuracy;
        }
        
        public void setPerQuestionAccuracy(Map<String, Double> perQuestionAccuracy) {
            this.perQuestionAccuracy = perQuestionAccuracy;
        }
        
        public Map<String, Double> getPerElementTypeAccuracy() {
            return perElementTypeAccuracy;
        }
        
        public void setPerElementTypeAccuracy(Map<String, Double> perElementTypeAccuracy) {
            this.perElementTypeAccuracy = perElementTypeAccuracy;
        }
    }
}
