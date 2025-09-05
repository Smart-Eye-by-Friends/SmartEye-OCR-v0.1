import React, { useState, useRef } from 'react';

const ImageLoader = ({ onImageLoad }) => {
  const [preview, setPreview] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef(null);

  const handleFileSelect = (file) => {
    if (file && file.type.startsWith('image/')) {
      const previewUrl = URL.createObjectURL(file);
      setPreview(previewUrl);
      onImageLoad(file);
    } else {
      alert('이미지 파일만 업로드 가능합니다.');
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      handleFileSelect(file);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFileSelect(files[0]);
    }
  };

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  const removeImage = () => {
    setPreview(null);
    onImageLoad(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <div className="image-loader">
      <div
        className={`upload-area ${isDragging ? 'dragging' : ''}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={handleClick}
      >
        {preview ? (
          <div className="image-preview">
            <img src={preview} alt="업로드된 이미지" />
            <div className="image-actions">
              <button 
                type="button"
                className="change-image-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  handleClick();
                }}
              >
                📷 다른 이미지 선택
              </button>
              <button 
                type="button"
                className="remove-image-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  removeImage();
                }}
              >
                🗑️ 제거
              </button>
            </div>
          </div>
        ) : (
          <div className="upload-placeholder">
            <div className="upload-icon">📷</div>
            <h3>이미지 업로드</h3>
            <p>이미지를 여기에 드래그하거나 클릭하여 업로드</p>
            <p className="upload-hint">JPG, PNG, GIF 파일 지원 (최대 10MB)</p>
          </div>
        )}
      </div>
      
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />
    </div>
  );
};

export default ImageLoader;
