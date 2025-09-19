package com.smarteye.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteye.dto.CIMAnalysisResponse;
import com.smarteye.dto.CIMToTextRequest;
import com.smarteye.dto.TextConversionResponse;
import com.smarteye.service.*;
import com.smarteye.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CIM API 통합 테스트
 * Phase 1 구현된 두 개의 새로운 API 엔드포인트 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("🔧 CIM API 통합 테스트")
public class CIMIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(CIMIntegrationTest.class);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // ═══════════════════════════════════════════════════
    // 🎯 CIM 통합 분석 API 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("📊 CIM 통합 분석 API - 기본 기능 테스트")
    void testAnalyzeCIMBasicFunctionality() throws Exception {
        logger.info("🔍 CIM 통합 분석 API 기본 기능 테스트 시작");

        // 테스트 이미지 생성
        MockMultipartFile testImage = createTestImage();

        // API 호출
        MvcResult result = mockMvc.perform(multipart("/api/document/analyze-cim")
                .file(testImage)
                .param("modelChoice", "SmartEyeSsen")
                .param("structuredAnalysis", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // 응답 검증
        String responseContent = result.getResponse().getContentAsString();
        CIMAnalysisResponse response = objectMapper.readValue(responseContent, CIMAnalysisResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("CIM 분석");
        assertThat(response.getJobId()).isNotNull().isNotEmpty();
        assertThat(response.getLayoutImageUrl()).isNotNull();
        assertThat(response.getStats()).isNotNull();
        assertThat(response.getCimData()).isNotNull();
        assertThat(response.getFormattedText()).isNotNull();
        assertThat(response.getTimestamp()).isNotNull();

        logger.info("✅ CIM 통합 분석 API 기본 기능 테스트 완료");
    }

    @Test
    @DisplayName("🔧 CIM 통합 분석 API - 다양한 모델 선택 테스트")
    void testAnalyzeCIMWithDifferentModels() throws Exception {
        logger.info("🔍 CIM 다양한 모델 선택 테스트 시작");

        MockMultipartFile testImage = createTestImage();
        String[] models = {"SmartEyeSsen", "SmartEyeMid", "SmartEyeLarge"};

        for (String model : models) {
            logger.info("모델 {} 테스트 중...", model);

            MvcResult result = mockMvc.perform(multipart("/api/document/analyze-cim")
                    .file(testImage)
                    .param("modelChoice", model)
                    .param("structuredAnalysis", "false"))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseContent = result.getResponse().getContentAsString();
            CIMAnalysisResponse response = objectMapper.readValue(responseContent, CIMAnalysisResponse.class);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getJobId()).isNotNull();
        }

        logger.info("✅ CIM 다양한 모델 선택 테스트 완료");
    }

    @Test
    @DisplayName("❌ CIM 통합 분석 API - 잘못된 파일 형식 테스트")
    void testAnalyzeCIMWithInvalidFileFormat() throws Exception {
        logger.info("🔍 CIM 잘못된 파일 형식 테스트 시작");

        // 텍스트 파일을 이미지로 업로드
        MockMultipartFile invalidFile = new MockMultipartFile(
            "image",
            "test.txt",
            "text/plain",
            "This is not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/document/analyze-cim")
                .file(invalidFile))
                .andExpect(status().is4xxClientError());

        logger.info("✅ CIM 잘못된 파일 형식 테스트 완료");
    }

    // ═══════════════════════════════════════════════════
    // 🎯 CIM 텍스트 변환 API 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("📝 CIM 텍스트 변환 API - FORMATTED 모드 테스트")
    void testConvertCIMToTextFormattedMode() throws Exception {
        logger.info("🔍 CIM 텍스트 변환 FORMATTED 모드 테스트 시작");

        // 테스트 CIM 데이터 생성
        Map<String, Object> testCimData = createTestCIMData();

        // 요청 생성
        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(testCimData);
        request.setOutputFormat(CIMToTextRequest.TextOutputFormat.FORMATTED);
        request.setIncludeMetadata(true);

        // API 호출
        MvcResult result = mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // 응답 검증
        String responseContent = result.getResponse().getContentAsString();
        TextConversionResponse response = objectMapper.readValue(responseContent, TextConversionResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("텍스트 변환");
        assertThat(response.getFormattedText()).isNotNull().isNotEmpty();
        assertThat(response.getStats()).isNotNull();
        assertThat(response.getMetadata()).isNotNull(); // includeMetadata=true이므로
        assertThat(response.getTimestamp()).isNotNull();

        // 통계 검증
        TextConversionResponse.TextConversionStats stats = response.getStats();
        assertThat(stats.getTotalCharacters()).isGreaterThan(0);
        assertThat(stats.getTotalWords()).isGreaterThan(0);
        assertThat(stats.getProcessingTimeMs()).isGreaterThanOrEqualTo(0);

        logger.info("✅ CIM 텍스트 변환 FORMATTED 모드 테스트 완료");
    }

    @Test
    @DisplayName("📝 CIM 텍스트 변환 API - STRUCTURED 모드 테스트")
    void testConvertCIMToTextStructuredMode() throws Exception {
        logger.info("🔍 CIM 텍스트 변환 STRUCTURED 모드 테스트 시작");

        Map<String, Object> testCimData = createTestCIMData();

        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(testCimData);
        request.setOutputFormat(CIMToTextRequest.TextOutputFormat.STRUCTURED);
        request.setIncludeMetadata(false);

        MvcResult result = mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TextConversionResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), TextConversionResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFormattedText()).isNotEmpty();
        assertThat(response.getFormattedText()).contains("📋 문서 분석 결과"); // 구조화된 형식 확인
        assertThat(response.getMetadata()).isNull(); // includeMetadata=false이므로

        logger.info("✅ CIM 텍스트 변환 STRUCTURED 모드 테스트 완료");
    }

    @Test
    @DisplayName("📝 CIM 텍스트 변환 API - RAW 모드 테스트")
    void testConvertCIMToTextRawMode() throws Exception {
        logger.info("🔍 CIM 텍스트 변환 RAW 모드 테스트 시작");

        Map<String, Object> testCimData = createTestCIMData();

        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(testCimData);
        request.setOutputFormat(CIMToTextRequest.TextOutputFormat.RAW);

        MvcResult result = mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TextConversionResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), TextConversionResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFormattedText()).isNotEmpty();
        // RAW 모드는 기본적으로 OCR 텍스트만 추출

        logger.info("✅ CIM 텍스트 변환 RAW 모드 테스트 완료");
    }

    @Test
    @DisplayName("📝 CIM 텍스트 변환 API - 섹션 필터링 테스트")
    void testConvertCIMToTextWithSectionFilter() throws Exception {
        logger.info("🔍 CIM 텍스트 변환 섹션 필터링 테스트 시작");

        Map<String, Object> testCimData = createTestCIMData();

        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(testCimData);
        request.setOutputFormat(CIMToTextRequest.TextOutputFormat.STRUCTURED);
        request.setSectionFilter("questions");

        MvcResult result = mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TextConversionResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), TextConversionResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFormattedText()).isNotEmpty();

        logger.info("✅ CIM 텍스트 변환 섹션 필터링 테스트 완료");
    }

    @Test
    @DisplayName("❌ CIM 텍스트 변환 API - 빈 데이터 테스트")
    void testConvertCIMToTextWithEmptyData() throws Exception {
        logger.info("🔍 CIM 텍스트 변환 빈 데이터 테스트 시작");

        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(new HashMap<>()); // 빈 데이터

        mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        logger.info("✅ CIM 텍스트 변환 빈 데이터 테스트 완료");
    }

    // ═══════════════════════════════════════════════════
    // 🔧 성능 및 부하 테스트
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("⚡ CIM API 성능 테스트")
    void testCIMAPIPerformance() throws Exception {
        logger.info("🔍 CIM API 성능 테스트 시작");

        MockMultipartFile testImage = createTestImage();
        Map<String, Object> testCimData = createTestCIMData();

        // CIM 분석 API 성능 측정
        long startTime = System.currentTimeMillis();

        MvcResult analysisResult = mockMvc.perform(multipart("/api/document/analyze-cim")
                .file(testImage)
                .param("structuredAnalysis", "false")) // 빠른 분석
                .andExpect(status().isOk())
                .andReturn();

        long analysisTime = System.currentTimeMillis() - startTime;

        // 텍스트 변환 API 성능 측정
        CIMToTextRequest request = new CIMToTextRequest();
        request.setCimData(testCimData);

        startTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/document/cim-to-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        long conversionTime = System.currentTimeMillis() - startTime;

        // 성능 검증 (관대한 기준)
        assertThat(analysisTime).isLessThan(10000L); // 10초 이내
        assertThat(conversionTime).isLessThan(1000L);  // 1초 이내

        logger.info("✅ CIM API 성능 테스트 완료 - 분석: {}ms, 변환: {}ms", analysisTime, conversionTime);
    }

    // ═══════════════════════════════════════════════════
    // 🛠️ 헬퍼 메서드들
    // ═══════════════════════════════════════════════════

    /**
     * 테스트용 이미지 파일 생성
     */
    private MockMultipartFile createTestImage() throws Exception {
        // 간단한 테스트 이미지 생성 (100x100 흰색 이미지)
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                testImage.setRGB(x, y, 0xFFFFFF); // 흰색
            }
        }

        // 이미지를 바이트 배열로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        return new MockMultipartFile(
            "image",
            "test-image.png",
            "image/png",
            imageBytes
        );
    }

    /**
     * 테스트용 CIM 데이터 생성
     */
    private Map<String, Object> createTestCIMData() {
        Map<String, Object> cimData = new HashMap<>();

        // Document info
        Map<String, Object> documentInfo = new HashMap<>();
        documentInfo.put("total_questions", 2);
        documentInfo.put("layout_type", "standard");
        cimData.put("document_info", documentInfo);

        // Questions
        List<Map<String, Object>> questions = List.of(
            createTestQuestion("1", "다음 중 맞는 것은?", List.of("① 선택지1", "② 선택지2")),
            createTestQuestion("2", "아래 그림을 설명하시오.", List.of())
        );
        cimData.put("questions", questions);

        // OCR results
        List<Map<String, Object>> ocrResults = List.of(
            Map.of("id", 1, "text", "1번 문제", "class", "question_number"),
            Map.of("id", 2, "text", "다음 중 맞는 것은?", "class", "question_text"),
            Map.of("id", 3, "text", "① 선택지1", "class", "choice"),
            Map.of("id", 4, "text", "② 선택지2", "class", "choice")
        );
        cimData.put("ocr_results", ocrResults);

        // AI results
        List<Map<String, Object>> aiResults = List.of(
            Map.of("id", 5, "description", "그림: 수학 공식이 표시된 이미지", "class", "figure")
        );
        cimData.put("ai_results", aiResults);

        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("analysis_date", "2024-01-01T10:00:00");
        metadata.put("total_questions", 2);
        cimData.put("metadata", metadata);

        return cimData;
    }

    /**
     * 테스트용 질문 데이터 생성
     */
    private Map<String, Object> createTestQuestion(String number, String text, List<String> choices) {
        Map<String, Object> question = new HashMap<>();
        question.put("question_number", number);
        question.put("section", "기본");

        Map<String, Object> content = new HashMap<>();
        content.put("main_question", text);
        content.put("passage", "");

        List<Map<String, Object>> choicesList = choices.stream()
            .map(choice -> Map.of("choice_text", (Object) choice))
            .collect(java.util.stream.Collectors.toList());
        content.put("choices", choicesList);

        content.put("images", List.of());
        content.put("tables", List.of());
        content.put("explanations", "");

        question.put("question_content", content);

        Map<String, Object> aiAnalysis = new HashMap<>();
        aiAnalysis.put("image_descriptions", List.of());
        aiAnalysis.put("table_analysis", List.of());
        aiAnalysis.put("problem_analysis", List.of());
        question.put("ai_analysis", aiAnalysis);

        return question;
    }
}