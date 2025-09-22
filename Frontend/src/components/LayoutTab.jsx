import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { safeGet, safeArray, normalizeAnalysisResults } from '../utils/dataUtils';

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

  // 데이터 정규화 - CIM과 레거시 응답 모두 처리
  const normalizedResults = normalizeAnalysisResults(analysisResults);

  // 정규화된 데이터 추출
  const ocrResults = normalizedResults.ocrResults || [];
  const aiResults = normalizedResults.aiResults || [];
  const stats = normalizedResults.stats || {};
  const layoutImageUrl = normalizedResults.layoutImageUrl || '';
  const jsonUrl = normalizedResults.jsonUrl || '';

  const handleImageError = () => {
    setImageError(true);
  };

  return (
    <div className="layout-content">
      {/* 레이아웃 이미지 */}
      <div className="layout-section">
        <h4>🔍 레이아웃 분석 결과</h4>
        {layoutImageUrl && !imageError ? (
          <div className="layout-image-container">
            <img
              src={layoutImageUrl}
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
            {normalizedResults.cimData && (
              <p className="fallback-note">CIM 데이터는 텍스트 탭에서 확인할 수 있습니다.</p>
            )}
          </div>
        )}
      </div>

      {/* OCR 결과 요약 */}
      {ocrResults.length > 0 && (
        <div className="ocr-summary">
          <h4>📝 감지된 텍스트 요소</h4>
          <div className="ocr-stats">
            <div className="stat-item">
              <span className="stat-label">총 텍스트 블록:</span>
              <span className="stat-value">{ocrResults.length}개</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">총 문자 수:</span>
              <span className="stat-value">{stats.total_characters || 0}자</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">평균 신뢰도:</span>
              <span className="stat-value">
                {stats.average_confidence
                  ? `${(stats.average_confidence * 100).toFixed(1)}%`
                  : 'N/A'
                }
              </span>
            </div>
          </div>

          {/* 바운딩 박스 정보 */}
          <div className="bbox-info">
            <h5>📦 바운딩 박스 정보</h5>
            <div className="bbox-stats">
              <div className="stat-item">
                <span className="stat-label">좌표 정보가 있는 요소:</span>
                <span className="stat-value">
                  {ocrResults.filter(item => item.bbox).length}개
                </span>
              </div>
            </div>
            {ocrResults.filter(item => item.bbox).length > 0 && (
              <div className="bbox-preview">
                <p className="bbox-note">
                  ℹ️ 바운딩 박스 좌표가 감지되었습니다. 레이아웃 이미지에서 위치를 확인할 수 있습니다.
                </p>
                <details className="bbox-details">
                  <summary>바운딩 박스 상세 정보 보기</summary>
                  <div className="bbox-list">
                    {ocrResults
                      .filter(item => item.bbox && item.text)
                      .slice(0, 5) // 처음 5개만 표시
                      .map((item, index) => (
                        <div key={index} className="bbox-item">
                          <div className="bbox-text">
                            <strong>{item.text.substring(0, 30)}{item.text.length > 30 ? '...' : ''}</strong>
                          </div>
                          <div className="bbox-coords">
                            위치: ({item.bbox.x}, {item.bbox.y})
                            크기: {item.bbox.width} × {item.bbox.height}
                            {item.confidence > 0 && (
                              <span className="bbox-confidence">
                                신뢰도: {(item.confidence * 100).toFixed(1)}%
                              </span>
                            )}
                          </div>
                        </div>
                      ))
                    }
                    {ocrResults.filter(item => item.bbox && item.text).length > 5 && (
                      <div className="bbox-more">
                        ... 및 {ocrResults.filter(item => item.bbox && item.text).length - 5}개 더
                      </div>
                    )}
                  </div>
                </details>
              </div>
            )}
          </div>
        </div>
      )}

      {/* AI 분석 결과 요약 */}
      {aiResults.length > 0 && (
        <div className="ai-summary">
          <h4>🤖 AI 분석 요소</h4>
          <div className="ai-stats">
            <div className="stat-item">
              <span className="stat-label">AI 분석 항목:</span>
              <span className="stat-value">{aiResults.length}개</span>
            </div>
          </div>
        </div>
      )}

      {/* CIM 데이터 요약 (OCR/AI 결과가 없을 때) */}
      {ocrResults.length === 0 && aiResults.length === 0 && normalizedResults.cimData && (
        <div className="cim-summary">
          <h4>📋 CIM 통합 데이터 감지됨</h4>
          <div className="cim-stats">
            <div className="stat-item">
              <span className="stat-label">데이터 상태:</span>
              <span className="stat-value">처리 완료</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">상세 내용:</span>
              <span className="stat-value">텍스트 탭에서 확인 가능</span>
            </div>
          </div>

          {/* CIM 데이터 구조 분석 */}
          <div className="cim-analysis">
            <h5>🔍 CIM 데이터 구조 분석</h5>
            <div className="structure-info">
              {(() => {
                const cimData = normalizedResults.cimData;
                const analysisInfo = [];

                // 구조화된 분석 확인 (questions 기반)
                const questions = safeGet(cimData, 'questions');
                if (Array.isArray(questions) && questions.length > 0) {
                  analysisInfo.push(`📚 구조화된 분석: ${questions.length}개 문제 감지`);

                  // 각 문제별 내용 확인
                  let totalChoices = 0;
                  let totalImages = 0;
                  let totalExplanations = 0;

                  questions.forEach(question => {
                    const content = safeGet(question, 'question_content', {});
                    if (content.choices) totalChoices += content.choices.length;
                    if (content.images) totalImages += content.images.length;
                    if (content.explanations) totalExplanations += content.explanations.length;
                  });

                  if (totalChoices > 0) analysisInfo.push(`🔘 총 선택지: ${totalChoices}개`);
                  if (totalImages > 0) analysisInfo.push(`🖼️ 이미지/차트: ${totalImages}개`);
                  if (totalExplanations > 0) analysisInfo.push(`💡 설명/해설: ${totalExplanations}개`);
                }

                // 기본 레이아웃 분석 확인
                if (safeGet(cimData, 'document_structure.layout_analysis.elements') ||
                    safeGet(cimData, 'layout_analysis.elements')) {
                  const elements = safeGet(cimData, 'document_structure.layout_analysis.elements') ||
                                 safeGet(cimData, 'layout_analysis.elements');
                  if (Array.isArray(elements)) {
                    analysisInfo.push(`📦 기본 레이아웃 요소 ${elements.length}개 감지`);
                  }
                }

                // 문서 정보 확인
                const docInfo = safeGet(cimData, 'document_info');
                if (docInfo) {
                  if (docInfo.total_questions) {
                    analysisInfo.push(`📊 문서 정보: 총 ${docInfo.total_questions}개 문제`);
                  }
                  if (docInfo.layout_type) {
                    analysisInfo.push(`📄 레이아웃 유형: ${docInfo.layout_type}`);
                  }
                }

                // AI 분석 확인
                if (questions && questions.some(q => safeGet(q, 'ai_analysis'))) {
                  analysisInfo.push('🤖 구조화된 AI 분석 결과 포함');
                } else if (safeGet(cimData, 'ai_analysis') || safeGet(cimData, 'document_structure.ai_analysis')) {
                  analysisInfo.push('🤖 기본 AI 분석 결과 포함');
                }

                // 메타데이터 확인
                const metadata = safeGet(cimData, 'metadata');
                if (metadata && metadata.conversion_source) {
                  analysisInfo.push(`⚙️ 분석 엔진: ${metadata.conversion_source}`);
                }

                return analysisInfo.length > 0 ? (
                  <ul className="analysis-list">
                    {analysisInfo.map((info, index) => (
                      <li key={index}>{info}</li>
                    ))}
                  </ul>
                ) : (
                  <p>📋 CIM 구조 분석 중...</p>
                );
              })()
              }
            </div>
          </div>

          <p className="cim-note">
            ℹ️ CIM 통합 데이터가 감지되었습니다.
            자세한 내용은 "텍스트 편집" 탭에서 확인하거나 "CIM→텍스트" 변환을 사용해보세요.
          </p>
        </div>
      )}

      {/* JSON 다운로드 */}
      {jsonUrl && (
        <div className="download-section">
          <h4>📄 원시 데이터 다운로드</h4>
          <a
            href={jsonUrl}
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

// PropTypes 정의
LayoutTab.propTypes = {
  analysisResults: PropTypes.shape({
    layoutImageUrl: PropTypes.string,
    jsonUrl: PropTypes.string,
    stats: PropTypes.object,
    ocrResults: PropTypes.array,
    aiResults: PropTypes.array,
    cimData: PropTypes.oneOfType([
      PropTypes.object,
      PropTypes.string
    ]),
    formattedText: PropTypes.string
  })
};

LayoutTab.defaultProps = {
  analysisResults: null
};

export default LayoutTab;
