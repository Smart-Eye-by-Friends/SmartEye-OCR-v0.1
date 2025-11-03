// src/components/slider/SliderRestoreButton.tsx
import React from "react";
import styles from "./SliderRestoreButton.module.css";

interface SliderRestoreButtonProps {
  onClick: () => void;
  pageCount?: number;
}

const SliderRestoreButton: React.FC<SliderRestoreButtonProps> = ({
  onClick,
  pageCount = 0,
}) => {
  return (
    <button
      className={styles.sliderRestoreBtn}
      onClick={onClick}
      aria-label="페이지 슬라이더 열기"
    >
      <div className={styles.restoreIcon}>⏵</div>
      <div className={styles.restoreText}>페이지</div>
      {pageCount > 0 && (
        <div className={styles.pageCountBadge}>{pageCount}</div>
      )}
    </button>
  );
};

export default SliderRestoreButton;
