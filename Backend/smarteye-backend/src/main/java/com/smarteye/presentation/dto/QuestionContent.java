package com.smarteye.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * 문제 내용 DTO - 각 문제의 세부 구성 요소들
 */
public class QuestionContent {
    
    @JsonProperty("main_question")
    private String mainQuestion;
    
    private String passage;
    
    private List<Choice> choices = new ArrayList<>();
    
    private List<ImageDescription> images = new ArrayList<>();
    
    private List<TableDescription> tables = new ArrayList<>();
    
    private String explanations;
    
    public QuestionContent() {}
    
    public QuestionContent(String mainQuestion, String passage, List<Choice> choices, 
                         List<ImageDescription> images, List<TableDescription> tables, String explanations) {
        this.mainQuestion = mainQuestion;
        this.passage = passage;
        this.choices = choices != null ? choices : new ArrayList<>();
        this.images = images != null ? images : new ArrayList<>();
        this.tables = tables != null ? tables : new ArrayList<>();
        this.explanations = explanations;
    }
    
    // Getters and Setters
    public String getMainQuestion() {
        return mainQuestion;
    }
    
    public void setMainQuestion(String mainQuestion) {
        this.mainQuestion = mainQuestion;
    }
    
    public String getPassage() {
        return passage;
    }
    
    public void setPassage(String passage) {
        this.passage = passage;
    }
    
    public List<Choice> getChoices() {
        return choices;
    }
    
    public void setChoices(List<Choice> choices) {
        this.choices = choices != null ? choices : new ArrayList<>();
    }
    
    public List<ImageDescription> getImages() {
        return images;
    }
    
    public void setImages(List<ImageDescription> images) {
        this.images = images != null ? images : new ArrayList<>();
    }
    
    public List<TableDescription> getTables() {
        return tables;
    }
    
    public void setTables(List<TableDescription> tables) {
        this.tables = tables != null ? tables : new ArrayList<>();
    }
    
    public String getExplanations() {
        return explanations;
    }
    
    public void setExplanations(String explanations) {
        this.explanations = explanations;
    }
    
    @Override
    public String toString() {
        return "QuestionContent{" +
                "mainQuestion='" + mainQuestion + '\'' +
                ", passage='" + passage + '\'' +
                ", choices=" + choices +
                ", images=" + images +
                ", tables=" + tables +
                ", explanations='" + explanations + '\'' +
                '}';
    }
}