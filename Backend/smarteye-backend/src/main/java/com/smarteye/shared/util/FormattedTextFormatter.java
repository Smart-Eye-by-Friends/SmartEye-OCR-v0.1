package com.smarteye.shared.util;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.QuestionData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FormattedText 다단 레이아웃 지원을 위한 포맷터 클래스.
 *
 * <p>이 클래스는 UnifiedAnalysisEngine의 StructuredData를 입력받아
 * 다단 레이아웃을 올바르게 처리한 FormattedText를 생성합니다.</p>
 *
 * <h2>핵심 기능</h2>
 * <ul>
 *   <li>다단 레이아웃 자동 감지 및 처리 (2단, 3단 이상)</li>
 *   <li>컬럼별 문제 정렬 (왼쪽 → 오른쪽 컬럼 순회)</li>
 *   <li>XSS 방지 HTML 이스케이프 처리</li>
 *   <li>단일 컬럼 문서 하위 호환성 보장</li>
 * </ul>
 *
 * <h2>사용 예제</h2>
 * <pre>{@code
 * StructuredData structuredData = unifiedAnalysisEngine.performUnifiedAnalysis(...).getStructuredData();
 * String formattedText = FormattedTextFormatter.format(structuredData);
 * }</pre>
 *
 * @author SmartEye Development Team
 * @version 1.0 (Phase 0 - Day 1)
 * @since 2025-10-03
 */
public class FormattedTextFormatter {

    private static final Logger logger = LoggerFactory.getLogger(FormattedTextFormatter.class);

    /**
     * 컬럼 감지를 위한 Gap 임계값 (페이지 너비 대비 비율).
     * 두 문제의 X좌표 차이가 이 비율을 초과하면 새로운 컬럼으로 간주합니다.
     */
    private static final double COLUMN_GAP_THRESHOLD_RATIO = 0.1; // 10%

    /**
     * 기본 페이지 너비 (픽셀).
     * 실제 이미지 너비를 알 수 없는 경우 사용됩니다.
     */
    private static final int DEFAULT_PAGE_WIDTH = 2000;

    /**
     * 컬럼 정보를 담는 내부 클래스.
     *
     * <p>각 컬럼의 인덱스, X좌표 범위, 너비 정보를 포함합니다.</p>
     */
    private static class ColumnInfo {
        private final int columnIndex;
        private final int startX;
        private final int endX;

        /**
         * ColumnInfo 생성자.
         *
         * @param columnIndex 컬럼 인덱스 (0-based)
         * @param startX 컬럼 시작 X좌표
         * @param endX 컬럼 종료 X좌표
         */
        public ColumnInfo(int columnIndex, int startX, int endX) {
            this.columnIndex = columnIndex;
            this.startX = startX;
            this.endX = endX;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        public int getStartX() {
            return startX;
        }

        public int getEndX() {
            return endX;
        }

        /**
         * 주어진 X좌표가 이 컬럼 범위에 포함되는지 확인합니다.
         *
         * @param x 확인할 X좌표
         * @return 포함되면 true, 아니면 false
         */
        public boolean contains(int x) {
            return x >= startX && x < endX;
        }

        /**
         * 컬럼의 너비를 반환합니다.
         *
         * @return 컬럼 너비 (픽셀)
         */
        public int getWidth() {
            return endX - startX;
        }

        @Override
        public String toString() {
            return String.format("Column %d [%d ~ %d] (width: %dpx)",
                    columnIndex, startX, endX, getWidth());
        }
    }

