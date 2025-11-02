"""
SmartEyeSsen Backend - Routers Package
=======================================
API 라우터 패키지 초기화

제공 라우터:
- projects.py: 프로젝트 CRUD
- pages.py: 페이지 업로드 및 조회
- analysis.py: 배치 분석 트리거
- downloads.py: 통합 텍스트/문서 다운로드 API
"""

from . import analysis, downloads, pages, projects

__all__ = [
    "analysis",
    "downloads",
    "pages",
    "projects",
]
