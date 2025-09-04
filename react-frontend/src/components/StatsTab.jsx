import React from 'react';

const StatsTab = ({ analysisResults }) => {
  if (!analysisResults || !analysisResults.stats) {
    return (
      <div className="no-result">
        <p>분석 통계가 없습니다.</p>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  const { stats, ocrResults, aiResults } = analysisResults;

  return (
    <div className="stats-content">
      <h4>📊 분석 통계</h4>
      
      {/* 전체 통계 */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">📝</div>
          <div className="stat-info">
            <div className="stat-number">{stats.total_elements || 0}</div>
            <div className="stat-label">총 감지 요소</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">🔤</div>
          <div className="stat-info">
            <div className="stat-number">{stats.total_characters || 0}</div>
            <div className="stat-label">총 문자 수</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">📈</div>
          <div className="stat-info">
            <div className="stat-number">
              {stats.average_confidence 
                ? `${(stats.average_confidence * 100).toFixed(1)}%`
                : 'N/A'
              }
            </div>
            <div className="stat-label">평균 신뢰도</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">⏱️</div>
          <div className="stat-info">
            <div className="stat-number">
              {stats.processing_time 
                ? `${stats.processing_time.toFixed(2)}초`
                : 'N/A'
              }
            </div>
            <div className="stat-label">처리 시간</div>
          </div>
        </div>
      </div>

      {/* 요소별 통계 */}
      {stats.element_counts && (
        <div className="element-stats">
          <h5>📋 요소별 감지 통계</h5>
          <div className="element-grid">
            {Object.entries(stats.element_counts).map(([element, count]) => (
              <div key={element} className="element-item">
                <span className="element-name">{element}</span>
                <span className="element-count">{count}개</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* OCR 상세 통계 */}
      {ocrResults && ocrResults.length > 0 && (
        <div className="ocr-details">
          <h5>🔍 OCR 상세 분석</h5>
          <div className="detail-stats">
            <div className="detail-item">
              <span className="detail-label">텍스트 블록 수:</span>
              <span className="detail-value">{ocrResults.length}개</span>
            </div>
            
            {/* 신뢰도 분포 */}
            <div className="confidence-distribution">
              <span className="detail-label">신뢰도 분포:</span>
              <div className="confidence-bars">
                {(() => {
                  const ranges = [
                    { min: 0.9, max: 1.0, label: '90-100%', color: '#4CAF50' },
                    { min: 0.7, max: 0.9, label: '70-90%', color: '#FF9800' },
                    { min: 0.5, max: 0.7, label: '50-70%', color: '#FF5722' },
                    { min: 0.0, max: 0.5, label: '0-50%', color: '#F44336' }
                  ];
                  
                  return ranges.map(range => {
                    const count = ocrResults.filter(result => 
                      result.confidence >= range.min && result.confidence < range.max
                    ).length;
                    const percentage = ocrResults.length > 0 ? (count / ocrResults.length) * 100 : 0;
                    
                    return (
                      <div key={range.label} className="confidence-bar">
                        <span className="bar-label">{range.label}</span>
                        <div className="bar-container">
                          <div 
                            className="bar-fill"
                            style={{ 
                              width: `${percentage}%`,
                              backgroundColor: range.color 
                            }}
                          ></div>
                        </div>
                        <span className="bar-count">{count}</span>
                      </div>
                    );
                  });
                })()}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* AI 분석 통계 */}
      {aiResults && aiResults.length > 0 && (
        <div className="ai-stats">
          <h5>🤖 AI 분석 통계</h5>
          <div className="detail-stats">
            <div className="detail-item">
              <span className="detail-label">AI 분석 항목:</span>
              <span className="detail-value">{aiResults.length}개</span>
            </div>
          </div>
        </div>
      )}

      {/* 추가 메타데이터 */}
      {stats.metadata && (
        <div className="metadata">
          <h5>ℹ️ 추가 정보</h5>
          <div className="metadata-grid">
            {Object.entries(stats.metadata).map(([key, value]) => (
              <div key={key} className="metadata-item">
                <span className="metadata-key">{key}:</span>
                <span className="metadata-value">{String(value)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default StatsTab;
