// src/components/sidebar/DocumentTypeSelector.tsx
import React, { useState } from "react";
import styles from "./DocumentTypeSelector.module.css";

type DocumentType = "worksheet" | "document";

interface DocumentTypeOption {
  id: DocumentType;
  label: string;
  icon: string;
  description: string;
}

interface DocumentTypeSelectorProps {
  onChange: (type: DocumentType) => void;
}

const DocumentTypeSelector: React.FC<DocumentTypeSelectorProps> = React.memo(
  ({ onChange }) => {
    const [selectedType, setSelectedType] = useState<DocumentType>("worksheet");

    const types: DocumentTypeOption[] = [
      {
        id: "worksheet",
        label: "문제지",
        icon: "📝",
        description: "시험지, 문제집",
      },
      {
        id: "document",
        label: "일반 문서",
        icon: "📄",
        description: "보고서, 논문",
      },
    ];

    const handleSelect = (typeId: DocumentType) => {
      setSelectedType(typeId);
      onChange(typeId);
    };

    return (
      <div className={styles.documentTypeSelector}>
        <h3 className={styles.selectorTitle}>문서 타입</h3>
        <div className={styles.typeOptions}>
          {types.map((type) => (
            <label
              key={type.id}
              className={`${styles.typeOption} ${
                selectedType === type.id ? styles.selected : ""
              }`}
            >
              <input
                type="radio"
                value={type.id}
                checked={selectedType === type.id}
                onChange={() => handleSelect(type.id)}
                className={styles.radioInput}
              />
              <div className={styles.optionContent}>
                <span className={styles.optionIcon}>{type.icon}</span>
                <div className={styles.optionText}>
                  <strong>{type.label}</strong>
                  <small>{type.description}</small>
                </div>
              </div>
            </label>
          ))}
        </div>
      </div>
    );
  }
);

DocumentTypeSelector.displayName = "DocumentTypeSelector";

export default DocumentTypeSelector;
