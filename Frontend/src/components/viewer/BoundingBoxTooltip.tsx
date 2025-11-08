// src/components/viewer/BoundingBoxTooltip.tsx
import React, { useLayoutEffect, useRef, useState } from "react";
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
  boundsPx?: {
    width: number;
    height: number;
  };
  zoom?: number;
  isVisible: boolean;
}

const DEFAULT_TOOLTIP_SIZE = {
  width: 220,
  height: 80,
};

const BoundingBoxTooltip: React.FC<BoundingBoxTooltipProps> = ({
  info,
  positionPx,
  boundsPx,
  zoom = 1,
  isVisible,
}) => {
  const tooltipRef = useRef<HTMLDivElement>(null);
  const [measuredSize, setMeasuredSize] = useState(DEFAULT_TOOLTIP_SIZE);

  useLayoutEffect(() => {
    if (!isVisible || !tooltipRef.current) return;
    const rect = tooltipRef.current.getBoundingClientRect();
    const safeZoom = zoom > 0 ? zoom : 1;
    const width = rect.width / safeZoom;
    const height = rect.height / safeZoom;
    setMeasuredSize((prev) => {
      if (Math.abs(prev.width - width) < 1 && Math.abs(prev.height - height) < 1) {
        return prev;
      }
      return { width, height };
    });
  }, [info, isVisible, zoom, positionPx]);

  if (!isVisible || !info) return null;

  const margin = 12;
  const boundsWidth = boundsPx?.width ?? Number.POSITIVE_INFINITY;
  const boundsHeight = boundsPx?.height ?? Number.POSITIVE_INFINITY;
  const tooltipWidth = measuredSize.width || DEFAULT_TOOLTIP_SIZE.width;
  const tooltipHeight = measuredSize.height || DEFAULT_TOOLTIP_SIZE.height;
  const halfWidth = tooltipWidth / 2;

  let centerX = positionPx.x + positionPx.width / 2;
  if (Number.isFinite(boundsWidth)) {
    const minCenter = margin + halfWidth;
    const maxCenter = Math.max(minCenter, boundsWidth - margin - halfWidth);
    centerX = Math.min(Math.max(centerX, minCenter), maxCenter);
  }
  const left = centerX - halfWidth;

  const topSpace = positionPx.y - margin;
  const bottomSpace =
    Number.isFinite(boundsHeight)
      ? boundsHeight - (positionPx.y + positionPx.height) - margin
      : Number.POSITIVE_INFINITY;

  let placement: "top" | "bottom" = "top";
  if (topSpace < tooltipHeight && bottomSpace > topSpace) {
    placement = "bottom";
  }

  let top =
    placement === "top"
      ? positionPx.y - margin - tooltipHeight
      : positionPx.y + positionPx.height + margin;

  if (placement === "top" && top < margin) {
    top = margin;
  }

  if (placement === "bottom" && Number.isFinite(boundsHeight)) {
    const maxTop = Math.max(margin, boundsHeight - margin - tooltipHeight);
    top = Math.min(Math.max(top, margin), maxTop);
  }

  return (
    <div
      ref={tooltipRef}
      className={styles.bboxTooltip}
      data-placement={placement}
      style={{
        left,
        top,
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
