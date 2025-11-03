// src/components/viewer/BoundingBoxOverlay.tsx
import React, { useMemo } from "react";
import { CoordinateScaler } from "@/utils/coordinateScaler";
import styles from "./BoundingBoxOverlay.module.css";

interface BoundingBoxOverlayProps {
  bboxes: any[];
  imageSize: { width: number; height: number };
  displaySize: { width: number; height: number };
  onBoxClick?: (box: any) => void;
  onBoxHover?: (box: any) => void;
}

const CLASS_COLORS: Record<string, string> = {
  question_number: "#FF5722",
  question_text: "#2196F3",
  choices: "#4CAF50",
  title: "#9C27B0",
  paragraph: "#FF9800",
  table: "#00BCD4",
  figure: "#E91E63",
};

const BoundingBoxOverlay: React.FC<BoundingBoxOverlayProps> = ({
  bboxes,
  imageSize,
  displaySize,
  onBoxClick,
  onBoxHover,
}) => {
  const scaler = useMemo(() => {
    if (!imageSize || !displaySize) return null;
    return new CoordinateScaler(
      imageSize.width,
      imageSize.height,
      displaySize.width,
      displaySize.height
    );
  }, [imageSize, displaySize]);

  const scaledBoxes = useMemo(() => {
    if (!scaler || !bboxes) return [];
    return scaler.scaleAll(bboxes);
  }, [scaler, bboxes]);

  if (!scaler || scaledBoxes.length === 0) {
    return null;
  }

  const strokeWidth = scaler.getStrokeWidth();

  return (
    <svg
      className={styles.boundingBoxOverlay}
      width={displaySize.width}
      height={displaySize.height}
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        pointerEvents: "none",
      }}
    >
      <g className={styles.bboxGroup}>
        {scaledBoxes.map((box, index) => {
          const coords = box.coordinates;
          const color = CLASS_COLORS[box.class] || "#999999";

          return (
            <g
              key={box.id || index}
              style={{ cursor: "pointer", pointerEvents: "all" }}
              onClick={() => onBoxClick?.(box)}
              onMouseEnter={() => onBoxHover?.(box)}
            >
              {/* 반투명 배경 */}
              <rect
                x={coords.x}
                y={coords.y}
                width={coords.width}
                height={coords.height}
                fill={color}
                fillOpacity={0.2}
                stroke={color}
                strokeWidth={strokeWidth}
                strokeOpacity={0.8}
                rx={2}
              />

              {/* 클래스 라벨 (호버 시만 표시하도록 나중에 개선) */}
              <text
                x={coords.x + 5}
                y={coords.y + 15}
                fontSize={12}
                fill={color}
                fontWeight="600"
                style={{ pointerEvents: "none" }}
              >
                {box.class} ({Math.round(box.confidence * 100)}%)
              </text>
            </g>
          );
        })}
      </g>
    </svg>
  );
};

export default BoundingBoxOverlay;
