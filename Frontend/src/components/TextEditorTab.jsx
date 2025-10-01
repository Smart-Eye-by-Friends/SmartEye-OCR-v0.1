import React, { useState, useRef, useEffect } from 'react';
import { Editor } from '@tinymce/tinymce-react';

const TextEditorTab = ({
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
  const [isEditing, setIsEditing] = useState(false);
  const [editorContent, setEditorContent] = useState('');
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
    </div>
  );
};

export default TextEditorTab;
