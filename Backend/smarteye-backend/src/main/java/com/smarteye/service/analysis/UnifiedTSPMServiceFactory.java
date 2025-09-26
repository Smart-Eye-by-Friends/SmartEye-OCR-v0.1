package com.smarteye.service.analysis;

import com.smarteye.presentation.dto.TSPMResult;
import com.smarteye.entity.LayoutBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 통합 TSPM 서비스 팩토리
 *
 * Factory Pattern + Strategy Pattern을 활용하여
 * 기존 중복된 TSPM 서비스들을 단일 인터페이스로 통합
 *
 * 지원하는 레거시 서비스:
 * - TSPMEngine
 * - TSPMUnifiedAnalysisEngine
 * - UnifiedAnalysisEngine
 * - StructuredAnalysisService
 */
@Component
public class UnifiedTSPMServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedTSPMServiceFactory.class);

    @Autowired
    private PatternMatchingEngine patternMatchingEngine;

    @Autowired
    private SpatialAnalysisEngine spatialAnalysisEngine;

    @Autowired
    private TextPatternAnalyzer textPatternAnalyzer;

    @Autowired
    private ElementClassifier elementClassifier;

    // ============================================================================
    // TSPM 분석 전략 인터페이스
    // ============================================================================

    /**
     * TSMP 분석 전략 인터페이스
     */
    public interface TSPMAnalysisStrategy {
        TSPMResult performAnalysis(Long documentPageId);
        CompletableFuture<TSPMResult> performAnalysisAsync(Long documentPageId);
        String getStrategyName();
        int getPerformanceLevel(); // 1=기본, 2=최적화, 3=고급
    }

    /**
     * 레거시 호환 전략 (기존 TSPMEngine 알고리즘)
     */
    public class LegacyCompatibleStrategy implements TSPMAnalysisStrategy {
        @Override
        public TSPMResult performAnalysis(Long documentPageId) {
            logger.info("🔧 레거시 호환 TSPM 분석 시작 - 페이지 ID: {}", documentPageId);
            long startTime = System.currentTimeMillis();

            try {
                // 1. 데이터 로드 (통합 엔진 사용)
                List<LayoutBlock> layoutsWithText = loadLayoutData(documentPageId);

                // 2. 문제 위치 추출 (통합 패턴 매칭 엔진 사용)
                Map<String, Integer> questionPositions = extractQuestionPositions(layoutsWithText);

                // 3. 공간 분석 수행 (통합 공간 엔진 사용)
                TSPMResult result = performIntegratedAnalysis(layoutsWithText, questionPositions);

                // 4. 메타데이터 설정
                finalizeAnalysisResult(result, startTime);

                logger.info("✅ 레거시 호환 TSPM 분석 완료 - 처리시간: {}ms",
                           System.currentTimeMillis() - startTime);

                return result;

            } catch (Exception e) {
                logger.error("❌ 레거시 호환 TSPM 분석 실패: {}", e.getMessage(), e);
                throw new RuntimeException("레거시 호환 TSPM 분석 중 오류 발생", e);
            }
        }

        @Override
        public CompletableFuture<TSPMResult> performAnalysisAsync(Long documentPageId) {
            return CompletableFuture.supplyAsync(() -> performAnalysis(documentPageId));
        }

        @Override
        public String getStrategyName() { return "LEGACY_COMPATIBLE"; }

        @Override
        public int getPerformanceLevel() { return 1; }
    }

    /**
     * 최적화된 통합 전략 (신규 통합 알고리즘)
     */
    public class OptimizedUnifiedStrategy implements TSPMAnalysisStrategy {
        @Override
        public TSPMResult performAnalysis(Long documentPageId) {
            logger.info("🚀 최적화 통합 TSPM 분석 시작 - 페이지 ID: {}", documentPageId);
            long startTime = System.currentTimeMillis();

            try {
                // 1. 병렬 데이터 로드
                CompletableFuture<List<LayoutBlock>> layoutFuture = loadLayoutDataAsync(documentPageId);

                // 2. 병렬 패턴 분석
                List<LayoutBlock> layouts = layoutFuture.get();
                CompletableFuture<Map<String, Integer>> patternFuture =
                    extractQuestionPositionsAsync(layouts);

                // 3. 병렬 공간 분석
                Map<String, Integer> questionPositions = patternFuture.get();
                CompletableFuture<TSPMResult> analysisFuture =
                    performIntegratedAnalysisAsync(layouts, questionPositions);

                TSPMResult result = analysisFuture.get();
                finalizeAnalysisResult(result, startTime);

                logger.info("✅ 최적화 통합 TSPM 분석 완료 - 처리시간: {}ms",
                           System.currentTimeMillis() - startTime);

                return result;

            } catch (Exception e) {
                logger.error("❌ 최적화 통합 TSPM 분석 실패: {}", e.getMessage(), e);
                throw new RuntimeException("최적화 통합 TSPM 분석 중 오류 발생", e);
            }
        }

        @Override
        public CompletableFuture<TSPMResult> performAnalysisAsync(Long documentPageId) {
            return CompletableFuture.supplyAsync(() -> performAnalysis(documentPageId));
        }

        @Override
        public String getStrategyName() { return "OPTIMIZED_UNIFIED"; }

        @Override
        public int getPerformanceLevel() { return 2; }
    }

    /**
     * 고급 AI 강화 전략 (미래 확장용)
     */
    public class AIEnhancedStrategy implements TSPMAnalysisStrategy {
        @Override
        public TSPMResult performAnalysis(Long documentPageId) {
            logger.info("🧠 AI 강화 TSPM 분석 시작 - 페이지 ID: {}", documentPageId);

            // AI 기반 고급 분석 로직 (향후 구현)
            // 머신러닝 기반 패턴 인식, 동적 임계값 조정 등

            return new LegacyCompatibleStrategy().performAnalysis(documentPageId);
        }

        @Override
        public CompletableFuture<TSPMResult> performAnalysisAsync(Long documentPageId) {
            return CompletableFuture.supplyAsync(() -> performAnalysis(documentPageId));
        }

        @Override
        public String getStrategyName() { return "AI_ENHANCED"; }

        @Override
        public int getPerformanceLevel() { return 3; }
    }

    // ============================================================================
    // Factory 메서드들
    // ============================================================================

    /**
     * TSMP 분석 전략 생성 (Factory Method)
     */
    public TSPMAnalysisStrategy createAnalysisStrategy(TSPMStrategyType strategyType) {
        return switch (strategyType) {
            case LEGACY_COMPATIBLE -> new LegacyCompatibleStrategy();
            case OPTIMIZED_UNIFIED -> new OptimizedUnifiedStrategy();
            case AI_ENHANCED -> new AIEnhancedStrategy();
        };
    }

    /**
     * 자동 전략 선택 (데이터 크기 기반)
     */
    public TSPMAnalysisStrategy createOptimalStrategy(int elementCount, boolean isAsync) {
        if (elementCount > 1000) {
            return createAnalysisStrategy(TSPMStrategyType.OPTIMIZED_UNIFIED);
        } else if (elementCount > 100) {
            return createAnalysisStrategy(TSPMStrategyType.LEGACY_COMPATIBLE);
        } else {
            return createAnalysisStrategy(TSPMStrategyType.LEGACY_COMPATIBLE);
        }
    }

    /**
     * 기본 TSPM 분석 수행 (Facade 패턴)
     */
    public TSPMResult performTSPMAnalysis(Long documentPageId) {
        return performTSPMAnalysis(documentPageId, TSPMStrategyType.OPTIMIZED_UNIFIED);
    }

    /**
     * 전략 지정 TSPM 분석 수행
     */
    public TSPMResult performTSPMAnalysis(Long documentPageId, TSPMStrategyType strategyType) {
        TSPMAnalysisStrategy strategy = createAnalysisStrategy(strategyType);
        logger.info("🎯 TSPM 분석 전략: {} (성능레벨: {})",
                   strategy.getStrategyName(), strategy.getPerformanceLevel());

        return strategy.performAnalysis(documentPageId);
    }

    /**
     * 비동기 TSPM 분석 수행
     */
    public CompletableFuture<TSPMResult> performTSPMAnalysisAsync(Long documentPageId,
                                                                 TSPMStrategyType strategyType) {
        TSPMAnalysisStrategy strategy = createAnalysisStrategy(strategyType);
        return strategy.performAnalysisAsync(documentPageId);
    }

    // ============================================================================
    // 통합된 내부 구현 메서드들 (중복 제거됨)
    // ============================================================================

    private List<LayoutBlock> loadLayoutData(Long documentPageId) {
        // 기존 중복된 로딩 로직을 통합
        // 실제 구현은 Repository 계층에서 처리
        logger.debug("📊 레이아웃 데이터 로드 - 페이지 ID: {}", documentPageId);
        return List.of(); // 실제 구현 필요
    }

    private CompletableFuture<List<LayoutBlock>> loadLayoutDataAsync(Long documentPageId) {
        return CompletableFuture.supplyAsync(() -> loadLayoutData(documentPageId));
    }

    private Map<String, Integer> extractQuestionPositions(List<LayoutBlock> layouts) {
        // 통합 패턴 매칭 엔진 활용
        logger.debug("🔍 문제 위치 추출 - 레이아웃 {}개", layouts.size());
        return Map.of(); // 실제 구현 필요
    }

    private CompletableFuture<Map<String, Integer>> extractQuestionPositionsAsync(List<LayoutBlock> layouts) {
        return CompletableFuture.supplyAsync(() -> extractQuestionPositions(layouts));
    }

    private TSPMResult performIntegratedAnalysis(List<LayoutBlock> layouts,
                                               Map<String, Integer> questionPositions) {
        // 통합 공간 분석 엔진 활용
        logger.debug("📊 통합 분석 수행 - 문제 {}개", questionPositions.size());
        return new TSPMResult(); // 실제 구현 필요
    }

    private CompletableFuture<TSPMResult> performIntegratedAnalysisAsync(List<LayoutBlock> layouts,
                                                                       Map<String, Integer> questionPositions) {
        return CompletableFuture.supplyAsync(() -> performIntegratedAnalysis(layouts, questionPositions));
    }

    private void finalizeAnalysisResult(TSPMResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        if (result.getAnalysisMetadata() != null) {
            result.getAnalysisMetadata().setProcessingTimeMs(processingTime);
        }
        logger.debug("⏱️ 분석 결과 최종화 완료 - 처리시간: {}ms", processingTime);
    }

    // ============================================================================
    // 전략 타입 정의
    // ============================================================================

    public enum TSPMStrategyType {
        LEGACY_COMPATIBLE,    // 기존 TSPMEngine 호환
        OPTIMIZED_UNIFIED,    // 최적화된 통합 버전
        AI_ENHANCED          // AI 강화 버전 (미래)
    }
}