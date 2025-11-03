// src/components/sidebar/Sidebar.tsx
import React from 'react'

const Sidebar: React.FC = () => {
  return (
    <div className="sidebar" style={{ padding: '20px' }}>
      <h2>📂 Sidebar</h2>
      <p style={{ color: '#666', fontSize: '14px' }}>
        문서 타입 선택, 모델 선택, 분석 설정 등이 들어갈 영역입니다.
      </p>
      <div style={{ marginTop: '20px', padding: '12px', background: '#F5F5F5', borderRadius: '4px' }}>
        <p style={{ margin: 0, fontSize: '13px' }}>
          ✅ 임시 컴포넌트<br />
          Phase 2에서 구현 예정
        </p>
      </div>
    </div>
  )
}

export default Sidebar
