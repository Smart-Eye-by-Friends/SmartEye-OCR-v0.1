// src/components/editor/EditorPanel.tsx
import React, { useEffect, useMemo, useState } from "react";
import TextEditorTab from "./TextEditorTab";
import AIStatsTab from "./AIStatsTab";
import styles from "./EditorPanel.module.css";
import { usePages } from "@/contexts/PagesContext";
import { analysisService } from "@/services/analysis";

type TabName = "text" | "stats";

const EditorPanel: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabName>("text");
  const [content, setContent] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);

  const { state, dispatch } = usePages();

  const sortedPages = useMemo(() => state.pages, [state.pages]);
  const currentPage = useMemo(() => {
    if (!state.currentPageId) {
      return null;
    }
    return sortedPages.find((page) => page.id === state.currentPageId) || null;
  }, [sortedPages, state.currentPageId]);

  const currentIndex = currentPage
    ? sortedPages.findIndex((page) => page.id === currentPage.id)
    : -1;
  const hasNext = currentIndex >= 0 && currentIndex < sortedPages.length - 1;
  const nextPageId = hasNext ? sortedPages[currentIndex + 1].id : null;

  useEffect(() => {
    if (!currentPage) {
      setContent("");
      setError(null);
      setIsDirty(false);
      return;
    }

    if (currentPage.analysisStatus !== "completed") {
      setContent("");
      setError(null);
      setIsDirty(false);
      return;
    }

    const pageIdNumber = Number(currentPage.id);
    if (!Number.isFinite(pageIdNumber)) {
      setError("잘못된 페이지 ID입니다.");
      return;
    }

    setIsLoading(true);
    setError(null);

    analysisService
      .getPageText(pageIdNumber)
      .then((data) => {
        setContent(data.content ?? "");
        setIsDirty(false);
      })
      .catch((err: unknown) => {
        console.error("페이지 텍스트 조회 실패", err);
        let message = "텍스트를 불러오는 중 오류가 발생했습니다.";
        if (err && typeof err === "object") {
          const errorObj = err as {
            response?: { status: number; data?: { detail?: string; error?: string } };
            message?: string;
          };
          if (errorObj.response) {
            message = `서버 오류 (${errorObj.response.status}): ${
              errorObj.response.data?.detail || errorObj.response.data?.error ||
              errorObj.message || "알 수 없는 오류"
            }`;
          }
        }
        setError(message);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [currentPage]);

  const handleContentChange = (value: string) => {
    setContent(value);
    setIsDirty(true);
    if (error) {
      setError(null);
    }
  };

  const handleSave = async () => {
    if (!currentPage) {
      alert("저장할 페이지가 없습니다.");
      return;
    }

    const pageIdNumber = Number(currentPage.id);
    if (!Number.isFinite(pageIdNumber)) {
      alert("잘못된 페이지 ID입니다.");
      return;
    }

    setIsSaving(true);

    try {
      await analysisService.savePageText(pageIdNumber, content, 1);
      setIsDirty(false);
      alert("텍스트가 저장되었습니다.");
    } catch (err) {
      console.error("텍스트 저장 실패", err);
      let message = "텍스트 저장 중 오류가 발생했습니다.";
      if (err && typeof err === "object") {
        const errorObj = err as {
          response?: { status: number; data?: { detail?: string; error?: string } };
          message?: string;
        };
        if (errorObj.response) {
          message = `서버 오류 (${errorObj.response.status}): ${
            errorObj.response.data?.detail || errorObj.response.data?.error ||
            errorObj.message || "알 수 없는 오류"
          }`;
        }
      }
      setError(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleNext = () => {
    if (!hasNext || !nextPageId) {
      return;
    }

    if (isDirty && !window.confirm("현재 변경사항이 저장되지 않았습니다. 이동하시겠습니까?")) {
      return;
    }

    dispatch({ type: "SET_CURRENT_PAGE", payload: nextPageId });
  };

  const renderTextEditor = () => {
    if (!currentPage) {
      return (
        <div className={styles.emptyState}>
          <p>편집할 페이지가 없습니다.</p>
          <small>왼쪽에서 페이지를 업로드하거나 선택해주세요.</small>
        </div>
      );
    }

    return (
      <TextEditorTab
        content={content}
        onChange={handleContentChange}
        isSaving={isSaving}
        isLoading={isLoading}
        disableSave={!isDirty || isLoading}
        hasNext={hasNext}
        error={error}
        onSave={handleSave}
        onNext={handleNext}
      />
    );
  };

  return (
    <div className={styles.editorPanel}>
      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${
            activeTab === "text" ? styles.active : ""
          }`}
          onClick={() => setActiveTab("text")}
        >
          📝 텍스트 편집
        </button>
        <button
          className={`${styles.tab} ${
            activeTab === "stats" ? styles.active : ""
          }`}
          onClick={() => setActiveTab("stats")}
        >
          🎨 AI 통계
        </button>
      </div>

      <div className={styles.tabContent}>
        {activeTab === "text" ? renderTextEditor() : <AIStatsTab />}
      </div>
    </div>
  );
};

export default EditorPanel;
