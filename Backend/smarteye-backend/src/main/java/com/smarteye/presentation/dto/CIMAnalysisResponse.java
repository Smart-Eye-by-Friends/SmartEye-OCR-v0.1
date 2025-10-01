package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * CIM 통합 분석 응답 DTO
 * Phase 1 API: /api/document/analyze-cim의 응답 데이터
 */
@Schema(description = "CIM 통합 분석 응답")
public class CIMAnalysisResponse {

    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 메시지", example = "CIM 분석이 성공적으로 완료되었습니다.")
    private String message;

    @Schema(description = "작업 ID", example = "job_1234567890")
    private String jobId;

    @Schema(description = "레이아웃 시각화 이미지 URL", example = "/static/layout_viz_1234567890.png")
    private String layoutImageUrl;

    @Schema(description = "분석 통계 정보")
    private Map<String, Object> stats;

    @Schema(description = "CIM 통합 데이터 (JSON 형태)")
    private Map<String, Object> cimData;

    @Schema(description = "포맷팅된 텍스트 (현재 Java 포맷팅 규칙 적용)")
    private String formattedText;

    @Schema(description = "분석 타임스탬프", example = "1640995200")
    private Long timestamp;

    // 기본 생성자
    public CIMAnalysisResponse() {}

    // 성공/실패 생성자
    public CIMAnalysisResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // 전체 데이터 생성자
    public CIMAnalysisResponse(
            boolean success,
            String message,
            String jobId,
            String layoutImageUrl,
            Map<String, Object> stats,
            Map<String, Object> cimData,
            String formattedText,
            Long timestamp) {
        this.success = success;
        this.message = message;
        this.jobId = jobId;
        this.layoutImageUrl = layoutImageUrl;
        this.stats = stats;
        this.cimData = cimData;
        this.formattedText = formattedText;
        this.timestamp = timestamp;
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getLayoutImageUrl() {
        return layoutImageUrl;
    }

    public void setLayoutImageUrl(String layoutImageUrl) {
        this.layoutImageUrl = layoutImageUrl;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }

    public Map<String, Object> getCimData() {
        return cimData;
    }

    public void setCimData(Map<String, Object> cimData) {
        this.cimData = cimData;
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
}