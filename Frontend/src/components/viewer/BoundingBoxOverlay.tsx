// src/components/viewer/BoundingBoxOverlay.tsx
import React, { useMemo, useState } from "react";
import BoundingBoxTooltip from "./BoundingBoxTooltip";
import styles from "./BoundingBoxOverlay.module.css";

interface BoundingBoxOverlayProps {
  bboxes: any[];
  imageSize: { width: number; height: number };
  displaySize: { width: number; height: number };
  transform: { zoom: number; position: { x: number; y: number } };
  isVisible: boolean;
  visibleClasses?: Set<string>;
  onBoxClick?: (box: any) => void;
  onBoxHover?: (box: any) => void;
}

const CLASS_COLORS: Record<string, string> = {
  question_number: "#FF5722",
  question_text: "#2196F3",
  question_type: "#E91E63",
  choices: "#4CAF50",
  title: "#9C27B0",
  paragraph: "#FF9800",
  plain_text: "#795548",
  table: "#00BCD4",
  figure: "#E91E63",
  table_caption: "#009688",
  table_footnote: "#607D8B",
};

const BoundingBoxOverlay: React.FC<BoundingBoxOverlayProps> = React.memo(
  ({
    bboxes,
    imageSize,
    displaySize,
    transform,
    isVisible,
    visibleClasses,
    onBoxClick,
    onBoxHover,
  }) => {
    const [hoveredBox, setHoveredBox] = useState<any>(null);

    // 필터링된 박스
    const filteredBoxes = useMemo(() => {
      if (!visibleClasses || visibleClasses.size === 0) {
        return bboxes;
      }
      return bboxes.filter((box) => visibleClasses.has(box.class));
    }, [bboxes, visibleClasses]);

    if (!isVisible || filteredBoxes.length === 0) {
      return null;
    }

    // Zoom에 따른 stroke width 조정, font size는 고정 (transform 상속으로 자동 확대)
    const strokeWidth = Math.max(1, 2);
    const fontSize = 18;

    const handleBoxClick = (box: any) => {
      onBoxClick?.(box);
    };

    const handleBoxHover = (box: any) => {
      setHoveredBox(box);
      onBoxHover?.(box);
    };

    const handleBoxLeave = () => {
      setHoveredBox(null);
    };

    return (
      <>
        <svg
          className={styles.boundingBoxOverlay}
          viewBox={`0 0 ${imageSize.width} ${imageSize.height}`}
          width={displaySize.width}
          height={displaySize.height}
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            pointerEvents: "all",
          }}
        >
          <g className={styles.bboxGroup}>
            {filteredBoxes.map((box, index) => {
              const coords = box.coordinates;
              const color = CLASS_COLORS[box.class] || "#999999";

              return (
                <g
                  key={box.id || index}
                  style={{ cursor: "pointer" }}
                  onClick={() => handleBoxClick(box)}
                  onMouseEnter={() => handleBoxHover(box)}
                  onMouseLeave={handleBoxLeave}
                >
                  {/* 반투명 배경 - 원본 좌표 사용 */}
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

                  {/* 클래스 라벨 배경 */}
                  <rect
                    x={coords.x}
                    y={coords.y}
                    width={coords.width * 0.4}
                    height={22}
                    fill="white"
                    fillOpacity={0.85}
                    rx={2}
                  />

                  {/* 클래스 라벨 */}
                  <text
                    x={coords.x + 5}
                    y={coords.y + 16}
                    fontSize={fontSize}
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

        {hoveredBox && (
          <BoundingBoxTooltip
            info={{
              id: hoveredBox.id,
              class: hoveredBox.class,
              confidence: hoveredBox.confidence,
              text: hoveredBox.text,
            }}
            position={hoveredBox.coordinates}
            isVisible={true}
          />
        )}
      </>
    );
  },
  (prevProps, nextProps) => {
    // Custom comparison for optimization
    return (
      prevProps.bboxes === nextProps.bboxes &&
      prevProps.imageSize.width === nextProps.imageSize.width &&
      prevProps.imageSize.height === nextProps.imageSize.height &&
      prevProps.displaySize.width === nextProps.displaySize.width &&
      prevProps.displaySize.height === nextProps.displaySize.height &&
      prevProps.transform.zoom === nextProps.transform.zoom &&
      prevProps.transform.position.x === nextProps.transform.position.x &&
      prevProps.transform.position.y === nextProps.transform.position.y &&
      prevProps.isVisible === nextProps.isVisible &&
      prevProps.visibleClasses === nextProps.visibleClasses
    );
  }
);

BoundingBoxOverlay.displayName = "BoundingBoxOverlay";

export default BoundingBoxOverlay;
