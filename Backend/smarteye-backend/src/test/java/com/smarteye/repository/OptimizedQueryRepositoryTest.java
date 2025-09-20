package com.smarteye.repository;

import com.smarteye.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OptimizedQueryRepository 테스트
 * 특히 processingTimeMs NULL 안전성 검증
 */
@DataJpaTest
@ActiveProfiles("test")
class OptimizedQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OptimizedQueryRepository optimizedQueryRepository;

    private User testUser;
    private AnalysisJob jobWithProcessingTime;
    private AnalysisJob jobWithoutProcessingTime;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@smarteye.com");
        entityManager.persist(testUser);

        // processingTimeMs가 있는 작업 생성
        jobWithProcessingTime = new AnalysisJob("job-001", "test-file-1.pdf", testUser);
        jobWithProcessingTime.setStatus(AnalysisJob.JobStatus.COMPLETED);
        jobWithProcessingTime.setProcessingTimeMs(5000L); // 5초
        jobWithProcessingTime.setCreatedAt(LocalDateTime.now().minusHours(1));
        jobWithProcessingTime.setCompletedAt(LocalDateTime.now());
        entityManager.persist(jobWithProcessingTime);

        // processingTimeMs가 NULL인 작업 생성
        jobWithoutProcessingTime = new AnalysisJob("job-002", "test-file-2.pdf", testUser);
        jobWithoutProcessingTime.setStatus(AnalysisJob.JobStatus.COMPLETED);
        jobWithoutProcessingTime.setProcessingTimeMs(null); // NULL 값
        jobWithoutProcessingTime.setCreatedAt(LocalDateTime.now().minusHours(2));
        jobWithoutProcessingTime.setCompletedAt(LocalDateTime.now());
        entityManager.persist(jobWithoutProcessingTime);

        // NULL인 작업에 대해 페이지 레벨 처리시간 추가
        DocumentPage page1 = new DocumentPage(1, "/test/page1.jpg", jobWithoutProcessingTime);
        page1.setProcessingTimeMs(2000L); // 2초
        page1.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        entityManager.persist(page1);

        DocumentPage page2 = new DocumentPage(2, "/test/page2.jpg", jobWithoutProcessingTime);
        page2.setProcessingTimeMs(3000L); // 3초
        page2.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        entityManager.persist(page2);

        // 첫 번째 작업에도 페이지 추가 (비교용)
        DocumentPage page3 = new DocumentPage(1, "/test/page3.jpg", jobWithProcessingTime);
        page3.setProcessingTimeMs(1000L); // 1초
        page3.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        entityManager.persist(page3);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("사용자 처리 통계 - NULL 안전성 검증")
    void testGetUserProcessingStatistics_NullSafety() {
        // when
        Object[] result = optimizedQueryRepository.getUserProcessingStatistics(testUser.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(8);

        Long userId = (Long) result[0];
        String username = (String) result[1];
        Long totalJobs = (Long) result[2];
        Long totalPages = (Long) result[3];
        Long totalBlocks = (Long) result[4];
        Double avgProcessingTime = (Double) result[5];
        Long completedJobs = (Long) result[6];
        Long failedJobs = (Long) result[7];

        // 기본 검증
        assertThat(userId).isEqualTo(testUser.getId());
        assertThat(username).isEqualTo("testuser");
        assertThat(totalJobs).isEqualTo(2L);
        assertThat(totalPages).isEqualTo(3L);
        assertThat(completedJobs).isEqualTo(2L);
        assertThat(failedJobs).isEqualTo(0L);

        // 핵심: avgProcessingTime이 NULL이 아니고 올바르게 계산되었는지 검증
        assertThat(avgProcessingTime).isNotNull();

        // 예상 계산:
        // - jobWithProcessingTime: 5000ms (Job 레벨 값 사용)
        // - jobWithoutProcessingTime: 5000ms (Page 레벨 합계: 2000 + 3000)
        // - 평균: (5000 + 5000) / 2 = 5000.0
        assertThat(avgProcessingTime).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("NULL 처리시간만 있는 사용자 통계")
    void testGetUserProcessingStatistics_OnlyNullValues() {
        // given: 새로운 사용자 생성 (processingTimeMs가 모두 NULL)
        User nullUser = new User();
        nullUser.setUsername("nulluser");
        nullUser.setEmail("null@smarteye.com");
        entityManager.persist(nullUser);

        AnalysisJob nullJob = new AnalysisJob("null-job", "null-file.pdf", nullUser);
        nullJob.setStatus(AnalysisJob.JobStatus.COMPLETED);
        nullJob.setProcessingTimeMs(null);
        entityManager.persist(nullJob);

        // 페이지도 NULL 처리시간
        DocumentPage nullPage = new DocumentPage(1, "/test/null-page.jpg", nullJob);
        nullPage.setProcessingTimeMs(null);
        nullPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        entityManager.persist(nullPage);

        entityManager.flush();
        entityManager.clear();

        // when
        Object[] result = optimizedQueryRepository.getUserProcessingStatistics(nullUser.getId());

        // then
        assertThat(result).isNotNull();
        Double avgProcessingTime = (Double) result[5];

        // NULL 값들의 평균은 0.0이어야 함 (COALESCE 처리)
        assertThat(avgProcessingTime).isEqualTo(0.0);
    }

    @Test
    @DisplayName("페이지 처리시간 평균 - NULL 안전성 검증")
    void testDocumentPageAverageProcessingTime_NullSafety() {
        // given: NULL 처리시간을 가진 페이지 추가
        DocumentPage nullPage = new DocumentPage(3, "/test/null-page.jpg", jobWithProcessingTime);
        nullPage.setProcessingTimeMs(null);
        nullPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        entityManager.persist(nullPage);
        entityManager.flush();

        // when: DocumentPageRepository의 개선된 쿼리 테스트
        DocumentPageRepository pageRepo = entityManager.getEntityManager()
                .unwrap(org.hibernate.Session.class)
                .getSessionFactory()
                .getCurrentSession()
                .createQuery("SELECT AVG(COALESCE(dp.processingTimeMs, 0)) FROM DocumentPage dp WHERE dp.processingStatus = 'COMPLETED'", Double.class)
                .getSingleResult();

        // then
        assertThat(pageRepo).isNotNull();

        // 예상: (2000 + 3000 + 1000 + 0) / 4 = 1500.0
        assertThat(pageRepo).isEqualTo(1500.0);
    }

    @Test
    @DisplayName("느린 작업 조회 - 성능 검증")
    void testFindSlowJobsWithDetails() {
        // given: 임계값 설정 (4초)
        Long thresholdMs = 4000L;

        // when
        var slowJobs = optimizedQueryRepository.findSlowJobsWithDetails(thresholdMs);

        // then
        assertThat(slowJobs).hasSize(1);
        assertThat(slowJobs.get(0).getJobId()).isEqualTo("job-001");
        assertThat(slowJobs.get(0).getProcessingTimeMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("블록 통계 조회 - 데이터 무결성 검증")
    void testGetBlockStatisticsByJobId() {
        // given: 테스트 블록 데이터 추가
        DocumentPage page = entityManager.find(DocumentPage.class,
            entityManager.getEntityManager()
                .createQuery("SELECT dp.id FROM DocumentPage dp WHERE dp.analysisJob.jobId = 'job-001'", Long.class)
                .getSingleResult());

        LayoutBlock block = new LayoutBlock();
        block.setBlockIndex(1);
        block.setClassName("text");
        block.setConfidence(0.95);
        block.setArea(1000);
        block.setX1(10); block.setY1(10);
        block.setX2(110); block.setY2(60);
        block.setDocumentPage(page);
        block.setProcessingTimeMs(500L);
        entityManager.persist(block);
        entityManager.flush();

        // when
        var statistics = optimizedQueryRepository.getBlockStatisticsByJobId("job-001");

        // then
        assertThat(statistics).isNotEmpty();
        Object[] firstStat = statistics.get(0);
        assertThat(firstStat[0]).isEqualTo("text"); // className
        assertThat(firstStat[1]).isEqualTo(1L);      // blockCount
    }
}