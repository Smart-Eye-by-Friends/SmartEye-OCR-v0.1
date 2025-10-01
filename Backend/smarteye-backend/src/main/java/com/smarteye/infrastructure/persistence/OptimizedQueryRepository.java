package com.smarteye.infrastructure.persistence;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.LayoutBlock;
import com.smarteye.domain.user.entity.User;
import com.smarteye.domain.document.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 최적화된 쿼리 리포지토리
 *
 * Phase 2 JPA 성능 최적화:
 * 1. N+1 문제 해결을 위한 Fetch Join 최적화
 * 2. 배치 쿼리로 여러 엔터티 한번에 로딩
 * 3. Projection 쿼리로 필요한 필드만 조회
 * 4. 인덱스 최적화된 쿼리 패턴
 */
@Repository
public interface OptimizedQueryRepository extends JpaRepository<AnalysisJob, Long> {

    // ============================================================================
    // 전체 분석 작업 데이터 한번에 로딩 (N+1 문제 해결)
    // ============================================================================

    /**
     * 분석 작업의 모든 관련 데이터를 한번의 쿼리로 로딩
     *
     * 기존 문제:
     * - AnalysisJob 조회 (1번)
     * - DocumentPages 조회 (N번)
     * - LayoutBlocks 조회 (N*M번)
     * - TextBlocks 조회 (N*M*K번)
     *
     * 해결책: Fetch Join으로 한번에 로딩
     */
    @Query("""
        SELECT DISTINCT aj FROM AnalysisJob aj
        LEFT JOIN FETCH aj.user u
        LEFT JOIN FETCH aj.documentPages dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        LEFT JOIN FETCH lb.textBlock tb
        LEFT JOIN FETCH aj.cimOutput co
        LEFT JOIN FETCH aj.processingLogs pl
        WHERE aj.jobId = :jobId
        """)
    Optional<AnalysisJob> findCompleteAnalysisJobByJobId(@Param("jobId") String jobId);

    /**
     * 사용자의 최근 분석 작업들을 관련 데이터와 함께 로딩
     */
    @Query("""
        SELECT DISTINCT aj FROM AnalysisJob aj
        LEFT JOIN FETCH aj.documentPages dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        LEFT JOIN FETCH aj.cimOutput co
        WHERE aj.user.id = :userId
        AND aj.createdAt >= :since
        ORDER BY aj.createdAt DESC
        """)
    List<AnalysisJob> findRecentJobsWithDataByUserId(
        @Param("userId") Long userId,
        @Param("since") LocalDateTime since);

    // ============================================================================
    // 페이지별 최적화된 쿼리들
    // ============================================================================

    /**
     * 페이지의 모든 레이아웃 블록과 텍스트 블록을 한번에 로딩
     * TSPM 엔진에서 사용 (Y좌표 순 정렬 포함)
     */
    @Query("""
        SELECT dp FROM DocumentPage dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        LEFT JOIN FETCH lb.textBlock tb
        WHERE dp.id = :pageId
        ORDER BY lb.y1 ASC, lb.x1 ASC
        """)
    Optional<DocumentPage> findPageWithOrderedBlocks(@Param("pageId") Long pageId);

    /**
     * 특정 작업의 모든 페이지를 블록 데이터와 함께 로딩
     */
    @Query("""
        SELECT DISTINCT dp FROM DocumentPage dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        LEFT JOIN FETCH lb.textBlock tb
        WHERE dp.analysisJob.jobId = :jobId
        ORDER BY dp.pageNumber ASC, lb.blockIndex ASC
        """)
    List<DocumentPage> findAllPagesWithBlocksByJobId(@Param("jobId") String jobId);

    // ============================================================================
    // 통계용 최적화된 집계 쿼리들
    // ============================================================================