    /**
     * StructuredData를 입력받아 다단 레이아웃을 지원하는 FormattedText를 생성합니다.
     *
     * <p>이 메서드는 다음 단계로 처리됩니다:</p>
     * <ol>
     *   <li>컬럼 정보 추출 (Gap Detection 알고리즘)</li>
     *   <li>문제를 컬럼별로 그룹핑</li>
     *   <li>각 컬럼 내 문제들을 Y좌표 순으로 정렬</li>
     *   <li>포맷팅 규칙 적용 및 HTML 이스케이프</li>
     * </ol>
     *
     * @param structuredData UnifiedAnalysisEngine에서 생성된 구조화 데이터
     * @return 다단 레이아웃이 올바르게 반영된 FormattedText (HTML-safe)
     * @throws IllegalArgumentException structuredData가 null이거나 문제가 없는 경우
     */
    public static String format(StructuredData structuredData) {
        if (structuredData == null) {
            throw new IllegalArgumentException("StructuredData는 null일 수 없습니다.");
        }

        List<QuestionData> questions = structuredData.getQuestions();
        if (questions == null || questions.isEmpty()) {
            logger.warn("⚠️ 문제 데이터가 없습니다.");
            return "분석된 문제가 없습니다.";
        }

        logger.info("📋 FormattedText 생성 시작 - 총 {}개 문제", questions.size());

        // Step 1: 컬럼 정보 추출
        List<ColumnInfo> columns = extractColumnInformation(structuredData);
        logger.info("📊 감지된 컬럼 수: {}", columns.size());
        columns.forEach(col -> logger.debug("  - {}", col));

        // Step 2: 컬럼별 문제 그룹핑
        Map<Integer, List<QuestionData>> columnGroups = groupQuestionsByColumn(questions, columns);

        // Step 3: FormattedText 생성
        StringBuilder formattedText = new StringBuilder();
        formattedText.append("=== 분석 결과 ===\n\n");

        // 컬럼별로 순회 (왼쪽 → 오른쪽)
        for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
            List<QuestionData> questionsInColumn = columnGroups.get(colIdx);

            if (questionsInColumn == null || questionsInColumn.isEmpty()) {
                continue;
            }

            if (columns.size() > 1) {
                formattedText.append(String.format("--- 컬럼 %d ---\n\n", colIdx + 1));
            }

            // 컬럼 내 문제들 처리
            for (QuestionData question : questionsInColumn) {
                String questionText = formatQuestion(question);
                formattedText.append(questionText);
            }
        }

        logger.info("✅ FormattedText 생성 완료 - {} 문자", formattedText.length());
        return formattedText.toString();
    }

    /**
     * StructuredData에서 컬럼 정보를 추출합니다.
     *
     * <p>Gap Detection 알고리즘을 사용하여 문제들의 X좌표 분포를 분석하고,
     * 컬럼 경계를 자동으로 감지합니다.</p>
     *
     * <h3>알고리즘 개요</h3>
     * <ol>
     *   <li>모든 문제의 X좌표 수집 및 정렬</li>
     *   <li>인접한 좌표 간 Gap 계산</li>
     *   <li>Gap이 임계값을 초과하는 지점을 컬럼 경계로 설정</li>
     *   <li>ColumnInfo 객체 리스트 생성</li>
     * </ol>
     *
     * @param data StructuredData 객체
     * @return 감지된 컬럼 정보 리스트 (최소 1개)
     */
    private static List<ColumnInfo> extractColumnInformation(StructuredData data) {
        List<QuestionData> questions = data.getQuestions();

        // Step 1: 모든 문제의 X좌표 수집
        List<Integer> xCoordinates = questions.stream()
                .map(FormattedTextFormatter::getQuestionXCoordinate)
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        logger.debug("📍 수집된 X좌표: {}", xCoordinates);

        if (xCoordinates.isEmpty()) {
            logger.warn("⚠️ X좌표를 찾을 수 없음 - 단일 컬럼으로 처리");
            return Collections.singletonList(new ColumnInfo(0, 0, DEFAULT_PAGE_WIDTH));
        }

        if (xCoordinates.size() == 1) {
            logger.info("ℹ️ 단일 X좌표 감지 - 단일 컬럼 문서");
            return Collections.singletonList(new ColumnInfo(0, 0, DEFAULT_PAGE_WIDTH));
        }

        // Step 2: Gap Detection으로 컬럼 경계 찾기
        List<Integer> columnBoundaries = findColumnBoundaries(xCoordinates);

        // Step 3: ColumnInfo 객체 생성
        List<ColumnInfo> columns = new ArrayList<>();
        for (int i = 0; i < columnBoundaries.size() - 1; i++) {
            int startX = columnBoundaries.get(i);
            int endX = columnBoundaries.get(i + 1);
            columns.add(new ColumnInfo(i, startX, endX));
        }

        logger.info("✅ 컬럼 감지 완료 - {}단 레이아웃", columns.size());
        return columns;
    }

    /**
     * Gap Detection 알고리즘으로 컬럼 경계를 찾습니다.
     *
     * <p>X좌표 리스트에서 인접 좌표 간 Gap을 계산하고,
     * 임계값을 초과하는 Gap을 컬럼 경계로 판단합니다.</p>
     *
     * @param sortedXCoordinates 정렬된 X좌표 리스트
     * @return 컬럼 경계 X좌표 리스트 (시작점 0, 끝점 페이지 너비 포함)
     */
    private static List<Integer> findColumnBoundaries(List<Integer> sortedXCoordinates) {
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0); // 첫 번째 컬럼 시작점

