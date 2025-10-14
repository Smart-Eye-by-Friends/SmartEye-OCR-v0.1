package com.smarteye.application.analysis.engine.validation;

/**
 * Bounding Box 유틸리티 클래스
 *
 * 공간 범위 검증을 위한 경계 박스 계산 및 중첩 검사 기능 제공
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
public class BoundingBox {

    public static final BoundingBox EMPTY = new BoundingBox(0, 0, 0, 0);

    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    /**
     * Bounding Box 생성자
     *
     * @param x1 좌측 상단 X 좌표
     * @param y1 좌측 상단 Y 좌표
     * @param x2 우측 하단 X 좌표
     * @param y2 우측 하단 Y 좌표
     */
    public BoundingBox(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /**
     * int[] 배열로부터 BoundingBox 생성
     *
     * @param box [x1, y1, x2, y2] 형식의 배열
     * @return BoundingBox 인스턴스
     */
    public static BoundingBox fromArray(int[] box) {
        if (box == null || box.length != 4) {
            return EMPTY;
        }
        return new BoundingBox(box[0], box[1], box[2], box[3]);
    }

    // Getters
    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    /**
     * 너비 계산
     */
    public double getWidth() {
        return x2 - x1;
    }

    /**
     * 높이 계산
     */
    public double getHeight() {
        return y2 - y1;
    }

    /**
     * 면적 계산
     */
    public double getArea() {
        return getWidth() * getHeight();
    }

    /**
     * 중심점 X 좌표
     */
    public double getCenterX() {
        return (x1 + x2) / 2.0;
    }

    /**
     * 중심점 Y 좌표
     */
    public double getCenterY() {
        return (y1 + y2) / 2.0;
    }

    /**
     * 다른 BoundingBox와의 중첩 여부 확인
     *
     * @param other 비교 대상
     * @return true if 중첩됨
     */
    public boolean overlaps(BoundingBox other) {
        if (other == null) return false;

        // 중첩 조건: X축과 Y축 모두 겹침
        boolean xOverlap = this.x1 < other.x2 && this.x2 > other.x1;
        boolean yOverlap = this.y1 < other.y2 && this.y2 > other.y1;

        return xOverlap && yOverlap;
    }

    /**
     * 중첩 영역 면적 계산
     *
     * @param other 비교 대상
     * @return 중첩 면적 (px²)
     */
    public double getOverlapArea(BoundingBox other) {
        if (!overlaps(other)) {
            return 0.0;
        }

        double overlapX1 = Math.max(this.x1, other.x1);
        double overlapY1 = Math.max(this.y1, other.y1);
        double overlapX2 = Math.min(this.x2, other.x2);
        double overlapY2 = Math.min(this.y2, other.y2);

        double overlapWidth = overlapX2 - overlapX1;
        double overlapHeight = overlapY2 - overlapY1;

        return overlapWidth * overlapHeight;
    }

    /**
     * IoU (Intersection over Union) 계산
     *
     * @param other 비교 대상
     * @return IoU 값 (0.0 ~ 1.0)
     */
    public double iou(BoundingBox other) {
        if (other == null) return 0.0;

        double intersection = getOverlapArea(other);
        if (intersection == 0.0) return 0.0;

        double union = this.getArea() + other.getArea() - intersection;
        return intersection / union;
    }

    /**
     * 다른 BoundingBox를 포함하는지 확인
     *
     * @param point [x, y] 형식의 좌표
     * @return true if 포함됨
     */
    public boolean contains(double[] point) {
        if (point == null || point.length != 2) return false;
        return point[0] >= x1 && point[0] <= x2 &&
               point[1] >= y1 && point[1] <= y2;
    }

    /**
     * 다른 BoundingBox를 포함하는지 확인
     *
     * @param other 비교 대상
     * @return true if 완전히 포함됨
     */
    public boolean contains(BoundingBox other) {
        if (other == null) return false;
        return this.x1 <= other.x1 && this.y1 <= other.y1 &&
               this.x2 >= other.x2 && this.y2 >= other.y2;
    }

    /**
     * 두 BoundingBox의 통합 범위 계산
     *
     * @param other 통합할 대상
     * @return 통합된 BoundingBox
     */
    public BoundingBox union(BoundingBox other) {
        if (other == null) return this;

        double minX1 = Math.min(this.x1, other.x1);
        double minY1 = Math.min(this.y1, other.y1);
        double maxX2 = Math.max(this.x2, other.x2);
        double maxY2 = Math.max(this.y2, other.y2);

        return new BoundingBox(minX1, minY1, maxX2, maxY2);
    }

    @Override
    public String toString() {
        return String.format("BBox[x1=%.0f, y1=%.0f, x2=%.0f, y2=%.0f, area=%.0f]",
                x1, y1, x2, y2, getArea());
    }
}