    /**
     * 클래스별 통계를 한번의 쿼리로 계산
     * 여러 번의 COUNT 쿼리 → 한번의 GROUP BY 쿼리
     */
    @Query("""
        SELECT
            lb.className,
            COUNT(lb.id) as blockCount,
            AVG(lb.confidence) as avgConfidence,
            AVG(lb.area) as avgArea,
            COUNT(CASE WHEN lb.ocrText IS NOT NULL AND LENGTH(lb.ocrText) > 0 THEN 1 END) as textBlockCount,
            COUNT(CASE WHEN lb.aiDescription IS NOT NULL AND LENGTH(lb.aiDescription) > 0 THEN 1 END) as aiBlockCount
        FROM LayoutBlock lb
        WHERE lb.documentPage.analysisJob.jobId = :jobId
        GROUP BY lb.className
        ORDER BY blockCount DESC
        """)
    List<Object[]> getBlockStatisticsByJobId(@Param("jobId") String jobId);

    /**
     * 사용자별 처리 통계 (여러 테이블 조인을 한번에)
     */
    @Query("""
        SELECT
            u.id as userId,
            u.username,
            COUNT(DISTINCT aj.id) as totalJobs,
            COUNT(DISTINCT dp.id) as totalPages,
            COUNT(DISTINCT lb.id) as totalBlocks,
            AVG(CASE
                WHEN aj.processingTimeMs IS NOT NULL THEN aj.processingTimeMs
                ELSE (SELECT COALESCE(SUM(dp.processingTimeMs), 0)
                      FROM DocumentPage dp
                      WHERE dp.analysisJob = aj)
            END) as avgProcessingTime,
            SUM(CASE WHEN aj.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedJobs,
            SUM(CASE WHEN aj.status = 'FAILED' THEN 1 ELSE 0 END) as failedJobs
        FROM User u
        LEFT JOIN u.analysisJobs aj
        LEFT JOIN aj.documentPages dp
        LEFT JOIN dp.layoutBlocks lb
        WHERE u.id = :userId
        GROUP BY u.id, u.username
        """)
    Object[] getUserProcessingStatistics(@Param("userId") Long userId);

    // ============================================================================
    // 성능 모니터링용 쿼리들
    // ============================================================================

    /**
     * 느린 처리 작업들을 관련 데이터와 함께 조회
     */
    @Query("""
        SELECT aj FROM AnalysisJob aj
        LEFT JOIN FETCH aj.documentPages dp
        LEFT JOIN FETCH aj.processingLogs pl
        WHERE aj.processingTimeMs > :thresholdMs
        ORDER BY aj.processingTimeMs DESC
        """)
    List<AnalysisJob> findSlowJobsWithDetails(@Param("thresholdMs") Long thresholdMs);

    /**
     * 실패한 블록들의 패턴 분석
     */
    @Query("""
        SELECT
            lb.className,
            lb.processingStatus,
            COUNT(lb.id) as failureCount,
            AVG(lb.confidence) as avgConfidence
        FROM LayoutBlock lb
        WHERE lb.processingStatus IN ('FAILED', 'ERROR')
        AND lb.documentPage.analysisJob.createdAt >= :since
        GROUP BY lb.className, lb.processingStatus
        ORDER BY failureCount DESC
        """)
    List<Object[]> getFailurePatternsSince(@Param("since") LocalDateTime since);

    // ============================================================================
    // 배치 처리용 최적화 쿼리들
    // ============================================================================

    /**
     * 처리 대기중인 작업들을 관련 데이터와 함께 배치로 조회
     */
    @Query("""
        SELECT aj FROM AnalysisJob aj
        LEFT JOIN FETCH aj.documentPages dp
        WHERE aj.status IN :statuses
        AND aj.updatedAt >= :since
        ORDER BY aj.createdAt ASC
        """)
    List<AnalysisJob> findJobsForBatchProcessing(
        @Param("statuses") List<AnalysisJob.JobStatus> statuses,
        @Param("since") LocalDateTime since);

