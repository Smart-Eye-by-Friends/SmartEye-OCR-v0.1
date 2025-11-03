// src/components/layout/MainLayout.tsx
import React, { useState } from 'react'
import Sidebar from '../sidebar/Sidebar'
import PageSlider from '../slider/PageSlider'
import SliderRestoreButton from '../slider/SliderRestoreButton'
import LayoutPanel from '../viewer/LayoutPanel'
import EditorPanel from '../editor/EditorPanel'
import { useGridLayout } from '../../hooks/useGridLayout'
import '../../styles/grid.css'

const MainLayout: React.FC = () => {
  const { isSliderCollapsed, closeSlider, openSlider } = useGridLayout()
  const [pageCount] = useState(5) // 임시 페이지 개수

  return (
    <div className={`main-layout ${isSliderCollapsed ? 'slider-collapsed' : ''}`}>
      {/* Sidebar */}
      <div className="sidebar">
        <Sidebar />
      </div>

      {/* Page Slider */}
      {!isSliderCollapsed && (
        <div className="page-slider">
          <PageSlider pageCount={pageCount} onClose={closeSlider} />
        </div>
      )}

      {/* Restore Button */}
      {isSliderCollapsed && (
        <div className="slider-restore-btn">
          <SliderRestoreButton onClick={openSlider} pageCount={pageCount} />
        </div>
      )}

      {/* Layout Panel */}
      <div className="layout-panel">
        <LayoutPanel />
      </div>

      {/* Editor Panel */}
      <div className="editor-panel">
        <EditorPanel />
      </div>
    </div>
  )
}

export default MainLayout
