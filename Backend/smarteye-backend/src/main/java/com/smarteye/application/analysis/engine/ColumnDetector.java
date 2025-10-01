package com.smarteye.application.analysis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 컬럼 감지 엔진 (Column Detector)
 *
 * <p>문제 번호들의 X좌표 분포를 분석하여 페이지의 컬럼 구조를 감지합니다.
 * 다단(Multi-column) 레이아웃을 처리하기 위한 핵심 컴포넌트입니다.</p>
 *
 * <h3>알고리즘: Gap Detection</h3>
 * <ol>
 *   <li>모든 문제 번호의 X좌표를 수집하고 정렬</li>
 *   <li>인접한 X좌표 간의 간격(gap)을 계산</li>
 *   <li>간격이 임계값보다 크면 컬럼 경계로 판단</li>
 *   <li>각 컬럼의 [시작 X, 끝 X] 범위를 반환</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * Map<String, Integer> questionPositions = questionNumberExtractor.extract(...);
 * List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);
 * }</pre>
 *
 * @author SmartEye Backend Team
 * @since v0.7
 * @see Spatial2DAnalyzer
 */
@Component
public class ColumnDetector {

    private static final Logger logger = LoggerFactory.getLogger(ColumnDetector.class);

    // ============================================================================
    // 컬럼 감지 상수
    // ============================================================================

    /**
     * 기본 컬럼 간격 비율 (페이지 너비 대비)
     * <p>예: 페이지 너비가 1000px일 때, 100px 이상 간격이면 컬럼 경계로 판단</p>
     */
    public static final double DEFAULT_COLUMN_GAP_RATIO = 0.1;

    /**
     * 최소 컬럼 간격 (절대값, px)
     * <p>페이지 너비가 매우 작아도 최소 이 값 이상의 간격이 있어야 컬럼 경계로 판단</p>
     */
    public static final int MIN_COLUMN_GAP_PX = 50;

    /**
     * 최대 컬럼 간격 (절대값, px)
     * <p>이 값보다 큰 간격은 무시 (데이터 오류로 간주)</p>
     */
    public static final int MAX_COLUMN_GAP_PX = 800;

    /**
     * 단일 컬럼 판단 최소 문제 수
     * <p>문제가 이보다 적으면 무조건 단일 컬럼으로 판단</p>
     */
    public static final int MIN_QUESTIONS_FOR_MULTI_COLUMN = 2;

    // ============================================================================
    // 핵심 메서드
    // ============================================================================

    /**
     * 문제 위치 정보로부터 컬럼 구조를 감지합니다.
     *
     * @param questionPositions 문제 번호 → X좌표 매핑 (QuestionNumberExtractor 출력)
     * @param pageWidth 페이지 너비 (px)
     * @return 감지된 컬럼 범위 리스트 (왼쪽부터 정렬됨)
     */
    public List<ColumnRange> detectColumns(Map<String, PositionInfo> questionPositions, int pageWidth) {
        if (questionPositions == null || questionPositions.isEmpty()) {
            logger.warn("⚠️ 문제 위치 정보가 없음 - 기본 단일 컬럼 반환");
            return createSingleColumnLayout(pageWidth);
        }

        if (questionPositions.size() < MIN_QUESTIONS_FOR_MULTI_COLUMN) {
            logger.debug("📄 문제 수 {}개 < {} - 단일 컬럼으로 판단",
                        questionPositions.size(), MIN_QUESTIONS_FOR_MULTI_COLUMN);
            return createSingleColumnLayout(pageWidth);
        }

        // 1. X좌표 수집 및 정렬
        List<Integer> xCoordinates = extractAndSortXCoordinates(questionPositions);

        // 2. Gap 분석으로 컬럼 경계 찾기
        List<Integer> columnBoundaries = findColumnBoundaries(xCoordinates, pageWidth);

        // 3. 컬럼 범위 생성
        List<ColumnRange> columns = createColumnRanges(columnBoundaries, pageWidth);

        logger.info("🔍 컬럼 감지 완료: {}개 컬럼 (페이지 너비: {}px, 문제 수: {}개)",
                   columns.size(), pageWidth, questionPositions.size());

        for (int i = 0; i < columns.size(); i++) {
            ColumnRange col = columns.get(i);
            logger.debug("  📐 컬럼 {}: X범위 [{} ~ {}] (너비: {}px)",
                        i + 1, col.getStartX(), col.getEndX(), col.getWidth());
        }

        return columns;
    }

