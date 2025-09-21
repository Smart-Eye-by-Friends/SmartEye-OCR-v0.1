import React from 'react';
import PropTypes from 'prop-types';
import { safeGet, safeArray, normalizeAnalysisResults } from '../utils/dataUtils';

// 숫자 안전 처리 함수
const safeNumber = (value, defaultValue = 0) => {
  const num = typeof value === 'number' ? value : parseFloat(value);
  return isNaN(num) ? defaultValue : num;
};

const StatsTab = ({ analysisResults }) => {
  // 로딩 상태 처리
  if (!analysisResults) {
    return (
      <div className="no-result">
        <div className="loading-state">
          <p>📊 분석 데이터를 로딩 중...</p>
        </div>
      </div>
    );
  }

  // 데이터 정규화 - CIM과 레거시 응답 모두 처리
  const normalizedResults = normalizeAnalysisResults(analysisResults);

  // 분석 데이터가 없는 경우
  if (!normalizedResults.stats && !normalizedResults.ocrResults.length && !normalizedResults.aiResults.length && !normalizedResults.cimData) {
    return (
      <div className="no-result">
        <p>분석 통계가 없습니다.</p>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  // 정규화된 데이터 추출
  const stats = normalizedResults.stats || {};
  const ocrResults = normalizedResults.ocrResults || [];
  const aiResults = normalizedResults.aiResults || [];

  // 통계 데이터 안전 처리
  const totalElements = safeNumber(stats.total_elements, 0);
  const totalCharacters = safeNumber(stats.total_characters, 0);
  const averageConfidence = safeNumber(stats.average_confidence);
  const processingTime = safeNumber(stats.processing_time);
  const elementCounts = safeGet(stats, 'element_counts', {});

  return (
    <div className="stats-content">
      <h4>📊 분석 통계</h4>
      
      {/* 전체 통계 */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">📝</div>
          <div className="stat-info">
            <div className="stat-number">{totalElements.toLocaleString()}</div>
            <div className="stat-label">총 감지 요소</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">🔤</div>
          <div className="stat-info">
            <div className="stat-number">{totalCharacters.toLocaleString()}</div>
            <div className="stat-label">총 문자 수</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">📈</div>
          <div className="stat-info">
            <div className="stat-number">
              {averageConfidence > 0
                ? `${(averageConfidence * 100).toFixed(1)}%`
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
              {processingTime > 0
                ? `${processingTime.toFixed(2)}초`
                : 'N/A'
              }
            </div>
            <div className="stat-label">처리 시간</div>
          </div>
        </div>
      </div>

      {/* 요소별 통계 */}
      {Object.keys(elementCounts).length > 0 && (
        <div className="element-stats">
          <h5>📋 요소별 감지 통계</h5>
          <div className="element-grid">
            {Object.entries(elementCounts)
              .filter(([element, count]) => element && count != null)
              .sort(([,a], [,b]) => b - a) // 개수 순으로 정렬
              .map(([element, count]) => (
                <div key={element} className="element-item">
                  <span className="element-name">{element}</span>
                  <span className="element-count">{safeNumber(count, 0)}개</span>
                </div>
              ))}
          </div>
        </div>
      )}

      {/* OCR 상세 통계 */}
      {ocrResults.length > 0 && (
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

                  // 유효한 confidence 값만 필터링
                  const validResults = ocrResults.filter(result => {
                    const confidence = safeNumber(result.confidence, -1);
                    return confidence >= 0 && confidence <= 1;
                  });

                  if (validResults.length === 0) {
                    return (
                      <div className="no-confidence-data">
                        <span>신뢰도 데이터를 사용할 수 없습니다.</span>
                      </div>
                    );
                  }

                  return ranges.map(range => {
                    const count = validResults.filter(result => {
                      const confidence = safeNumber(result.confidence, 0);
                      return confidence >= range.min && (range.max === 1.0 ? confidence <= range.max : confidence < range.max);
                    }).length;
                    const percentage = validResults.length > 0 ? (count / validResults.length) * 100 : 0;

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
      {aiResults.length > 0 && (
        <div className="ai-stats">
          <h5>🤖 AI 분석 통계</h5>
          <div className="detail-stats">
            <div className="detail-item">
              <span className="detail-label">AI 분석 항목:</span>
              <span className="detail-value">{aiResults.length}개</span>
            </div>

            {/* AI 분석 타입별 통계 */}
            {(() => {
              const typeCount = {};
              aiResults.forEach(result => {
                const type = safeGet(result, 'type') || safeGet(result, 'element_type') || '기타';
                typeCount[type] = (typeCount[type] || 0) + 1;
              });

              if (Object.keys(typeCount).length > 1) {
                return (
                  <div className="ai-type-distribution">
                    <span className="detail-label">분석 타입별:</span>
                    <div className="type-list">
                      {Object.entries(typeCount).map(([type, count]) => (
                        <span key={type} className="type-item">
                          {type}: {count}개
                        </span>
                      ))}
                    </div>
                  </div>
                );
              }
              return null;
            })()}
          </div>
        </div>
      )}

      {/* 추가 메타데이터 */}
      {(() => {
        const metadata = safeGet(stats, 'metadata', {});
        const validMetadata = Object.entries(metadata).filter(([key, value]) =>
          key && value != null && value !== ''
        );

        if (validMetadata.length === 0) return null;

        return (
          <div className="metadata">
            <h5>ℹ️ 추가 정보</h5>
            <div className="metadata-grid">
              {validMetadata.map(([key, value]) => (
                <div key={key} className="metadata-item">
                  <span className="metadata-key">{key}:</span>
                  <span className="metadata-value">
                    {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        );
      })()}
    </div>
  );
};

// PropTypes 정의
StatsTab.propTypes = {
  analysisResults: PropTypes.shape({
    stats: PropTypes.object,
    ocrResults: PropTypes.array,
    aiResults: PropTypes.array,
    layoutImageUrl: PropTypes.string,
    jsonUrl: PropTypes.string,
    cimData: PropTypes.object,
    formattedText: PropTypes.string
  })
};

StatsTab.defaultProps = {
  analysisResults: null
};

export default StatsTab;
