// src/components/sidebar/DownloadProgressModal.tsx
import React from "react";
import ReactDOM from "react-dom";
import type { DownloadProgress } from "@/services/download";
import styles from "./DownloadProgressModal.module.css";

interface DownloadProgressModalProps {
  isOpen: boolean;
  progress: DownloadProgress;
  onClose: () => void;
}

const DownloadProgressModal: React.FC<DownloadProgressModalProps> = ({
  isOpen,
  progress,
  onClose,
}) => {
  if (!isOpen) return null;

  const modalContent = (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div
        className={styles.progressModal}
        onClick={(e) => e.stopPropagation()}
      >
        <h3>다운로드 진행 중...</h3>

        <div className={styles.progressBarContainer}>
          <div
            className={styles.progressBarFill}
            style={{ width: `${progress.percentage}%` }}
          />
        </div>

        <p className={styles.progressText}>
          {progress.current} / {progress.total} 페이지 ({progress.percentage}%)
        </p>

        {progress.percentage === 100 && (
          <div className={styles.successMessage}>✅ 다운로드 완료!</div>
        )}

        <button
          className={styles.closeBtn}
          disabled={progress.percentage < 100}
          onClick={onClose}
        >
          닫기
        </button>
      </div>
    </div>
  );

  return ReactDOM.createPortal(modalContent, document.body);
};

export default DownloadProgressModal;
