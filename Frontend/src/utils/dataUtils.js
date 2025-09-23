/**
 * 프론트엔드 데이터 정규화 유틸리티
 * CIM 응답 구조를 기존 컴포넌트 호환 구조로 변환
 */

/**
 * CIM 응답 데이터를 기존 컴포넌트 호환 구조로 정규화
 * @param {Object} rawResponse - CIM API 응답 데이터
 * @returns {Object} 정규화된 데이터 구조
 */
export const normalizeCIMResponse = (rawResponse) => {
  if (!rawResponse || typeof rawResponse !== 'object') {
    console.warn('CIM 응답 데이터가 유효하지 않음:', rawResponse);
    return createEmptyNormalizedData();
  }

  try {
    // CIM 데이터에서 OCR 및 AI 결과 추출
    const cimData = rawResponse.cimData || rawResponse.cim_data || {};
    const ocrResults = extractOCRResults(cimData, rawResponse);
    const aiResults = extractAIResults(cimData, rawResponse);

    // 통계 정보 생성
    const stats = generateStats(ocrResults, aiResults, rawResponse.stats);

    return {
      // 기존 호환성 유지
      layoutImageUrl: rawResponse.layoutImageUrl || rawResponse.layout_image_url || '',
      jsonUrl: rawResponse.jsonUrl || rawResponse.json_url || '',
      formattedText: rawResponse.formattedText || rawResponse.formatted_text || '',

      // 정규화된 데이터
      ocrResults: ocrResults,
      aiResults: aiResults,
      stats: stats,

      // CIM 원본 데이터 보존
      cimData: cimData,

      // 메타데이터
      timestamp: rawResponse.timestamp || Date.now(),
      jobId: rawResponse.jobId || rawResponse.job_id || '',
      success: rawResponse.success !== false,
      message: rawResponse.message || ''
    };
  } catch (error) {
    console.error('CIM 데이터 정규화 실패:', error.message || error);
    return createEmptyNormalizedData();
  }
};

/**
 * CIM 데이터에서 OCR 결과 추출
 * @param {Object} cimData - CIM 통합 데이터
 * @param {Object} rawResponse - 원본 응답
 * @returns {Array} OCR 결과 배열
 */
