package com.smarteye.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "책 요약 정보 DTO")
public class BookSummaryDto {
    
    @Schema(description = "책 ID", example = "1")
    private Long bookId;
    
    @Schema(description = "책 제목", example = "수학 문제집 1권")
    private String title;
    
    @Schema(description = "총 분석된 텍스트 블록 수", example = "150")
    private Integer totalTextBlocks;
    
    @Schema(description = "총 레이아웃 요소 수", example = "50")
    private Integer totalLayoutElements;
    
    @Schema(description = "추출된 전체 텍스트 길이", example = "5000")
    private Integer totalTextLength;
    
    @Schema(description = "레이아웃 요소 타입별 개수")
    private Map<String, Integer> layoutElementCounts;
    
    @Schema(description = "텍스트 블록 타입별 개수")
    private Map<String, Integer> textBlockCounts;
    
    @Schema(description = "페이지별 요약")
    private List<PageSummaryDto> pageSummaries;
    
    @Schema(description = "주요 키워드들")
    private List<String> keywords;
    
    @Schema(description = "전체 텍스트 (요약본)", example = "이 문서는 중학교 1학년 수학 문제집으로...")
    private String consolidatedText;
    
    @Schema(description = "AI 생성 전체 요약", example = "이 책은 총 50페이지로 구성된 수학 문제집입니다...")
    private String aiGeneratedSummary;
    
    @Schema(description = "평균 처리 시간 (ms/페이지)", example = "2500.0")
    private Double averageProcessingTimePerPage;
    
    @Schema(description = "품질 점수 (0-100)", example = "95.5")
    private Double qualityScore;
    
    // Constructors
    public BookSummaryDto() {}
    
    public BookSummaryDto(Long bookId, String title) {
        this.bookId = bookId;
        this.title = title;
    }
    
    // Getters and Setters
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public Integer getTotalTextBlocks() { return totalTextBlocks; }
    public void setTotalTextBlocks(Integer totalTextBlocks) { this.totalTextBlocks = totalTextBlocks; }
    
    public Integer getTotalLayoutElements() { return totalLayoutElements; }
    public void setTotalLayoutElements(Integer totalLayoutElements) { this.totalLayoutElements = totalLayoutElements; }
    
    public Integer getTotalTextLength() { return totalTextLength; }
    public void setTotalTextLength(Integer totalTextLength) { this.totalTextLength = totalTextLength; }
    
    public Map<String, Integer> getLayoutElementCounts() { return layoutElementCounts; }
    public void setLayoutElementCounts(Map<String, Integer> layoutElementCounts) { this.layoutElementCounts = layoutElementCounts; }
    
    public Map<String, Integer> getTextBlockCounts() { return textBlockCounts; }
    public void setTextBlockCounts(Map<String, Integer> textBlockCounts) { this.textBlockCounts = textBlockCounts; }
    
    public List<PageSummaryDto> getPageSummaries() { return pageSummaries; }
    public void setPageSummaries(List<PageSummaryDto> pageSummaries) { this.pageSummaries = pageSummaries; }
    
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    
    public String getConsolidatedText() { return consolidatedText; }
    public void setConsolidatedText(String consolidatedText) { this.consolidatedText = consolidatedText; }
    
    public String getAiGeneratedSummary() { return aiGeneratedSummary; }
    public void setAiGeneratedSummary(String aiGeneratedSummary) { this.aiGeneratedSummary = aiGeneratedSummary; }
    
    public Double getAverageProcessingTimePerPage() { return averageProcessingTimePerPage; }
    public void setAverageProcessingTimePerPage(Double averageProcessingTimePerPage) { this.averageProcessingTimePerPage = averageProcessingTimePerPage; }
    
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    
    @Override
    public String toString() {
        return "BookSummaryDto{" +
                "bookId=" + bookId +
                ", title='" + title + '\'' +
                ", totalTextBlocks=" + totalTextBlocks +
                ", totalLayoutElements=" + totalLayoutElements +
                ", totalTextLength=" + totalTextLength +
                ", qualityScore=" + qualityScore +
                '}';
    }
    
    // Nested DTO for page summaries
    @Schema(description = "페이지 요약 정보")
    public static class PageSummaryDto {
        
        @Schema(description = "페이지 번호", example = "1")
        private Integer pageNumber;
        
        @Schema(description = "페이지의 텍스트 블록 수", example = "15")
        private Integer textBlockCount;
        
        @Schema(description = "페이지의 레이아웃 요소 수", example = "5")
        private Integer layoutElementCount;
        
        @Schema(description = "페이지 처리 시간 (ms)", example = "2500")
        private Long processingTimeMs;
        
        @Schema(description = "페이지 요약 텍스트")
        private String summary;
        
        public PageSummaryDto() {}
        
        public PageSummaryDto(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }
        
        // Getters and Setters
        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
        
        public Integer getTextBlockCount() { return textBlockCount; }
        public void setTextBlockCount(Integer textBlockCount) { this.textBlockCount = textBlockCount; }
        
        public Integer getLayoutElementCount() { return layoutElementCount; }
        public void setLayoutElementCount(Integer layoutElementCount) { this.layoutElementCount = layoutElementCount; }
        
        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        
        @Override
        public String toString() {
            return "PageSummaryDto{" +
                    "pageNumber=" + pageNumber +
                    ", textBlockCount=" + textBlockCount +
                    ", layoutElementCount=" + layoutElementCount +
                    ", processingTimeMs=" + processingTimeMs +
                    '}';
        }
    }
}