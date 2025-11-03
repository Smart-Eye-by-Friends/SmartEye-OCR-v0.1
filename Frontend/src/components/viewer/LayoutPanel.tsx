// src/components/viewer/LayoutPanel.tsx
import React from 'react'

const LayoutPanel: React.FC = () => {
  return (
    <div style={{ 
      padding: '20px', 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center',
      height: '100%'
    }}>
      <h2>🖼️ Layout Panel</h2>
      <p>이미지 뷰어 및 바운딩 박스 오버레이</p>
    </div>
  )
}

export default LayoutPanel
