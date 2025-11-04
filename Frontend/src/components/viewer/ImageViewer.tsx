// src/components/viewer/ImageViewer.tsx
import React, { useState, useRef } from "react";
import styles from "./ImageViewer.module.css";

interface ImageViewerProps {
  image?: {
    url: string;
    originalSize: { width: number; height: number };
  } | null;
}

const ImageViewer: React.FC<ImageViewerProps> = ({ image }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });

  const hasImage = Boolean(image?.url);

  const zoomIn = () => {
    if (!hasImage) return;
    setZoom((prev) => Math.min(prev + 0.1, 5));
  };

  const zoomOut = () => {
    if (!hasImage) return;
    setZoom((prev) => Math.max(prev - 0.1, 0.1));
  };

  const resetZoom = () => {
    if (!hasImage) return;
    setZoom(1);
    setPosition({ x: 0, y: 0 });
  };

  return (
    <div className={styles.imageViewer} ref={containerRef}>
      <div className={styles.viewerToolbar}>
        <button onClick={zoomOut} disabled={!hasImage}>
          🔍-
        </button>
        <span>{Math.round(zoom * 100)}%</span>
        <button onClick={zoomIn} disabled={!hasImage}>
          🔍+
        </button>
        <button onClick={resetZoom} disabled={!hasImage}>
          원본
        </button>
      </div>

      <div
        className={styles.imageContainer}
        style={{
          transform: `scale(${zoom}) translate(${position.x}px, ${position.y}px)`,
        }}
      >
        {hasImage ? (
          <img src={image?.url} alt="Document" />
        ) : (
          <div className={styles.placeholder}>페이지 이미지를 선택하세요</div>
        )}
      </div>
    </div>
  );
};

export default ImageViewer;
