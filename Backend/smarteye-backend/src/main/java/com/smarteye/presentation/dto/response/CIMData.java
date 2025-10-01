package com.smarteye.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * React-friendly CIM 데이터 DTO
 *
 * 기존 Map<String, Object> cimData를 타입 안전한 구조로 대체
 * React 컴포넌트에서 직접 접근 가능한 명시적 필드 구조
 */
@Schema(description = "타입 안전한 CIM 분석 데이터")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CIMData {

    @Schema(description = "구조화된 섹션 목록")
    @JsonProperty("sections")
    private final List<CIMSection> sections;

    @Schema(description = "레이아웃 블록 목록")
    @JsonProperty("layoutBlocks")
    private final List<CIMLayoutBlock> layoutBlocks;

    @Schema(description = "텍스트 블록 목록")
    @JsonProperty("textBlocks")
    private final List<CIMTextBlock> textBlocks;

    @Schema(description = "문제 구조 정보")
    @JsonProperty("problemStructure")
    private final CIMProblemStructure problemStructure;

    @Schema(description = "메타데이터")
    @JsonProperty("metadata")
    private final CIMMetadata metadata;

    // 생성자
    public CIMData(List<CIMSection> sections,
                  List<CIMLayoutBlock> layoutBlocks,
                  List<CIMTextBlock> textBlocks,
                  CIMProblemStructure problemStructure,
                  CIMMetadata metadata) {
        this.sections = sections;
        this.layoutBlocks = layoutBlocks;
        this.textBlocks = textBlocks;
        this.problemStructure = problemStructure;
        this.metadata = metadata;
    }

    // Builder 패턴 지원
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<CIMSection> sections;
        private List<CIMLayoutBlock> layoutBlocks;
        private List<CIMTextBlock> textBlocks;
        private CIMProblemStructure problemStructure;
        private CIMMetadata metadata;

        public Builder sections(List<CIMSection> sections) {
            this.sections = sections;
            return this;
        }

        public Builder layoutBlocks(List<CIMLayoutBlock> layoutBlocks) {
            this.layoutBlocks = layoutBlocks;
            return this;
        }

        public Builder textBlocks(List<CIMTextBlock> textBlocks) {
            this.textBlocks = textBlocks;
            return this;
        }

        public Builder problemStructure(CIMProblemStructure problemStructure) {
            this.problemStructure = problemStructure;
            return this;
        }

        public Builder metadata(CIMMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public CIMData build() {
            return new CIMData(sections, layoutBlocks, textBlocks,
                             problemStructure, metadata);
        }
    }

    // Getters
    public List<CIMSection> getSections() { return sections; }
    public List<CIMLayoutBlock> getLayoutBlocks() { return layoutBlocks; }
    public List<CIMTextBlock> getTextBlocks() { return textBlocks; }
    public CIMProblemStructure getProblemStructure() { return problemStructure; }
    public CIMMetadata getMetadata() { return metadata; }

    // React 메모이제이션을 위한 equals/hashCode
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CIMData cimData = (CIMData) obj;
        return Objects.equals(sections, cimData.sections) &&
               Objects.equals(layoutBlocks, cimData.layoutBlocks) &&
               Objects.equals(textBlocks, cimData.textBlocks) &&
               Objects.equals(problemStructure, cimData.problemStructure) &&
               Objects.equals(metadata, cimData.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sections, layoutBlocks, textBlocks,
                          problemStructure, metadata);
    }

    @Override
    public String toString() {
        return "CIMData{" +
               "sections=" + (sections != null ? sections.size() : 0) +
               ", layoutBlocks=" + (layoutBlocks != null ? layoutBlocks.size() : 0) +
               ", textBlocks=" + (textBlocks != null ? textBlocks.size() : 0) +
               '}';
    }

    // 내부 DTO 클래스들

    /**
     * CIM 섹션 정보
     */
    @Schema(description = "CIM 섹션 정보")
    public static class CIMSection {
        @JsonProperty("id")
        private final String id;

        @JsonProperty("title")
        private final String title;

        @JsonProperty("type")
        private final String type;

        @JsonProperty("content")
        private final String content;

        @JsonProperty("order")
        private final int order;

        public CIMSection(String id, String title, String type, String content, int order) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.content = content;
            this.order = order;
        }

        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getContent() { return content; }
        public int getOrder() { return order; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CIMSection that = (CIMSection) obj;
            return order == that.order &&
                   Objects.equals(id, that.id) &&
                   Objects.equals(title, that.title) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, type, content, order);
        }
    }

    /**
     * CIM 레이아웃 블록 정보
     */
    @Schema(description = "CIM 레이아웃 블록 정보")
    public static class CIMLayoutBlock {
        @JsonProperty("id")
        private final String id;

        @JsonProperty("type")
        private final String type;

        @JsonProperty("bbox")
        private final BoundingBox bbox;

        @JsonProperty("confidence")
        private final double confidence;

        public CIMLayoutBlock(String id, String type, BoundingBox bbox, double confidence) {
            this.id = id;
            this.type = type;
            this.bbox = bbox;
            this.confidence = confidence;
        }

        // Getters
        public String getId() { return id; }
        public String getType() { return type; }
        public BoundingBox getBbox() { return bbox; }
        public double getConfidence() { return confidence; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CIMLayoutBlock that = (CIMLayoutBlock) obj;
            return Double.compare(that.confidence, confidence) == 0 &&
                   Objects.equals(id, that.id) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(bbox, that.bbox);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type, bbox, confidence);
        }
    }

    /**
     * CIM 텍스트 블록 정보
     */
    @Schema(description = "CIM 텍스트 블록 정보")
    public static class CIMTextBlock {
        @JsonProperty("id")
        private final String id;

        @JsonProperty("text")
        private final String text;

        @JsonProperty("type")
        private final String type;

        @JsonProperty("bbox")
        private final BoundingBox bbox;

        @JsonProperty("confidence")
        private final double confidence;

        public CIMTextBlock(String id, String text, String type, BoundingBox bbox, double confidence) {
            this.id = id;
            this.text = text;
            this.type = type;
            this.bbox = bbox;
            this.confidence = confidence;
        }

        // Getters
        public String getId() { return id; }
        public String getText() { return text; }
        public String getType() { return type; }
        public BoundingBox getBbox() { return bbox; }
        public double getConfidence() { return confidence; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CIMTextBlock that = (CIMTextBlock) obj;
            return Double.compare(that.confidence, confidence) == 0 &&
                   Objects.equals(id, that.id) &&
                   Objects.equals(text, that.text) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(bbox, that.bbox);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, text, type, bbox, confidence);
        }
    }

    /**
     * 문제 구조 정보
     */
    @Schema(description = "문제 구조 정보")
    public static class CIMProblemStructure {
        @JsonProperty("totalProblems")
        private final int totalProblems;

        @JsonProperty("problems")
        private final List<Problem> problems;

        public CIMProblemStructure(int totalProblems, List<Problem> problems) {
            this.totalProblems = totalProblems;
            this.problems = problems;
        }

        // Getters
        public int getTotalProblems() { return totalProblems; }
        public List<Problem> getProblems() { return problems; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CIMProblemStructure that = (CIMProblemStructure) obj;
            return totalProblems == that.totalProblems &&
                   Objects.equals(problems, that.problems);
        }

        @Override
        public int hashCode() {
            return Objects.hash(totalProblems, problems);
        }

        public static class Problem {
            @JsonProperty("number")
            private final int number;

            @JsonProperty("question")
            private final String question;

            @JsonProperty("choices")
            private final List<String> choices;

            @JsonProperty("answer")
            private final String answer;

            public Problem(int number, String question, List<String> choices, String answer) {
                this.number = number;
                this.question = question;
                this.choices = choices;
                this.answer = answer;
            }

            // Getters
            public int getNumber() { return number; }
            public String getQuestion() { return question; }
            public List<String> getChoices() { return choices; }
            public String getAnswer() { return answer; }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                Problem problem = (Problem) obj;
                return number == problem.number &&
                       Objects.equals(question, problem.question) &&
                       Objects.equals(choices, problem.choices) &&
                       Objects.equals(answer, problem.answer);
            }

            @Override
            public int hashCode() {
                return Objects.hash(number, question, choices, answer);
            }
        }
    }

    /**
     * CIM 메타데이터
     */
    @Schema(description = "CIM 메타데이터")
    public static class CIMMetadata {
        @JsonProperty("version")
        private final String version;

        @JsonProperty("model")
        private final String model;

        @JsonProperty("createdAt")
        private final long createdAt;

        public CIMMetadata(String version, String model, long createdAt) {
            this.version = version;
            this.model = model;
            this.createdAt = createdAt;
        }

        // Getters
        public String getVersion() { return version; }
        public String getModel() { return model; }
        public long getCreatedAt() { return createdAt; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CIMMetadata that = (CIMMetadata) obj;
            return createdAt == that.createdAt &&
                   Objects.equals(version, that.version) &&
                   Objects.equals(model, that.model);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, model, createdAt);
        }
    }

    /**
     * 바운딩 박스 좌표
     */
    @Schema(description = "바운딩 박스 좌표")
    public static class BoundingBox {
        @JsonProperty("x")
        private final int x;

        @JsonProperty("y")
        private final int y;

        @JsonProperty("width")
        private final int width;

        @JsonProperty("height")
        private final int height;

        public BoundingBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        // Getters
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BoundingBox that = (BoundingBox) obj;
            return x == that.x && y == that.y &&
                   width == that.width && height == that.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, width, height);
        }
    }
}