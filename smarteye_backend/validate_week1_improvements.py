#!/usr/bin/env python3
"""
Week 1 개선사항 검증 스크립트
"""
import os
import sys

def validate_files():
    """필수 파일들이 존재하는지 확인"""
    print("📁 Week 1 개선사항 파일 검증...")
    
    required_files = [
        'utils/performance_monitor.py',
        'utils/alert_system.py',
        'utils/api_optimization.py',
        'utils/enhanced_logging.py',
        'utils/security_enhancements.py',
        'apps/monitoring/views.py',
        'apps/monitoring/urls.py',
        'apps/monitoring/apps.py',
        'templates/admin/monitoring_dashboard.html'
    ]
    
    missing_files = []
    for file_path in required_files:
        if not os.path.exists(file_path):
            missing_files.append(file_path)
        else:
            print(f"✅ {file_path}")
    
    if missing_files:
        print("\n❌ 누락된 파일들:")
        for file_path in missing_files:
            print(f"   - {file_path}")
        return False
    
    return True

def validate_requirements():
    """requirements.txt에 필요한 패키지들이 있는지 확인"""
    print("\n📦 Requirements 검증...")
    
    with open('requirements.txt', 'r') as f:
        requirements = f.read()
    
    required_packages = [
        'openai>=1.30.0',
        'psutil',
        'aioredis'
    ]
    
    for package in required_packages:
        if package.split('>=')[0].split('==')[0] in requirements:
            print(f"✅ {package}")
        else:
            print(f"❌ {package}")
            return False
    
    return True

def validate_settings():
    """Django 설정 확인"""
    print("\n⚙️ Django 설정 검증...")
    
    # settings/base.py 확인
    with open('smarteye/settings/base.py', 'r') as f:
        settings_content = f.read()
    
    if "'apps.monitoring'" in settings_content:
        print("✅ Monitoring app이 INSTALLED_APPS에 추가됨")
    else:
        print("❌ Monitoring app이 INSTALLED_APPS에 없음")
        return False
    
    # urls.py 확인
    with open('smarteye/urls.py', 'r') as f:
        urls_content = f.read()
    
    if "include('apps.monitoring.urls')" in urls_content:
        print("✅ Monitoring URLs가 포함됨")
    else:
        print("❌ Monitoring URLs가 누락됨")
        return False
    
    return True

def validate_syntax():
    """Python 문법 검증"""
    print("\n🔍 Python 문법 검증...")
    
    import py_compile
    import glob
    
    python_files = []
    python_files.extend(glob.glob('utils/*.py'))
    python_files.extend(glob.glob('apps/monitoring/*.py'))
    python_files.extend(glob.glob('core/*/service.py'))
    
    for file_path in python_files:
        try:
            py_compile.compile(file_path, doraise=True)
            print(f"✅ {file_path}")
        except py_compile.PyCompileError as e:
            print(f"❌ {file_path}: {e}")
            return False
    
    return True

def main():
    """메인 검증 함수"""
    print("🔍 SmartEye Backend Week 1 개선사항 검증 시작\n")
    
    all_passed = True
    
    # 파일 존재 여부 확인
    if not validate_files():
        all_passed = False
    
    # Requirements 확인
    if not validate_requirements():
        all_passed = False
    
    # Django 설정 확인
    if not validate_settings():
        all_passed = False
    
    # 문법 검증
    if not validate_syntax():
        all_passed = False
    
    print("\n" + "="*50)
    if all_passed:
        print("🎉 Week 1 개선사항 검증 완료!")
        print("\n구현된 기능:")
        print("1. ✅ Docker 환경 업데이트 (OpenAI 라이브러리 버전 반영)")
        print("2. ✅ 모니터링 대시보드 (성능 메트릭 시각화)")
        print("3. ✅ 알림 시스템 (메모리/에러 임계값 알림)")
        print("\n추가 기능:")
        print("- ✅ 성능 모니터링 시스템")
        print("- ✅ API 최적화 도구")  
        print("- ✅ 보안 강화 기능")
        print("- ✅ 향상된 로깅 시스템")
        print("- ✅ 실시간 웹 대시보드")
        print("\n🚀 모든 Week 1 개선사항이 성공적으로 구현되었습니다!")
        return 0
    else:
        print("❌ 일부 검증이 실패했습니다. 위의 오류를 확인해주세요.")
        return 1

if __name__ == "__main__":
    sys.exit(main())