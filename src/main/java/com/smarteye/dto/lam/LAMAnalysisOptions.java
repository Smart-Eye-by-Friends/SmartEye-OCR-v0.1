package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 분석 옵션 DTO
 */
@Data
public class LAMAnalysisOptions {
    private double confidenceThreshold = 0.5;
    private int maxBlocks = 100;
    private boolean detectText = true;
    private boolean detectTables = true;
    private boolean detectFigures = true;
}
