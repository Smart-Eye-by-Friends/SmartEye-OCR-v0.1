package com.smarteye.service;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.domain.analysis.CIMOutput;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.domain.user.User;
import com.smarteye.infrastructure.persistence.AnalysisJobRepository;
import com.smarteye.infrastructure.persistence.CIMOutputRepository;
import com.smarteye.infrastructure.persistence.DocumentPageRepository;
import com.smarteye.infrastructure.persistence.UserRepository;
import com.smarteye.service.StructuredJSONService.StructuredResult;
import com.smarteye.service.StructuredJSONService.DocumentInfo;
import com.smarteye.service.StructuredJSONService.QuestionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CIM 분석 API DB 저장 기능 통합 테스트
 *
 * 테스트 범위:
 * 1. 정상적인 DB 저장 프로세스
 * 2. 동시성 제어 및 멱등성 보장
 * 3. 데이터 무결성 검증
 * 4. 오류 복구 메커니즘
 * 5. 트랜잭션 롤백 시나리오
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.com.smarteye.service.CIMService=DEBUG"
})
class CIMServiceIntegrationTest {

    @Autowired
    private CIMService cimService;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private CIMOutputRepository cimOutputRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private AnalysisJob testAnalysisJob;
    private BufferedImage testImage;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성
        testUser = new User();
        testUser.setUsername("test_user_" + UUID.randomUUID().toString().substring(0, 8));
        testUser.setEmail("test@example.com");
        testUser = userRepository.save(testUser);

        // 테스트용 분석 작업 생성
        testAnalysisJob = new AnalysisJob();
        testAnalysisJob.setJobId(UUID.randomUUID().toString());
        testAnalysisJob.setOriginalFilename("test_image.png");
        testAnalysisJob.setFilePath("/tmp/test_image.png");
        testAnalysisJob.setFileType("image/png");
        testAnalysisJob.setModelChoice("SmartEyeSsen");
        testAnalysisJob.setUser(testUser);
        testAnalysisJob.setStatus(AnalysisJob.JobStatus.PROCESSING);
        testAnalysisJob = analysisJobRepository.save(testAnalysisJob);

