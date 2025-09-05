package com.smarteye.exception;

public class DocumentAnalysisException extends RuntimeException {
    
    private final String errorCode;
    
    public DocumentAnalysisException(String message) {
        super(message);
        this.errorCode = "DOCUMENT_ANALYSIS_ERROR";
    }
    
    public DocumentAnalysisException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public DocumentAnalysisException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOCUMENT_ANALYSIS_ERROR";
    }
    
    public DocumentAnalysisException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}