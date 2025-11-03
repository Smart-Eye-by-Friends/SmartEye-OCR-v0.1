// src/components/editor/TextEditorTab.tsx
import React from "react";
import ActionButtons from "./ActionButtons";
import styles from "./TextEditorTab.module.css";

interface TextEditorTabProps {
  content: string;
  onChange: (value: string) => void;
  isSaving?: boolean;
  onSave: () => void;
  onNext: () => void;
}

const TextEditorTab: React.FC<TextEditorTabProps> = ({
  content,
  onChange,
  isSaving = false,
  onSave,
  onNext,
}) => {
  return (
    <div className={styles.textEditorTab}>
      <div className={styles.editorContainer}>
        <textarea
          className={styles.textArea}
          value={content}
          onChange={(e) => onChange(e.target.value)}
          placeholder="텍스트를 입력하세요... (TinyMCE는 향후 통합 예정)"
        />
      </div>

      <ActionButtons isSaving={isSaving} onSave={onSave} onNext={onNext} />
    </div>
  );
};

export default TextEditorTab;
