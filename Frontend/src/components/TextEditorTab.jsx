/**
 * TextEditorTab - 완전히 최적화된 텍스트 에디터 탭
 * 무한 루프 방지, 성능 최적화, 방어적 코딩 패턴 적용
 */

import React, { useEffect, useCallback, useMemo, memo, useState } from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';
import { getTinyMCEExtensionSafeConfig } from '../utils/extensionCompatibility';

// 새로운 최적화 훅들 임포트
import { useTextEditor } from '../hooks/useTextEditor';
import { useStableAnalysisData } from '../hooks/useStableAnalysisData';
import { usePerformanceGuard } from '../components/PerformanceGuard';

// ===========================
// 🎯 순수 유틸리티 함수들 (메모이제이션 최적화)
// ===========================

// 에러 감지 함수 (개선된 버전)
const detectTextError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const trimmed = text.trim();
  if (trimmed.length < 3) return false;

  const errorPatterns = [
    /^(error|오류|실패|exception):/i,
    /extraction failed/i,
    /분석에 실패/,
    /처리 중 오류/,
    /데이터를 불러올 수 없/
  ];

  return errorPatterns.some(pattern => pattern.test(trimmed));
};

// 소스 설명 매핑
const sourceDescriptions = {
  high_confidence_ocr: '고신뢰도 OCR',
  all_ocr: 'OCR 분석',
  ai_analysis: 'AI 분석',
  cim_data: 'CIM 데이터',
  fallback: '백업 데이터'
};

// CIM 데이터 변환 함수
const convertCimToText = (cimData) => {
  if (!cimData) return '';

  try {
    if (typeof cimData === 'string') {
      cimData = JSON.parse(cimData);
    }

    let text = '';
    if (cimData.problems && Array.isArray(cimData.problems)) {
      text += '📝 문제 분석 결과:\n\n';
      cimData.problems.forEach((problem, index) => {
        text += `문제 ${index + 1}: ${problem.question || ''}\n`;
        if (problem.options && problem.options.length > 0) {
          problem.options.forEach((option, optIndex) => {
            text += `  ${String.fromCharCode(65 + optIndex)}. ${option}\n`;
          });
        }
        if (problem.answer) {
          text += `정답: ${problem.answer}\n`;
        }
        text += '\n';
      });
    }

    if (cimData.analysis) {
      text += '🔍 분석 정보:\n';
      text += JSON.stringify(cimData.analysis, null, 2);
    }

    return text;
  } catch (error) {
    console.error('CIM 데이터 변환 오류:', error);
    return '❌ CIM 데이터 변환에 실패했습니다.';
  }
};

// CIM 데이터 표시 컴포넌트
const CimDataDisplay = memo(({ cimData, onClose }) => {
  if (!cimData) return null;

  const displayData = typeof cimData === 'string' ? cimData : JSON.stringify(cimData, null, 2);

  return (
    <div className="cim-data-overlay">
      <div className="cim-data-content">
        <div className="cim-data-header">
          <h4>🔺 CIM 원시 데이터</h4>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>
        <pre className="cim-data-text">{displayData}</pre>
      </div>
    </div>
  );
});

CimDataDisplay.displayName = 'CimDataDisplay';
CimDataDisplay.propTypes = {
  cimData: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
  onClose: PropTypes.func.isRequired
};

// ===========================
// 🧩 메모이제이션된 서브 컴포넌트들
// ===========================

const ErrorAlert = memo(({ message, onDismiss }) => (
  <div className="error-notification" role="alert">
    <div className="error-content">
      <span className="error-icon">⚠️</span>
      <span className="error-text">{message}</span>
      <button
        className="error-dismiss"
        onClick={onDismiss}
        aria-label="알림 닫기"
      >
        ✕
      </button>
    </div>
  </div>
));

ErrorAlert.displayName = 'ErrorAlert';
ErrorAlert.propTypes = {
  message: PropTypes.string.isRequired,
  onDismiss: PropTypes.func.isRequired
};

const LoadingState = memo(() => (
  <div className="no-result">
    <div className="loading-state">
      <div className="loading-spinner"></div>
      <p>📝 텍스트 데이터를 로딩 중...</p>
    </div>
  </div>
));

LoadingState.displayName = 'LoadingState';

