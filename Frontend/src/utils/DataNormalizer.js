/**
 * DataNormalizer - 방어적 데이터 처리 및 백엔드 변경 대응 클래스
 * 백엔드 데이터 스키마 변경에 강건한 구조 제공
 */

class DataNormalizer {
  constructor(options = {}) {
    this.options = {
      enableLogging: options.enableLogging ?? (process.env.NODE_ENV === 'development'),
      strictMode: options.strictMode ?? false,
      maxRecursionDepth: options.maxRecursionDepth ?? 5,
      errorStrategy: options.errorStrategy ?? 'fallback', // 'throw', 'fallback', 'ignore'
      schemaVersion: options.schemaVersion ?? 'auto',
      ...options
    };

    // 에러 통계 수집
    this.errorStats = {
      totalErrors: 0,
      errorTypes: {},
      lastError: null
    };

    // 스키마 패턴 정의 (백엔드 변경 대응)
    this.schemaPatterns = {
      ocr: {
        textFields: ['text', 'content', 'extracted_text', 'ocr_text', 'recognized_text'],
        confidenceFields: ['confidence', 'conf', 'ocr_confidence', 'text_confidence', 'score'],
        bboxFields: ['bbox', 'bounding_box', 'coordinates', 'position', 'location', 'rect'],
        typeFields: ['element_type', 'elementType', 'type', 'classification', 'category', 'class']
      },
      ai: {
        descriptionFields: ['description', 'content', 'text', 'explanation', 'analysis'],
        confidenceFields: ['confidence', 'conf', 'score', 'certainty'],
        typeFields: ['type', 'element_type', 'category', 'kind']
      },
      cim: {
        questionsFields: ['questions', 'question_list', 'problems', 'items'],
        analysisFields: ['analysis', 'ai_analysis', 'structured_analysis'],
        contentFields: ['question_content', 'content', 'data']
      }
    };
  }

  /**
   * 메인 정규화 함수
   * @param {any} data - 정규화할 데이터
   * @param {Object} context - 처리 컨텍스트
   * @returns {Object} 정규화된 데이터
   */
  normalize(data, context = {}) {
    const startTime = performance.now();

    try {
      // 1단계: 입력 검증
      const validationResult = this.validateInput(data);
      if (!validationResult.isValid) {
        return this.handleError('validation', validationResult.error, data, context);
      }

      // 2단계: 스키마 감지
      const detectedSchema = this.detectSchema(data);
      this.log('스키마 감지 결과:', detectedSchema);

      // 3단계: 데이터 정규화
      const normalizedData = this.normalizeBySchema(data, detectedSchema);

      // 4단계: 품질 검증
      const qualityCheck = this.validateQuality(normalizedData);

      // 5단계: 메타데이터 추가
      const result = {
        ...normalizedData,
        _meta: {
          processingTime: performance.now() - startTime,
          schema: detectedSchema,
          quality: qualityCheck,
          timestamp: Date.now(),
          normalizerVersion: '1.0.0'
        }
      };

      this.log('정규화 완료:', {
        processingTime: result._meta.processingTime.toFixed(2) + 'ms',
        schema: detectedSchema.type,
        quality: qualityCheck.score
      });

      return result;

    } catch (error) {
      return this.handleError('processing', error, data, context);
    }
  }

  /**
   * 입력 데이터 검증
   * @param {any} data - 검증할 데이터
   * @returns {Object} 검증 결과
   */
  validateInput(data) {
    if (data === null || data === undefined) {
      return { isValid: false, error: 'Data is null or undefined' };
    }

    if (typeof data !== 'object') {
      return { isValid: false, error: 'Data must be an object' };
    }

    // 순환 참조 검사
    try {
      JSON.stringify(data);
    } catch (error) {
      if (error.message.includes('circular')) {
        return { isValid: false, error: 'Circular reference detected' };
      }
    }

    return { isValid: true };
  }

