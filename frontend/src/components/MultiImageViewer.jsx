import React, { useState } from 'react';
import PageThumbnailList from './PageThumbnailList';
import MainImageViewer from './MainImageViewer';

const MultiImageViewer = ({ 
  images = [], 
  selectedPageIndex, 
  onPageSelect, 
  onImagesLoad 
}) => {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className="multi-image-viewer">
      {/* 좌측 페이지 썸네일 사이드바 */}
      <div className={`page-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-header">
          <h3>페이지</h3>
          <button 
            className="collapse-btn"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            title={sidebarCollapsed ? '사이드바 열기' : '사이드바 닫기'}
          >
            {sidebarCollapsed ? '▶' : '◀'}
          </button>
        </div>
        
        {!sidebarCollapsed && (
          <div className="sidebar-content">
            <div className="page-count">
              총 {images.length}페이지
            </div>
            
            <PageThumbnailList
              images={images}
              selectedPageIndex={selectedPageIndex}
              onPageSelect={onPageSelect}
              onImagesLoad={onImagesLoad}
            />
          </div>
        )}
      </div>

      {/* 메인 이미지 뷰어 */}
      <div className="main-viewer-container">
        <MainImageViewer
          images={images}
          selectedPageIndex={selectedPageIndex}
          onPageSelect={onPageSelect}
        />
      </div>
    </div>
  );
};

export default MultiImageViewer;