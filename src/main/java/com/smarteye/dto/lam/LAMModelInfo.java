package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 모델 정보 DTO
 */
@Data
public class LAMModelInfo {
    private String modelName;
    private String version;
    private String description;
    private String inputSize;
    private String[] supportedFormats;
}
