package com.smarteye.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * React-friendly CIM 분석 통계 DTO
 *
 * 기존 Map<String, Object> stats를 타입 안전한 객체로 대체
 * React useMemo 최적화를 위한 불변 객체 설계
 */
@Schema(description = "CIM 분석 통계 정보")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CIMStats {

    @Schema(description = "전체 레이아웃 블록 수", example = "25")
    @JsonProperty("totalLayoutBlocks")
    private final int totalLayoutBlocks;

    @Schema(description = "텍스트 블록 수", example = "18")
    @JsonProperty("textBlocks")
    private final int textBlocks;

    @Schema(description = "이미지/그래프 블록 수", example = "4")
    @JsonProperty("imageBlocks")
    private final int imageBlocks;

    @Schema(description = "표 블록 수", example = "2")
    @JsonProperty("tableBlocks")
    private final int tableBlocks;

    @Schema(description = "수식 블록 수", example = "1")
    @JsonProperty("formulaBlocks")
    private final int formulaBlocks;

    @Schema(description = "평균 OCR 신뢰도 (0-100)", example = "87.5")
    @JsonProperty("averageConfidence")
    private final double averageConfidence;

    @Schema(description = "처리 시간 (밀리초)", example = "2450")
    @JsonProperty("processingTimeMs")
    private final long processingTimeMs;

    @Schema(description = "감지된 언어", example = "ko")
    @JsonProperty("detectedLanguage")
    private final String detectedLanguage;

    @Schema(description = "전체 텍스트 길이", example = "1,234")
    @JsonProperty("totalTextLength")
    private final int totalTextLength;

    @Schema(description = "구조화된 섹션 수", example = "5")
    @JsonProperty("structuredSections")
    private final int structuredSections;

    // 생성자
    public CIMStats(int totalLayoutBlocks,
                   int textBlocks,
                   int imageBlocks,
                   int tableBlocks,
                   int formulaBlocks,
                   double averageConfidence,
                   long processingTimeMs,
                   String detectedLanguage,
                   int totalTextLength,
                   int structuredSections) {
        this.totalLayoutBlocks = totalLayoutBlocks;
        this.textBlocks = textBlocks;
        this.imageBlocks = imageBlocks;
        this.tableBlocks = tableBlocks;
        this.formulaBlocks = formulaBlocks;
        this.averageConfidence = averageConfidence;
        this.processingTimeMs = processingTimeMs;
        this.detectedLanguage = detectedLanguage;
        this.totalTextLength = totalTextLength;
        this.structuredSections = structuredSections;
    }

    // Builder 패턴 지원
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int totalLayoutBlocks = 0;
        private int textBlocks = 0;
        private int imageBlocks = 0;
        private int tableBlocks = 0;
        private int formulaBlocks = 0;
        private double averageConfidence = 0.0;
        private long processingTimeMs = 0;
        private String detectedLanguage = "ko";
        private int totalTextLength = 0;
        private int structuredSections = 0;

        public Builder totalLayoutBlocks(int totalLayoutBlocks) {
            this.totalLayoutBlocks = totalLayoutBlocks;
            return this;
        }

        public Builder textBlocks(int textBlocks) {
            this.textBlocks = textBlocks;
            return this;
        }

        public Builder imageBlocks(int imageBlocks) {
            this.imageBlocks = imageBlocks;
            return this;
        }

        public Builder tableBlocks(int tableBlocks) {
            this.tableBlocks = tableBlocks;
            return this;
        }

        public Builder formulaBlocks(int formulaBlocks) {
            this.formulaBlocks = formulaBlocks;
            return this;
        }

        public Builder averageConfidence(double averageConfidence) {
            this.averageConfidence = averageConfidence;
            return this;
        }

        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public Builder detectedLanguage(String detectedLanguage) {
            this.detectedLanguage = detectedLanguage;
            return this;
        }

        public Builder totalTextLength(int totalTextLength) {
            this.totalTextLength = totalTextLength;
            return this;
        }

        public Builder structuredSections(int structuredSections) {
            this.structuredSections = structuredSections;
            return this;
        }

        public CIMStats build() {
            return new CIMStats(totalLayoutBlocks, textBlocks, imageBlocks,
                              tableBlocks, formulaBlocks, averageConfidence,
                              processingTimeMs, detectedLanguage,
                              totalTextLength, structuredSections);
        }
    }

    // Getters
    public int getTotalLayoutBlocks() { return totalLayoutBlocks; }
    public int getTextBlocks() { return textBlocks; }
    public int getImageBlocks() { return imageBlocks; }
    public int getTableBlocks() { return tableBlocks; }
    public int getFormulaBlocks() { return formulaBlocks; }
    public double getAverageConfidence() { return averageConfidence; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public String getDetectedLanguage() { return detectedLanguage; }
    public int getTotalTextLength() { return totalTextLength; }
    public int getStructuredSections() { return structuredSections; }

    // React 메모이제이션을 위한 equals/hashCode
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CIMStats cimStats = (CIMStats) obj;
        return totalLayoutBlocks == cimStats.totalLayoutBlocks &&
               textBlocks == cimStats.textBlocks &&
               imageBlocks == cimStats.imageBlocks &&
               tableBlocks == cimStats.tableBlocks &&
               formulaBlocks == cimStats.formulaBlocks &&
               Double.compare(cimStats.averageConfidence, averageConfidence) == 0 &&
               processingTimeMs == cimStats.processingTimeMs &&
               totalTextLength == cimStats.totalTextLength &&
               structuredSections == cimStats.structuredSections &&
               Objects.equals(detectedLanguage, cimStats.detectedLanguage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalLayoutBlocks, textBlocks, imageBlocks,
                          tableBlocks, formulaBlocks, averageConfidence,
                          processingTimeMs, detectedLanguage, totalTextLength,
                          structuredSections);
    }

    @Override
    public String toString() {
        return "CIMStats{" +
               "totalLayoutBlocks=" + totalLayoutBlocks +
               ", textBlocks=" + textBlocks +
               ", averageConfidence=" + averageConfidence +
               ", processingTimeMs=" + processingTimeMs +
               '}';
    }
}