package com.smarteye.application.analysis.engine;

import com.smarteye.application.analysis.engine.ColumnDetector.ColumnRange;
import com.smarteye.application.analysis.engine.ColumnDetector.PositionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 2D 공간 분석 엔진 (2D Spatial Analyzer)
 *
 * <p>X, Y 좌표를 모두 고려한 2차원 공간 분석을 통해
 * 레이아웃 요소를 가장 적합한 문제 번호에 할당합니다.
 * 다단(Multi-column) 레이아웃 처리의 핵심 컴포넌트입니다.</p>
 *
 * <h3>알고리즘: Column-Aware 2D Distance</h3>
 * <ol>
 *   <li>요소가 속한 컬럼 판단 (X좌표 기준)</li>
 *   <li>같은 컬럼 내의 문제 번호들만 후보로 선택</li>
 *   <li>2D 유클리드 거리 계산: sqrt((dx)² + (dy)²)</li>
 *   <li>최소 거리의 문제 번호에 할당</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * List<ColumnRange> columns = columnDetector.detectColumns(...);
 * Map<String, PositionInfo> questionPositions = ...;
 * String assignedQuestion = spatial2DAnalyzer.assignElementToQuestion(
 *     elementX, elementY, questionPositions, columns
 * );
 * }</pre>
 *
 * @author SmartEye Backend Team
 * @since v0.7
 * @see ColumnDetector
 */
