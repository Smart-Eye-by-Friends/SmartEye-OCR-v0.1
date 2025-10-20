package com.smarteye.application.analysis.dto;

/**
 * 문제 경계 타입
 * 
 * "question number" 또는 "question type" 요소의 분류
 * 
 * @version 2.0 (순수 2D 거리 방식)
 * @since 2025-10-20
 */
public enum BoundaryType {
    /**
     * 문제 번호 (예: "001", "002", "1", "2")
     * LAM 클래스명: "question number" (띄어쓰기 형식)
     */
    QUESTION_NUMBER,
    
    /**
     * 문제 유형 (예: "유형 01", "Type A")
     * LAM 클래스명: "question type" (띄어쓰기 형식)
     */
    QUESTION_TYPE
}