  /**
   * 데이터 스키마 감지
   * @param {Object} data - 분석할 데이터
   * @returns {Object} 감지된 스키마 정보
   */
  detectSchema(data) {
    const features = {
      hasOcrResults: false,
      hasAiResults: false,
      hasCimData: false,
      hasQuestions: false,
      isLegacy: false,
      confidence: 0
    };

    // OCR 데이터 감지
    const ocrFields = this.schemaPatterns.ocr.textFields;
    for (const field of ocrFields) {
      if (this.findByPattern(data, field)) {
        features.hasOcrResults = true;
        features.confidence += 0.3;
        break;
      }
    }

    // AI 데이터 감지
    const aiFields = this.schemaPatterns.ai.descriptionFields;
    for (const field of aiFields) {
      if (this.findByPattern(data, field)) {
        features.hasAiResults = true;
        features.confidence += 0.2;
        break;
      }
    }

    // CIM 구조 감지
    const cimFields = this.schemaPatterns.cim.questionsFields;
    for (const field of cimFields) {
      const found = this.findByPattern(data, field);
      if (found && Array.isArray(found)) {
        features.hasCimData = true;
        features.hasQuestions = true;
        features.confidence += 0.4;
        break;
      }
    }

    // 레거시 구조 감지
    if (data.ocrResults && data.aiResults && !data.cimData) {
      features.isLegacy = true;
      features.confidence += 0.3;
    }

    // 스키마 타입 결정
    let schemaType = 'unknown';
    if (features.hasQuestions && features.hasCimData) {
      schemaType = 'cim_structured';
    } else if (features.hasCimData) {
      schemaType = 'cim_basic';
    } else if (features.isLegacy) {
      schemaType = 'legacy';
    } else if (features.hasOcrResults || features.hasAiResults) {
      schemaType = 'partial';
    }

    return {
      type: schemaType,
      features,
      confidence: Math.min(1.0, features.confidence)
    };
  }

  /**
   * 스키마에 따른 데이터 정규화
   * @param {Object} data - 정규화할 데이터
   * @param {Object} schema - 스키마 정보
   * @returns {Object} 정규화된 데이터
   */
  normalizeBySchema(data, schema) {
    switch (schema.type) {
      case 'cim_structured':
        return this.normalizeCIMStructured(data);

      case 'cim_basic':
        return this.normalizeCIMBasic(data);

      case 'legacy':
        return this.normalizeLegacy(data);

      case 'partial':
        return this.normalizePartial(data);

      default:
        this.log('알 수 없는 스키마, 기본 정규화 적용');
        return this.normalizeDefault(data);
    }
  }

  /**
   * CIM 구조화된 데이터 정규화
   * @param {Object} data - CIM 구조화 데이터
   * @returns {Object} 정규화된 결과
   */
  normalizeCIMStructured(data) {
    const result = {
      ocrResults: [],
      aiResults: [],
      stats: {},
      cimData: null,
      formattedText: '',
      layoutImageUrl: '',
      jsonUrl: ''
    };

    try {
      // CIM 데이터 추출
      const cimData = this.findByPattern(data, 'cimData') ||
                     this.findByPattern(data, 'cim_data') ||
                     data;

      if (cimData) {
        result.cimData = cimData;

        // 구조화된 문제 데이터 처리
        const questions = this.findByPattern(cimData, 'questions') || [];
        if (Array.isArray(questions)) {
          questions.forEach((question, qIndex) => {
            this.extractFromQuestion(question, qIndex, result);
          });
        }

        // 기본 메타데이터 추출
        result.formattedText = this.findByPattern(data, 'formattedText') ||
                              this.findByPattern(data, 'formatted_text') || '';
        result.layoutImageUrl = this.findByPattern(data, 'layoutImageUrl') ||
                               this.findByPattern(data, 'layout_image_url') || '';
        result.jsonUrl = this.findByPattern(data, 'jsonUrl') ||
                        this.findByPattern(data, 'json_url') || '';
      }

      // 통계 생성
      result.stats = this.generateStats(result.ocrResults, result.aiResults);

    } catch (error) {
      this.handleError('cim_structured_normalization', error, data);
    }

    return result;
  }

