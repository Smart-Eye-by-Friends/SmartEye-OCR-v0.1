package com.smarteye.exception;

public class LAMServiceException extends RuntimeException {
    
    private final String errorCode;
    
    public LAMServiceException(String message) {
        super(message);
        this.errorCode = "LAM_SERVICE_ERROR";
    }
    
    public LAMServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public LAMServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "LAM_SERVICE_ERROR";
    }
    
    public LAMServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}