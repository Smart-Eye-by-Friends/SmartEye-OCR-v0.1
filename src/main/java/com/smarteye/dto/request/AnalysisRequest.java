package com.smarteye.dto.request;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalysisRequest {
    private MultipartFile file;
    private String analysisType;
    private String language;
}
