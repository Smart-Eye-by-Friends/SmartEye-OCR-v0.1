package com.smarteye.exception;

/**
 * 파일 처리 관련 예외
 */
public class FileProcessingException extends RuntimeException {
    
    public FileProcessingException(String message) {
        super(message);
    }
    
    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
