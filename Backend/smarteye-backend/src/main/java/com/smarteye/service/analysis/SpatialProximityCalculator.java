package com.smarteye.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 공간 근접성 계산 통합 유틸리티
 *
 * 기존 TSPMEngine과 StructuredJSONService에 분산되어 있던
 * Y좌표 기반 proximity 알고리즘을 통합
 *
 * SOLID 원칙 적용:
 * - 단일 책임: 공간적 거리 계산만 담당
 * - 개방-폐쇄: 새로운 거리 계산 방식 추가 가능
 */
@Component
public class SpatialProximityCalculator {

    private static final Logger logger = LoggerFactory.getLogger(SpatialProximityCalculator.class);

    // proximity 알고리즘 임계값 (레거시 Python과 동일)
    public static final int DEFAULT_PROXIMITY_THRESHOLD = 500;

    /**
     * 요소를 가장 가까운 문제에 할당
     * 기존 TSPMEngine.assignElementToQuestion() 통합
     *
     * @param elementY 요소의 Y좌표
     * @param questionPositions 문제별 Y좌표 맵
     * @return 할당된 문제 번호 (임계값 초과시 "unknown")
     */
    public String assignElementToNearestQuestion(int elementY, Map<String, Integer> questionPositions) {
        return assignElementToNearestQuestion(elementY, questionPositions, DEFAULT_PROXIMITY_THRESHOLD);
    }

    /**
     * 요소를 가장 가까운 문제에 할당 (임계값 커스터마이징)
     *
     * @param elementY 요소의 Y좌표
     * @param questionPositions 문제별 Y좌표 맵
     * @param proximityThreshold 근접성 임계값
     * @return 할당된 문제 번호 (임계값 초과시 "unknown")
     */
    public String assignElementToNearestQuestion(int elementY,
                                               Map<String, Integer> questionPositions,
                                               int proximityThreshold) {
        if (questionPositions == null || questionPositions.isEmpty()) {
            logger.warn("문제 위치 정보가 없음");
            return "unknown";
        }

        String bestQuestion = "unknown";
        int minDistance = Integer.MAX_VALUE;

        // 가장 가까운 문제 찾기
        for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
            int distance = calculateDistance(elementY, entry.getValue());

            if (distance < minDistance) {
                minDistance = distance;
                bestQuestion = entry.getKey();
            }
        }

        // 거리 임계값 확인
        if (minDistance > proximityThreshold) {
            logger.debug("⚠️ 요소 Y={} 가 가장 가까운 문제와 거리 {}px로 임계값 {}px 초과",
                        elementY, minDistance, proximityThreshold);
            return "unknown";
        }

        logger.debug("✅ 요소 Y={} → 문제 {} 할당 (거리: {}px)",
                    elementY, bestQuestion, minDistance);
        return bestQuestion;
    }

    /**
     * 두 Y좌표 간의 거리 계산
     *
     * @param y1 첫 번째 Y좌표
     * @param y2 두 번째 Y좌표
     * @return 절대 거리값
     */
    public int calculateDistance(int y1, int y2) {
        return Math.abs(y1 - y2);
    }

    /**
     * 요소가 문제 범위 내에 있는지 확인
     *
     * @param elementY 요소의 Y좌표
     * @param questionY 문제의 Y좌표
     * @param nextQuestionY 다음 문제의 Y좌표 (경계)
     * @return 범위 내 포함 여부
     */
    public boolean isElementInQuestionRange(int elementY, int questionY, int nextQuestionY) {
        if (nextQuestionY == Integer.MAX_VALUE) {
            // 마지막 문제의 경우 범위 제한 없음
            return elementY >= questionY;
        }
        return questionY <= elementY && elementY < nextQuestionY;
    }

    /**
     * 근접성 분석 결과 클래스
     */
    public static class ProximityResult {
        private final String assignedQuestion;
        private final int distance;
        private final boolean withinThreshold;

        public ProximityResult(String assignedQuestion, int distance, boolean withinThreshold) {
            this.assignedQuestion = assignedQuestion;
            this.distance = distance;
            this.withinThreshold = withinThreshold;
        }

        public String getAssignedQuestion() { return assignedQuestion; }
        public int getDistance() { return distance; }
        public boolean isWithinThreshold() { return withinThreshold; }
    }

    /**
     * 상세한 근접성 분석 수행
     *
     * @param elementY 요소의 Y좌표
     * @param questionPositions 문제별 Y좌표 맵
     * @param proximityThreshold 근접성 임계값
     * @return 상세 분석 결과
     */
    public ProximityResult analyzeProximity(int elementY,
                                          Map<String, Integer> questionPositions,
                                          int proximityThreshold) {
        String assignedQuestion = assignElementToNearestQuestion(elementY, questionPositions, proximityThreshold);

        int minDistance = Integer.MAX_VALUE;
        if (!"unknown".equals(assignedQuestion) && questionPositions.containsKey(assignedQuestion)) {
            minDistance = calculateDistance(elementY, questionPositions.get(assignedQuestion));
        }

        boolean withinThreshold = minDistance <= proximityThreshold;

        return new ProximityResult(assignedQuestion, minDistance, withinThreshold);
    }
}