import React from 'react';

const AnalysisProgress = ({ progress, status }) => {
  return (
    <div className="analysis-progress">
      <div className="progress-header">
        <span className="progress-label">분석 진행률</span>
        <span className="progress-percentage">{progress}%</span>
      </div>
      
      <div className="progress-container">
        <div 
          className="progress-bar"
          style={{ width: `${progress}%` }}
        ></div>
      </div>
      
      <div className="status-text">
        <span className="status-icon">⚡</span>
        {status}
      </div>
    </div>
  );
};

export default AnalysisProgress;