    /**
     * 특정 상태의 블록들을 페이지 정보와 함께 배치로 조회
     */
    @Query("""
        SELECT lb FROM LayoutBlock lb
        LEFT JOIN FETCH lb.documentPage dp
        LEFT JOIN FETCH dp.analysisJob aj
        WHERE lb.processingStatus = :status
        AND lb.updatedAt >= :since
        ORDER BY aj.createdAt ASC, lb.blockIndex ASC
        """)
    List<LayoutBlock> findBlocksForBatchProcessing(
        @Param("status") LayoutBlock.ProcessingStatus status,
        @Param("since") LocalDateTime since);

    // ============================================================================
    // Projection 쿼리들 (필요한 필드만 조회)
    // ============================================================================

    /**
     * 작업 목록용 경량 쿼리 (큰 BLOB 필드 제외)
     */
    @Query("""
        SELECT
            aj.id,
            aj.jobId,
            aj.originalFilename,
            aj.status,
            aj.progressPercentage,
            aj.createdAt,
            aj.completedAt,
            aj.processingTimeMs,
            u.username
        FROM AnalysisJob aj
        LEFT JOIN aj.user u
        WHERE aj.user.id = :userId
        ORDER BY aj.createdAt DESC
        """)
    List<Object[]> findJobSummariesByUserId(@Param("userId") Long userId);

    /**
     * 블록 검색용 경량 쿼리
     */
    @Query("""
        SELECT
            lb.id,
            lb.blockIndex,
            lb.className,
            lb.confidence,
            lb.x1, lb.y1, lb.x2, lb.y2,
            CASE WHEN LENGTH(lb.ocrText) > 100 THEN CONCAT(SUBSTRING(lb.ocrText, 1, 100), '...') ELSE lb.ocrText END
        FROM LayoutBlock lb
        WHERE lb.documentPage.analysisJob.jobId = :jobId
        ORDER BY lb.blockIndex
        """)
    List<Object[]> findBlockSummariesByJobId(@Param("jobId") String jobId);

    // ============================================================================
    // 캐시 무효화용 쿼리들
    // ============================================================================

    /**
     * 관련 캐시를 무효화해야 하는 작업들 찾기
     */
    @Query("""
        SELECT aj.jobId FROM AnalysisJob aj
        WHERE aj.status = 'COMPLETED'
        AND aj.completedAt >= :since
        AND EXISTS (
            SELECT 1 FROM DocumentPage dp
            WHERE dp.analysisJob = aj
            AND dp.processingStatus = 'COMPLETED'
        )
        """)
    List<String> findJobIdsForCacheInvalidation(@Param("since") LocalDateTime since);

    /**
     * 오래된 분석 결과 정리용 쿼리
     */
    @Query("""
        SELECT aj FROM AnalysisJob aj
        LEFT JOIN FETCH aj.documentPages dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        WHERE aj.completedAt < :before
        AND aj.status = 'COMPLETED'
        ORDER BY aj.completedAt ASC
        """)
    List<AnalysisJob> findOldJobsForCleanup(@Param("before") LocalDateTime before);

    // ============================================================================
    // 멀티 페이지 문서 최적화
    // ============================================================================

    /**
     * PDF 문서의 모든 페이지를 한번에 로딩
     */
    @Query("""
        SELECT dp FROM DocumentPage dp
        LEFT JOIN FETCH dp.layoutBlocks lb
        LEFT JOIN FETCH lb.textBlock tb
        WHERE dp.analysisJob.id = :analysisJobId
        ORDER BY dp.pageNumber ASC, lb.y1 ASC, lb.x1 ASC
        """)
    List<DocumentPage> findAllPagesSortedByPosition(@Param("analysisJobId") Long analysisJobId);

    /**
     * 문서의 구조화된 결과를 위한 최적화된 쿼리
     */
    @Query("""
        SELECT lb FROM LayoutBlock lb
        LEFT JOIN FETCH lb.textBlock tb
        LEFT JOIN FETCH lb.documentPage dp
        WHERE dp.analysisJob.jobId = :jobId
        AND lb.className IN ('question_number', 'question_text', 'choice', 'answer')
        ORDER BY dp.pageNumber ASC, lb.y1 ASC, lb.x1 ASC
        """)
    List<LayoutBlock> findEducationalElementsByJobId(@Param("jobId") String jobId);
}