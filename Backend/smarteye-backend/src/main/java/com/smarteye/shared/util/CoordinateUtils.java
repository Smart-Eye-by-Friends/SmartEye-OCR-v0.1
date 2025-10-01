package com.smarteye.shared.util;

import com.smarteye.presentation.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * 좌표계 변환 및 검증 유틸리티
 * LAM 서비스와 백엔드 간의 좌표계 불일치 문제 해결
 */
public class CoordinateUtils {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateUtils.class);

    /**
     * 좌표 스케일링 적용
     * @param layoutInfoList 레이아웃 정보 리스트
     * @param scaleX X축 스케일 비율
     * @param scaleY Y축 스케일 비율
     * @return 스케일링이 적용된 새로운 레이아웃 정보 리스트
     */
    public static List<LayoutInfo> scaleCoordinates(List<LayoutInfo> layoutInfoList, double scaleX, double scaleY) {
        if (layoutInfoList == null || layoutInfoList.isEmpty()) {
            return layoutInfoList;
        }

        if (Math.abs(scaleX - 1.0) < 0.001 && Math.abs(scaleY - 1.0) < 0.001) {
            logger.debug("스케일링 불필요 - 비율이 1.0에 가까움: {:.3f}x{:.3f}", scaleX, scaleY);
            return layoutInfoList;
        }

        logger.info("좌표 스케일링 시작 - 요소 {}개, 스케일 {:.3f}x{:.3f}",
                   layoutInfoList.size(), scaleX, scaleY);

        List<LayoutInfo> scaledList = new ArrayList<>();

        for (LayoutInfo original : layoutInfoList) {
            int[] originalBox = original.getBox();
            if (originalBox == null || originalBox.length < 4) {
                scaledList.add(original);
                continue;
            }

            // 좌표 스케일링 적용
            int[] scaledBox = {
                (int) Math.round(originalBox[0] * scaleX),  // x1
                (int) Math.round(originalBox[1] * scaleY),  // y1
                (int) Math.round(originalBox[2] * scaleX),  // x2
                (int) Math.round(originalBox[3] * scaleY)   // y2
            };

            // 새로운 크기 및 면적 계산
            int scaledWidth = scaledBox[2] - scaledBox[0];
            int scaledHeight = scaledBox[3] - scaledBox[1];
            int scaledArea = scaledWidth * scaledHeight;

            LayoutInfo scaledInfo = new LayoutInfo(
                original.getId(),
                original.getClassName(),
                original.getConfidence(),
                scaledBox,
                scaledWidth,
                scaledHeight,
                scaledArea
            );

            scaledList.add(scaledInfo);

            logger.debug("요소 {} 스케일링: [{}, {}, {}, {}] -> [{}, {}, {}, {}]",
                        original.getClassName(),
                        originalBox[0], originalBox[1], originalBox[2], originalBox[3],
                        scaledBox[0], scaledBox[1], scaledBox[2], scaledBox[3]);
        }

        logger.info("좌표 스케일링 완료 - {}개 요소 처리", scaledList.size());
        return scaledList;
    }

    /**
     * 좌표가 이미지 범위 내에 있는지 검증
     * @param layoutInfo 레이아웃 정보
     * @param imageWidth 이미지 너비
     * @param imageHeight 이미지 높이
     * @return 유효한 좌표 여부
     */
    public static boolean isCoordinateValid(LayoutInfo layoutInfo, int imageWidth, int imageHeight) {
        if (layoutInfo == null || layoutInfo.getBox() == null || layoutInfo.getBox().length < 4) {
            return false;
        }

        int[] box = layoutInfo.getBox();
        int x1 = box[0], y1 = box[1], x2 = box[2], y2 = box[3];

        // 좌표 순서 검증
        if (x1 >= x2 || y1 >= y2) {
            return false;
        }

        // 이미지 범위 내 검증
        return x1 >= 0 && y1 >= 0 && x2 <= imageWidth && y2 <= imageHeight;
    }

    /**
     * 좌표를 이미지 범위 내로 제한
     * @param layoutInfo 레이아웃 정보
     * @param imageWidth 이미지 너비
     * @param imageHeight 이미지 높이
     * @return 보정된 레이아웃 정보 (원본 수정 없이 새 객체 반환)
     */
    public static LayoutInfo clampCoordinates(LayoutInfo layoutInfo, int imageWidth, int imageHeight) {
        if (layoutInfo == null || layoutInfo.getBox() == null || layoutInfo.getBox().length < 4) {
            return layoutInfo;
        }

        int[] originalBox = layoutInfo.getBox();
        int x1 = Math.max(0, Math.min(originalBox[0], imageWidth - 1));
        int y1 = Math.max(0, Math.min(originalBox[1], imageHeight - 1));
        int x2 = Math.max(x1 + 1, Math.min(originalBox[2], imageWidth));
        int y2 = Math.max(y1 + 1, Math.min(originalBox[3], imageHeight));

        int[] clampedBox = {x1, y1, x2, y2};
        int clampedWidth = x2 - x1;
        int clampedHeight = y2 - y1;
        int clampedArea = clampedWidth * clampedHeight;

        return new LayoutInfo(
            layoutInfo.getId(),
            layoutInfo.getClassName(),
            layoutInfo.getConfidence(),
            clampedBox,
            clampedWidth,
            clampedHeight,
            clampedArea
        );
    }

    /**
     * 좌표 검증 및 통계 정보 생성
     * @param layoutInfoList 레이아웃 정보 리스트
     * @param imageWidth 이미지 너비
     * @param imageHeight 이미지 높이
     * @return 검증 결과 통계
     */
    public static CoordinateValidationResult validateCoordinates(List<LayoutInfo> layoutInfoList,
                                                               int imageWidth, int imageHeight) {
        CoordinateValidationResult result = new CoordinateValidationResult();

        if (layoutInfoList == null || layoutInfoList.isEmpty()) {
            return result;
        }

        for (LayoutInfo layoutInfo : layoutInfoList) {
            result.totalCount++;

            if (layoutInfo == null || layoutInfo.getBox() == null || layoutInfo.getBox().length < 4) {
                result.invalidBoxCount++;
                continue;
            }

            int[] box = layoutInfo.getBox();
            int x1 = box[0], y1 = box[1], x2 = box[2], y2 = box[3];

            // 좌표 순서 검증
            if (x1 >= x2 || y1 >= y2) {
                result.invalidOrderCount++;
                continue;
            }

            // 이미지 범위 검증
            if (x1 < 0 || y1 < 0 || x2 > imageWidth || y2 > imageHeight) {
                result.outOfBoundsCount++;
            } else {
                result.validCount++;
            }
        }

        logger.debug("좌표 검증 완료 - 총 {}, 유효 {}, 무효박스 {}, 순서오류 {}, 범위초과 {}",
                    result.totalCount, result.validCount, result.invalidBoxCount,
                    result.invalidOrderCount, result.outOfBoundsCount);

        return result;
    }

    /**
     * 좌표 검증 결과 클래스
     */
    public static class CoordinateValidationResult {
        public int totalCount = 0;
        public int validCount = 0;
        public int invalidBoxCount = 0;
        public int invalidOrderCount = 0;
        public int outOfBoundsCount = 0;

        public boolean hasIssues() {
            return invalidBoxCount > 0 || invalidOrderCount > 0 || outOfBoundsCount > 0;
        }

        public double getValidPercentage() {
            return totalCount > 0 ? (double) validCount / totalCount * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("CoordinateValidation{total=%d, valid=%d(%.1f%%), invalid=%d, order=%d, bounds=%d}",
                    totalCount, validCount, getValidPercentage(), invalidBoxCount, invalidOrderCount, outOfBoundsCount);
        }
    }
}