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
  position: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  isVisible: boolean;
}

const BoundingBoxTooltip: React.FC<BoundingBoxTooltipProps> = ({
  info,
  position,
  isVisible,
}) => {
  if (!isVisible || !info) return null;

  return (
    <div
      className={styles.bboxTooltip}
      style={{
        left: position.x + position.width / 2,
        top: position.y - 10,
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
