// src/components/editor/EditorPanel.tsx
import React from 'react'

const EditorPanel: React.FC = () => {
  return (
    <div style={{ padding: '20px' }}>
      <h2>✏️ Editor Panel</h2>
      <p>텍스트 편집 및 AI 통계</p>
      
      <div style={{ marginTop: '20px' }}>
        <div style={{
          padding: '16px',
          background: '#F5F5F5',
          borderRadius: '8px',
          marginBottom: '12px'
        }}>
          <strong>📝 텍스트 편집 탭</strong>
          <p style={{ fontSize: '14px', marginTop: '8px' }}>TinyMCE 에디터 영역</p>
        </div>

        <div style={{
          padding: '16px',
          background: '#F5F5F5',
          borderRadius: '8px'
        }}>
          <strong>📊 AI 통계 탭</strong>
          <p style={{ fontSize: '14px', marginTop: '8px' }}>분석 결과 통계</p>
        </div>
      </div>
    </div>
  )
}

export default EditorPanel
