"""
SmartEye CIM Visualization Generator

분석 결과를 시각화하는 클래스
"""

import os
import tempfile
import numpy as np
from typing import List, Dict, Any, Optional, Tuple
from PIL import Image, ImageDraw, ImageFont
import cv2
import matplotlib.pyplot as plt
import matplotlib.patches as patches
import logging
logger = logging.getLogger(__name__)

from .config import CIMConfig


class VisualizationGenerator:
    """시각화 생성기"""
    
    def __init__(self):
        self.config = CIMConfig()
        self.font_path = CIMConfig.get_font_path()
        logger.info("시각화 생성기 초기화 완료")
    
    def generate_comparison_visualization(self, image_path: str, analysis_results: Dict[str, Any], 
                                       output_path: str = None) -> str:
        """원본과 분석 결과 비교 시각화 생성"""
        try:
            # 원본 이미지 로드
            original_image = cv2.imread(image_path)
            if original_image is None:
                raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
            
            # RGB 변환
            original_rgb = cv2.cvtColor(original_image, cv2.COLOR_BGR2RGB)
            
            # 분석 결과가 적용된 이미지 생성
            processed_image = self._create_processed_image(original_rgb, analysis_results)
            
            # 비교 이미지 생성
            comparison_image = self._create_comparison_image(original_rgb, processed_image)
            
            # 출력 경로 설정
            if output_path is None:
                output_path = tempfile.mktemp(suffix=f'.{CIMConfig.DEFAULT_FORMAT}')
            
            # 이미지 저장
            self._save_image(comparison_image, output_path)
            
            logger.info(f"비교 시각화 생성 완료: {output_path}")
            return output_path
            
        except Exception as e:
            logger.error(f"비교 시각화 생성 실패: {e}")
            raise
    
    def generate_detailed_visualization(self, image_path: str, analysis_results: Dict[str, Any],
                                      output_path: str = None) -> str:
        """상세 분석 결과 시각화 생성"""
        try:
            # 원본 이미지 로드
            original_image = cv2.imread(image_path)
            if original_image is None:
                raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
            
            # matplotlib 그래프 생성
            fig, axes = plt.subplots(2, 2, figsize=(16, 12))
            fig.suptitle('SmartEye 상세 분석 결과', fontsize=16, fontweight='bold')
            
            # 1. 원본 이미지
            axes[0, 0].imshow(cv2.cvtColor(original_image, cv2.COLOR_BGR2RGB))
            axes[0, 0].set_title('원본 이미지')
            axes[0, 0].axis('off')
            
            # 2. 레이아웃 감지 결과
            layout_image = self._draw_layout_detection(original_image, analysis_results)
            axes[0, 1].imshow(layout_image)
            axes[0, 1].set_title('레이아웃 감지 결과')
            axes[0, 1].axis('off')
            
            # 3. 클래스별 분포
            self._plot_class_distribution(axes[1, 0], analysis_results)
            
            # 4. 신뢰도 분포
            self._plot_confidence_distribution(axes[1, 1], analysis_results)
            
            plt.tight_layout()
            
            # 출력 경로 설정
            if output_path is None:
                output_path = tempfile.mktemp(suffix=f'.{CIMConfig.DEFAULT_FORMAT}')
            
            # 그래프 저장
            plt.savefig(output_path, dpi=CIMConfig.OUTPUT_DPI, bbox_inches='tight')
            plt.close()
            
            logger.info(f"상세 시각화 생성 완료: {output_path}")
            return output_path
            
        except Exception as e:
            logger.error(f"상세 시각화 생성 실패: {e}")
            raise
    
    def generate_content_visualization(self, integrated_results: Dict[str, Any],
                                     output_path: str = None) -> str:
        """콘텐츠 통합 결과 시각화"""
        try:
            # matplotlib 그래프 생성
            fig, axes = plt.subplots(2, 3, figsize=(18, 12))
            fig.suptitle('SmartEye 콘텐츠 분석 결과', fontsize=16, fontweight='bold')
            
            # 1. 콘텐츠 타입별 분포
            self._plot_content_type_distribution(axes[0, 0], integrated_results)
            
            # 2. 페이지별 객체 수
            self._plot_objects_per_page(axes[0, 1], integrated_results)
            
            # 3. 클래스별 신뢰도
            self._plot_confidence_by_class(axes[0, 2], integrated_results)
            
            # 4. 주제 카테고리 분포
            self._plot_subject_categories(axes[1, 0], integrated_results)
            
            # 5. 처리 시간 분석
            self._plot_processing_time(axes[1, 1], integrated_results)
            
            # 6. 텍스트 길이 분포
            self._plot_text_length_distribution(axes[1, 2], integrated_results)
            
            plt.tight_layout()
            
            # 출력 경로 설정
            if output_path is None:
                output_path = tempfile.mktemp(suffix=f'.{CIMConfig.DEFAULT_FORMAT}')
            
            # 그래프 저장
            plt.savefig(output_path, dpi=CIMConfig.OUTPUT_DPI, bbox_inches='tight')
            plt.close()
            
            logger.info(f"콘텐츠 시각화 생성 완료: {output_path}")
            return output_path
            
        except Exception as e:
            logger.error(f"콘텐츠 시각화 생성 실패: {e}")
            raise
    
    def _create_processed_image(self, image: np.ndarray, analysis_results: Dict[str, Any]) -> np.ndarray:
        """분석 결과가 적용된 이미지 생성"""
        try:
            # 캔버스 생성 (흰 배경)
            canvas = np.ones_like(image) * 255
            
            # PIL 변환
            canvas_pil = Image.fromarray(canvas)
            draw = ImageDraw.Draw(canvas_pil)
            
            # 폰트 설정
            font = self._get_font(CIMConfig.DEFAULT_FONT_SIZE)
            
            # 감지 결과별 처리
            detections = analysis_results.get('detections', [])
            
            for detection in detections:
                bbox = detection['bbox']
                x1, y1, x2, y2 = bbox['x1'], bbox['y1'], bbox['x2'], bbox['y2']
                class_name = detection['class_name']
                
                # 바운딩 박스 그리기
                if class_name.lower() in CIMConfig.CONTENT_TYPES['text'] + CIMConfig.CONTENT_TYPES['formula']:
                    color = CIMConfig.COLORS['ocr_bbox']
                else:
                    color = CIMConfig.COLORS['api_bbox']
                
                draw.rectangle([x1, y1, x2, y2], outline=color, width=CIMConfig.BBOX_THICKNESS)
                
                # 텍스트 내용 추가
                content = self._get_detection_content(detection)
                if content:
                    # 텍스트를 박스 크기에 맞게 조정
                    wrapped_text = self._wrap_text_to_box(content, font, x2 - x1, y2 - y1)
                    
                    # 텍스트 그리기
                    text_y = y1 + CIMConfig.TEXT_PADDING
                    for line in wrapped_text:
                        draw.text((x1 + CIMConfig.TEXT_PADDING, text_y), line, 
                                font=font, fill=CIMConfig.COLORS['text_color'])
                        text_y += font.size + 2
            
            return np.array(canvas_pil)
            
        except Exception as e:
            logger.error(f"처리된 이미지 생성 실패: {e}")
            return image
    
    def _create_comparison_image(self, original: np.ndarray, processed: np.ndarray) -> np.ndarray:
        """원본과 처리된 이미지를 나란히 배치"""
        try:
            height, width = original.shape[:2]
            gap_width = CIMConfig.COMPARISON_LAYOUT['gap_width']
            
            # 검정색 구분선 생성
            black_line = np.zeros((height, gap_width, 3), dtype=np.uint8)
            
            # 이미지 연결: [원본 | 구분선 | 처리본]
            comparison = np.hstack((original, black_line, processed))
            
            # 라벨 추가 (옵션)
            if CIMConfig.COMPARISON_LAYOUT['show_labels']:
                comparison = self._add_comparison_labels(comparison, width, gap_width)
            
            return comparison
            
        except Exception as e:
            logger.error(f"비교 이미지 생성 실패: {e}")
            return original
    
    def _add_comparison_labels(self, image: np.ndarray, original_width: int, gap_width: int) -> np.ndarray:
        """비교 이미지에 라벨 추가"""
        try:
            label_height = CIMConfig.COMPARISON_LAYOUT['label_height']
            height, width = image.shape[:2]
            
            # 라벨 영역 추가
            labeled_image = np.ones((height + label_height, width, 3), dtype=np.uint8) * 255
            labeled_image[label_height:, :] = image
            
            # PIL 변환하여 텍스트 추가
            pil_image = Image.fromarray(labeled_image)
            draw = ImageDraw.Draw(pil_image)
            font = self._get_font(14)
            
            # 원본 라벨
            draw.text((original_width // 2 - 50, 5), '원본 문서', 
                     font=font, fill=(0, 0, 0), anchor='mm')
            
            # 처리본 라벨
            processed_center = original_width + gap_width + original_width // 2
            draw.text((processed_center - 50, 5), '분석 결과', 
                     font=font, fill=(0, 0, 0), anchor='mm')
            
            return np.array(pil_image)
            
        except Exception as e:
            logger.error(f"라벨 추가 실패: {e}")
            return image
    
    def _draw_layout_detection(self, image: np.ndarray, analysis_results: Dict[str, Any]) -> np.ndarray:
        """레이아웃 감지 결과 시각화"""
        try:
            # 이미지 복사
            result_image = image.copy()
            
            # 감지 결과별 박스 그리기
            detections = analysis_results.get('detections', [])
            
            for detection in detections:
                bbox = detection['bbox']
                x1, y1, x2, y2 = bbox['x1'], bbox['y1'], bbox['x2'], bbox['y2']
                class_name = detection['class_name']
                confidence = detection['confidence']
                
                # 클래스별 색상 선택
                if class_name.lower() in CIMConfig.CONTENT_TYPES['text']:
                    color = (0, 255, 0)  # 녹색
                elif class_name.lower() in CIMConfig.CONTENT_TYPES['formula']:
                    color = (0, 0, 255)  # 파란색
                else:
                    color = (255, 0, 0)  # 빨간색
                
                # 바운딩 박스 그리기
                cv2.rectangle(result_image, (x1, y1), (x2, y2), color, CIMConfig.BBOX_THICKNESS)
                
                # 클래스명과 신뢰도 표시
                label = f"{class_name}: {confidence:.2f}"
                cv2.putText(result_image, label, (x1, y1 - 10), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
            
            return cv2.cvtColor(result_image, cv2.COLOR_BGR2RGB)
            
        except Exception as e:
            logger.error(f"레이아웃 감지 시각화 실패: {e}")
            return cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    
    def _plot_class_distribution(self, ax, analysis_results: Dict[str, Any]):
        """클래스별 분포 차트"""
        try:
            detections = analysis_results.get('detections', [])
            classes = [d['class_name'] for d in detections]
            
            from collections import Counter
            class_counts = Counter(classes)
            
            if class_counts:
                labels, counts = zip(*class_counts.most_common())
                ax.bar(labels, counts)
                ax.set_title('클래스별 객체 수')
                ax.set_xlabel('클래스')
                ax.set_ylabel('개수')
                plt.setp(ax.get_xticklabels(), rotation=45, ha='right')
            else:
                ax.text(0.5, 0.5, '감지된 객체 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('클래스별 객체 수')
            
        except Exception as e:
            logger.error(f"클래스 분포 차트 생성 실패: {e}")
            ax.text(0.5, 0.5, '차트 생성 실패', ha='center', va='center', transform=ax.transAxes)
    
    def _plot_confidence_distribution(self, ax, analysis_results: Dict[str, Any]):
        """신뢰도 분포 히스토그램"""
        try:
            detections = analysis_results.get('detections', [])
            confidences = [d['confidence'] for d in detections]
            
            if confidences:
                ax.hist(confidences, bins=20, alpha=0.7, color='skyblue', edgecolor='black')
                ax.set_title('신뢰도 분포')
                ax.set_xlabel('신뢰도')
                ax.set_ylabel('빈도')
                ax.axvline(np.mean(confidences), color='red', linestyle='--', 
                          label=f'평균: {np.mean(confidences):.3f}')
                ax.legend()
            else:
                ax.text(0.5, 0.5, '데이터 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('신뢰도 분포')
            
        except Exception as e:
            logger.error(f"신뢰도 분포 차트 생성 실패: {e}")
            ax.text(0.5, 0.5, '차트 생성 실패', ha='center', va='center', transform=ax.transAxes)
    
    def _plot_content_type_distribution(self, ax, integrated_results: Dict[str, Any]):
        """콘텐츠 타입별 분포"""
        try:
            content_by_type = integrated_results.get('content_by_type', {})
            types = []
            counts = []
            
            for content_type, items in content_by_type.items():
                if items:  # 빈 리스트가 아닌 경우만
                    types.append(content_type.title())
                    counts.append(len(items))
            
            if types:
                ax.pie(counts, labels=types, autopct='%1.1f%%', startangle=90)
                ax.set_title('콘텐츠 타입별 분포')
            else:
                ax.text(0.5, 0.5, '콘텐츠 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('콘텐츠 타입별 분포')
            
        except Exception as e:
            logger.error(f"콘텐츠 타입 분포 차트 생성 실패: {e}")
    
    def _plot_objects_per_page(self, ax, integrated_results: Dict[str, Any]):
        """페이지별 객체 수"""
        try:
            images = integrated_results.get('images', [])
            pages = []
            object_counts = []
            
            for image in images:
                pages.append(f"Page {image['page_number']}")
                object_counts.append(len(image['detections']))
            
            if pages:
                ax.bar(pages, object_counts)
                ax.set_title('페이지별 감지 객체 수')
                ax.set_xlabel('페이지')
                ax.set_ylabel('객체 수')
                plt.setp(ax.get_xticklabels(), rotation=45)
            else:
                ax.text(0.5, 0.5, '데이터 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('페이지별 감지 객체 수')
            
        except Exception as e:
            logger.error(f"페이지별 객체 수 차트 생성 실패: {e}")
    
    def _plot_confidence_by_class(self, ax, integrated_results: Dict[str, Any]):
        """클래스별 평균 신뢰도"""
        try:
            from collections import defaultdict
            
            class_confidences = defaultdict(list)
            
            for image in integrated_results.get('images', []):
                for detection in image['detections']:
                    class_confidences[detection['class_name']].append(detection['confidence'])
            
            if class_confidences:
                classes = list(class_confidences.keys())
                avg_confidences = [np.mean(confidences) for confidences in class_confidences.values()]
                
                ax.bar(classes, avg_confidences)
                ax.set_title('클래스별 평균 신뢰도')
                ax.set_xlabel('클래스')
                ax.set_ylabel('평균 신뢰도')
                plt.setp(ax.get_xticklabels(), rotation=45, ha='right')
            else:
                ax.text(0.5, 0.5, '데이터 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('클래스별 평균 신뢰도')
            
        except Exception as e:
            logger.error(f"클래스별 신뢰도 차트 생성 실패: {e}")
    
    def _plot_subject_categories(self, ax, integrated_results: Dict[str, Any]):
        """주제 카테고리 분포"""
        try:
            visual_content = integrated_results.get('content_by_type', {}).get('visual', [])
            categories = [item.get('subject_category', '기타') for item in visual_content]
            
            if categories:
                from collections import Counter
                category_counts = Counter(categories)
                labels, counts = zip(*category_counts.most_common())
                
                ax.pie(counts, labels=labels, autopct='%1.1f%%', startangle=90)
                ax.set_title('시각적 콘텐츠 주제 분포')
            else:
                ax.text(0.5, 0.5, '시각적 콘텐츠 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('시각적 콘텐츠 주제 분포')
            
        except Exception as e:
            logger.error(f"주제 카테고리 차트 생성 실패: {e}")
    
    def _plot_processing_time(self, ax, integrated_results: Dict[str, Any]):
        """처리 시간 분석"""
        try:
            images = integrated_results.get('images', [])
            processing_times = [img['processing_time_ms'] for img in images if img.get('processing_time_ms')]
            
            if processing_times:
                ax.bar(range(len(processing_times)), processing_times)
                ax.set_title('이미지별 처리 시간')
                ax.set_xlabel('이미지 순서')
                ax.set_ylabel('처리 시간 (ms)')
                
                # 평균선 추가
                avg_time = np.mean(processing_times)
                ax.axhline(avg_time, color='red', linestyle='--', 
                          label=f'평균: {avg_time:.1f}ms')
                ax.legend()
            else:
                ax.text(0.5, 0.5, '처리 시간 데이터 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('이미지별 처리 시간')
            
        except Exception as e:
            logger.error(f"처리 시간 차트 생성 실패: {e}")
    
    def _plot_text_length_distribution(self, ax, integrated_results: Dict[str, Any]):
        """텍스트 길이 분포"""
        try:
            text_content = integrated_results.get('content_by_type', {}).get('text', [])
            text_lengths = [len(item['content']) for item in text_content]
            
            if text_lengths:
                ax.hist(text_lengths, bins=20, alpha=0.7, color='lightgreen', edgecolor='black')
                ax.set_title('텍스트 길이 분포')
                ax.set_xlabel('텍스트 길이 (문자 수)')
                ax.set_ylabel('빈도')
                
                # 평균선 추가
                avg_length = np.mean(text_lengths)
                ax.axvline(avg_length, color='red', linestyle='--', 
                          label=f'평균: {avg_length:.1f}자')
                ax.legend()
            else:
                ax.text(0.5, 0.5, '텍스트 데이터 없음', ha='center', va='center', transform=ax.transAxes)
                ax.set_title('텍스트 길이 분포')
            
        except Exception as e:
            logger.error(f"텍스트 길이 분포 차트 생성 실패: {e}")
    
    def _get_detection_content(self, detection: Dict[str, Any]) -> str:
        """감지 결과에서 콘텐츠 추출"""
        try:
            # OCR 결과 확인
            ocr_results = detection.get('ocr_results', [])
            if ocr_results:
                return ocr_results[0].get('processed_text', '')
            
            # 이미지 설명 결과 확인
            desc_results = detection.get('description_results', [])
            if desc_results:
                return desc_results[0].get('description_text', '')
            
            return ''
            
        except Exception:
            return ''
    
    def _wrap_text_to_box(self, text: str, font: ImageFont.ImageFont, 
                         box_width: int, box_height: int) -> List[str]:
        """텍스트를 박스 크기에 맞게 줄바꿈"""
        try:
            words = text.split()
            lines = []
            current_line = ''
            
            for word in words:
                test_line = f"{current_line} {word}".strip()
                bbox = font.getbbox(test_line)
                text_width = bbox[2] - bbox[0]
                
                if text_width <= box_width - 2 * CIMConfig.TEXT_PADDING:
                    current_line = test_line
                else:
                    if current_line:
                        lines.append(current_line)
                        current_line = word
                    else:
                        # 단어가 너무 길면 강제로 자르기
                        lines.append(word[:20] + '...')
                        current_line = ''
            
            if current_line:
                lines.append(current_line)
            
            # 박스 높이에 맞게 라인 수 제한
            max_lines = (box_height - 2 * CIMConfig.TEXT_PADDING) // font.size
            return lines[:max_lines]
            
        except Exception:
            return [text[:50] + '...' if len(text) > 50 else text]
    
    def _get_font(self, size: int = None) -> ImageFont.ImageFont:
        """폰트 객체 반환"""
        try:
            size = size or CIMConfig.DEFAULT_FONT_SIZE
            size = max(CIMConfig.MIN_FONT_SIZE, min(size, CIMConfig.MAX_FONT_SIZE))
            
            if self.font_path:
                return ImageFont.truetype(self.font_path, size)
            else:
                return ImageFont.load_default()
                
        except Exception:
            return ImageFont.load_default()
    
    def _save_image(self, image: np.ndarray, output_path: str):
        """이미지 저장"""
        try:
            # PIL 이미지로 변환
            pil_image = Image.fromarray(image.astype(np.uint8))
            
            # 형태에 따라 저장
            ext = os.path.splitext(output_path)[1].lower()
            
            if ext == '.pdf':
                pil_image.save(output_path, 'PDF', resolution=CIMConfig.OUTPUT_DPI)
            elif ext in ['.jpg', '.jpeg']:
                pil_image.save(output_path, 'JPEG', quality=CIMConfig.OUTPUT_QUALITY)
            else:  # png 기본
                pil_image.save(output_path, 'PNG')
            
        except Exception as e:
            logger.error(f"이미지 저장 실패: {output_path} - {e}")
            raise
    
    def cleanup(self):
        """리소스 정리"""
        logger.debug("시각화 생성기 정리 완료")
