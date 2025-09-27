import React, { useState, useRef, useEffect } from 'react';

const MainImageViewer = ({ 
  images, 
  selectedPageIndex, 
  onPageSelect 
}) => {
  const [scale, setScale] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const viewerRef = useRef(null);
  const imageRef = useRef(null);

  const currentImage = images[selectedPageIndex];

  // 페이지 변경시 초기화
  useEffect(() => {
    setScale(1);
    setPosition({ x: 0, y: 0 });
  }, [selectedPageIndex]);

  // 줌 인/아웃
  const handleZoomIn = () => {
    setScale(prev => Math.min(prev * 1.2, 5));
  };

  const handleZoomOut = () => {
    setScale(prev => Math.max(prev / 1.2, 0.1));
  };

  const handleZoomReset = () => {
    setScale(1);
    setPosition({ x: 0, y: 0 });
  };

  const handleFitToWindow = () => {
    if (imageRef.current && viewerRef.current) {
      const imageRect = imageRef.current.getBoundingClientRect();
      const viewerRect = viewerRef.current.getBoundingClientRect();
      
      const scaleX = viewerRect.width / imageRect.width;
      const scaleY = viewerRect.height / imageRect.height;
      const newScale = Math.min(scaleX, scaleY, 1);
      
      setScale(newScale);
      setPosition({ x: 0, y: 0 });
    }
  };

  // 마우스 휠 줌
  const handleWheel = (e) => {
    if (e.ctrlKey) {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setScale(prev => Math.min(Math.max(prev * delta, 0.1), 5));
    }
  };

  // 드래그 시작
  const handleMouseDown = (e) => {
    if (scale > 1) {
      setIsDragging(true);
      setDragStart({
        x: e.clientX - position.x,
        y: e.clientY - position.y
      });
    }
  };

  // 드래그 중
  const handleMouseMove = (e) => {
    if (isDragging) {
      setPosition({
        x: e.clientX - dragStart.x,
        y: e.clientY - dragStart.y
      });
    }
  };

  // 드래그 종료
  const handleMouseUp = () => {
    setIsDragging(false);
  };

  // 페이지 네비게이션
  const handlePrevPage = () => {
    if (selectedPageIndex > 0) {
      onPageSelect(selectedPageIndex - 1);
    }
  };

  const handleNextPage = () => {
    if (selectedPageIndex < images.length - 1) {
      onPageSelect(selectedPageIndex + 1);
    }
  };

  // 키보드 네비게이션
  useEffect(() => {
    const handleKeyDown = (e) => {
      switch (e.key) {
        case 'ArrowLeft':
          handlePrevPage();
          break;
        case 'ArrowRight':
          handleNextPage();
          break;
        case '+':
        case '=':
          handleZoomIn();
          break;
        case '-':
          handleZoomOut();
          break;
        case '0':
          handleZoomReset();
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedPageIndex, images.length]);

  if (!currentImage) {
    return (
      <div className="main-image-viewer empty">
        <div className="empty-viewer">
          <div className="empty-icon">🖼️</div>
          <h3>이미지를 선택하세요</h3>
          <p>좌측에서 페이지를 선택하거나 새 파일을 추가하세요</p>
        </div>
      </div>
    );
  }

  return (
    <div className="main-image-viewer">
      {/* 툴바 */}
      <div className="viewer-toolbar">
        <div className="toolbar-left">
          <button 
            className="toolbar-btn"
            onClick={handlePrevPage}
            disabled={selectedPageIndex === 0}
          >
            ◀ 이전
          </button>
          <span className="page-indicator">
            {selectedPageIndex + 1} / {images.length}
          </span>
          <button 
            className="toolbar-btn"
            onClick={handleNextPage}
            disabled={selectedPageIndex === images.length - 1}
          >
            다음 ▶
          </button>
        </div>

        <div className="toolbar-center">
          <button className="toolbar-btn" onClick={handleZoomOut}>
            🔍-
          </button>
          <span className="zoom-level">
            {Math.round(scale * 100)}%
          </span>
          <button className="toolbar-btn" onClick={handleZoomIn}>
            🔍+
          </button>
          <button className="toolbar-btn" onClick={handleZoomReset}>
            원본
          </button>
          <button className="toolbar-btn" onClick={handleFitToWindow}>
            맞춤
          </button>
        </div>

        <div className="toolbar-right">
          <span className="image-name" title={currentImage.name}>
            {currentImage.name}
          </span>
        </div>
      </div>

      {/* 이미지 뷰어 */}
      <div
        ref={viewerRef}
        className={`image-viewer ${isDragging ? 'dragging' : ''}`}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <div
          className="image-container"
          style={{
            transform: `scale(${scale}) translate(${position.x / scale}px, ${position.y / scale}px)`,
            transformOrigin: 'center center'
          }}
        >
          <img
            ref={imageRef}
            src={currentImage.preview}
            alt={`페이지 ${selectedPageIndex + 1}`}
            className="main-image"
            draggable={false}
          />
        </div>
      </div>

      {/* 페이지 네비게이션 (이미지 위 오버레이) */}
      {images.length > 1 && (
        <>
          <button
            className="nav-arrow nav-prev"
            onClick={handlePrevPage}
            disabled={selectedPageIndex === 0}
          >
            ◀
          </button>
          <button
            className="nav-arrow nav-next"
            onClick={handleNextPage}
            disabled={selectedPageIndex === images.length - 1}
          >
            ▶
          </button>
        </>
      )}
    </div>
  );
};

export default MainImageViewer;