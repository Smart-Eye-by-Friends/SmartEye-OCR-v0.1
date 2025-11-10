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
  visibleClasses?: Set<string> | null;
  onBoxClick?: (box: any) => void;
  onBoxHover?: (box: any) => void;
}

const PREDEFINED_CLASS_COLORS: Record<string, string> = {
  question_number: "#FF7043",
  question_text: "#42A5F5",
  question_type: "#EC407A",
  choices: "#66BB6A",
  title: "#AB47BC",
  paragraph: "#FFA726",
  plain_text: "#8D6E63",
  table: "#26C6DA",
  figure: "#EC407A",
  table_caption: "#26A69A",
  table_footnote: "#607D8B",
  heading: "#7E57C2",
  section_title: "#7E57C2",
  body_text: "#8D6E63",
  list_item: "#FFA000",
  image: "#EF5350",
  caption: "#26A69A",
  footer: "#546E7A",
  header: "#5C6BC0",
};

const CLASS_COLOR_PALETTE = [
  "#F06292",
  "#64B5F6",
  "#4DB6AC",
  "#FF8A65",
  "#BA68C8",
  "#AED581",
  "#FFB74D",
  "#4DD0E1",
  "#9575CD",
  "#81C784",
  "#FFAB91",
  "#90CAF9",
];

const dynamicColorMap = new Map<string, string>();

const getClassColor = (className: string): string => {
  if (PREDEFINED_CLASS_COLORS[className]) {
    return PREDEFINED_CLASS_COLORS[className];
  }

  if (dynamicColorMap.has(className)) {
    return dynamicColorMap.get(className)!;
  }

  const nextIndex = dynamicColorMap.size % CLASS_COLOR_PALETTE.length;
  const color = CLASS_COLOR_PALETTE[nextIndex];
  dynamicColorMap.set(className, color);
  return color;
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
      if (!visibleClasses) {
        return bboxes;
      }
      if (visibleClasses.size === 0) {
        return [];
      }
      return bboxes.filter((box) => visibleClasses.has(box.class));
    }, [bboxes, visibleClasses]);

    const hasDisplaySize =
      displaySize.width > 0 &&
      displaySize.height > 0 &&
      imageSize.width > 0 &&
      imageSize.height > 0;

    if (!isVisible || filteredBoxes.length === 0 || !hasDisplaySize) {
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

    const scale = {
      x: displaySize.width / imageSize.width,
      y: displaySize.height / imageSize.height,
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
              const color = getClassColor(box.class);

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
            positionPx={{
              x: hoveredBox.coordinates.x * scale.x,
              y: hoveredBox.coordinates.y * scale.y,
              width: hoveredBox.coordinates.width * scale.x,
              height: hoveredBox.coordinates.height * scale.y,
            }}
            boundsPx={{
              width: displaySize.width,
              height: displaySize.height,
            }}
            zoom={transform.zoom}
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
