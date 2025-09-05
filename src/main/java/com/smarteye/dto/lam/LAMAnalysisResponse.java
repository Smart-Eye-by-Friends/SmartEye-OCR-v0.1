package com.smarteye.dto.lam;

import lombok.Data;
import java.util.List;

/**
 * LAM 분석 응답 DTO
 */
@Data
public class LAMAnalysisResponse {
    private boolean success;
    private String message;
    private List<LAMLayoutBlock> blocks;
    private long processingTimeMs;
    private String imageId;
}
