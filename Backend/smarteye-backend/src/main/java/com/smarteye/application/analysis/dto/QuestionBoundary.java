package com.smarteye.application.analysis.dto;

/**
 * 문제 경계 정보
 * 
 * "question number" 또는 "question type" 요소의 위치 및 메타데이터
 * 
 * ⚠️ LAM data.yaml 형식: "question number", "question type" (띄어쓰기)
 * ⚠️ v2.0: columnIndex 제거 (컬럼 감지 불필요, 순수 2D 거리 방식)
 * 
 * @version 2.0 (순수 2D 거리 방식)
 * @since 2025-10-20
 */
public class QuestionBoundary {
    
    /** 문제 식별자 (OCR 텍스트 그대로: "001", "유형 01", etc.) */
    private String identifier;
    
    /** 경계 타입 (QUESTION_NUMBER 또는 QUESTION_TYPE) */
    private BoundaryType type;
    
    /** 좌측 X좌표 (px) */
    private int x;
    
    /** 상단 Y좌표 (px) */
    private int y;
    
    /** 너비 (px) */
    private int width;
    
    /** 높이 (px) */
    private int height;
    
    /** 원본 OCR 텍스트 (정제 전) */
    private String ocrText;
    
    /** LAM 신뢰도 (0.0 ~ 1.0) - 참고용 */
    private double lamConfidence;
    
    /** LAM 요소 ID */
    private int elementId;
    
    // Constructors
    
    public QuestionBoundary() {}
    
    public QuestionBoundary(String identifier, BoundaryType type, int x, int y, 
                           int width, int height, String ocrText, double lamConfidence, int elementId) {
        this.identifier = identifier;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.ocrText = ocrText;
        this.lamConfidence = lamConfidence;
        this.elementId = elementId;
    }
    
    // Getters and Setters
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public BoundaryType getType() {
        return type;
    }
    
    public void setType(BoundaryType type) {
        this.type = type;
    }
    
    public int getX() {
        return x;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public int getY() {
        return y;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public String getOcrText() {
        return ocrText;
    }
    
    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }
    
    public double getLamConfidence() {
        return lamConfidence;
    }
    
    public void setLamConfidence(double lamConfidence) {
        this.lamConfidence = lamConfidence;
    }
    
    public int getElementId() {
        return elementId;
    }
    
    public void setElementId(int elementId) {
        this.elementId = elementId;
    }
    
    // Utility methods
    
    /**
     * 경계의 중심 X좌표 계산
     */
    public int getCenterX() {
        return x + width / 2;
    }
    
    /**
     * 경계의 중심 Y좌표 계산
     */
    public int getCenterY() {
        return y + height / 2;
    }
    
    /**
     * 경계의 우측 X좌표 계산
     */
    public int getRightX() {
        return x + width;
    }
    
    /**
     * 경계의 하단 Y좌표 계산
     */
    public int getBottomY() {
        return y + height;
    }
    
    @Override
    public String toString() {
        return String.format("QuestionBoundary{id='%s', type=%s, x=%d, y=%d, width=%d, height=%d, ocrText='%s', conf=%.2f}",
                           identifier, type, x, y, width, height, ocrText, lamConfidence);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        QuestionBoundary that = (QuestionBoundary) o;
        
        if (x != that.x) return false;
        if (y != that.y) return false;
        if (width != that.width) return false;
        if (height != that.height) return false;
        if (Double.compare(that.lamConfidence, lamConfidence) != 0) return false;
        if (elementId != that.elementId) return false;
        if (identifier != null ? !identifier.equals(that.identifier) : that.identifier != null) return false;
        if (type != that.type) return false;
        return ocrText != null ? ocrText.equals(that.ocrText) : that.ocrText == null;
    }
    
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + (ocrText != null ? ocrText.hashCode() : 0);
        temp = Double.doubleToLongBits(lamConfidence);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + elementId;
        return result;
    }
}