@Component
public class Spatial2DAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(Spatial2DAnalyzer.class);

    // ============================================================================
    // 2D 분석 상수
    // ============================================================================

    /**
     * 거리 계산 방식
     */
    public enum DistanceMetric {
        /** 유클리드 거리: sqrt((x2-x1)² + (y2-y1)²) */
        EUCLIDEAN,
        /** 맨해튼 거리: |x2-x1| + |y2-y1| */
        MANHATTAN
    }

    /**
     * 기본 최대 할당 거리 (px)
     * <p>이 값보다 멀리 떨어진 문제에는 할당하지 않음</p>
     */
    public static final int DEFAULT_MAX_ASSIGNMENT_DISTANCE = 500;

    /**
     * Y축 가중치 (거리 계산 시)
     * <p>일반적으로 Y 거리가 X 거리보다 중요하므로 가중치 부여</p>
     */
    public static final double DEFAULT_Y_WEIGHT = 1.5;

    /**
     * X축 가중치 (거리 계산 시)
     */
    public static final double DEFAULT_X_WEIGHT = 1.0;

    /**
     * 방향성 가중치 비율
     * <p>문제 번호 아래쪽 요소에 우선순위 부여 (10% 감소)</p>
     */
    public static final double DIRECTION_WEIGHT_BELOW = 0.9;

    // ============================================================================
    // 핵심 메서드
    // ============================================================================

    /**
     * 레이아웃 요소를 가장 가까운 문제에 할당 (2D 분석)
     *
     * @param elementX 요소의 X좌표
     * @param elementY 요소의 Y좌표
     * @param questionPositions 문제 번호 → 위치 정보 매핑
     * @param columns 감지된 컬럼 범위 리스트
     * @return 할당된 문제 번호 (실패 시 "unknown")
     */
    public String assignElementToQuestion(
            int elementX,
            int elementY,
            Map<String, PositionInfo> questionPositions,
            List<ColumnRange> columns) {

        return assignElementToQuestion(
            elementX, elementY, questionPositions, columns,
            DistanceMetric.EUCLIDEAN, DEFAULT_MAX_ASSIGNMENT_DISTANCE
        );
    }

    /**
     * 커스터마이징 가능한 2D 할당 메서드
     *
     * @param metric 거리 계산 방식 (유클리드/맨해튼)
     * @param maxDistance 최대 할당 거리 (px)
     */
    public String assignElementToQuestion(
            int elementX,
            int elementY,
            Map<String, PositionInfo> questionPositions,
            List<ColumnRange> columns,
            DistanceMetric metric,
            int maxDistance) {

        if (questionPositions == null || questionPositions.isEmpty()) {
            logger.debug("⚠️ 문제 위치 정보 없음 - unknown 반환");
            return "unknown";
        }

        // 1. 요소가 속한 컬럼 찾기
        ColumnRange elementColumn = findColumnForElement(elementX, columns);

        if (elementColumn == null) {
            logger.warn("⚠️ 요소 X={}가 어떤 컬럼에도 속하지 않음", elementX);
            return "unknown";
        }

        logger.trace("📍 요소 (X={}, Y={}) → {}", elementX, elementY, elementColumn);

        // 2. 같은 컬럼 내의 문제 번호들만 필터링
        Map<String, PositionInfo> candidateQuestions = filterQuestionsInColumn(
            questionPositions, elementColumn
        );

        if (candidateQuestions.isEmpty()) {
            logger.debug("⚠️ {}에 문제 번호 없음", elementColumn);
            return "unknown";
        }

        // 3. 2D 거리 계산 및 최소 거리 문제 선택
        AssignmentResult result = findNearestQuestion(
            elementX, elementY, candidateQuestions, metric, maxDistance
        );

        // 4. 결과 로깅 및 반환
        if (result.isAssigned()) {
            logger.trace("✅ 요소 (X={}, Y={}) → 문제 {} (거리: {:.1f}px, 컬럼: {})",
                        elementX, elementY, result.getQuestionNumber(),
                        result.getDistance(), elementColumn.getColumnIndex());
            return result.getQuestionNumber();
        } else {
            logger.debug("❌ 요소 (X={}, Y={}) 할당 실패: 최소거리 {:.1f}px > 임계값 {}px",
                        elementX, elementY, result.getDistance(), maxDistance);
            return "unknown";
        }
    }

    // ============================================================================
    // 내부 헬퍼 메서드
    // ============================================================================

    /**
     * 요소의 X좌표로 속한 컬럼 찾기
     */
    private ColumnRange findColumnForElement(int elementX, List<ColumnRange> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }

        return columns.stream()
            .filter(col -> col.contains(elementX))
            .findFirst()
            .orElse(null);
    }

    /**
     * 특정 컬럼 내의 문제 번호들만 필터링
     */
    private Map<String, PositionInfo> filterQuestionsInColumn(
            Map<String, PositionInfo> allQuestions,
            ColumnRange column) {

        return allQuestions.entrySet().stream()
            .filter(entry -> column.contains(entry.getValue().getX()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }

    /**
     * 2D 거리 기반 최근접 문제 찾기
     */
    private AssignmentResult findNearestQuestion(
            int elementX,
            int elementY,
            Map<String, PositionInfo> candidateQuestions,
            DistanceMetric metric,
            int maxDistance) {

        String bestQuestion = null;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<String, PositionInfo> entry : candidateQuestions.entrySet()) {
            String questionNum = entry.getKey();
            PositionInfo questionPos = entry.getValue();

            // 2D 거리 계산
            double distance = calculateDistance(
                elementX, elementY,
                questionPos.getX(), questionPos.getY(),
                metric
            );

            // 방향성 가중치 적용 (문제 아래쪽 요소 우선)
            if (elementY > questionPos.getY()) {
                distance *= DIRECTION_WEIGHT_BELOW;
            }

            if (distance < minDistance) {
                minDistance = distance;
                bestQuestion = questionNum;
            }
        }

        boolean assigned = (minDistance <= maxDistance);
        return new AssignmentResult(bestQuestion, minDistance, assigned);
    }

    /**
     * 2D 거리 계산 (메트릭에 따라)
     */
    private double calculateDistance(
            int x1, int y1,
            int x2, int y2,
            DistanceMetric metric) {

        double dx = (x2 - x1) * DEFAULT_X_WEIGHT;
        double dy = (y2 - y1) * DEFAULT_Y_WEIGHT;

        switch (metric) {
            case EUCLIDEAN:
                return Math.sqrt(dx * dx + dy * dy);
            case MANHATTAN:
                return Math.abs(dx) + Math.abs(dy);
            default:
                throw new IllegalArgumentException("Unknown distance metric: " + metric);
        }
    }

    // ============================================================================
    // 고급 기능 메서드
    // ============================================================================

    /**
     * 배치 할당: 여러 요소를 한번에 처리
     *
     * @param elements 요소 리스트 (X, Y 좌표 포함)
     * @return 요소 → 문제 번호 매핑
     */
    public Map<ElementPosition, String> assignElementsBatch(
            List<ElementPosition> elements,
            Map<String, PositionInfo> questionPositions,
            List<ColumnRange> columns) {

        Map<ElementPosition, String> assignments = new HashMap<>();

        for (ElementPosition element : elements) {
            String assigned = assignElementToQuestion(
                element.getX(), element.getY(),
                questionPositions, columns
            );
            assignments.put(element, assigned);
        }

        return assignments;
    }

    /**
     * 할당 품질 분석
     *
     * @return 할당 통계 정보
     */
    public AssignmentStatistics analyzeAssignmentQuality(
            List<ElementPosition> elements,
            Map<String, PositionInfo> questionPositions,
            List<ColumnRange> columns) {

        int totalElements = elements.size();
        int assignedElements = 0;
        int unknownElements = 0;
        Map<String, Integer> elementsByQuestion = new HashMap<>();
        double totalDistance = 0.0;

        for (ElementPosition element : elements) {
            String assigned = assignElementToQuestion(
                element.getX(), element.getY(),
                questionPositions, columns
            );

            if ("unknown".equals(assigned)) {
                unknownElements++;
            } else {
                assignedElements++;
                elementsByQuestion.merge(assigned, 1, Integer::sum);

                // 거리 계산
                PositionInfo qPos = questionPositions.get(assigned);
                if (qPos != null) {
                    double dist = calculateDistance(
                        element.getX(), element.getY(),
                        qPos.getX(), qPos.getY(),
                        DistanceMetric.EUCLIDEAN
                    );
                    totalDistance += dist;
                }
            }
        }

        double assignmentRate = totalElements > 0 ? (double) assignedElements / totalElements : 0.0;
        double avgDistance = assignedElements > 0 ? totalDistance / assignedElements : 0.0;

        return new AssignmentStatistics(
            totalElements, assignedElements, unknownElements,
            assignmentRate, avgDistance, elementsByQuestion
        );
    }

    // ============================================================================
    // 데이터 클래스
    // ============================================================================

    /**
     * 할당 결과
     */
    private static class AssignmentResult {
        private final String questionNumber;
        private final double distance;
        private final boolean assigned;

        public AssignmentResult(String questionNumber, double distance, boolean assigned) {
            this.questionNumber = questionNumber;
            this.distance = distance;
            this.assigned = assigned;
        }

        public String getQuestionNumber() { return questionNumber; }
        public double getDistance() { return distance; }
        public boolean isAssigned() { return assigned; }
    }

    /**
     * 요소 위치 정보
     */
    public static class ElementPosition {
        private final int x;
        private final int y;
        private final String id; // 선택적 식별자

        public ElementPosition(int x, int y, String id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }

        public ElementPosition(int x, int y) {
            this(x, y, null);
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public String getId() { return id; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ElementPosition that = (ElementPosition) o;
            return x == that.x && y == that.y && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, id);
        }

        @Override
        public String toString() {
            return String.format("Element(x=%d, y=%d, id=%s)", x, y, id);
        }
    }

    /**
     * 할당 통계 정보
     */
    public static class AssignmentStatistics {
        private final int totalElements;
        private final int assignedElements;
        private final int unknownElements;
        private final double assignmentRate;
        private final double averageDistance;
        private final Map<String, Integer> elementsByQuestion;

        public AssignmentStatistics(
                int totalElements, int assignedElements, int unknownElements,
                double assignmentRate, double averageDistance,
                Map<String, Integer> elementsByQuestion) {
            this.totalElements = totalElements;
            this.assignedElements = assignedElements;
            this.unknownElements = unknownElements;
            this.assignmentRate = assignmentRate;
            this.averageDistance = averageDistance;
            this.elementsByQuestion = elementsByQuestion;
        }

        public int getTotalElements() { return totalElements; }
        public int getAssignedElements() { return assignedElements; }
        public int getUnknownElements() { return unknownElements; }
        public double getAssignmentRate() { return assignmentRate; }
        public double getAverageDistance() { return averageDistance; }
        public Map<String, Integer> getElementsByQuestion() { return elementsByQuestion; }

        @Override
        public String toString() {
            return String.format(
                "AssignmentStats(total=%d, assigned=%d, unknown=%d, rate=%.1f%%, avgDist=%.1fpx)",
                totalElements, assignedElements, unknownElements,
                assignmentRate * 100, averageDistance
            );
        }
    }
}
