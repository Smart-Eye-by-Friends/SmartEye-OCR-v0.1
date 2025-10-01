package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 텍스트 변환 응답 DTO
 * Phase 1 API: /api/document/cim-to-text의 응답 데이터
 */
@Schema(description = "CIM 데이터 텍스트 변환 응답")
public class TextConversionResponse {

    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 메시지", example = "텍스트 변환이 성공적으로 완료되었습니다.")
    private String message;

    @Schema(description = "변환된 포맷팅 텍스트")
    private String formattedText;

    @Schema(description = "변환 통계 정보")
    private TextConversionStats stats;

    @Schema(description = "메타데이터 (요청 시 포함)")
    private Map<String, Object> metadata;

    @Schema(description = "변환 타임스탬프", example = "1640995200")
    private Long timestamp;

    /**
     * 텍스트 변환 통계 내부 클래스
     */
    @Schema(description = "텍스트 변환 통계")
    public static class TextConversionStats {
        @Schema(description = "총 문자 수", example = "1024")
        private int totalCharacters;

        @Schema(description = "총 단어 수", example = "256")
        private int totalWords;

        @Schema(description = "총 문제 수", example = "5")
        private int totalQuestions;

        @Schema(description = "처리 시간 (밀리초)", example = "150")
        private long processingTimeMs;

        @Schema(description = "원본 데이터 크기 (바이트)", example = "2048")
        private long originalDataSize;

        // 기본 생성자
        public TextConversionStats() {}

        // 전체 데이터 생성자
        public TextConversionStats(
                int totalCharacters,
                int totalWords,
                int totalQuestions,
                long processingTimeMs,
                long originalDataSize) {
            this.totalCharacters = totalCharacters;
            this.totalWords = totalWords;
            this.totalQuestions = totalQuestions;
            this.processingTimeMs = processingTimeMs;
            this.originalDataSize = originalDataSize;
        }

        // Getters and Setters
        public int getTotalCharacters() {
            return totalCharacters;
        }

        public void setTotalCharacters(int totalCharacters) {
            this.totalCharacters = totalCharacters;
        }

        public int getTotalWords() {
            return totalWords;
        }

        public void setTotalWords(int totalWords) {
            this.totalWords = totalWords;
        }

        public int getTotalQuestions() {
            return totalQuestions;
        }

        public void setTotalQuestions(int totalQuestions) {
            this.totalQuestions = totalQuestions;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        public long getOriginalDataSize() {
            return originalDataSize;
        }

        public void setOriginalDataSize(long originalDataSize) {
            this.originalDataSize = originalDataSize;
        }
    }

    // 기본 생성자
    public TextConversionResponse() {}

    // 성공/실패 생성자
    public TextConversionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    // 성공 응답 생성자
    public TextConversionResponse(
            boolean success,
            String message,
            String formattedText,
            TextConversionStats stats) {
        this.success = success;
        this.message = message;
        this.formattedText = formattedText;
        this.stats = stats;
        this.timestamp = System.currentTimeMillis();
    }

    // 전체 데이터 생성자
    public TextConversionResponse(
            boolean success,
            String message,
            String formattedText,
            TextConversionStats stats,
            Map<String, Object> metadata) {
        this.success = success;
        this.message = message;
        this.formattedText = formattedText;
        this.stats = stats;
        this.metadata = metadata;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFormattedText() {
        return formattedText;
    }

    public void setFormattedText(String formattedText) {
        this.formattedText = formattedText;
    }

    public TextConversionStats getStats() {
        return stats;
    }

    public void setStats(TextConversionStats stats) {
        this.stats = stats;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}