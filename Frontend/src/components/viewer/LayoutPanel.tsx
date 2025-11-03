// src/components/viewer/LayoutPanel.tsx
import React, { useState, useRef, useEffect } from "react";
import BoundingBoxOverlay from "./BoundingBoxOverlay";
import styles from "./LayoutPanel.module.css";

const LayoutPanel: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [displaySize, setDisplaySize] = useState({ width: 0, height: 0 });

  // TODO: 실제 데이터 연동
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
  }, []);

  const handleBoxClick = (box: any) => {
    console.log("Box clicked:", box);
    // TODO: 에디터로 스크롤
  };

  const handleBoxHover = (box: any) => {
    console.log("Box hovered:", box);
  };

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      <div
        style={{
          padding: "20px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <h2>🖼️ Layout Viewer</h2>
        <p style={{ color: "#666", fontSize: "14px", textAlign: "center" }}>
          이미지 뷰어 및 바운딩 박스 표시 영역입니다.
        </p>
        <div
          style={{
            marginTop: "20px",
            padding: "40px",
            background: "#F9F9F9",
            borderRadius: "8px",
            border: "1px solid #E0E0E0",
          }}
        >
          <p
            style={{
              margin: 0,
              fontSize: "13px",
              color: "#999",
              textAlign: "center",
            }}
          >
            📷 이미지 뷰어
            <br />
            (Task 3.3에서 ImageViewer 구현)
          </p>
        </div>
      </div>

      {analysisResult.bboxes.length > 0 && (
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
