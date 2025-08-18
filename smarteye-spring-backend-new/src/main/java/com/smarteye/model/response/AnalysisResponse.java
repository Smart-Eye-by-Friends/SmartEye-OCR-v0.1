package com.smarteye.model.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AnalysisResponse {
    private boolean success;
    private String message;
    private Object data;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public static AnalysisResponse success(Object data) {
        return AnalysisResponse.builder()
                .success(true)
                .message("분석이 성공적으로 완료되었습니다.")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static AnalysisResponse error(String message) {
        return AnalysisResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
