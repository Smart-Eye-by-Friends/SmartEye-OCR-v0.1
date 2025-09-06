package com.smarteye.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 문제별 구조 정보 저장 엔티티
 * Python의 구조화 분석 결과를 문제별로 저장
 */
@Entity
@Table(name = "question_structures")
@EntityListeners(AuditingEntityListener.class)
public class QuestionStructure {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "question_number", nullable = false)
    private String questionNumber;
    
    @Column(name = "section_name")
    private String sectionName;
    
    @Column(name = "start_y_position")
    private Integer startY;
    
    @Column(name = "end_y_position")
    private Integer endY;
    
    @Column(name = "elements_json", columnDefinition = "TEXT")
    private String elementsJson;        // 6개 카테고리 요소들
    
    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;        // 문제 텍스트
    
    @Column(name = "choices_count")
    private Integer choicesCount;       // 선택지 개수
    
    @Column(name = "images_count")
    private Integer imagesCount;        // 이미지 개수
    
    @Column(name = "tables_count")
    private Integer tablesCount;        // 테이블 개수
    
    @Column(name = "confidence_score")
    private Double confidenceScore;     // 구조 분석 신뢰도
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cim_output_id", nullable = false)
    private CIMOutput cimOutput;
    
    // Constructors
    public QuestionStructure() {}
    
    public QuestionStructure(String questionNumber, String sectionName) {
        this.questionNumber = questionNumber;
        this.sectionName = sectionName;
    }
    
    public QuestionStructure(String questionNumber, String sectionName, CIMOutput cimOutput) {
        this.questionNumber = questionNumber;
        this.sectionName = sectionName;
        this.cimOutput = cimOutput;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(String questionNumber) { this.questionNumber = questionNumber; }
    
    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    
    public Integer getStartY() { return startY; }
    public void setStartY(Integer startY) { this.startY = startY; }
    
    public Integer getEndY() { return endY; }
    public void setEndY(Integer endY) { this.endY = endY; }
    
    public String getElementsJson() { return elementsJson; }
    public void setElementsJson(String elementsJson) { this.elementsJson = elementsJson; }
    
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    
    public Integer getChoicesCount() { return choicesCount; }
    public void setChoicesCount(Integer choicesCount) { this.choicesCount = choicesCount; }
    
    public Integer getImagesCount() { return imagesCount; }
    public void setImagesCount(Integer imagesCount) { this.imagesCount = imagesCount; }
    
    public Integer getTablesCount() { return tablesCount; }
    public void setTablesCount(Integer tablesCount) { this.tablesCount = tablesCount; }
    
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public CIMOutput getCimOutput() { return cimOutput; }
    public void setCimOutput(CIMOutput cimOutput) { this.cimOutput = cimOutput; }
    
    // 편의 메서드
    @SuppressWarnings("unchecked")
    public Map<String, Object> getElementsMap() {
        if (elementsJson == null || elementsJson.trim().isEmpty()) {
            return Map.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(elementsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
    
    public void setElementsMap(Map<String, Object> elementsMap) {
        if (elementsMap == null || elementsMap.isEmpty()) {
            this.elementsJson = null;
        } else {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.elementsJson = mapper.writeValueAsString(elementsMap);
            } catch (Exception e) {
                this.elementsJson = null;
            }
        }
    }
    
    // Helper methods
    public boolean hasChoices() {
        return choicesCount != null && choicesCount > 0;
    }
    
    public boolean hasImages() {
        return imagesCount != null && imagesCount > 0;
    }
    
    public boolean hasTables() {
        return tablesCount != null && tablesCount > 0;
    }
    
    public boolean hasSection() {
        return sectionName != null && !sectionName.trim().isEmpty() && !"기본".equals(sectionName);
    }
    
    public int getYRange() {
        if (startY == null || endY == null) return 0;
        return Math.max(0, endY - startY);
    }
    
    public boolean isValidRange() {
        return startY != null && endY != null && endY > startY;
    }
    
    public String getDisplayName() {
        if (hasSection()) {
            return sectionName + " - " + questionNumber + "번";
        }
        return questionNumber + "번";
    }
    
    @Override
    public String toString() {
        return "QuestionStructure{" +
                "id=" + id +
                ", questionNumber='" + questionNumber + '\'' +
                ", sectionName='" + sectionName + '\'' +
                ", startY=" + startY +
                ", endY=" + endY +
                ", choicesCount=" + choicesCount +
                ", imagesCount=" + imagesCount +
                ", tablesCount=" + tablesCount +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionStructure that = (QuestionStructure) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(questionNumber, that.questionNumber);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, questionNumber);
    }
}