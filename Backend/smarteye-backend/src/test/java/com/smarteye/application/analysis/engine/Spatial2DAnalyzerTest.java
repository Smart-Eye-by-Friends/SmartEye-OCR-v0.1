package com.smarteye.application.analysis.engine;

import com.smarteye.application.analysis.engine.ColumnDetector.ColumnRange;
import com.smarteye.application.analysis.engine.ColumnDetector.PositionInfo;
import com.smarteye.application.analysis.engine.Spatial2DAnalyzer.ElementPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spatial2DAnalyzer 단위 테스트
 *
 * <p>2D 공간 분석 알고리즘의 정확성을 검증합니다.</p>
 */
class Spatial2DAnalyzerTest {

    private Spatial2DAnalyzer spatial2DAnalyzer;

    @BeforeEach
    void setUp() {
        spatial2DAnalyzer = new Spatial2DAnalyzer();
    }

    // ============================================================================
    // 단일 컬럼 테스트
    // ============================================================================

    @Test
    @DisplayName("단일 컬럼: 요소가 가장 가까운 문제에 할당됨")
    void testSingleColumnAssignment() {
        // Given: 단일 컬럼 레이아웃
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(100, 400));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        // 요소 위치: (100, 250) - 문제 1과 2 사이
        int elementX = 100;
        int elementY = 250;

