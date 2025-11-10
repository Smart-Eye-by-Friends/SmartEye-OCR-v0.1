# -*- coding: utf-8 -*-
"""
SmartEyeSsen API Routes (Phase 3.1, 3.2, 3.3)
==============================================
(Phase 3.1: 프로젝트/페이지 관리 API 추가)
(Phase 3.3: 통합 다운로드 API 추가)

FastAPI 라우트 정의

주요 엔드포인트:
- GET /health: 헬스 체크
- GET /: API 루트 정보
- POST /api/projects: 새 프로젝트 생성 (3.1)
- GET /api/projects: 모든 프로젝트 목록 조회 (3.1)
- GET /api/projects/{project_id}: 프로젝트 상세 조회 (3.1)
- POST /api/pages/upload: 프로젝트에 페이지 추가 (3.1)
- GET /api/projects/{project_id}/pages: 프로젝트 페이지 목록 조회 (3.1)
- GET /api/pages/{page_id}: 페이지 상세 조회 (3.1)
- POST /api/projects/{project_id}/analyze: 배치 분석 (동기) (3.2)
- POST /api/projects/{project_id}/analyze-async: 배치 분석 (비동기) (3.2)
- GET /api/projects/{project_id}/status: 프로젝트 상태 조회 (3.2)
- GET /api/projects/{project_id}/combined-text: 통합 텍스트 조회 (3.3)
- POST /api/projects/{project_id}/download: Word 문서 다운로드 (3.3)
- GET /download/{filename}: 생성된 파일 다운로드 (3.3)
"""

from fastapi import FastAPI, BackgroundTasks, HTTPException, status, UploadFile, File, Form, Depends
from fastapi.responses import JSONResponse, StreamingResponse, FileResponse
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List, Union
from loguru import logger
import sys
import os
from pathlib import Path
from datetime import datetime
import io
import asyncio

# 프로젝트 루트를 Python 경로에 추가
project_root = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(project_root))

# 서비스 모듈 임포트
from app.services.batch_analysis import analyze_project_batch, get_project_mock as get_project_status_mock, mock_projects, mock_pages # status용 조회 함수 이름 변경
from app.services.project_service import ( # Phase 3.1
    create_new_project,
    get_project_details,
    list_all_projects,
    add_new_page,
    get_page_details,
    list_pages_for_project
)
from app.services.download_service import ( # Phase 3.3
    generate_combined_text,
    generate_word_document
)
from app.services.pdf_processor import pdf_processor # PDF 처리 서비스
from app.services.batch_analysis import (
    get_current_page_text, # <--- 신규 임포트
    save_user_edited_version # <--- 신규 임포트
)

# FastAPI 관련 임포트
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

# 스키마 임포트
from app import schemas

# ============================================================================
# FastAPI 앱 초기화
# ============================================================================

app = FastAPI(
    title="SmartEyeSsen API",
    description="AI 기반 학습지 분석 시스템 API (Phase 3.1, 3.2, 3.3)",
    version="3.3.0" # 버전 업데이트
)

# --- 🔽 [필수] CORS 미들웨어 추가 🔽 ---
origins = [
    "http://localhost:3000", # React (Create React App)
    "http://localhost:5173", # Vue/React (Vite)
    "http://localhost:8080", # Vue (Vue CLI)
    # TODO: 향후 배포될 프론트엔드 도메인 추가
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,       # 허용할 출처
    allow_credentials=True,    # 쿠키/인증 헤더 허용
    allow_methods=["*"],       # 모든 HTTP 메소드 허용
    allow_headers=["*"],       # 모든 HTTP 헤더 허용
)
# --- 🔼 [필수] CORS 미들웨어 추가 🔼 ---

# --- 🔽 [필수] 정적 파일 마운트 추가 🔽 ---
# 'uploads' 폴더를 '/uploads' URL 경로로 서빙합니다.
# project_root는 .../Project/backend/app/api/routes.py에서 .../Project/backend/를 가리켜야 합니다.
# project_service.py가 backend/uploads/에 저장하므로:
static_path = project_root / "uploads" 
app.mount("/uploads", StaticFiles(directory=static_path), name="uploads")
# --- 🔼 [필수] 정적 파일 마운트 추가 🔼 ---