    /**
     * PositionInfo 없이 단순 X좌표 맵으로 감지 (하위 호환성)
     */
    public List<ColumnRange> detectColumns(Map<String, Integer> simplePositions, int pageWidth, boolean isSimpleMap) {
        if (!isSimpleMap) {
            throw new IllegalArgumentException("Use detectColumns(Map<String, PositionInfo>, int) for PositionInfo");
        }

        Map<String, PositionInfo> converted = simplePositions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> new PositionInfo(e.getValue(), 0) // Y좌표는 0으로 (사용 안 함)
            ));

        return detectColumns(converted, pageWidth);
    }

    // ============================================================================
    // 내부 헬퍼 메서드
    // ============================================================================

    /**
     * X좌표 추출 및 정렬
     */
    private List<Integer> extractAndSortXCoordinates(Map<String, PositionInfo> questionPositions) {
        return questionPositions.values().stream()
            .map(PositionInfo::getX)
            .sorted()
            .distinct() // 중복 제거
            .collect(Collectors.toList());
    }

    /**
     * Gap Detection 알고리즘으로 컬럼 경계 찾기
     *
     * @return 컬럼 시작 X좌표 리스트 (첫 번째는 항상 0)
     */
    private List<Integer> findColumnBoundaries(List<Integer> sortedXCoords, int pageWidth) {
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0); // 첫 번째 컬럼 시작점

        if (sortedXCoords.size() < 2) {
            return boundaries; // 단일 컬럼
        }

        // 적응형 임계값 계산
        int gapThreshold = calculateGapThreshold(pageWidth);
        logger.debug("🎯 컬럼 간격 임계값: {}px (페이지 너비: {}px)", gapThreshold, pageWidth);

        // 인접 X좌표 간 간격 분석
        for (int i = 1; i < sortedXCoords.size(); i++) {
            int gap = sortedXCoords.get(i) - sortedXCoords.get(i - 1);

            if (gap >= gapThreshold && gap <= MAX_COLUMN_GAP_PX) {
                // 컬럼 경계 발견
                int boundaryX = (sortedXCoords.get(i - 1) + sortedXCoords.get(i)) / 2; // 중간점
                boundaries.add(boundaryX);
                logger.debug("  ✅ 컬럼 경계 발견: X={}px (간격: {}px)", boundaryX, gap);
            } else if (gap > MAX_COLUMN_GAP_PX) {
                logger.warn("  ⚠️ 비정상적으로 큰 간격 무시: {}px (X좌표: {} ~ {})",
                           gap, sortedXCoords.get(i - 1), sortedXCoords.get(i));
            }
        }

        return boundaries;
    }

    /**
     * 적응형 간격 임계값 계산
     */
    private int calculateGapThreshold(int pageWidth) {
        int adaptiveGap = (int) (pageWidth * DEFAULT_COLUMN_GAP_RATIO);

        // 범위 제한
        adaptiveGap = Math.max(MIN_COLUMN_GAP_PX, adaptiveGap);
        adaptiveGap = Math.min(MAX_COLUMN_GAP_PX, adaptiveGap);

        return adaptiveGap;
    }

    /**
     * 컬럼 범위 객체 생성
     */
    private List<ColumnRange> createColumnRanges(List<Integer> boundaries, int pageWidth) {
        List<ColumnRange> columns = new ArrayList<>();

        for (int i = 0; i < boundaries.size(); i++) {
            int startX = boundaries.get(i);
            int endX = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) : pageWidth;

            columns.add(new ColumnRange(i, startX, endX));
        }

        return columns;
    }

    /**
     * 단일 컬럼 레이아웃 생성 (기본값)
     */
    private List<ColumnRange> createSingleColumnLayout(int pageWidth) {
        return Collections.singletonList(new ColumnRange(0, 0, pageWidth));
    }

    // ============================================================================
    // 데이터 클래스
    // ============================================================================

    /**
     * 위치 정보 (X, Y 좌표)
     */
    public static class PositionInfo {
        private final int x;
        private final int y;

        public PositionInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y; }

        @Override
        public String toString() {
            return String.format("Position(x=%d, y=%d)", x, y);
        }
    }

    /**
     * 컬럼 범위 정보
     */
    public static class ColumnRange {
        private final int columnIndex;  // 컬럼 인덱스 (0부터 시작)
        private final int startX;       // 시작 X좌표
        private final int endX;         // 끝 X좌표

        public ColumnRange(int columnIndex, int startX, int endX) {
            if (startX >= endX) {
                throw new IllegalArgumentException(
                    String.format("Invalid column range: startX(%d) >= endX(%d)", startX, endX));
            }
            this.columnIndex = columnIndex;
            this.startX = startX;
            this.endX = endX;
        }

        public int getColumnIndex() { return columnIndex; }
        public int getStartX() { return startX; }
        public int getEndX() { return endX; }
        public int getWidth() { return endX - startX; }

        /**
         * 주어진 X좌표가 이 컬럼에 속하는지 판단
         */
        public boolean contains(int x) {
            return x >= startX && x < endX;
        }

        @Override
        public String toString() {
            return String.format("Column%d[%d~%d] (width=%d)",
                               columnIndex, startX, endX, getWidth());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ColumnRange that = (ColumnRange) o;
            return columnIndex == that.columnIndex &&
                   startX == that.startX &&
                   endX == that.endX;
        }

        @Override
        public int hashCode() {
            return Objects.hash(columnIndex, startX, endX);
        }
    }
}
