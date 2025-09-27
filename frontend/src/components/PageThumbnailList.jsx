import React, { useRef } from 'react';

const PageThumbnailList = ({ 
  images, 
  selectedPageIndex, 
  onPageSelect, 
  onImagesLoad 
}) => {
  const fileInputRef = useRef(null);

  const handleAddFiles = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    if (files.length > 0) {
      onImagesLoad(files);
    }
  };

  return (
    <div className="page-thumbnail-list">
      {/* 파일 추가 버튼 */}
      <div className="add-files-section">
        <button className="add-files-btn" onClick={handleAddFiles}>
          ➕ 파일 추가
        </button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*"
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
      </div>

      {/* 페이지 썸네일들 */}
      <div className="thumbnails-container">
        {images.map((image, index) => (
          <div
            key={`${image.name}-${index}`}
            className={`thumbnail-item ${
              index === selectedPageIndex ? 'selected' : ''
            }`}
            onClick={() => onPageSelect(index)}
          >
            <div className="thumbnail-wrapper">
              <img
                src={image.preview}
                alt={`페이지 ${index + 1}`}
                className="thumbnail-image"
                loading="lazy"
              />
              <div className="thumbnail-overlay">
                <div className="page-number">{index + 1}</div>
              </div>
            </div>
            <div className="thumbnail-info">
              <div className="page-name" title={image.name}>
                {image.name.length > 15 
                  ? `${image.name.substring(0, 12)}...` 
                  : image.name
                }
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* 빈 상태 */}
      {images.length === 0 && (
        <div className="empty-state">
          <div className="empty-icon">📄</div>
          <p>이미지를 추가하세요</p>
        </div>
      )}
    </div>
  );
};

export default PageThumbnailList;