const extractOCRResults = (cimData, rawResponse) => {
  // OCR 데이터 추출 로그 (개발 환경에서만)
  if (process.env.NODE_ENV === 'development') {
    console.debug('OCR 데이터 추출 시작:', {
      hasCimData: !!cimData,
      responseKeys: Object.keys(rawResponse || {})
    });
  }

  const extractedTexts = [];

  // 1순위: 구조화된 분석 데이터에서 추출 (questions 기반)
  const questions = safeGet(cimData, 'questions');
  if (Array.isArray(questions) && questions.length > 0) {
    if (process.env.NODE_ENV === 'development') {
      console.debug(`구조화된 분석 데이터 발견: ${questions.length}개 문제`);
    }

    questions.forEach((question, qIndex) => {
      const questionContent = safeGet(question, 'question_content', {});

      // 메인 문제 텍스트
      if (questionContent.main_question) {
        extractedTexts.push({
          text: questionContent.main_question,
          confidence: 0.9,
          element_type: 'question_text',
          block_id: `q${qIndex}_main`,
          bbox: null,
          source: 'structured_analysis'
        });
      }

      // 지문 텍스트
      if (questionContent.passage) {
        extractedTexts.push({
          text: questionContent.passage,
          confidence: 0.9,
          element_type: 'passage',
          block_id: `q${qIndex}_passage`,
          bbox: null,
          source: 'structured_analysis'
        });
      }

      // 선택지 텍스트
      const choices = safeGet(questionContent, 'choices', []);
      choices.forEach((choice, cIndex) => {
        if (choice.choice_text) {
          extractedTexts.push({
            text: choice.choice_text,
            confidence: 0.9,
            element_type: 'choice',
            block_id: `q${qIndex}_choice${cIndex}`,
            bbox: null,
            choice_number: choice.choice_number,
            source: 'structured_analysis'
          });
        }
      });

      // 이미지 설명
      const images = safeGet(questionContent, 'images', []);
      images.forEach((image, iIndex) => {
        if (image.description) {
          extractedTexts.push({
            text: image.description,
            confidence: 0.8,
            element_type: 'image_description',
            block_id: `q${qIndex}_image${iIndex}`,
            bbox: image.bbox || null,
            source: 'structured_analysis'
          });
        }
      });

      // 설명/해설
      const explanations = safeGet(questionContent, 'explanations', []);
      if (Array.isArray(explanations)) {
        explanations.forEach((explanation, eIndex) => {
          if (typeof explanation === 'string' && explanation.trim()) {
            extractedTexts.push({
              text: explanation,
              confidence: 0.9,
              element_type: 'explanation',
              block_id: `q${qIndex}_explanation${eIndex}`,
              bbox: null,
              source: 'structured_analysis'
            });
          }
        });
      }
    });

    if (extractedTexts.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug(`구조화된 분석에서 ${extractedTexts.length}개 텍스트 추출 완료`);
      }
      return extractedTexts;
    }
  }

  // 2순위: 기본 CIM 레이아웃 분석 데이터에서 추출
  const cimPaths = [
    'document_structure.layout_analysis.elements',
    'layout_analysis.elements',
    'elements'
  ];

  for (const path of cimPaths) {
    const elements = safeGet(cimData, path);
    if (process.env.NODE_ENV === 'development') {
      console.debug(`CIM 경로 ${path}:`, elements);
    }
    if (Array.isArray(elements) && elements.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug(`기본 CIM 경로에서 OCR 데이터 발견: ${path}`);
      }
      return elements.map(normalizeOCRItem);
    }
  }

  // 2순위: 기존 직접 경로들
  const directSources = [
    rawResponse.ocrResults,
    rawResponse.ocr_results,
    cimData.ocr_results,
    cimData.ocrResults,
    cimData.text_blocks,
    cimData.textBlocks
  ];

  for (const source of directSources) {
    if (Array.isArray(source) && source.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug('직접 경로에서 OCR 데이터 발견');
      }
      return source.map(normalizeOCRItem);
    }
  }

  // 3순위: CIM 데이터에서 텍스트 정보 직접 추출 시도
  if (typeof cimData === 'object' && cimData !== null) {
    const extractedTexts = extractTextFromCIMData(cimData);
    if (process.env.NODE_ENV === 'development') {
      console.debug('CIM 데이터에서 추출된 텍스트:', extractedTexts);
    }
    if (extractedTexts.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug('CIM 데이터에서 텍스트 추출 성공');
      }
      return extractedTexts;
    }
  }

  if (process.env.NODE_ENV === 'development') {
    console.warn('OCR 데이터를 찾을 수 없음');
  }
  return [];
};

/**
 * CIM 데이터에서 AI 결과 추출
 * @param {Object} cimData - CIM 통합 데이터
 * @param {Object} rawResponse - 원본 응답
 * @returns {Array} AI 결과 배열
 */
