package com.smarteye.application.analysis;

import com.smarteye.application.analysis.dto.BoundaryType;
import com.smarteye.application.analysis.dto.QuestionBoundary;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuestionBoundaryDetector 단위 테스트
 * 
 * Phase 1-2 구현 검증:
 * - QuestionBoundary 데이터 구조
 * - extractBoundaries() 기본 동작
 * - LAM 클래스명 표준화 (띄어쓰기 형식)
 * 
 * @version 1.0
 * @since 2025-10-20
 */
class QuestionBoundaryDetectorTest {

    private QuestionBoundaryDetector detector;

    @BeforeEach
    void setUp() {
        detector = new QuestionBoundaryDetector();
    }

    @Test
    @DisplayName("기본 테스트: question number 추출")
    void testExtractQuestionNumbers() {
        // Given
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // question number 3개
        layoutElements.add(createLayoutInfo(1, "question number", 0.95, new int[]{100, 200, 150, 250}));
        layoutElements.add(createLayoutInfo(2, "question number", 0.92, new int[]{100, 400, 150, 450}));
        layoutElements.add(createLayoutInfo(3, "question number", 0.88, new int[]{100, 600, 150, 650}));

        ocrResults.add(createOCRResult(1, "question number", new int[]{100, 200, 150, 250}, "001"));
        ocrResults.add(createOCRResult(2, "question number", new int[]{100, 400, 150, 450}, "002"));
        ocrResults.add(createOCRResult(3, "question number", new int[]{100, 600, 150, 650}, "003"));

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then
        assertEquals(3, boundaries.size(), "3개의 경계가 추출되어야 함");
        
        // 첫 번째 경계 검증
        QuestionBoundary first = boundaries.get(0);
        assertEquals("001", first.getIdentifier());
        assertEquals(BoundaryType.QUESTION_NUMBER, first.getType());
        assertEquals(100, first.getX());
        assertEquals(200, first.getY());
        assertEquals(50, first.getWidth());
        assertEquals(50, first.getHeight());
        assertEquals("001", first.getOcrText());
        assertEquals(0.95, first.getLamConfidence(), 0.01);
        
        // Y좌표 정렬 검증
        assertTrue(boundaries.get(0).getY() < boundaries.get(1).getY());
        assertTrue(boundaries.get(1).getY() < boundaries.get(2).getY());
    }

    @Test
    @DisplayName("question type 추출 테스트")
    void testExtractQuestionTypes() {
        // Given
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        layoutElements.add(createLayoutInfo(1, "question type", 0.90, new int[]{100, 100, 200, 150}));
        layoutElements.add(createLayoutInfo(2, "question type", 0.85, new int[]{100, 300, 200, 350}));

        ocrResults.add(createOCRResult(1, "question type", new int[]{100, 100, 200, 150}, "유형 01"));
        ocrResults.add(createOCRResult(2, "question type", new int[]{100, 300, 200, 350}, "유형 02"));

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then
        assertEquals(2, boundaries.size());
        assertEquals(BoundaryType.QUESTION_TYPE, boundaries.get(0).getType());
        assertEquals("유형 01", boundaries.get(0).getIdentifier());
        assertEquals("유형 02", boundaries.get(1).getIdentifier());
    }

    @Test
    @DisplayName("혼합 타입 추출 테스트: question number + question type")
    void testExtractMixedTypes() {
        // Given
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // question number
        layoutElements.add(createLayoutInfo(1, "question number", 0.95, new int[]{100, 200, 150, 250}));
        ocrResults.add(createOCRResult(1, "question number", new int[]{100, 200, 150, 250}, "001"));

        // question type
        layoutElements.add(createLayoutInfo(2, "question type", 0.90, new int[]{100, 100, 200, 150}));
        ocrResults.add(createOCRResult(2, "question type", new int[]{100, 100, 200, 150}, "유형 A"));

        // question number
        layoutElements.add(createLayoutInfo(3, "question number", 0.92, new int[]{100, 400, 150, 450}));
        ocrResults.add(createOCRResult(3, "question number", new int[]{100, 400, 150, 450}, "002"));

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then
        assertEquals(3, boundaries.size());
        
        // Y좌표로 정렬되어야 함: 100(유형A) -> 200(001) -> 400(002)
        assertEquals("유형 A", boundaries.get(0).getIdentifier());
        assertEquals(BoundaryType.QUESTION_TYPE, boundaries.get(0).getType());
        assertEquals("001", boundaries.get(1).getIdentifier());
        assertEquals(BoundaryType.QUESTION_NUMBER, boundaries.get(1).getType());
        assertEquals("002", boundaries.get(2).getIdentifier());
    }

    @Test
    @DisplayName("언더스코어 형식 거부 테스트 (LAM 표준화)")
    void testRejectUnderscoreFormat() {
        // Given: 잘못된 언더스코어 형식
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        layoutElements.add(createLayoutInfo(1, "question_number", 0.95, new int[]{100, 200, 150, 250}));
        layoutElements.add(createLayoutInfo(2, "question_type", 0.90, new int[]{100, 100, 200, 150}));

        ocrResults.add(createOCRResult(1, "question_number", new int[]{100, 200, 150, 250}, "001"));
        ocrResults.add(createOCRResult(2, "question_type", new int[]{100, 100, 200, 150}, "유형 A"));

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then: 언더스코어 형식은 무시되어야 함
        assertEquals(0, boundaries.size(), "언더스코어 형식은 추출되지 않아야 함");
    }

