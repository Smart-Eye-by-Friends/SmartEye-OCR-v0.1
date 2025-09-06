package com.smarteye.exception;

/**
 * CIM 생성 과정에서 발생하는 예외
 * Python의 구조화 분석 과정에서 발생할 수 있는 오류들을 처리
 */
public class CIMGenerationException extends RuntimeException {
    
    private String errorCode;
    private Object[] params;
    
    public CIMGenerationException(String message) {
        super(message);
    }
    
    public CIMGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CIMGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public CIMGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public CIMGenerationException(String errorCode, String message, Object... params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object[] getParams() {
        return params;
    }
    
    // 자주 사용되는 정적 팩토리 메서드들
    
    public static CIMGenerationException analysisJobNotFound(Long jobId) {
        return new CIMGenerationException("ANALYSIS_JOB_NOT_FOUND", 
            "분석 작업을 찾을 수 없습니다: " + jobId, jobId);
    }
    
    public static CIMGenerationException noLayoutBlocks(Long jobId) {
        return new CIMGenerationException("NO_LAYOUT_BLOCKS", 
            "레이아웃 블록이 없습니다: " + jobId, jobId);
    }
    
    public static CIMGenerationException noTextBlocks(Long jobId) {
        return new CIMGenerationException("NO_TEXT_BLOCKS", 
            "텍스트 블록이 없습니다: " + jobId, jobId);
    }
    
    public static CIMGenerationException noQuestionNumbers() {
        return new CIMGenerationException("NO_QUESTION_NUMBERS", 
            "문제 번호를 찾을 수 없습니다");
    }
    
    public static CIMGenerationException invalidQuestionStructure(String questionNumber) {
        return new CIMGenerationException("INVALID_QUESTION_STRUCTURE", 
            "유효하지 않은 문제 구조: " + questionNumber, questionNumber);
    }
    
    public static CIMGenerationException jsonProcessingError(Throwable cause) {
        return new CIMGenerationException("JSON_PROCESSING_ERROR", 
            "JSON 처리 중 오류가 발생했습니다", cause);
    }
    
    public static CIMGenerationException databaseError(Throwable cause) {
        return new CIMGenerationException("DATABASE_ERROR", 
            "데이터베이스 처리 중 오류가 발생했습니다", cause);
    }
    
    public static CIMGenerationException patternMatchingError(String pattern) {
        return new CIMGenerationException("PATTERN_MATCHING_ERROR", 
            "패턴 매칭 실패: " + pattern, pattern);
    }
    
    public static CIMGenerationException integrationError(String message) {
        return new CIMGenerationException("INTEGRATION_ERROR", 
            "다중 페이지 통합 오류: " + message);
    }
}