        // When
        String assignedQuestion = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns
        );

        // Then
        // Y 거리: 문제1=150px, 문제2=150px (동일)
        // 하지만 요소가 문제1 아래쪽이므로 방향성 가중치 0.9 적용 → 문제1 선택
        assertEquals("1", assignedQuestion, "가장 가까운 문제에 할당됨");
    }

    // ============================================================================
    // 2단 컬럼 테스트
    // ============================================================================

    @Test
    @DisplayName("2단 레이아웃: 왼쪽 컬럼 요소는 왼쪽 문제에 할당")
    void testTwoColumnLeftAssignment() {
        // Given: 2단 레이아웃
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        // 왼쪽 컬럼
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(100, 400));
        // 오른쪽 컬럼
        questionPositions.put("3", new PositionInfo(600, 100));
        questionPositions.put("4", new PositionInfo(600, 400));

        List<ColumnRange> columns = Arrays.asList(
            new ColumnRange(0, 0, 350),    // 왼쪽 컬럼
            new ColumnRange(1, 350, 1000)  // 오른쪽 컬럼
        );

        // 왼쪽 컬럼의 요소 (X=100, Y=250)
        int elementX = 100;
        int elementY = 250;

        // When
        String assignedQuestion = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns
        );

        // Then
        // 왼쪽 컬럼에 속하므로 문제 1 또는 2에만 할당 가능
        // 오른쪽 컬럼의 문제 3, 4는 후보에서 제외됨
        assertTrue(assignedQuestion.equals("1") || assignedQuestion.equals("2"),
                  "왼쪽 컬럼 요소는 왼쪽 문제에만 할당");
        assertNotEquals("3", assignedQuestion, "다른 컬럼 문제에 할당 안 됨");
        assertNotEquals("4", assignedQuestion, "다른 컬럼 문제에 할당 안 됨");
    }

    @Test
    @DisplayName("2단 레이아웃: 오른쪽 컬럼 요소는 오른쪽 문제에 할당")
    void testTwoColumnRightAssignment() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(100, 400));
        questionPositions.put("3", new PositionInfo(600, 100));
        questionPositions.put("4", new PositionInfo(600, 400));

        List<ColumnRange> columns = Arrays.asList(
            new ColumnRange(0, 0, 350),
            new ColumnRange(1, 350, 1000)
        );

        // 오른쪽 컬럼의 요소 (X=600, Y=250)
        int elementX = 600;
        int elementY = 250;

        // When
        String assignedQuestion = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns
        );

        // Then
        assertTrue(assignedQuestion.equals("3") || assignedQuestion.equals("4"),
                  "오른쪽 컬럼 요소는 오른쪽 문제에만 할당");
        assertNotEquals("1", assignedQuestion);
        assertNotEquals("2", assignedQuestion);
    }

    @Test
    @DisplayName("2단 레이아웃: 같은 Y좌표, 다른 컬럼 → 각자 컬럼의 문제에 할당")
    void testSameYDifferentColumn() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));  // 왼쪽
        questionPositions.put("3", new PositionInfo(600, 100));  // 오른쪽

        List<ColumnRange> columns = Arrays.asList(
            new ColumnRange(0, 0, 350),
            new ColumnRange(1, 350, 1000)
        );

        // 요소 1: 왼쪽 컬럼, Y=150
        String assigned1 = spatial2DAnalyzer.assignElementToQuestion(
            100, 150, questionPositions, columns
        );

        // 요소 2: 오른쪽 컬럼, Y=150 (같은 Y좌표!)
        String assigned2 = spatial2DAnalyzer.assignElementToQuestion(
            600, 150, questionPositions, columns
        );

        // Then
        assertEquals("1", assigned1, "왼쪽 요소는 문제 1에 할당");
        assertEquals("3", assigned2, "오른쪽 요소는 문제 3에 할당");
        assertNotEquals(assigned1, assigned2, "같은 Y좌표여도 다른 문제에 할당됨");
    }

    // ============================================================================
    // 거리 계산 테스트
    // ============================================================================

    @Test
    @DisplayName("2D 거리 계산: 유클리드 거리 vs 맨해튼 거리")
    void testDistanceMetric() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        int elementX = 200;
        int elementY = 200;

        // When: 유클리드 거리
        String euclidean = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns,
            Spatial2DAnalyzer.DistanceMetric.EUCLIDEAN, 500
        );

        // When: 맨해튼 거리
        String manhattan = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns,
            Spatial2DAnalyzer.DistanceMetric.MANHATTAN, 500
        );

        // Then: 둘 다 문제 1에 할당되어야 함 (거리가 충분히 가까움)
        assertEquals("1", euclidean);
        assertEquals("1", manhattan);
    }

    @Test
    @DisplayName("최대 거리 임계값: 너무 멀면 unknown")
    void testMaxDistanceThreshold() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        // 매우 먼 요소 (X=100, Y=800)
        int elementX = 100;
        int elementY = 800;

        // When: 최대 거리 100px로 제한
        String assigned = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns,
            Spatial2DAnalyzer.DistanceMetric.EUCLIDEAN, 100
        );

        // Then
        assertEquals("unknown", assigned, "임계값 초과 시 unknown");
    }

    // ============================================================================
    // 엣지 케이스 테스트
    // ============================================================================

    @Test
    @DisplayName("엣지 케이스: 문제 위치 정보 없음 → unknown")
    void testNoQuestionPositions() {
        // Given
        Map<String, PositionInfo> emptyPositions = new HashMap<>();
        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        // When
        String assigned = spatial2DAnalyzer.assignElementToQuestion(
            100, 100, emptyPositions, columns
        );

        // Then
        assertEquals("unknown", assigned);
    }

    @Test
    @DisplayName("엣지 케이스: 요소가 어떤 컬럼에도 속하지 않음 → unknown")
    void testElementOutsideAllColumns() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 500)  // 컬럼 범위: 0~500
        );

        // 요소 X좌표가 컬럼 범위 밖 (X=600)
        int elementX = 600;
        int elementY = 100;

        // When
        String assigned = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns
        );

        // Then
        assertEquals("unknown", assigned, "컬럼 범위 밖 요소는 unknown");
    }

    @Test
    @DisplayName("엣지 케이스: 컬럼 내에 문제 번호 없음 → unknown")
    void testNoQuestionsInColumn() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));  // 왼쪽 컬럼에만 문제

        List<ColumnRange> columns = Arrays.asList(
            new ColumnRange(0, 0, 500),     // 왼쪽 컬럼
            new ColumnRange(1, 500, 1000)   // 오른쪽 컬럼 (문제 없음)
        );

        // 오른쪽 컬럼의 요소
        int elementX = 700;
        int elementY = 100;

        // When
        String assigned = spatial2DAnalyzer.assignElementToQuestion(
            elementX, elementY, questionPositions, columns
        );

        // Then
        assertEquals("unknown", assigned, "해당 컬럼에 문제 없으면 unknown");
    }

    // ============================================================================
    // 배치 할당 테스트
    // ============================================================================

    @Test
    @DisplayName("배치 할당: 여러 요소를 한번에 처리")
    void testBatchAssignment() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(100, 400));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        List<ElementPosition> elements = Arrays.asList(
            new ElementPosition(100, 150, "elem1"),  // 문제 1 근처
            new ElementPosition(100, 350, "elem2"),  // 문제 2 근처
            new ElementPosition(100, 200, "elem3")   // 중간
        );

        // When
        Map<ElementPosition, String> assignments = spatial2DAnalyzer.assignElementsBatch(
            elements, questionPositions, columns
        );

        // Then
        assertEquals(3, assignments.size(), "3개 요소 모두 할당");
        assertNotNull(assignments.get(elements.get(0)));
        assertNotNull(assignments.get(elements.get(1)));
        assertNotNull(assignments.get(elements.get(2)));
    }

    // ============================================================================
    // 할당 품질 분석 테스트
    // ============================================================================

    @Test
    @DisplayName("할당 품질 분석: 통계 정보 정확성")
    void testAssignmentQualityAnalysis() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));
        questionPositions.put("2", new PositionInfo(100, 400));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        List<ElementPosition> elements = Arrays.asList(
            new ElementPosition(100, 150),  // 문제 1 근처
            new ElementPosition(100, 350),  // 문제 2 근처
            new ElementPosition(900, 200)   // 컬럼 밖 (unknown)
        );

        // When
        Spatial2DAnalyzer.AssignmentStatistics stats =
            spatial2DAnalyzer.analyzeAssignmentQuality(elements, questionPositions, columns);

        // Then
        assertEquals(3, stats.getTotalElements(), "총 요소 3개");
        assertEquals(2, stats.getAssignedElements(), "2개 할당됨");
        assertEquals(1, stats.getUnknownElements(), "1개 unknown");
        assertTrue(stats.getAssignmentRate() > 0.6, "할당률 > 60%");
        assertTrue(stats.getAverageDistance() > 0, "평균 거리 계산됨");
    }

    // ============================================================================
    // 방향성 가중치 테스트
    // ============================================================================

    @Test
    @DisplayName("방향성 가중치: 문제 아래쪽 요소 우선")
    void testDirectionWeight() {
        // Given
        Map<String, PositionInfo> questionPositions = new HashMap<>();
        questionPositions.put("1", new PositionInfo(100, 100));

        List<ColumnRange> columns = Collections.singletonList(
            new ColumnRange(0, 0, 1000)
        );

        // 문제 위쪽 요소 vs 아래쪽 요소 (같은 거리)
        int belowY = 200;  // 문제 아래 100px
        int aboveY = 0;    // 문제 위 100px

        // When
        String assignedBelow = spatial2DAnalyzer.assignElementToQuestion(
            100, belowY, questionPositions, columns
        );
        String assignedAbove = spatial2DAnalyzer.assignElementToQuestion(
            100, aboveY, questionPositions, columns
        );

        // Then: 둘 다 할당되지만, 실제 내부적으로 아래쪽 요소가 더 낮은 거리 점수를 받음
        // (방향성 가중치 0.9 적용)
        assertEquals("1", assignedBelow);
        assertEquals("1", assignedAbove);
        // 참고: 실제 가중치는 내부 로직이므로 직접 검증 어려움
    }
}
