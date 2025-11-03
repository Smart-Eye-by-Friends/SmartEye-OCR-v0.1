// src/components/viewer/BoundingBoxTooltip.tsx
import React from "react";
import ReactDOM from "react-dom";
import styles from "./BoundingBoxTooltip.module.css";

interface BoundingBoxTooltipProps {
  info: any;
  position: any;
  isVisible: boolean;
}

const BoundingBoxTooltip: React.FC<BoundingBoxTooltipProps> = ({
  info,
  position,
  isVisible,
}) => {
  if (!isVisible || !info) return null;

  const tooltipContent = (
    <div
      className={styles.bboxTooltip}
      style={{
        left: `${position.x + position.width / 2}px`,
        top: `${position.y - 10}px`,
        transform: "translate(-50%, -100%)",
      }}
    >
      <div className={styles.tooltipHeader}>
        <strong>{info.title}</strong>
        <span className={styles.confidenceBadge}>{info.confidence}</span>
      </div>
      {info.text && <div className={styles.tooltipContent}>{info.text}</div>}
      <div className={styles.tooltipArrow} />
    </div>
  );

  return ReactDOM.createPortal(tooltipContent, document.body);
};

export default BoundingBoxTooltip;
