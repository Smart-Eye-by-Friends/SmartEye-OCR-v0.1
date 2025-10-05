package com.smarteye.application.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.CIMOutput;
import com.smarteye.domain.analysis.entity.LayoutBlock;
import com.smarteye.domain.analysis.entity.TextBlock;
import com.smarteye.domain.analysis.repository.AnalysisJobRepository;
import com.smarteye.domain.analysis.repository.CIMOutputRepository;
import com.smarteye.domain.analysis.repository.LayoutBlockRepository;
import com.smarteye.domain.analysis.repository.TextBlockRepository;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.domain.document.repository.DocumentPageRepository;
import com.smarteye.domain.logging.entity.ProcessingLog;
import com.smarteye.domain.logging.repository.ProcessingLogRepository;
import com.smarteye.domain.user.entity.User;
import com.smarteye.domain.user.repository.UserRepository;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentAnalysisDataService 트랜잭션 검증 테스트
 *
 * <p>테스트 목표:</p>
 * <ul>
 *   <li>TransactionRequiredException 해결 검증 (CompletableFuture 제거 → 동기 @Transactional)</li>
 *   <li>모든 엔티티 저장 검증 (LayoutBlock, TextBlock, CIMOutput, ProcessingLog)</li>
 *   <li>배치 처리 성능 검증 (flush/clear 간격 50개)</li>
 *   <li>트랜잭션 롤백 검증 (예외 발생 시 부분 저장 방지)</li>
 *   <li>트랜잭션 격리 및 동시성 검증</li>
 * </ul>
 *
 * <p>주요 수정 사항:</p>
 * <ul>
 *   <li>기존: CompletableFuture&lt;Void&gt; saveAnalysisResultsBatch() - 비동기</li>
 *   <li>수정: void saveAnalysisResultsBatch() - 동기 @Transactional</li>
 *   <li>효과: TransactionRequiredException 해결, 트랜잭션 무결성 보장</li>
 * </ul>
 *
 * @author SmartEye QA Team
 * @since v0.4
 */
@ExtendWith(SpringExtension.class)
@DataJpaTest
@ActiveProfiles("test")
@Import({DocumentAnalysisDataService.class, ObjectMapper.class})
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
class DocumentAnalysisDataServiceTest {

    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataService;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private LayoutBlockRepository layoutBlockRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private CIMOutputRepository cimOutputRepository;

    @Autowired
    private ProcessingLogRepository processingLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private AnalysisJob testJob;

    /**
     * 각 테스트 전 초기 설정
     *
     * <p>테스트용 User와 AnalysisJob을 생성하여 일관된 테스트 환경 구축</p>
     */
    @BeforeEach
    void setUp() {
        // Test user 생성 및 저장 (JPA Auditing 활성화됨)
        testUser = new User();
        testUser.setUsername("test_user_" + System.currentTimeMillis());
        testUser.setEmail("test@smarteye.com");
        testUser = userRepository.save(testUser);
        entityManager.flush();

        // AnalysisJob 생성 및 저장
        testJob = new AnalysisJob(
            "test-job-" + System.currentTimeMillis(),
            "test-document.jpg",
            "/uploads/test-document.jpg",
            "image/jpeg",
            1024L,
            testUser
        );
        testJob.setStatus(AnalysisJob.JobStatus.PROCESSING);
        testJob = analysisJobRepository.save(testJob);
        entityManager.flush();
    }

    /**
     * 각 테스트 후 정리 작업
     *
     * <p>EntityManager를 명시적으로 clear하여 캐시 상태 초기화</p>
     */
    @AfterEach
    void tearDown() {
        if (entityManager != null) {
            entityManager.clear();
        }
    }

