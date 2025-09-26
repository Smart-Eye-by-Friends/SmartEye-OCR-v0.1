package com.smarteye.presentation.dto;

/**
 * 문서 생성 응답 DTO  
 * Python api_server.py의 save_as_word 응답과 동일
 */
public class DocumentResponse {
    
    private boolean success;
    private String message;
    private String filename;
    private String downloadUrl;
    private Long timestamp;
    
    public DocumentResponse() {}
    
    public DocumentResponse(boolean success, String message, String filename, String downloadUrl) {
        this.success = success;
        this.message = message;
        this.filename = filename;
        this.downloadUrl = downloadUrl;
        this.timestamp = System.currentTimeMillis() / 1000; // Unix timestamp
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getFilename() {
        return filename;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "DocumentResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", filename='" + filename + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}