# ============================================================================
# Phase 3.1: 프로젝트 및 페이지 관리 API
# ============================================================================

@app.post(
    "/api/projects",
    response_model=schemas.Project,
    status_code=status.HTTP_201_CREATED,
    summary="새 프로젝트 생성",
    description="사용자 ID, 문서 타입, 프로젝트 이름을 받아 새 분석 프로젝트를 생성합니다."
)
def create_project(project_data: schemas.ProjectCreate) -> schemas.Project:
    """새 프로젝트 생성 엔드포인트"""
    try:
        project = create_new_project(
            user_id=project_data.user_id, # 실제로는 인증 통해 획득
            doc_type_id=project_data.doc_type_id,
            project_name=project_data.project_name
        )
        return schemas.Project.model_validate(project) # Pydantic v2
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(ve))
    except Exception as e:
        logger.error(f"프로젝트 생성 중 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="프로젝트 생성 실패")

@app.get(
    "/api/projects",
    response_model=schemas.ProjectListResponse,
    summary="모든 프로젝트 목록 조회",
    description="현재 Mock DB에 저장된 모든 프로젝트 목록을 반환합니다."
)
def get_all_projects() -> schemas.ProjectListResponse:
    """모든 프로젝트 목록 조회 엔드포인트"""
    projects = list_all_projects()
    # Pydantic 모델 리스트로 변환
    project_models = [schemas.Project.model_validate(p) for p in projects]
    return schemas.ProjectListResponse(projects=project_models)

@app.get(
    "/api/projects/{project_id}",
    response_model=schemas.Project,
    summary="프로젝트 상세 정보 조회",
    description="특정 프로젝트의 상세 정보를 조회합니다."
)
def get_project(project_id: int) -> schemas.Project:
    """프로젝트 상세 조회 엔드포인트"""
    project = get_project_details(project_id)
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트 ID {project_id} 없음")
    return schemas.Project.model_validate(project)

@app.post(
    "/api/pages/upload",
    response_model=Union[schemas.PageCreateResponse, schemas.MultiPageCreateResponse],
    status_code=status.HTTP_201_CREATED,
    summary="프로젝트에 페이지 추가",
    description="이미지 또는 PDF 파일을 업로드하여 특정 프로젝트에 새 페이지로 추가합니다. PDF는 자동으로 페이지별 이미지로 변환됩니다."
)
async def upload_page(
    project_id: int = Form(...),
    page_number: Optional[int] = Form(None), # 선택적으로 받도록 수정
    image: UploadFile = File(...)
) -> Union[schemas.PageCreateResponse, schemas.MultiPageCreateResponse]:
    """페이지 추가 (이미지/PDF 업로드) 엔드포인트"""
    # MIME 타입 검증: 이미지 또는 PDF 허용
    if not image.content_type or not (
        image.content_type.startswith("image/") or
        image.content_type == "application/pdf"
    ):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 파일(jpg, png 등) 또는 PDF 파일만 업로드 가능합니다."
        )

    try:
        # === PDF 파일 처리 ===
        if image.content_type == "application/pdf":
            logger.info(f"PDF 파일 업로드 시작 - ProjectID: {project_id}, FileName: {image.filename}")

            # PDF 바이트 데이터 읽기
            pdf_bytes = await image.read()

            # 현재 프로젝트의 다음 페이지 번호 결정
            from app.services.project_service import get_pages_for_project_mock
            existing_pages = get_pages_for_project_mock(project_id)
            start_page_number = len(existing_pages) + 1

            # PDF를 이미지로 변환
            try:
                converted_pages_info = pdf_processor.convert_pdf_to_images(
                    pdf_bytes=pdf_bytes,
                    project_id=project_id,
                    start_page_number=start_page_number
                )
            except ValueError as pdf_error:
                logger.error(f"PDF 변환 실패: {pdf_error}")
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"PDF 변환 실패: {str(pdf_error)}"
                )

            if not converted_pages_info:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="PDF에서 변환된 페이지가 없습니다."
                )

            concurrency_limit = max(1, min(len(converted_pages_info), 5))
            semaphore = asyncio.Semaphore(concurrency_limit)
            created_pages: List[Dict[str, Any]] = []
            failed_pages: List[Dict[str, Any]] = []

            async def process_page(page_info: Dict[str, Any]) -> None:
                async with semaphore:
                    try:
                        page = await add_new_page(
                            project_id=project_id,
                            page_number=page_info['page_number'],
                            image_file=None,  # 이미 저장됨
                            pre_saved_image_path=page_info['image_path'],
                            pre_saved_image_width=page_info['width'],
                            pre_saved_image_height=page_info['height']
                        )
                        created_pages.append(page)
                        logger.debug(
                            "PDF 페이지 추가 완료 - PageID: {}, PageNumber: {}",
                            page.get('page_id'),
                            page.get('page_number')
                        )
                    except Exception as page_error:
                        error_detail = {
                            "page_number": page_info.get('page_number'),
                            "error": str(page_error)
                        }
                        failed_pages.append(error_detail)
                        logger.error(
                            "페이지 추가 실패 (페이지 번호 {}): {}",
                            page_info.get('page_number'),
                            page_error,
                            exc_info=True
                        )

            await asyncio.gather(*(process_page(info) for info in converted_pages_info))

            if failed_pages:
                logger.warning(
                    "PDF 페이지 추가 중 {}건 실패 - details: {}",
                    len(failed_pages),
                    failed_pages
                )

            created_pages.sort(key=lambda p: p.get('page_number', 0))
            logger.info(
                "PDF 업로드 완료 - {}개 페이지 생성 (총 시도: {}, 실패: {})",
                len(created_pages),
                len(converted_pages_info),
                len(failed_pages)
            )

            # MultiPageCreateResponse 반환
            return schemas.MultiPageCreateResponse(
                project_id=project_id,
                total_created=len(created_pages),
                source_type="pdf",
                pages=[schemas.PageBase.model_validate(p) for p in created_pages]
            )

        # === 이미지 파일 처리 (기존 로직) ===
        else:
            page = await add_new_page(project_id, page_number, image)
            return schemas.PageCreateResponse.model_validate(page)

    except HTTPException:
        # HTTPException은 그대로 전달
        raise
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(ve))
    except FileNotFoundError as fnfe:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트를 찾을 수 없음: {str(fnfe)}")
    except OSError as ose:
        logger.error(f"파일 저장 오류: {ose}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"파일 저장 실패: {str(ose)}")
    except Exception as e:
        logger.error(f"예기치 않은 페이지 추가 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="서버 내부 오류")

@app.get(
    "/api/projects/{project_id}/pages",
    response_model=schemas.PageListResponse,
    summary="프로젝트 페이지 목록 조회",
    description="특정 프로젝트에 속한 모든 페이지 목록을 페이지 번호 순으로 반환합니다."
)
def get_project_pages(project_id: int) -> schemas.PageListResponse:
    """프로젝트 페이지 목록 조회 엔드포인트"""
    try:
        pages = list_pages_for_project(project_id)
        project = get_project_details(project_id) # 프로젝트 정보도 함께 조회
        if not project:
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트 ID {project_id} 없음")

        # Pydantic 모델 리스트로 변환
        page_models = [schemas.PageBase.model_validate(p) for p in pages]
        return schemas.PageListResponse(
            project_id=project_id,
            total_pages=project['total_pages'],
            pages=page_models
        )
    except ValueError as ve:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(ve))
    except Exception as e:
        logger.error(f"페이지 목록 조회 중 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="페이지 목록 조회 실패")

@app.get(
    "/api/pages/{page_id}",
    response_model=schemas.PageBase, # 간단한 정보만 반환
    summary="페이지 상세 정보 조회",
    description="특정 페이지의 상세 정보를 조회합니다."
)
def get_page(page_id: int) -> schemas.PageBase:
    """페이지 상세 조회 엔드포인트"""
    page = get_page_details(page_id)
    if not page:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"페이지 ID {page_id} 없음")
    return schemas.PageBase.model_validate(page)


# ============================================================================
# Phase 3.1 (계속): 텍스트 버전 관리 (신규 API)
# ============================================================================

@app.get(
    "/api/pages/{page_id}/text",
    response_model=schemas.PageTextResponse,
    summary="현재 페이지 텍스트 조회 (에디터 로딩용)",
    description="페이지의 현재 활성화된(is_current=True) 텍스트 버전을 반환합니다."
)
def get_page_text_endpoint(page_id: int):
    """프론트엔드 텍스트 에디터가 호출할 초기 텍스트 로딩 API"""
    try:
        version_data = get_current_page_text(page_id)
        if not version_data:
             raise HTTPException(
                 status_code=status.HTTP_404_NOT_FOUND,
                 detail="이 페이지의 텍스트를 찾을 수 없거나 아직 분석되지 않았습니다."
             )
        return schemas.PageTextResponse.model_validate(version_data)
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(ve))
    except Exception as e:
        logger.error(f"페이지 텍스트 조회 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="텍스트 조회 실패")

@app.post(
    "/api/pages/{page_id}/text",
    response_model=schemas.PageTextResponse,
    summary="사용자 수정 텍스트 저장",
    description="사용자가 수정한 텍스트를 'user_edited' 타입의 새 버전으로 저장하고, 이 버전을 'is_current'로 설정합니다."
)
def save_page_text_endpoint(page_id: int, update_data: schemas.PageTextUpdate):
    """프론트엔드 텍스트 에디터의 '저장' 버튼이 호출할 API"""
    try:
        new_version = save_user_edited_version(
            page_id=page_id,
            content=update_data.content,
            user_id=update_data.user_id
        )
        return schemas.PageTextResponse.model_validate(new_version)
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(ve))
    except Exception as e:
        logger.error(f"페이지 텍스트 저장 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="텍스트 저장 실패")

# ============================================================================
# Phase 3.2: 배치 분석 API (이전 답변과 동일, 함수명 충돌 방지 위해 수정)
# ============================================================================

@app.post(
    "/api/projects/{project_id}/analyze",
    response_model=schemas.AnalyzeProjectResponse,
    status_code=status.HTTP_200_OK,
    summary="프로젝트 배치 분석 (동기)",
    # ... (description 동일) ...
)
def analyze_project_sync_endpoint( # 함수 이름 변경
    project_id: int,
    request: schemas.AnalyzeProjectRequest
) -> schemas.AnalyzeProjectResponse:
    """ 프로젝트 배치 분석 (동기) 엔드포인트 """
    logger.info(f"📥 동기 배치 분석 요청: project_id={project_id}")
    if project_id not in mock_projects:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트 ID {project_id} 없음")
    try:
        result = analyze_project_batch(
            project_id=project_id, document_type=request.document_type,
            use_ai_descriptions=request.use_ai_descriptions, api_key=request.api_key )
        # 결과 모델 변환 시 에러 처리 추가
        validated_results = []
        for page_res in result.get('page_results', []):
            try:
                # 수정: stats 필드가 없을 경우 기본값 제공
                page_res_validated = {**page_res, 'stats': page_res.get('stats', {})}
                validated_results.append(schemas.PageAnalysisResult.model_validate(page_res_validated))
            except (ValueError, TypeError, KeyError) as val_err:
                 logger.warning(f"페이지 결과 검증 실패 ID {page_res.get('page_id')}: {val_err}")
                 # 실패한 페이지는 기본값으로 대체하거나 제외
                 validated_results.append(schemas.PageAnalysisResult(
                     page_id=page_res.get('page_id', -1), page_number=page_res.get('page_number', -1),
                     status='validation_error', processing_time=0.0, error=str(val_err),
                     stats={} # 기본 stats 추가
                 ))

        # Pydantic 모델로 변환하기 전에 page_results를 올바른 형태로 변환
        response_data = {
            **result,
            'page_results': [res.model_dump() for res in validated_results]
        }
        return schemas.AnalyzeProjectResponse.model_validate(response_data)

    except ValueError as ve:
        logger.error(f"❌ 잘못된 프로젝트 데이터: {ve}")
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"잘못된 요청: {str(ve)}")
    except FileNotFoundError as fnfe:
        logger.error(f"❌ 파일을 찾을 수 없음: {fnfe}")
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"파일 없음: {str(fnfe)}")
    except OSError as ose:
        logger.error(f"❌ 파일 시스템 오류: {ose}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"시스템 오류: {str(ose)}")
    except Exception as e:
        logger.error(f"❌ 예기치 않은 배치 분석 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="서버 내부 오류")

@app.post(
    "/api/projects/{project_id}/analyze-async",
    status_code=status.HTTP_202_ACCEPTED,
    summary="프로젝트 배치 분석 (비동기)",
    response_model=schemas.MessageResponse, # 간단한 메시지 반환
    # ... (description 동일) ...
)
def analyze_project_async_endpoint( # 함수 이름 변경
    project_id: int,
    request: schemas.AnalyzeProjectRequest,
    background_tasks: BackgroundTasks
) -> schemas.MessageResponse:
    """ 프로젝트 배치 분석 (비동기) 엔드포인트 """
    logger.info(f"📥 비동기 배치 분석 요청: project_id={project_id}")
    if project_id not in mock_projects:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트 ID {project_id} 없음")

    background_tasks.add_task(
        analyze_project_batch, project_id=project_id, document_type=request.document_type,
        use_ai_descriptions=request.use_ai_descriptions, api_key=request.api_key )
    logger.info(f"📤 비동기 배치 분석 시작됨: project_id={project_id}")
    return schemas.MessageResponse(
        message="배치 분석이 백그라운드에서 시작되었습니다.",
        # status_url은 클라이언트가 직접 생성하도록 유도 (선택적)
        # status_url=f"/api/projects/{project_id}/status"
    )

@app.get(
    "/api/projects/{project_id}/status",
    response_model=schemas.ProjectStatusResponse,
    status_code=status.HTTP_200_OK,
    summary="프로젝트 상태 조회",
    # ... (description 동일) ...
)
def get_project_status_endpoint(project_id: int) -> schemas.ProjectStatusResponse: # 함수 이름 변경
    """ 프로젝트 상태 조회 엔드포인트 """
    logger.debug(f"📊 프로젝트 상태 조회 요청: project_id={project_id}")
    project = get_project_status_mock(project_id) # 이름 변경된 mock 함수 사용
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"프로젝트 ID {project_id} 없음")

    pages = [p for p in mock_pages.values() if p['project_id'] == project_id]
    pages.sort(key=lambda x: x['page_number'])
    # 페이지 정보 간소화 및 datetime 객체를 ISO 문자열로 변환
    page_statuses = []
    for p in pages:
        analyzed_at_iso = None
        analyzed_at = p.get('analyzed_at')
        # ✅ 타입 안전성 강화: isinstance로 datetime 타입 확인 후 isoformat() 호출
        if analyzed_at and isinstance(analyzed_at, datetime):
            analyzed_at_iso = analyzed_at.isoformat()

        page_statuses.append({
            'page_id': p['page_id'], 'page_number': p['page_number'],
            'analysis_status': p['analysis_status'],
            'processing_time': p.get('processing_time'),
            'analyzed_at': analyzed_at_iso
        })


    return schemas.ProjectStatusResponse(
        project_id=project['project_id'], project_name=project['project_name'],
        total_pages=project['total_pages'], status=project['status'],
        created_at=project['created_at'], updated_at=project['updated_at'],
        pages=page_statuses )

# ============================================================================
# Phase 3.3: 통합 다운로드 API
# ============================================================================

@app.get(
    "/api/projects/{project_id}/combined-text",
    response_model=schemas.CombinedTextResponse,
    summary="통합 텍스트 조회",
    description="프로젝트의 모든 페이지에 대한 최신 텍스트 버전을 통합하여 JSON으로 반환합니다."
)
def get_combined_project_text(project_id: int) -> schemas.CombinedTextResponse:
    """ 통합 텍스트 조회 엔드포인트 """
    try:
        combined_data = generate_combined_text(project_id)
        # 날짜 문자열을 datetime 객체로 변환 (Pydantic 검증용)
        combined_data['generated_at'] = datetime.fromisoformat(combined_data['generated_at'])
        return schemas.CombinedTextResponse.model_validate(combined_data)
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(ve))
    except KeyError as ke:
        logger.error(f"데이터 구조 오류: {ke}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="데이터 구조 오류")
    except Exception as e:
        logger.error(f"예기치 않은 통합 텍스트 생성 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="서버 내부 오류")

@app.post(
    "/api/projects/{project_id}/download",
    response_class=StreamingResponse, # StreamingResponse 사용
    summary="Word 문서 다운로드 생성",
    description="프로젝트의 통합 텍스트를 Word(.docx) 문서로 생성하여 다운로드합니다."
)
async def download_project_as_word(project_id: int) -> StreamingResponse:
    """ Word 문서 다운로드 엔드포인트 """
    try:
        filename, file_stream = generate_word_document(project_id)

        # StreamingResponse 사용하여 메모리 내 스트림 전송
        return StreamingResponse(
            file_stream,
            media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            headers={"Content-Disposition": f"attachment; filename=\"{filename}\""}
        )
    except ImportError as ie:
         raise HTTPException(status_code=status.HTTP_501_NOT_IMPLEMENTED, detail=str(ie))
    except ValueError as ve:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(ve))
    except KeyError as ke:
        logger.error(f"Word 문서 생성 중 데이터 구조 오류: {ke}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="데이터 구조 오류")
    except OSError as ose:
        logger.error(f"Word 문서 생성 중 파일 시스템 오류: {ose}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="파일 시스템 오류")
    except Exception as e:
        logger.error(f"예기치 않은 Word 문서 생성 오류: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="서버 내부 오류")

# 다운로드 경로는 실제 파일 서빙 필요 시 사용 (StreamingResponse는 URL 불필요)
# @app.get("/download/{filename}")
# async def download_generated_file(filename: str):
#     """ 생성된 파일 다운로드 엔드포인트 """
#     file_path = os.path.join("static", filename) # 실제 파일 저장 경로 가정
#     if not os.path.exists(file_path):
#         raise HTTPException(status_code=404, detail="파일 없음")
#     return FileResponse(path=file_path, filename=filename, media_type='application/octet-stream')


# ============================================================================
# 기타 API (헬스 체크, 루트)
# ============================================================================

@app.get("/health", response_model=schemas.MessageResponse, summary="헬스 체크")
def health_check() -> schemas.MessageResponse:
    """API 서버 상태 확인"""
    return schemas.MessageResponse(message="healthy")

@app.get("/", response_model=Dict[str, str], summary="API 루트")
def read_root() -> Dict[str, str]:
    """API 정보 및 문서 링크"""
    return {
        "message": "SmartEyeSsen API (Phase 3.3)",
        "version": "3.3.0",
        "docs": "/docs",
        "redoc": "/redoc"
    }

# ============================================================================
# 전역 에러 핸들러 및 라이프사이클 (변경 없음)
# ============================================================================
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    # ... (이전 답변과 동일한 코드) ...
    """전역 에러 핸들러"""
    logger.error(f"⚠️ 처리되지 않은 예외 발생: {exc}", exc_info=True)
    return JSONResponse( status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={ "detail": "서버 내부 오류가 발생했습니다.", "error": str(exc)} )

@app.on_event("startup")
async def startup_event():
    logger.info("🚀 SmartEyeSsen API 서버 시작됨 (Phase 3.3)")
    # 서버 시작 시 Mock DB 초기화 (테스트 용이성)
    from app.services.batch_analysis import initialize_mock_db_for_test
    from app.services.batch_analysis import _save_text_version
    project_id = initialize_mock_db_for_test(num_pages=2) # 2페이지만 생성
    _save_text_version(page_id=1, content="페이지 1의 자동 포맷된 텍스트입니다.", version_type='auto_formatted')
    _save_text_version(page_id=2, content="페이지 2의 자동 포맷된 텍스트입니다.", version_type='auto_formatted')
    logger.info(f"테스트용 Mock DB 및 텍스트 버전 초기화 완료: project_id={project_id}")


@app.on_event("shutdown")
async def shutdown_event():
    logger.info("🛑 SmartEyeSsen API 서버 종료됨")

# ============================================================================
# 개발 서버 실행 (변경 없음)
# ============================================================================
if __name__ == "__main__":
    import uvicorn
    # 서버 시작 시 startup 이벤트 핸들러에서 Mock DB 초기화 수행

    uvicorn.run( "routes:app", host="0.0.0.0", port=8000, reload=True, log_level="info" )
