package com.smarteye.exception;

/**
 * TSPM 분석 관련 예외
 */
public class TSPMAnalysisException extends RuntimeException {
    
    public TSPMAnalysisException(String message) {
        super(message);
    }
    
    public TSPMAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
