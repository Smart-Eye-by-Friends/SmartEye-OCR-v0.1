// src/components/sidebar/Sidebar.tsx
import React, { useState } from "react";
import DocumentTypeSelector from "./DocumentTypeSelector";
import ModelSelector from "./ModelSelector";
import { useModelSelection } from "@/hooks/useModelSelection";
import styles from "./Sidebar.module.css";

type DocumentType = "worksheet" | "document";

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");
  const { selectedModel, isAutoSelected } = useModelSelection(documentType);

  const handleDocumentTypeChange = (type: DocumentType) => {
    setDocumentType(type);
    console.log("Document type changed:", type);
  };

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={handleDocumentTypeChange} />

      <ModelSelector
        selectedModel={selectedModel}
        isAutoSelected={isAutoSelected}
      />
    </div>
  );
};

export default Sidebar;