const EmptyState = memo(({ onLoadFallback, hasAlternativeData }) => (
  <div className="no-result">
    <div className="no-result-icon">📝</div>
    <h3>텍스트 결과가 없습니다</h3>
    <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
    {hasAlternativeData && (
      <button
        className="load-fallback-btn"
        onClick={onLoadFallback}
        style={{
          marginTop: '10px',
          padding: '8px 16px',
          backgroundColor: '#007bff',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
      >
        📋 사용 가능한 데이터 불러오기
      </button>
    )}
  </div>
));

EmptyState.displayName = 'EmptyState';
EmptyState.propTypes = {
  onLoadFallback: PropTypes.func,
  hasAlternativeData: PropTypes.bool
};

const EditorActions = memo(({
  isEditing,
  isProcessing,
  hasContent,
  hasError,
  hasAlternativeData,
  onEditToggle,
  onReset,
  onCopy,
  onConvertCim,
  onToggleCimData,
  onDownload,
  onSaveAsWord,
  showCimData,
  isConverting,
  isWordSaving
}) => (
  <div className="editor-actions">
    <button
      className="action-btn edit-btn"
      onClick={onEditToggle}
      disabled={isProcessing}
    >
      {isEditing ? '📖 읽기 모드' : '✏️ 편집 모드'}
    </button>

    <button
      className="action-btn reset-btn"
      onClick={onReset}
      disabled={isProcessing || (!hasContent && !hasAlternativeData)}
      title={hasError ? '대체 데이터로 복원' : '원본으로 복원'}
    >
      🔄 {hasError ? '대체 데이터로 복원' : '원본으로 복원'}
    </button>

    <button
      className="action-btn copy-btn"
      onClick={onCopy}
      disabled={!hasContent}
    >
      📋 복사
    </button>

    <button
      className="action-btn download-btn"
      onClick={onDownload}
      disabled={!hasContent || typeof onDownload !== 'function'}
    >
      💾 텍스트 다운로드
    </button>

    <button
      className="action-btn convert-btn"
      onClick={onConvertCim}
      disabled={isConverting}
      title="CIM 데이터를 텍스트로 변환"
    >
      {isConverting ? (
        <>
          <span className="loading-spinner small"></span>
          변환 중...
        </>
      ) : (
        '🔄 CIM→텍스트'
      )}
    </button>

    <button
      className="action-btn data-btn"
      onClick={onToggleCimData}
      title="CIM 원시 데이터 보기/숨기기"
    >
      {showCimData ? '🔻 데이터 숨기기' : '🔺 데이터 보기'}
    </button>

    <button
      className="action-btn word-btn"
      onClick={onSaveAsWord}
      disabled={isWordSaving || !hasContent || typeof onSaveAsWord !== 'function'}
    >
      {isWordSaving ? (
        <>
          <span className="loading-spinner small"></span>
          변환 중...
        </>
      ) : (
        '📄 워드 저장'
      )}
    </button>
  </div>
));

EditorActions.displayName = 'EditorActions';
EditorActions.propTypes = {
  isEditing: PropTypes.bool.isRequired,
  isProcessing: PropTypes.bool.isRequired,
  hasContent: PropTypes.bool.isRequired,
  hasError: PropTypes.bool.isRequired,
  hasAlternativeData: PropTypes.bool.isRequired,
  onEditToggle: PropTypes.func.isRequired,
  onReset: PropTypes.func.isRequired,
  onCopy: PropTypes.func.isRequired,
  onConvertCim: PropTypes.func.isRequired,
  onToggleCimData: PropTypes.func.isRequired,
  onDownload: PropTypes.func,
  onSaveAsWord: PropTypes.func,
  showCimData: PropTypes.bool.isRequired,
  isConverting: PropTypes.bool.isRequired,
  isWordSaving: PropTypes.bool.isRequired
};

// ===========================
// 🚀 메인 컴포넌트
// ===========================

const TextEditorTab = ({
  formattedText = '',
  editableText = '',
  onTextChange = null,
  onSaveText = null,
  onResetText = null,
  onDownloadText = null,
  onCopyText = null,
  onSaveAsWord = null,
  isWordSaving = false,
  analysisResults = null
}) => {
  // CIM 관련 상태 추가
  const [showCimData, setShowCimData] = useState(false);
  const [isConverting, setIsConverting] = useState(false);
  const { trackRender, isBlocked, performanceStats } = usePerformanceGuard('TextEditorTab');
  trackRender();

  if (isBlocked) {
    return (
      <div className="text-editor-blocked">
        <h3>🚫 성능 문제로 에디터가 차단되었습니다</h3>
        <pre>{JSON.stringify(performanceStats, null, 2)}</pre>
      </div>
    );
  }

  const { 
    isEditing, 
    editableText: editorContent, 
    isWordSaving: isSavingWord, 
    setEditing, 
    setEditableText, 
    saveAsWord: saveWordAction, 
    copyText: copyAction,
    downloadText: downloadAction,
    resetText: resetAction
  } = useTextEditor(formattedText || editableText);

  const { normalizedData, availability, textExtractors } = useStableAnalysisData(analysisResults);

  const extractedText = useMemo(() => textExtractors.getHighConfidenceText() || textExtractors.getAllOCRText() || textExtractors.getAIDescriptions(), [textExtractors]);

  // CIM 데이터 추출
  const cimData = useMemo(() => {
    if (!analysisResults) return null;
    return analysisResults.cim_output || analysisResults.cimOutput || null;
  }, [analysisResults]);

  useEffect(() => {
    console.log('🔍 TextEditorTab Debug:', {
      formattedText: formattedText || '(empty)',
      extractedText: extractedText?.text || '(empty)',
      editorContent: editorContent || '(empty)',
      analysisResults: !!analysisResults
    });
    
    const newContent = formattedText || (extractedText ? extractedText.text : '');
    if (newContent !== editorContent) {
      setEditableText(newContent);
    }
  }, [formattedText, extractedText, editorContent, setEditableText]);

  const handleEditorChange = useCallback((content) => {
    setEditableText(content);
    if (onTextChange) {
      onTextChange(content);
    }
  }, [setEditableText, onTextChange]);

  // CIM 변환 핸들러
  const handleConvertCim = useCallback(async () => {
    if (!cimData || isConverting) return;

    setIsConverting(true);
    try {
      const convertedText = convertCimToText(cimData);
      if (convertedText) {
        setEditableText(convertedText);
        if (onTextChange) {
          onTextChange(convertedText);
        }
      }
    } catch (error) {
      console.error('CIM 변환 오류:', error);
      setEditableText('❌ CIM 데이터 변환에 실패했습니다.');
    } finally {
      setIsConverting(false);
    }
  }, [cimData, isConverting, setEditableText, onTextChange]);

  // CIM 데이터 토글 핸들러
  const handleToggleCimData = useCallback(() => {
    setShowCimData(prev => !prev);
  }, []);

  if (!availability.hasData && !editorContent) {
    return <EmptyState hasAlternativeData={!!extractedText} onLoadFallback={() => setEditableText(extractedText?.text || '')} />;
  }

  return (
    <div className="text-editor-content">
      <div className="editor-header">
        <h4>📝 텍스트 편집기</h4>
        <EditorActions
          isEditing={isEditing}
          isProcessing={isSavingWord}
          hasContent={!!editorContent}
          hasError={detectTextError(editorContent)}
          hasAlternativeData={!!extractedText}
          onEditToggle={() => setEditing(!isEditing)}
          onReset={resetAction}
          onCopy={copyAction}
          onConvertCim={handleConvertCim}
          onToggleCimData={handleToggleCimData}
          onDownload={onDownloadText ? () => onDownloadText(editorContent) : null}
          onSaveAsWord={onSaveAsWord ? () => onSaveAsWord(editorContent) : null}
          showCimData={showCimData}
          isConverting={isConverting}
          isWordSaving={isSavingWord}
        />
      </div>
      <div className="editor-container">
        {isEditing ? (
          <Editor
            value={editorContent}
            onEditorChange={handleEditorChange}
            init={{
              height: 500,
              menubar: false,
              plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code fullscreen insertdatetime media table help wordcount',
              toolbar: 'undo redo | blocks | bold italic forecolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | removeformat | help',
              language: 'ko_KR',
              ...getTinyMCEExtensionSafeConfig()
            }}
          />
        ) : (
          <div className="text-display" dangerouslySetInnerHTML={{ __html: editorContent }} />
        )}
      </div>

      {/* CIM 데이터 표시 오버레이 */}
      {showCimData && cimData && (
        <CimDataDisplay
          cimData={cimData}
          onClose={() => setShowCimData(false)}
        />
      )}
    </div>
  );
};

TextEditorTab.propTypes = {
  formattedText: PropTypes.string,
  editableText: PropTypes.string,
  onTextChange: PropTypes.func,
  onSaveText: PropTypes.func,
  onResetText: PropTypes.func,
  onDownloadText: PropTypes.func,
  onCopyText: PropTypes.func,
  onSaveAsWord: PropTypes.func,
  isWordSaving: PropTypes.bool,
  analysisResults: PropTypes.object
};

export default memo(TextEditorTab);