package com.smarteye.exception;

/**
 * 문서 분석 관련 예외
 */
public class DocumentAnalysisException extends RuntimeException {
    
    public DocumentAnalysisException(String message) {
        super(message);
    }
    
    public DocumentAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
