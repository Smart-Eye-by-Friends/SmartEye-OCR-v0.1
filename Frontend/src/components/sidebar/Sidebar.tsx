// src/components/sidebar/Sidebar.tsx
import React, { useState } from "react";
import DocumentTypeSelector from "./DocumentTypeSelector";
import ModelSelector from "./ModelSelector";
import AnalyzeButton from "./AnalyzeButton";
import { useModelSelection } from "@/hooks/useModelSelection";
import styles from "./Sidebar.module.css";

type DocumentType = "worksheet" | "document";

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");
  const { selectedModel, isAutoSelected } = useModelSelection(documentType);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [hasFiles, setHasFiles] = useState(false);

  const handleDocumentTypeChange = (type: DocumentType) => {
    setDocumentType(type);
    console.log("Document type changed:", type);
  };

  const handleAnalyze = async () => {
    if (!hasFiles) {
      alert("파일을 먼저 업로드해주세요");
      return;
    }

    setIsAnalyzing(true);

    // TODO: 실제 분석 API 호출
    console.log("분석 시작:", { documentType, selectedModel });

    setTimeout(() => {
      setIsAnalyzing(false);
      console.log("분석 완료");
    }, 3000);
  };

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={handleDocumentTypeChange} />

      <ModelSelector
        selectedModel={selectedModel}
        isAutoSelected={isAutoSelected}
      />

      <AnalyzeButton
        isLoading={isAnalyzing}
        hasFiles={hasFiles}
        onClick={handleAnalyze}
      />
    </div>
  );
};

export default Sidebar;
