import React from 'react';

const AnalysisModeSelector = ({ analysisMode, onModeChange }) => {
  return (
    <div className="analysis-mode">
      <label>📋 분석 모드</label>
      <div className="radio-group">
        <label className="radio-label">
          <input
            type="radio"
            name="analysis-mode"
            value="basic"
            checked={analysisMode === 'basic'}
            onChange={(e) => onModeChange(e.target.value)}
          />
          <div className="radio-content">
            <span className="radio-title">일반 분석</span>
            <small className="radio-description">
              기본적인 OCR 및 레이아웃 분석을 수행합니다
            </small>
          </div>
        </label>
        
        <label className="radio-label">
          <input
            type="radio"
            name="analysis-mode"
            value="structured"
            checked={analysisMode === 'structured'}
            onChange={(e) => onModeChange(e.target.value)}
          />
          <div className="radio-content">
            <span className="radio-title">구조화된 분석 (권장)</span>
            <small className="radio-description">
              문제별로 정렬된 상세 분석 결과를 제공합니다
            </small>
          </div>
        </label>
      </div>
    </div>
  );
};

export default AnalysisModeSelector;
