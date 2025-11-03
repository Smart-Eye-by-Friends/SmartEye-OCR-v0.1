// src/components/slider/SliderRestoreButton.tsx
import React from 'react'

interface SliderRestoreButtonProps {
  onClick: () => void
  pageCount?: number
}

const SliderRestoreButton: React.FC<SliderRestoreButtonProps> = ({ onClick, pageCount = 0 }) => {
  return (
    <div
      className="slider-restore-btn"
      onClick={onClick}
      style={{
        position: 'relative',
        transition: 'all 0.2s',
      }}
      title="페이지 목록 열기"
    >
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
        <span style={{ fontSize: '20px' }}>⏵</span>
        <span
          style={{
            writingMode: 'vertical-rl',
            fontSize: '12px',
            fontWeight: '600',
            color: '#666',
            letterSpacing: '2px',
          }}
        >
          페이지
        </span>
        {pageCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '8px',
              right: '8px',
              background: 'var(--primary-color)',
              color: 'white',
              borderRadius: '10px',
              padding: '2px 6px',
              fontSize: '10px',
              fontWeight: '700',
            }}
          >
            {pageCount}
          </span>
        )}
      </div>
    </div>
  )
}

export default SliderRestoreButton
