package com.smarteye.application.analysis.engine;

import com.smarteye.application.analysis.engine.ColumnDetector.ColumnRange;
import com.smarteye.application.analysis.engine.ColumnDetector.PositionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ColumnDetector 단위 테스트
 *
 * <p>컬럼 감지 알고리즘의 정확성을 검증합니다.</p>
 */
class ColumnDetectorTest {

    private ColumnDetector columnDetector;

    @BeforeEach
    void setUp() {
        columnDetector = new ColumnDetector();
    }

    // ============================================================================
    // 1단 레이아웃 테스트
    // ============================================================================

    @Test
    @DisplayName("1단 레이아웃: 모든 문제가 같은 X 범위에 있으면 단일 컬럼")
    void testSingleColumnLayout() {
        // Given: 모든 문제가 X=50~100 범위에 있는 경우
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(50, 100));
        questionPositions.put("2", new PositionInfo(60, 300));
        questionPositions.put("3", new PositionInfo(70, 500));
        questionPositions.put("4", new PositionInfo(80, 700));

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        assertEquals(1, columns.size(), "단일 컬럼이어야 함");
        ColumnRange column = columns.get(0);
        assertEquals(0, column.getColumnIndex());
        assertEquals(0, column.getStartX());
        assertEquals(pageWidth, column.getEndX());
    }

    // ============================================================================
    // 2단 레이아웃 테스트
    // ============================================================================

    @Test
    @DisplayName("2단 레이아웃: X좌표 간격이 큰 경우 2개 컬럼 감지")
    void testTwoColumnLayout() {
        // Given: 왼쪽(X=50~100) vs 오른쪽(X=550~600) 컬럼
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        // 왼쪽 컬럼
        questionPositions.put("1", new PositionInfo(50, 100));
        questionPositions.put("2", new PositionInfo(70, 300));
        // 오른쪽 컬럼
        questionPositions.put("3", new PositionInfo(550, 100));
        questionPositions.put("4", new PositionInfo(570, 300));

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        assertEquals(2, columns.size(), "2개 컬럼이어야 함");

        // 컬럼 0 (왼쪽)
        ColumnRange col0 = columns.get(0);
        assertEquals(0, col0.getColumnIndex());
        assertTrue(col0.contains(50), "왼쪽 컬럼이 X=50 포함해야 함");
        assertTrue(col0.contains(70), "왼쪽 컬럼이 X=70 포함해야 함");
        assertFalse(col0.contains(550), "왼쪽 컬럼이 X=550 포함하지 않아야 함");

        // 컬럼 1 (오른쪽)
        ColumnRange col1 = columns.get(1);
        assertEquals(1, col1.getColumnIndex());
        assertTrue(col1.contains(550), "오른쪽 컬럼이 X=550 포함해야 함");
        assertTrue(col1.contains(570), "오른쪽 컬럼이 X=570 포함해야 함");
        assertFalse(col1.contains(50), "오른쪽 컬럼이 X=50 포함하지 않아야 함");
    }

    @Test
    @DisplayName("2단 레이아웃: 정확히 50% 위치에서 컬럼 분리")
    void testTwoColumnLayoutAtHalfPage() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));  // 왼쪽
        questionPositions.put("3", new PositionInfo(600, 100));  // 오른쪽 (50% 지점 이후)

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        assertEquals(2, columns.size(), "2개 컬럼이어야 함");
        assertTrue(columns.get(0).contains(100));
        assertTrue(columns.get(1).contains(600));
    }

    // ============================================================================
    // 3단 레이아웃 테스트
    // ============================================================================

    @Test
    @DisplayName("3단 레이아웃: X좌표 간격이 큰 경우 3개 컬럼 감지")
    void testThreeColumnLayout() {
        // Given: 왼쪽(X=50) vs 중앙(X=400) vs 오른쪽(X=750) 컬럼
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(50, 100));   // 왼쪽
        questionPositions.put("4", new PositionInfo(400, 100));  // 중앙
        questionPositions.put("7", new PositionInfo(750, 100));  // 오른쪽

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        assertEquals(3, columns.size(), "3개 컬럼이어야 함");
        assertTrue(columns.get(0).contains(50), "첫 번째 컬럼이 X=50 포함해야 함");
        assertTrue(columns.get(1).contains(400), "두 번째 컬럼이 X=400 포함해야 함");
        assertTrue(columns.get(2).contains(750), "세 번째 컬럼이 X=750 포함해야 함");
    }

    // ============================================================================
    // 엣지 케이스 테스트
    // ============================================================================

    @Test
    @DisplayName("엣지 케이스: 문제 위치 정보 없음 → 기본 단일 컬럼 반환")
    void testEmptyQuestionPositions() {
        // Given
        Map<String, PositionInfo> emptyPositions = new HashMap<>();
        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(emptyPositions, pageWidth);

        // Then
        assertEquals(1, columns.size(), "빈 입력도 기본 단일 컬럼 반환");
        assertEquals(0, columns.get(0).getStartX());
        assertEquals(pageWidth, columns.get(0).getEndX());
    }

    @Test
    @DisplayName("엣지 케이스: 문제 1개만 → 단일 컬럼")
    void testSingleQuestion() {
        // Given
        Map<String, PositionInfo> singleQuestion = new HashMap<>();
        singleQuestion.put("1", new PositionInfo(200, 100));

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(singleQuestion, pageWidth);

        // Then
        assertEquals(1, columns.size(), "문제 1개는 단일 컬럼");
    }

    @Test
    @DisplayName("엣지 케이스: 중복 X좌표 → 단일 컬럼")
    void testDuplicateXCoordinates() {
        // Given: 모든 문제가 같은 X좌표
        Map<String, PositionInfo> sameXPositions = new HashMap<>();
        sameXPositions.put("1", new PositionInfo(100, 100));
        sameXPositions.put("2", new PositionInfo(100, 300));
        sameXPositions.put("3", new PositionInfo(100, 500));

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(sameXPositions, pageWidth);

        // Then
        assertEquals(1, columns.size(), "같은 X좌표는 단일 컬럼");
    }

    @Test
    @DisplayName("엣지 케이스: 매우 큰 간격 (800px 이상) → 무시하고 단일 컬럼")
    void testVeryLargeGap() {
        // Given: 비정상적으로 큰 간격 (900px)
        Map<String, PositionInfo> largeGapPositions = new HashMap<>();
        largeGapPositions.put("1", new PositionInfo(50, 100));
        largeGapPositions.put("2", new PositionInfo(950, 100)); // 900px 간격

        int pageWidth = 1000;

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(largeGapPositions, pageWidth);

        // Then
        // 매우 큰 간격은 데이터 오류로 간주하여 무시 → 단일 컬럼
        assertEquals(1, columns.size(), "비정상적으로 큰 간격은 무시");
    }

    // ============================================================================
    // ColumnRange 테스트
    // ============================================================================

    @Test
    @DisplayName("ColumnRange.contains() 메서드 정확성 검증")
    void testColumnRangeContains() {
        // Given
        ColumnRange column = new ColumnRange(0, 100, 500);

        // When & Then
        assertTrue(column.contains(100), "시작점 포함");
        assertTrue(column.contains(300), "중간점 포함");
        assertFalse(column.contains(500), "끝점 미포함 (< 연산)");
        assertFalse(column.contains(50), "범위 밖 (왼쪽)");
        assertFalse(column.contains(600), "범위 밖 (오른쪽)");
    }

    @Test
    @DisplayName("ColumnRange.getWidth() 정확성 검증")
    void testColumnRangeWidth() {
        // Given
        ColumnRange column = new ColumnRange(0, 100, 500);

        // When & Then
        assertEquals(400, column.getWidth(), "너비 = 끝 - 시작");
    }

    @Test
    @DisplayName("ColumnRange 생성 시 잘못된 범위 → 예외 발생")
    void testColumnRangeInvalidRange() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new ColumnRange(0, 500, 100); // startX >= endX
        }, "startX >= endX이면 예외 발생해야 함");
    }

    // ============================================================================
    // 적응형 임계값 테스트
    // ============================================================================

    @Test
    @DisplayName("적응형 임계값: 작은 페이지(500px)에서는 낮은 임계값")
    void testAdaptiveThresholdSmallPage() {
        // Given: 작은 페이지
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(50, 100));
        questionPositions.put("2", new PositionInfo(200, 100)); // 150px 간격

        int pageWidth = 500; // 작은 페이지

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        // 페이지 너비 * 0.1 = 50px 임계값
        // 150px 간격이므로 2개 컬럼으로 인식되어야 함
        assertEquals(2, columns.size(), "작은 페이지에서도 간격 비율 고려");
    }

    @Test
    @DisplayName("적응형 임계값: 큰 페이지(2000px)에서는 높은 임계값")
    void testAdaptiveThresholdLargePage() {
        // Given: 큰 페이지
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(250, 100)); // 150px 간격

        int pageWidth = 2000; // 큰 페이지

        // When
        List<ColumnRange> columns = columnDetector.detectColumns(questionPositions, pageWidth);

        // Then
        // 페이지 너비 * 0.1 = 200px 임계값
        // 150px 간격이므로 단일 컬럼으로 인식되어야 함
        assertEquals(1, columns.size(), "큰 페이지에서는 높은 임계값 적용");
    }
}
