package com.smarteye.dto.lam;

import lombok.Data;

/**
 * LAM 서비스 상태 응답 DTO
 */
@Data
public class LAMHealthResponse {
    private String status;
    private String message;
    private long uptime;
    private String version;
}
