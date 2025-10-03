package com.smarteye.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CoordinateScalingUtils 테스트
 */
class CoordinateScalingUtilsTest {

    @Test
    @DisplayName("좌표 스케일링 정보 계산 - 스케일링 필요")
    void testCalculateScaling_NeedsScaling() {
        // Given
        int originalWidth = 1000, originalHeight = 800;
        int processedWidth = 500, processedHeight = 400;

        // When
        CoordinateScalingUtils.ScalingInfo result =
            CoordinateScalingUtils.calculateScaling(originalWidth, originalHeight, processedWidth, processedHeight);

        // Then
        assertTrue(result.needsScaling());
        assertEquals(2.0, result.getScaleX(), 0.001);
        assertEquals(2.0, result.getScaleY(), 0.001);
    }

    @Test
    @DisplayName("좌표 스케일링 정보 계산 - 스케일링 불필요")
    void testCalculateScaling_NoScalingNeeded() {
        // Given
        int originalWidth = 1000, originalHeight = 800;
        int processedWidth = 1000, processedHeight = 800;

        // When
        CoordinateScalingUtils.ScalingInfo result =
            CoordinateScalingUtils.calculateScaling(originalWidth, originalHeight, processedWidth, processedHeight);

        // Then
        assertFalse(result.needsScaling());
        assertEquals(1.0, result.getScaleX(), 0.001);
        assertEquals(1.0, result.getScaleY(), 0.001);
    }

    @Test
    @DisplayName("좌표 스케일링 적용")
    void testScaleCoordinates() {
        // Given
        int[] originalCoords = {10, 20, 50, 80};
        CoordinateScalingUtils.ScalingInfo scalingInfo =
            new CoordinateScalingUtils.ScalingInfo(2.0, 1.5, true);

        // When
        int[] result = CoordinateScalingUtils.scaleCoordinates(originalCoords, scalingInfo);

        // Then
        assertArrayEquals(new int[]{20, 30, 100, 120}, result);
    }

    @Test
    @DisplayName("좌표 스케일링 적용 - 스케일링 불필요")
    void testScaleCoordinates_NoScaling() {
        // Given
        int[] originalCoords = {10, 20, 50, 80};
        CoordinateScalingUtils.ScalingInfo scalingInfo =
            new CoordinateScalingUtils.ScalingInfo(1.0, 1.0, false);

        // When
        int[] result = CoordinateScalingUtils.scaleCoordinates(originalCoords, scalingInfo);

        // Then
        assertArrayEquals(originalCoords, result);
    }

    @Test
    @DisplayName("좌표 유효성 검증 - 유효한 좌표")
    void testValidateCoordinates_Valid() {
        // Given
        int[] coords = {10, 20, 50, 80};
        int imageWidth = 100, imageHeight = 100;

        // When
        CoordinateScalingUtils.ValidationResult result =
            CoordinateScalingUtils.validateCoordinates(coords, imageWidth, imageHeight);

        // Then
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("좌표 유효성 검증 - 범위 초과")
    void testValidateCoordinates_OutOfBounds() {
        // Given
        int[] coords = {10, 20, 150, 80}; // x2가 이미지 너비 초과
        int imageWidth = 100, imageHeight = 100;

        // When
        CoordinateScalingUtils.ValidationResult result =
            CoordinateScalingUtils.validateCoordinates(coords, imageWidth, imageHeight);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("좌표 범위 초과"));
    }

    @Test
    @DisplayName("좌표 유효성 검증 - 잘못된 순서")
    void testValidateCoordinates_InvalidOrder() {
        // Given
        int[] coords = {50, 20, 10, 80}; // x1 > x2
        int imageWidth = 100, imageHeight = 100;

        // When
        CoordinateScalingUtils.ValidationResult result =
            CoordinateScalingUtils.validateCoordinates(coords, imageWidth, imageHeight);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("좌표 순서 무효"));
    }

    @Test
    @DisplayName("좌표 자동 보정")
    void testClampCoordinates() {
        // Given
        int[] coords = {-10, 5, 150, 80}; // x1이 음수, x2가 범위 초과
        int imageWidth = 100, imageHeight = 100;

        // When
        int[] result = CoordinateScalingUtils.clampCoordinates(coords, imageWidth, imageHeight);

        // Then
        assertEquals(0, result[0]);    // x1이 0으로 보정
        assertEquals(5, result[1]);    // y1은 유지
        assertEquals(100, result[2]);  // x2가 imageWidth로 보정
        assertEquals(80, result[3]);   // y2는 유지
    }

    @Test
    @DisplayName("좌표 자동 보정 - null 입력")
    void testClampCoordinates_NullInput() {
        // Given
        int[] coords = null;
        int imageWidth = 100, imageHeight = 100;

        // When
        int[] result = CoordinateScalingUtils.clampCoordinates(coords, imageWidth, imageHeight);

        // Then
        assertNull(result);
    }
}