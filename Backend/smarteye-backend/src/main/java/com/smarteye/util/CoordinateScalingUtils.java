package com.smarteye.util;

import com.smarteye.presentation.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 좌표 스케일링 유틸리티 클래스
 * LAM 서비스와 백엔드 간의 이미지 크기 불일치를 처리
 */
public class CoordinateScalingUtils {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateScalingUtils.class);

    /**
     * 스케일링 정보를 담는 클래스
     */
    public static class ScalingInfo {
        private final double scaleX;
        private final double scaleY;
        private final boolean needsScaling;

        public ScalingInfo(double scaleX, double scaleY, boolean needsScaling) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.needsScaling = needsScaling;
        }

        public double getScaleX() { return scaleX; }
        public double getScaleY() { return scaleY; }
        public boolean needsScaling() { return needsScaling; }

        @Override
        public String toString() {
            return String.format("ScalingInfo{scaleX=%.3f, scaleY=%.3f, needsScaling=%s}",
                               scaleX, scaleY, needsScaling);
        }
    }

    /**
     * 스케일링 정보 계산
     * @param originalWidth 원본 이미지 너비
     * @param originalHeight 원본 이미지 높이
     * @param processedWidth 처리된 이미지 너비
     * @param processedHeight 처리된 이미지 높이
     * @return 스케일링 정보
     */
    public static ScalingInfo calculateScaling(int originalWidth, int originalHeight,
                                             int processedWidth, int processedHeight) {
        boolean needsScaling = (originalWidth != processedWidth || originalHeight != processedHeight) &&
                              originalWidth > 0 && originalHeight > 0 &&
                              processedWidth > 0 && processedHeight > 0;

        double scaleX = needsScaling ? (double) originalWidth / processedWidth : 1.0;
        double scaleY = needsScaling ? (double) originalHeight / processedHeight : 1.0;

        ScalingInfo info = new ScalingInfo(scaleX, scaleY, needsScaling);

        if (needsScaling) {
            logger.debug("좌표 스케일링 정보 계산: 원본 {}x{} → 처리됨 {}x{}, 스케일 {:.3f}x{:.3f}",
                        originalWidth, originalHeight, processedWidth, processedHeight, scaleX, scaleY);
        }

        return info;
    }

    /**
     * 좌표 배열 스케일링
     * @param coords 원본 좌표 [x1, y1, x2, y2]
     * @param scalingInfo 스케일링 정보
     * @return 스케일링된 좌표
     */
    public static int[] scaleCoordinates(int[] coords, ScalingInfo scalingInfo) {
        if (!scalingInfo.needsScaling() || coords == null || coords.length < 4) {
            return coords;
        }

        int[] scaledCoords = new int[4];
        scaledCoords[0] = (int) Math.round(coords[0] * scalingInfo.getScaleX()); // x1
        scaledCoords[1] = (int) Math.round(coords[1] * scalingInfo.getScaleY()); // y1
        scaledCoords[2] = (int) Math.round(coords[2] * scalingInfo.getScaleX()); // x2
        scaledCoords[3] = (int) Math.round(coords[3] * scalingInfo.getScaleY()); // y2

        logger.debug("좌표 스케일링: [{}, {}, {}, {}] → [{}, {}, {}, {}]",
                    coords[0], coords[1], coords[2], coords[3],
                    scaledCoords[0], scaledCoords[1], scaledCoords[2], scaledCoords[3]);

        return scaledCoords;
    }

    /**
     * 레이아웃 정보 목록의 좌표 스케일링
     * @param layoutInfoList 원본 레이아웃 정보 목록
     * @param scalingInfo 스케일링 정보
     * @return 스케일링된 레이아웃 정보 목록 (새 객체)
     */
    public static List<LayoutInfo> scaleLayoutInfo(List<LayoutInfo> layoutInfoList, ScalingInfo scalingInfo) {
        if (!scalingInfo.needsScaling() || layoutInfoList == null) {
            return layoutInfoList;
        }

        logger.info("레이아웃 정보 좌표 스케일링 시작: {}개 요소, 스케일 {:.3f}x{:.3f}",
                   layoutInfoList.size(), scalingInfo.getScaleX(), scalingInfo.getScaleY());

        return layoutInfoList.stream()
            .map(original -> {
                int[] scaledBox = scaleCoordinates(original.getBox(), scalingInfo);

                // 새로운 LayoutInfo 객체 생성 (원본은 수정하지 않음)
                return new LayoutInfo(
                    original.getId(),
                    original.getClassName(),
                    original.getConfidence(),
                    scaledBox,
                    scaledBox[2] - scaledBox[0], // width
                    scaledBox[3] - scaledBox[1], // height
                    (scaledBox[2] - scaledBox[0]) * (scaledBox[3] - scaledBox[1]) // area
                );
            })
            .toList();
    }

    /**
     * 좌표 유효성 검증
     * @param coords 검증할 좌표 [x1, y1, x2, y2]
     * @param imageWidth 이미지 너비
     * @param imageHeight 이미지 높이
     * @return 유효성 검증 결과
     */
    public static ValidationResult validateCoordinates(int[] coords, int imageWidth, int imageHeight) {
        if (coords == null || coords.length < 4) {
            return new ValidationResult(false, "좌표 배열이 null이거나 길이가 4 미만입니다");
        }

        int x1 = coords[0], y1 = coords[1], x2 = coords[2], y2 = coords[3];

        // 좌표 순서 검증
        if (x1 >= x2 || y1 >= y2) {
            return new ValidationResult(false,
                String.format("좌표 순서 무효: x1=%d, y1=%d, x2=%d, y2=%d (기대: x1<x2, y1<y2)",
                             x1, y1, x2, y2));
        }

        // 범위 검증
        if (x1 < 0 || y1 < 0 || x2 > imageWidth || y2 > imageHeight) {
            return new ValidationResult(false,
                String.format("좌표 범위 초과: [%d, %d, %d, %d] vs 이미지 [0, 0, %d, %d]",
                             x1, y1, x2, y2, imageWidth, imageHeight));
        }

        // 최소 크기 검증 (너무 작은 바운딩 박스 필터링)
        int minWidth = 5;
        int minHeight = 5;
        if ((x2 - x1) < minWidth || (y2 - y1) < minHeight) {
            return new ValidationResult(false,
                String.format("바운딩 박스가 너무 작음: 크기 %dx%d (최소: %dx%d)",
                             x2 - x1, y2 - y1, minWidth, minHeight));
        }

        // 최대 크기 검증 (비현실적으로 큰 바운딩 박스 필터링)
        double maxAreaRatio = 0.95; // 이미지의 95% 이상을 차지하면 의심스러움
        int boxArea = (x2 - x1) * (y2 - y1);
        int imageArea = imageWidth * imageHeight;
        if (boxArea > imageArea * maxAreaRatio) {
            return new ValidationResult(false,
                String.format("바운딩 박스가 너무 큼: %d (이미지 면적의 %.1f%%)",
                             boxArea, (double) boxArea / imageArea * 100));
        }

        return new ValidationResult(true, "좌표 유효함");
    }

    /**
     * 좌표 자동 보정 (이미지 범위 내로 제한)
     * @param coords 원본 좌표 [x1, y1, x2, y2]
     * @param imageWidth 이미지 너비
     * @param imageHeight 이미지 높이
     * @return 보정된 좌표
     */
    public static int[] clampCoordinates(int[] coords, int imageWidth, int imageHeight) {
        if (coords == null || coords.length < 4) {
            return coords;
        }

        int[] clampedCoords = new int[4];
        clampedCoords[0] = Math.max(0, Math.min(coords[0], imageWidth - 1));  // x1
        clampedCoords[1] = Math.max(0, Math.min(coords[1], imageHeight - 1)); // y1
        clampedCoords[2] = Math.max(clampedCoords[0] + 1, Math.min(coords[2], imageWidth));  // x2
        clampedCoords[3] = Math.max(clampedCoords[1] + 1, Math.min(coords[3], imageHeight)); // y2

        boolean wasModified = !java.util.Arrays.equals(coords, clampedCoords);
        if (wasModified) {
            logger.debug("좌표 자동 보정: [{}, {}, {}, {}] → [{}, {}, {}, {}]",
                        coords[0], coords[1], coords[2], coords[3],
                        clampedCoords[0], clampedCoords[1], clampedCoords[2], clampedCoords[3]);
        }

        return clampedCoords;
    }

    /**
     * 검증 결과를 담는 클래스
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, message='%s'}", valid, message);
        }
    }
}