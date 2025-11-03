// src/components/sidebar/Sidebar.tsx
import React, { useState } from "react";
import DocumentTypeSelector from "./DocumentTypeSelector";
import styles from "./Sidebar.module.css";

type DocumentType = "worksheet" | "document";

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");

  const handleDocumentTypeChange = (type: DocumentType) => {
    setDocumentType(type);
    console.log("Document type changed:", type);
  };

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={handleDocumentTypeChange} />
    </div>
  );
};

export default Sidebar;
