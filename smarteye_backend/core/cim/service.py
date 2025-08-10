"""
SmartEye CIM (Content Integration Module) Service

CIM 모듈의 주요 서비스 로직을 담당하는 클래스
"""

import os
import tempfile
import logging
from typing import List, Dict, Any, Optional
from django.core.files.storage import default_storage

from .config import CIMConfig
from .content_integrator import ContentIntegrator
from .visualization_generator import VisualizationGenerator
from utils.file_managers import temp_file_manager, managed_file_path, FileResourceManager
from utils.base import ProcessingService

logger = logging.getLogger(__name__)


class CIMService(ProcessingService):
    """CIM 서비스 클래스"""
    
    def __init__(self):
        # ProcessingService 초기화
        super().__init__()
        
        self.content_integrator = ContentIntegrator()
        self.visualization_generator = VisualizationGenerator()
        
        # 리소스 관리에 추가
        self.add_resource(self.content_integrator)
        self.add_resource(self.visualization_generator)
        
        # 설정 유효성 검사
        if not CIMConfig.validate_config():
            self.logger.warning("CIM 설정에 문제가 있습니다. 일부 기능이 제한될 수 있습니다.")
        
        self.logger.info("CIM 서비스 초기화 완료")
    
    def process(self, job_id: int, **kwargs) -> Dict[str, Any]:
        """BaseService 추상 메서드 구현"""
        return self.process_job(job_id, **kwargs)
    
    def process_job(self, job_id: int, lam_result: Optional[Dict[str, Any]] = None,
                   tspm_result: Optional[Dict[str, Any]] = None, 
                   visualization_type: str = 'comparison') -> Dict[str, Any]:
        """LAM, TSPM 결과를 기반으로 CIM 처리 (Celery용 통합 인터페이스)"""
        try:
            logger.info(f"CIM 처리 시작: Job {job_id}")
            
            # 결과가 제공되지 않은 경우 DB에서 가져오기
            if lam_result is None or tspm_result is None:
                lam_result, tspm_result = self._get_analysis_results_from_db(job_id)
            
            # 분석 결과 통합
            integrated_results = self.content_integrator.integrate_results(
                lam_result, tspm_result
            )
            
            # 시각화 생성
            visualizations = self._generate_visualizations_with_type(
                job_id, integrated_results, visualization_type
            )
            
            # 결과 저장
            self._save_cim_results(job_id, integrated_results, visualizations)
            
            logger.info(f"CIM 처리 완료: Job {job_id}")
            
            return {
                'success': True,
                'job_id': job_id,
                'integrated_results': integrated_results,
                'visualizations': visualizations,
                'processing_options': {
                    'visualization_type': visualization_type
                },
                'processing_time': integrated_results.get('processing_time', 0),
                'confidence_score': integrated_results.get('confidence_score', 0.0)
            }
            
        except Exception as e:
            logger.error(f"CIM 작업 처리 실패: {job_id} - {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def process_job_legacy(self, job_id: int) -> Dict[str, Any]:
        """TSPM 완료된 작업에 대한 CIM 처리"""
        try:
            logger.info(f"CIM 처리 시작: Job {job_id}")
            
            # 분석 결과 통합
            integrated_results = self.content_integrator.integrate_analysis_results(job_id)
            
            # 시각화 생성
            visualizations = self._generate_visualizations(job_id, integrated_results)
            
            # 결과 저장
            self._save_cim_results(job_id, integrated_results, visualizations)
            
            logger.info(f"CIM 처리 완료: Job {job_id}")
            
            return {
                'success': True,
                'job_id': job_id,
                'integrated_results': integrated_results,
                'visualizations': visualizations
            }
            
        except Exception as e:
            logger.error(f"CIM 처리 실패: {job_id} - {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def _generate_visualizations(self, job_id: int, integrated_results: Dict[str, Any]) -> Dict[str, str]:
        """시각화 파일 생성"""
        try:
            visualizations = {}
            
            # 이미지별 시각화 생성
            for image_result in integrated_results['images']:
                image_id = image_result['image_id']
                
                # 원본 이미지 경로 구성
                image_path = self._get_image_path(image_result)
                if not image_path:
                    logger.warning(f"이미지 경로를 찾을 수 없음: {image_result['filename']}")
                    continue
                
                try:
                    # 비교 시각화 생성
                    comparison_path = self.visualization_generator.generate_comparison_visualization(
                        image_path, image_result
                    )
                    visualizations[f'comparison_{image_id}'] = comparison_path
                    
                    # 상세 시각화 생성
                    detailed_path = self.visualization_generator.generate_detailed_visualization(
                        image_path, image_result
                    )
                    visualizations[f'detailed_{image_id}'] = detailed_path
                    
                except Exception as e:
                    logger.error(f"이미지 {image_id} 시각화 생성 실패: {e}")
            
            # 전체 콘텐츠 시각화 생성
            try:
                content_viz_path = self.visualization_generator.generate_content_visualization(
                    integrated_results
                )
                visualizations['content_analysis'] = content_viz_path
            except Exception as e:
                logger.error(f"콘텐츠 시각화 생성 실패: {e}")
            
            return visualizations
            
        except Exception as e:
            logger.error(f"시각화 생성 실패: {e}")
            return {}
    
    def _get_image_path(self, image_result: Dict[str, Any]) -> Optional[str]:
        """이미지 파일 경로 반환"""
        try:
            # 데이터베이스에서 이미지 정보 가져오기
            from apps.analysis.models import ProcessedImage
            
            processed_image = ProcessedImage.objects.get(id=image_result['image_id'])
            source_file = processed_image.source_file
            
            if source_file.file_type == 'pdf':
                # PDF의 경우 LAM에서 추출한 이미지 사용 
                from core.lam.service import LAMService
                lam_service = LAMService()
                return lam_service._extract_pdf_page(
                    source_file.storage_path, 
                    processed_image.page_number
                )
            else:
                # 이미지 파일의 경우 직접 경로 사용
                file_path = source_file.storage_path
                if default_storage.exists(file_path):
                    return default_storage.path(file_path)
                else:
                    logger.error(f"이미지 파일이 존재하지 않음: {file_path}")
                    return None
            
        except Exception as e:
            logger.error(f"이미지 경로 구성 실패: {e}")
            return None
    
    def _save_cim_results(self, job_id: int, integrated_results: Dict[str, Any], 
                         visualizations: Dict[str, str]):
        """CIM 결과를 데이터베이스에 저장"""
        from apps.analysis.models import CIMIntegratedResult
        
        try:
            # 기존 결과 삭제 (재처리인 경우)
            CIMIntegratedResult.objects.filter(job_id=job_id).delete()
            
            # 통합 결과 저장
            cim_result = CIMIntegratedResult.objects.create(
                job_id=job_id,
                integrated_data=integrated_results,
                visualization_paths=visualizations,
                summary_stats=integrated_results.get('summary', {}),
                export_formats=integrated_results.get('export_ready', {})
            )
            
            logger.info(f"CIM 결과 저장 완료: Job {job_id}")
            return cim_result
            
        except Exception as e:
            logger.error(f"CIM 결과 저장 실패: {job_id} - {e}")
            raise
    
    def get_integrated_results(self, job_id: int) -> Dict[str, Any]:
        """통합 결과 조회"""
        from apps.analysis.models import CIMIntegratedResult
        
        try:
            cim_result = CIMIntegratedResult.objects.get(job_id=job_id)
            
            return {
                'job_id': job_id,
                'integrated_data': cim_result.integrated_data,
                'visualization_paths': cim_result.visualization_paths,
                'summary_stats': cim_result.summary_stats,
                'export_formats': cim_result.export_formats,
                'created_at': cim_result.created_at.isoformat()
            }
            
        except CIMIntegratedResult.DoesNotExist:
            return {
                'error': 'CIM 결과를 찾을 수 없습니다.'
            }
        except Exception as e:
            logger.error(f"CIM 결과 조회 실패: {job_id} - {e}")
            raise
    
    def export_results(self, job_id: int, format_type: str = 'json') -> Dict[str, Any]:
        """결과를 지정된 형식으로 내보내기"""
        try:
            if format_type not in CIMConfig.EXPORT_FORMATS:
                return {
                    'success': False,
                    'error': f'지원하지 않는 형식: {format_type}'
                }
            
            # 통합 결과 가져오기
            integrated_results = self.get_integrated_results(job_id)
            if 'error' in integrated_results:
                return {
                    'success': False,
                    'error': integrated_results['error']
                }
            
            # 형식별 데이터 추출
            export_data = integrated_results['export_formats'].get(format_type)
            if not export_data:
                return {
                    'success': False,
                    'error': f'{format_type} 형식 데이터가 없습니다.'
                }
            
            # Context Manager를 사용한 안전한 임시 파일 처리
            with temp_file_manager(
                suffix=f'.{format_type}',
                prefix=f'smarteye_export_{job_id}_'
            ) as temp_file:
                
                # 형식별 저장
                if format_type == 'json':
                    import json
                    temp_file.write(json.dumps(export_data, ensure_ascii=False, indent=2).encode())
                elif format_type in ['txt', 'html', 'csv']:
                    temp_file.write(export_data.encode())
                elif format_type == 'pdf':
                    # PDF 생성은 별도 처리 필요
                    return self._export_as_pdf(job_id, integrated_results)
                
                temp_file.flush()  # 버퍼 플러시
                
                # 파일 크기 계산
                file_size = os.path.getsize(temp_file.name)
                
                # 파일을 영구 저장소로 복사 (필요한 경우)
                # 여기서는 임시 경로를 반환하지만, 실제로는 영구 저장소로 이동해야 함
                permanent_path = self._save_to_permanent_storage(temp_file.name, format_type, job_id)
                
                return {
                    'success': True,
                    'file_path': permanent_path,
                    'format': format_type,
                    'size': file_size
                }
            
        except Exception as e:
            logger.error(f"결과 내보내기 실패: {job_id} - {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def _save_to_permanent_storage(self, temp_path: str, format_type: str, job_id: int) -> str:
        """임시 파일을 영구 저장소로 이동"""
        try:
            from django.core.files.base import ContentFile
            import shutil
            
            # 영구 파일 경로 생성
            filename = f"export_{job_id}_{format_type}.{format_type}"
            media_path = os.path.join('exports', filename)
            
            # 임시 파일을 media 디렉토리로 복사
            permanent_path = os.path.join(settings.MEDIA_ROOT, media_path)
            os.makedirs(os.path.dirname(permanent_path), exist_ok=True)
            shutil.copy2(temp_path, permanent_path)
            
            logger.info(f"Exported file saved to: {permanent_path}")
            return permanent_path
            
        except Exception as e:
            logger.error(f"Failed to save file to permanent storage: {e}")
            return temp_path  # 실패 시 임시 경로 반환
    
    def _export_as_pdf(self, job_id: int, integrated_results: Dict[str, Any]) -> Dict[str, Any]:
        """PDF 형식으로 내보내기"""
        try:
            from reportlab.lib.pagesizes import letter, A4
            from reportlab.lib.styles import getSampleStyleSheet
            from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image as RLImage
            from reportlab.lib.units import inch
            
            # Context Manager를 사용한 안전한 PDF 파일 생성
            with managed_file_path(
                suffix='.pdf',
                prefix=f'smarteye_report_{job_id}_'
            ) as temp_pdf_path:
                
                # PDF 문서 생성
                doc = SimpleDocTemplate(temp_pdf_path, pagesize=A4)
                styles = getSampleStyleSheet()
                story = []
                
                # 제목
                title = Paragraph(CIMConfig.DOCUMENT_TEMPLATE['title'], styles['Title'])
                story.append(title)
                story.append(Spacer(1, 12))
                
                # 요약 정보
                summary = integrated_results['integrated_data'].get('summary', {})
                summary_text = f"""
                작업명: {integrated_results['integrated_data']['job_info']['job_name']}<br/>
                총 이미지 수: {summary.get('total_images', 0)}<br/>
                총 감지 객체 수: {summary.get('total_detections', 0)}<br/>
                평균 신뢰도: {summary.get('average_confidence', 0):.3f}
                """
                story.append(Paragraph(summary_text, styles['Normal']))
                story.append(Spacer(1, 12))
                
                # 시각화 이미지 추가
                visualization_paths = integrated_results.get('visualization_paths', {})
                if 'content_analysis' in visualization_paths:
                    try:
                        img_path = visualization_paths['content_analysis']
                        if os.path.exists(img_path):
                            img = RLImage(img_path, width=6*inch, height=4*inch)
                            story.append(img)
                            story.append(Spacer(1, 12))
                    except Exception as e:
                        logger.warning(f"PDF에 이미지 추가 실패: {e}")
                
                # 텍스트 콘텐츠
                content_by_type = integrated_results['integrated_data'].get('content_by_type', {})
                if content_by_type.get('text'):
                    story.append(Paragraph('📝 추출된 텍스트', styles['Heading2']))
                    for item in content_by_type['text'][:10]:  # 최대 10개만
                        text = f"[{item['class_name']}] {item['content']}"
                        story.append(Paragraph(text, styles['Normal']))
                        story.append(Spacer(1, 6))
                
                # PDF 빌드
                doc.build(story)
                
                # 파일 크기 계산
                file_size = os.path.getsize(temp_pdf_path)
                
                # 영구 저장소로 저장
                permanent_path = self._save_to_permanent_storage(temp_pdf_path, 'pdf', job_id)
                
                return {
                    'success': True,
                    'file_path': permanent_path,
                    'format': 'pdf',
                    'size': file_size
                }
            
        except ImportError:
            return {
                'success': False,
                'error': 'reportlab 라이브러리가 설치되지 않았습니다.'
            }
        except Exception as e:
            logger.error(f"PDF 내보내기 실패: {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def cleanup_temp_files(self, older_than_hours: int = None):
        """임시 파일 정리"""
        try:
            import time
            import glob
            
            older_than_hours = older_than_hours or CIMConfig.TEMP_FILE_CLEANUP_HOURS
            cutoff_time = time.time() - (older_than_hours * 3600)
            
            # 임시 디렉토리에서 CIM 관련 파일 찾기
            temp_dir = tempfile.gettempdir()
            patterns = [
                os.path.join(temp_dir, 'smarteye_*'),
                os.path.join(temp_dir, 'cim_*'),
                os.path.join(temp_dir, 'tsmp_*')
            ]
            
            deleted_count = 0
            for pattern in patterns:
                for file_path in glob.glob(pattern):
                    try:
                        if os.path.getmtime(file_path) < cutoff_time:
                            os.remove(file_path)
                            deleted_count += 1
                    except Exception as e:
                        logger.warning(f"임시 파일 삭제 실패: {file_path} - {e}")
            
            logger.info(f"임시 파일 정리 완료: {deleted_count}개 파일 삭제")
            
        except Exception as e:
            logger.error(f"임시 파일 정리 실패: {e}")
    
    def cleanup(self):
        """서비스 리소스 정리"""
        try:
            if hasattr(self, 'content_integrator'):
                self.content_integrator.cleanup()
            
            if hasattr(self, 'visualization_generator'):
                self.visualization_generator.cleanup()
            
            logger.info("CIM 서비스 리소스 정리 완료")
            
        except Exception as e:
            logger.warning(f"CIM 리소스 정리 중 오류: {e}")
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        self.cleanup()
