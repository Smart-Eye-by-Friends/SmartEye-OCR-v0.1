package com.smarteye.presentation.dto;

import com.smarteye.presentation.dto.common.LayoutInfo;
import java.util.List;

/**
 * 레이아웃 분석 결과 DTO
 * Python api_server.py의 layout_info 결과와 동일한 구조
 */
public class LayoutAnalysisResult {

    private List<LayoutInfo> layoutInfo;
    private String status;
    private String message;

    // 좌표계 스케일링을 위한 이미지 크기 정보
    private int originalImageWidth;   // 백엔드에서 LAM으로 보낸 원본 이미지 크기
    private int originalImageHeight;
    private int processedImageWidth;  // LAM 서비스가 실제 분석한 이미지 크기
    private int processedImageHeight;
    
    public LayoutAnalysisResult() {}
    
    public LayoutAnalysisResult(List<LayoutInfo> layoutInfo) {
        this.layoutInfo = layoutInfo;
        this.status = "success";
        this.message = "Layout analysis completed successfully";
    }

    public LayoutAnalysisResult(List<LayoutInfo> layoutInfo, int originalWidth, int originalHeight,
                               int processedWidth, int processedHeight) {
        this.layoutInfo = layoutInfo;
        this.status = "success";
        this.message = "Layout analysis completed successfully";
        this.originalImageWidth = originalWidth;
        this.originalImageHeight = originalHeight;
        this.processedImageWidth = processedWidth;
        this.processedImageHeight = processedHeight;
    }
    
    public LayoutAnalysisResult(List<LayoutInfo> layoutInfo, String status, String message) {
        this.layoutInfo = layoutInfo;
        this.status = status;
        this.message = message;
    }
    
    // Getters and Setters
    public List<LayoutInfo> getLayoutInfo() {
        return layoutInfo;
    }
    
    public void setLayoutInfo(List<LayoutInfo> layoutInfo) {
        this.layoutInfo = layoutInfo;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSuccess() {
        return "success".equals(status);
    }
    
    public int getElementCount() {
        return layoutInfo != null ? layoutInfo.size() : 0;
    }

    // 이미지 크기 관련 getter/setter
    public int getOriginalImageWidth() { return originalImageWidth; }
    public void setOriginalImageWidth(int originalImageWidth) { this.originalImageWidth = originalImageWidth; }

    public int getOriginalImageHeight() { return originalImageHeight; }
    public void setOriginalImageHeight(int originalImageHeight) { this.originalImageHeight = originalImageHeight; }

    public int getProcessedImageWidth() { return processedImageWidth; }
    public void setProcessedImageWidth(int processedImageWidth) { this.processedImageWidth = processedImageWidth; }

    public int getProcessedImageHeight() { return processedImageHeight; }
    public void setProcessedImageHeight(int processedImageHeight) { this.processedImageHeight = processedImageHeight; }

    /**
     * 좌표 스케일링이 필요한지 확인
     */
    public boolean needsCoordinateScaling() {
        return originalImageWidth > 0 && originalImageHeight > 0 &&
               processedImageWidth > 0 && processedImageHeight > 0 &&
               (originalImageWidth != processedImageWidth || originalImageHeight != processedImageHeight);
    }

    /**
     * X 좌표 스케일링 비율 계산
     */
    public double getScaleX() {
        if (processedImageWidth <= 0) return 1.0;
        return (double) originalImageWidth / processedImageWidth;
    }

    /**
     * Y 좌표 스케일링 비율 계산
     */
    public double getScaleY() {
        if (processedImageHeight <= 0) return 1.0;
        return (double) originalImageHeight / processedImageHeight;
    }
    
    @Override
    public String toString() {
        return "LayoutAnalysisResult{" +
                "layoutInfo=" + layoutInfo +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", elementCount=" + getElementCount() +
                '}';
    }
}