const extractAIResults = (cimData, rawResponse) => {
  // AI 데이터 추출 로그 (개발 환경에서만)
  if (process.env.NODE_ENV === 'development') {
    console.debug('AI 데이터 추출 시작');
  }

  const aiResults = [];

  // 1순위: 구조화된 분석의 AI 데이터에서 추출 (questions 기반)
  const questions = safeGet(cimData, 'questions');
  if (Array.isArray(questions) && questions.length > 0) {
    if (process.env.NODE_ENV === 'development') {
      console.debug(`구조화된 분석 AI 데이터 확인: ${questions.length}개 문제`);
    }

    questions.forEach((question, qIndex) => {
      const aiAnalysis = safeGet(question, 'ai_analysis', {});

      // 이미지 설명
      const imageDescriptions = safeGet(aiAnalysis, 'image_descriptions', []);
      imageDescriptions.forEach((desc, iIndex) => {
        if (desc && typeof desc === 'string' && desc.trim()) {
          aiResults.push({
            description: desc,
            confidence: 0.8,
            type: 'image_description',
            source: 'structured_ai_analysis',
            block_id: `q${qIndex}_ai_image${iIndex}`,
            bbox: null
          });
        }
      });

      // 표 분석
      const tableAnalysis = safeGet(aiAnalysis, 'table_analysis', []);
      tableAnalysis.forEach((analysis, tIndex) => {
        if (analysis && typeof analysis === 'string' && analysis.trim()) {
          aiResults.push({
            description: analysis,
            confidence: 0.8,
            type: 'table_analysis',
            source: 'structured_ai_analysis',
            block_id: `q${qIndex}_ai_table${tIndex}`,
            bbox: null
          });
        }
      });

      // 문제 분석
      const problemAnalysis = safeGet(aiAnalysis, 'problem_analysis', []);
      problemAnalysis.forEach((analysis, pIndex) => {
        if (analysis && typeof analysis === 'string' && analysis.trim()) {
          aiResults.push({
            description: analysis,
            confidence: 0.8,
            type: 'problem_analysis',
            source: 'structured_ai_analysis',
            block_id: `q${qIndex}_ai_problem${pIndex}`,
            bbox: null
          });
        }
      });
    });

    if (aiResults.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug(`구조화된 분석에서 ${aiResults.length}개 AI 결과 추출 완료`);
      }
      return aiResults;
    }
  }

  // 2순위: 기본 CIM 데이터의 AI 경로 시도
  const cimPaths = [
    'document_structure.ai_analysis.descriptions',
    'ai_analysis.descriptions',
    'ai_analysis.results',
    'ai_descriptions',
    'ai_results'
  ];

  for (const path of cimPaths) {
    const elements = safeGet(cimData, path);
    if (process.env.NODE_ENV === 'development') {
      console.debug(`AI CIM 경로 ${path}:`, elements);
    }
    if (Array.isArray(elements) && elements.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug(`기본 CIM 경로에서 AI 데이터 발견: ${path}`);
      }
      return elements.map(normalizeAIItem);
    }
  }

  // 2순위: 기존 직접 경로들
  const directSources = [
    rawResponse.aiResults,
    rawResponse.ai_results,
    cimData.ai_results,
    cimData.aiResults,
    cimData.ai_descriptions,
    cimData.aiDescriptions
  ];

  for (const source of directSources) {
    if (Array.isArray(source) && source.length > 0) {
      if (process.env.NODE_ENV === 'development') {
        console.debug('직접 경로에서 AI 데이터 발견');
      }
      return source.map(normalizeAIItem);
    }
  }

  if (process.env.NODE_ENV === 'development') {
    console.warn('AI 데이터를 찾을 수 없음');
  }
  return [];
};

/**
 * OCR 아이템 정규화
 * @param {Object} item - OCR 결과 아이템
 * @returns {Object} 정규화된 OCR 아이템
 */
const normalizeOCRItem = (item) => {
  if (!item || typeof item !== 'object') {
    return { text: '', confidence: 0, bbox: null, element_type: 'unknown' };
  }

  // CIM 데이터 구조의 다양한 텍스트 필드 지원
  const text = item.text || item.content || item.extracted_text || item.ocr_text || '';

  // 신뢰도 계산 - CIM에서는 다양한 신뢰도 필드 사용
  const confidence = safeNumber(
    item.confidence ||
    item.conf ||
    item.ocr_confidence ||
    item.text_confidence ||
    item.detection_confidence,
    0
  );

  // 바운딩 박스 - CIM의 다양한 좌표 필드 지원
  const bbox = normalizeBBox(
    item.bbox ||
    item.bounding_box ||
    item.coordinates ||
    item.position ||
    item.location
  );

  // 요소 타입 - CIM의 분류 체계 지원
  const element_type = item.element_type ||
                      item.elementType ||
                      item.type ||
                      item.classification ||
                      item.category ||
                      'text';

  return {
    text: text.trim(),
    confidence: confidence,
    bbox: bbox,
    element_type: element_type,
    page_num: safeNumber(item.page_num || item.pageNum || item.page, 1),
    block_id: item.block_id || item.blockId || item.id || generateBlockId(),
    // CIM 추가 메타데이터 보존
    layout_class: item.layout_class,
    text_class: item.text_class,
    reading_order: item.reading_order,
    ...item // 모든 추가 속성 보존
  };
};

/**
 * AI 아이템 정규화
 * @param {Object} item - AI 결과 아이템
 * @returns {Object} 정규화된 AI 아이템
 */