    @Test
    @DisplayName("OCR 텍스트 그대로 사용 (정제 없음)")
    void testOCRTextNoCleanup() {
        // Given
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // 특수 문자, 공백 포함된 OCR 텍스트
        layoutElements.add(createLayoutInfo(1, "question number", 0.95, new int[]{100, 200, 150, 250}));
        ocrResults.add(createOCRResult(1, "question number", new int[]{100, 200, 150, 250}, "  294...  "));

        layoutElements.add(createLayoutInfo(2, "question number", 0.90, new int[]{100, 400, 150, 450}));
        ocrResults.add(createOCRResult(2, "question number", new int[]{100, 400, 150, 450}, "★001번"));

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then: OCR 텍스트가 trim만 되고 그대로 사용되어야 함
        assertEquals(2, boundaries.size());
        assertEquals("294...", boundaries.get(0).getIdentifier());
        assertEquals("  294...  ", boundaries.get(0).getOcrText());
        assertEquals("★001번", boundaries.get(1).getIdentifier());
        assertEquals("★001번", boundaries.get(1).getOcrText());
    }

    @Test
    @DisplayName("빈 결과 처리")
    void testEmptyResults() {
        // Given
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // When
        List<QuestionBoundary> boundaries = detector.extractBoundaries(layoutElements, ocrResults);

        // Then
        assertNotNull(boundaries);
        assertEquals(0, boundaries.size());
    }

    @Test
    @DisplayName("filterByType 유틸리티 메서드 테스트")
    void testFilterByType() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", BoundaryType.QUESTION_NUMBER, 100, 200));
        boundaries.add(createBoundary("유형 A", BoundaryType.QUESTION_TYPE, 100, 100));
        boundaries.add(createBoundary("002", BoundaryType.QUESTION_NUMBER, 100, 400));

        // When
        List<QuestionBoundary> numberBoundaries = detector.filterByType(boundaries, BoundaryType.QUESTION_NUMBER);
        List<QuestionBoundary> typeBoundaries = detector.filterByType(boundaries, BoundaryType.QUESTION_TYPE);

        // Then
        assertEquals(2, numberBoundaries.size());
        assertEquals(1, typeBoundaries.size());
        assertEquals("유형 A", typeBoundaries.get(0).getIdentifier());
    }

    @Test
    @DisplayName("findByIdentifier 유틸리티 메서드 테스트")
    void testFindByIdentifier() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", BoundaryType.QUESTION_NUMBER, 100, 200));
        boundaries.add(createBoundary("002", BoundaryType.QUESTION_NUMBER, 100, 400));
        boundaries.add(createBoundary("003", BoundaryType.QUESTION_NUMBER, 100, 600));

        // When
        QuestionBoundary found = detector.findByIdentifier(boundaries, "002");
        QuestionBoundary notFound = detector.findByIdentifier(boundaries, "999");

        // Then
        assertNotNull(found);
        assertEquals("002", found.getIdentifier());
        assertEquals(400, found.getY());
        assertNull(notFound);
    }

    @Test
    @DisplayName("findInYRange 유틸리티 메서드 테스트")
    void testFindInYRange() {
        // Given
        List<QuestionBoundary> boundaries = new ArrayList<>();
        boundaries.add(createBoundary("001", BoundaryType.QUESTION_NUMBER, 100, 100));
        boundaries.add(createBoundary("002", BoundaryType.QUESTION_NUMBER, 100, 300));
        boundaries.add(createBoundary("003", BoundaryType.QUESTION_NUMBER, 100, 500));
        boundaries.add(createBoundary("004", BoundaryType.QUESTION_NUMBER, 100, 700));

        // When
        List<QuestionBoundary> inRange = detector.findInYRange(boundaries, 200, 600);

        // Then
        assertEquals(2, inRange.size());
        assertEquals("002", inRange.get(0).getIdentifier());
        assertEquals("003", inRange.get(1).getIdentifier());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private LayoutInfo createLayoutInfo(int id, String className, double confidence, int[] box) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setConfidence(confidence);
        layout.setBox(box);
        layout.setWidth(box[2] - box[0]);
        layout.setHeight(box[3] - box[1]);
        layout.setArea((box[2] - box[0]) * (box[3] - box[1]));
        return layout;
    }

    private OCRResult createOCRResult(int id, String className, int[] coordinates, String text) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setClassName(className);
        ocr.setCoordinates(coordinates);
        ocr.setText(text);
        ocr.setConfidence(0.9);
        return ocr;
    }

    private QuestionBoundary createBoundary(String identifier, BoundaryType type, int x, int y) {
        return new QuestionBoundary(identifier, type, x, y, 50, 50, identifier, 0.9, 0);
    }
}
