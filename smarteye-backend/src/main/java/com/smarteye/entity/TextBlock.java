package com.smarteye.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "text_blocks")
@EntityListeners(AuditingEntityListener.class)
public class TextBlock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "extracted_text", columnDefinition = "TEXT", nullable = false)
    private String extractedText;
    
    @Column(name = "cleaned_text", columnDefinition = "TEXT")
    private String cleanedText;
    
    @Column(name = "word_count")
    private Integer wordCount;
    
    @Column(name = "char_count")
    private Integer charCount;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "language", length = 10)
    private String language = "kor";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "text_type")
    private TextType textType;
    
    @Column(name = "font_size")
    private Integer fontSize;
    
    @Column(name = "is_bold")
    private Boolean isBold = false;
    
    @Column(name = "is_italic")
    private Boolean isItalic = false;
    
    @Column(name = "text_angle")
    private Double textAngle = 0.0;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_block_id", nullable = false)
    private LayoutBlock layoutBlock;
    
    public enum TextType {
        TITLE,
        HEADING,
        PARAGRAPH,
        QUESTION,
        ANSWER,
        CAPTION,
        FOOTNOTE,
        LIST_ITEM,
        TABLE_CELL,
        FORMULA,
        OTHER
    }
    
    // Constructors
    public TextBlock() {}
    
    public TextBlock(String extractedText) {
        this.extractedText = extractedText;
        this.cleanedText = cleanText(extractedText);
        updateWordAndCharCount();
    }
    
    public TextBlock(String extractedText, Double confidence) {
        this.extractedText = extractedText;
        this.cleanedText = cleanText(extractedText);
        this.confidence = confidence;
        updateWordAndCharCount();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { 
        this.extractedText = extractedText; 
        this.cleanedText = cleanText(extractedText);
        updateWordAndCharCount();
    }
    
    public String getCleanedText() { return cleanedText; }
    public void setCleanedText(String cleanedText) { this.cleanedText = cleanedText; }
    
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public TextType getTextType() { return textType; }
    public void setTextType(TextType textType) { this.textType = textType; }
    
    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
    
    public Boolean getIsBold() { return isBold; }
    public void setIsBold(Boolean bold) { isBold = bold; }
    
    public Boolean getIsItalic() { return isItalic; }
    public void setIsItalic(Boolean italic) { isItalic = italic; }
    
    public Double getTextAngle() { return textAngle; }
    public void setTextAngle(Double textAngle) { this.textAngle = textAngle; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LayoutBlock getLayoutBlock() { return layoutBlock; }
    public void setLayoutBlock(LayoutBlock layoutBlock) { this.layoutBlock = layoutBlock; }
    
    // Helper methods
    private String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        return text
                .replaceAll("\\s+", " ")  // Multiple whitespace to single space
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "") // Control characters
                .trim();
    }
    
    private void updateWordAndCharCount() {
        if (cleanedText != null && !cleanedText.isEmpty()) {
            this.charCount = cleanedText.length();
            this.wordCount = cleanedText.split("\\s+").length;
        } else {
            this.charCount = 0;
            this.wordCount = 0;
        }
    }
    
    public void inferTextType() {
        if (layoutBlock == null || layoutBlock.getClassName() == null) {
            this.textType = TextType.OTHER;
            return;
        }
        
        String className = layoutBlock.getClassName().toLowerCase();
        
        if (className.contains("title")) {
            this.textType = TextType.TITLE;
        } else if (className.contains("question")) {
            this.textType = TextType.QUESTION;
        } else if (className.contains("caption")) {
            this.textType = TextType.CAPTION;
        } else if (className.contains("footnote")) {
            this.textType = TextType.FOOTNOTE;
        } else if (className.contains("list")) {
            this.textType = TextType.LIST_ITEM;
        } else if (className.contains("formula")) {
            this.textType = TextType.FORMULA;
        } else if (className.contains("table")) {
            this.textType = TextType.TABLE_CELL;
        } else if (className.contains("text") || className.contains("plain")) {
            this.textType = TextType.PARAGRAPH;
        } else {
            this.textType = TextType.OTHER;
        }
    }
    
    public boolean isEmpty() {
        return extractedText == null || extractedText.trim().isEmpty();
    }
    
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }
    
    public boolean isMediumConfidence() {
        return confidence != null && confidence >= 0.5 && confidence < 0.8;
    }
    
    public boolean isLowConfidence() {
        return confidence == null || confidence < 0.5;
    }
    
    @Override
    public String toString() {
        return "TextBlock{" +
                "id=" + id +
                ", extractedText='" + (extractedText != null && extractedText.length() > 50 ? 
                    extractedText.substring(0, 50) + "..." : extractedText) + '\'' +
                ", wordCount=" + wordCount +
                ", confidence=" + confidence +
                ", textType=" + textType +
                ", language='" + language + '\'' +
                '}';
    }
}