  /**
   * 문제 데이터에서 OCR/AI 결과 추출
   * @param {Object} question - 문제 데이터
   * @param {number} qIndex - 문제 인덱스
   * @param {Object} result - 결과 객체 (참조로 수정)
   */
  extractFromQuestion(question, qIndex, result) {
    try {
      const questionContent = this.findByPattern(question, 'question_content') || {};

      // 메인 문제 텍스트
      const mainQuestion = this.findByPattern(questionContent, 'main_question');
      if (mainQuestion) {
        result.ocrResults.push({
          text: mainQuestion,
          confidence: 0.95,
          element_type: 'question_text',
          block_id: `q${qIndex}_main`,
          source: 'structured_analysis'
        });
      }

      // 지문 텍스트
      const passage = this.findByPattern(questionContent, 'passage');
      if (passage) {
        result.ocrResults.push({
          text: passage,
          confidence: 0.95,
          element_type: 'passage',
          block_id: `q${qIndex}_passage`,
          source: 'structured_analysis'
        });
      }

      // 선택지 처리
      const choices = this.findByPattern(questionContent, 'choices') || [];
      if (Array.isArray(choices)) {
        choices.forEach((choice, cIndex) => {
          const choiceText = this.findByPattern(choice, 'choice_text');
          if (choiceText) {
            result.ocrResults.push({
              text: choiceText,
              confidence: 0.95,
              element_type: 'choice',
              block_id: `q${qIndex}_choice${cIndex}`,
              choice_number: this.findByPattern(choice, 'choice_number'),
              source: 'structured_analysis'
            });
          }
        });
      }

      // AI 분석 결과 처리
      const aiAnalysis = this.findByPattern(question, 'ai_analysis') || {};
      this.extractAIAnalysis(aiAnalysis, qIndex, result);

    } catch (error) {
      this.handleError('question_extraction', error, question);
    }
  }

  /**
   * AI 분석 결과 추출
   * @param {Object} aiAnalysis - AI 분석 데이터
   * @param {number} qIndex - 문제 인덱스
   * @param {Object} result - 결과 객체
   */
  extractAIAnalysis(aiAnalysis, qIndex, result) {
    const analysisTypes = [
      { field: 'image_descriptions', type: 'image_description' },
      { field: 'table_analysis', type: 'table_analysis' },
      { field: 'problem_analysis', type: 'problem_analysis' }
    ];

    analysisTypes.forEach(({ field, type }) => {
      const analysisData = this.findByPattern(aiAnalysis, field) || [];
      if (Array.isArray(analysisData)) {
        analysisData.forEach((item, index) => {
          if (item && typeof item === 'string' && item.trim()) {
            result.aiResults.push({
              description: item,
              confidence: 0.8,
              type: type,
              source: 'structured_ai_analysis',
              block_id: `q${qIndex}_ai_${type}_${index}`
            });
          }
        });
      }
    });
  }

  /**
   * 레거시 데이터 정규화
   * @param {Object} data - 레거시 데이터
   * @returns {Object} 정규화된 결과
   */
  normalizeLegacy(data) {
    return {
      ocrResults: this.normalizeArray(data.ocrResults || [], this.normalizeOCRItem.bind(this)),
      aiResults: this.normalizeArray(data.aiResults || [], this.normalizeAIItem.bind(this)),
      stats: data.stats || {},
      cimData: null,
      formattedText: data.formattedText || '',
      layoutImageUrl: data.layoutImageUrl || '',
      jsonUrl: data.jsonUrl || ''
    };
  }

