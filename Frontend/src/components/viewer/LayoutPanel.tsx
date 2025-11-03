// src/components/viewer/LayoutPanel.tsx
import React from 'react'

const LayoutPanel: React.FC = () => {
  return (
    <div className="layout-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
      <h2>🖼️ Layout Viewer</h2>
      <p style={{ color: '#666', fontSize: '14px', textAlign: 'center' }}>
        이미지 뷰어 및 바운딩 박스 표시 영역입니다.
      </p>
      <div style={{ marginTop: '20px', padding: '40px', background: '#F9F9F9', borderRadius: '8px', border: '1px solid #E0E0E0' }}>
        <p style={{ margin: 0, fontSize: '13px', color: '#999', textAlign: 'center' }}>
          📷 이미지 뷰어<br />
          (Phase 3에서 구현)
        </p>
      </div>
    </div>
  )
}

export default LayoutPanel
