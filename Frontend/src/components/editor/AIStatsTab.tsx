// src/components/editor/AIStatsTab.tsx
import React from "react";
import styles from "./AIStatsTab.module.css";

const AIStatsTab: React.FC = () => {
  return (
    <div className={styles.aiStatsTab}>
      <div className={styles.statsContainer}>
        <h3 className={styles.title}>🎨 AI 분석 통계</h3>
        <p className={styles.description}>
          바운딩 박스 클래스별 통계 및 AI 모델 신뢰도 정보를 표시합니다.
        </p>

        <div className={styles.placeholder}>
          <div className={styles.placeholderIcon}>📊</div>
          <p className={styles.placeholderText}>
            AI 통계 시각화
            <br />
            <span className={styles.placeholderSubtext}>
              (Phase 4에서 구현 예정)
            </span>
          </p>
        </div>
      </div>
    </div>
  );
};

export default AIStatsTab;
