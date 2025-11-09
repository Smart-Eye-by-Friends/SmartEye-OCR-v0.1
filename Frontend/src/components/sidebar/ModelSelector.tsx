// src/components/sidebar/ModelSelector.tsx
import React from "react";
import type { AIModel } from "@/hooks/useModelSelection";
import styles from "./ModelSelector.module.css";

interface ModelSelectorProps {
  selectedModel: AIModel;
  isAutoSelected?: boolean;
}

const ModelSelector: React.FC<ModelSelectorProps> = ({
  selectedModel,
  isAutoSelected = true,
}) => {
  const models = [
    {
      id: "smarteye" as AIModel,
      label: "SmartEye",
      description: "문제지 특화",
    },
    {
      id: "doclayout" as AIModel,
      label: "DocLayout",
      description: "일반 문서",
    },
  ];

  return (
    <div className={styles.modelSelector}>
      <h3 className={styles.selectorTitle}>
        AI 모델
        {isAutoSelected && <span className={styles.autoBadge}>자동 선택</span>}
      </h3>
      <div className={styles.modelOptions}>
        {models.map((model) => (
          <div
            key={model.id}
            className={`${styles.modelOption} ${
              selectedModel === model.id ? styles.selected : ""
            } ${isAutoSelected ? styles.disabled : ""}`}
          >
            <strong>{model.label}</strong>
            <small>{model.description}</small>
          </div>
        ))}
      </div>
      {isAutoSelected && (
        <p className={styles.autoInfo}>
          ℹ️ 문서 타입에 따라 자동으로 선택됩니다
        </p>
      )}
    </div>
  );
};

export default ModelSelector;