        // Gap 임계값 계산 (페이지 너비의 10%)
        int pageWidth = sortedXCoordinates.stream().max(Integer::compareTo).orElse(DEFAULT_PAGE_WIDTH);
        int gapThreshold = (int) (pageWidth * COLUMN_GAP_THRESHOLD_RATIO);
        logger.debug("🔍 Gap 임계값: {}px (페이지 너비: {}px)", gapThreshold, pageWidth);

        // 인접 좌표 간 Gap 분석
        for (int i = 1; i < sortedXCoordinates.size(); i++) {
            int gap = sortedXCoordinates.get(i) - sortedXCoordinates.get(i - 1);

            if (gap >= gapThreshold && gap <= 800) { // 최대 800px 제한
                logger.debug("✓ 컬럼 경계 감지: X={} (Gap={}px)", sortedXCoordinates.get(i), gap);
                boundaries.add(sortedXCoordinates.get(i));
            }
        }

        boundaries.add(Math.max(pageWidth, DEFAULT_PAGE_WIDTH)); // 마지막 컬럼 끝점
        return boundaries;
    }

    /**
     * 문제를 컬럼별로 그룹핑합니다.
     *
     * <p>각 문제의 X좌표를 기준으로 어느 컬럼에 속하는지 판단하고,
     * 컬럼별로 문제들을 그룹핑합니다. 각 컬럼 내에서는 Y좌표 순으로 정렬됩니다.</p>
     *
     * @param questions 문제 데이터 리스트
     * @param columns 컬럼 정보 리스트
     * @return 컬럼 인덱스 → 문제 리스트 맵 (Y좌표 순 정렬됨)
     */
    private static Map<Integer, List<QuestionData>> groupQuestionsByColumn(
            List<QuestionData> questions,
            List<ColumnInfo> columns) {

        Map<Integer, List<QuestionData>> columnGroups = new HashMap<>();

        // 각 문제를 적절한 컬럼에 할당
        for (QuestionData question : questions) {
            Integer questionX = getQuestionXCoordinate(question);

            if (questionX == null) {
                logger.warn("⚠️ 문제 {}의 X좌표를 찾을 수 없음 - 첫 번째 컬럼에 할당",
                        question.getQuestionNumber());
                columnGroups.computeIfAbsent(0, k -> new ArrayList<>()).add(question);
                continue;
            }

            // X좌표가 속한 컬럼 찾기
            ColumnInfo matchedColumn = columns.stream()
                    .filter(col -> col.contains(questionX))
                    .findFirst()
                    .orElse(columns.get(0)); // 기본값: 첫 번째 컬럼

            logger.debug("📍 문제 {} → {} (X={})",
                    question.getQuestionNumber(),
                    matchedColumn,
                    questionX);

            columnGroups.computeIfAbsent(matchedColumn.getColumnIndex(), k -> new ArrayList<>())
                    .add(question);
        }

        // 각 컬럼 내 문제들을 Y좌표 기준으로 정렬
        for (List<QuestionData> questionsInColumn : columnGroups.values()) {
            questionsInColumn.sort((q1, q2) -> {
                Integer y1 = getQuestionYCoordinate(q1);
                Integer y2 = getQuestionYCoordinate(q2);

                if (y1 == null || y2 == null) {
                    // Y좌표를 찾을 수 없는 경우 문제 번호로 대체 (String → Integer 변환)
                    int num1 = parseQuestionNumber(q1.getQuestionNumber());
                    int num2 = parseQuestionNumber(q2.getQuestionNumber());
                    return Integer.compare(num1, num2);
                }

                return Integer.compare(y1, y2);
            });
        }

        logger.info("📊 컬럼별 문제 분포:");
        columnGroups.forEach((colIdx, qs) ->
                logger.info("  - 컬럼 {}: {}개 문제", colIdx + 1, qs.size()));

        return columnGroups;
    }

    /**
     * 문제의 X좌표를 추출합니다.
     *
     * <p>문제에 속한 첫 번째 요소의 레이아웃 정보에서 X좌표를 가져옵니다.</p>
     *
     * @param question QuestionData 객체
     * @return X좌표 (픽셀), 찾을 수 없으면 null
     */
    private static Integer getQuestionXCoordinate(QuestionData question) {
        if (question == null || question.getElements() == null) {
            return null;
        }

        return question.getElements().values().stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(AnalysisElement::getLayoutInfo)
                .filter(Objects::nonNull)
                .map(layout -> layout.getBox()[0]) // X좌표 (box[0])
                .findFirst()
                .orElse(null);
    }

    /**
     * 문제의 Y좌표를 추출합니다.
    /**
     * 문제의 Y좌표를 가져옵니다.
     *
     * @param question QuestionData 객체
     * @return Y좌표 (픽셀), 찾을 수 없으면 null
     */
    private static Integer getQuestionYCoordinate(QuestionData question) {
        if (question == null || question.getElements() == null) {
            return null;
        }

        return question.getElements().values().stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(AnalysisElement::getLayoutInfo)
                .filter(Objects::nonNull)
                .map(layout -> layout.getBox()[1]) // Y좌표 (box[1])
                .min(Integer::compareTo) // 가장 위쪽 요소의 Y좌표
                .orElse(null);
    }

    /**
     * 문제 번호 문자열을 정수로 변환합니다.
     * 
     * @param questionNumber 문제 번호 문자열 ("004", "004-1", "col0_q1" 등)
     * @return 정수 문제 번호 (변환 실패 시 0)
     */
    private static int parseQuestionNumber(String questionNumber) {
        if (questionNumber == null || questionNumber.trim().isEmpty()) {
            return 0;
        }
        
        try {
            // "004-1" → "004"만 추출
            if (questionNumber.contains("-")) {
                questionNumber = questionNumber.substring(0, questionNumber.indexOf("-"));
            }
            
            // "col0_q1" 같은 자동 ID 처리
            if (questionNumber.startsWith("col")) {
                return Integer.MAX_VALUE; // 자동 ID는 맨 뒤로
            }
            
            return Integer.parseInt(questionNumber);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 단일 문제를 포맷팅합니다.
     *
     * <p>문제 번호, 문제 텍스트, 요소들을 포맷팅 규칙에 따라 변환하고
     * XSS 방지를 위한 HTML 이스케이프를 적용합니다.</p>
     *
     * @param question QuestionData 객체
     * @return 포맷팅된 문제 텍스트 (HTML-safe)
     */
    private static String formatQuestion(QuestionData question) {
        StringBuilder output = new StringBuilder();

        // 1. 문제 번호
        if (question.getQuestionNumber() != null) {
            output.append(question.getQuestionNumber()).append(". ");
        }

        // 2. 문제 텍스트
        if (question.getQuestionText() != null && !question.getQuestionText().trim().isEmpty()) {
            String safeQuestionText = escapeHtml(question.getQuestionText());
            output.append(safeQuestionText).append("\n");
        }

        // 3. 요소별 포맷팅 (Y좌표 순)
        Map<String, List<AnalysisElement>> elements = question.getElements();
        if (elements != null && !elements.isEmpty()) {
            List<AnalysisElement> allElements = elements.values().stream()
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(e -> {
                        if (e.getLayoutInfo() == null || e.getLayoutInfo().getBox() == null) {
                            return Integer.MAX_VALUE;
                        }
                        return e.getLayoutInfo().getBox()[1]; // Y좌표
                    }))
                    .collect(Collectors.toList());

            for (AnalysisElement element : allElements) {
                String elementText = extractElementText(element);
                if (elementText != null && !elementText.trim().isEmpty()) {
                    String safeText = escapeHtml(elementText);
                    String prefix = getFormattingPrefix(element.getCategory());
                    output.append(prefix).append(safeText).append("\n");
                }
            }
        }

        output.append("\n---\n\n");
        return output.toString();
    }

    /**
     * 요소에서 텍스트를 추출합니다.
     *
     * <p>OCR 결과를 우선적으로 사용하고, 없으면 AI 설명을 사용합니다.</p>
     *
     * @param element AnalysisElement 객체
     * @return 추출된 텍스트, 없으면 null
     */
    private static String extractElementText(AnalysisElement element) {
        if (element == null) {
            return null;
        }

        // OCR 결과 우선
        if (element.getOcrResult() != null && element.getOcrResult().getText() != null) {
            return element.getOcrResult().getText().trim();
        }

        // AI 설명 보조
        if (element.getAiResult() != null && element.getAiResult().getDescription() != null) {
            return element.getAiResult().getDescription().trim();
        }

        return null;
    }

    /**
     * QuestionData의 question_content Map에서 텍스트를 추출합니다.
     *
     * <p>P2 호환성 개선: 동적 필드 생성 지원</p>
     * <p>우선순위: question_text > plain_text > passage_text > list > 기타 필드</p>
     *
     * @param questionContent question_content Map (레이아웃 클래스 → 텍스트)
     * @return 결합된 텍스트 (우선순위 기반), 없으면 빈 문자열
     * @since v0.7 P2
     */
    public static String extractQuestionText(Map<String, Object> questionContent) {
        if (questionContent == null || questionContent.isEmpty()) {
            return "";
        }

        StringBuilder combinedText = new StringBuilder();

        // 우선순위 1: question_text (가장 중요)
        String questionText = getStringValue(questionContent, "question_text");
        if (questionText != null && !questionText.trim().isEmpty()) {
            combinedText.append(questionText.trim()).append("\n");
        }

        // 우선순위 2: plain_text (일반 텍스트)
        String plainText = getStringValue(questionContent, "plain_text");
        if (plainText != null && !plainText.trim().isEmpty()) {
            combinedText.append(plainText.trim()).append("\n");
        }

        // 우선순위 3: passage_text (지문 텍스트)
        String passageText = getStringValue(questionContent, "passage_text");
        if (passageText != null && !passageText.trim().isEmpty()) {
            combinedText.append("[지문] ").append(passageText.trim()).append("\n");
        }

        // 우선순위 4: list (목록 텍스트)
        String listText = getStringValue(questionContent, "list");
        if (listText != null && !listText.trim().isEmpty()) {
            combinedText.append("[목록] ").append(listText.trim()).append("\n");
        }

        // 우선순위 5: 기타 발견된 필드들 (알파벳 순)
        List<String> otherKeys = questionContent.keySet().stream()
                .filter(key -> !key.equals("question_text") &&
                               !key.equals("plain_text") &&
                               !key.equals("passage_text") &&
                               !key.equals("list"))
                .sorted()
                .collect(Collectors.toList());

        for (String key : otherKeys) {
            String value = getStringValue(questionContent, key);
            if (value != null && !value.trim().isEmpty()) {
                String label = formatFieldLabel(key);
                combinedText.append(label).append(value.trim()).append("\n");
            }
        }

        // 결과 반환 (마지막 개행 제거)
        String result = combinedText.toString().trim();
        logger.trace("📝 question_content 추출: {}바이트 (필드 {}개)",
                    result.length(), questionContent.size());

        return result;
    }

    /**
     * Map에서 문자열 값을 안전하게 추출합니다.
     *
     * @param map 대상 Map
     * @param key 키
     * @return 문자열 값, 없거나 null이면 null
     */
    private static String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 다른 타입인 경우 toString() 사용
        return value.toString();
    }

    /**
     * 필드명을 사람이 읽기 쉬운 레이블로 변환합니다.
     *
     * @param fieldName 필드명 (예: "figure", "table", "choice_1")
     * @return 포맷된 레이블 (예: "[그림] ", "[표] ", "[보기 1] ")
     */
    private static String formatFieldLabel(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "";
        }

        switch (fieldName) {
            case "figure":
                return "[그림] ";
            case "table":
                return "[표] ";
            case "chart":
                return "[차트] ";
            case "equation":
                return "[수식] ";
            case "diagram":
                return "[도표] ";
            case "choice_1":
                return "[보기 1] ";
            case "choice_2":
                return "[보기 2] ";
            case "choice_3":
                return "[보기 3] ";
            case "choice_4":
                return "[보기 4] ";
            case "choice_5":
                return "[보기 5] ";
            case "answer":
                return "[정답] ";
            case "explanation":
                return "[해설] ";
            default:
                // 기본값: [필드명]
                return String.format("[%s] ", fieldName);
        }
    }

    /**
     * 요소 카테고리에 따른 포맷팅 접두사를 반환합니다.
     *
     * @param category 요소 카테고리
     * @return 포맷팅 접두사
     */
    private static String getFormattingPrefix(String category) {
        if (category == null) {
            return "  ";
        }

        switch (category) {
            case "question_type":
            case "choice_1":
            case "choice_2":
            case "choice_3":
            case "choice_4":
            case "choice_5":
                return "    ";
            case "figure":
                return "\n[그림 설명] ";
            case "table":
                return "\n[표 설명] ";
            default:
                return "  ";
        }
    }

    /**
     * HTML 이스케이프 처리를 수행합니다 (XSS 방지).
     *
     * <p>Apache Commons Text의 StringEscapeUtils를 사용하여
     * HTML 특수 문자를 안전하게 이스케이프합니다.</p>
     *
     * <h3>이스케이프되는 문자</h3>
     * <ul>
     *   <li>{@code <} → {@code &lt;}</li>
     *   <li>{@code >} → {@code &gt;}</li>
     *   <li>{@code &} → {@code &amp;}</li>
     *   <li>{@code "} → {@code &quot;}</li>
     *   <li>{@code '} → {@code &#39;}</li>
     * </ul>
     *
     * @param text 원본 텍스트
     * @return HTML-safe 텍스트
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return StringEscapeUtils.escapeHtml4(text);
    }
}
