import React, { useState } from 'react';

const AITab = ({ analysisResults }) => {
  const [expandedItems, setExpandedItems] = useState(new Set());

  if (!analysisResults || !analysisResults.aiResults || analysisResults.aiResults.length === 0) {
    return (
      <div className="no-result">
        <div className="no-result-icon">🤖</div>
        <h3>AI 분석 결과가 없습니다</h3>
        <p>OpenAI API 키를 입력하고 분석을 실행하면 AI 기반 이미지 분석 결과를 확인할 수 있습니다.</p>
        <div className="ai-info">
          <h4>AI 분석 기능:</h4>
          <ul>
            <li>🖼️ 이미지 내용 자동 설명</li>
            <li>📊 표와 그래프 분석</li>
            <li>📋 문제 유형 분류</li>
            <li>🎯 핵심 내용 요약</li>
          </ul>
        </div>
      </div>
    );
  }

  const toggleExpanded = (index) => {
    const newExpanded = new Set(expandedItems);
    if (newExpanded.has(index)) {
      newExpanded.delete(index);
    } else {
      newExpanded.add(index);
    }
    setExpandedItems(newExpanded);
  };

  const getItemTypeIcon = (description) => {
    const desc = description.toLowerCase();
    if (desc.includes('table') || desc.includes('표')) return '📊';
    if (desc.includes('chart') || desc.includes('그래프')) return '📈';
    if (desc.includes('image') || desc.includes('그림')) return '🖼️';
    if (desc.includes('text') || desc.includes('텍스트')) return '📝';
    if (desc.includes('question') || desc.includes('문제')) return '❓';
    return '🤖';
  };

  const getConfidenceColor = (confidence) => {
    if (confidence >= 0.8) return '#4CAF50';
    if (confidence >= 0.6) return '#FF9800';
    return '#F44336';
  };

  return (
    <div className="ai-content">
      <div className="ai-header">
        <h4>🤖 AI 분석 결과</h4>
        <div className="ai-summary">
          <span className="summary-item">
            <strong>{analysisResults.aiResults.length}</strong>개 항목 분석됨
          </span>
          <span className="summary-item">
            평균 신뢰도: <strong>
              {(analysisResults.aiResults.reduce((sum, item) => sum + (item.confidence || 0), 0) / analysisResults.aiResults.length * 100).toFixed(1)}%
            </strong>
          </span>
        </div>
      </div>

      <div className="ai-results">
        {analysisResults.aiResults.map((item, index) => (
          <div key={index} className="ai-item">
            <div 
              className="ai-item-header"
              onClick={() => toggleExpanded(index)}
            >
              <div className="ai-item-info">
                <span className="ai-item-icon">
                  {getItemTypeIcon(item.description || '')}
                </span>
                <div className="ai-item-details">
                  <h5>AI 분석 항목 #{index + 1}</h5>
                  {item.element_type && (
                    <span className="element-type">{item.element_type}</span>
                  )}
                </div>
              </div>
              
              <div className="ai-item-meta">
                {item.confidence && (
                  <div 
                    className="confidence-badge"
                    style={{ backgroundColor: getConfidenceColor(item.confidence) }}
                  >
                    {(item.confidence * 100).toFixed(1)}%
                  </div>
                )}
                <span className="expand-icon">
                  {expandedItems.has(index) ? '▼' : '▶'}
                </span>
              </div>
            </div>

            {expandedItems.has(index) && (
              <div className="ai-item-content">
                {item.description && (
                  <div className="description-section">
                    <h6>📋 AI 분석 내용</h6>
                    <p className="ai-description">{item.description}</p>
                  </div>
                )}

                {item.coordinates && (
                  <div className="coordinates-section">
                    <h6>📍 위치 정보</h6>
                    <div className="coordinates-grid">
                      <span>X: {item.coordinates.x}px</span>
                      <span>Y: {item.coordinates.y}px</span>
                      <span>너비: {item.coordinates.width}px</span>
                      <span>높이: {item.coordinates.height}px</span>
                    </div>
                  </div>
                )}

                {item.extracted_text && (
                  <div className="extracted-text-section">
                    <h6>📝 추출된 텍스트</h6>
                    <div className="extracted-text">
                      {item.extracted_text}
                    </div>
                  </div>
                )}

                {item.analysis_metadata && (
                  <div className="metadata-section">
                    <h6>ℹ️ 추가 정보</h6>
                    <div className="metadata-grid">
                      {Object.entries(item.analysis_metadata).map(([key, value]) => (
                        <div key={key} className="metadata-row">
                          <span className="metadata-key">{key}:</span>
                          <span className="metadata-value">{String(value)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* AI 분석 팁 */}
      <div className="ai-tips">
        <h5>💡 AI 분석 활용 팁</h5>
        <ul>
          <li>신뢰도가 높은 항목일수록 정확한 분석 결과입니다</li>
          <li>이미지 품질이 좋을수록 더 정확한 AI 분석이 가능합니다</li>
          <li>복잡한 수식이나 그래프는 AI 분석과 함께 수동 검토를 권장합니다</li>
        </ul>
      </div>
    </div>
  );
};

export default AITab;
