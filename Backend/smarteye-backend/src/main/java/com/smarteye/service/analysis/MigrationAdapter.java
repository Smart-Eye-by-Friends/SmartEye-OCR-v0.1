package com.smarteye.service.analysis;

import com.smarteye.presentation.dto.TSPMResult;
import com.smarteye.service.UnifiedAnalysisEngine;
import com.smarteye.service.analysis.UnifiedTSPMServiceFactory.TSPMStrategyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 마이그레이션 어댑터
 *
 * Adapter Pattern을 사용하여 기존 서비스들과 신규 통합 서비스 간의
 * 점진적 마이그레이션을 지원
 *
 * 마이그레이션 전략:
 * 1. LEGACY_ONLY: 기존 서비스만 사용 (기본값)
 * 2. HYBRID: 기존 + 신규 병행 실행 및 검증
 * 3. UNIFIED_ONLY: 신규 통합 서비스만 사용
 */
@Component
public class MigrationAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MigrationAdapter.class);

    // 마이그레이션 모드 설정 (application.properties에서 제어)
    @Value("${smarteye.migration.mode:LEGACY_ONLY}")
    private MigrationMode migrationMode;

    // 검증 모드 설정 (성능 비교 등)
    @Value("${smarteye.migration.validation.enabled:false}")
    private boolean validationEnabled;

    // @Autowired
    // private TSPMEngine legacyTSPMEngine; // TSPMEngine이 삭제되어 임시 비활성화

    @Autowired
    private UnifiedAnalysisEngine legacyUnifiedEngine;

    @Autowired
    private UnifiedTSPMServiceFactory unifiedTSPMServiceFactory;

    // ============================================================================
    // 어댑터 메서드들
    // ============================================================================

    /**
     * TSPM 분석 어댑터 메서드
     * 마이그레이션 모드에 따라 레거시/신규/하이브리드 실행
     */
    public TSPMResult performTSPMAnalysis(Long documentPageId) {
        switch (migrationMode) {
            case LEGACY_ONLY:
                return performLegacyTSPMAnalysis(documentPageId);

            case HYBRID:
                return performHybridTSPMAnalysis(documentPageId);

            case UNIFIED_ONLY:
                return performUnifiedTSPMAnalysis(documentPageId);

            default:
                logger.warn("알 수 없는 마이그레이션 모드: {}, LEGACY_ONLY로 대체", migrationMode);
                return performLegacyTSPMAnalysis(documentPageId);
        }
    }

    /**
     * 레거시 전용 TSPM 분석
     */
    private TSPMResult performLegacyTSPMAnalysis(Long documentPageId) {
        logger.info("🔧 레거시 TSPM 분석 수행 - 페이지 ID: {}", documentPageId);
        long startTime = System.currentTimeMillis();

        try {
            // TSPMEngine이 삭제되어 임시로 통합 엔진 사용
            logger.warn("TSPMEngine이 삭제되어 UnifiedAnalysisEngine 사용");
            return performUnifiedTSPMAnalysis(documentPageId);

        } catch (Exception e) {
            logger.error("❌ 레거시 TSPM 분석 실패", e);
            throw e;
        }
    }

    /**
     * 통합 전용 TSPM 분석
     */
    private TSPMResult performUnifiedTSPMAnalysis(Long documentPageId) {
        logger.info("🚀 통합 TSPM 분석 수행 - 페이지 ID: {}", documentPageId);
        long startTime = System.currentTimeMillis();

        try {
            TSPMResult result = unifiedTSPMServiceFactory.performTSPMAnalysis(documentPageId,
                TSPMStrategyType.OPTIMIZED_UNIFIED);

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 통합 TSPM 분석 완료 - 처리시간: {}ms", processingTime);

            return result;

        } catch (Exception e) {
            logger.error("❌ 통합 TSPM 분석 실패", e);

            // 폴백 전략: 통합 분석 실패시 레거시로 대체
            logger.warn("통합 분석 실패로 레거시 분석으로 폴백 수행");
            return performLegacyTSPMAnalysis(documentPageId);
        }
    }

    /**
     * 하이브리드 TSPM 분석 (병행 실행 및 검증)
     */
    private TSPMResult performHybridTSPMAnalysis(Long documentPageId) {
        logger.info("🔄 하이브리드 TSPM 분석 수행 - 페이지 ID: {}", documentPageId);
        long startTime = System.currentTimeMillis();

        TSPMResult legacyResult = null;
        TSPMResult unifiedResult = null;
        Exception legacyException = null;
        Exception unifiedException = null;

        // 1. 레거시 분석 수행 (TSPMEngine 삭제로 건너뜀)
        legacyException = new RuntimeException("TSPMEngine이 삭제되어 레거시 분석 건너뜀");
        logger.warn("TSPMEngine이 삭제되어 레거시 분석 건너뜀");

        // 2. 통합 분석 수행
        try {
            long unifiedStart = System.currentTimeMillis();
            unifiedResult = unifiedTSPMServiceFactory.performTSPMAnalysis(documentPageId,
                TSPMStrategyType.OPTIMIZED_UNIFIED);
            long unifiedTime = System.currentTimeMillis() - unifiedStart;
            logger.info("✅ 통합 분석 완료 - 처리시간: {}ms", unifiedTime);
        } catch (Exception e) {
            unifiedException = e;
            logger.error("❌ 통합 분석 실패", e);
        }

        // 3. 결과 검증 및 선택
        TSPMResult finalResult = selectBestResult(legacyResult, unifiedResult,
                                                 legacyException, unifiedException);

        // 4. 검증 모드인 경우 상세 비교 수행
        if (validationEnabled && legacyResult != null && unifiedResult != null) {
            performResultValidation(documentPageId, legacyResult, unifiedResult);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("🔄 하이브리드 분석 완료 - 총 처리시간: {}ms", totalTime);

        return finalResult;
    }

    /**
     * 최적 결과 선택 로직
     */
    private TSPMResult selectBestResult(TSPMResult legacyResult, TSPMResult unifiedResult,
                                       Exception legacyException, Exception unifiedException) {
        // 둘 다 성공한 경우: 통합 결과 우선 (더 발전된 알고리즘)
        if (legacyResult != null && unifiedResult != null) {
            logger.info("🎯 둘 다 성공: 통합 결과 선택");
            return unifiedResult;
        }

        // 통합만 성공한 경우
        if (unifiedResult != null) {
            logger.info("🎯 통합만 성공: 통합 결과 선택");
            return unifiedResult;
        }

        // 레거시만 성공한 경우
        if (legacyResult != null) {
            logger.info("🎯 레거시만 성공: 레거시 결과 선택");
            return legacyResult;
        }

        // 둘 다 실패한 경우
        logger.error("🎯 둘 다 실패: 예외 발생");
        RuntimeException combinedException = new RuntimeException("레거시 및 통합 분석 모두 실패");
        if (legacyException != null) {
            combinedException.addSuppressed(legacyException);
        }
        if (unifiedException != null) {
            combinedException.addSuppressed(unifiedException);
        }
        throw combinedException;
    }

    /**
     * 결과 검증 (성능 및 정확도 비교)
     */
    private void performResultValidation(Long documentPageId, TSPMResult legacyResult,
                                       TSPMResult unifiedResult) {
        logger.info("🔍 결과 검증 시작 - 페이지 ID: {}", documentPageId);

        try {
            // 1. 문제 개수 비교
            int legacyQuestions = legacyResult.getQuestionGroups() != null ?
                legacyResult.getQuestionGroups().size() : 0;
            int unifiedQuestions = unifiedResult.getQuestionGroups() != null ?
                unifiedResult.getQuestionGroups().size() : 0;

            logger.info("📊 문제 개수 비교: 레거시={}개, 통합={}개", legacyQuestions, unifiedQuestions);

            // 2. 처리 시간 비교
            long legacyTime = legacyResult.getAnalysisMetadata() != null ?
                legacyResult.getAnalysisMetadata().getProcessingTimeMs() : 0;
            long unifiedTime = unifiedResult.getAnalysisMetadata() != null ?
                unifiedResult.getAnalysisMetadata().getProcessingTimeMs() : 0;

            logger.info("⏱️ 처리 시간 비교: 레거시={}ms, 통합={}ms", legacyTime, unifiedTime);

            // 3. 성능 개선율 계산
            if (legacyTime > 0 && unifiedTime > 0) {
                double performanceImprovement = ((double) (legacyTime - unifiedTime) / legacyTime) * 100;
                logger.info("📈 성능 개선율: {:.2f}%", performanceImprovement);
            }

            // 4. 정확도 비교 (문제 개수 기준)
            if (legacyQuestions > 0 && unifiedQuestions > 0) {
                double accuracyDifference = Math.abs(legacyQuestions - unifiedQuestions) /
                    (double) Math.max(legacyQuestions, unifiedQuestions) * 100;
                logger.info("📊 정확도 차이: {:.2f}%", accuracyDifference);
            }

        } catch (Exception e) {
            logger.error("❌ 결과 검증 실패", e);
        }
    }

    // ============================================================================
    // 설정 및 제어 메서드들
    // ============================================================================

    /**
     * 마이그레이션 모드 동적 변경
     */
    public void setMigrationMode(MigrationMode mode) {
        logger.info("🔧 마이그레이션 모드 변경: {} → {}", this.migrationMode, mode);
        this.migrationMode = mode;
    }

    /**
     * 현재 마이그레이션 모드 조회
     */
    public MigrationMode getMigrationMode() {
        return migrationMode;
    }

    /**
     * 검증 모드 토글
     */
    public void setValidationEnabled(boolean enabled) {
        logger.info("🔍 검증 모드 변경: {} → {}", this.validationEnabled, enabled);
        this.validationEnabled = enabled;
    }

    /**
     * 마이그레이션 상태 헬스 체크
     */
    public MigrationHealthStatus getHealthStatus() {
        try {
            // 레거시 서비스 상태 확인 (TSPMEngine 삭제로 false)
            boolean legacyHealthy = false;

            // 통합 서비스 상태 확인
            boolean unifiedHealthy = unifiedTSPMServiceFactory != null;

            return new MigrationHealthStatus(legacyHealthy, unifiedHealthy, migrationMode, validationEnabled);

        } catch (Exception e) {
            logger.error("마이그레이션 헬스 체크 실패", e);
            return new MigrationHealthStatus(false, false, migrationMode, validationEnabled);
        }
    }

    // ============================================================================
    // 내부 클래스 및 열거형
    // ============================================================================

    public enum MigrationMode {
        LEGACY_ONLY,    // 레거시 서비스만 사용
        HYBRID,         // 병행 실행 및 검증
        UNIFIED_ONLY    // 통합 서비스만 사용
    }

    public static class MigrationHealthStatus {
        public final boolean legacyHealthy;
        public final boolean unifiedHealthy;
        public final MigrationMode currentMode;
        public final boolean validationEnabled;

        public MigrationHealthStatus(boolean legacyHealthy, boolean unifiedHealthy,
                                   MigrationMode currentMode, boolean validationEnabled) {
            this.legacyHealthy = legacyHealthy;
            this.unifiedHealthy = unifiedHealthy;
            this.currentMode = currentMode;
            this.validationEnabled = validationEnabled;
        }

        public boolean isHealthy() {
            return switch (currentMode) {
                case LEGACY_ONLY -> legacyHealthy;
                case UNIFIED_ONLY -> unifiedHealthy;
                case HYBRID -> legacyHealthy && unifiedHealthy;
            };
        }
    }
}