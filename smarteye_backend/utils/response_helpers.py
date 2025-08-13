"""
Response Helper Utilities

HTTP 응답 생성 관련 유틸리티 모듈
"""

import logging
from typing import Dict, Any, Optional, Union
from rest_framework import status
from rest_framework.response import Response
from django.http import JsonResponse
from datetime import datetime

logger = logging.getLogger(__name__)


class ResponseHelper:
    """HTTP 응답 생성 헬퍼 클래스"""
    
    @staticmethod
    def success_response(data: Dict[str, Any], 
                        status_code: int = status.HTTP_200_OK,
                        message: Optional[str] = None) -> Response:
        """성공 응답 생성"""
        response_data = {
            'success': True,
            'data': data,
            'timestamp': datetime.now().isoformat()
        }
        
        if message:
            response_data['message'] = message
        
        return Response(response_data, status=status_code)
    
    @staticmethod
    def error_response(error_message: str, 
                      status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR,
                      error_code: Optional[str] = None,
                      details: Optional[Dict] = None) -> Response:
        """에러 응답 생성"""
        response_data = {
            'success': False,
            'error': {
                'message': error_message,
                'timestamp': datetime.now().isoformat()
            }
        }
        
        if error_code:
            response_data['error']['code'] = error_code
        
        if details:
            response_data['error']['details'] = details
        
        logger.error(f"Error response: {error_message} (Status: {status_code})")
        
        return Response(response_data, status=status_code)
    
    @staticmethod
    def validation_error_response(errors: Dict[str, Any]) -> Response:
        """검증 오류 응답 생성"""
        return ResponseHelper.error_response(
            error_message="입력 데이터 검증에 실패했습니다.",
            status_code=status.HTTP_400_BAD_REQUEST,
            error_code="VALIDATION_ERROR",
            details={'validation_errors': errors}
        )
    
    @staticmethod
    def not_found_response(resource_name: str = "리소스") -> Response:
        """404 Not Found 응답 생성"""
        return ResponseHelper.error_response(
            error_message=f"{resource_name}를 찾을 수 없습니다.",
            status_code=status.HTTP_404_NOT_FOUND,
            error_code="RESOURCE_NOT_FOUND"
        )
    
    @staticmethod
    def unauthorized_response(message: str = "인증이 필요합니다.") -> Response:
        """401 Unauthorized 응답 생성"""
        return ResponseHelper.error_response(
            error_message=message,
            status_code=status.HTTP_401_UNAUTHORIZED,
            error_code="UNAUTHORIZED"
        )
    
    @staticmethod
    def forbidden_response(message: str = "권한이 없습니다.") -> Response:
        """403 Forbidden 응답 생성"""
        return ResponseHelper.error_response(
            error_message=message,
            status_code=status.HTTP_403_FORBIDDEN,
            error_code="FORBIDDEN"
        )
    
    @staticmethod
    def created_response(data: Dict[str, Any], message: str = "성공적으로 생성되었습니다.") -> Response:
        """201 Created 응답 생성"""
        return ResponseHelper.success_response(
            data=data,
            status_code=status.HTTP_201_CREATED,
            message=message
        )
    
    @staticmethod
    def accepted_response(data: Dict[str, Any], message: str = "요청이 접수되었습니다.") -> Response:
        """202 Accepted 응답 생성"""
        return ResponseHelper.success_response(
            data=data,
            status_code=status.HTTP_202_ACCEPTED,
            message=message
        )
    
    @staticmethod
    def no_content_response() -> Response:
        """204 No Content 응답 생성"""
        return Response(status=status.HTTP_204_NO_CONTENT)


class PaginatedResponseHelper:
    """페이지네이션 응답 헬퍼"""
    
    @staticmethod
    def paginated_response(data: list, 
                          page: int, 
                          page_size: int, 
                          total_count: int,
                          additional_data: Optional[Dict] = None) -> Response:
        """페이지네이션 응답 생성"""
        total_pages = (total_count + page_size - 1) // page_size
        has_next = page < total_pages
        has_previous = page > 1
        
        response_data = {
            'results': data,
            'pagination': {
                'page': page,
                'page_size': page_size,
                'total_count': total_count,
                'total_pages': total_pages,
                'has_next': has_next,
                'has_previous': has_previous,
                'next_page': page + 1 if has_next else None,
                'previous_page': page - 1 if has_previous else None
            }
        }
        
        if additional_data:
            response_data.update(additional_data)
        
        return ResponseHelper.success_response(response_data)


