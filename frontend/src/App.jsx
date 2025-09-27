import React, { useState, useEffect } from 'react';
import './styles/App.css';
import MultiFileLoader from './components/MultiFileLoader';
import MultiImageViewer from './components/MultiImageViewer';
import AnalysisProgress from './components/AnalysisProgress';
import ModelSelector from './components/ModelSelector';
import AnalysisModeSelector from './components/AnalysisModeSelector';
import ResultTabs from './components/ResultTabs';
import { useAnalysis } from './hooks/useAnalysis';
import { useTextEditor } from './hooks/useTextEditor';

function App() {
  // 상태 관리
  const [images, setImages] = useState([]);
  const [selectedPageIndex, setSelectedPageIndex] = useState(0);
  const [selectedModel, setSelectedModel] = useState('SmartEyeSsen');
  const [apiKey, setApiKey] = useState('');
  const [analysisMode, setAnalysisMode] = useState('basic');
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
    saveText,
    resetText,
    downloadText,
    copyText,
    saveAsWord,
    isWordSaving
  } = useTextEditor();

  // API 키 로컬 스토리지에서 불러오기
  useEffect(() => {
    const savedApiKey = localStorage.getItem('openai_api_key');
    if (savedApiKey) {
      setApiKey(savedApiKey);
    }
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
      setEditableText(analysisResults.formattedText);
    }
  }, [analysisResults, setEditableText]);

  // 이미지 로드 핸들러
  const handleImagesLoad = (newImages) => {
    setImages(prev => [...prev, ...newImages]);
    if (newImages.length > 0 && images.length === 0) {
      setSelectedPageIndex(0);
    }
    resetAnalysis();
  };

  // 페이지 선택 핸들러
  const handlePageSelect = (pageIndex) => {
    setSelectedPageIndex(pageIndex);
    resetAnalysis();
  };

  // 현재 선택된 이미지
  const currentImage = images[selectedPageIndex];

  // 분석 시작 핸들러
  const handleAnalyze = async () => {
    if (!currentImage) {
      alert('이미지를 먼저 업로드해주세요.');
      return;
    }

    await analyzeWorksheet({
      image: currentImage.file,
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
            <h2>📤 파일 업로드</h2>
            <MultiFileLoader 
              onFilesLoad={handleImagesLoad}
              maxFiles={50}
            />
          </div>

          <div className="panel-section">
            <h2>⚙️ 분석 설정</h2>
            
            {/* 현재 선택된 이미지 정보 */}
            {currentImage && (
              <div className="current-image-info">
                <div className="info-item">
                  <strong>선택된 페이지:</strong>
                  <span>{selectedPageIndex + 1} / {images.length}</span>
                </div>
                <div className="info-item">
                  <strong>파일명:</strong>
                  <span title={currentImage.name}>{currentImage.name}</span>
                </div>
              </div>
            )}

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
              disabled={isAnalyzing || !currentImage}
            >
              {isAnalyzing ? (
                <>
                  <span className="loading-spinner"></span>
                  분석 중...
                </>
              ) : (
                <>
                  🚀 현재 페이지 분석
                </>
              )}
            </button>
          </div>
        </div>

        {/* 중앙 패널: 이미지 뷰어 */}
        <div className="center-panel">
          <MultiImageViewer
            images={images}
            selectedPageIndex={selectedPageIndex}
            onPageSelect={handlePageSelect}
            onImagesLoad={handleImagesLoad}
          />
        </div>

        {/* 오른쪽 패널: 결과 표시 */}
        <div className="right-panel">
          <div className="panel-section">
            <h2>📊 분석 결과</h2>
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
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
