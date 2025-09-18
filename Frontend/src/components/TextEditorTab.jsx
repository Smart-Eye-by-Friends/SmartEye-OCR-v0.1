import React, { useState, useRef, useEffect } from 'react';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';

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
  const editorRef = useRef(null);

  useEffect(() => {
    setEditorContent(editableText || '');
  }, [editableText]);

  const handleEditorChange = (content) => {
    setEditorContent(content);
    onTextChange(content);
  };

  const handleSave = () => {
    onSaveText();
    setIsEditing(false);
  };

  const handleReset = () => {
    const resetContent = formattedText || '';
    setEditorContent(resetContent);
    onTextChange(resetContent);
    onResetText();
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(editorContent);
      alert('텍스트가 클립보드에 복사되었습니다.');
    } catch (err) {
      console.error('클립보드 복사 실패:', err);
      onCopyText();
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

  if (!formattedText && !editableText) {
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
            disabled={!formattedText}
          >
            🔄 원본으로 복원
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
              <button className="save-btn" onClick={handleSave}>
                💾 저장
              </button>
              <span className="character-count">
                문자 수: {editorContent.replace(/<[^>]*>/g, '').length}
              </span>
            </div>
          </div>
        ) : (
          <div className="text-display">
            <div 
              className="text-content"
              dangerouslySetInnerHTML={{ __html: editorContent || formattedText }}
            />
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

export default TextEditorTab;
