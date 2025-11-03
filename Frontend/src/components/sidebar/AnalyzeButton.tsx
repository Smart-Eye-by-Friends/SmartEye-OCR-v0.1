// src/components/sidebar/AnalyzeButton.tsx
import React from "react";
import styles from "./AnalyzeButton.module.css";

interface AnalyzeButtonProps {
  isLoading?: boolean;
  disabled?: boolean;
  hasFiles?: boolean;
  onClick: () => void;
}

const AnalyzeButton: React.FC<AnalyzeButtonProps> = ({
  isLoading = false,
  disabled = false,
  hasFiles = false,
  onClick,
}) => {
  const isDisabled = disabled || !hasFiles || isLoading;

  return (
    <button
      className={`${styles.analyzeBtn} ${isLoading ? styles.loading : ""}`}
      disabled={isDisabled}
      onClick={onClick}
    >
      {isLoading ? (
        <>
          <span className={styles.spinner}></span>
          분석 중...
        </>
      ) : (
        <>
          <span className={styles.icon}>🚀</span>
          분석 시작
        </>
      )}
    </button>
  );
};

export default AnalyzeButton;
