// src/components/slider/PageSlider.tsx
import React from 'react'

interface PageSliderProps {
  pageCount?: number
  onClose: () => void
}

const PageSlider: React.FC<PageSliderProps> = ({ pageCount = 0, onClose }) => {
  return (
    <div className="page-slider" style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h3 style={{ margin: 0 }}>📄 페이지 목록</h3>
        <button
          onClick={onClose}
          style={{
            background: 'none',
            border: 'none',
            fontSize: '24px',
            cursor: 'pointer',
            padding: '4px 8px',
          }}
          title="Slider 닫기"
        >
          ✕
        </button>
      </div>
      
      <p style={{ color: '#666', fontSize: '14px', marginBottom: '16px' }}>
        업로드된 페이지: <strong>{pageCount}개</strong>
      </p>
      
      <div style={{ padding: '20px', background: '#F9F9F9', borderRadius: '4px', border: '2px dashed #E0E0E0', textAlign: 'center' }}>
        <p style={{ margin: 0, fontSize: '13px', color: '#999' }}>
          📤 파일 업로드 영역<br />
          (Phase 2에서 구현)
        </p>
      </div>
      
      <div style={{ marginTop: '20px' }}>
        <p style={{ fontSize: '12px', color: '#999' }}>
          ✅ 임시 컴포넌트<br />
          썸네일 리스트는 Phase 1 Task 1.4에서 구현
        </p>
      </div>
    </div>
  )
}

export default PageSlider
