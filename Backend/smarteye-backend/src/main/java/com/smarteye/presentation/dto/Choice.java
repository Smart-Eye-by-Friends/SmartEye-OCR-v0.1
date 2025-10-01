package com.smarteye.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 선택지 DTO - 문제의 개별 선택지 정보
 */
public class Choice {
    
    @JsonProperty("choice_number")
    private String choiceNumber;
    
    @JsonProperty("choice_text")
    private String choiceText;
    
    private List<Integer> bbox;
    
    public Choice() {}
    
    public Choice(String choiceNumber, String choiceText, List<Integer> bbox) {
        this.choiceNumber = choiceNumber;
        this.choiceText = choiceText;
        this.bbox = bbox;
    }
    
    // Getters and Setters
    public String getChoiceNumber() {
        return choiceNumber;
    }
    
    public void setChoiceNumber(String choiceNumber) {
        this.choiceNumber = choiceNumber;
    }
    
    public String getChoiceText() {
        return choiceText;
    }
    
    public void setChoiceText(String choiceText) {
        this.choiceText = choiceText;
    }
    
    public List<Integer> getBbox() {
        return bbox;
    }
    
    public void setBbox(List<Integer> bbox) {
        this.bbox = bbox;
    }
    
    @Override
    public String toString() {
        return "Choice{" +
                "choiceNumber='" + choiceNumber + '\'' +
                ", choiceText='" + choiceText + '\'' +
                ", bbox=" + bbox +
                '}';
    }
}