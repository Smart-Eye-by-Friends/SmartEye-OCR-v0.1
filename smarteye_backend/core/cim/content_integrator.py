"""
SmartEye CIM Content Integrator

분석 결과를 통합하고 구조화하는 클래스
"""

import json
import time
from typing import List, Dict, Any, Optional, Tuple
from collections import defaultdict, Counter
import logging
logger = logging.getLogger(__name__)

from .config import CIMConfig


class ContentIntegrator:
    """콘텐츠 통합 처리기"""
    
    def __init__(self):
        self.config = CIMConfig()
        logger.info("콘텐츠 통합기 초기화 완료")
    
    def integrate_analysis_results(self, job_id: int) -> Dict[str, Any]:
        """분석 결과 통합"""
        from apps.analysis.models import AnalysisJob, ProcessedImage
        
        try:
            # 작업 정보 가져오기
            job = AnalysisJob.objects.get(id=job_id)
            
            logger.info(f"분석 결과 통합 시작: {job.job_name} (ID: {job_id})")
            
            # 통합 결과 구조 초기화
            integrated_result = {
                'job_info': self._extract_job_info(job),
                'summary': {},
                'content_by_type': {
                    'text': [],
                    'formula': [],
                    'visual': []
                },
                'images': [],
                'statistics': {},
                'export_ready': {}
            }
            
            # 이미지별 결과 통합
            images = ProcessedImage.objects.filter(job=job).order_by('page_number')
            
            for image in images:
                image_result = self._integrate_image_results(image)
                integrated_result['images'].append(image_result)
                
                # 콘텐츠 타입별 분류
                self._classify_content_by_type(image_result, integrated_result['content_by_type'])
            
            # 요약 정보 생성
            integrated_result['summary'] = self._generate_summary(integrated_result)
            
            # 통계 정보 생성
            integrated_result['statistics'] = self._generate_statistics(integrated_result)
            
            # 내보내기 형태로 변환
            integrated_result['export_ready'] = self._prepare_export_data(integrated_result)
            
            logger.info(f"분석 결과 통합 완료: {job.job_name}")
            return integrated_result
            
        except Exception as e:
            logger.error(f"분석 결과 통합 실패: {job_id} - {e}")
            raise
    
    def _extract_job_info(self, job) -> Dict[str, Any]:
        """작업 정보 추출"""
        return {
            'job_id': job.id,
            'job_name': job.job_name,
            'model_type': job.model_type,
            'status': job.status,
            'total_images': job.total_images,
            'processed_images': job.processed_images,
            'failed_images': job.failed_images,
            'progress_percentage': job.progress_percentage,
            'created_at': job.created_at.isoformat() if job.created_at else None,
            'started_at': job.started_at,
            'completed_at': job.completed_at
        }
    
    def _integrate_image_results(self, image) -> Dict[str, Any]:
        """단일 이미지 결과 통합"""
        try:
            # 기본 이미지 정보
            image_result = {
                'image_id': image.id,
                'filename': image.processed_filename,
                'page_number': image.page_number,
                'processing_status': image.processing_status,
                'image_width': image.image_width,
                'image_height': image.image_height,
                'processing_time_ms': image.processing_time_ms,
                'memory_usage_mb': float(image.memory_usage_mb) if image.memory_usage_mb else None,
                'detections': []
            }
            
            # 감지 결과별 통합
            detections = image.layout_detections.all().order_by('detection_order')
            
            for detection in detections:
                detection_result = {
                    'detection_id': detection.id,
                    'detection_order': detection.detection_order,
                    'class_name': detection.class_name,
                    'confidence': float(detection.confidence),
                    'bbox': {
                        'x1': detection.bbox_x1,
                        'y1': detection.bbox_y1,
                        'x2': detection.bbox_x2,
                        'y2': detection.bbox_y2
                    },
                    'area_pixels': detection.area_pixels,
                    'center': {
                        'x': detection.center_x,
                        'y': detection.center_y
                    },
                    'ocr_results': [],
                    'description_results': []
                }
                
                # OCR 결과 추가
                for ocr_result in detection.ocr_results.all():
                    detection_result['ocr_results'].append({
                        'extracted_text': ocr_result.extracted_text,
                        'processed_text': ocr_result.processed_text,
                        'confidence': float(ocr_result.confidence) if ocr_result.confidence else None,
                        'language': ocr_result.language,
                        'text_length': ocr_result.text_length,
                        'processing_time_ms': ocr_result.processing_time_ms
                    })
                
                # 이미지 설명 결과 추가
                for desc_result in detection.image_descriptions.all():
                    detection_result['description_results'].append({
                        'description_text': desc_result.description_text,
                        'subject_category': desc_result.subject_category,
                        'description_type': desc_result.description_type,
                        'api_model': desc_result.api_model,
                        'api_cost': float(desc_result.api_cost),
                        'processing_time_ms': desc_result.processing_time_ms
                    })
                
                image_result['detections'].append(detection_result)
            
            return image_result
            
        except Exception as e:
            logger.error(f"이미지 결과 통합 실패: {image.processed_filename} - {e}")
            raise
    
    def _classify_content_by_type(self, image_result: Dict[str, Any], content_by_type: Dict[str, List]):
        """콘텐츠를 타입별로 분류"""
        try:
            for detection in image_result['detections']:
                class_name = detection['class_name'].lower()
                
                # 텍스트 콘텐츠
                if class_name in CIMConfig.CONTENT_TYPES['text']:
                    for ocr_result in detection['ocr_results']:
                        if ocr_result['processed_text']:
                            content_by_type['text'].append({
                                'image_filename': image_result['filename'],
                                'page_number': image_result['page_number'],
                                'class_name': detection['class_name'],
                                'content': ocr_result['processed_text'],
                                'confidence': ocr_result['confidence'],
                                'bbox': detection['bbox']
                            })
                
                # 수식 콘텐츠
                elif class_name in CIMConfig.CONTENT_TYPES['formula']:
                    for ocr_result in detection['ocr_results']:
                        if ocr_result['processed_text']:
                            content_by_type['formula'].append({
                                'image_filename': image_result['filename'],
                                'page_number': image_result['page_number'],
                                'class_name': detection['class_name'],
                                'content': ocr_result['processed_text'],
                                'confidence': ocr_result['confidence'],
                                'bbox': detection['bbox']
                            })
                
                # 시각적 콘텐츠
                elif class_name in CIMConfig.CONTENT_TYPES['visual']:
                    for desc_result in detection['description_results']:
                        if desc_result['description_text']:
                            content_by_type['visual'].append({
                                'image_filename': image_result['filename'],
                                'page_number': image_result['page_number'],
                                'class_name': detection['class_name'],
                                'content': desc_result['description_text'],
                                'subject_category': desc_result['subject_category'],
                                'bbox': detection['bbox']
                            })
        
        except Exception as e:
            logger.error(f"콘텐츠 타입별 분류 실패: {e}")
    
    def _generate_summary(self, integrated_result: Dict[str, Any]) -> Dict[str, Any]:
        """요약 정보 생성"""
        try:
            total_detections = sum(len(img['detections']) for img in integrated_result['images'])
            total_text_items = len(integrated_result['content_by_type']['text'])
            total_formula_items = len(integrated_result['content_by_type']['formula'])
            total_visual_items = len(integrated_result['content_by_type']['visual'])
            
            # 클래스별 통계
            class_counts = Counter()
            for image in integrated_result['images']:
                for detection in image['detections']:
                    class_counts[detection['class_name']] += 1
            
            # 신뢰도 통계
            confidences = []
            for image in integrated_result['images']:
                for detection in image['detections']:
                    confidences.append(detection['confidence'])
            
            avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0
            
            return {
                'total_images': len(integrated_result['images']),
                'total_detections': total_detections,
                'content_counts': {
                    'text': total_text_items,
                    'formula': total_formula_items,
                    'visual': total_visual_items
                },
                'class_distribution': dict(class_counts.most_common()),
                'average_confidence': round(avg_confidence, 3),
                'processing_info': {
                    'job_name': integrated_result['job_info']['job_name'],
                    'model_type': integrated_result['job_info']['model_type'],
                    'status': integrated_result['job_info']['status']
                }
            }
            
        except Exception as e:
            logger.error(f"요약 정보 생성 실패: {e}")
            return {}
    
    def _generate_statistics(self, integrated_result: Dict[str, Any]) -> Dict[str, Any]:
        """통계 정보 생성"""
        try:
            statistics = {
                'detection_statistics': {},
                'content_statistics': {},
                'performance_statistics': {}
            }
            
            # 감지 통계
            all_detections = []
            for image in integrated_result['images']:
                all_detections.extend(image['detections'])
            
            if all_detections:
                confidences = [d['confidence'] for d in all_detections]
                areas = [d['area_pixels'] for d in all_detections]
                
                statistics['detection_statistics'] = {
                    'total_detections': len(all_detections),
                    'confidence_stats': {
                        'min': min(confidences),
                        'max': max(confidences),
                        'mean': sum(confidences) / len(confidences),
                        'median': sorted(confidences)[len(confidences) // 2]
                    },
                    'area_stats': {
                        'min': min(areas),
                        'max': max(areas),
                        'mean': sum(areas) / len(areas)
                    }
                }
            
            # 콘텐츠 통계
            content_types = integrated_result['content_by_type']
            statistics['content_statistics'] = {
                'text_items': len(content_types['text']),
                'formula_items': len(content_types['formula']),
                'visual_items': len(content_types['visual']),
                'subject_categories': Counter([
                    item.get('subject_category', '기타') 
                    for item in content_types['visual']
                ])
            }
            
            # 성능 통계
            processing_times = []
            memory_usage = []
            
            for image in integrated_result['images']:
                if image['processing_time_ms']:
                    processing_times.append(image['processing_time_ms'])
                if image['memory_usage_mb']:
                    memory_usage.append(image['memory_usage_mb'])
            
            if processing_times:
                statistics['performance_statistics'] = {
                    'avg_processing_time_ms': sum(processing_times) / len(processing_times),
                    'total_processing_time_ms': sum(processing_times),
                    'avg_memory_usage_mb': sum(memory_usage) / len(memory_usage) if memory_usage else 0
                }
            
            return statistics
            
        except Exception as e:
            logger.error(f"통계 정보 생성 실패: {e}")
            return {}
    
    def _prepare_export_data(self, integrated_result: Dict[str, Any]) -> Dict[str, Any]:
        """내보내기용 데이터 준비"""
        try:
            export_data = {}
            
            # JSON 형태 (원본 데이터)
            export_data['json'] = integrated_result
            
            # 텍스트 형태
            export_data['text'] = self._format_as_text(integrated_result)
            
            # CSV 형태
            export_data['csv'] = self._format_as_csv(integrated_result)
            
            # HTML 형태
            export_data['html'] = self._format_as_html(integrated_result)
            
            return export_data
            
        except Exception as e:
            logger.error(f"내보내기 데이터 준비 실패: {e}")
            return {}
    
    def _format_as_text(self, integrated_result: Dict[str, Any]) -> str:
        """텍스트 형태로 포맷팅"""
        lines = []
        lines.append(f"=== {CIMConfig.DOCUMENT_TEMPLATE['title']} ===\n")
        
        # 요약
        summary = integrated_result.get('summary', {})
        lines.append(f"{CIMConfig.DOCUMENT_TEMPLATE['sections']['summary']}")
        lines.append(f"- 총 이미지 수: {summary.get('total_images', 0)}")
        lines.append(f"- 총 감지 객체 수: {summary.get('total_detections', 0)}")
        lines.append(f"- 평균 신뢰도: {summary.get('average_confidence', 0):.3f}")
        lines.append("")
        
        # 텍스트 콘텐츠
        content_by_type = integrated_result.get('content_by_type', {})
        if content_by_type.get('text'):
            lines.append(f"{CIMConfig.DOCUMENT_TEMPLATE['sections']['text_content']}")
            for item in content_by_type['text']:
                lines.append(f"[{item['class_name']}] {item['content']}")
            lines.append("")
        
        # 시각적 콘텐츠
        if content_by_type.get('visual'):
            lines.append(f"{CIMConfig.DOCUMENT_TEMPLATE['sections']['visual_content']}")
            for item in content_by_type['visual']:
                lines.append(f"[{item['class_name']}] {item['content']}")
            lines.append("")
        
        return "\n".join(lines)
    
    def _format_as_csv(self, integrated_result: Dict[str, Any]) -> str:
        """CSV 형태로 포맷팅"""
        lines = []
        lines.append("Type,Class,Content,Confidence,Page,Filename")
        
        content_by_type = integrated_result.get('content_by_type', {})
        
        for content_type, items in content_by_type.items():
            for item in items:
                confidence = item.get('confidence', '')
                content = item['content'].replace('"', '""').replace('\n', ' ')
                lines.append(f'"{content_type}","{item["class_name"]}","{content}","{confidence}","{item["page_number"]}","{item["image_filename"]}"')
        
        return "\n".join(lines)
    
    def _format_as_html(self, integrated_result: Dict[str, Any]) -> str:
        """HTML 형태로 포맷팅"""
        html = f"""
        <html>
        <head>
            <title>{CIMConfig.DOCUMENT_TEMPLATE['title']}</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; }}
                .summary {{ background-color: #f5f5f5; padding: 15px; border-radius: 5px; }}
                .content-section {{ margin: 20px 0; }}
                .content-item {{ margin: 10px 0; padding: 10px; border-left: 3px solid #007cba; }}
                .class-name {{ font-weight: bold; color: #007cba; }}
            </style>
        </head>
        <body>
            <h1>{CIMConfig.DOCUMENT_TEMPLATE['title']}</h1>
        """
        
        # 요약 섹션
        summary = integrated_result.get('summary', {})
        html += f"""
            <div class="summary">
                <h2>{CIMConfig.DOCUMENT_TEMPLATE['sections']['summary']}</h2>
                <p>총 이미지 수: {summary.get('total_images', 0)}</p>
                <p>총 감지 객체 수: {summary.get('total_detections', 0)}</p>
                <p>평균 신뢰도: {summary.get('average_confidence', 0):.3f}</p>
            </div>
        """
        
        # 콘텐츠 섹션
        content_by_type = integrated_result.get('content_by_type', {})
        
        for content_type, items in content_by_type.items():
            if items:
                section_title = CIMConfig.DOCUMENT_TEMPLATE['sections'].get(f'{content_type}_content', f'{content_type.title()} 콘텐츠')
                html += f'<div class="content-section"><h2>{section_title}</h2>'
                
                for item in items:
                    html += f'''
                    <div class="content-item">
                        <span class="class-name">[{item["class_name"]}]</span>
                        <p>{item["content"]}</p>
                    </div>
                    '''
                html += '</div>'
        
        html += """
        </body>
        </html>
        """
        
        return html
    
    def cleanup(self):
        """리소스 정리"""
        logger.debug("콘텐츠 통합기 정리 완료")
