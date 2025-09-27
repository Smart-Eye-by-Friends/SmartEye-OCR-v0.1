package com.smarteye.shared.exception;

public class FileProcessingException extends RuntimeException {
    
    private final String errorCode;
    
    public FileProcessingException(String message) {
        super(message);
        this.errorCode = "FILE_PROCESSING_ERROR";
    }
    
    public FileProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FILE_PROCESSING_ERROR";
    }
    
    public FileProcessingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}