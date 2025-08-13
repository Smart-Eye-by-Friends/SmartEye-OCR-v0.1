"""
SmartEye Backend API 테스트

실제 테스트에서 검증된 API 엔드포인트들을 테스트합니다.
JWT 인증, 파일 업로드, 분석 작업 API 등을 포함합니다.
"""

import requests
import json
import os
from pathlib import Path
import logging

logger = logging.getLogger(__name__)


class SmartEyeAPITester:
    """SmartEye API 테스트 클래스"""
    
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
        self.access_token = None
        self.refresh_token = None
        
    def authenticate(self, username="admin", password="smarteye123"):
        """JWT 인증 테스트"""
        url = f"{self.base_url}/api/v1/auth/token/"
        data = {
            "username": username,
            "password": password
        }
        
        try:
            response = requests.post(url, json=data)
            if response.status_code == 200:
                tokens = response.json()
                self.access_token = tokens.get('access')
                self.refresh_token = tokens.get('refresh')
                logger.info("JWT 인증 성공")
                return True
            else:
                logger.error(f"인증 실패: {response.status_code} - {response.text}")
                return False
        except Exception as e:
            logger.error(f"인증 요청 실패: {e}")
            return False
    
    def get_headers(self):
        """인증 헤더 반환"""
        if not self.access_token:
            raise ValueError("먼저 authenticate()를 호출해주세요")
        
        return {
            'Authorization': f'Bearer {self.access_token}',
            'Content-Type': 'application/json'
        }
    
    def test_user_profile(self):
        """사용자 프로필 조회 테스트"""
        url = f"{self.base_url}/api/v1/users/profile/"
        
        try:
            response = requests.get(url, headers=self.get_headers())
            if response.status_code == 200:
                profile = response.json()
                logger.info(f"사용자 프로필 조회 성공: {profile.get('username')}")
                return profile
            else:
                logger.error(f"프로필 조회 실패: {response.status_code}")
                return None
        except Exception as e:
            logger.error(f"프로필 조회 요청 실패: {e}")
            return None
    
    def test_analysis_jobs_list(self):
        """분석 작업 목록 조회 테스트"""
        url = f"{self.base_url}/api/v1/analysis/jobs/"
        
        try:
            response = requests.get(url, headers=self.get_headers())
            if response.status_code == 200:
                jobs = response.json()
                logger.info(f"분석 작업 목록 조회 성공: {len(jobs.get('results', []))}개")
                return jobs
            else:
                logger.error(f"작업 목록 조회 실패: {response.status_code}")
                return None
        except Exception as e:
            logger.error(f"작업 목록 조회 요청 실패: {e}")
            return None
    
    def test_create_analysis_job(self):
        """분석 작업 생성 테스트"""
        url = f"{self.base_url}/api/v1/analysis/jobs/"
        data = {
            "job_name": "API Test Job",
            "description": "API를 통한 테스트 작업",
            "model_type": "docsynth300k",
            "processing_mode": "basic",
            "total_images": 1
        }
        
        try:
            response = requests.post(url, json=data, headers=self.get_headers())
            if response.status_code == 201:
                job = response.json()
                logger.info(f"분석 작업 생성 성공: Job ID {job.get('id')}")
                return job
            else:
                logger.error(f"작업 생성 실패: {response.status_code} - {response.text}")
                return None
        except Exception as e:
            logger.error(f"작업 생성 요청 실패: {e}")
            return None
    
    def test_file_upload(self, file_path=None):
        """파일 업로드 테스트"""
        url = f"{self.base_url}/api/v1/files/upload/"
        
        if not file_path:
            # 테스트용 이미지 파일 생성
            from PIL import Image, ImageDraw
            import io
            
            img = Image.new('RGB', (800, 600), color='white')
            draw = ImageDraw.Draw(img)
            draw.text((50, 50), 'API Test Document', fill='black')
            
            img_buffer = io.BytesIO()
            img.save(img_buffer, format='JPEG')
            img_buffer.seek(0)
            
            files = {'file': ('api_test.jpg', img_buffer, 'image/jpeg')}
        else:
            with open(file_path, 'rb') as f:
                files = {'file': f}
        
        try:
            headers = {'Authorization': f'Bearer {self.access_token}'}
            response = requests.post(url, files=files, headers=headers)
            
            if response.status_code == 201:
                file_data = response.json()
                logger.info(f"파일 업로드 성공: File ID {file_data.get('id')}")
                return file_data
            else:
                logger.error(f"파일 업로드 실패: {response.status_code} - {response.text}")
                return None
        except Exception as e:
            logger.error(f"파일 업로드 요청 실패: {e}")
            return None
    
    def test_pipeline_analysis(self):
        """파이프라인 분석 테스트 (파일 업로드 + 분석 시작)"""
        url = f"{self.base_url}/api/v1/analysis/jobs/upload_and_analyze/"
        
        # 테스트용 이미지 생성
        from PIL import Image, ImageDraw
        import io
        
        img = Image.new('RGB', (800, 600), color='white')
        draw = ImageDraw.Draw(img)
        draw.text((50, 50), 'Pipeline Test Document', fill='black')
        draw.text((50, 100), 'This document tests the full pipeline.', fill='black')
        
        img_buffer = io.BytesIO()
        img.save(img_buffer, format='JPEG')
        img_buffer.seek(0)
        
        # Multipart form data 준비
        files = {'file': ('pipeline_test.jpg', img_buffer, 'image/jpeg')}
        data = {
            'job_name': 'API Pipeline Test',
            'model_type': 'docsynth300k',
            'processing_mode': 'basic'
        }
        
        try:
            headers = {'Authorization': f'Bearer {self.access_token}'}
            response = requests.post(url, files=files, data=data, headers=headers)
            
            if response.status_code == 201:
                result = response.json()
                logger.info(f"파이프라인 분석 시작 성공: Job ID {result.get('job_id')}")
                return result
            else:
                logger.error(f"파이프라인 분석 실패: {response.status_code} - {response.text}")
                return None
        except Exception as e:
            logger.error(f"파이프라인 분석 요청 실패: {e}")
            return None
    
    def test_job_status(self, job_id):
        """작업 상태 확인 테스트"""
        url = f"{self.base_url}/api/v1/analysis/jobs/{job_id}/"
        
        try:
            response = requests.get(url, headers=self.get_headers())
            if response.status_code == 200:
                job_data = response.json()
                logger.info(f"작업 상태 조회 성공: {job_data.get('status')}")
                return job_data
            else:
                logger.error(f"작업 상태 조회 실패: {response.status_code}")
                return None
        except Exception as e:
            logger.error(f"작업 상태 조회 요청 실패: {e}")
            return None
    
    def run_comprehensive_test(self):
        """종합 API 테스트 실행"""
        print("🚀 SmartEye API 종합 테스트 시작")
        
        # 1. 인증 테스트
        print("\n1. JWT 인증 테스트...")
        if not self.authenticate():
            print("❌ 인증 실패 - 테스트 중단")
            return False
        
        # 2. 사용자 프로필 테스트
        print("\n2. 사용자 프로필 조회 테스트...")
        profile = self.test_user_profile()
        if profile:
            print(f"✅ 프로필 조회 성공: {profile.get('username')}")
        
        # 3. 분석 작업 목록 조회
        print("\n3. 분석 작업 목록 조회 테스트...")
        jobs = self.test_analysis_jobs_list()
        if jobs:
            print(f"✅ 작업 목록 조회 성공: {len(jobs.get('results', []))}개")
        
        # 4. 분석 작업 생성
        print("\n4. 분석 작업 생성 테스트...")
        new_job = self.test_create_analysis_job()
        if new_job:
            print(f"✅ 작업 생성 성공: Job ID {new_job.get('id')}")
        
        # 5. 파일 업로드
        print("\n5. 파일 업로드 테스트...")
        uploaded_file = self.test_file_upload()
        if uploaded_file:
            print(f"✅ 파일 업로드 성공: File ID {uploaded_file.get('id')}")
        
        # 6. 파이프라인 분석 (통합 테스트)
        print("\n6. 파이프라인 분석 통합 테스트...")
        pipeline_result = self.test_pipeline_analysis()
        if pipeline_result:
            job_id = pipeline_result.get('job_id')
            print(f"✅ 파이프라인 분석 시작 성공: Job ID {job_id}")
            
            # 작업 상태 확인
            if job_id:
                print(f"\n7. 작업 상태 확인 (Job ID: {job_id})...")
                status = self.test_job_status(job_id)
                if status:
                    print(f"✅ 작업 상태: {status.get('status')}")
        
        print("\n🎉 SmartEye API 종합 테스트 완료!")
        return True


def main():
    """메인 실행 함수"""
    # 로깅 설정
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )
    
    # API 테스터 생성 및 실행
    tester = SmartEyeAPITester()
    tester.run_comprehensive_test()


if __name__ == "__main__":
    main()
