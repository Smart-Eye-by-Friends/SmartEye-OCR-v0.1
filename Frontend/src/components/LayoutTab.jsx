import React, { useState } from 'react';

const LayoutTab = ({ analysisResults }) => {
  const [imageError, setImageError] = useState(false);

  if (!analysisResults) {
    return (
      <div className="no-result">
        <p>레이아웃 분석 결과가 없습니다.</p>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  const handleImageError = () => {
    setImageError(true);
  };

  return (
    <div className="layout-content">
      {/* 레이아웃 이미지 */}
      <div className="layout-section">
        <h4>🔍 레이아웃 분석 결과</h4>
        {analysisResults.layoutImageUrl && !imageError ? (
          <div className="layout-image-container">
            <img 
              src={analysisResults.layoutImageUrl} 
              alt="레이아웃 분석 결과"
              onError={handleImageError}
              className="layout-image"
            />
            <p className="image-caption">
              감지된 요소들이 색상별로 표시되어 있습니다
            </p>
          </div>
        ) : (
          <div className="no-image">
            <div className="no-image-icon">🖼️</div>
            <p>레이아웃 이미지를 불러올 수 없습니다.</p>
          </div>
        )}
      </div>

      {/* OCR 결과 요약 */}
      {analysisResults.ocrResults && analysisResults.ocrResults.length > 0 && (
        <div className="ocr-summary">
          <h4>📝 감지된 텍스트 요소</h4>
          <div className="ocr-stats">
            <div className="stat-item">
              <span className="stat-label">총 텍스트 블록:</span>
              <span className="stat-value">{analysisResults.ocrResults.length}개</span>
            </div>
            {analysisResults.stats && (
              <>
                <div className="stat-item">
                  <span className="stat-label">총 문자 수:</span>
                  <span className="stat-value">{analysisResults.stats.total_characters || 0}자</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">평균 신뢰도:</span>
                  <span className="stat-value">
                    {analysisResults.stats.average_confidence 
                      ? `${(analysisResults.stats.average_confidence * 100).toFixed(1)}%`
                      : 'N/A'
                    }
                  </span>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* AI 분석 결과 요약 */}
      {analysisResults.aiResults && analysisResults.aiResults.length > 0 && (
        <div className="ai-summary">
          <h4>🤖 AI 분석 요소</h4>
          <div className="ai-stats">
            <div className="stat-item">
              <span className="stat-label">AI 분석 항목:</span>
              <span className="stat-value">{analysisResults.aiResults.length}개</span>
            </div>
          </div>
        </div>
      )}

      {/* JSON 다운로드 */}
      {analysisResults.jsonUrl && (
        <div className="download-section">
          <h4>📄 원시 데이터 다운로드</h4>
          <a 
            href={analysisResults.jsonUrl}
            download="analysis_result.json"
            className="download-btn"
          >
            📥 JSON 파일 다운로드
          </a>
        </div>
      )}
    </div>
  );
};

export default LayoutTab;