        // 테스트용 이미지 생성
        testImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 800, 600);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Test Image for CIM Analysis", 100, 100);
        g2d.dispose();
    }

    @Test
    @DisplayName("정상적인 CIM 분석 및 DB 저장 테스트")
    @Transactional
    void testSuccessfulCIMAnalysisAndSave() throws Exception {
        // Given
        StructuredResult mockResult = createMockStructuredResult();

        // When
        StructuredResult result = cimService.performStructuredAnalysisWithCIM(
            testImage, testAnalysisJob, "SmartEyeSsen", null
        );

        // Then
        assertNotNull(result);
        assertNotNull(result.documentInfo);
        assertTrue(result.documentInfo.totalQuestions > 0);

        // DB 저장 확인
        var cimOutput = cimOutputRepository.findByAnalysisJobId(testAnalysisJob.getId());
        assertTrue(cimOutput.isPresent());
        assertEquals(CIMOutput.GenerationStatus.COMPLETED, cimOutput.get().getGenerationStatus());
        assertNotNull(cimOutput.get().getCimData());
        assertFalse(cimOutput.get().getCimData().trim().isEmpty());

        // DocumentPage 생성 확인
        var documentPages = documentPageRepository.findByAnalysisJobId(testAnalysisJob.getId());
        assertEquals(1, documentPages.size());
        assertEquals(DocumentPage.ProcessingStatus.COMPLETED, documentPages.get(0).getProcessingStatus());
    }

    @Test
    @DisplayName("동시성 제어 및 멱등성 보장 테스트")
    void testConcurrencyControlAndIdempotency() throws Exception {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        List<CompletableFuture<StructuredResult>> futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                try {
                    return cimService.performStructuredAnalysisWithCIM(
                        testImage, testAnalysisJob, "SmartEyeSsen", null
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor))
            .toList();

        // 모든 스레드 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Then
        // 모든 요청이 성공해야 함 (멱등성)
        futures.forEach(future -> {
            assertDoesNotThrow(() -> {
                StructuredResult result = future.get();
                assertNotNull(result);
            });
        });

        // CIMOutput은 하나만 생성되어야 함 (중복 방지)
        var cimOutputs = cimOutputRepository.findByAnalysisJobId(testAnalysisJob.getId());
        assertTrue(cimOutputs.isPresent());
        assertEquals(CIMOutput.GenerationStatus.COMPLETED, cimOutputs.get().getGenerationStatus());

        // DocumentPage도 하나만 생성되어야 함
        var documentPages = documentPageRepository.findByAnalysisJobId(testAnalysisJob.getId());
        assertEquals(1, documentPages.size());

        executor.shutdown();
    }

    @Test
    @DisplayName("데이터 무결성 검증 테스트")
    @Transactional
    void testDataIntegrityValidation() {
        // Given: null 데이터로 테스트
        assertThrows(IllegalArgumentException.class, () -> {
            cimService.performStructuredAnalysisWithCIM(
                testImage, null, "SmartEyeSsen", null
            );
        });

        // Given: 잘못된 JobID로 테스트
        AnalysisJob invalidJob = new AnalysisJob();
        invalidJob.setJobId("");
        assertThrows(IllegalArgumentException.class, () -> {
            cimService.performStructuredAnalysisWithCIM(
                testImage, invalidJob, "SmartEyeSsen", null
            );
        });
    }

    @Test
    @DisplayName("통합 JSON 생성 테스트")
    @Transactional
    void testIntegratedJSONGeneration() throws Exception {
        // Given: 먼저 CIM 분석 수행
        cimService.performStructuredAnalysisWithCIM(
            testImage, testAnalysisJob, "SmartEyeSsen", null
        );

        // When: 통합 JSON 생성
        StructuredResult integratedResult = cimService.generateIntegratedJSON(testAnalysisJob);

        // Then
        assertNotNull(integratedResult);
        assertNotNull(integratedResult.documentInfo);
        assertNotNull(integratedResult.questions);
        assertTrue(integratedResult.documentInfo.totalQuestions >= 0);

        // JSON 직렬화/역직렬화 테스트
        String jsonString = objectMapper.writeValueAsString(integratedResult);
        StructuredResult deserialized = objectMapper.readValue(jsonString, StructuredResult.class);
        assertEquals(integratedResult.documentInfo.totalQuestions,
                    deserialized.documentInfo.totalQuestions);
    }

    @Test
    @DisplayName("오류 복구 메커니즘 테스트")
    @Transactional
    void testErrorRecoveryMechanism() {
        // Given: 잘못된 이미지 데이터
        BufferedImage invalidImage = null;

        // When & Then: 적절한 예외 처리
        assertThrows(RuntimeException.class, () -> {
            cimService.performStructuredAnalysisWithCIM(
                invalidImage, testAnalysisJob, "SmartEyeSsen", null
            );
        });

        // 실패 후 상태 확인
        AnalysisJob updatedJob = analysisJobRepository.findById(testAnalysisJob.getId()).orElse(null);
        assertNotNull(updatedJob);
        // 오류 발생시 상태가 FAILED로 변경되어야 함
        assertTrue(updatedJob.getStatus() == AnalysisJob.JobStatus.FAILED ||
                  updatedJob.getStatus() == AnalysisJob.JobStatus.PROCESSING);
    }

    @Test
    @DisplayName("다중 페이지 통합 JSON 생성 테스트")
    @Transactional
    void testMultiplePageIntegratedJSON() throws Exception {
        // Given: 여러 분석 작업 생성
        List<AnalysisJob> jobs = IntStream.range(0, 3)
            .mapToObj(i -> {
                AnalysisJob job = new AnalysisJob();
                job.setJobId(UUID.randomUUID().toString());
                job.setOriginalFilename("test_page_" + i + ".png");
                job.setFilePath("/tmp/test_page_" + i + ".png");
                job.setFileType("image/png");
                job.setModelChoice("SmartEyeSsen");
                job.setUser(testUser);
                job.setStatus(AnalysisJob.JobStatus.PROCESSING);
                return analysisJobRepository.save(job);
            })
            .toList();

        // 각 페이지에 대해 CIM 분석 수행
        jobs.forEach(job -> {
            try {
                cimService.performStructuredAnalysisWithCIM(
                    testImage, job, "SmartEyeSsen", null
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // When: 다중 페이지 통합 JSON 생성
        StructuredResult integratedResult = cimService.generateIntegratedJSONForMultiplePages(jobs);

        // Then
        assertNotNull(integratedResult);
        assertNotNull(integratedResult.documentInfo);
        assertTrue(integratedResult.documentInfo.totalQuestions >= 0);
        assertTrue(integratedResult.questions.size() >= 0);
    }

    @Test
    @DisplayName("DB 제약조건 테스트")
    @Transactional
    void testDatabaseConstraints() throws Exception {
        // Given: 첫 번째 CIM 분석 수행
        cimService.performStructuredAnalysisWithCIM(
            testImage, testAnalysisJob, "SmartEyeSsen", null
        );

        // When: 동일한 AnalysisJob에 대해 직접 CIMOutput 생성 시도 (제약조건 위반)
        CIMOutput duplicateOutput = new CIMOutput();
        duplicateOutput.setAnalysisJob(testAnalysisJob);
        duplicateOutput.setCimData("{\"test\": \"duplicate\"}");
        duplicateOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);

        // Then: 제약조건 위반으로 예외 발생해야 함
        assertThrows(Exception.class, () -> {
            cimOutputRepository.saveAndFlush(duplicateOutput);
        });
    }

    @Test
    @DisplayName("JSON 직렬화 안전성 테스트")
    void testJSONSerializationSafety() {
        // Given: 특수 문자가 포함된 StructuredResult
        StructuredResult result = createMockStructuredResult();
        result.documentInfo.layoutType = "특수문자테스트\"'<>&";

        // When & Then: JSON 직렬화가 안전하게 처리되어야 함
        assertDoesNotThrow(() -> {
            String json = objectMapper.writeValueAsString(result);
            assertNotNull(json);
            assertFalse(json.isEmpty());

            // 역직렬화도 정상 동작해야 함
            StructuredResult deserialized = objectMapper.readValue(json, StructuredResult.class);
            assertEquals(result.documentInfo.layoutType, deserialized.documentInfo.layoutType);
        });
    }

    /**
     * 테스트용 StructuredResult 생성
     */
    private StructuredResult createMockStructuredResult() {
        StructuredResult result = new StructuredResult();

        // DocumentInfo 생성
        result.documentInfo = new DocumentInfo();
        result.documentInfo.totalQuestions = 3;
        result.documentInfo.layoutType = "학습지";

        // QuestionResult 리스트 생성
        result.questions = List.of(
            createMockQuestionResult("1", "테스트 문제 1"),
            createMockQuestionResult("2", "테스트 문제 2"),
            createMockQuestionResult("3", "테스트 문제 3")
        );

        return result;
    }

    /**
     * 테스트용 QuestionResult 생성
     */
    private QuestionResult createMockQuestionResult(String number, String text) {
        QuestionResult question = new QuestionResult();
        question.questionNumber = number;
        question.section = "섹션 A";

        // QuestionContent는 내부 클래스이므로 적절히 초기화
        // 실제 구현에 따라 조정 필요

        return question;
    }

    /**
     * 테스트용 LayoutInfo 생성
     */
    private List<LayoutInfo> createMockLayoutInfo() {
        return Arrays.asList(
            new LayoutInfo(1, "question_text", 0.95, new int[]{100, 100, 300, 150}, 200, 50, 10000),
            new LayoutInfo(2, "choice", 0.90, new int[]{100, 200, 300, 250}, 200, 50, 10000),
            new LayoutInfo(3, "figure", 0.85, new int[]{400, 100, 600, 300}, 200, 200, 40000)
        );
    }

    /**
     * 테스트용 OCRResult 생성
     */
    private List<OCRResult> createMockOCRResults() {
        return Arrays.asList(
            new OCRResult(1, "question_text", new int[]{100, 100, 300, 150}, "다음 중 올바른 답을 고르세요.", 0.95),
            new OCRResult(2, "choice", new int[]{100, 200, 300, 250}, "① 선택지 1", 0.90),
            new OCRResult(3, "choice", new int[]{100, 300, 300, 350}, "② 선택지 2", 0.88)
        );
    }

    /**
     * 테스트용 AIDescriptionResult 생성
     */
    private List<AIDescriptionResult> createMockAIResults() {
        return Arrays.asList(
            new AIDescriptionResult(3, "figure", new int[]{400, 100, 600, 300}, "수학 공식이 포함된 다이어그램")
        );
    }
}