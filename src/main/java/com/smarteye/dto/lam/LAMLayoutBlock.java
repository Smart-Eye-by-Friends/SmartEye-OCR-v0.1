package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 레이아웃 블록 DTO
 */
@Data
public class LAMLayoutBlock {
    private String type;
    private double confidence;
    private LAMCoordinates bbox;
}
