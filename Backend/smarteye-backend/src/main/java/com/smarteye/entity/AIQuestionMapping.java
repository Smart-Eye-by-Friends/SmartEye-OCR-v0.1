package com.smarteye.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 결과와 문제 매핑 추적 엔티티
 * Python의 AI-문제 매핑 로직을 Java로 이식
 */
@Entity
@Table(name = "ai_question_mappings")
@EntityListeners(AuditingEntityListener.class)
public class AIQuestionMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "question_number", nullable = false)
    private String questionNumber;
    
    @Column(name = "ai_description", columnDefinition = "TEXT")
    private String aiDescription;
    
    @Column(name = "element_class")
    private String elementClass;        // figure, table, formula 등
    
    @Column(name = "element_id")
    private Long elementId;             // 연결된 LayoutBlock ID
    
    @Column(name = "distance_score")
    private Integer distanceScore;      // Y좌표 거리 (픽셀)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false)
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.UNKNOWN;
    
    @Column(name = "mapping_method")
    private String mappingMethod;       // distance_based, pattern_based, manual 등
    
    @Column(name = "x_coordinate")
    private Integer xCoordinate;        // AI 설명 대상의 X 좌표
    
    @Column(name = "y_coordinate")
    private Integer yCoordinate;        // AI 설명 대상의 Y 좌표
    
    @Column(name = "bbox_area")
    private Integer bboxArea;           // 바운딩 박스 면적
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cim_output_id", nullable = false)
    private CIMOutput cimOutput;
    
    public enum ConfidenceLevel {
        HIGH,      // 500px 이내, 명확한 매핑
        MEDIUM,    // 500-1000px, 합리적 매핑
        LOW,       // 1000px 이상, 불확실한 매핑
        UNKNOWN    // 매핑 실패 또는 미분류
    }
    
    // Constructors
    public AIQuestionMapping() {}
    
    public AIQuestionMapping(String questionNumber, String aiDescription) {
        this.questionNumber = questionNumber;
        this.aiDescription = aiDescription;
    }
    
    public AIQuestionMapping(String questionNumber, String aiDescription, CIMOutput cimOutput) {
        this.questionNumber = questionNumber;
        this.aiDescription = aiDescription;
        this.cimOutput = cimOutput;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(String questionNumber) { this.questionNumber = questionNumber; }
    
    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
    
    public String getElementClass() { return elementClass; }
    public void setElementClass(String elementClass) { this.elementClass = elementClass; }
    
    public Long getElementId() { return elementId; }
    public void setElementId(Long elementId) { this.elementId = elementId; }
    
    public Integer getDistanceScore() { return distanceScore; }
    public void setDistanceScore(Integer distanceScore) { 
        this.distanceScore = distanceScore;
        // 거리에 따른 신뢰도 자동 계산
        updateConfidenceLevelByDistance();
    }
    
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    
    public String getMappingMethod() { return mappingMethod; }
    public void setMappingMethod(String mappingMethod) { this.mappingMethod = mappingMethod; }
    
    public Integer getXCoordinate() { return xCoordinate; }
    public void setXCoordinate(Integer xCoordinate) { this.xCoordinate = xCoordinate; }
    
    public Integer getYCoordinate() { return yCoordinate; }
    public void setYCoordinate(Integer yCoordinate) { this.yCoordinate = yCoordinate; }
    
    public Integer getBboxArea() { return bboxArea; }
    public void setBboxArea(Integer bboxArea) { this.bboxArea = bboxArea; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public CIMOutput getCimOutput() { return cimOutput; }
    public void setCimOutput(CIMOutput cimOutput) { this.cimOutput = cimOutput; }
    
    // Helper methods
    private void updateConfidenceLevelByDistance() {
        if (distanceScore == null) {
            this.confidenceLevel = ConfidenceLevel.UNKNOWN;
            return;
        }
        
        // Python의 500px 임계값 기준
        if (distanceScore <= 500) {
            this.confidenceLevel = ConfidenceLevel.HIGH;
        } else if (distanceScore <= 1000) {
            this.confidenceLevel = ConfidenceLevel.MEDIUM;
        } else {
            this.confidenceLevel = ConfidenceLevel.LOW;
        }
    }
    
    public boolean isHighConfidence() {
        return confidenceLevel == ConfidenceLevel.HIGH;
    }
    
    public boolean isMediumOrHighConfidence() {
        return confidenceLevel == ConfidenceLevel.HIGH || confidenceLevel == ConfidenceLevel.MEDIUM;
    }
    
    public boolean hasValidMapping() {
        return distanceScore != null && confidenceLevel != ConfidenceLevel.UNKNOWN;
    }
    
    public boolean isImageElement() {
        return "figure".equals(elementClass) || "image".equals(elementClass);
    }
    
    public boolean isTableElement() {
        return "table".equals(elementClass);
    }
    
    public boolean isFormulaElement() {
        return "formula".equals(elementClass) || "isolated_formula".equals(elementClass);
    }
    
    public String getShortDescription() {
        if (aiDescription == null || aiDescription.length() <= 100) {
            return aiDescription;
        }
        return aiDescription.substring(0, 97) + "...";
    }
    
    public String getDisplayName() {
        return String.format("문제 %s - %s (%s)", 
            questionNumber, 
            elementClass != null ? elementClass : "unknown", 
            confidenceLevel.name());
    }
    
    /**
     * Python의 거리 계산 알고리즘과 동일한 점수 계산
     */
    public static int calculateDistance(int questionY, int elementY) {
        return Math.abs(questionY - elementY);
    }
    
    /**
     * 두 좌표 간의 유클리드 거리 계산
     */
    public static int calculateEuclideanDistance(int x1, int y1, int x2, int y2) {
        return (int) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }
    
    @Override
    public String toString() {
        return "AIQuestionMapping{" +
                "id=" + id +
                ", questionNumber='" + questionNumber + '\'' +
                ", elementClass='" + elementClass + '\'' +
                ", distanceScore=" + distanceScore +
                ", confidenceLevel=" + confidenceLevel +
                ", mappingMethod='" + mappingMethod + '\'' +
                ", coordinates=(" + xCoordinate + "," + yCoordinate + ")" +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AIQuestionMapping that = (AIQuestionMapping) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(questionNumber, that.questionNumber) &&
               Objects.equals(elementId, that.elementId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, questionNumber, elementId);
    }
}