  /**
   * 부분 데이터 정규화
   * @param {Object} data - 부분 데이터
   * @returns {Object} 정규화된 결과
   */
  normalizePartial(data) {
    const result = {
      ocrResults: [],
      aiResults: [],
      stats: {},
      cimData: null,
      formattedText: '',
      layoutImageUrl: '',
      jsonUrl: ''
    };

    // 다양한 경로에서 데이터 추출 시도
    const ocrPaths = ['ocrResults', 'ocr_results', 'textBlocks', 'text_blocks'];
    const aiPaths = ['aiResults', 'ai_results', 'aiDescriptions', 'ai_descriptions'];

    for (const path of ocrPaths) {
      const found = this.findByPattern(data, path);
      if (Array.isArray(found)) {
        result.ocrResults = this.normalizeArray(found, this.normalizeOCRItem.bind(this));
        break;
      }
    }

    for (const path of aiPaths) {
      const found = this.findByPattern(data, path);
      if (Array.isArray(found)) {
        result.aiResults = this.normalizeArray(found, this.normalizeAIItem.bind(this));
        break;
      }
    }

    result.stats = this.generateStats(result.ocrResults, result.aiResults);
    return result;
  }

  /**
   * 기본 정규화
   * @param {Object} data - 기본 데이터
   * @returns {Object} 정규화된 결과
   */
  normalizeDefault(data) {
    return {
      ocrResults: [],
      aiResults: [],
      stats: {},
      cimData: data,
      formattedText: '',
      layoutImageUrl: '',
      jsonUrl: ''
    };
  }

  /**
   * 패턴 기반 데이터 찾기 (대소문자 무시, 다양한 구분자 지원)
   * @param {Object} obj - 검색할 객체
   * @param {string} pattern - 찾을 패턴
   * @returns {any} 찾은 데이터 또는 null
   */
  findByPattern(obj, pattern) {
    if (!obj || typeof obj !== 'object') return null;

    // 정확한 일치
    if (obj[pattern] !== undefined) return obj[pattern];

    // 대소문자 무시 검색
    const lowerPattern = pattern.toLowerCase();
    for (const [key, value] of Object.entries(obj)) {
      if (key.toLowerCase() === lowerPattern) return value;
    }

    // snake_case, camelCase 변환 시도
    const variations = [
      pattern.replace(/_/g, ''),          // snake_case -> camelCase 부분
      pattern.replace(/([A-Z])/g, '_$1').toLowerCase(), // camelCase -> snake_case
      pattern.replace(/_(.)/g, (_, c) => c.toUpperCase()) // snake_case -> camelCase
    ];

    for (const variation of variations) {
      if (obj[variation] !== undefined) return obj[variation];
    }

    return null;
  }

  /**
   * 배열 정규화 (안전한 처리)
   * @param {Array} arr - 정규화할 배열
   * @param {Function} normalizer - 항목 정규화 함수
   * @returns {Array} 정규화된 배열
   */
  normalizeArray(arr, normalizer) {
    if (!Array.isArray(arr)) return [];

    return arr
      .filter(item => item != null)
      .map(item => {
        try {
          return normalizer(item);
        } catch (error) {
          this.handleError('array_item_normalization', error, item);
          return null;
        }
      })
      .filter(item => item != null);
  }

  /**
   * OCR 항목 정규화
   * @param {Object} item - OCR 항목
   * @returns {Object} 정규화된 OCR 항목
   */
  normalizeOCRItem(item) {
    if (!item || typeof item !== 'object') {
      return { text: '', confidence: 0, element_type: 'unknown' };
    }

    return {
      text: this.extractText(item),
      confidence: this.extractConfidence(item),
      element_type: this.extractType(item),
      bbox: this.extractBBox(item),
      block_id: item.block_id || item.blockId || item.id || this.generateId(),
      ...this.extractAdditionalFields(item, ['page_num', 'layout_class', 'text_class'])
    };
  }

  /**
   * AI 항목 정규화
   * @param {Object} item - AI 항목
   * @returns {Object} 정규화된 AI 항목
   */
  normalizeAIItem(item) {
    if (!item || typeof item !== 'object') {
      return { description: '', confidence: 0, type: 'unknown' };
    }

    return {
      description: this.extractDescription(item),
      confidence: this.extractConfidence(item),
      type: this.extractType(item),
      bbox: this.extractBBox(item),
      ...this.extractAdditionalFields(item, ['source', 'category'])
    };
  }

