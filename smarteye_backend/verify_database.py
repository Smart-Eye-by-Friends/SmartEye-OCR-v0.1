#!/usr/bin/env python3
"""
SmartEye Backend 데이터베이스 검증 스크립트

이 스크립트는 LAM→TSPM→CIM 파이프라인의 각 단계가 
데이터베이스에 올바르게 저장되었는지 상세히 확인합니다.
"""

import os
import sys
import json
import argparse
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional

# Django 설정
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')

try:
    import django
    django.setup()
except Exception as e:
    print(f"❌ Django 설정 실패: {e}")
    sys.exit(1)

# Django 모델 임포트
try:
    from django.contrib.auth import get_user_model
    from django.db.models import Count, Avg, Sum, Max, Min
    from django.utils import timezone
    
    from apps.analysis.models import AnalysisJob, ProcessedImage, AnalysisResult
    from apps.files.models import SourceFile
    from apps.users.models import User as CustomUser
    
    User = get_user_model()
except ImportError as e:
    print(f"❌ 모델 임포트 실패: {e}")
    sys.exit(1)

# 색상 코드
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'

def print_colored(text: str, color: str = Colors.ENDC) -> None:
    """색상이 있는 텍스트 출력"""
    print(f"{color}{text}{Colors.ENDC}")

def print_header(text: str) -> None:
    """헤더 출력"""
    print_colored(f"\n{'='*60}", Colors.HEADER)
    print_colored(f" {text}", Colors.HEADER + Colors.BOLD)
    print_colored(f"{'='*60}", Colors.HEADER)

def print_success(text: str) -> None:
    """성공 메시지 출력"""
    print_colored(f"✅ {text}", Colors.GREEN)

def print_warning(text: str) -> None:
    """경고 메시지 출력"""
    print_colored(f"⚠️  {text}", Colors.WARNING)

def print_error(text: str) -> None:
    """오류 메시지 출력"""
    print_colored(f"❌ {text}", Colors.FAIL)

def print_info(text: str) -> None:
    """정보 메시지 출력"""
    print_colored(f"ℹ️  {text}", Colors.BLUE)

