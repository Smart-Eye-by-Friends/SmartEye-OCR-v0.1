// src/utils/coordinateScaler.ts

/**
 * 좌표 스케일링 유틸리티 클래스
 * 원본 이미지 좌표를 표시 영역 좌표로 변환
 */
export class CoordinateScaler {
  private scaleX: number;
  private scaleY: number;

  constructor(
    originalWidth: number,
    originalHeight: number,
    displayWidth: number,
    displayHeight: number
  ) {
    this.scaleX = displayWidth / originalWidth;
    this.scaleY = displayHeight / originalHeight;
  }

  /**
   * 단일 좌표 객체 스케일링
   */
  scale(coordinates: { x: number; y: number; width: number; height: number }): {
    x: number;
    y: number;
    width: number;
    height: number;
  } {
    return {
      x: coordinates.x * this.scaleX,
      y: coordinates.y * this.scaleY,
      width: coordinates.width * this.scaleX,
      height: coordinates.height * this.scaleY,
    };
  }

  /**
   * 바운딩 박스 배열 스케일링
   */
  scaleAll(bboxes: any[]): any[] {
    return bboxes.map((bbox) => ({
      ...bbox,
      coordinates: this.scale(bbox.coordinates),
    }));
  }

  /**
   * 스케일에 따른 적절한 stroke width 계산
   */
  getStrokeWidth(baseWidth: number = 2): number {
    const avgScale = (this.scaleX + this.scaleY) / 2;
    return Math.max(1, Math.min(baseWidth / avgScale, 4));
  }
}