  /**
   * 텍스트 추출 (다양한 필드명 지원)
   * @param {Object} item - 데이터 항목
   * @returns {string} 추출된 텍스트
   */
  extractText(item) {
    const textFields = this.schemaPatterns.ocr.textFields;
    for (const field of textFields) {
      const value = this.findByPattern(item, field);
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
    return '';
  }

  /**
   * 설명 추출 (AI 항목용)
   * @param {Object} item - AI 항목
   * @returns {string} 추출된 설명
   */
  extractDescription(item) {
    const descFields = this.schemaPatterns.ai.descriptionFields;
    for (const field of descFields) {
      const value = this.findByPattern(item, field);
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
    return '';
  }

  /**
   * 신뢰도 추출
   * @param {Object} item - 데이터 항목
   * @returns {number} 추출된 신뢰도 (0-1)
   */
  extractConfidence(item) {
    const confFields = this.schemaPatterns.ocr.confidenceFields;
    for (const field of confFields) {
      const value = this.findByPattern(item, field);
      if (typeof value === 'number' && value >= 0 && value <= 1) {
        return value;
      }
      if (typeof value === 'string') {
        const parsed = parseFloat(value);
        if (!isNaN(parsed) && parsed >= 0 && parsed <= 1) {
          return parsed;
        }
      }
    }
    return 0;
  }

  /**
   * 타입 추출
   * @param {Object} item - 데이터 항목
   * @returns {string} 추출된 타입
   */
  extractType(item) {
    const typeFields = this.schemaPatterns.ocr.typeFields;
    for (const field of typeFields) {
      const value = this.findByPattern(item, field);
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
    return 'text';
  }

  /**
   * 바운딩 박스 추출 및 정규화
   * @param {Object} item - 데이터 항목
   * @returns {Object|null} 정규화된 바운딩 박스
   */
  extractBBox(item) {
    const bboxFields = this.schemaPatterns.ocr.bboxFields;
    for (const field of bboxFields) {
      const value = this.findByPattern(item, field);
      if (value) {
        return this.normalizeBBox(value);
      }
    }
    return null;
  }

  /**
   * 바운딩 박스 정규화
   * @param {any} bbox - 바운딩 박스 데이터
   * @returns {Object|null} 정규화된 바운딩 박스
   */
  normalizeBBox(bbox) {
    if (!bbox) return null;

    try {
      // 배열 형태 처리
      if (Array.isArray(bbox) && bbox.length >= 4) {
        const [x, y, w, h] = bbox.map(v => parseFloat(v) || 0);
        return {
          x, y, width: w, height: h,
          x1: x, y1: y, x2: x + w, y2: y + h
        };
      }

      // 객체 형태 처리
      if (typeof bbox === 'object') {
        const x = parseFloat(bbox.x || bbox.left || bbox.x1 || 0);
        const y = parseFloat(bbox.y || bbox.top || bbox.y1 || 0);
        const width = parseFloat(bbox.width || bbox.w || (bbox.x2 - x) || 0);
        const height = parseFloat(bbox.height || bbox.h || (bbox.y2 - y) || 0);

        return {
          x, y, width, height,
          x1: x, y1: y,
          x2: x + width, y2: y + height
        };
      }
    } catch (error) {
      this.log('바운딩 박스 정규화 실패:', error);
    }

    return null;
  }

  /**
   * 추가 필드 추출
   * @param {Object} item - 데이터 항목
   * @param {Array} fields - 추출할 필드명 배열
   * @returns {Object} 추출된 필드들
   */
  extractAdditionalFields(item, fields) {
    const result = {};
    fields.forEach(field => {
      const value = this.findByPattern(item, field);
      if (value !== null && value !== undefined) {
        result[field] = value;
      }
    });
    return result;
  }

  /**
   * 통계 생성
   * @param {Array} ocrResults - OCR 결과
   * @param {Array} aiResults - AI 결과
   * @returns {Object} 생성된 통계
   */
  generateStats(ocrResults, aiResults) {
    const totalElements = ocrResults.length + aiResults.length;
    const totalCharacters = ocrResults.reduce((sum, item) => sum + (item.text?.length || 0), 0);

    const validConfidences = [...ocrResults, ...aiResults]
      .map(item => item.confidence)
      .filter(conf => typeof conf === 'number' && conf >= 0 && conf <= 1);

    const averageConfidence = validConfidences.length > 0
      ? validConfidences.reduce((sum, conf) => sum + conf, 0) / validConfidences.length
      : 0;

    return {
      total_elements: totalElements,
      total_characters: totalCharacters,
      average_confidence: averageConfidence,
      ocr_block_count: ocrResults.length,
      ai_analysis_count: aiResults.length,
      processing_time: 0,
      element_counts: this.countElementTypes([...ocrResults, ...aiResults])
    };
  }

  /**
   * 요소 타입별 카운트
   * @param {Array} items - 데이터 항목들
   * @returns {Object} 타입별 카운트
   */
  countElementTypes(items) {
    const counts = {};
    items.forEach(item => {
      const type = item.element_type || item.type || 'unknown';
      counts[type] = (counts[type] || 0) + 1;
    });
    return counts;
  }

  /**
   * 품질 검증
   * @param {Object} data - 검증할 데이터
   * @returns {Object} 품질 평가 결과
   */
  validateQuality(data) {
    const issues = [];
    let score = 100;

    // 데이터 존재 여부
    if (!data.ocrResults?.length && !data.aiResults?.length) {
      issues.push('데이터가 없음');
      score -= 50;
    }

    // 신뢰도 검사
    const lowConfidenceItems = [...(data.ocrResults || []), ...(data.aiResults || [])]
      .filter(item => item.confidence < 0.5);

    if (lowConfidenceItems.length > 0) {
      issues.push(`낮은 신뢰도 항목 ${lowConfidenceItems.length}개`);
      score -= Math.min(30, lowConfidenceItems.length * 5);
    }

    // 텍스트 품질 검사
    const emptyTexts = (data.ocrResults || []).filter(item => !item.text?.trim());
    if (emptyTexts.length > 0) {
      issues.push(`빈 텍스트 항목 ${emptyTexts.length}개`);
      score -= Math.min(20, emptyTexts.length * 2);
    }

    return {
      score: Math.max(0, score),
      issues,
      grade: score >= 80 ? 'A' : score >= 60 ? 'B' : score >= 40 ? 'C' : 'D'
    };
  }

  /**
   * 에러 처리
   * @param {string} type - 에러 타입
   * @param {Error} error - 에러 객체
   * @param {any} data - 관련 데이터
   * @param {Object} context - 처리 컨텍스트
   * @returns {Object} 에러 처리 결과
   */
  handleError(type, error, data, context = {}) {
    this.errorStats.totalErrors++;
    this.errorStats.errorTypes[type] = (this.errorStats.errorTypes[type] || 0) + 1;
    this.errorStats.lastError = {
      type,
      message: error.message || error,
      timestamp: Date.now()
    };

    const errorMessage = `DataNormalizer ${type} error: ${error.message || error}`;
    this.log('에러 발생:', errorMessage);

    if (this.options.errorStrategy === 'throw') {
      throw new Error(errorMessage);
    }

    if (this.options.errorStrategy === 'ignore') {
      return data;
    }

    // 'fallback' 전략 (기본값)
    return {
      ocrResults: [],
      aiResults: [],
      stats: {},
      cimData: null,
      formattedText: '',
      layoutImageUrl: '',
      jsonUrl: '',
      _error: errorMessage,
      _originalData: data
    };
  }

  /**
   * 고유 ID 생성
   * @returns {string} 생성된 ID
   */
  generateId() {
    return `id_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
  }

  /**
   * 로깅
   * @param {...any} args - 로그 인수들
   */
  log(...args) {
    if (this.options.enableLogging) {
      console.debug('[DataNormalizer]', ...args);
    }
  }

  /**
   * 에러 통계 조회
   * @returns {Object} 에러 통계
   */
  getErrorStats() {
    return { ...this.errorStats };
  }

  /**
   * 통계 초기화
   */
  resetStats() {
    this.errorStats = {
      totalErrors: 0,
      errorTypes: {},
      lastError: null
    };
  }
}

export default DataNormalizer;