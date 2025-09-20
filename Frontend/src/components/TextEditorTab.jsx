import React, { useState, useRef, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';

// 에러 감지 유틸리티 함수
const detectError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const errorPatterns = [
    /error/i,
    /오류/,
    /실패/,
    /exception/i,
    /not found/i,
    /cannot/i,
    /unable/i,
    /invalid/i,
    /처리할 수 없습니다/,
    /불러올 수 없습니다/,
    /문제가 발생했습니다/
  ];

  return errorPatterns.some(pattern => pattern.test(text));
};

// 안전한 텍스트 추출 함수
const extractFallbackText = (analysisResults) => {
  if (!analysisResults) return '';

  // OCR 결과에서 텍스트 추출
  if (analysisResults.ocrResults && Array.isArray(analysisResults.ocrResults)) {
    const ocrText = analysisResults.ocrResults
      .filter(result => result && result.text)
      .map(result => result.text)
      .join('\n');
    if (ocrText.trim()) return ocrText;
  }

  // CIM 데이터에서 텍스트 추출
  if (analysisResults.cimData) {
    try {
      const cimText = typeof analysisResults.cimData === 'string'
        ? analysisResults.cimData
        : JSON.stringify(analysisResults.cimData, null, 2);
      if (cimText.trim()) return cimText;
    } catch (error) {
      console.warn('CIM 데이터 파싱 오류:', error);
    }
  }

  return '추출 가능한 텍스트가 없습니다.';
};

