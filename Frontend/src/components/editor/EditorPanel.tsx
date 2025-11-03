// src/components/editor/EditorPanel.tsx
import React, { useState } from "react";
import TextEditorTab from "./TextEditorTab";
import AIStatsTab from "./AIStatsTab";
import styles from "./EditorPanel.module.css";

type TabName = "text" | "stats";

const EditorPanel: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabName>("text");
  const [content, setContent] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    // TODO: API 호출
    setTimeout(() => {
      setIsSaving(false);
    }, 1000);
  };

  const handleNext = () => {
    console.log("Next page");
    // TODO: 다음 페이지로 이동
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
        {activeTab === "text" ? (
          <TextEditorTab
            content={content}
            onChange={setContent}
            isSaving={isSaving}
            onSave={handleSave}
            onNext={handleNext}
          />
        ) : (
          <AIStatsTab />
        )}
      </div>
    </div>
  );
};

export default EditorPanel;
