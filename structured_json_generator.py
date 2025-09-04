# -*- coding: utf-8 -*-
"""
구조화된 JSON 생성기 - 문제별 정렬된 결과 생성
"""

import re
from typing import Dict, List, Optional
from layout_analyzer_enhanced import EnhancedLayoutAnalyzer
from loguru import logger

class StructuredJSONGenerator:
    def __init__(self):
        self.layout_analyzer = EnhancedLayoutAnalyzer()
    
    def generate_structured_json(self, ocr_results: List, ai_results: List, layout_elements: List) -> Dict:
        """구조화된 JSON 생성 (디버깅 강화)"""
        
        # OCR 결과 캐시 (AI 매핑에서 사용)
        self._cached_ocr_results = ocr_results
        
        # 🔍 디버깅: 입력 데이터 확인
        logger.info(f"🔍 [DEBUG] OCR 결과 개수: {len(ocr_results)}")
        logger.info(f"🔍 [DEBUG] AI 결과 개수: {len(ai_results)}")
        logger.info(f"🔍 [DEBUG] 레이아웃 요소 개수: {len(layout_elements)}")
        
        # OCR 결과 샘플 출력
        if ocr_results:
            logger.info(f"🔍 [DEBUG] OCR 샘플: {ocr_results[0]}")
        
        # AI 결과 샘플 출력
        if ai_results:
            logger.info(f"🔍 [DEBUG] AI 샘플: {ai_results[0]}")
        
        # 레이아웃 요소 샘플 출력
        if layout_elements:
            logger.info(f"🔍 [DEBUG] 레이아웃 샘플: {layout_elements[0]}")
        
        
        # 1. 문제 구조 분석
        logger.info("🔧 문제 구조 분석 시작...")
        structure = self.layout_analyzer.detect_question_structure(ocr_results, layout_elements)
        
        logger.info(f"🔍 [DEBUG] 감지된 문제 구조: {structure}")
        
        # 2. AI 결과를 문제별로 분류
        ai_by_question = self._classify_ai_results_by_question(ai_results, structure)
        
        # 3. 최종 구조화된 결과 생성
        structured_result = {
            'document_info': {
                'total_questions': structure['total_questions'],
                'layout_type': structure['layout_type'],
                'sections': structure['sections']
            },
            'questions': []
        }
        
        # 4. 각 문제별로 정리
        for q_num, question_data in structure['questions'].items():
            logger.info(f"🔍 [DEBUG] 문제 {q_num} 처리 중: {question_data}")
            
            question_result = self._format_question_result(
                q_num, question_data, ai_by_question.get(q_num, [])
            )
            structured_result['questions'].append(question_result)
        
        logger.info(f"🔍 [DEBUG] 최종 구조화 결과: {len(structured_result['questions'])}개 문제")
        
        return structured_result
    
    def _classify_ai_results_by_question(self, ai_results: List, structure: Dict) -> Dict:
        """AI 결과를 문제별로 분류 (디버깅 강화)"""
        ai_by_question = {}
        
        logger.info(f"🔍 [DEBUG] AI 결과 분류 시작: {len(ai_results)}개 항목")
        
        for i, result in enumerate(ai_results):
            logger.info(f"🔍 [DEBUG] AI 결과 {i}: {result}")
            
            # AI 결과의 위치나 내용을 기반으로 문제 번호 추정
            question_num = self._estimate_question_for_ai_result(result, structure)
            logger.info(f"🔍 [DEBUG] AI 결과 {i} → 문제 {question_num}에 할당")
            
            if question_num not in ai_by_question:
                ai_by_question[question_num] = []
            
            ai_by_question[question_num].append(result)
        
        logger.info(f"🔍 [DEBUG] AI 분류 완료: {ai_by_question}")
        return ai_by_question
    
    def _estimate_question_for_ai_result(self, result: Dict, structure: Dict) -> str:
        """AI 결과가 어느 문제에 속하는지 추정 (개선)"""
        
        # coordinates 키 확인
        ai_coords = result.get('coordinates', [])
        if not ai_coords or len(ai_coords) < 2:
            logger.warning(f"🔍 [DEBUG] AI 결과에 유효한 coordinates 없음: {result}")
            return "unknown"
        
        ai_y = ai_coords[1]
        logger.info(f"🔍 [DEBUG] AI 결과 Y 좌표: {ai_y}")
        
        # 가장 가까운 문제 찾기
        best_question = "unknown"
        min_distance = float('inf')
        
        # 문제별 Y 좌표를 OCR 결과에서 직접 찾기
        questions = structure.get('questions', {})
        for q_num in questions.keys():
            q_y = self._get_question_y_from_ocr(q_num)
            
            if q_y is not None:
                distance = abs(ai_y - q_y)
                logger.debug(f"🔍 [DEBUG] 문제 {q_num} Y={q_y}, AI Y={ai_y}, 거리={distance}")
                
                if distance < min_distance:
                    min_distance = distance
                    best_question = q_num
        
        # 거리 임계값 확인 (너무 멀면 unknown)
        if min_distance > 500:  # 500px 이상 차이나면 unknown
            logger.warning(f"🔍 [DEBUG] 가장 가까운 문제와의 거리가 너무 큼: {min_distance}px")
            best_question = "unknown"
        
        logger.info(f"🔍 [DEBUG] 가장 가까운 문제: {best_question} (거리: {min_distance})")
        return best_question
    
    def _get_question_y_from_ocr(self, question_num: str) -> Optional[int]:
        """OCR 결과에서 특정 문제 번호의 Y 좌표 찾기"""
        if hasattr(self, '_cached_ocr_results'):
            for result in self._cached_ocr_results:
                text = result.get('text', '').strip()
                class_name = result.get('class_name', '')
                
                if text == question_num and class_name == 'question_number':
                    coords = result.get('coordinates', [])
                    if len(coords) > 1:
                        return coords[1]
        return None
    
    def _format_question_result(self, q_num: str, question_data: Dict, ai_results: List) -> Dict:
        """문제별 결과 포맷팅 (디버깅 강화)"""
        
        logger.info(f"🔍 [DEBUG] 문제 {q_num} 포맷팅: elements={question_data.get('elements', {}).keys()}")
        logger.info(f"🔍 [DEBUG] 문제 {q_num} AI 결과: {len(ai_results)}개")
        
        elements = question_data.get('elements', {})
        
        # 각 요소별 개수 로깅
        for element_type, element_list in elements.items():
            logger.info(f"🔍 [DEBUG] 문제 {q_num} - {element_type}: {len(element_list)}개")
            if element_list:
                logger.info(f"🔍 [DEBUG] 문제 {q_num} - {element_type} 샘플: {element_list[0]}")
        
        return {
            'question_number': q_num,
            'section': question_data.get('section'),
            'question_content': {
                'main_question': self._extract_main_question(elements),
                'passage': self._combine_texts(elements.get('passage', [])),
                'choices': self._format_choices(elements.get('choices', [])),
                'images': self._format_images(elements.get('images', []), ai_results),
                'tables': self._format_tables(elements.get('tables', []), ai_results),
                'explanations': self._combine_texts(elements.get('explanations', []))
            },
            'ai_analysis': {
                'image_descriptions': [r for r in ai_results if r.get('class_name') == 'figure'],
                'table_analysis': [r for r in ai_results if r.get('class_name') == 'table'],
                'problem_analysis': [r for r in ai_results if r.get('class_name') not in ['figure', 'table']]
            }
        }
    
    def _extract_main_question(self, elements: Dict) -> str:
        """주요 문제 텍스트 추출 (디버깅 강화)"""
        question_texts = elements.get('question_text', [])
        logger.info(f"🔍 [DEBUG] 주요 문제 텍스트 추출: {len(question_texts)}개 후보")
        
        if question_texts:
            # 가장 긴 텍스트를 주요 문제로 간주
            main_text = max(question_texts, key=lambda x: len(x.get('text', '')))
            result = main_text.get('text', '')
            logger.info(f"🔍 [DEBUG] 선택된 주요 문제: '{result[:50]}...'")
            return result
        
        logger.info(f"🔍 [DEBUG] 주요 문제 텍스트 없음")
        return ""
    
    def _combine_texts(self, text_elements: List) -> str:
        """텍스트 요소들 결합 (디버깅 강화)"""
        if not text_elements:
            return ""
        
        logger.info(f"🔍 [DEBUG] 텍스트 결합: {len(text_elements)}개 요소")
        
        # Y 좌표 순으로 정렬 후 결합
        sorted_elements = sorted(text_elements, key=lambda x: x.get('bbox', [0, 0, 0, 0])[1])
        result = " ".join([elem.get('text', '') for elem in sorted_elements])
        
        logger.info(f"🔍 [DEBUG] 결합된 텍스트: '{result[:50]}...'")
        return result
    
    def _format_choices(self, choice_elements: List) -> List[Dict]:
        """선택지 포맷팅"""
        if not choice_elements:
            return []
        
        # Y 좌표 순으로 정렬
        sorted_choices = sorted(choice_elements, key=lambda x: x['bbox'][1])
        
        formatted_choices = []
        for i, choice in enumerate(sorted_choices):
            formatted_choices.append({
                'choice_number': self._extract_choice_number(choice['text']),
                'choice_text': choice['text'],
                'bbox': choice['bbox']
            })
        
        return formatted_choices
    
    def _format_images(self, image_elements: List, ai_results: List) -> List[Dict]:
        """이미지 포맷팅"""
        formatted_images = []
        
        for image in image_elements:
            # 해당 이미지에 대한 AI 설명 찾기
            description = ""
            for ai_result in ai_results:
                if ai_result.get('class_name') == 'figure':
                    description = ai_result.get('description', '')
                    break
            
            formatted_images.append({
                'bbox': image.get('box', image.get('bbox', [])),
                'description': description,
                'confidence': image.get('confidence', 0)
            })
        
        return formatted_images
    
    def _format_tables(self, table_elements: List, ai_results: List) -> List[Dict]:
        """표 포맷팅"""
        formatted_tables = []
        
        for table in table_elements:
            # 해당 표에 대한 AI 설명 찾기
            description = ""
            for ai_result in ai_results:
                if ai_result.get('class_name') == 'table':
                    description = ai_result.get('description', '')
                    break
            
            formatted_tables.append({
                'bbox': table.get('box', table.get('bbox', [])),
                'description': description,
                'confidence': table.get('confidence', 0)
            })
        
        return formatted_tables
    
    def _extract_choice_number(self, text: str) -> str:
        """선택지 번호 추출"""
        patterns = [
            r'^([①②③④⑤⑥⑦⑧⑨⑩])',
            r'^[(（]\s*([1-5])\s*[)）]',
            r'^([1-5])\s*[.．]'
        ]
        
        for pattern in patterns:
            match = re.match(pattern, text)
            if match:
                return match.group(1)
        
        return ""
