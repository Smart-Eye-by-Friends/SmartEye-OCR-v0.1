package com.smarteye.dto;

import com.smarteye.dto.common.LayoutInfo;
import java.util.List;

/**
 * 레이아웃 분석 결과 DTO
 * Python api_server.py의 layout_info 결과와 동일한 구조
 */
public class LayoutAnalysisResult {
    
    private List<LayoutInfo> layoutInfo;
    private String status;
    private String message;
    
    public LayoutAnalysisResult() {}
    
    public LayoutAnalysisResult(List<LayoutInfo> layoutInfo) {
        this.layoutInfo = layoutInfo;
        this.status = "success";
        this.message = "Layout analysis completed successfully";
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