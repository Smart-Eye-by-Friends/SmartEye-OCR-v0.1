// src/components/viewer/ImageViewer.tsx
import React, { useState, useRef } from "react";
import styles from "./ImageViewer.module.css";

interface ImageViewerProps {
  image: {
    url: string;
    originalSize: { width: number; height: number };
  };
}

const ImageViewer: React.FC<ImageViewerProps> = ({ image }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });

  const zoomIn = () => {
    setZoom((prev) => Math.min(prev + 0.1, 5));
  };

  const zoomOut = () => {
    setZoom((prev) => Math.max(prev - 0.1, 0.1));
  };

  const resetZoom = () => {
    setZoom(1);
    setPosition({ x: 0, y: 0 });
  };

  return (
    <div className={styles.imageViewer} ref={containerRef}>
      <div className={styles.viewerToolbar}>
        <button onClick={zoomOut}>🔍-</button>
        <span>{Math.round(zoom * 100)}%</span>
        <button onClick={zoomIn}>🔍+</button>
        <button onClick={resetZoom}>원본</button>
      </div>

      <div
        className={styles.imageContainer}
        style={{
          transform: `scale(${zoom}) translate(${position.x}px, ${position.y}px)`,
        }}
      >
        {image.url && <img src={image.url} alt="Document" />}
      </div>
    </div>
  );
};

export default ImageViewer;
