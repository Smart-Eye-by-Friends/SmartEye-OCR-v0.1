package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 문제별 그룹화된 교육 문서 요소들 DTO
 * TSPM 분석으로 question_number를 기준으로 그룹화된 결과
 */
public class QuestionGroup {
    
    @JsonProperty("question_number")
    private String questionNumber;
    
    @JsonProperty("question_y_position")
    private int questionYPosition;
    
    @JsonProperty("section")
    private String section;
    
    @JsonProperty("elements")
    private QuestionElements elements;
    
    @JsonProperty("ai_analysis")
    private AIAnalysis aiAnalysis;
    
    @JsonProperty("spatial_info")
    private SpatialInfo spatialInfo;
    
    // 기본 생성자
    public QuestionGroup() {
        this.elements = new QuestionElements();
        this.aiAnalysis = new AIAnalysis();
        this.spatialInfo = new SpatialInfo();
    }
    
    // 생성자
    public QuestionGroup(String questionNumber, int questionYPosition) {
        this();
        this.questionNumber = questionNumber;
        this.questionYPosition = questionYPosition;
    }
    
    // Getters and Setters
    public String getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(String questionNumber) { this.questionNumber = questionNumber; }
    
    public int getQuestionYPosition() { return questionYPosition; }
    public void setQuestionYPosition(int questionYPosition) { this.questionYPosition = questionYPosition; }
    
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    
    public QuestionElements getElements() { return elements; }
    public void setElements(QuestionElements elements) { this.elements = elements; }
    
    public AIAnalysis getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(AIAnalysis aiAnalysis) { this.aiAnalysis = aiAnalysis; }
    
    public SpatialInfo getSpatialInfo() { return spatialInfo; }
    public void setSpatialInfo(SpatialInfo spatialInfo) { this.spatialInfo = spatialInfo; }
    
    /**
     * 문제별 교육 요소들
     */
    public static class QuestionElements {
        @JsonProperty("question_text")
        private List<EducationalElement> questionText;
        
        @JsonProperty("question_type")
        private List<EducationalElement> questionType;
        
        @JsonProperty("title")
        private List<EducationalElement> title;
        
        @JsonProperty("plain_text")
        private List<EducationalElement> plainText;
        
        @JsonProperty("list_items")
        private List<EducationalElement> listItems; // 선택지 후보
        
        @JsonProperty("figures")
        private List<EducationalElement> figures;
        
        @JsonProperty("tables")
        private List<EducationalElement> tables;
        
        @JsonProperty("formulas")
        private List<EducationalElement> formulas;
        
        // 기본 생성자
        public QuestionElements() {
            this.questionText = new ArrayList<>();
            this.questionType = new ArrayList<>();
            this.title = new ArrayList<>();
            this.plainText = new ArrayList<>();
            this.listItems = new ArrayList<>();
            this.figures = new ArrayList<>();
            this.tables = new ArrayList<>();
            this.formulas = new ArrayList<>();
        }
        
        // Getters and Setters
        public List<EducationalElement> getQuestionText() { return questionText; }
        public void setQuestionText(List<EducationalElement> questionText) { this.questionText = questionText; }
        
        public List<EducationalElement> getQuestionType() { return questionType; }
        public void setQuestionType(List<EducationalElement> questionType) { this.questionType = questionType; }
        
        public List<EducationalElement> getTitle() { return title; }
        public void setTitle(List<EducationalElement> title) { this.title = title; }
        
        public List<EducationalElement> getPlainText() { return plainText; }
        public void setPlainText(List<EducationalElement> plainText) { this.plainText = plainText; }
        
        public List<EducationalElement> getListItems() { return listItems; }
        public void setListItems(List<EducationalElement> listItems) { this.listItems = listItems; }
        
        public List<EducationalElement> getFigures() { return figures; }
        public void setFigures(List<EducationalElement> figures) { this.figures = figures; }
        
        public List<EducationalElement> getTables() { return tables; }
        public void setTables(List<EducationalElement> tables) { this.tables = tables; }
        
        public List<EducationalElement> getFormulas() { return formulas; }
        public void setFormulas(List<EducationalElement> formulas) { this.formulas = formulas; }
    }
    
    /**
     * AI 분석 정보
     */
    public static class AIAnalysis {
        @JsonProperty("image_descriptions")
        private List<String> imageDescriptions;
        
        @JsonProperty("table_analysis")
        private List<String> tableAnalysis;
        
        @JsonProperty("formula_descriptions")
        private List<String> formulaDescriptions;
        
        // 기본 생성자
        public AIAnalysis() {
            this.imageDescriptions = new ArrayList<>();
            this.tableAnalysis = new ArrayList<>();
            this.formulaDescriptions = new ArrayList<>();
        }
        
        // Getters and Setters
        public List<String> getImageDescriptions() { return imageDescriptions; }
        public void setImageDescriptions(List<String> imageDescriptions) { this.imageDescriptions = imageDescriptions; }
        
        public List<String> getTableAnalysis() { return tableAnalysis; }
        public void setTableAnalysis(List<String> tableAnalysis) { this.tableAnalysis = tableAnalysis; }
        
        public List<String> getFormulaDescriptions() { return formulaDescriptions; }
        public void setFormulaDescriptions(List<String> formulaDescriptions) { this.formulaDescriptions = formulaDescriptions; }
    }
    
    /**
     * 공간 배치 정보
     */
    public static class SpatialInfo {
        @JsonProperty("total_elements")
        private int totalElements;
        
        @JsonProperty("y_range")
        private int[] yRange = new int[2]; // [min_y, max_y]
        
        @JsonProperty("proximity_elements")
        private int proximityElements; // proximity 알고리즘으로 할당된 요소 수
        
        // 기본 생성자
        public SpatialInfo() {}
        
        // Getters and Setters
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
        
        public int[] getYRange() { return yRange; }
        public void setYRange(int[] yRange) { this.yRange = yRange; }
        
        public int getProximityElements() { return proximityElements; }
        public void setProximityElements(int proximityElements) { this.proximityElements = proximityElements; }
    }
    
    @Override
    public String toString() {
        return "QuestionGroup{" +
                "questionNumber='" + questionNumber + '\'' +
                ", questionYPosition=" + questionYPosition +
                ", section='" + section + '\'' +
                ", totalElements=" + (spatialInfo != null ? spatialInfo.totalElements : 0) +
                '}';
    }
}