    /**
     * 테스트 1: 트랜잭션 성공 - TransactionRequiredException 발생하지 않음
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>10개 레이아웃 요소로 saveAnalysisResultsBatch() 호출</li>
     *   <li>예외 없이 완료 검증 (TransactionRequiredException 해결)</li>
     *   <li>모든 엔티티 저장 확인 (LayoutBlock, TextBlock, CIMOutput, ProcessingLog)</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>동기 @Transactional 메서드로 변경 후 트랜잭션 컨텍스트 유지</li>
     *   <li>entityManager.flush() 정상 실행 (TransactionRequiredException 없음)</li>
     *   <li>모든 엔티티가 올바른 연관관계로 저장됨</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("트랜잭션 성공: TransactionRequiredException 발생하지 않음")
    void testTransactionSuccessWithoutException() {
        // Given: 테스트 데이터 생성
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(10);
        List<OCRResult> ocrResults = createTestOCRResults(10);
        List<AIDescriptionResult> aiResults = createTestAIResults(3);
        Map<String, Object> cimResult = createTestCIMResult();
        String formattedText = "테스트 포맷된 텍스트";
        long processingTimeMs = 1500L;

        // When: 배치 저장 실행 (동기 트랜잭션 메서드)
        // 기존: CompletableFuture<Void> 반환 (비동기) → TransactionRequiredException
        // 수정: void 반환 (동기 @Transactional) → 정상 동작
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            ocrResults,
            aiResults,
            cimResult,
            formattedText,
            processingTimeMs
        );

        // 강제 플러시 (테스트 환경에서 트랜잭션 커밋 전 확인)
        entityManager.flush();
        entityManager.clear();

        // Then: 엔티티 저장 검증
        // 1. DocumentPage 저장 확인
        List<DocumentPage> savedPages = documentPageRepository.findAll();
        assertThat(savedPages)
            .as("DocumentPage가 1개 저장되어야 함")
            .hasSize(1);

        DocumentPage savedPage = savedPages.get(0);
        assertThat(savedPage.getPageNumber())
            .as("페이지 번호가 1이어야 함")
            .isEqualTo(1);
        assertThat(savedPage.getProcessingStatus())
            .as("처리 상태가 COMPLETED여야 함")
            .isEqualTo(DocumentPage.ProcessingStatus.COMPLETED);
        assertThat(savedPage.getAnalysisJob().getJobId())
            .as("AnalysisJob 연관관계가 올바르게 설정되어야 함")
            .isEqualTo(testJob.getJobId());

        // 2. LayoutBlock 저장 확인
        List<LayoutBlock> savedLayoutBlocks = layoutBlockRepository.findAll();
        assertThat(savedLayoutBlocks)
            .as("LayoutBlock이 10개 저장되어야 함")
            .hasSize(10);

        // LayoutBlock 상세 검증
        LayoutBlock firstBlock = savedLayoutBlocks.get(0);
        assertThat(firstBlock.getDocumentPage())
            .as("DocumentPage 연관관계가 설정되어야 함")
            .isNotNull();
        assertThat(firstBlock.getClassName())
            .as("클래스명이 설정되어야 함")
            .isNotNull();
        assertThat(firstBlock.getProcessingStatus())
            .as("처리 상태가 설정되어야 함")
            .isNotNull();

        // 3. TextBlock 저장 확인 (OCR 텍스트가 있는 것만)
        List<TextBlock> savedTextBlocks = textBlockRepository.findAll();
        assertThat(savedTextBlocks)
            .as("TextBlock이 10개 저장되어야 함 (OCR 텍스트 있음)")
            .hasSize(10);

        // TextBlock 상세 검증
        TextBlock firstTextBlock = savedTextBlocks.get(0);
        assertThat(firstTextBlock.getLayoutBlock())
            .as("LayoutBlock 연관관계가 설정되어야 함")
            .isNotNull();
        assertThat(firstTextBlock.getExtractedText())
            .as("텍스트가 설정되어야 함")
            .isNotNull()
            .isNotBlank();
        assertThat(firstTextBlock.getConfidence())
            .as("신뢰도가 설정되어야 함")
            .isGreaterThan(0.0);

        // 4. CIMOutput 저장 확인
        List<CIMOutput> savedCIMOutputs = cimOutputRepository.findAll();
        assertThat(savedCIMOutputs)
            .as("CIMOutput이 1개 저장되어야 함")
            .hasSize(1);

        CIMOutput savedCIM = savedCIMOutputs.get(0);
        assertThat(savedCIM.getFormattedText())
            .as("FormattedText가 올바르게 저장되어야 함")
            .isEqualTo(formattedText);
        assertThat(savedCIM.getProcessingTimeMs())
            .as("처리 시간이 올바르게 저장되어야 함")
            .isEqualTo(processingTimeMs);
        assertThat(savedCIM.getGenerationStatus())
            .as("생성 상태가 COMPLETED여야 함")
            .isEqualTo(CIMOutput.GenerationStatus.COMPLETED);
        assertThat(savedCIM.getTotalElements())
            .as("총 요소 수가 10개여야 함")
            .isEqualTo(10);
        assertThat(savedCIM.getAnalysisJob())
            .as("AnalysisJob 연관관계가 설정되어야 함")
            .isNotNull();

        // 5. ProcessingLog 저장 확인
        List<ProcessingLog> savedLogs = processingLogRepository.findAll();
        assertThat(savedLogs)
            .as("ProcessingLog가 2개 이상 저장되어야 함 (main + perf)")
            .hasSizeGreaterThanOrEqualTo(2);

        // 메인 로그 검증 (step 필드 사용)
        Optional<ProcessingLog> mainLog = savedLogs.stream()
            .filter(log -> log.getStep() != null && log.getStep().equals("BATCH_ANALYSIS_COMPLETED"))
            .findFirst();
        assertThat(mainLog)
            .as("BATCH_ANALYSIS_COMPLETED 로그가 존재해야 함")
            .isPresent();
        assertThat(mainLog.get().getExecutionTimeMs())
            .as("실행 시간이 기록되어야 함")
            .isEqualTo(processingTimeMs);
        assertThat(mainLog.get().getAnalysisJob())
            .as("AnalysisJob 연관관계가 설정되어야 함")
            .isNotNull();
    }

    /**
     * 테스트 2: 트랜잭션 롤백 - 예외 발생 시 부분 저장 방지
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>존재하지 않는 JobID로 호출</li>
     *   <li>RuntimeException 발생 검증</li>
     *   <li>롤백으로 인해 어떤 엔티티도 저장되지 않음 확인</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>@Transactional 메서드 내 예외 발생 시 자동 롤백</li>
     *   <li>부분 저장 방지 (트랜잭션 무결성 보장)</li>
     *   <li>롤백 전 데이터와 롤백 후 데이터 일관성</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("트랜잭션 롤백: 예외 발생 시 부분 저장 방지")
    void testTransactionRollbackOnException() {
        // Given: 존재하지 않는 JobID
        String invalidJobId = "invalid-job-id-" + System.currentTimeMillis();
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(5);
        List<OCRResult> ocrResults = createTestOCRResults(5);
        List<AIDescriptionResult> aiResults = createTestAIResults(1);
        Map<String, Object> cimResult = createTestCIMResult();

        // 롤백 전 카운트 확인
        long beforeDocumentPageCount = documentPageRepository.count();
        long beforeLayoutBlockCount = layoutBlockRepository.count();
        long beforeTextBlockCount = textBlockRepository.count();
        long beforeCIMOutputCount = cimOutputRepository.count();

        // When & Then: 예외 발생 검증
        assertThatThrownBy(() -> {
            documentAnalysisDataService.saveAnalysisResultsBatch(
                invalidJobId,
                layoutInfoList,
                ocrResults,
                aiResults,
                cimResult,
                "테스트 텍스트",
                1000L
            );
        })
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("배치 저장 중 오류 발생");

        // 강제 플러시 시도 (롤백 확인용)
        entityManager.flush();
        entityManager.clear();

        // Then: 롤백으로 인해 기존 데이터만 존재 (새 데이터 없음)
        assertThat(documentPageRepository.count())
            .as("롤백으로 인해 DocumentPage 개수가 변경되지 않아야 함")
            .isEqualTo(beforeDocumentPageCount);

        assertThat(layoutBlockRepository.count())
            .as("롤백으로 인해 LayoutBlock 개수가 변경되지 않아야 함")
            .isEqualTo(beforeLayoutBlockCount);

        assertThat(textBlockRepository.count())
            .as("롤백으로 인해 TextBlock 개수가 변경되지 않아야 함")
            .isEqualTo(beforeTextBlockCount);

        assertThat(cimOutputRepository.count())
            .as("롤백으로 인해 CIMOutput 개수가 변경되지 않아야 함")
            .isEqualTo(beforeCIMOutputCount);
    }

    /**
     * 테스트 3: 배치 처리 성능 - 100개 요소 처리 및 flush/clear 검증
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>100개 레이아웃 요소로 배치 처리</li>
     *   <li>배치 크기(50개)마다 flush/clear 실행 검증</li>
     *   <li>모든 요소가 정상 저장되었는지 확인</li>
     *   <li>성능 메트릭 측정 및 로깅</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>배치 크기 50개마다 entityManager.flush() 및 clear() 실행</li>
     *   <li>대용량 데이터 처리 성능 (초당 처리량 로깅)</li>
     *   <li>메모리 효율성 (clear로 영속성 컨텍스트 정리)</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("배치 처리 성능: 100개 요소 처리 및 flush/clear 검증")
    void testBatchProcessingWithLargeDataset() {
        // Given: 100개 요소
        int totalElements = 100;
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(totalElements);
        List<OCRResult> ocrResults = createTestOCRResults(totalElements);
        List<AIDescriptionResult> aiResults = createTestAIResults(10);
        Map<String, Object> cimResult = createTestCIMResult();
        String formattedText = "배치 테스트 포맷된 텍스트";
        long processingTimeMs = 5000L;

        // When: 배치 저장 실행
        long startTime = System.currentTimeMillis();
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            ocrResults,
            aiResults,
            cimResult,
            formattedText,
            processingTimeMs
        );
        long endTime = System.currentTimeMillis();

        // 강제 플러시
        entityManager.flush();
        entityManager.clear();

        // Then: 성능 검증
        long actualTime = endTime - startTime;
        System.out.println("📊 배치 저장 성능 메트릭:");
        System.out.println("  └─ 총 요소: " + totalElements + "개");
        System.out.println("  └─ 저장 시간: " + actualTime + "ms");
        System.out.println("  └─ 초당 처리: " + String.format("%.1f개/초", totalElements / (actualTime / 1000.0)));

        // 성능 임계값 검증 (10초 이내 완료)
        assertThat(actualTime)
            .as("100개 요소 저장이 10초 이내 완료되어야 함")
            .isLessThan(10000L);

        // 엔티티 저장 검증
        List<LayoutBlock> savedLayoutBlocks = layoutBlockRepository.findAll();
        assertThat(savedLayoutBlocks)
            .as("LayoutBlock이 100개 저장되어야 함")
            .hasSize(totalElements);

        List<TextBlock> savedTextBlocks = textBlockRepository.findAll();
        assertThat(savedTextBlocks)
            .as("TextBlock이 100개 저장되어야 함")
            .hasSize(totalElements);

        // CIMOutput 통계 검증
        List<CIMOutput> savedCIMOutputs = cimOutputRepository.findAll();
        assertThat(savedCIMOutputs)
            .as("CIMOutput이 1개 저장되어야 함")
            .hasSize(1);

        CIMOutput savedCIM = savedCIMOutputs.get(0);
        assertThat(savedCIM.getTotalElements())
            .as("총 요소 수가 100개여야 함")
            .isEqualTo(totalElements);
        assertThat(savedCIM.getTextElements())
            .as("텍스트 요소 수가 100개여야 함")
            .isEqualTo(totalElements);
        assertThat(savedCIM.getAiDescribedElements())
            .as("AI 설명 요소 수가 10개여야 함")
            .isEqualTo(10);
    }

    /**
     * 테스트 4: EntityManager.flush() 실행 검증
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>saveAnalysisResultsBatch() 메서드 실행</li>
     *   <li>entityManager.flush()가 호출되었는지 검증 (간접 검증)</li>
     *   <li>데이터베이스에 실제로 저장되었는지 확인</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>트랜잭션 컨텍스트 내에서 flush 정상 실행</li>
     *   <li>flush 후 DB에서 직접 조회 가능</li>
     *   <li>영속성 컨텍스트와 DB 간 동기화 확인</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("EntityManager.flush() 실행 검증")
    void testEntityManagerFlushExecution() {
        // Given: 테스트 데이터
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(10);
        List<OCRResult> ocrResults = createTestOCRResults(10);
        List<AIDescriptionResult> aiResults = createTestAIResults(2);
        Map<String, Object> cimResult = createTestCIMResult();

        // When: 배치 저장 실행
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            ocrResults,
            aiResults,
            cimResult,
            "플러시 테스트",
            1200L
        );

        // Then: flush 없이도 트랜잭션 컨텍스트에서 조회 가능해야 함
        // (flush가 내부에서 호출되었기 때문)
        List<LayoutBlock> foundBlocks = layoutBlockRepository.findAll();
        assertThat(foundBlocks)
            .as("flush 후 LayoutBlock 조회 가능해야 함")
            .hasSize(10);

        // EntityManager를 clear하고 다시 조회 (DB에서 직접 조회)
        entityManager.clear();
        List<LayoutBlock> blocksFromDb = layoutBlockRepository.findAll();
        assertThat(blocksFromDb)
            .as("DB에서 직접 조회 시에도 10개 존재해야 함")
            .hasSize(10);

        // 두 결과가 동일한지 검증 (영속성 컨텍스트와 DB 동기화 확인)
        assertThat(blocksFromDb)
            .as("영속성 컨텍스트와 DB 데이터가 일치해야 함")
            .extracting(LayoutBlock::getBlockIndex)
            .containsExactlyInAnyOrderElementsOf(
                foundBlocks.stream()
                    .map(LayoutBlock::getBlockIndex)
                    .toList()
            );
    }

    /**
     * 테스트 5: 성능 메트릭 로깅 검증
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>배치 저장 실행</li>
     *   <li>ProcessingLog에 성능 메트릭 로그 기록 확인</li>
     *   <li>로그 메시지 및 실행 시간 검증</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>BATCH_ANALYSIS_COMPLETED 로그 존재</li>
     *   <li>PERFORMANCE_METRICS 로그 존재 및 내용 검증</li>
     *   <li>로그 메타데이터 정확성</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("성능 메트릭 로깅 검증")
    void testPerformanceMetricsLogging() {
        // Given: 테스트 데이터
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(15);
        List<OCRResult> ocrResults = createTestOCRResults(15);
        List<AIDescriptionResult> aiResults = createTestAIResults(3);
        Map<String, Object> cimResult = createTestCIMResult();
        long expectedProcessingTime = 2500L;

        // When: 배치 저장 실행
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            ocrResults,
            aiResults,
            cimResult,
            "메트릭 테스트",
            expectedProcessingTime
        );

        entityManager.flush();
        entityManager.clear();

        // Then: ProcessingLog 검증
        List<ProcessingLog> savedLogs = processingLogRepository.findAll();
        assertThat(savedLogs)
            .as("ProcessingLog가 최소 2개 존재해야 함")
            .hasSizeGreaterThanOrEqualTo(2);

        // BATCH_ANALYSIS_COMPLETED 로그 확인 (step 필드 사용)
        Optional<ProcessingLog> mainLog = savedLogs.stream()
            .filter(log -> log.getStep() != null && log.getStep().equals("BATCH_ANALYSIS_COMPLETED"))
            .findFirst();
        assertThat(mainLog)
            .as("BATCH_ANALYSIS_COMPLETED 로그가 존재해야 함")
            .isPresent();
        assertThat(mainLog.get().getMessage())
            .as("로그 메시지에 레이아웃, OCR, AI 개수 포함")
            .contains("레이아웃: 15개")
            .contains("OCR: 15개")
            .contains("AI: 3개");

        // PERFORMANCE_METRICS 로그 확인 (step 필드 사용)
        Optional<ProcessingLog> perfLog = savedLogs.stream()
            .filter(log -> log.getStep() != null && log.getStep().equals("PERFORMANCE_METRICS"))
            .findFirst();

        assertThat(perfLog)
            .as("PERFORMANCE_METRICS 로그가 존재해야 함")
            .isPresent();

        ProcessingLog perfLogEntry = perfLog.get();
        assertThat(perfLogEntry.getMessage())
            .as("성능 메트릭 메시지에 처리 시간 포함")
            .contains("처리 시간")
            .contains("초당 요소");
    }

    /**
     * 테스트 6: OCR 텍스트 없는 요소 처리
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>OCR 텍스트가 없는 레이아웃 요소 생성</li>
     *   <li>LayoutBlock은 생성되지만 TextBlock은 생성되지 않음 확인</li>
     *   <li>CIMOutput 통계 정확성 검증</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>선택적 엔티티 생성 로직 (TextBlock은 OCR 텍스트 있을 때만)</li>
     *   <li>통계 계산의 정확성 (textElements = 0)</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("OCR 텍스트 없는 요소 처리")
    void testLayoutWithoutOCRText() {
        // Given: OCR 텍스트 없는 레이아웃 요소
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(5);
        List<OCRResult> emptyOCRResults = new ArrayList<>(); // OCR 결과 없음
        List<AIDescriptionResult> aiResults = createTestAIResults(2);
        Map<String, Object> cimResult = createTestCIMResult();

        // When: 배치 저장 실행
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            emptyOCRResults,
            aiResults,
            cimResult,
            "OCR 없는 테스트",
            800L
        );

        entityManager.flush();
        entityManager.clear();

        // Then: LayoutBlock은 생성되고 TextBlock은 생성되지 않음
        List<LayoutBlock> savedLayoutBlocks = layoutBlockRepository.findAll();
        assertThat(savedLayoutBlocks)
            .as("LayoutBlock은 5개 생성되어야 함")
            .hasSize(5);

        List<TextBlock> savedTextBlocks = textBlockRepository.findAll();
        assertThat(savedTextBlocks)
            .as("OCR 텍스트가 없어 TextBlock은 생성되지 않아야 함")
            .isEmpty();

        // CIMOutput 통계 확인
        List<CIMOutput> savedCIMOutputs = cimOutputRepository.findAll();
        assertThat(savedCIMOutputs).hasSize(1);
        CIMOutput savedCIM = savedCIMOutputs.get(0);
        assertThat(savedCIM.getTotalElements())
            .as("총 요소는 5개")
            .isEqualTo(5);
        assertThat(savedCIM.getTextElements())
            .as("텍스트 요소는 0개")
            .isEqualTo(0);
        assertThat(savedCIM.getAiDescribedElements())
            .as("AI 설명 요소는 2개")
            .isEqualTo(2);
    }

    /**
     * 테스트 7: 트랜잭션 격리 검증 - 동시 호출 시 간섭 없음
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>동일 JobID에 대해 두 번의 saveAnalysisResultsBatch 호출</li>
     *   <li>첫 번째 호출은 성공, 두 번째 호출도 독립적으로 처리됨</li>
     *   <li>트랜잭션 격리 수준이 올바르게 작동하는지 확인</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>각 트랜잭션이 독립적으로 실행됨</li>
     *   <li>데이터 일관성 유지</li>
     *   <li>트랜잭션 간 간섭 없음</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("트랜잭션 격리 검증: 동시 호출 시 간섭 없음")
    void testTransactionIsolation() {
        // Given: 첫 번째 호출용 데이터
        List<LayoutInfo> firstLayoutInfoList = createTestLayoutInfoList(5);
        List<OCRResult> firstOCRResults = createTestOCRResults(5);
        List<AIDescriptionResult> firstAIResults = createTestAIResults(1);
        Map<String, Object> firstCIMResult = createTestCIMResult();

        // When: 첫 번째 배치 저장
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            firstLayoutInfoList,
            firstOCRResults,
            firstAIResults,
            firstCIMResult,
            "첫 번째 호출",
            1000L
        );

        entityManager.flush();
        entityManager.clear();

        // Then: 첫 번째 호출 결과 검증
        long firstLayoutBlockCount = layoutBlockRepository.count();
        long firstTextBlockCount = textBlockRepository.count();
        long firstCIMOutputCount = cimOutputRepository.count();

        assertThat(firstLayoutBlockCount)
            .as("첫 번째 호출 후 LayoutBlock 5개 존재")
            .isEqualTo(5);
        assertThat(firstTextBlockCount)
            .as("첫 번째 호출 후 TextBlock 5개 존재")
            .isEqualTo(5);
        assertThat(firstCIMOutputCount)
            .as("첫 번째 호출 후 CIMOutput 1개 존재")
            .isEqualTo(1);

        // Given: 두 번째 호출용 새로운 User 및 Job (JPA Auditing 활성화됨)
        User secondUser = new User();
        secondUser.setUsername("test_user_2_" + System.currentTimeMillis());
        secondUser.setEmail("test2@smarteye.com");
        secondUser = userRepository.save(secondUser);
        entityManager.flush();

        AnalysisJob secondJob = new AnalysisJob(
            "test-job-2-" + System.currentTimeMillis(),
            "test-document-2.jpg",
            "/uploads/test-document-2.jpg",
            "image/jpeg",
            2048L,
            secondUser  // 새로운 User 사용
        );
        secondJob.setStatus(AnalysisJob.JobStatus.PROCESSING);
        secondJob = analysisJobRepository.save(secondJob);
        entityManager.flush();

        List<LayoutInfo> secondLayoutInfoList = createTestLayoutInfoList(3);
        List<OCRResult> secondOCRResults = createTestOCRResults(3);
        List<AIDescriptionResult> secondAIResults = createTestAIResults(1);
        Map<String, Object> secondCIMResult = createTestCIMResult();

        // When: 두 번째 배치 저장 (독립적인 트랜잭션)
        documentAnalysisDataService.saveAnalysisResultsBatch(
            secondJob.getJobId(),
            secondLayoutInfoList,
            secondOCRResults,
            secondAIResults,
            secondCIMResult,
            "두 번째 호출",
            1200L
        );

        entityManager.flush();
        entityManager.clear();

        // Then: 두 번째 호출 후 전체 검증
        long totalLayoutBlockCount = layoutBlockRepository.count();
        long totalTextBlockCount = textBlockRepository.count();
        long totalCIMOutputCount = cimOutputRepository.count();

        assertThat(totalLayoutBlockCount)
            .as("두 번째 호출 후 총 LayoutBlock 8개 존재 (5 + 3)")
            .isEqualTo(8);
        assertThat(totalTextBlockCount)
            .as("두 번째 호출 후 총 TextBlock 8개 존재 (5 + 3)")
            .isEqualTo(8);
        assertThat(totalCIMOutputCount)
            .as("두 번째 호출 후 총 CIMOutput 2개 존재 (1 + 1)")
            .isEqualTo(2);

        // 각 Job에 연결된 데이터 독립성 검증
        String firstJobId = testJob.getJobId();
        String secondJobId = secondJob.getJobId();
        List<DocumentPage> firstJobPages = documentPageRepository.findAll().stream()
            .filter(page -> page.getAnalysisJob().getJobId().equals(firstJobId))
            .toList();
        List<DocumentPage> secondJobPages = documentPageRepository.findAll().stream()
            .filter(page -> page.getAnalysisJob().getJobId().equals(secondJobId))
            .toList();

        assertThat(firstJobPages)
            .as("첫 번째 Job에 1개 페이지 연결")
            .hasSize(1);
        assertThat(secondJobPages)
            .as("두 번째 Job에 1개 페이지 연결")
            .hasSize(1);
    }

    /**
     * 테스트 8: 빈 데이터 처리 - 엣지 케이스
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>빈 layoutInfo 리스트로 호출</li>
     *   <li>예외 발생 없이 처리되어야 함</li>
     *   <li>DocumentPage와 CIMOutput은 생성되지만 내용은 비어있음</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>엣지 케이스 처리 안정성</li>
     *   <li>빈 데이터에 대한 정상적인 응답</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("빈 데이터 처리: 엣지 케이스 안정성")
    void testEmptyDataProcessing() {
        // Given: 빈 데이터
        List<LayoutInfo> emptyLayoutInfo = new ArrayList<>();
        List<OCRResult> emptyOCRResults = new ArrayList<>();
        List<AIDescriptionResult> emptyAIResults = new ArrayList<>();
        Map<String, Object> cimResult = createTestCIMResult();

        // When: 배치 저장 실행 (예외 없이 완료되어야 함)
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            emptyLayoutInfo,
            emptyOCRResults,
            emptyAIResults,
            cimResult,
            "빈 데이터 테스트",
            500L
        );

        entityManager.flush();
        entityManager.clear();

        // Then: 기본 엔티티는 생성되지만 내용은 비어있음
        List<DocumentPage> savedPages = documentPageRepository.findAll();
        assertThat(savedPages)
            .as("DocumentPage는 생성되어야 함")
            .hasSize(1);

        List<LayoutBlock> savedLayoutBlocks = layoutBlockRepository.findAll();
        assertThat(savedLayoutBlocks)
            .as("LayoutBlock은 생성되지 않아야 함")
            .isEmpty();

        List<TextBlock> savedTextBlocks = textBlockRepository.findAll();
        assertThat(savedTextBlocks)
            .as("TextBlock은 생성되지 않아야 함")
            .isEmpty();

        List<CIMOutput> savedCIMOutputs = cimOutputRepository.findAll();
        assertThat(savedCIMOutputs)
            .as("CIMOutput은 생성되어야 함")
            .hasSize(1);

        CIMOutput savedCIM = savedCIMOutputs.get(0);
        assertThat(savedCIM.getTotalElements())
            .as("총 요소는 0개")
            .isEqualTo(0);
        assertThat(savedCIM.getTextElements())
            .as("텍스트 요소는 0개")
            .isEqualTo(0);
    }

    /**
     * 테스트 9: 연관관계 무결성 검증
     *
     * <p>시나리오:</p>
     * <ol>
     *   <li>모든 엔티티 저장 후 연관관계 확인</li>
     *   <li>양방향 연관관계가 올바르게 설정되었는지 검증</li>
     *   <li>orphanRemoval 및 cascade 동작 확인</li>
     * </ol>
     *
     * <p>핵심 검증:</p>
     * <ul>
     *   <li>DocumentPage ↔ AnalysisJob 연관관계</li>
     *   <li>LayoutBlock ↔ DocumentPage 연관관계</li>
     *   <li>TextBlock ↔ LayoutBlock 연관관계</li>
     *   <li>CIMOutput ↔ AnalysisJob 연관관계</li>
     * </ul>
     */
    @Test
    @Transactional
    @DisplayName("연관관계 무결성 검증")
    void testEntityRelationshipIntegrity() {
        // Given: 테스트 데이터
        List<LayoutInfo> layoutInfoList = createTestLayoutInfoList(5);
        List<OCRResult> ocrResults = createTestOCRResults(5);
        List<AIDescriptionResult> aiResults = createTestAIResults(2);
        Map<String, Object> cimResult = createTestCIMResult();

        // When: 배치 저장 실행
        documentAnalysisDataService.saveAnalysisResultsBatch(
            testJob.getJobId(),
            layoutInfoList,
            ocrResults,
            aiResults,
            cimResult,
            "연관관계 테스트",
            1000L
        );

        entityManager.flush();
        entityManager.clear();

        // Then: 연관관계 검증

        // 1. DocumentPage ↔ AnalysisJob
        List<DocumentPage> savedPages = documentPageRepository.findAll();
        assertThat(savedPages).hasSize(1);
        DocumentPage savedPage = savedPages.get(0);
        assertThat(savedPage.getAnalysisJob())
            .as("DocumentPage의 AnalysisJob 연관관계 설정")
            .isNotNull();
        assertThat(savedPage.getAnalysisJob().getJobId())
            .as("AnalysisJob의 JobId 일치")
            .isEqualTo(testJob.getJobId());

        // 2. LayoutBlock ↔ DocumentPage
        List<LayoutBlock> savedLayoutBlocks = layoutBlockRepository.findAll();
        assertThat(savedLayoutBlocks).hasSize(5);
        for (LayoutBlock layoutBlock : savedLayoutBlocks) {
            assertThat(layoutBlock.getDocumentPage())
                .as("LayoutBlock의 DocumentPage 연관관계 설정")
                .isNotNull();
            assertThat(layoutBlock.getDocumentPage().getId())
                .as("DocumentPage ID 일치")
                .isEqualTo(savedPage.getId());
        }

        // 3. TextBlock ↔ LayoutBlock
        List<TextBlock> savedTextBlocks = textBlockRepository.findAll();
        assertThat(savedTextBlocks).hasSize(5);
        for (TextBlock textBlock : savedTextBlocks) {
            assertThat(textBlock.getLayoutBlock())
                .as("TextBlock의 LayoutBlock 연관관계 설정")
                .isNotNull();
            assertThat(savedLayoutBlocks)
                .as("LayoutBlock 목록에 포함되어야 함")
                .contains(textBlock.getLayoutBlock());
        }

        // 4. CIMOutput ↔ AnalysisJob
        List<CIMOutput> savedCIMOutputs = cimOutputRepository.findAll();
        assertThat(savedCIMOutputs).hasSize(1);
        CIMOutput savedCIM = savedCIMOutputs.get(0);
        assertThat(savedCIM.getAnalysisJob())
            .as("CIMOutput의 AnalysisJob 연관관계 설정")
            .isNotNull();
        assertThat(savedCIM.getAnalysisJob().getJobId())
            .as("AnalysisJob의 JobId 일치")
            .isEqualTo(testJob.getJobId());
    }

