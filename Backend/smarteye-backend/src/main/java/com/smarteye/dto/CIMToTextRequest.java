package com.smarteye.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * CIM -> 텍스트 변환 요청 DTO
 * Phase 1 API: /api/document/cim-to-text의 요청 데이터
 */
@Schema(description = "CIM 데이터를 텍스트로 변환 요청")
public class CIMToTextRequest {

    @Schema(description = "작업 ID (기존 분석 결과 조회용)", example = "job_1234567890")
    private String jobId;

    @Schema(description = "CIM 데이터 (JSON 형태)")
    @NotNull(message = "CIM 데이터는 필수입니다")
    private Map<String, Object> cimData;

    @Schema(description = "텍스트 출력 형식", example = "FORMATTED", allowableValues = {"FORMATTED", "STRUCTURED", "RAW"})
    private TextOutputFormat outputFormat = TextOutputFormat.FORMATTED;

    @Schema(description = "메타데이터 포함 여부", example = "false")
    private boolean includeMetadata = false;

    @Schema(description = "특정 섹션만 추출 (선택사항)", example = "questions")
    private String sectionFilter;

    /**
     * 텍스트 출력 형식 열거형
     */
    public enum TextOutputFormat {
        FORMATTED,   // JsonUtils.createFormattedText() 사용
        STRUCTURED,  // 구조화된 형태
        RAW         // 원시 텍스트만
    }

    // 기본 생성자
    public CIMToTextRequest() {}

    // 필수 데이터 생성자
    public CIMToTextRequest(Map<String, Object> cimData) {
        this.cimData = cimData;
    }

    // 전체 데이터 생성자
    public CIMToTextRequest(
            String jobId,
            Map<String, Object> cimData,
            TextOutputFormat outputFormat,
            boolean includeMetadata,
            String sectionFilter) {
        this.jobId = jobId;
        this.cimData = cimData;
        this.outputFormat = outputFormat;
        this.includeMetadata = includeMetadata;
        this.sectionFilter = sectionFilter;
    }

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Map<String, Object> getCimData() {
        return cimData;
    }

    public void setCimData(Map<String, Object> cimData) {
        this.cimData = cimData;
    }

    public TextOutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(TextOutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public String getSectionFilter() {
        return sectionFilter;
    }

    public void setSectionFilter(String sectionFilter) {
        this.sectionFilter = sectionFilter;
    }
}