// src/components/viewer/BoundingBoxTooltip.tsx
import React from "react";
import styles from "./BoundingBoxTooltip.module.css";

interface BoundingBoxTooltipProps {
  info: {
    id: string;
    class: string;
    confidence: number;
    text?: string | null;
  };
  positionPx: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  isVisible: boolean;
}

const BoundingBoxTooltip: React.FC<BoundingBoxTooltipProps> = ({
  info,
  positionPx,
  isVisible,
}) => {
  if (!isVisible || !info) return null;

  return (
    <div
      className={styles.bboxTooltip}
      style={{
        left: positionPx.x + positionPx.width / 2,
        top: positionPx.y - 10,
        transform: "translate(-50%, -100%)",
      }}
    >
      <div className={styles.tooltipHeader}>
        <strong>{info.class}</strong>
        <span className={styles.confidenceBadge}>
          {Math.round(info.confidence * 100)}%
        </span>
      </div>
      {info.text && <div className={styles.tooltipContent}>{info.text}</div>}
      <div className={styles.tooltipArrow} />
    </div>
  );
};

export default BoundingBoxTooltip;