const normalizeAIItem = (item) => {
  if (!item || typeof item !== 'object') {
    return { description: '', confidence: 0, type: 'unknown' };
  }

  return {
    description: item.description || item.content || item.text || '',
    confidence: safeNumber(item.confidence || item.conf, 0),
    type: item.type || item.element_type || item.elementType || 'description',
    bbox: normalizeBBox(item.bbox || item.bounding_box),
    ...item // 추가 속성 보존
  };
};

/**
 * 바운딩 박스 정규화
 * @param {Object|Array} bbox - 바운딩 박스 데이터
 * @returns {Object|null} 정규화된 바운딩 박스
 */
const normalizeBBox = (bbox) => {
  if (!bbox) return null;

  // 배열 형태 처리: [x, y, width, height] 또는 [x1, y1, x2, y2]
  if (Array.isArray(bbox) && bbox.length >= 4) {
    const [val1, val2, val3, val4] = bbox.map(v => safeNumber(v));

    // x2, y2 형태인지 width, height 형태인지 판단
    // 일반적으로 x2 > x1이고 width는 상대적으로 작은 값
    if (val3 > val1 && val4 > val2 && val3 - val1 > 10 && val4 - val2 > 10) {
      // [x1, y1, x2, y2] 형태로 추정
      return {
        x: val1,
        y: val2,
        width: val3 - val1,
        height: val4 - val2,
        x1: val1,
        y1: val2,
        x2: val3,
        y2: val4
      };
    } else {
      // [x, y, width, height] 형태로 추정
      return {
        x: val1,
        y: val2,
        width: val3,
        height: val4,
        x1: val1,
        y1: val2,
        x2: val1 + val3,
        y2: val2 + val4
      };
    }
  }

  // 객체 형태 처리
  if (typeof bbox === 'object') {
    // 다양한 좌표 표현 방식 지원
    const x = safeNumber(bbox.x || bbox.left || bbox.x1 || bbox.min_x, 0);
    const y = safeNumber(bbox.y || bbox.top || bbox.y1 || bbox.min_y, 0);

    let width, height, x2, y2;

    if (bbox.width !== undefined || bbox.w !== undefined) {
      // width/height 방식
      width = safeNumber(bbox.width || bbox.w, 0);
      height = safeNumber(bbox.height || bbox.h, 0);
      x2 = x + width;
      y2 = y + height;
    } else if (bbox.x2 !== undefined || bbox.right !== undefined) {
      // x2/y2 방식
      x2 = safeNumber(bbox.x2 || bbox.right || bbox.max_x, x);
      y2 = safeNumber(bbox.y2 || bbox.bottom || bbox.max_y, y);
      width = x2 - x;
      height = y2 - y;
    } else {
      // 기본값
      width = 0;
      height = 0;
      x2 = x;
      y2 = y;
    }

    return {
      x: x,
      y: y,
      width: Math.max(0, width),
      height: Math.max(0, height),
      x1: x,
      y1: y,
      x2: x2,
      y2: y2
    };
  }

  return null;
};

/**
 * CIM 데이터에서 직접 텍스트 추출
 * @param {Object} cimData - CIM 데이터
 * @returns {Array} 추출된 텍스트 배열
 */
const extractTextFromCIMData = (cimData) => {
  const extractedTexts = [];

  // CIM 데이터 구조를 순회하며 텍스트 추출
  const traverse = (obj, path = '') => {
    if (typeof obj !== 'object' || obj === null) return;

    Object.entries(obj).forEach(([key, value]) => {
      if (typeof value === 'string' && value.trim().length > 0) {
        // 의미있는 텍스트인지 확인 (최소 길이, 특수문자 비율 등)
        if (isValidText(value)) {
          extractedTexts.push({
            text: value.trim(),
            confidence: 0.5, // 기본 신뢰도
            element_type: inferElementType(key, value),
            source: `cim.${path}.${key}`,
            bbox: null
          });
        }
      } else if (Array.isArray(value)) {
        value.forEach((item, index) => {
          traverse(item, `${path}.${key}[${index}]`);
        });
      } else if (typeof value === 'object') {
        traverse(value, `${path}.${key}`);
      }
    });
  };

  traverse(cimData);
  return extractedTexts;
};

