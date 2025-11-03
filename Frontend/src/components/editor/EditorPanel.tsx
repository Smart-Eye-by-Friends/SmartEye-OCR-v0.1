// src/components/editor/EditorPanel.tsx
import React from "react";

const EditorPanel: React.FC = () => {
  return (
    <div className="editor-panel" style={{ padding: "20px" }}>
      <h2>✏️ Editor Panel</h2>
      <p style={{ color: "#666", fontSize: "14px" }}>
        텍스트 편집 및 분석 결과 표시 영역입니다.
      </p>
      <div
        style={{
          marginTop: "20px",
          padding: "20px",
          background: "#FAFAFA",
          borderRadius: "4px",
          border: "1px solid #EEEEEE",
        }}
      >
        <p style={{ margin: 0, fontSize: "13px", color: "#999" }}>
          📝 TinyMCE 에디터
          <br />
          (Phase 4에서 구현)
        </p>
      </div>
      <div
        style={{
          marginTop: "16px",
          padding: "12px",
          background: "#F5F5F5",
          borderRadius: "4px",
        }}
      >
        <p style={{ margin: 0, fontSize: "12px" }}>
          ✅ 임시 컴포넌트
          <br />탭 네비게이션, AI 통계 등 추가 예정
        </p>
      </div>
    </div>
  );
};

export default EditorPanel;
