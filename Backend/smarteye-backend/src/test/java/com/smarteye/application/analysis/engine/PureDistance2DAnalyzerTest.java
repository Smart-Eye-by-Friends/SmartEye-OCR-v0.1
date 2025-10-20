package com.smarteye.application.analysis.engine;

import com.smarteye.application.analysis.dto.BoundaryType;
import com.smarteye.application.analysis.dto.QuestionBoundary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PureDistance2DAnalyzer 단위 테스트
 * 
 * Phase 3 구현 검증:
 * - 순수 2D 유클리드 거리 계산
 * - 방향성 가중치 적용
 * - 적응형 임계값
 * - 예외 처리 (6가지 시나리오)
 * 
 * @version 1.0
 * @since 2025-10-20
 */
class PureDistance2DAnalyzerTest {

    private PureDistance2DAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PureDistance2DAnalyzer();
    }

    @Test
    @DisplayName("기본 테스트: 가장 가까운 문제에 할당")
    void testFindNearestQuestion_Basic() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 100));
        boundaries.add(createBoundary("002", 100, 300));
        boundaries.add(createBoundary("003", 100, 500));

        // 요소: (X=150, Y=200) → 001과 002 사이, 001이 더 가까움
        int elementX = 150;
        int elementY = 200;

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("001", result, "001이 가장 가까우므로 할당되어야 함");
    }

    @Test
    @DisplayName("방향성 가중치: 아래쪽 요소 선호")
    void testDirectionWeight_Below() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 100));

        // 요소: (X=100, Y=200) → 001 아래쪽 (선호)
        int elementX = 100;
        int elementY = 200;  // dy = 100 (아래)

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("001", result);
        // 실제 거리: 100px
        // 가중치 적용: 100 * 0.7 = 70px (임계값 500px 이내)
    }

    @Test
    @DisplayName("방향성 가중치: 위쪽 요소 비선호")
    void testDirectionWeight_Above() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 300));

        // 요소: (X=100, Y=200) → 001 위쪽 (비선호)
        int elementX = 100;
        int elementY = 200;  // dy = -100 (위)

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("001", result);
        // 실제 거리: 100px
        // 가중치 적용: 100 * 1.5 = 150px (임계값 500px 이내)
    }

    @Test
    @DisplayName("2단 레이아웃: 컬럼 무시하고 가장 가까운 문제 선택")
    void testTwoColumnLayout() {
        // Given: 2단 레이아웃
        List<QuestionBoundary> boundaries = new ArrayList<>();
        // 좌측 컬럼
        boundaries.add(createBoundary("001", 50, 100));
        boundaries.add(createBoundary("002", 50, 300));
        // 우측 컬럼
        boundaries.add(createBoundary("003", 400, 120));
        boundaries.add(createBoundary("004", 400, 320));

        // 요소: (X=420, Y=150) → 우측 컬럼, 003에 가장 가까움
        int elementX = 420;
        int elementY = 150;

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("003", result, "컬럼 구분 없이 003이 가장 가까우므로 할당되어야 함");
        
        // 거리 계산:
        // - 001: √[(420-50)² + (150-100)²] = √[136900 + 2500] = 373px
        // - 002: √[(420-50)² + (150-300)²] = √[136900 + 22500] = 399px
        // - 003: √[(420-400)² + (150-120)²] = √[400 + 900] = 36px ← 최소!
        // - 004: √[(420-400)² + (150-320)²] = √[400 + 28900] = 171px
    }

    @Test
    @DisplayName("혼합 레이아웃: 상단 1단 + 하단 2단")
    void testMixedLayout_TopSingle_BottomDouble() {
        // Given: 혼합 레이아웃
        List<QuestionBoundary> boundaries = new ArrayList<>();
        // 상단: 단일 컬럼 (중앙)
        boundaries.add(createBoundary("001", 300, 100));
        boundaries.add(createBoundary("002", 300, 200));
        // 하단: 2단
        boundaries.add(createBoundary("003", 50, 400));
        boundaries.add(createBoundary("004", 400, 420));

        // 테스트 1: 상단 요소 (X=310, Y=150) → 001에 가장 가까움
        assertEquals("001", analyzer.findNearestQuestion(310, 150, boundaries, false));

        // 테스트 2: 하단 좌측 요소 (X=60, Y=450) → 003에 가장 가까움
        assertEquals("003", analyzer.findNearestQuestion(60, 450, boundaries, false));

        // 테스트 3: 하단 우측 요소 (X=410, Y=470) → 004에 가장 가까움
        assertEquals("004", analyzer.findNearestQuestion(410, 470, boundaries, false));
    }

    @Test
    @DisplayName("적응형 임계값: 문제 적음 (대형 논술)")
    void testAdaptiveThreshold_FewQuestions() {
        // Given: 문제 2개 (대형 논술)
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 100));
        boundaries.add(createBoundary("002", 100, 800));

        // 요소: (X=100, Y=500)
        // - 001까지: dy = 500-100 = 400 (아래) → 400 × 0.7 = 280px ✅
        // - 002까지: dy = 500-800 = -300 (위) → 300 × 1.5 = 450px
        // → 최소 거리: 001 (280px)
        int elementX = 100;
        int elementY = 500;

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("001", result, "방향성 가중치 적용으로 001이 더 가까움 (280px < 450px)");
        // 문제 2개 → 임계값 500 * 1.2 = 600px (여유 있게)
        // 001까지 280px → 할당 성공
    }

    @Test
    @DisplayName("적응형 임계값: 문제 많음 (미니 테스트)")
    void testAdaptiveThreshold_ManyQuestions() {
        // Given: 문제 100개 (미니 테스트)
        List<QuestionBoundary> boundaries = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            boundaries.add(createBoundary(String.format("%03d", i), 100, i * 50));
        }

        // 요소: (X=500, Y=250) → 가장 가까운 문제까지 400px
        int elementX = 500;
        int elementY = 250;

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("unknown", result);
        // 문제 100개 → 임계값 500 * 0.8 = 400px (엄격하게)
        // 최소 거리 400px = 임계값 → 경계 케이스 (임계값 초과로 간주)
    }

    @Test
    @DisplayName("대형 요소: 확장 임계값 적용")
    void testLargeElement_ExtendedThreshold() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 100));

        // 요소: (X=700, Y=100) → 거리 600px (dy=0이므로 가중치 없음)
        int elementX = 700;
        int elementY = 100;

        // When
        String resultNormal = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);
        String resultLarge = analyzer.findNearestQuestion(elementX, elementY, boundaries, true);

        // Then
        // ✅ 문제 1개 (≤5) → 적응형 임계값 1.2배 적용
        // - 일반: 500 × 1.2 = 600px → 600px 거리는 경계선 (할당 성공)
        // - 대형: 800 × 1.2 = 960px → 600px < 960px (할당 성공)
        assertEquals("001", resultNormal, "일반 요소 임계값 600px = 600px → 할당 성공");
        assertEquals("001", resultLarge, "대형 요소 임계값 960px > 600px → 할당 성공");
    }

    @Test
    @DisplayName("예외 처리: 경계 없음")
    void testException_NoBoundaries() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();

        // When
        String result = analyzer.findNearestQuestion(100, 100, boundaries, false);

        // Then
        assertEquals("unknown", result);
    }

    @Test
    @DisplayName("예외 처리: null 경계")
    void testException_NullBoundaries() {
        // When
        String result = analyzer.findNearestQuestion(100, 100, null, false);

        // Then
        assertEquals("unknown", result);
    }

    @Test
    @DisplayName("예외 처리: 거리 임계값 초과")
    void testException_ExceedThreshold() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", 100, 100));

        // 요소: (X=1000, Y=100) → 거리 900px > 임계값 500px
        int elementX = 1000;
        int elementY = 100;

        // When
        String result = analyzer.findNearestQuestion(elementX, elementY, boundaries, false);

        // Then
        assertEquals("unknown", result, "거리 임계값 초과로 할당 실패해야 함");
    }

    @Test
    @DisplayName("isLargeElement: 면적 기반 판단")
    void testIsLargeElement_ByArea() {
        // Given
        int width1 = 800;
        int height1 = 750;  // area = 600,000 (경계값)

        int width2 = 500;
        int height2 = 500;  // area = 250,000 (작음)

        // When
        boolean isLarge1 = analyzer.isLargeElement(width1, height1);
        boolean isLarge2 = analyzer.isLargeElement(width2, height2);

        // Then
        assertTrue(isLarge1, "600,000 이상은 대형 요소");
        assertFalse(isLarge2, "600,000 미만은 일반 요소");
    }

    @Test
    @DisplayName("isLargeElement: 타입 기반 판단")
    void testIsLargeElement_ByType() {
        // Given
        String[] largeTypes = {"figure", "table", "equation"};
        String[] normalTypes = {"text", "choice", "title"};

        // When & Then
        for (String type : largeTypes) {
            assertTrue(analyzer.isLargeElement(type), type + "는 대형 요소");
        }
        for (String type : normalTypes) {
            assertFalse(analyzer.isLargeElement(type), type + "는 일반 요소");
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private QuestionBoundary createBoundary(String identifier, int x, int y) {
        return new QuestionBoundary(
            identifier,               // identifier
            BoundaryType.QUESTION_NUMBER,  // type
            x,                        // x
            y,                        // y
            50,                       // width
            50,                       // height
            identifier,               // ocrText
            0.9,                      // lamConfidence
            0                         // elementId
        );
    }
}
