// src/components/slider/PageSlider.tsx
import React from 'react'

interface PageSliderProps {
  pageCount?: number
  onClose: () => void
}

const PageSlider: React.FC<PageSliderProps> = ({ pageCount = 0, onClose }) => {
  return (
    <div style={{ padding: '20px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h3>📄 페이지 ({pageCount})</h3>
        <button
          onClick={onClose}
          style={{
            background: 'none',
            border: 'none',
            fontSize: '20px',
            cursor: 'pointer',
            padding: '8px'
          }}
        >
          ⏴
        </button>
      </div>

      <div style={{
        border: '2px dashed #E0E0E0',
        borderRadius: '8px',
        padding: '40px',
        textAlign: 'center',
        marginBottom: '20px'
      }}>
        <p>파일을 드래그하거나 클릭하세요</p>
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {Array.from({ length: pageCount }, (_, i) => (
          <div
            key={i}
            style={{
              padding: '12px',
              border: '1px solid #E0E0E0',
              borderRadius: '4px',
              marginBottom: '8px',
              cursor: 'pointer'
            }}
          >
            페이지 {i + 1}
          </div>
        ))}
      </div>
    </div>
  )
}

export default PageSlider