class ApiResponseBuilder:
    """API 응답 빌더 클래스"""
    
    def __init__(self):
        self.data = {}
        self.status_code = status.HTTP_200_OK
        self.message = None
        self.error_info = None
    
    def set_data(self, data: Dict[str, Any]) -> 'ApiResponseBuilder':
        """응답 데이터 설정"""
        self.data = data
        return self
    
    def set_status(self, status_code: int) -> 'ApiResponseBuilder':
        """상태 코드 설정"""
        self.status_code = status_code
        return self
    
    def set_message(self, message: str) -> 'ApiResponseBuilder':
        """메시지 설정"""
        self.message = message
        return self
    
    def set_error(self, error_message: str, error_code: Optional[str] = None) -> 'ApiResponseBuilder':
        """에러 정보 설정"""
        self.error_info = {
            'message': error_message,
            'code': error_code
        }
        return self
    
    def add_metadata(self, key: str, value: Any) -> 'ApiResponseBuilder':
        """메타데이터 추가"""
        if 'metadata' not in self.data:
            self.data['metadata'] = {}
        self.data['metadata'][key] = value
        return self
    
    def build(self) -> Response:
        """응답 생성"""
        if self.error_info:
            return ResponseHelper.error_response(
                error_message=self.error_info['message'],
                status_code=self.status_code,
                error_code=self.error_info.get('code')
            )
        else:
            return ResponseHelper.success_response(
                data=self.data,
                status_code=self.status_code,
                message=self.message
            )


class BulkOperationResponseHelper:
    """벌크 작업 응답 헬퍼"""
    
    @staticmethod
    def bulk_operation_response(success_items: list,
                               failed_items: list,
                               operation_name: str = "벌크 작업") -> Response:
        """벌크 작업 결과 응답"""
        total_items = len(success_items) + len(failed_items)
        success_count = len(success_items)
        failure_count = len(failed_items)
        
        data = {
            'summary': {
                'total_items': total_items,
                'success_count': success_count,
                'failure_count': failure_count,
                'success_rate': round((success_count / total_items * 100), 2) if total_items > 0 else 0
            },
            'successful_items': success_items,
            'failed_items': failed_items
        }
        
        if failure_count == 0:
            message = f"{operation_name}이 모두 성공적으로 완료되었습니다."
            status_code = status.HTTP_200_OK
        elif success_count == 0:
            message = f"{operation_name}이 모두 실패했습니다."
            status_code = status.HTTP_400_BAD_REQUEST
        else:
            message = f"{operation_name}이 부분적으로 완료되었습니다. (성공: {success_count}, 실패: {failure_count})"
            status_code = status.HTTP_207_MULTI_STATUS
        
        return ResponseHelper.success_response(
            data=data,
            status_code=status_code,
            message=message
        )


class StreamResponseHelper:
    """스트리밍 응답 헬퍼"""
    
    @staticmethod
    def create_streaming_response(generator, content_type: str = 'application/json'):
        """스트리밍 응답 생성"""
        from django.http import StreamingHttpResponse
        
        response = StreamingHttpResponse(
            generator,
            content_type=content_type
        )
        response['Cache-Control'] = 'no-cache'
        return response
    
    @staticmethod
    def create_file_download_response(file_path: str, filename: str):
        """파일 다운로드 응답 생성"""
        from django.http import FileResponse
        import os
        
        if not os.path.exists(file_path):
            return ResponseHelper.not_found_response("파일")
        
        response = FileResponse(
            open(file_path, 'rb'),
            as_attachment=True,
            filename=filename
        )
        return response


class WebSocketResponseHelper:
    """WebSocket 응답 헬퍼"""
    
    @staticmethod
    def create_websocket_message(message_type: str, data: Dict[str, Any]) -> Dict[str, Any]:
        """WebSocket 메시지 생성"""
        return {
            'type': message_type,
            'timestamp': datetime.now().isoformat(),
            'data': data
        }
    
    @staticmethod
    def create_progress_message(job_id: str, progress: float, message: str = "") -> Dict[str, Any]:
        """진행상황 메시지 생성"""
        return WebSocketResponseHelper.create_websocket_message(
            message_type='progress_update',
            data={
                'job_id': job_id,
                'progress': progress,
                'message': message
            }
        )
    
    @staticmethod
    def create_error_message(error: str, error_code: Optional[str] = None) -> Dict[str, Any]:
        """에러 메시지 생성"""
        return WebSocketResponseHelper.create_websocket_message(
            message_type='error',
            data={
                'error': error,
                'error_code': error_code
            }
        )