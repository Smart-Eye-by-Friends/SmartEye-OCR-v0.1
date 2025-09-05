package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 이미지 정보 DTO
 */
@Data
public class LAMImageInfo {
    private String filename;
    private long size;
    private String mimeType;
}