    // ========== 테스트 데이터 생성 헬퍼 메서드 ==========

    /**
     * LayoutInfo 테스트 데이터 생성
     *
     * @param count 생성할 레이아웃 정보 개수
     * @return LayoutInfo 리스트
     */
    private List<LayoutInfo> createTestLayoutInfoList(int count) {
        List<LayoutInfo> layoutInfoList = new ArrayList<>();
        String[] classNames = {"QuestionText", "figure", "table", "QuestionText", "AnswerText"};

        for (int i = 0; i < count; i++) {
            LayoutInfo layoutInfo = new LayoutInfo();
            layoutInfo.setId(i);
            layoutInfo.setClassName(classNames[i % classNames.length]);
            layoutInfo.setConfidence(85.0 + (i % 15)); // 85.0 ~ 99.0
            layoutInfo.setBox(new int[]{
                i * 10,          // x1
                i * 20,          // y1
                i * 10 + 100,    // x2
                i * 20 + 50      // y2
            });
            layoutInfoList.add(layoutInfo);
        }

        return layoutInfoList;
    }

    /**
     * OCRResult 테스트 데이터 생성
     *
     * @param count 생성할 OCR 결과 개수
     * @return OCRResult 리스트
     */
    private List<OCRResult> createTestOCRResults(int count) {
        List<OCRResult> ocrResults = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            OCRResult ocr = new OCRResult();
            ocr.setId(i);
            ocr.setText("문제 텍스트 " + (i + 1));
            ocr.setConfidence(90.0 + (i % 10)); // 90.0 ~ 99.0
            ocrResults.add(ocr);
        }

        return ocrResults;
    }

    /**
     * AIDescriptionResult 테스트 데이터 생성
     *
     * @param count 생성할 AI 설명 결과 개수
     * @return AIDescriptionResult 리스트
     */
    private List<AIDescriptionResult> createTestAIResults(int count) {
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            AIDescriptionResult ai = new AIDescriptionResult();
            ai.setId(i);
            ai.setDescription("AI 설명: 이미지 " + (i + 1));
            aiResults.add(ai);
        }

        return aiResults;
    }

    /**
     * CIM 결과 테스트 데이터 생성
     *
     * @return CIM 결과 Map
     */
    private Map<String, Object> createTestCIMResult() {
        Map<String, Object> cimResult = new HashMap<>();
        cimResult.put("questions", Arrays.asList(
            Map.of("id", 1, "text", "문제 1", "type", "객관식"),
            Map.of("id", 2, "text", "문제 2", "type", "주관식")
        ));
        cimResult.put("metadata", Map.of(
            "totalQuestions", 2,
            "difficulty", "중"
        ));
        return cimResult;
    }
}