const TextEditorTab = ({
  formattedText,
  editableText,
  onTextChange,
  onSaveText,
  onResetText,
  onDownloadText,
  onCopyText,
  onSaveAsWord,
  isWordSaving,
  analysisResults
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editorContent, setEditorContent] = useState('');
  const [isConverting, setIsConverting] = useState(false);
  const [showCimData, setShowCimData] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const editorRef = useRef(null);

  useEffect(() => {
    setIsLoading(true);

    // 포맷된 텍스트 오류 감지
    const textToCheck = formattedText || editableText || '';
    const hasTextError = detectError(textToCheck);

    if (hasTextError) {
      setHasError(true);
      setErrorMessage('포맷팅된 텍스트를 불러올 수 없습니다. 원본 OCR 데이터를 표시합니다.');

      // 대체 텍스트 사용
      const fallbackText = extractFallbackText(analysisResults);
      setEditorContent(fallbackText);

      // onTextChange가 있다면 대체 텍스트로 업데이트
      if (onTextChange && typeof onTextChange === 'function') {
        onTextChange(fallbackText);
      }
    } else {
      setHasError(false);
      setErrorMessage('');
      setEditorContent(editableText || formattedText || '');
    }

    // 로딩 상태 해제
    setTimeout(() => setIsLoading(false), 300);
  }, [editableText, formattedText, analysisResults, onTextChange]);

  const handleEditorChange = (content) => {
    setEditorContent(content);
    onTextChange(content);
  };

  const handleSave = () => {
    onSaveText();
    setIsEditing(false);
  };

  const handleReset = () => {
    try {
      const resetContent = formattedText || '';

      // 리셋할 텍스트에 오류가 있는지 확인
      if (detectError(resetContent)) {
        const fallbackText = extractFallbackText(analysisResults);
        setEditorContent(fallbackText);
        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(fallbackText);
        }
        setHasError(true);
        setErrorMessage('원본 텍스트에 오류가 있어 OCR 데이터로 복원했습니다.');
      } else {
        setEditorContent(resetContent);
        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(resetContent);
        }
        setHasError(false);
        setErrorMessage('');
      }

      if (onResetText && typeof onResetText === 'function') {
        onResetText();
      }
    } catch (error) {
      console.error('텍스트 리셋 오류:', error);
      setErrorMessage('텍스트 리셋 중 오류가 발생했습니다.');
    }
  };

  const handleCopy = async () => {
    try {
      const textToCopy = editorContent || '';

      if (!textToCopy.trim()) {
        alert('복사할 텍스트가 없습니다.');
        return;
      }

      // HTML 태그 제거
      const plainText = textToCopy.replace(/<[^>]*>/g, '');

      await navigator.clipboard.writeText(plainText);
      alert('텍스트가 클립보드에 복사되었습니다.');
    } catch (err) {
      console.error('클립보드 복사 실패:', err);

      // 대체 방법 시도
      if (onCopyText && typeof onCopyText === 'function') {
        onCopyText();
      } else {
        alert('클립보드 복사에 실패했습니다. 브라우저 설정을 확인해주세요.');
      }
    }
  };

  // CIM → 텍스트 변환 핸들러
  const handleConvertCimToText = async () => {
    if (!analysisResults?.cimData) {
      alert('CIM 데이터가 없습니다. 먼저 분석을 실행해주세요.');
      return;
    }

    setIsConverting(true);
    try {
      const convertedText = await apiService.convertCimToText(analysisResults.cimData);
      setEditorContent(convertedText.text || convertedText);
      onTextChange(convertedText.text || convertedText);
      alert('CIM 데이터가 텍스트로 변환되었습니다.');
    } catch (error) {
      console.error('CIM → 텍스트 변환 실패:', error);
      alert('CIM → 텍스트 변환에 실패했습니다.');
    } finally {
      setIsConverting(false);
    }
  };

  // 로딩 상태 처리
  if (isLoading) {
    return (
      <div className="no-result">
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>📝 텍스트 데이터를 로딩 중...</p>
        </div>
      </div>
    );
  }

  // 텍스트 데이터가 전혀 없는 경우
  if (!formattedText && !editableText && !analysisResults?.ocrResults && !analysisResults?.cimData) {
    return (
      <div className="no-result">
        <div className="no-result-icon">📝</div>
        <h3>텍스트 결과가 없습니다</h3>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  return (
    <div className="text-editor-content">
      {/* 오류 알림 표시 */}
      {hasError && (
        <div className="error-notification">
          <div className="error-content">
            <span className="error-icon">⚠️</span>
            <span className="error-text">{errorMessage}</span>
            <button
              className="error-dismiss"
              onClick={() => {
                setHasError(false);
                setErrorMessage('');
              }}
              title="알림 닫기"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      <div className="editor-header">
        <h4>📝 텍스트 편집기</h4>
        <div className="editor-actions">
          <button
            className="action-btn edit-btn"
            onClick={() => setIsEditing(!isEditing)}
          >
            {isEditing ? '📖 읽기 모드' : '✏️ 편집 모드'}
          </button>
          
          <button
            className="action-btn reset-btn"
            onClick={handleReset}
            disabled={!formattedText && !analysisResults?.ocrResults}
            title={hasError ? 'OCR 데이터로 복원' : '포맷된 텍스트로 복원'}
          >
            🔄 {hasError ? 'OCR로 복원' : '원본으로 복원'}
          </button>
          
          <button
            className="action-btn copy-btn"
            onClick={handleCopy}
          >
            📋 복사
          </button>
          
          <button
            className="action-btn download-btn"
            onClick={onDownloadText}
          >
            💾 텍스트 다운로드
          </button>
          
          <button
            className="action-btn convert-btn"
            onClick={handleConvertCimToText}
            disabled={isConverting || !analysisResults?.cimData}
            title="CIM 데이터를 최종 텍스트로 변환"
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
            onClick={() => setShowCimData(!showCimData)}
            disabled={!analysisResults?.cimData}
            title="CIM 원시 데이터 보기/숨기기"
          >
            {showCimData ? '🔻 데이터 숨기기' : '🔺 데이터 보기'}
          </button>

          <button
            className="action-btn word-btn"
            onClick={onSaveAsWord}
            disabled={isWordSaving}
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
      </div>

      <div className="editor-container">
        {isEditing ? (
          <div className="editor-wrapper">
            <Editor
              ref={editorRef}
              value={editorContent}
              onEditorChange={handleEditorChange}
              init={{
                height: 500,
                menubar: false,
                plugins: [
                  'advlist', 'autolink', 'lists', 'link', 'image', 'charmap',
                  'preview', 'anchor', 'searchreplace', 'visualblocks', 'code',
                  'fullscreen', 'insertdatetime', 'media', 'table', 'code',
                  'help', 'wordcount'
                ],
                toolbar: 'undo redo | blocks | ' +
                  'bold italic forecolor | alignleft aligncenter ' +
                  'alignright alignjustify | bullist numlist outdent indent | ' +
                  'removeformat | help',
                content_style: 'body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 14px }',
                language: 'ko_KR',
                placeholder: '여기에 텍스트를 입력하세요...'
              }}
            />
            <div className="editor-footer">
              <button
                className="save-btn"
                onClick={handleSave}
                disabled={!editorContent.trim()}
              >
                💾 저장
              </button>
              <span className="character-count">
                문자 수: {editorContent.replace(/<[^>]*>/g, '').length.toLocaleString()}
              </span>
              {hasError && (
                <span className="fallback-indicator">
                  📋 대체 텍스트 사용 중
                </span>
              )}
            </div>
          </div>
        ) : (
          <div className="text-display">
            {editorContent || formattedText ? (
              <div
                className={`text-content ${hasError ? 'fallback-content' : ''}`}
                dangerouslySetInnerHTML={{ __html: editorContent || formattedText }}
              />
            ) : (
              <div className="empty-content">
                <p>표시할 텍스트가 없습니다.</p>
                {analysisResults?.ocrResults && (
                  <button
                    className="load-ocr-btn"
                    onClick={() => {
                      const fallbackText = extractFallbackText(analysisResults);
                      setEditorContent(fallbackText);
                      if (onTextChange && typeof onTextChange === 'function') {
                        onTextChange(fallbackText);
                      }
                    }}
                  >
                    📋 OCR 데이터 불러오기
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* CIM 원시 데이터 표시 */}
      {showCimData && analysisResults?.cimData && (
        <div className="cim-data-section">
          <h5>📋 CIM 원시 데이터 (Circuit Integration Management)</h5>
          <div className="cim-data-container">
            <pre className="cim-data-content">
              {JSON.stringify(analysisResults.cimData, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};

// PropTypes 정의
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
  analysisResults: PropTypes.shape({
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

TextEditorTab.defaultProps = {
  formattedText: '',
  editableText: '',
  onTextChange: () => {},
  onSaveText: () => {},
  onResetText: () => {},
  onDownloadText: () => {},
  onCopyText: () => {},
  onSaveAsWord: () => {},
  isWordSaving: false,
  analysisResults: null
};

export default TextEditorTab;
