import React, { useState, useEffect } from 'react';
import './styles/App.css';
import ImageLoader from './components/ImageLoader';
import AnalysisProgress from './components/AnalysisProgress';
import ModelSelector from './components/ModelSelector';
import AnalysisModeSelector from './components/AnalysisModeSelector';
import ResultTabs from './components/ResultTabs';
import ErrorBoundary from './components/ErrorBoundary';
import { useAnalysis } from './hooks/useAnalysis';
import { useTextEditor } from './hooks/useTextEditor';
import {
  setupExtensionErrorHandler,
  detectProblematicExtensions,
  showExtensionWarning
} from './utils/extensionCompatibility';

function App() {
  // 상태 관리
  const [selectedImage, setSelectedImage] = useState(null);
  const [selectedModel, setSelectedModel] = useState('SmartEyeSsen');
  const [apiKey, setApiKey] = useState('');
  const [analysisMode, setAnalysisMode] = useState('cim');
  const [activeTab, setActiveTab] = useState('layout');

  // 커스텀 훅 사용
  const {
    isAnalyzing,
    progress,
    status,
    analysisResults,
    structuredResult,
    analyzeWorksheet,
    reset: resetAnalysis
  } = useAnalysis();

  const {
    formattedText,
    editableText,
    setEditableText,
    updateFormattedText,
    saveText,
    resetText,
    downloadText,
    copyText,
    saveAsWord,
    isWordSaving
  } = useTextEditor();

  // API 키 로컬 스토리지에서 불러오기 및 확장프로그램 호환성 설정
  useEffect(() => {
    // API 키 복원
    const savedApiKey = localStorage.getItem('openai_api_key');
    if (savedApiKey) {
      setApiKey(savedApiKey);
    }

    // 브라우저 확장프로그램 호환성 설정
    const cleanupExtensionHandler = setupExtensionErrorHandler();

    // 확장프로그램 감지 및 경고 (개발 환경에서만)
    if (process.env.NODE_ENV === 'development') {
      setTimeout(() => {
        const problematicExtensions = detectProblematicExtensions();
        if (problematicExtensions.length > 0) {
          showExtensionWarning(problematicExtensions);
        }
      }, 2000); // 2초 후 확장프로그램 감지 (DOM 로딩 완료 후)
    }

    // cleanup function
    return () => {
      cleanupExtensionHandler();
    };
  }, []);

  // API 키 저장
  useEffect(() => {
    if (apiKey) {
      localStorage.setItem('openai_api_key', apiKey);
    }
  }, [apiKey]);

  // 분석 결과가 있을 때 텍스트 에디터 업데이트
  useEffect(() => {
    if (analysisResults?.formattedText) {
      updateFormattedText(analysisResults.formattedText);
    }
  }, [analysisResults, updateFormattedText]);

  // 이미지 로드 핸들러
  const handleImageLoad = (imageFile) => {
    setSelectedImage(imageFile);
    resetAnalysis();
  };

  // 분석 시작 핸들러
  const handleAnalyze = async () => {
    if (!selectedImage) {
      alert('이미지를 먼저 업로드해주세요.');
      return;
    }

    await analyzeWorksheet({
      image: selectedImage,
      model: selectedModel,
      apiKey: apiKey,
      mode: analysisMode
    });
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>🔍 SmartEyeSsen 학습지 분석</h1>
        <p>AI 기반 학습지 OCR 및 구조 분석 시스템</p>
      </header>

      <main className="main-layout">
        {/* 왼쪽 패널: 업로드 및 설정 */}
        <div className="left-panel">
          <div className="panel-section">
            <h2>📤 이미지 업로드</h2>
            <ImageLoader 
              onImageLoad={handleImageLoad} 
            />
          </div>

          <div className="panel-section">
            <h2>⚙️ 분석 설정</h2>
            
            {/* 모델 선택 */}
            <ModelSelector
              selectedModel={selectedModel}
              onModelChange={setSelectedModel}
            />

            {/* 분석 모드 선택 */}
            <AnalysisModeSelector
              analysisMode={analysisMode}
              onModeChange={setAnalysisMode}
            />

            {/* API 키 입력 */}
            <div className="api-key-input">
              <label htmlFor="api-key">
                🔑 OpenAI API 키 (선택사항)
                <span className="tooltip">AI 이미지 분석을 위해 필요합니다</span>
              </label>
              <input
                id="api-key"
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="sk-..."
              />
            </div>

            {/* 분석 진행률 */}
            {isAnalyzing && (
              <AnalysisProgress progress={progress} status={status} />
            )}

            {/* 분석 버튼 */}
            <button
              className="analyze-btn"
              onClick={handleAnalyze}
              disabled={isAnalyzing || !selectedImage}
            >
              {isAnalyzing ? (
                <>
                  <span className="loading-spinner"></span>
                  분석 중...
                </>
              ) : (
                <>
                  🚀 분석 시작
                </>
              )}
            </button>
          </div>
        </div>

        {/* 오른쪽 패널: 결과 표시 */}
        <div className="right-panel">
          <div className="panel-section">
            <h2>📊 분석 결과</h2>
            <ErrorBoundary
              onError={(error, errorInfo) => {
                console.error('ResultTabs 에러:', error, errorInfo);
              }}
              onReset={() => {
                setActiveTab('layout');
                resetAnalysis();
              }}
            >
              <ResultTabs
                activeTab={activeTab}
                onTabChange={setActiveTab}
                analysisResults={analysisResults}
                structuredResult={structuredResult}
                formattedText={formattedText}
                editableText={editableText}
                onTextChange={setEditableText}
                onSaveText={saveText}
                onResetText={resetText}
                onDownloadText={downloadText}
                onCopyText={copyText}
                onSaveAsWord={saveAsWord}
                isWordSaving={isWordSaving}
              />
            </ErrorBoundary>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
