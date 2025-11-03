// src/hooks/useModelSelection.ts
import { useState, useEffect } from "react";

export type AIModel = "smarteye" | "doclayout";
export type DocumentType = "worksheet" | "document";

export const useModelSelection = (documentType: DocumentType) => {
  const [selectedModel, setSelectedModel] = useState<AIModel>("smarteye");
  const isAutoSelected = true;

  useEffect(() => {
    if (documentType === "worksheet") {
      setSelectedModel("smarteye");
    } else if (documentType === "document") {
      setSelectedModel("doclayout");
    }
  }, [documentType]);

  return {
    selectedModel,
    isAutoSelected,
  };
};
