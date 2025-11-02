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
            value="cim"
            checked={analysisMode === 'cim'}
            onChange={(e) => onModeChange(e.target.value)}
          />
          <div className="radio-content">
            <span className="radio-title">CIM 통합 분석 (권장)</span>
            <small className="radio-description">
              Circuit Integration Management 시스템을 통한 완전 통합 분석
            </small>
          </div>
        </label>

        <label className="radio-label">
          <input
            type="radio"
            name="analysis-mode"
            value="basic"
            checked={analysisMode === 'basic'}
            onChange={(e) => onModeChange(e.target.value)}
          />
          <div className="radio-content">
            <span className="radio-title">기본 분석</span>
            <small className="radio-description">
              표준 OCR 및 레이아웃 분석 (레거시 모드)
            </small>
          </div>
        </label>
      </div>
    </div>
  );
};

export default AnalysisModeSelector;
