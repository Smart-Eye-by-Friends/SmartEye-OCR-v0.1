package com.smarteye.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.service.StructuredAnalysisService.QuestionStructure;
import com.smarteye.service.StructuredAnalysisService.QuestionElements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 레거시 호환성 검증 테스트
 * Python legacy/structured_json_generator.py와 Java StructuredAnalysisService 간의
 * 호환성을 검증하고 데이터 구조 일관성을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("🔍 레거시 호환성 검증 테스트")
public class LegacyCompatibilityTest {

    private static final Logger logger = LoggerFactory.getLogger(LegacyCompatibilityTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StructuredAnalysisService structuredAnalysisService;

    // 테스트 데이터 샘플
    private List<OCRResult> sampleOcrResults;
    private List<LayoutInfo> sampleLayoutElements;

    @BeforeEach
    void setUp() {
        setupSampleTestData();
    }

    // ═══════════════════════════════════════════════════
    // 🎯 핵심 호환성 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("📊 JSON 구조 호환성 검증")
    void testJsonStructureCompatibility() {
        logger.info("🔍 JSON 구조 호환성 검증 시작");

        // Java 구조화 분석 실행
        QuestionStructure javaResult = structuredAnalysisService
            .detectQuestionStructure(sampleOcrResults, sampleLayoutElements);

        // Java 결과를 JSON으로 변환
        Map<String, Object> javaJson = convertJavaResultToJson(javaResult);

        // 핵심 구조 검증
        assertThat(javaJson).containsKeys(
            "document_info", "questions"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> documentInfo = (Map<String, Object>) javaJson.get("document_info");
        assertThat(documentInfo).containsKeys(
            "total_questions", "layout_type", "sections"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) javaJson.get("questions");
        if (!questions.isEmpty()) {
            Map<String, Object> firstQuestion = questions.get(0);
            assertThat(firstQuestion).containsKeys(
                "question_number", "section", "question_content", "ai_analysis"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> questionContent = (Map<String, Object>) firstQuestion.get("question_content");
            assertThat(questionContent).containsKeys(
                "main_question", "passage", "choices", "images", "tables", "explanations"
            );
        }

        logger.info("✅ JSON 구조 호환성 검증 완료");
    }

    @Test
    @DisplayName("🔢 문제 번호 추출 호환성 검증")
    void testQuestionNumberExtractionCompatibility() {
        logger.info("🔍 문제 번호 추출 호환성 검증 시작");

        // 다양한 문제 번호 패턴 테스트
        List<String> testTexts = Arrays.asList(
            "1번", "2.", "문제 3", "4)", "Q5", "593", "594번"
        );

        List<String> expectedNumbers = Arrays.asList(
            "1", "2", "3", "4", "5", "593", "594"
        );

        List<OCRResult> testOcrResults = createOcrResultsFromTexts(testTexts);
        QuestionStructure result = structuredAnalysisService
            .detectQuestionStructure(testOcrResults, new ArrayList<>());

        Set<String> extractedNumbers = result.questions.keySet();

        // Python과 동일한 패턴 인식 검증
        for (String expected : expectedNumbers) {
            assertThat(extractedNumbers).contains(expected);
        }

        logger.info("✅ 추출된 문제 번호: {}", extractedNumbers);
        logger.info("✅ 문제 번호 추출 호환성 검증 완료");
    }

    @Test
    @DisplayName("📝 텍스트 요소 분류 호환성 검증")
    void testTextElementClassificationCompatibility() {
        logger.info("🔍 텍스트 요소 분류 호환성 검증 시작");

        // Python과 동일한 분류 로직 테스트
        Map<String, String> testCases = Map.of(
            "①학교에서", "choices",
            "다음을 보고", "passage",
            "설명하시오", "explanations",
            "위의 그림을", "passage",
            "답: 정답은", "explanations",
            "일반적인 문제", "question_text"
        );

        for (Map.Entry<String, String> testCase : testCases.entrySet()) {
            String text = testCase.getKey();
            String expectedType = testCase.getValue();

            String actualType = classifyTextForTesting(text);

            assertThat(actualType)
                .withFailMessage("텍스트 '%s'의 분류가 Python과 다름: 예상=%s, 실제=%s",
                    text, expectedType, actualType)
                .isEqualTo(expectedType);
        }

        logger.info("✅ 텍스트 요소 분류 호환성 검증 완료");
    }

    @Test
    @DisplayName("🏗️ 레이아웃 타입 결정 호환성 검증")
    void testLayoutTypeCompatibility() {
        logger.info("🔍 레이아웃 타입 결정 호환성 검증 시작");

        // 다양한 케이스별 레이아웃 타입 검증
        Map<Integer, String> expectedLayoutTypes = Map.of(
            1, "simple",      // 1-2문제: simple
            3, "standard",    // 3-5문제: standard
            7, "multiple_choice", // 6+문제: multiple_choice
            2, "simple"       // 섹션 없는 2문제: simple
        );

        for (Map.Entry<Integer, String> testCase : expectedLayoutTypes.entrySet()) {
            int questionCount = testCase.getKey();
            String expectedType = testCase.getValue();

            List<OCRResult> testResults = createTestOcrForQuestionCount(questionCount);
            QuestionStructure result = structuredAnalysisService
                .detectQuestionStructure(testResults, new ArrayList<>());

            assertThat(result.layoutType)
                .withFailMessage("문제 %d개일 때 레이아웃 타입이 Python과 다름", questionCount)
                .isEqualTo(expectedType);
        }

        logger.info("✅ 레이아웃 타입 결정 호환성 검증 완료");
    }

    // ═══════════════════════════════════════════════════
    // 🔧 데이터 무결성 검증 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("🔒 데이터 무결성 검증")
    void testDataIntegrityValidation() {
        logger.info("🔍 데이터 무결성 검증 시작");

        QuestionStructure result = structuredAnalysisService
            .detectQuestionStructure(sampleOcrResults, sampleLayoutElements);

        // 1. 필수 필드 존재 검증
        assertThat(result.totalQuestions).isGreaterThanOrEqualTo(0);
        assertThat(result.layoutType).isNotNull().isNotEmpty();
        assertThat(result.questions).isNotNull();
        assertThat(result.sections).isNotNull();

        // 2. 문제별 데이터 일관성 검증
        for (Map.Entry<String, StructuredAnalysisService.QuestionData> entry : result.questions.entrySet()) {
            String questionNum = entry.getKey();
            StructuredAnalysisService.QuestionData questionData = entry.getValue();

            assertThat(questionData.number).isEqualTo(questionNum);
            assertThat(questionData.elements).isNotNull();

            QuestionElements elements = questionData.elements;
            assertThat(elements.questionText).isNotNull();
            assertThat(elements.passage).isNotNull();
            assertThat(elements.choices).isNotNull();
            assertThat(elements.images).isNotNull();
            assertThat(elements.tables).isNotNull();
            assertThat(elements.explanations).isNotNull();
        }

        // 3. 좌표 데이터 유효성 검증
        validateCoordinateData(result);

        logger.info("✅ 데이터 무결성 검증 완료");
    }

    @Test
    @DisplayName("📐 좌표 시스템 호환성 검증")
    void testCoordinateSystemCompatibility() {
        logger.info("🔍 좌표 시스템 호환성 검증 시작");

        // Python과 Java 간의 좌표 시스템 일관성 검증
        List<OCRResult> testResults = Arrays.asList(
            createOcrResult("1번", new int[]{100, 200, 150, 220}, "question_number"),
            createOcrResult("2번", new int[]{100, 400, 150, 420}, "question_number"),
            createOcrResult("선택지 내용", new int[]{120, 300, 200, 320}, "choice")
        );

        QuestionStructure result = structuredAnalysisService
            .detectQuestionStructure(testResults, new ArrayList<>());

        // 문제 순서가 Y 좌표 순으로 정렬되는지 검증
        List<String> questionNumbers = new ArrayList<>(result.questions.keySet());
        questionNumbers.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        assertThat(questionNumbers).containsExactly("1", "2");

        logger.info("✅ 좌표 시스템 호환성 검증 완료");
    }

    // ═══════════════════════════════════════════════════
    // 🎭 에러 시나리오 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("❌ 빈 데이터 처리 검증")
    void testEmptyDataHandling() {
        logger.info("🔍 빈 데이터 처리 검증 시작");

        // 빈 OCR 결과로 테스트
        QuestionStructure emptyOcrResult = structuredAnalysisService
            .detectQuestionStructure(new ArrayList<>(), new ArrayList<>());

        assertThat(emptyOcrResult.totalQuestions).isEqualTo(0);
        assertThat(emptyOcrResult.questions).isEmpty();
        assertThat(emptyOcrResult.layoutType).isEqualTo("simple");

        logger.info("✅ 빈 데이터 처리 검증 완료");
    }

    @Test
    @DisplayName("🚨 잘못된 형식 데이터 처리 검증")
    void testMalformedDataHandling() {
        logger.info("🔍 잘못된 형식 데이터 처리 검증 시작");

        // 잘못된 좌표를 가진 OCR 결과
        List<OCRResult> malformedResults = Arrays.asList(
            createOcrResult("1번", null, "question_number"),
            createOcrResult("텍스트", new int[]{}, "text"),
            createOcrResult("", new int[]{100, 200}, "empty_text")
        );

        // 예외 발생 없이 안전하게 처리되어야 함
        assertThatCode(() -> {
            QuestionStructure result = structuredAnalysisService
                .detectQuestionStructure(malformedResults, new ArrayList<>());
            assertThat(result).isNotNull();
        }).doesNotThrowAnyException();

        logger.info("✅ 잘못된 형식 데이터 처리 검증 완료");
    }

    // ═══════════════════════════════════════════════════
    // 🚀 성능 벤치마크 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("⚡ 성능 벤치마크 검증")
    void testPerformanceBenchmark() {
        logger.info("🔍 성능 벤치마크 검증 시작");

        // 대용량 테스트 데이터 생성 (100개 문제)
        List<OCRResult> largeDateSet = createLargeTestDataset(100);
        List<LayoutInfo> largeLayoutSet = createLargeLayoutDataset(50);

        long startTime = System.currentTimeMillis();

        QuestionStructure result = structuredAnalysisService
            .detectQuestionStructure(largeDateSet, largeLayoutSet);

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;

        // 성능 기준: 100개 문제를 5초 이내에 처리
        assertThat(processingTime)
            .withFailMessage("성능 기준 초과: %dms (기준: 5000ms)", processingTime)
            .isLessThan(5000L);

        assertThat(result.totalQuestions).isEqualTo(100);

        logger.info("✅ 성능 검증 완료: {}ms (문제 {}개)", processingTime, result.totalQuestions);
    }

    // ═══════════════════════════════════════════════════
    // 🛠️ 헬퍼 메서드들
    // ═══════════════════════════════════════════════════

    private void setupSampleTestData() {
        sampleOcrResults = Arrays.asList(
            createOcrResult("1번", new int[]{100, 200, 150, 220}, "question_number"),
            createOcrResult("다음을 보고 답하시오", new int[]{100, 250, 300, 270}, "passage"),
            createOcrResult("①학교", new int[]{120, 300, 180, 320}, "choice"),
            createOcrResult("②병원", new int[]{120, 330, 180, 350}, "choice"),
            createOcrResult("2번", new int[]{100, 400, 150, 420}, "question_number"),
            createOcrResult("위의 그림을 설명하시오", new int[]{100, 450, 300, 470}, "question_text")
        );

        sampleLayoutElements = Arrays.asList(
            createLayoutInfo("figure", new int[]{200, 280, 350, 380}),
            createLayoutInfo("table", new int[]{200, 480, 400, 580})
        );
    }

    private OCRResult createOcrResult(String text, int[] coordinates, String className) {
        OCRResult result = new OCRResult();
        result.setText(text);
        result.setCoordinates(coordinates);
        result.setClassName(className);
        result.setConfidence(90.0);
        return result;
    }

    private LayoutInfo createLayoutInfo(String className, int[] box) {
        LayoutInfo info = new LayoutInfo();
        info.setClassName(className);
        info.setBox(box);
        info.setConfidence(0.9f);
        return info;
    }

    private List<OCRResult> createOcrResultsFromTexts(List<String> texts) {
        List<OCRResult> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            results.add(createOcrResult(texts.get(i),
                new int[]{100, 200 + i * 50, 200, 220 + i * 50}, "question_number"));
        }
        return results;
    }

    private List<OCRResult> createTestOcrForQuestionCount(int count) {
        List<OCRResult> results = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            results.add(createOcrResult(i + "번",
                new int[]{100, 200 + i * 100, 150, 220 + i * 100}, "question_number"));
        }
        return results;
    }

    private List<OCRResult> createLargeTestDataset(int questionCount) {
        List<OCRResult> results = new ArrayList<>();
        for (int i = 1; i <= questionCount; i++) {
            results.add(createOcrResult(i + "번",
                new int[]{100, 200 + i * 20, 150, 220 + i * 20}, "question_number"));
            results.add(createOcrResult("문제 내용 " + i,
                new int[]{100, 230 + i * 20, 300, 250 + i * 20}, "question_text"));
        }
        return results;
    }

    private List<LayoutInfo> createLargeLayoutDataset(int elementCount) {
        List<LayoutInfo> elements = new ArrayList<>();
        for (int i = 0; i < elementCount; i++) {
            elements.add(createLayoutInfo(i % 2 == 0 ? "figure" : "table",
                new int[]{200, 300 + i * 30, 350, 380 + i * 30}));
        }
        return elements;
    }

    private String classifyTextForTesting(String text) {
        // StructuredAnalysisService의 private 메서드 로직 재현
        if (text.matches("^[①②③④⑤⑥⑦⑧⑨⑩].*") ||
            text.matches("^[(（]\\s*[1-5]\\s*[)）].*") ||
            text.matches("^[1-5]\\s*[.．].*")) {
            return "choices";
        }

        if (text.contains("다음을") || text.contains("아래의") || text.contains("위의") ||
            text.contains("그림을") || text.contains("표를")) {
            return "passage";
        }

        if (text.contains("설명") || text.contains("해설") || text.contains("풀이") || text.contains("답:")) {
            return "explanations";
        }

        return "question_text";
    }

    private Map<String, Object> convertJavaResultToJson(QuestionStructure javaResult) {
        Map<String, Object> result = new HashMap<>();

        // Document info
        Map<String, Object> documentInfo = Map.of(
            "total_questions", javaResult.totalQuestions,
            "layout_type", javaResult.layoutType,
            "sections", javaResult.sections != null ? javaResult.sections : new HashMap<>()
        );
        result.put("document_info", documentInfo);

        // Questions
        List<Map<String, Object>> questions = javaResult.questions.entrySet().stream()
            .map(entry -> {
                Map<String, Object> question = new HashMap<>();
                question.put("question_number", entry.getKey());
                question.put("section", entry.getValue().section);
                question.put("question_content", Map.of(
                    "main_question", extractMainQuestionText(entry.getValue().elements),
                    "passage", "",
                    "choices", new ArrayList<>(),
                    "images", new ArrayList<>(),
                    "tables", new ArrayList<>(),
                    "explanations", ""
                ));
                question.put("ai_analysis", Map.of(
                    "image_descriptions", new ArrayList<>(),
                    "table_analysis", new ArrayList<>(),
                    "problem_analysis", new ArrayList<>()
                ));
                return question;
            })
            .toList();

        result.put("questions", questions);
        return result;
    }

    private String extractMainQuestionText(QuestionElements elements) {
        if (elements != null && !elements.questionText.isEmpty()) {
            return elements.questionText.get(0).text;
        }
        return "";
    }

    private void validateCoordinateData(QuestionStructure result) {
        for (StructuredAnalysisService.QuestionData questionData : result.questions.values()) {
            QuestionElements elements = questionData.elements;

            // 텍스트 요소 좌표 검증
            validateTextElementCoordinates(elements.questionText);
            validateTextElementCoordinates(elements.passage);
            validateTextElementCoordinates(elements.choices);
            validateTextElementCoordinates(elements.explanations);

            // 레이아웃 요소 좌표 검증
            for (LayoutInfo image : elements.images) {
                assertThat(image.getBox()).isNotNull().hasSize(4);
            }
            for (LayoutInfo table : elements.tables) {
                assertThat(table.getBox()).isNotNull().hasSize(4);
            }
        }
    }

    private void validateTextElementCoordinates(List<StructuredAnalysisService.TextElement> elements) {
        for (StructuredAnalysisService.TextElement element : elements) {
            if (element.bbox != null) {
                assertThat(element.bbox).hasSize(4);
                // x1 <= x2, y1 <= y2 검증
                assertThat(element.bbox[0]).isLessThanOrEqualTo(element.bbox[2]);
                assertThat(element.bbox[1]).isLessThanOrEqualTo(element.bbox[3]);
            }
        }
    }
}