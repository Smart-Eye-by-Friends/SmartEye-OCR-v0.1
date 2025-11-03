// src/components/slider/PageSlider.tsx
import React from "react";
import styles from "./PageSlider.module.css";

interface PageSliderProps {
  pageCount?: number;
  onClose: () => void;
}

const PageSlider: React.FC<PageSliderProps> = ({ pageCount = 0, onClose }) => {
  return (
    <div className={styles.pageSlider}>
      <div className={styles.sliderHeader}>
        <h3>📄 페이지 ({pageCount})</h3>
        <button
          className={styles.closeBtn}
          onClick={onClose}
          aria-label="슬라이더 닫기"
        >
          ⏴
        </button>
      </div>

      <div className={styles.sliderContent}>
        {/* 파일 업로드 존 */}
        <div className={styles.fileUploadZone}>
          <p>파일을 드래그하거나 클릭하세요</p>
        </div>

        {/* 썸네일 리스트 (임시) */}
        <div className={styles.thumbnailList}>
          {Array.from({ length: pageCount }, (_, i) => (
            <div key={i} className={styles.thumbnailItem}>
              페이지 {i + 1}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default PageSlider;
