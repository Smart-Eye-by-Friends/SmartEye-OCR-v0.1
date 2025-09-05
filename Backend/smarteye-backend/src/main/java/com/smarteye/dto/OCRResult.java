package com.smarteye.dto;

/**
 * OCR 결과 DTO
 * Python api_server.py의 OCR 결과 구조와 동일
 */
public class OCRResult {
    
    private int id;
    private String className;
    private int[] coordinates; // [x1, y1, x2, y2]
    private String text;
    
    public OCRResult() {}
    
    public OCRResult(int id, String className, int[] coordinates, String text) {
        this.id = id;
        this.className = className;
        this.coordinates = coordinates;
        this.text = text;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public int[] getCoordinates() {
        return coordinates;
    }
    
    public void setCoordinates(int[] coordinates) {
        this.coordinates = coordinates;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    @Override
    public String toString() {
        return "OCRResult{" +
                "id=" + id +
                ", className='" + className + '\'' +
                ", coordinates=" + java.util.Arrays.toString(coordinates) +
                ", text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
                '}';
    }
}