class DatabaseVerifier:
    """데이터베이스 검증 클래스"""
    
    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.errors = []
        self.warnings = []
        
    def log_verbose(self, message: str) -> None:
        """상세 로그 출력"""
        if self.verbose:
            print_colored(f"  🔍 {message}", Colors.CYAN)
    
    def add_error(self, message: str) -> None:
        """오류 추가"""
        self.errors.append(message)
        print_error(message)
    
    def add_warning(self, message: str) -> None:
        """경고 추가"""
        self.warnings.append(message)
        print_warning(message)
    
    def verify_database_connection(self) -> bool:
        """데이터베이스 연결 확인"""
        print_header("데이터베이스 연결 확인")
        
        try:
            # 간단한 쿼리로 연결 테스트
            user_count = User.objects.count()
            print_success(f"데이터베이스 연결 성공 (사용자 수: {user_count})")
            return True
        except Exception as e:
            self.add_error(f"데이터베이스 연결 실패: {e}")
            return False
    
    def verify_table_structure(self) -> bool:
        """테이블 구조 확인"""
        print_header("테이블 구조 확인")
        
        required_models = [
            (User, "사용자"),
            (SourceFile, "소스 파일"),
            (AnalysisJob, "분석 작업"),
            (ProcessedImage, "처리된 이미지"),
            (AnalysisResult, "분석 결과")
        ]
        
        all_tables_exist = True
        
        for model, name in required_models:
            try:
                count = model.objects.count()
                print_success(f"{name} 테이블: {count}개 레코드")
                self.log_verbose(f"{model.__name__} 모델 정상")
            except Exception as e:
                self.add_error(f"{name} 테이블 접근 실패: {e}")
                all_tables_exist = False
        
        return all_tables_exist
    
    def verify_data_integrity(self) -> Dict[str, Any]:
        """데이터 무결성 확인"""
        print_header("데이터 무결성 확인")
        
        integrity_report = {
            'total_jobs': 0,
            'completed_jobs': 0,
            'failed_jobs': 0,
            'orphaned_images': 0,
            'orphaned_results': 0,
            'missing_stages': [],
            'data_consistency': True
        }
        
        try:
            # 분석 작업 통계
            total_jobs = AnalysisJob.objects.count()
            completed_jobs = AnalysisJob.objects.filter(status='completed').count()
            failed_jobs = AnalysisJob.objects.filter(status='failed').count()
            
            integrity_report['total_jobs'] = total_jobs
            integrity_report['completed_jobs'] = completed_jobs
            integrity_report['failed_jobs'] = failed_jobs
            
            print_info(f"전체 분석 작업: {total_jobs}개")
            print_info(f"완료된 작업: {completed_jobs}개")
            print_info(f"실패한 작업: {failed_jobs}개")
            
            # 고아 이미지 확인 (Job이 없는 ProcessedImage)
            orphaned_images = ProcessedImage.objects.filter(job__isnull=True).count()
            integrity_report['orphaned_images'] = orphaned_images
            
            if orphaned_images > 0:
                self.add_warning(f"고아 처리 이미지 {orphaned_images}개 발견")
            else:
                print_success("고아 처리 이미지 없음")
            
            # 고아 결과 확인 (Job이 없는 AnalysisResult)
            orphaned_results = AnalysisResult.objects.filter(job__isnull=True).count()
            integrity_report['orphaned_results'] = orphaned_results
            
            if orphaned_results > 0:
                self.add_warning(f"고아 분석 결과 {orphaned_results}개 발견")
            else:
                print_success("고아 분석 결과 없음")
            
            # 파이프라인 단계 완전성 확인
            missing_stages = []
            required_stages = ['lam', 'tspm', 'cim']
            
            for job in AnalysisJob.objects.filter(status='completed'):
                job_stages = set(
                    ProcessedImage.objects.filter(job=job)
                    .values_list('stage', flat=True)
                    .distinct()
                )
                
                missing_job_stages = set(required_stages) - job_stages
                if missing_job_stages:
                    missing_stages.append(f"Job {job.id}: {', '.join(missing_job_stages)}")
            
            integrity_report['missing_stages'] = missing_stages
            
            if missing_stages:
                for missing in missing_stages:
                    self.add_warning(f"누락된 파이프라인 단계: {missing}")
            else:
                print_success("모든 완료된 작업에 필요한 파이프라인 단계 존재")
                
        except Exception as e:
            self.add_error(f"데이터 무결성 확인 중 오류: {e}")
            integrity_report['data_consistency'] = False
        
        return integrity_report
    
    def verify_pipeline_stages(self, job_id: Optional[int] = None) -> Dict[str, Any]:
        """파이프라인 단계별 검증"""
        print_header("파이프라인 단계 검증")
        
        pipeline_report = {
            'lam_success_rate': 0,
            'tspm_success_rate': 0,
            'cim_success_rate': 0,
            'avg_processing_time': {},
            'stage_details': {}
        }
        
        try:
            # 특정 작업 또는 전체 작업 확인
            jobs_query = AnalysisJob.objects.all()
            if job_id:
                jobs_query = jobs_query.filter(id=job_id)
                print_info(f"Job ID {job_id} 파이프라인 단계 확인")
            else:
                print_info("전체 작업 파이프라인 단계 확인")
            
            stages = ['lam', 'tspm', 'cim']
            
            for stage in stages:
                # 단계별 통계
                total_images = ProcessedImage.objects.filter(stage=stage)
                if job_id:
                    total_images = total_images.filter(job_id=job_id)
                
                total_count = total_images.count()
                completed_count = total_images.filter(processing_status='completed').count()
                failed_count = total_images.filter(processing_status='failed').count()
                
                success_rate = (completed_count / total_count * 100) if total_count > 0 else 0
                pipeline_report[f'{stage}_success_rate'] = success_rate
                
                print_info(f"{stage.upper()} 단계:")
                print_info(f"  - 전체: {total_count}개")
                print_info(f"  - 완료: {completed_count}개")
                print_info(f"  - 실패: {failed_count}개")
                print_info(f"  - 성공률: {success_rate:.1f}%")
                
                # 단계별 상세 정보
                stage_details = {
                    'total': total_count,
                    'completed': completed_count,
                    'failed': failed_count,
                    'success_rate': success_rate
                }
                
                # 처리 시간 분석 (있는 경우)
                if stage == 'lam':
                    # LAM 결과가 있는 이미지들의 처리 시간
                    lam_images = total_images.filter(lam_results__isnull=False)
                    if lam_images.exists():
                        stage_details['has_results'] = True
                        self.log_verbose(f"LAM 결과가 있는 이미지: {lam_images.count()}개")
                
                elif stage == 'tspm':
                    # OCR 또는 AI 설명이 있는 이미지들
                    from django.db import models
                    tspm_images = total_images.filter(
                        models.Q(ocr_text__isnull=False) | 
                        models.Q(ai_description__isnull=False)
                    )
                    if tspm_images.exists():
                        stage_details['has_results'] = True
                        self.log_verbose(f"TSPM 결과가 있는 이미지: {tspm_images.count()}개")
                
                pipeline_report['stage_details'][stage] = stage_details
                
        except Exception as e:
            self.add_error(f"파이프라인 단계 검증 중 오류: {e}")
        
        return pipeline_report
    
    def verify_final_results(self, job_id: Optional[int] = None) -> Dict[str, Any]:
        """최종 결과 검증"""
        print_header("최종 결과 검증")
        
        results_report = {
            'total_results': 0,
            'results_with_text': 0,
            'results_with_braille': 0,
            'results_with_pdf': 0,
            'avg_confidence': 0,
            'avg_elements': 0,
            'avg_processing_time': 0
        }
        
        try:
            # 분석 결과 쿼리
            results_query = AnalysisResult.objects.all()
            if job_id:
                results_query = results_query.filter(job_id=job_id)
                print_info(f"Job ID {job_id} 최종 결과 확인")
            else:
                print_info("전체 최종 결과 확인")
            
            total_results = results_query.count()
            results_report['total_results'] = total_results
            
            if total_results == 0:
                self.add_warning("분석 결과가 없습니다")
                return results_report
            
            print_info(f"전체 분석 결과: {total_results}개")
            
            # 결과 유형별 통계
            results_with_text = results_query.exclude(text_content__isnull=True).exclude(text_content='').count()
            results_with_braille = results_query.exclude(braille_content__isnull=True).exclude(braille_content='').count()
            results_with_pdf = results_query.exclude(pdf_path__isnull=True).exclude(pdf_path='').count()
            
            results_report['results_with_text'] = results_with_text
            results_report['results_with_braille'] = results_with_braille
            results_report['results_with_pdf'] = results_with_pdf
            
            print_info(f"텍스트 결과 포함: {results_with_text}개 ({results_with_text/total_results*100:.1f}%)")
            print_info(f"점자 결과 포함: {results_with_braille}개 ({results_with_braille/total_results*100:.1f}%)")
            print_info(f"PDF 결과 포함: {results_with_pdf}개 ({results_with_pdf/total_results*100:.1f}%)")
            
            # 통계 분석
            stats = results_query.aggregate(
                avg_confidence=Avg('confidence_score'),
                avg_elements=Avg('total_detected_elements'),
                avg_processing_time=Avg('processing_time_seconds'),
                max_confidence=Max('confidence_score'),
                min_confidence=Min('confidence_score')
            )
            
            for key, value in stats.items():
                if value is not None:
                    results_report[key] = float(value)
            
            print_info(f"평균 신뢰도: {stats['avg_confidence']:.3f}")
            print_info(f"신뢰도 범위: {stats['min_confidence']:.3f} ~ {stats['max_confidence']:.3f}")
            print_info(f"평균 탐지 요소: {stats['avg_elements']:.1f}개")
            print_info(f"평균 처리 시간: {stats['avg_processing_time']:.1f}초")
            
            # 최근 결과 샘플 표시
            if self.verbose:
                print_info("\n최근 결과 샘플:")
                recent_results = results_query.order_by('-created_at')[:3]
                
                for i, result in enumerate(recent_results, 1):
                    print_info(f"  {i}. Job {result.job_id}: "
                              f"신뢰도 {result.confidence_score:.3f}, "
                              f"요소 {result.total_detected_elements}개, "
                              f"처리시간 {result.processing_time_seconds:.1f}초")
                    
                    if result.text_content:
                        preview = result.text_content[:100] + "..." if len(result.text_content) > 100 else result.text_content
                        self.log_verbose(f"     텍스트: {preview}")
                        
        except Exception as e:
            self.add_error(f"최종 결과 검증 중 오류: {e}")
        
        return results_report
    
    def verify_recent_activity(self, hours: int = 24) -> Dict[str, Any]:
        """최근 활동 확인"""
        print_header(f"최근 {hours}시간 활동 확인")
        
        activity_report = {
            'recent_jobs': 0,
            'recent_files': 0,
            'recent_results': 0,
            'active_users': 0
        }
        
        try:
            cutoff_time = timezone.now() - timedelta(hours=hours)
            
            # 최근 분석 작업
            recent_jobs = AnalysisJob.objects.filter(created_at__gte=cutoff_time).count()
            activity_report['recent_jobs'] = recent_jobs
            print_info(f"최근 {hours}시간 분석 작업: {recent_jobs}개")
            
            # 최근 파일 업로드
            recent_files = SourceFile.objects.filter(created_at__gte=cutoff_time).count()
            activity_report['recent_files'] = recent_files
            print_info(f"최근 {hours}시간 파일 업로드: {recent_files}개")
            
            # 최근 분석 결과
            recent_results = AnalysisResult.objects.filter(created_at__gte=cutoff_time).count()
            activity_report['recent_results'] = recent_results
            print_info(f"최근 {hours}시간 분석 결과: {recent_results}개")
            
            # 활성 사용자 (최근 작업을 수행한 사용자)
            active_users = User.objects.filter(
                analysisjob__created_at__gte=cutoff_time
            ).distinct().count()
            activity_report['active_users'] = active_users
            print_info(f"최근 {hours}시간 활성 사용자: {active_users}명")
            
            if recent_jobs == 0 and recent_files == 0:
                self.add_warning(f"최근 {hours}시간 동안 활동이 없습니다")
            else:
                print_success("최근 활동 정상 확인")
                
        except Exception as e:
            self.add_error(f"최근 활동 확인 중 오류: {e}")
        
        return activity_report
    
    def generate_summary_report(self) -> Dict[str, Any]:
        """종합 요약 리포트 생성"""
        print_header("종합 요약 리포트")
        
        summary = {
            'timestamp': datetime.now().isoformat(),
            'total_errors': len(self.errors),
            'total_warnings': len(self.warnings),
            'database_healthy': len(self.errors) == 0,
            'overall_status': 'healthy' if len(self.errors) == 0 else 'issues_found'
        }
        
        try:
            # 전체 통계
            total_users = User.objects.count()
            total_files = SourceFile.objects.count()
            total_jobs = AnalysisJob.objects.count()
            total_images = ProcessedImage.objects.count()
            total_results = AnalysisResult.objects.count()
            
            # 성공률 계산
            completed_jobs = AnalysisJob.objects.filter(status='completed').count()
            success_rate = (completed_jobs / total_jobs * 100) if total_jobs > 0 else 0
            
            summary.update({
                'total_users': total_users,
                'total_files': total_files,
                'total_jobs': total_jobs,
                'total_images': total_images,
                'total_results': total_results,
                'job_success_rate': success_rate
            })
            
            print_info(f"전체 사용자: {total_users}명")
            print_info(f"전체 파일: {total_files}개")
            print_info(f"전체 분석 작업: {total_jobs}개")
            print_info(f"전체 처리 이미지: {total_images}개")
            print_info(f"전체 분석 결과: {total_results}개")
            print_info(f"작업 성공률: {success_rate:.1f}%")
            
            if summary['database_healthy']:
                print_success("✅ 데이터베이스 상태 양호")
            else:
                print_error(f"❌ 데이터베이스에 {len(self.errors)}개 오류 발견")
                
            if self.warnings:
                print_warning(f"⚠️ {len(self.warnings)}개 경고사항 발견")
                
        except Exception as e:
            self.add_error(f"요약 리포트 생성 중 오류: {e}")
            summary['overall_status'] = 'error'
        
        return summary
    
    def run_full_verification(self, job_id: Optional[int] = None) -> Dict[str, Any]:
        """전체 검증 실행"""
        print_colored("🔍 SmartEye Backend 데이터베이스 검증 시작", Colors.HEADER + Colors.BOLD)
        
        # 단계별 검증 실행
        results = {}
        
        if not self.verify_database_connection():
            print_error("데이터베이스 연결 실패로 검증을 중단합니다.")
            return {'error': 'database_connection_failed'}
        
        results['table_structure'] = self.verify_table_structure()
        results['data_integrity'] = self.verify_data_integrity()
        results['pipeline_stages'] = self.verify_pipeline_stages(job_id)
        results['final_results'] = self.verify_final_results(job_id)
        results['recent_activity'] = self.verify_recent_activity()
        results['summary'] = self.generate_summary_report()
        
        return results

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="SmartEye Backend 데이터베이스 검증 스크립트",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  python verify_database.py                    # 전체 검증
  python verify_database.py --job-id 5         # 특정 작업 검증
  python verify_database.py --verbose          # 상세 출력
  python verify_database.py --export report.json  # 결과를 JSON으로 저장
        """
    )
    
    parser.add_argument(
        '--job-id', 
        type=int, 
        help='특정 분석 작업 ID로 검증 범위 제한'
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='상세한 출력 표시'
    )
    parser.add_argument(
        '--export',
        type=str,
        help='검증 결과를 JSON 파일로 저장'
    )
    parser.add_argument(
        '--recent-hours',
        type=int,
        default=24,
        help='최근 활동 확인 시간 (시간, 기본값: 24)'
    )
    
    args = parser.parse_args()
    
    # 검증 실행
    verifier = DatabaseVerifier(verbose=args.verbose)
    results = verifier.run_full_verification(job_id=args.job_id)
    
    # 결과 저장
    if args.export:
        try:
            with open(args.export, 'w', encoding='utf-8') as f:
                json.dump(results, f, indent=2, ensure_ascii=False, default=str)
            print_success(f"검증 결과가 {args.export}에 저장되었습니다.")
        except Exception as e:
            print_error(f"결과 저장 실패: {e}")
    
    # 종료 코드 설정
    if verifier.errors:
        print_error(f"\n검증 완료: {len(verifier.errors)}개 오류, {len(verifier.warnings)}개 경고")
        sys.exit(1)
    elif verifier.warnings:
        print_warning(f"\n검증 완료: {len(verifier.warnings)}개 경고")
        sys.exit(0)
    else:
        print_success("\n✅ 모든 검증 통과!")
        sys.exit(0)

if __name__ == "__main__":
    main()
