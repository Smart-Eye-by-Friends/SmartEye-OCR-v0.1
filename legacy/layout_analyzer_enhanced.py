# -*- coding: utf-8 -*-
"""
강화된 레이아웃 분석기 - 문제별 구조화 정렬
"""

import re
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
from loguru import logger

@dataclass
class QuestionElement:
    question_number: str
    element_type: str  # 'question', 'passage', 'image', 'table', 'choices'
    content: str
    bbox: Tuple[int, int, int, int]  # x1, y1, x2, y2
    confidence: float

class EnhancedLayoutAnalyzer:
    def __init__(self):
        self.question_patterns = [
            r'(\d+)번',           # 1번, 2번 형식
            r'(\d+)\.',           # 1., 2. 형식  
            r'문제\s*(\d+)',      # 문제 1, 문제 2 형식
            r'(\d+)\s*(?:\)|）)', # 1), 2) 형식
            r'Q\s*(\d+)',         # Q1, Q2 형식
            r'(\d{2,3})',         # 593, 594 등 문제번호
        ]
        
        self.section_patterns = [
            r'([A-Z])\s*섹션',    # A섹션, B섹션
            r'([A-Z])\s*부분',    # A부분, B부분
            r'([A-Z])\s+',        # A, B (단독)
        ]
    
    def detect_question_structure(self, ocr_results: List, layout_elements: List) -> Dict:
        """문제 구조 감지 및 분석 (디버깅 강화)"""
        
        logger.info(f"🔍 [DEBUG] 문제 구조 감지 시작 - OCR: {len(ocr_results)}개, Layout: {len(layout_elements)}개")
        
        questions = {}
        current_section = None
        
        # 1. 문제 번호 감지
        question_numbers = self._extract_question_numbers(ocr_results)
        logger.info(f"🔍 [DEBUG] 감지된 문제 번호들: {question_numbers}")
        
        # 2. 섹션 구분 감지 
        sections = self._extract_sections(ocr_results)
        logger.info(f"🔍 [DEBUG] 감지된 섹션들: {sections}")
        
        # 3. 각 문제별 요소 그룹핑
        for q_num in question_numbers:
            logger.info(f"🔍 [DEBUG] 문제 {q_num} 처리 시작")
            questions[q_num] = {
                'number': q_num,
                'section': self._find_section_for_question(q_num, sections),
                'elements': self._group_elements_by_question(q_num, ocr_results, layout_elements)
            }
            logger.info(f"🔍 [DEBUG] 문제 {q_num} 처리 완료")
        
        result = {
            'total_questions': len(questions),
            'sections': sections,
            'questions': questions,
            'layout_type': self._determine_layout_type(questions)
        }
        
        logger.info(f"🔍 [DEBUG] 최종 문제 구조: 총 {len(questions)}개 문제, 타입: {result['layout_type']}")
        return result
    
    def _extract_question_numbers(self, ocr_results: List) -> List[str]:
        """문제 번호 추출"""
        question_numbers = []
        
        for result in ocr_results:
            text = result.get('text', '').strip()
            
            for pattern in self.question_patterns:
                matches = re.findall(pattern, text)
                for match in matches:
                    if match not in question_numbers:
                        question_numbers.append(match)
        
        # 숫자 순으로 정렬
        return sorted(question_numbers, key=lambda x: int(x) if x.isdigit() else 999)
    
    def _extract_sections(self, ocr_results: List) -> Dict:
        """섹션 구분 추출"""
        sections = {}
        
        for result in ocr_results:
            text = result.get('text', '').strip()
            
            for pattern in self.section_patterns:
                matches = re.findall(pattern, text)
                for match in matches:
                    sections[match] = {
                        'name': match,
                        'bbox': result.get('coordinates', result.get('bbox')),
                        'y_position': result.get('coordinates', result.get('bbox', [0, 0, 0, 0]))[1]
                    }
        
        return sections
    
    def _find_section_for_question(self, question_num: str, sections: Dict) -> Optional[str]:
        """문제가 속한 섹션 찾기"""
        # 간단한 구현: 첫 번째 섹션에 할당
        if sections:
            return list(sections.keys())[0]
        return None
    
    def _group_elements_by_question(self, question_num: str, ocr_results: List, layout_elements: List) -> Dict:
        """문제별 요소 그룹핑 (개선)"""
        
        logger.info(f"🔍 [DEBUG] 문제 {question_num} 요소 그룹핑 시작")
        logger.debug(f"OCR 결과 개수: {len(ocr_results)}")
        logger.debug(f"레이아웃 요소 개수: {len(layout_elements)}")
        
        # 문제 번호의 위치 찾기
        q_bbox = self._find_question_bbox(question_num, ocr_results)
        if not q_bbox or len(q_bbox) < 2:
            logger.warning(f"🔍 [DEBUG] 문제 {question_num}의 유효한 bbox를 찾을 수 없음")
            return {
                'question_text': [],
                'passage': [],
                'images': [],
                'tables': [],
                'choices': [],
                'explanations': []
            }
        
        q_y = q_bbox[1]  # 문제의 Y 좌표
        logger.info(f"🔍 [DEBUG] 문제 {question_num} Y 좌표: {q_y}")
        
        # 다음 문제의 Y 좌표 찾기 (경계 설정)
        next_q_y = self._find_next_question_y(question_num, ocr_results)
        logger.info(f"🔍 [DEBUG] 문제 {question_num} 다음 경계 Y: {next_q_y}")
        
        elements = {
            'question_text': [],
            'passage': [],
            'images': [],
            'tables': [],
            'choices': [],
            'explanations': []
        }
        
        # OCR 결과에서 해당 문제 범위의 텍스트 수집
        matched_count = 0
        for result in ocr_results:
            # coordinates 키 사용 (디버깅에서 확인됨)
            bbox = result.get('coordinates', [0, 0, 0, 0])
            y_pos = bbox[1] if len(bbox) > 1 else 0
            
            # 문제 범위 내의 요소만 포함
            if q_y <= y_pos < next_q_y:
                text = result.get('text', '').strip()
                
                # 텍스트 유형 분류
                element_type = self._classify_text_element(text)
                elements[element_type].append({
                    'text': text,
                    'bbox': bbox,
                    'confidence': result.get('confidence', 0)
                })
                
                matched_count += 1
                logger.debug(f"🔍 [DEBUG] 문제 {question_num}에 할당: {element_type} - '{text[:30]}...'")
        
        logger.info(f"문제 {question_num} 범위에서 {matched_count}개 OCR 요소 발견")
        
        # 레이아웃 요소에서 이미지, 표 등 수집
        layout_matched_count = 0
        for element in layout_elements:
            # box 키 사용 (디버깅에서 확인됨)
            bbox = element.get('box', [0, 0, 0, 0])
            y_pos = bbox[1] if len(bbox) > 1 else 0
            
            if q_y <= y_pos < next_q_y:
                class_name = element.get('class_name', '')
                if class_name == 'figure':
                    elements['images'].append(element)
                    layout_matched_count += 1
                    logger.debug(f"🔍 [DEBUG] 문제 {question_num}에 이미지 할당")
                elif class_name == 'table':
                    elements['tables'].append(element)
                    layout_matched_count += 1
                    logger.debug(f"🔍 [DEBUG] 문제 {question_num}에 표 할당")
        
        logger.info(f"문제 {question_num} 범위에서 {layout_matched_count}개 레이아웃 요소 발견")
        
        # 결과 요약
        for element_type, element_list in elements.items():
            if element_list:
                logger.info(f"🔍 [DEBUG] 문제 {question_num} - {element_type}: {len(element_list)}개")
        
        return elements
    
    def _classify_text_element(self, text: str) -> str:
        """텍스트 요소 분류"""
        
        # 선택지 패턴
        choice_patterns = [
            r'^[①②③④⑤⑥⑦⑧⑨⑩]',  # 원문자 선택지
            r'^[(（]\s*[1-5]\s*[)）]',   # (1), (2) 형식
            r'^[1-5]\s*[.．]',          # 1., 2. 형식
        ]
        
        for pattern in choice_patterns:
            if re.match(pattern, text):
                return 'choices'
        
        # 지문/설명 패턴
        if any(keyword in text for keyword in ['다음을', '아래의', '위의', '그림을', '표를']):
            return 'passage'
        
        # 설명/해설 패턴  
        if any(keyword in text for keyword in ['설명', '해설', '풀이', '답:']):
            return 'explanations'
        
        # 기본은 문제 텍스트
        return 'question_text'
    
    def _find_question_bbox(self, question_num: str, ocr_results: List) -> Optional[List]:
        """문제 번호의 bbox 찾기 (수정)"""
        logger.info(f"🔍 [DEBUG] 문제 번호 '{question_num}' 찾는 중...")
        
        for result in ocr_results:
            text = result.get('text', '').strip()
            class_name = result.get('class_name', '')
            
            logger.debug(f"🔍 [DEBUG] OCR 텍스트 확인: '{text}' (클래스: {class_name})")
            
            # 1차: 정확한 텍스트 매칭 + 클래스 확인
            if text == question_num and class_name == 'question_number':
                bbox = result.get('coordinates', [])
                logger.info(f"🔍 [DEBUG] ✅ 정확한 매칭: 문제 {question_num} bbox = {bbox}")
                return bbox
            
            # 2차: 유연한 매칭 (fallback)
            if class_name == 'question_number':
                if any([
                    text == f"{question_num}번",
                    text == f"{question_num}.",
                    text.startswith(question_num)
                ]):
                    bbox = result.get('coordinates', [])
                    logger.info(f"🔍 [DEBUG] ✅ 패턴 매칭: 문제 {question_num} bbox = {bbox}")
                    return bbox
        
        logger.warning(f"🔍 [DEBUG] ❌ 문제 {question_num}의 bbox를 찾을 수 없음")
        return None
    
    def _find_next_question_y(self, current_num: str, ocr_results: List) -> int:
        """다음 문제의 Y 좌표 찾기 (안전한 방식)"""
        try:
            current_int = int(current_num)
            logger.debug(f"🔍 [DEBUG] 현재 문제 번호: {current_int}")
            
            # 다음 문제들 순차적으로 확인 (최대 10개까지)
            for next_int in range(current_int + 1, current_int + 11):
                next_num = str(next_int)
                next_bbox = self._find_question_bbox(next_num, ocr_results)
                
                if next_bbox and len(next_bbox) > 1:
                    next_y = next_bbox[1]
                    logger.info(f"🔍 [DEBUG] 다음 문제 {next_num} Y좌표: {next_y}")
                    return next_y
            
            # 다음 문제를 찾을 수 없는 경우
            logger.info(f"🔍 [DEBUG] 문제 {current_num} 이후 문제가 없음 (마지막 문제)")
            return float('inf')
            
        except ValueError:
            logger.error(f"🔍 [DEBUG] 문제 번호 '{current_num}'를 정수로 변환할 수 없음")
            return float('inf')
        except Exception as e:
            logger.error(f"🔍 [DEBUG] 다음 문제 Y좌표 찾기 실패: {e}")
            return float('inf')
    
    def _determine_layout_type(self, questions: Dict) -> str:
        """레이아웃 타입 결정"""
        if len(questions) <= 2:
            return 'simple'
        elif any(q.get('section') for q in questions.values()):
            return 'sectioned'
        elif len(questions) > 5:
            return 'multiple_choice'
        else:
            return 'standard'
