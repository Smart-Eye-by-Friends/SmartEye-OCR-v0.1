package com.smarteye.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalysisResult {
    private String analysisId;
    private String fileName;
    private String fileType;
    private long fileSize;
    
    // LAM (Layout Analysis) 결과
    private LayoutAnalysisResult layoutAnalysis;
    
    // TSPM (Text & Semantic Processing) 결과
    private TextAnalysisResult textAnalysis;
    
    // CIM (Content Integration) 결과
    private IntegratedResult integratedResult;
    
    // 처리 메타데이터
    private ProcessingMetadata metadata;
    
    @Data
    @Builder
    public static class LayoutAnalysisResult {
        private List<DetectedRegion> regions;
        private String visualizationImageUrl;
        private Map<String, Object> layoutStatistics;
    }
    
    @Data
    @Builder
    public static class DetectedRegion {
        private String type; // text, image, table, figure, etc.
        private BoundingBox boundingBox;
        private double confidence;
        private Map<String, Object> properties;
    }
    
    @Data
    @Builder
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
    }
    
    @Data
    @Builder
    public static class TextAnalysisResult {
        private String extractedText;
        private List<TextRegion> textRegions;
        private String semanticAnalysis;
        private double ocrConfidence;
    }
    
    @Data
    @Builder
    public static class TextRegion {
        private String text;
        private BoundingBox boundingBox;
        private double confidence;
        private String language;
    }
    
    @Data
    @Builder
    public static class IntegratedResult {
        private String summary;
        private List<String> keyPoints;
        private Map<String, Object> structuredData;
        private String finalImageUrl;
    }
    
    @Data
    @Builder
    public static class ProcessingMetadata {
        private long processingTimeMs;
        private String processingVersion;
        private Map<String, String> modelVersions;
        private String processingTimestamp;
    }
}
