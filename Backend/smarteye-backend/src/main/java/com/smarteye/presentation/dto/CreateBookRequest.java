package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "새 책 생성 요청")
public class CreateBookRequest {
    
    @NotBlank(message = "책 제목은 필수입니다")
    @Size(max = 255, message = "책 제목은 255자를 초과할 수 없습니다")
    @Schema(description = "책 제목", example = "수학 문제집 1권", required = true)
    private String title;
    
    @Size(max = 1000, message = "책 설명은 1000자를 초과할 수 없습니다")
    @Schema(description = "책 설명", example = "중학교 1학년 수학 문제집입니다.")
    private String description;
    
    @Schema(description = "사용자 ID (인증된 사용자는 생략 가능)", example = "1")
    private Long userId;
    
    // Constructors
    public CreateBookRequest() {}
    
    public CreateBookRequest(String title) {
        this.title = title;
    }
    
    public CreateBookRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }
    
    public CreateBookRequest(String title, String description, Long userId) {
        this.title = title;
        this.description = description;
        this.userId = userId;
    }
    
    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    @Override
    public String toString() {
        return "CreateBookRequest{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", userId=" + userId +
                '}';
    }
}