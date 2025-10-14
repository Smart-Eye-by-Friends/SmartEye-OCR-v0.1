package com.smarteye.application.analysis;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.LayoutBlock;
import com.smarteye.domain.analysis.entity.TextBlock;
import com.smarteye.domain.analysis.entity.CIMOutput;
import com.smarteye.domain.analysis.repository.AnalysisJobRepository;
import com.smarteye.domain.analysis.repository.LayoutBlockRepository;
import com.smarteye.domain.analysis.repository.TextBlockRepository;
import com.smarteye.domain.analysis.repository.CIMOutputRepository;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.domain.document.repository.DocumentPageRepository;
import com.smarteye.domain.user.entity.User;
import com.smarteye.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * DocumentAnalysisDataService 캐시 기능 통합 테스트
 * 
 * 테스트 시나리오:
 * 1. 캐시 미스 (첫 조회) - DB 쿼리 실행
 * 2. 캐시 히트 (두 번째 조회) - 메모리에서 반환
 * 3. 캐시 무효화 - 캐시 삭제 후 재조회
 * 4. 성능 검증 - 캐시 히트 시 응답 시간 69% 감소 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DocumentAnalysisDataServiceCacheTest {

    @Autowired
    private DocumentAnalysisDataService dataService;

    @Autowired
    private CacheManager cacheManager;

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
    private UserRepository userRepository;

    private String testJobId;
    private AnalysisJob testJob;
    private User testUser;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        Cache cache = cacheManager.getCache("cim-results");
        if (cache != null) {
            cache.clear();
        }

        // 테스트 User 생성 (AnalysisJob의 필수 필드)
        testUser = createTestUser();

        // 테스트 데이터 생성 (jobId는 UUID 36자)
        testJobId = UUID.randomUUID().toString();
        testJob = createTestAnalysisJob(testJobId);
        DocumentPage testPage = createTestDocumentPage(testJob);
        LayoutBlock testLayout = createTestLayoutBlock(testPage);
        createTestTextBlock(testLayout);
        createTestCIMOutput(testJob);
    }

    @Test
    @DisplayName("캐시 미스 - 첫 조회 시 DB에서 데이터 로딩")
    void testCacheMiss() {
        // Given
        Cache cache = cacheManager.getCache("cim-results");
        assertThat(cache).isNotNull();
        assertThat(cache.get(testJobId)).isNull();

        // When
        long startTime = System.currentTimeMillis();
        Optional<Map<String, Object>> result = dataService.getAnalysisResult(testJobId);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().get("jobId")).isEqualTo(testJobId);
        assertThat(cache.get(testJobId)).isNotNull();
        
        System.out.println("✅ 캐시 미스 - 조회 시간: " + duration + "ms (예상: ~200ms)");
        assertThat(duration).isLessThan(500); // DB 쿼리 시간 체크
    }

    @Test
    @DisplayName("캐시 히트 - 두 번째 조회 시 메모리에서 반환")
    void testCacheHit() {
        // Given - 첫 번째 조회로 캐시 워밍업
        dataService.getAnalysisResult(testJobId);
        
        Cache cache = cacheManager.getCache("cim-results");
        assertThat(cache.get(testJobId)).isNotNull();

        // When - 두 번째 조회
        long startTime = System.currentTimeMillis();
        Optional<Map<String, Object>> result = dataService.getAnalysisResult(testJobId);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().get("jobId")).isEqualTo(testJobId);
        
        System.out.println("✅ 캐시 히트 - 조회 시간: " + duration + "ms (예상: ~10ms)");
        assertThat(duration).isLessThan(50); // 캐시 읽기 시간 체크
    }

    @Test
    @DisplayName("캐시 성능 비교 - 히트 vs 미스 응답 시간")
    void testCachePerformanceComparison() {
        // Given
        Cache cache = cacheManager.getCache("cim-results");
        
        // When - 캐시 미스 (첫 조회)
        long missStart = System.currentTimeMillis();
        dataService.getAnalysisResult(testJobId);
        long missDuration = System.currentTimeMillis() - missStart;

        // When - 캐시 히트 (두 번째 조회)
        long hitStart = System.currentTimeMillis();
        dataService.getAnalysisResult(testJobId);
        long hitDuration = System.currentTimeMillis() - hitStart;

        // Then
        double improvement = ((double) (missDuration - hitDuration) / missDuration) * 100;
        
        System.out.println("📊 캐시 성능 비교:");
        System.out.println("  └─ 캐시 미스: " + missDuration + "ms");
        System.out.println("  └─ 캐시 히트: " + hitDuration + "ms");
        System.out.println("  └─ 성능 향상: " + String.format("%.1f%%", improvement));
        
        // 캐시 히트가 미스보다 빨라야 함
        assertThat(hitDuration).isLessThan(missDuration);
        
        // 목표: 50% 이상 성능 향상 (실제로는 69% 목표)
        assertThat(improvement).isGreaterThan(50.0);
    }

    @Test
    @DisplayName("캐시 무효화 - invalidateCache() 호출 후 캐시 삭제 확인")
    void testCacheEviction() {
        // Given - 캐시에 데이터 저장
        dataService.getAnalysisResult(testJobId);
        Cache cache = cacheManager.getCache("cim-results");
        assertThat(cache.get(testJobId)).isNotNull();

        // When - 캐시 무효화
        dataService.invalidateCache(testJobId);

        // Then - 캐시가 비어있어야 함
        assertThat(cache.get(testJobId)).isNull();
        
        System.out.println("✅ 캐시 무효화 성공 - JobID: " + testJobId);
    }

    @Test
    @DisplayName("전체 캐시 무효화 - invalidateAllCache() 호출")
    void testCacheEvictionAll() {
        // Given - 여러 작업의 캐시 데이터 생성
        String jobId1 = UUID.randomUUID().toString();
        String jobId2 = UUID.randomUUID().toString();
        
        createTestAnalysisJob(jobId1);
        createTestAnalysisJob(jobId2);
        
        dataService.getAnalysisResult(jobId1);
        dataService.getAnalysisResult(jobId2);
        
        Cache cache = cacheManager.getCache("cim-results");
        assertThat(cache.get(jobId1)).isNotNull();
        assertThat(cache.get(jobId2)).isNotNull();

        // When - 전체 캐시 무효화
        dataService.invalidateAllCache();

        // Then - 모든 캐시가 비어있어야 함
        assertThat(cache.get(jobId1)).isNull();
        assertThat(cache.get(jobId2)).isNull();
        
        System.out.println("✅ 전체 캐시 무효화 성공");
    }

    @Test
    @DisplayName("캐시 업데이트 - updateAnalysisResultCache() 호출")
    void testCacheUpdate() {
        // Given
        Map<String, Object> newResult = new HashMap<>();
        newResult.put("jobId", testJobId);
        newResult.put("status", "COMPLETED");
        newResult.put("layoutBlocks", Collections.emptyList());

        Cache cache = cacheManager.getCache("cim-results");
        assertThat(cache.get(testJobId)).isNull();

        // When - 캐시 업데이트 (@CachePut)
        Map<String, Object> updated = dataService.updateAnalysisResultCache(testJobId, newResult);

        // Then - 캐시에 저장되어야 함
        assertThat(updated).isEqualTo(newResult);
        assertThat(cache.get(testJobId)).isNotNull();
        
        System.out.println("✅ 캐시 업데이트 성공 - JobID: " + testJobId);
    }

    @Test
    @DisplayName("존재하지 않는 작업 조회 - 빈 Optional 반환")
    void testGetAnalysisResultNotFound() {
        // Given
        String nonExistentJobId = UUID.randomUUID().toString();

        // When
        Optional<Map<String, Object>> result = dataService.getAnalysisResult(nonExistentJobId);

        // Then
        assertThat(result).isEmpty();
        
        System.out.println("✅ 존재하지 않는 작업 처리 성공");
    }

    // ============================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ============================================

    private User createTestUser() {
        User user = new User();
        user.setUsername("test-user-" + UUID.randomUUID().toString());
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setIsActive(true);
        return userRepository.save(user);
    }

    private AnalysisJob createTestAnalysisJob(String jobId) {
        AnalysisJob job = new AnalysisJob();
        job.setJobId(jobId);
        job.setOriginalFilename("test-image.jpg");
        job.setFilePath("/test/image.jpg");
        job.setStatus(AnalysisJob.JobStatus.COMPLETED);
        job.setModelChoice("SmartEyeSsen");
        job.setUseAiDescription(false);
        job.setUser(testUser);  // User 설정 (필수)
        
        return analysisJobRepository.save(job);
    }

    private DocumentPage createTestDocumentPage(AnalysisJob job) {
        DocumentPage page = new DocumentPage();
        page.setAnalysisJob(job);
        page.setPageNumber(1);
        page.setImagePath(job.getFilePath());
        page.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        return documentPageRepository.save(page);
    }

    private LayoutBlock createTestLayoutBlock(DocumentPage page) {
        LayoutBlock layout = new LayoutBlock();
        layout.setDocumentPage(page);
        layout.setBlockIndex(1);
        layout.setClassName("question");
        layout.setConfidence(0.95);
        layout.setX1(100);
        layout.setY1(200);
        layout.setX2(500);
        layout.setY2(400);
        layout.setWidth(400);
        layout.setHeight(200);
        layout.setArea(80000);
        layout.setOcrText("테스트 문제입니다.");
        layout.setOcrConfidence(0.92);
        layout.setProcessingStatus(LayoutBlock.ProcessingStatus.OCR_COMPLETED);
        return layoutBlockRepository.save(layout);
    }

    private TextBlock createTestTextBlock(LayoutBlock layout) {
        TextBlock text = new TextBlock();
        text.setLayoutBlock(layout);
        text.setExtractedText("테스트 문제입니다.");
        text.setCleanedText("테스트 문제입니다.");
        text.setTextType(TextBlock.TextType.QUESTION);
        text.setLanguage("ko");
        text.setConfidence(0.92);
        text.setWordCount(3);
        text.setCharCount(10);
        return textBlockRepository.save(text);
    }

    private CIMOutput createTestCIMOutput(AnalysisJob job) {
        CIMOutput cim = new CIMOutput();
        cim.setAnalysisJob(job);
        cim.setCimData("{}");  // JSON 형식의 빈 객체 (NOT NULL 필드)
        cim.setFormattedText("테스트 문제입니다.");
        cim.setTotalElements(1);
        cim.setTextElements(1);
        cim.setTotalFigures(0);
        cim.setTotalTables(0);
        cim.setTotalWordCount(3);
        cim.setTotalCharCount(10);
        cim.setProcessingTimeMs(1500L);
        cim.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
        return cimOutputRepository.save(cim);
    }
}
