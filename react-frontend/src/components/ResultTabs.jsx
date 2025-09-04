import React from 'react';
import LayoutTab from './LayoutTab';
import StatsTab from './StatsTab';
import TextEditorTab from './TextEditorTab';
import AITab from './AITab';
import StructuredTab from './StructuredTab';

const ResultTabs = ({
  activeTab,
  onTabChange,
  analysisResults,
  structuredResult,
  formattedText,
  editableText,
  onTextChange,
  onSaveText,
  onResetText,
  onDownloadText,
  onCopyText,
  onSaveAsWord,
  isWordSaving
}) => {
  const tabs = [
    { 
      id: 'layout', 
      label: '레이아웃 분석', 
      icon: '🔍',
      description: '감지된 요소들의 위치와 구조'
    },
    { 
      id: 'stats', 
      label: '분석 통계', 
      icon: '📊',
      description: '분석 결과 요약 정보'
    },
    { 
      id: 'text', 
      label: '텍스트 편집', 
      icon: '📝',
      description: 'OCR 결과 텍스트 편집'
    },
    { 
      id: 'ai', 
      label: 'AI 설명', 
      icon: '🤖',
      description: 'AI 기반 이미지 분석'
    },
    { 
      id: 'structured', 
      label: '문제별 정리', 
      icon: '📋',
      description: '구조화된 문제별 분석'
    }
  ];

  const renderTabContent = () => {
    if (!analysisResults && !structuredResult) {
      return (
        <div className="no-results">
          <div className="no-results-icon">📷</div>
          <h3>분석 결과가 없습니다</h3>
          <p>왼쪽에서 이미지를 업로드하고 분석을 시작해주세요.</p>
        </div>
      );
    }

    switch (activeTab) {
      case 'layout':
        return <LayoutTab analysisResults={analysisResults} />;
      case 'stats':
        return <StatsTab analysisResults={analysisResults} />;
      case 'text':
        return (
          <TextEditorTab
            formattedText={formattedText}
            editableText={editableText}
            onTextChange={onTextChange}
            onSaveText={onSaveText}
            onResetText={onResetText}
            onDownloadText={onDownloadText}
            onCopyText={onCopyText}
            onSaveAsWord={onSaveAsWord}
            isWordSaving={isWordSaving}
          />
        );
      case 'ai':
        return <AITab analysisResults={analysisResults} />;
      case 'structured':
        return <StructuredTab structuredResult={structuredResult} />;
      default:
        return <div>탭을 선택해주세요.</div>;
    }
  };

  return (
    <div className="results-container">
      <div className="tabs">
        {tabs.map(tab => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => onTabChange(tab.id)}
            title={tab.description}
          >
            <span className="tab-icon">{tab.icon}</span>
            <span className="tab-label">{tab.label}</span>
          </button>
        ))}
      </div>

      <div className="tab-content">
        {renderTabContent()}
      </div>
    </div>
  );
};

export default ResultTabs;
