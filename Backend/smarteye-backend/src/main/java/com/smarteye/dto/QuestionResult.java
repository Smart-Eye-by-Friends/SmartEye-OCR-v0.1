package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 문제 결과 DTO - 개별 문제의 완전한 분석 결과
 */
public class QuestionResult {
    
    @JsonProperty("question_number")
    private String questionNumber;
    
    private String section;
    
    @JsonProperty("question_content")
    private QuestionContent questionContent;
    
    @JsonProperty("ai_analysis")
    private AIAnalysis aiAnalysis;
    
    public QuestionResult() {}
    
    public QuestionResult(String questionNumber, String section, QuestionContent questionContent, AIAnalysis aiAnalysis) {
        this.questionNumber = questionNumber;
        this.section = section;
        this.questionContent = questionContent;
        this.aiAnalysis = aiAnalysis;
    }
    
    // Getters and Setters
    public String getQuestionNumber() {
        return questionNumber;
    }
    
    public void setQuestionNumber(String questionNumber) {
        this.questionNumber = questionNumber;
    }
    
    public String getSection() {
        return section;
    }
    
    public void setSection(String section) {
        this.section = section;
    }
    
    public QuestionContent getQuestionContent() {
        return questionContent;
    }
    
    public void setQuestionContent(QuestionContent questionContent) {
        this.questionContent = questionContent;
    }
    
    public AIAnalysis getAiAnalysis() {
        return aiAnalysis;
    }
    
    public void setAiAnalysis(AIAnalysis aiAnalysis) {
        this.aiAnalysis = aiAnalysis;
    }
    
    @Override
    public String toString() {
        return "QuestionResult{" +
                "questionNumber='" + questionNumber + '\'' +
                ", section='" + section + '\'' +
                ", questionContent=" + questionContent +
                ", aiAnalysis=" + aiAnalysis +
                '}';
    }
}