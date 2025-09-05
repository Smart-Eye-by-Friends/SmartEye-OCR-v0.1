import React, { useState } from 'react';

const StructuredTab = ({ structuredResult }) => {
  const [expandedQuestions, setExpandedQuestions] = useState(new Set());
  const [selectedSection, setSelectedSection] = useState('all');

  if (!structuredResult) {
    return (
      <div className="no-result">
        <div className="no-result-icon">📋</div>
        <h3>구조화된 분석 결과가 없습니다</h3>
        <p>상단에서 "구조화된 분석" 모드를 선택하고 분석을 실행해주세요.</p>
        <div className="structured-info">
          <h4>구조화된 분석 기능:</h4>
          <ul>
            <li>📝 문제별 자동 분류</li>
            <li>📖 지문과 선택지 구분</li>
            <li>🖼️ 이미지와 표 요소 인식</li>
            <li>🤖 AI 기반 내용 분석</li>
          </ul>
        </div>
      </div>
    );
  }

  const toggleQuestion = (questionNumber) => {
    const newExpanded = new Set(expandedQuestions);
    if (newExpanded.has(questionNumber)) {
      newExpanded.delete(questionNumber);
    } else {
      newExpanded.add(questionNumber);
    }
    setExpandedQuestions(newExpanded);
  };

  const hasContent = (question) => {
    const content = question.question_content;
    return (
      content?.main_question ||
      content?.passage ||
      (content?.choices && content.choices.length > 0) ||
      (content?.images && content.images.length > 0) ||
      (content?.tables && content.tables.length > 0)
    );
  };

  const hasAiAnalysis = (aiAnalysis) => {
    return aiAnalysis &&
      ((aiAnalysis.image_descriptions && aiAnalysis.image_descriptions.length > 0) ||
       (aiAnalysis.table_analysis && aiAnalysis.table_analysis.length > 0));
  };

  const getQuestionsBySection = () => {
    if (!structuredResult.questions) return [];
    
    if (selectedSection === 'all') {
      return structuredResult.questions;
    }
    
    return structuredResult.questions.filter(q => q.section === selectedSection);
  };

  const getSections = () => {
    if (!structuredResult.questions) return [];
    
    const sections = [...new Set(structuredResult.questions
      .map(q => q.section)
      .filter(s => s))];
    
    return sections.sort();
  };

  const filteredQuestions = getQuestionsBySection();
  const sections = getSections();

  return (
    <div className="structured-content">
      {/* 문서 정보 */}
      <div className="document-info">
        <h4>📋 문서 분석 개요</h4>
        <div className="info-grid">
          <div className="info-item">
            <span className="info-label">총 문제 수:</span>
            <span className="info-value">
              {structuredResult.document_info?.total_questions || 0}개
            </span>
          </div>
          <div className="info-item">
            <span className="info-label">레이아웃 유형:</span>
            <span className="info-value">
              {structuredResult.document_info?.layout_type || '미확인'}
            </span>
          </div>
          {structuredResult.document_info?.analysis_timestamp && (
            <div className="info-item">
              <span className="info-label">분석 시간:</span>
              <span className="info-value">
                {new Date(structuredResult.document_info.analysis_timestamp).toLocaleString('ko-KR')}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* 섹션 필터 */}
      {sections.length > 0 && (
        <div className="section-filter">
          <h5>📂 섹션별 필터</h5>
          <div className="filter-buttons">
            <button
              className={`filter-btn ${selectedSection === 'all' ? 'active' : ''}`}
              onClick={() => setSelectedSection('all')}
            >
              전체 ({structuredResult.questions?.length || 0})
            </button>
            {sections.map(section => {
              const count = structuredResult.questions.filter(q => q.section === section).length;
              return (
                <button
                  key={section}
                  className={`filter-btn ${selectedSection === section ? 'active' : ''}`}
                  onClick={() => setSelectedSection(section)}
                >
                  {section} ({count})
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* 문제별 상세 정보 */}
      <div className="questions-list">
        <h4>🔍 문제별 상세 분석 ({filteredQuestions.length}개)</h4>
        
        {filteredQuestions.length > 0 ? (
          filteredQuestions.map((question, index) => (
            <div key={question.question_number || index} className="question-item">
              <div 
                className="question-header"
                onClick={() => toggleQuestion(question.question_number)}
              >
                <div className="question-title">
                  <h5>
                    🔸 문제 {question.question_number}
                    {question.section && (
                      <span className="section-badge">{question.section}</span>
                    )}
                  </h5>
                  {question.difficulty && (
                    <span className={`difficulty-badge ${question.difficulty.toLowerCase()}`}>
                      {question.difficulty}
                    </span>
                  )}
                </div>
                <span className="expand-icon">
                  {expandedQuestions.has(question.question_number) ? '▼' : '▶'}
                </span>
              </div>

              {expandedQuestions.has(question.question_number) && (
                <div className="question-details">
                  {hasContent(question) ? (
                    <>
                      {/* 지문 */}
                      {question.question_content?.passage && (
                        <div className="content-section">
                          <h6>📖 지문</h6>
                          <div className="passage-content">
                            {question.question_content.passage}
                          </div>
                        </div>
                      )}

                      {/* 주요 문제 */}
                      {question.question_content?.main_question && (
                        <div className="content-section">
                          <h6>❓ 문제</h6>
                          <div className="question-text">
                            {question.question_content.main_question}
                          </div>
                        </div>
                      )}

                      {/* 선택지 */}
                      {question.question_content?.choices && 
                       question.question_content.choices.length > 0 && (
                        <div className="content-section">
                          <h6>📝 선택지</h6>
                          <div className="choices-container">
                            {question.question_content.choices.map((choice, i) => (
                              <div key={i} className="choice-item">
                                <span className="choice-number">{choice.choice_number}</span>
                                <span className="choice-text">{choice.choice_text}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* 이미지 */}
                      {question.question_content?.images && 
                       question.question_content.images.length > 0 && (
                        <div className="content-section">
                          <h6>🖼️ 이미지 요소</h6>
                          <div className="media-container">
                            {question.question_content.images.map((image, i) => (
                              <div key={i} className="media-item">
                                <div className="media-header">
                                  <span className="media-type">이미지 #{i + 1}</span>
                                  <span className="confidence-score">
                                    신뢰도: {(image.confidence * 100).toFixed(1)}%
                                  </span>
                                </div>
                                {image.description && (
                                  <p className="media-description">{image.description}</p>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* 표 */}
                      {question.question_content?.tables && 
                       question.question_content.tables.length > 0 && (
                        <div className="content-section">
                          <h6>📊 표 요소</h6>
                          <div className="media-container">
                            {question.question_content.tables.map((table, i) => (
                              <div key={i} className="media-item">
                                <div className="media-header">
                                  <span className="media-type">표 #{i + 1}</span>
                                  <span className="confidence-score">
                                    신뢰도: {(table.confidence * 100).toFixed(1)}%
                                  </span>
                                </div>
                                {table.description && (
                                  <p className="media-description">{table.description}</p>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* AI 분석 */}
                      {hasAiAnalysis(question.ai_analysis) && (
                        <div className="content-section ai-section">
                          <h6>🤖 AI 분석 결과</h6>
                          <div className="ai-analysis-container">
                            {question.ai_analysis.image_descriptions?.map((desc, i) => (
                              <div key={i} className="ai-description">
                                <div className="ai-type">🖼️ 이미지 분석</div>
                                <p>{desc.description}</p>
                              </div>
                            ))}
                            {question.ai_analysis.table_analysis?.map((desc, i) => (
                              <div key={i} className="ai-description">
                                <div className="ai-type">📊 표 분석</div>
                                <p>{desc.description}</p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="no-content">
                      <div className="no-content-icon">📄</div>
                      <p>이 문제에 대한 상세 내용이 감지되지 않았습니다.</p>
                      <small>OCR 품질을 높이려면 더 선명한 이미지를 사용해보세요.</small>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))
        ) : (
          <div className="no-questions">
            <div className="no-questions-icon">🔍</div>
            <h4>선택한 섹션에 문제가 없습니다</h4>
            <p>다른 섹션을 선택하거나 전체 보기를 확인해보세요.</p>
          </div>
        )}
      </div>

      {/* 분석 통계 요약 */}
      {filteredQuestions.length > 0 && (
        <div className="analysis-summary">
          <h5>📈 분석 요약</h5>
          <div className="summary-stats">
            <div className="summary-item">
              <span className="summary-label">지문이 있는 문제:</span>
              <span className="summary-value">
                {filteredQuestions.filter(q => q.question_content?.passage).length}개
              </span>
            </div>
            <div className="summary-item">
              <span className="summary-label">선택지가 있는 문제:</span>
              <span className="summary-value">
                {filteredQuestions.filter(q => q.question_content?.choices?.length > 0).length}개
              </span>
            </div>
            <div className="summary-item">
              <span className="summary-label">이미지/표가 있는 문제:</span>
              <span className="summary-value">
                {filteredQuestions.filter(q => 
                  (q.question_content?.images?.length > 0) || 
                  (q.question_content?.tables?.length > 0)
                ).length}개
              </span>
            </div>
            <div className="summary-item">
              <span className="summary-label">AI 분석된 문제:</span>
              <span className="summary-value">
                {filteredQuestions.filter(q => hasAiAnalysis(q.ai_analysis)).length}개
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StructuredTab;
