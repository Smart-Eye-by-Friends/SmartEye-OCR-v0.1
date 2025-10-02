package com.smarteye.application.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteye.domain.analysis.AnalysisResult;
import com.smarteye.domain.analysis.Block;
import com.smarteye.domain.analysis.CimInput;
import com.smarteye.domain.analysis.StructuredData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "feature.use-2d-spatial-analysis=true" })
class UnifiedAnalysisEngineIntegrationTest {

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    @Autowired
    private ObjectMapper objectMapper;

    private CimInput cimInput;

    @BeforeEach
    void setUp() {
        cimInput = new CimInput();
        cimInput.setImageWidth(1000.0);
        cimInput.setImageHeight(1200.0);
    }

    @Test
    @DisplayName("표준 2단 신문 기사형 레이아웃 테스트")
    void shouldCorrectlyProcessStandardTwoColumnLayout() throws Exception {
        // Given: 표준 2단 레이아웃 CIM 데이터 생성
        List<Block> blocks = Arrays.asList(
            // Column 1
            createBlock("question_number", "1", 50, 50, 70, 70),
            createBlock("text", "첫 번째 문제 내용입니다.", 50, 80, 450, 180),
            createBlock("question_number", "2", 50, 200, 70, 220),
            createBlock("text", "두 번째 문제 내용입니다.", 50, 230, 450, 330),
            // Column 2
            createBlock("question_number", "3", 550, 50, 570, 70),
            createBlock("text", "세 번째 문제 내용입니다.", 550, 80, 950, 180),
            createBlock("question_number", "4", 550, 200, 570, 220),
            createBlock("text", "네 번째 문제 내용입니다.", 550, 230, 950, 330)
        );
        cimInput.setBlocks(blocks);

        // When: UnifiedAnalysisEngine으로 분석 실행
        AnalysisResult analysisResult = unifiedAnalysisEngine.analyze(cimInput, true);
        StructuredData structuredData = objectMapper.readValue(analysisResult.getStructuredData(), StructuredData.class);

        // Then: 문제 그룹핑 결과 검증
        assertThat(structuredData.getProblemGroups()).hasSize(4);
        assertThat(structuredData.getProblemGroups().get(0).getQuestionNumber()).isEqualTo("1");
        assertThat(structuredData.getProblemGroups().get(0).getContents().get(0).getText()).contains("첫 번째 문제");
        assertThat(structuredData.getProblemGroups().get(1).getQuestionNumber()).isEqualTo("2");
        assertThat(structuredData.getProblemGroups().get(2).getQuestionNumber()).isEqualTo("3");
        assertThat(structuredData.getProblemGroups().get(3).getQuestionNumber()).isEqualTo("4");
    }

    @Test
    @DisplayName("비대칭 2단 레이아웃 테스트 (왼쪽 단이 더 김)")
    void shouldHandleAsymmetricTwoColumnLayout() throws Exception {
        // Given: 왼쪽 단이 더 긴 비대칭 2단 레이아웃
        List<Block> blocks = Arrays.asList(
            // Column 1 (Longer)
            createBlock("question_number", "1", 50, 50, 70, 70),
            createBlock("text", "첫 번째 문제 내용.", 50, 80, 450, 180),
            createBlock("question_number", "2", 50, 200, 70, 220),
            createBlock("text", "두 번째 문제 내용.", 50, 230, 450, 400), // 긴 내용
            // Column 2 (Shorter)
            createBlock("question_number", "3", 550, 50, 570, 70),
            createBlock("text", "세 번째 문제 내용.", 550, 80, 950, 180)
        );
        cimInput.setBlocks(blocks);

        // When
        AnalysisResult analysisResult = unifiedAnalysisEngine.analyze(cimInput, true);
        StructuredData structuredData = objectMapper.readValue(analysisResult.getStructuredData(), StructuredData.class);

        // Then
        assertThat(structuredData.getProblemGroups()).hasSize(3);
        assertThat(structuredData.getProblemGroups().get(0).getQuestionNumber()).isEqualTo("1");
        assertThat(structuredData.getProblemGroups().get(1).getQuestionNumber()).isEqualTo("2");
        assertThat(structuredData.getProblemGroups().get(2).getQuestionNumber()).isEqualTo("3");
        assertThat(structuredData.getProblemGroups().get(1).getContents().get(0).getText()).contains("두 번째 문제");
    }