/**
 * 유효한 텍스트인지 확인
 * @param {string} text - 확인할 텍스트
 * @returns {boolean} 유효 여부
 */
const isValidText = (text) => {
  if (!text || typeof text !== 'string') return false;

  const trimmedText = text.trim();

  // 최소 길이 확인
  if (trimmedText.length < 2) return false;

  // URL, 파일 경로, JSON 등 제외
  if (trimmedText.match(/^(https?:\/\/|\/|[{[])/)) return false;

  // 타임스탬프나 숫자만 있는 경우 제외
  if (trimmedText.match(/^\d+(\.\d+)?$/)) return false;

  return true;
};

/**
 * 키와 값으로부터 요소 타입 추론
 * @param {string} key - 키 이름
 * @param {string} value - 텍스트 값
 * @returns {string} 추론된 요소 타입
 */
const inferElementType = (key, value) => {
  const keyLower = key.toLowerCase();
  const valueLower = value.toLowerCase();

  if (keyLower.includes('title') || keyLower.includes('heading')) return 'title';
  if (keyLower.includes('question') || keyLower.includes('problem')) return 'question';
  if (keyLower.includes('answer') || keyLower.includes('solution')) return 'answer';
  if (keyLower.includes('option') || keyLower.includes('choice')) return 'option';
  if (valueLower.length > 100) return 'paragraph';

  return 'text';
};

/**
 * 통계 정보 생성
 * @param {Array} ocrResults - OCR 결과
 * @param {Array} aiResults - AI 결과
 * @param {Object} existingStats - 기존 통계 (있다면)
 * @returns {Object} 생성된 통계
 */
const generateStats = (ocrResults, aiResults, existingStats = {}) => {
  const totalElements = ocrResults.length + aiResults.length;
  const totalCharacters = ocrResults.reduce((sum, item) => sum + (item.text?.length || 0), 0);

  // 신뢰도 계산 (유효한 confidence 값만 사용)
  const validConfidences = ocrResults
    .map(item => item.confidence)
    .filter(conf => typeof conf === 'number' && conf >= 0 && conf <= 1);

  const averageConfidence = validConfidences.length > 0
    ? validConfidences.reduce((sum, conf) => sum + conf, 0) / validConfidences.length
    : 0;

  // 요소별 카운트 (구조화된 분석 유형도 고려)
  const elementCounts = {};
  [...ocrResults, ...aiResults].forEach(item => {
    const type = item.element_type || item.type || 'unknown';
    elementCounts[type] = (elementCounts[type] || 0) + 1;
  });

  // 구조화된 분석 특화 통계
  const structuredStats = {};
  const structuredItems = ocrResults.filter(item => item.source === 'structured_analysis');
  if (structuredItems.length > 0) {
    // 문제별 분류
    const questionTexts = structuredItems.filter(item => item.element_type === 'question_text');
    const choices = structuredItems.filter(item => item.element_type === 'choice');
    const passages = structuredItems.filter(item => item.element_type === 'passage');
    const explanations = structuredItems.filter(item => item.element_type === 'explanation');

    structuredStats.total_questions = questionTexts.length;
    structuredStats.total_choices = choices.length;
    structuredStats.total_passages = passages.length;
    structuredStats.total_explanations = explanations.length;
    structuredStats.analysis_type = 'structured';
  } else {
    structuredStats.analysis_type = 'basic_layout';
  }

  return {
    total_elements: totalElements,
    total_characters: totalCharacters,
    average_confidence: averageConfidence,
    processing_time: existingStats.processing_time || 0,
    element_counts: elementCounts,
    ocr_block_count: ocrResults.length,
    ai_analysis_count: aiResults.length,
    structured_stats: structuredStats,
    metadata: existingStats.metadata || {},
    ...existingStats // 기존 통계 보존
  };
};

/**
 * 빈 정규화 데이터 생성
 * @returns {Object} 빈 정규화된 데이터 구조
 */
const createEmptyNormalizedData = () => ({
  layoutImageUrl: '',
  jsonUrl: '',
  formattedText: '',
  ocrResults: [],
  aiResults: [],
  stats: {
    total_elements: 0,
    total_characters: 0,
    average_confidence: 0,
    processing_time: 0,
    element_counts: {},
    ocr_block_count: 0,
    ai_analysis_count: 0,
    metadata: {}
  },
  cimData: null,
  timestamp: Date.now(),
  jobId: '',
  success: false,
  message: 'No data available'
});

/**
 * 안전한 숫자 변환
 * @param {any} value - 변환할 값
 * @param {number} defaultValue - 기본값
 * @returns {number} 변환된 숫자
 */
const safeNumber = (value, defaultValue = 0) => {
  if (typeof value === 'number' && !isNaN(value)) return value;
  if (typeof value === 'string') {
    const parsed = parseFloat(value);
    if (!isNaN(parsed)) return parsed;
  }
  return defaultValue;
};

/**
 * 고유한 블록 ID 생성
 * @returns {string} 생성된 블록 ID
 */
const generateBlockId = () => {
  return `block_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * 안전한 배열 접근
 * @param {any} value - 확인할 값
 * @param {Array} defaultValue - 기본값
 * @returns {Array} 배열 또는 기본값
 */
export const safeArray = (value, defaultValue = []) => {
  return Array.isArray(value) ? value : defaultValue;
};

/**
 * 안전한 객체 속성 접근
 * @param {Object} obj - 객체
 * @param {string} path - 경로 (예: 'a.b.c')
 * @param {any} defaultValue - 기본값
 * @returns {any} 값 또는 기본값
 */
export const safeGet = (obj, path, defaultValue = null) => {
  try {
    return path.split('.').reduce((current, key) => {
      return current && typeof current === 'object' && key in current ? current[key] : defaultValue;
    }, obj);
  } catch (error) {
    console.warn('SafeGet 실패:', error.message || error);
    return defaultValue;
  }
};

/**
 * 백하위 호환성을 위한 레거시 응답 감지
 * @param {Object} response - 응답 데이터
 * @returns {boolean} 레거시 응답 여부
 */
export const isLegacyResponse = (response) => {
  if (!response || typeof response !== 'object') return false;

  // 레거시 응답의 특징: ocrResults, aiResults가 직접 있고 cimData가 없음
  return (
    (response.ocrResults || response.ocr_results) &&
    !response.cimData &&
    !response.cim_data
  );
};

/**
 * 응답 데이터 자동 정규화 (레거시와 CIM 모두 처리)
 * @param {Object} response - 원본 응답
 * @returns {Object} 정규화된 데이터
 */
export const normalizeAnalysisResponse = (response) => {
  if (!response) return createEmptyNormalizedData();

  // 레거시 응답인 경우 그대로 사용
  if (isLegacyResponse(response)) {
    if (process.env.NODE_ENV === 'development') {
    console.debug('레거시 응답 감지, 기존 구조 사용');
  }
    return {
      ...response,
      ocrResults: safeArray(response.ocrResults || response.ocr_results),
      aiResults: safeArray(response.aiResults || response.ai_results),
      stats: response.stats || {},
      cimData: null
    };
  }

  // CIM 응답인 경우 정규화 수행
  if (process.env.NODE_ENV === 'development') {
    console.debug('CIM 응답 감지, 정규화 수행');
  }
  return normalizeCIMResponse(response);
};

/**
 * 텍스트 내용의 유효성 검증
 * @param {string} text - 검증할 텍스트
 * @returns {boolean} 유효 여부
 */
export const isValidTextContent = (text) => {
  if (!text || typeof text !== 'string') return false;

  const trimmedText = text.trim();

  // 최소 길이 확인
  if (trimmedText.length < 1) return false;

  // 공백만 있는 경우 제외
  if (/^\s*$/.test(trimmedText)) return false;

  // 의미있는 텍스트인지 확인
  return true;
};

/**
 * 텍스트 내용 정제
 * @param {string} text - 정제할 텍스트
 * @returns {string} 정제된 텍스트
 */
export const sanitizeText = (text) => {
  if (!text || typeof text !== 'string') return '';

  return text
    .trim()
    .replace(/\s+/g, ' ') // 연속된 공백을 하나로
    .replace(/[\r\n]+/g, '\n') // 연속된 줄바꿈을 하나로
    .replace(/[^\x20-\x7E\uAC00-\uD7AF\u3131-\u318E\u1100-\u11FF]/g, '') // 한글, 영문, 숫자, 기본 기호만 유지
    .trim();
};

/**
 * 텍스트 신뢰도 계산
 * @param {string} text - 신뢰도를 계산할 텍스트
 * @param {number} baseConfidence - 기본 신뢰도 (0-1)
 * @returns {number} 계산된 신뢰도 (0-1)
 */
export const calculateTextConfidence = (text, baseConfidence = 0.8) => {
  if (!text || typeof text !== 'string') return 0;

  const trimmedText = text.trim();
  if (trimmedText.length === 0) return 0;

  let confidence = baseConfidence;

  // 길이에 따른 신뢰도 조정
  if (trimmedText.length < 3) {
    confidence *= 0.7; // 매우 짧은 텍스트는 신뢰도 감소
  } else if (trimmedText.length > 100) {
    confidence *= 1.1; // 긴 텍스트는 신뢰도 증가
  }

  // 한글/영문 비율에 따른 조정
  const koreanRatio = (trimmedText.match(/[\uAC00-\uD7AF]/g) || []).length / trimmedText.length;
  const englishRatio = (trimmedText.match(/[a-zA-Z]/g) || []).length / trimmedText.length;

  if (koreanRatio > 0.5 || englishRatio > 0.5) {
    confidence *= 1.05; // 의미있는 언어 비율이 높으면 신뢰도 증가
  }

  // 특수문자 비율 확인
  const specialCharRatio = (trimmedText.match(/[^\w\s\uAC00-\uD7AF]/g) || []).length / trimmedText.length;
  if (specialCharRatio > 0.3) {
    confidence *= 0.8; // 특수문자가 많으면 신뢰도 감소
  }

  // 신뢰도 범위 제한 (0-1)
  return Math.min(1, Math.max(0, confidence));
};

/**
 * 컴포넌트에서 사용할 정규화 결과 생성 (개선된 버전)
 * analysisResults가 이미 정규화되었는지 확인 후 필요시 정규화 수행
 * @param {Object} analysisResults - 분석 결과 데이터
 * @returns {Object} 정규화된 데이터
 */
export const normalizeAnalysisResults = (analysisResults) => {
  if (!analysisResults) return createEmptyNormalizedData();

  try {
    // 이미 정규화된 데이터인지 확인
    const hasNormalizedStructure =
      analysisResults.hasOwnProperty('ocrResults') &&
      analysisResults.hasOwnProperty('aiResults') &&
      analysisResults.hasOwnProperty('stats');

    if (hasNormalizedStructure) {
      // 이미 정규화됨, 데이터 품질 검증 및 안전성 확인
      const normalizedOcr = safeArray(analysisResults.ocrResults)
        .filter(item => item && typeof item === 'object')
        .map(item => normalizeOCRItem(item))
        .filter(item => item.text.trim() !== '');

      const normalizedAi = safeArray(analysisResults.aiResults)
        .filter(item => item && typeof item === 'object')
        .map(item => normalizeAIItem(item))
        .filter(item => (item.description || item.text || '').trim() !== '');

      return {
        ...analysisResults,
        ocrResults: normalizedOcr,
        aiResults: normalizedAi,
        stats: analysisResults.stats || {}
      };
    }

    // 정규화되지 않은 데이터, 정규화 수행
    const normalized = normalizeAnalysisResponse(analysisResults);

    // 정규화 후 데이터 품질 검증
    if (process.env.NODE_ENV === 'development') {
      console.log('🔍 정규화 결과:', {
        ocrCount: normalized.ocrResults.length,
        aiCount: normalized.aiResults.length,
        hasStats: !!normalized.stats,
        hasCimData: !!normalized.cimData
      });
    }

    return normalized;
  } catch (error) {
    console.error('정규화 실패:', error.message || error);
    return createEmptyNormalizedData();
  }
};

export default {
  normalizeCIMResponse,
  normalizeAnalysisResponse,
  normalizeAnalysisResults,
  safeArray,
  safeGet,
  isLegacyResponse,
  isValidTextContent,
  sanitizeText,
  calculateTextConfidence
};