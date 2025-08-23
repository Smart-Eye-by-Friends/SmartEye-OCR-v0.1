package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 분석 요청 DTO
 */
@Data
public class LAMAnalysisRequest {
    private String imageData; // Base64 encoded image
    private LAMImageInfo imageInfo;
    private LAMAnalysisOptions options;
}
