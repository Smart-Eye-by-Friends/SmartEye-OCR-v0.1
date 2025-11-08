// src/components/viewer/ImageViewer.tsx
import React, { useState, useRef, useMemo } from "react";
import styles from "./ImageViewer.module.css";

interface ImageViewerProps {
  image?: {
    url: string;
    originalSize: { width: number; height: number };
  } | null;
  displaySize?: { width: number; height: number };
}

const ImageViewer: React.FC<ImageViewerProps> = ({ image, displaySize }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const isPanningRef = useRef(false);
  const panStartRef = useRef({ x: 0, y: 0 });
  const pointerStartRef = useRef({ x: 0, y: 0 });

  const hasImage = Boolean(image?.url);
  const targetSize = useMemo(() => {
    if (displaySize && displaySize.width > 0 && displaySize.height > 0) {
      return displaySize;
    }
    if (image?.originalSize?.width && image?.originalSize?.height) {
      return image.originalSize;
    }
    return null;
  }, [displaySize, image]);

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

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!hasImage) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    isPanningRef.current = true;
    panStartRef.current = { ...position };
    pointerStartRef.current = { x: event.clientX, y: event.clientY };
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isPanningRef.current) return;
    event.preventDefault();
    const deltaX = event.clientX - pointerStartRef.current.x;
    const deltaY = event.clientY - pointerStartRef.current.y;
    setPosition({
      x: panStartRef.current.x + deltaX,
      y: panStartRef.current.y + deltaY,
    });
  };

  const stopPanning = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isPanningRef.current) return;
    event.preventDefault();
    event.currentTarget.releasePointerCapture(event.pointerId);
    isPanningRef.current = false;
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
      >
        {hasImage ? (
          <div
            className={styles.imageStage}
            ref={stageRef}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={stopPanning}
            onPointerLeave={stopPanning}
            style={{
              width: targetSize?.width ? `${targetSize.width}px` : "100%",
              height: targetSize?.height ? `${targetSize.height}px` : "100%",
            }}
          >
            <img
              src={image?.url}
              alt="Document"
              style={{
                transform: `translate(${position.x}px, ${position.y}px) scale(${zoom})`,
                transformOrigin: "top left",
              }}
            />
          </div>
        ) : (
          <div className={styles.placeholder}>페이지 이미지를 선택하세요</div>
        )}
      </div>
    </div>
  );
};

export default ImageViewer;
