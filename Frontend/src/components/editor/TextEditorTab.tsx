// src/components/editor/TextEditorTab.tsx
import React from "react";
import ActionButtons from "./ActionButtons";
import styles from "./TextEditorTab.module.css";

interface TextEditorTabProps {
  content: string;
  onChange: (value: string) => void;
  isSaving?: boolean;
  isLoading?: boolean;
  disableSave?: boolean;
  hasNext?: boolean;
  error?: string | null;
  onSave: () => void;
  onNext: () => void;
}

const TextEditorTab: React.FC<TextEditorTabProps> = ({
  content,
  onChange,
  isSaving = false,
  isLoading = false,
  disableSave = false,
  hasNext = true,
  error = null,
  onSave,
  onNext,
}) => {
  return (
    <div className={styles.textEditorTab}>
      {error && <div className={styles.errorMessage}>{error}</div>}
      <div className={styles.editorContainer}>
        <textarea
          className={styles.textArea}
          value={content}
          onChange={(e) => onChange(e.target.value)}
          disabled={isLoading}
          placeholder="텍스트를 입력하세요... (TinyMCE는 향후 통합 예정)"
        />
        {isLoading && (
          <div className={styles.loadingOverlay}>텍스트를 불러오는 중...</div>
        )}
      </div>

      <ActionButtons
        isSaving={isSaving}
        disableSave={disableSave}
        hasNext={hasNext}
        onSave={onSave}
        onNext={onNext}
      />
    </div>
  );
};

export default TextEditorTab;
