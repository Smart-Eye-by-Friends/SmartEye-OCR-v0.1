// src/components/sidebar/Sidebar.tsx
import React, { useState } from "react";
import DocumentTypeSelector from "./DocumentTypeSelector";
import ModelSelector from "./ModelSelector";
import AnalyzeButton from "./AnalyzeButton";
import IntegratedDownloadButton from "./IntegratedDownloadButton";
import { useModelSelection } from "@/hooks/useModelSelection";
import type { AIModel } from "@/hooks/useModelSelection";
import { usePages } from "@/contexts/PagesContext";
import { analysisService } from "@/services/analysis";
import styles from "./Sidebar.module.css";

type DocumentType = "worksheet" | "document";
const MODEL_NAME_MAP: Record<AIModel, string> = {
  smarteye: "SmartEyeSsen",
  doclayout: "docstructbench",
};

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");
  const { selectedModel, isAutoSelected } = useModelSelection(documentType);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const { state, dispatch } = usePages();

  const hasFiles = state.pages.length > 0;
  const projectId = state.currentProjectId;

  const handleDocumentTypeChange = (type: DocumentType) => {
    setDocumentType(type);
    console.log("Document type changed:", type);
  };

  const handleAnalyze = async () => {
    const pendingPages = state.pages.filter(
      (page) => page.analysisStatus === "pending"
    );

    if (!hasFiles) {
      alert("파일을 먼저 업로드해주세요");
      return;
    }

    if (!projectId) {
      alert("프로젝트 정보를 찾을 수 없습니다. 페이지를 다시 업로드해주세요.");
      return;
    }

    if (pendingPages.length === 0) {
      alert("분석할 대기 페이지가 없습니다.");
      return;
    }

    setIsAnalyzing(true);

    // 대기 중인 페이지 상태를 processing으로 업데이트
    pendingPages.forEach((page) =>
      dispatch({ type: "UPDATE_PAGE_STATUS", payload: { id: page.id, status: "processing" } })
    );

    try {
      const response = await analysisService.analyzeProject(projectId, {
        useAiDescriptions: true,
        analysisModel: MODEL_NAME_MAP[selectedModel],
      });

      console.log("분석 완료", response);

      alert("분석이 완료되었습니다.");
    } catch (error) {
      console.error("분석 실패", error);

      pendingPages.forEach((page) =>
        dispatch({ type: "UPDATE_PAGE_STATUS", payload: { id: page.id, status: "error" } })
      );

      let errorMessage = "분석 요청 중 오류가 발생했습니다.";
      if (error && typeof error === "object") {
        const err = error as {
          response?: { status: number; data?: { error?: string; detail?: string } };
          message?: string;
        };

        if (err.response) {
          errorMessage = `서버 오류 (${err.response.status}): ${
            err.response.data?.error || err.response.data?.detail || err.message || "알 수 없는 오류"
          }`;
        }
      }

      alert(errorMessage);
    } finally {
      setIsAnalyzing(false);
    }
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

      <IntegratedDownloadButton
        pages={state.pages}
        projectId={state.currentProjectId}
      />
    </div>
  );
};

export default Sidebar;
