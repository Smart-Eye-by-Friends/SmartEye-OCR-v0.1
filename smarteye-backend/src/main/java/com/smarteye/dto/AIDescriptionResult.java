package com.smarteye.dto;

/**
 * AI 설명 결과 DTO
 * Python api_server.py의 AI 결과 구조와 동일
 */
public class AIDescriptionResult {
    
    private int id;
    private String className;
    private int[] coordinates; // [x1, y1, x2, y2]
    private String description;
    
    public AIDescriptionResult() {}
    
    public AIDescriptionResult(int id, String className, int[] coordinates, String description) {
        this.id = id;
        this.className = className;
        this.coordinates = coordinates;
        this.description = description;
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "AIDescriptionResult{" +
                "id=" + id +
                ", className='" + className + '\'' +
                ", coordinates=" + java.util.Arrays.toString(coordinates) +
                ", description='" + (description.length() > 100 ? description.substring(0, 100) + "..." : description) + '\'' +
                '}';
    }
}