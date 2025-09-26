package com.smarteye.application.analysis.engine;

import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.LayoutBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import java.util.stream.Collectors;

/**
 * 통합 공간 분석 엔진
 *
 * TSPM 관련 모든 서비스의 중복된 공간 계산 로직을 통합
 * Template Method Pattern을 사용하여 공통 알고리즘 추상화
 */
@Component
public class SpatialAnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(SpatialAnalysisEngine.class);

    // ============================================================================
    // 공간 분석 상수
    // ============================================================================

    /** 기본 proximity 임계값 (레거시 Python: 500px) */
    public static final int DEFAULT_PROXIMITY_THRESHOLD = 500;

    /** 정밀 proximity 임계값 */
    public static final int PRECISE_PROXIMITY_THRESHOLD = 50;

    /** 확장 proximity 임계값 */
    public static final int EXTENDED_PROXIMITY_THRESHOLD = 1000;

    // ============================================================================
    // 공간 분석 전략 인터페이스
    // ============================================================================

    /**
     * 공간 분석 전략 인터페이스
     */
    public interface SpatialAnalysisStrategy {
        String assignElement(int elementY, Map<String, Integer> questionPositions);
        List<? extends Object> findRelatedElements(Object targetElement, List<? extends Object> allElements);
        int getProximityThreshold();
    }

    /**
     * 기본 Y좌표 proximity 전략 (TSPM 기본 알고리즘)
     */
    public static class ProximityBasedStrategy implements SpatialAnalysisStrategy {
        private final int proximityThreshold;

        public ProximityBasedStrategy(int proximityThreshold) {
            this.proximityThreshold = proximityThreshold;
        }

        @Override
        public String assignElement(int elementY, Map<String, Integer> questionPositions) {
            String bestQuestion = "unknown";
            int minDistance = Integer.MAX_VALUE;

            // 가장 가까운 문제 찾기
            for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
                int distance = Math.abs(elementY - entry.getValue());

                if (distance < minDistance) {
                    minDistance = distance;
                    bestQuestion = entry.getKey();
                }
            }

            // 거리 임계값 확인
            if (minDistance > proximityThreshold) {
                return "unknown";
            }

            return bestQuestion;
        }

        @Override
        public List<LayoutInfo> findRelatedElements(Object targetElement, List<?> allElements) {
            if (!(targetElement instanceof LayoutInfo) || allElements.isEmpty()) {
                return new ArrayList<>();
            }

            LayoutInfo target = (LayoutInfo) targetElement;
            int targetY = target.getBox()[1]; // Y1 좌표

            return allElements.stream()
                .filter(LayoutInfo.class::isInstance)
                .map(LayoutInfo.class::cast)
                .filter(layout -> {
                    int layoutY = layout.getBox()[1];
                    return Math.abs(layoutY - targetY) <= proximityThreshold;
                })
                .sorted(Comparator.comparing(layout -> layout.getBox()[0])) // X좌표순 정렬
                .collect(Collectors.toList());
        }

        @Override
        public int getProximityThreshold() {
            return proximityThreshold;
        }
    }

    /**
     * 영역 기반 공간 분석 전략 (고급 공간 분석)
     */
    public static class RegionBasedStrategy implements SpatialAnalysisStrategy {
        private final int regionThreshold;

        public RegionBasedStrategy(int regionThreshold) {
            this.regionThreshold = regionThreshold;
        }

        @Override
        public String assignElement(int elementY, Map<String, Integer> questionPositions) {
            // 영역별 가중치를 고려한 할당
            Map<String, Double> weightedScores = new HashMap<>();

            for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
                int distance = Math.abs(elementY - entry.getValue());
                double weight = calculateRegionWeight(distance);
                weightedScores.put(entry.getKey(), weight);
            }

            // 최고 점수의 문제 선택
            return weightedScores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.5) // 최소 임계값
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
        }

        private double calculateRegionWeight(int distance) {
            if (distance <= regionThreshold) {
                return 1.0 - ((double) distance / regionThreshold) * 0.5; // 거리에 따른 가중치 감소
            }
            return 0.0;
        }

        @Override
        public List<LayoutInfo> findRelatedElements(Object targetElement, List<?> allElements) {
            // 영역 기반 관련 요소 찾기 (확장된 로직)
            if (!(targetElement instanceof LayoutInfo)) {
                return new ArrayList<>();
            }

            LayoutInfo target = (LayoutInfo) targetElement;
            int[] targetBox = target.getBox();

            return allElements.stream()
                .filter(LayoutInfo.class::isInstance)
                .map(LayoutInfo.class::cast)
                .filter(layout -> isInRegion(targetBox, layout.getBox()))
                .collect(Collectors.toList());
        }

        private boolean isInRegion(int[] targetBox, int[] elementBox) {
            // 영역 겹침 확인 로직
            int targetCenterY = (targetBox[1] + targetBox[3]) / 2;
            int elementCenterY = (elementBox[1] + elementBox[3]) / 2;
            return Math.abs(targetCenterY - elementCenterY) <= regionThreshold;
        }

        @Override
        public int getProximityThreshold() {
            return regionThreshold;
        }
    }

    // ============================================================================
    // 통합 공간 분석 메서드들
    // ============================================================================

    /**
     * 요소를 가장 가까운 문제에 할당 (모든 TSPM 서비스의 공통 로직)
     */
    public String assignElementToNearestQuestion(int elementY, Map<String, Integer> questionPositions) {
        return assignElementToNearestQuestion(elementY, questionPositions, DEFAULT_PROXIMITY_THRESHOLD);
    }

    /**
     * 사용자 정의 임계값으로 요소 할당
     */
    public String assignElementToNearestQuestion(int elementY, Map<String, Integer> questionPositions, int threshold) {
        SpatialAnalysisStrategy strategy = new ProximityBasedStrategy(threshold);
        String result = strategy.assignElement(elementY, questionPositions);

        logger.debug("✅ 요소 Y={} → 문제 {} 할당 (임계값: {}px)",
                    elementY, result, threshold);

        return result;
    }

    /**
     * 고급 영역 기반 요소 할당
     */
    public String assignElementWithRegionAnalysis(int elementY, Map<String, Integer> questionPositions, int regionThreshold) {
        SpatialAnalysisStrategy strategy = new RegionBasedStrategy(regionThreshold);
        return strategy.assignElement(elementY, questionPositions);
    }

    /**
     * LayoutBlock 전용 할당 (JPA 엔터티)
     */
    public String assignLayoutBlockToQuestion(LayoutBlock layout, Map<String, Integer> questionPositions) {
        if (layout == null) return "unknown";
        return assignElementToNearestQuestion(layout.getY1(), questionPositions);
    }

    /**
     * LayoutInfo 전용 할당 (DTO)
     */
    public String assignLayoutInfoToQuestion(LayoutInfo layout, Map<String, Integer> questionPositions) {
        if (layout == null || layout.getBox() == null || layout.getBox().length < 2) {
            return "unknown";
        }
        return assignElementToNearestQuestion(layout.getBox()[1], questionPositions);
    }

    /**
     * 관련 요소들 찾기 (proximity 알고리즘 기반)
     */
    public List<LayoutInfo> findRelatedLayoutElements(LayoutInfo targetLayout,
                                                     List<LayoutInfo> allLayouts,
                                                     int proximityThreshold) {
        if (targetLayout == null || allLayouts == null) {
            return new ArrayList<>();
        }

        SpatialAnalysisStrategy strategy = new ProximityBasedStrategy(proximityThreshold);
        return (List<LayoutInfo>) strategy.findRelatedElements(targetLayout, allLayouts);
    }

    /**
     * 영역 기반 관련 요소 찾기
     */
    public List<LayoutInfo> findRelatedElementsInRegion(LayoutInfo targetLayout,
                                                       List<LayoutInfo> allLayouts,
                                                       int regionThreshold) {
        if (targetLayout == null || allLayouts == null) {
            return new ArrayList<>();
        }

        SpatialAnalysisStrategy strategy = new RegionBasedStrategy(regionThreshold);
        return (List<LayoutInfo>) strategy.findRelatedElements(targetLayout, allLayouts);
    }

    /**
     * 문제별 Y 범위 계산
     */
    public Map<String, int[]> calculateQuestionYRanges(Map<String, Integer> questionPositions,
                                                      List<LayoutBlock> allElements) {
        Map<String, int[]> yRanges = new HashMap<>();

        for (String questionNum : questionPositions.keySet()) {
            int[] range = calculateYRangeForQuestion(questionNum, questionPositions, allElements);
            yRanges.put(questionNum, range);
        }

        return yRanges;
    }

    /**
     * 특정 문제의 Y 범위 계산
     */
    private int[] calculateYRangeForQuestion(String questionNum,
                                           Map<String, Integer> questionPositions,
                                           List<LayoutBlock> allElements) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        // 해당 문제에 속한 모든 요소의 Y 범위 계산
        for (LayoutBlock element : allElements) {
            String assignedQuestion = assignLayoutBlockToQuestion(element, questionPositions);
            if (questionNum.equals(assignedQuestion)) {
                minY = Math.min(minY, element.getY1());
                maxY = Math.max(maxY, element.getY2());
            }
        }

        return new int[]{minY, maxY};
    }

    /**
     * 공간 분석 통계 생성
     */
    public SpatialAnalysisStatistics generateSpatialStatistics(List<LayoutBlock> elements,
                                                              Map<String, Integer> questionPositions) {
        int totalElements = elements.size();
        int assignedElements = 0;
        Map<String, Integer> elementsByQuestion = new HashMap<>();

        for (LayoutBlock element : elements) {
            String assignedQuestion = assignLayoutBlockToQuestion(element, questionPositions);
            if (!"unknown".equals(assignedQuestion)) {
                assignedElements++;
                elementsByQuestion.merge(assignedQuestion, 1, Integer::sum);
            }
        }

        double assignmentRate = totalElements > 0 ? (double) assignedElements / totalElements : 0.0;

        return new SpatialAnalysisStatistics(
            totalElements, assignedElements, totalElements - assignedElements,
            elementsByQuestion, assignmentRate
        );
    }

    /**
     * 공간 분석 통계 클래스
     */
    public static class SpatialAnalysisStatistics {
        private final int totalElements;
        private final int assignedElements;
        private final int unassignedElements;
        private final Map<String, Integer> elementsByQuestion;
        private final double assignmentRate;

        public SpatialAnalysisStatistics(int totalElements, int assignedElements,
                                       int unassignedElements, Map<String, Integer> elementsByQuestion,
                                       double assignmentRate) {
            this.totalElements = totalElements;
            this.assignedElements = assignedElements;
            this.unassignedElements = unassignedElements;
            this.elementsByQuestion = elementsByQuestion;
            this.assignmentRate = assignmentRate;
        }

        // Getters
        public int getTotalElements() { return totalElements; }
        public int getAssignedElements() { return assignedElements; }
        public int getUnassignedElements() { return unassignedElements; }
        public Map<String, Integer> getElementsByQuestion() { return elementsByQuestion; }
        public double getAssignmentRate() { return assignmentRate; }
    }
}