package com.smarteye.dto.common;

/**
 * 레이아웃 정보 공통 DTO
 * LAMServiceClient와 OCRService에서 공통으로 사용하는 레이아웃 정보
 */
public class LayoutInfo {
    private int id;
    private String className;
    private double confidence;
    private int[] box; // [x1, y1, x2, y2]
    private int width;
    private int height;
    private int area;
    
    public LayoutInfo() {}
    
    public LayoutInfo(int id, String className, double confidence, int[] box, int width, int height, int area) {
        this.id = id;
        this.className = className;
        this.confidence = confidence;
        this.box = box;
        this.width = width;
        this.height = height;
        this.area = area;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public int[] getBox() { return box; }
    public void setBox(int[] box) { this.box = box; }
    
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    
    public int getArea() { return area; }
    public void setArea(int area) { this.area = area; }
    
    @Override
    public String toString() {
        return "LayoutInfo{" +
                "id=" + id +
                ", className='" + className + '\'' +
                ", confidence=" + confidence +
                ", box=" + java.util.Arrays.toString(box) +
                ", area=" + area +
                '}';
    }
}