package com.smarteye.dto;

/**
 * 텍스트 포맷팅 응답 DTO
 * Python api_server.py의 format_text_from_json 응답과 동일
 */
public class FormatTextResponse {
    
    private boolean success;
    private String formattedText;
    private String message;
    private Long timestamp;
    
    public FormatTextResponse() {}
    
    public FormatTextResponse(boolean success, String formattedText, String message) {
        this.success = success;
        this.formattedText = formattedText;
        this.message = message;
        this.timestamp = System.currentTimeMillis() / 1000; // Unix timestamp
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getFormattedText() {
        return formattedText;
    }
    
    public void setFormattedText(String formattedText) {
        this.formattedText = formattedText;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "FormatTextResponse{" +
                "success=" + success +
                ", textLength=" + (formattedText != null ? formattedText.length() : 0) +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}