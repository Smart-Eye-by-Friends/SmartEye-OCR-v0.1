import React from 'react';

const ModelSelector = ({ selectedModel, onModelChange }) => {
  const models = [
    { 
      value: 'SmartEyeSsen', 
      label: 'SmartEyeSsen (권장)', 
      description: '한국어 학습지에 최적화된 모델' 
    },
    { 
      value: 'docstructbench', 
      label: 'DocStructBench',
      description: '일반적인 문서 구조 분석' 
    },
    { 
      value: 'doclaynet_docsynth', 
      label: 'DocLayNet-DocSynth',
      description: '복잡한 레이아웃 분석에 특화' 
    },
    { 
      value: 'docsynth300k', 
      label: 'DocSynth300K',
      description: '대용량 학습 데이터 기반 모델' 
    }
  ];

  return (
    <div className="model-selection">
      <label htmlFor="model-select">🧠 AI 모델 선택</label>
      <select
        id="model-select"
        value={selectedModel}
        onChange={(e) => onModelChange(e.target.value)}
        className="model-select"
      >
        {models.map(model => (
          <option key={model.value} value={model.value}>
            {model.label}
          </option>
        ))}
      </select>
      
      {/* 선택된 모델 설명 */}
      <div className="model-description">
        {models.find(m => m.value === selectedModel)?.description}
      </div>
    </div>
  );
};

export default ModelSelector;
