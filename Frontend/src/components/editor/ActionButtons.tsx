// src/components/editor/ActionButtons.tsx
import React from "react";
import styles from "./ActionButtons.module.css";

interface ActionButtonsProps {
  isSaving?: boolean;
  disableSave?: boolean;
  hasNext?: boolean;
  onSave: () => void;
  onNext: () => void;
}

const ActionButtons: React.FC<ActionButtonsProps> = ({
  isSaving = false,
  disableSave = false,
  hasNext = true,
  onSave,
  onNext,
}) => {
  return (
    <div className={styles.actionButtons}>
      <button
        className={styles.saveBtn}
        disabled={isSaving || disableSave}
        onClick={onSave}
      >
        {isSaving ? (
          <>
            <span className={styles.spinner}></span>
            저장 중...
          </>
        ) : (
          <>
            <span className={styles.icon}>💾</span>
            저장
          </>
        )}
      </button>

      <button className={styles.nextBtn} disabled={!hasNext} onClick={onNext}>
        <span className={styles.icon}>▶️</span>
        다음 페이지
      </button>
    </div>
  );
};

export default ActionButtons;