    @Test
    @DisplayName("가운데 넓은 단과 양옆 좁은 단으로 구성된 3단 레이아웃 테스트")
    void shouldProcessThreeColumnLayoutWithWideCenter() throws Exception {
        // Given: 3단 레이아웃 (좁음 - 넓음 - 좁음)
        List<Block> blocks = Arrays.asList(
            // Column 1 (Narrow)
            createBlock("question_number", "1", 50, 50, 70, 70),
            createBlock("text", "첫 번째.", 50, 80, 250, 180),
            // Column 2 (Wide)
            createBlock("question_number", "2", 300, 50, 320, 70),
            createBlock("text", "두 번째 문제 내용입니다. 이 단은 다른 단에 비해 훨씬 넓습니다.", 300, 80, 700, 180),
            // Column 3 (Narrow)
            createBlock("question_number", "3", 750, 50, 770, 70),
            createBlock("text", "세 번째.", 750, 80, 950, 180)
        );
        cimInput.setBlocks(blocks);

        // When
        AnalysisResult analysisResult = unifiedAnalysisEngine.analyze(cimInput, true);
        StructuredData structuredData = objectMapper.readValue(analysisResult.getStructuredData(), StructuredData.class);

        // Then
        assertThat(structuredData.getProblemGroups()).hasSize(3);
        assertThat(structuredData.getProblemGroups().get(0).getQuestionNumber()).isEqualTo("1");
        assertThat(structuredData.getProblemGroups().get(1).getQuestionNumber()).isEqualTo("2");
        assertThat(structuredData.getProblemGroups().get(2).getQuestionNumber()).isEqualTo("3");
        assertThat(structuredData.getProblemGroups().get(1).getContents().get(0).getText()).contains("훨씬 넓습니다");
    }

    @Test
    @DisplayName("하나의 이미지가 두 개의 컬럼에 걸쳐있는 복잡한 레이아웃 테스트")
    void shouldHandleComplexLayoutWithSpanningImage() throws Exception {
        // Given: 이미지가 1, 2번 문제에 걸쳐있는 레이아웃
        List<Block> blocks = Arrays.asList(
            // Column 1
            createBlock("question_number", "1", 50, 50, 70, 70),
            createBlock("text", "첫 번째 문제 내용입니다.", 50, 80, 450, 150),
            // Spanning Image (문제 1과 2에 모두 연관)
            createBlock("image", "spanning_image.jpg", 50, 160, 950, 300),
            // Column 2
            createBlock("question_number", "2", 550, 50, 570, 70),
            createBlock("text", "두 번째 문제 내용입니다.", 550, 80, 950, 150),
             // Column 1 (아래)
            createBlock("question_number", "3", 50, 320, 70, 340),
            createBlock("text", "세 번째 문제 내용입니다.", 50, 350, 450, 450)
        );
        cimInput.setBlocks(blocks);

        // When
        AnalysisResult analysisResult = unifiedAnalysisEngine.analyze(cimInput, true);
        StructuredData structuredData = objectMapper.readValue(analysisResult.getStructuredData(), StructuredData.class);

        // Then
        assertThat(structuredData.getProblemGroups()).hasSize(3);
        // 1번 문제 그룹 검증 (문제, 내용, 이미지 포함)
        assertThat(structuredData.getProblemGroups().get(0).getQuestionNumber()).isEqualTo("1");
        assertThat(structuredData.getProblemGroups().get(0).getContents()).hasSize(2);
        assertThat(structuredData.getProblemGroups().get(0).getContents().get(0).getType()).isEqualTo("text");
        assertThat(structuredData.getProblemGroups().get(0).getContents().get(1).getType()).isEqualTo("image");

        // 2번 문제 그룹 검증 (문제, 내용, 이미지 포함)
        assertThat(structuredData.getProblemGroups().get(1).getQuestionNumber()).isEqualTo("2");
        assertThat(structuredData.getProblemGroups().get(1).getContents()).hasSize(2);
        assertThat(structuredData.getProblemGroups().get(1).getContents().get(0).getType()).isEqualTo("text");
        assertThat(structuredData.getProblemGroups().get(1).getContents().get(1).getType()).isEqualTo("image");

        // 3번 문제 그룹 검증
        assertThat(structuredData.getProblemGroups().get(2).getQuestionNumber()).isEqualTo("3");
        assertThat(structuredData.getProblemGroups().get(2).getContents()).hasSize(1);
        assertThat(structuredData.getProblemGroups().get(2).getContents().get(0).getType()).isEqualTo("text");
    }

    private Block createBlock(String type, String text, double x1, double y1, double x2, double y2) {
        Block block = new Block();
        block.setType(type);
        block.setText(text);
        block.setBbox(Arrays.asList(x1, y1, x2, y2));
        return block;
    }
}
