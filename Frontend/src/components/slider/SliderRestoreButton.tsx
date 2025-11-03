// src/components/slider/SliderRestoreButton.tsx
import React from 'react'

interface SliderRestoreButtonProps {
  onClick: () => void
  pageCount?: number
}

const SliderRestoreButton: React.FC<SliderRestoreButtonProps> = ({ 
  onClick, 
  pageCount = 0 
}) => {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '8px',
        width: '100%',
        height: '100%',
        background: 'linear-gradient(135deg, #00BCD4, #00ACC1)',
        color: 'white',
        border: 'none',
        cursor: 'pointer',
        position: 'relative',
        transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        boxShadow: '2px 0 8px rgba(0, 0, 0, 0.1)'
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateX(2px)'
        e.currentTarget.style.boxShadow = '4px 0 12px rgba(0, 0, 0, 0.15)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateX(0)'
        e.currentTarget.style.boxShadow = '2px 0 8px rgba(0, 0, 0, 0.1)'
      }}
    >
      <div style={{ fontSize: '24px', animation: 'pulse 2s infinite' }}>⏵</div>
      <div style={{
        writingMode: 'vertical-rl',
        fontSize: '12px',
        fontWeight: 600,
        letterSpacing: '2px'
      }}>
        페이지
      </div>
      {pageCount > 0 && (
        <div style={{
          position: 'absolute',
          top: '8px',
          right: '4px',
          background: 'rgba(255, 255, 255, 0.9)',
          color: '#00BCD4',
          fontSize: '10px',
          fontWeight: 'bold',
          padding: '2px 6px',
          borderRadius: '10px',
          boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)'
        }}>
          {pageCount}
        </div>
      )}
    </button>
  )
}

export default SliderRestoreButton
