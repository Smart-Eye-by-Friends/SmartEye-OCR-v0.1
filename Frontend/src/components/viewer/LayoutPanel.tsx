// src/components/viewer/LayoutPanel.tsx
import React, { useState, useRef, useEffect } from "react";
import ImageViewer from "./ImageViewer";
import BoundingBoxOverlay from "./BoundingBoxOverlay";
import styles from "./LayoutPanel.module.css";

const LayoutPanel: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [displaySize, setDisplaySize] = useState({ width: 0, height: 0 });

  // TODO: Context나 Props로 실제 데이터 연동
  const currentImage = {
    url: "",
    originalSize: { width: 2000, height: 3000 },
  };

  const analysisResult = {
    bboxes: [],
  };

  const updateSize = () => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setDisplaySize({
      width: rect.width,
      height: rect.height,
    });
  };

  useEffect(() => {
    updateSize();
    window.addEventListener("resize", updateSize);
    return () => window.removeEventListener("resize", updateSize);
  }, [currentImage]);

  const handleBoxClick = (box: any) => {
    console.log("Box clicked:", box);
    // TODO: 에디터로 스크롤
  };

  const handleBoxHover = (box: any) => {
    console.log("Box hovered:", box);
  };

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      <ImageViewer image={currentImage} />

      {analysisResult && analysisResult.bboxes.length > 0 && (
        <BoundingBoxOverlay
          bboxes={analysisResult.bboxes}
          imageSize={currentImage.originalSize}
          displaySize={displaySize}
          onBoxClick={handleBoxClick}
          onBoxHover={handleBoxHover}
        />
      )}
    </div>
  );
};

export default LayoutPanel;
