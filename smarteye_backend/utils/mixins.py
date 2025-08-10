"""
Django ViewSet을 위한 공통 Mixin 클래스들
"""
from rest_framework import permissions
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework import status
from django.db.models import QuerySet
from typing import Any


class UserFilteredMixin:
    """
    현재 사용자로 필터링하는 Mixin
    사용자 소유의 데이터만 접근 가능하도록 제한
    """
    
    def get_queryset(self) -> QuerySet:
        """현재 사용자의 데이터만 반환"""
        queryset = super().get_queryset()
        
        # 사용자 필드가 있는 경우 필터링
        if hasattr(queryset.model, 'user'):
            return queryset.filter(user=self.request.user)
        
        # 소유자 필드가 있는 경우 필터링  
        elif hasattr(queryset.model, 'owner'):
            return queryset.filter(owner=self.request.user)
            
        # 생성자 필드가 있는 경우 필터링
        elif hasattr(queryset.model, 'created_by'):
            return queryset.filter(created_by=self.request.user)
        
        # 해당 필드가 없으면 원본 쿼리셋 반환
        return queryset
    
    def perform_create(self, serializer):
        """객체 생성 시 현재 사용자 설정"""
        # 사용자 필드가 있는 경우
        if hasattr(serializer.Meta.model, 'user'):
            serializer.save(user=self.request.user)
        # 소유자 필드가 있는 경우
        elif hasattr(serializer.Meta.model, 'owner'):
            serializer.save(owner=self.request.user)
        # 생성자 필드가 있는 경우
        elif hasattr(serializer.Meta.model, 'created_by'):
            serializer.save(created_by=self.request.user)
        else:
            serializer.save()


class TimestampMixin:
    """
    생성/수정 시간으로 필터링 및 정렬하는 Mixin
    """
    
    def get_queryset(self) -> QuerySet:
        """최신순으로 정렬된 쿼리셋 반환"""
        queryset = super().get_queryset()
        
        # 생성시간 필드로 정렬 (최신순)
        if hasattr(queryset.model, 'created_at'):
            return queryset.order_by('-created_at')
        elif hasattr(queryset.model, 'created'):
            return queryset.order_by('-created')
        
        return queryset
    
    @action(detail=False, methods=['get'])
    def recent(self, request):
        """최근 생성된 항목들 조회"""
        queryset = self.get_queryset()[:10]  # 최근 10개
        serializer = self.get_serializer(queryset, many=True)
        return Response(serializer.data)


class BulkActionMixin:
    """
    대량 작업(bulk operations)을 위한 Mixin
    """
    
    @action(detail=False, methods=['post'])
    def bulk_delete(self, request):
        """선택된 항목들 일괄 삭제"""
        ids = request.data.get('ids', [])
        
        if not ids:
            return Response(
                {'error': '삭제할 항목의 ID가 필요합니다.'}, 
                status=status.HTTP_400_BAD_REQUEST
            )
        
        queryset = self.get_queryset().filter(id__in=ids)
        deleted_count = queryset.count()
        
        if deleted_count == 0:
            return Response(
                {'error': '삭제할 항목을 찾을 수 없습니다.'},
                status=status.HTTP_404_NOT_FOUND
            )
        
        queryset.delete()
        
        return Response({
            'message': f'{deleted_count}개 항목이 삭제되었습니다.',
            'deleted_count': deleted_count
        })
    
    @action(detail=False, methods=['patch'])
    def bulk_update(self, request):
        """선택된 항목들 일괄 업데이트"""
        ids = request.data.get('ids', [])
        update_data = request.data.get('data', {})
        
        if not ids or not update_data:
            return Response(
                {'error': '업데이트할 항목의 ID와 데이터가 필요합니다.'},
                status=status.HTTP_400_BAD_REQUEST
            )
        
        queryset = self.get_queryset().filter(id__in=ids)
        updated_count = queryset.update(**update_data)
        
        if updated_count == 0:
            return Response(
                {'error': '업데이트할 항목을 찾을 수 없습니다.'},
                status=status.HTTP_404_NOT_FOUND
            )
        
        return Response({
            'message': f'{updated_count}개 항목이 업데이트되었습니다.',
            'updated_count': updated_count
        })


class StatusFilterMixin:
    """
    상태 필드로 필터링하는 Mixin
    """
    
    def get_queryset(self) -> QuerySet:
        """상태 파라미터로 필터링된 쿼리셋 반환"""
        queryset = super().get_queryset()
        status_param = self.request.query_params.get('status')
        
        if status_param and hasattr(queryset.model, 'status'):
            return queryset.filter(status=status_param)
        
        return queryset
    
    @action(detail=False, methods=['get'])
    def by_status(self, request):
        """상태별 항목 수 집계"""
        if not hasattr(self.get_queryset().model, 'status'):
            return Response(
                {'error': '이 모델은 상태 필드를 지원하지 않습니다.'},
                status=status.HTTP_400_BAD_REQUEST
            )
        
        from django.db.models import Count
        
        status_counts = (
            self.get_queryset()
            .values('status')
            .annotate(count=Count('id'))
            .order_by('status')
        )
        
        return Response({
            'status_counts': list(status_counts),
            'total': self.get_queryset().count()
        })


class SearchMixin:
    """
    검색 기능을 제공하는 Mixin
    search_fields 속성을 정의해야 함
    """
    
    search_fields = []  # 서브클래스에서 정의해야 함
    
    def get_queryset(self) -> QuerySet:
        """검색어로 필터링된 쿼리셋 반환"""
        queryset = super().get_queryset()
        search_query = self.request.query_params.get('search')
        
        if search_query and self.search_fields:
            from django.db.models import Q
            
            # 모든 검색 필드에 대해 OR 조건으로 검색
            search_filter = Q()
            for field in self.search_fields:
                search_filter |= Q(**{f"{field}__icontains": search_query})
            
            queryset = queryset.filter(search_filter)
        
        return queryset
    
    @action(detail=False, methods=['get'])
    def search_suggestions(self, request):
        """검색 자동완성 제안"""
        query = request.query_params.get('q', '')
        
        if not query or not self.search_fields:
            return Response({'suggestions': []})
        
        # 첫 번째 검색 필드를 기준으로 자동완성 제안 생성
        primary_field = self.search_fields[0]
        
        suggestions = (
            self.get_queryset()
            .filter(**{f"{primary_field}__icontains": query})
            .values_list(primary_field, flat=True)
            .distinct()[:10]
        )
        
        return Response({'suggestions': list(suggestions)})


class PermissionMixin:
    """
    권한 관리를 위한 Mixin
    """
    
    def get_permissions(self):
        """액션별로 다른 권한 적용"""
        if self.action == 'list':
            permission_classes = [permissions.IsAuthenticated]
        elif self.action == 'create':
            permission_classes = [permissions.IsAuthenticated]
        elif self.action in ['retrieve', 'update', 'partial_update', 'destroy']:
            permission_classes = [permissions.IsAuthenticated]
        else:
            permission_classes = [permissions.IsAuthenticated]
        
        return [permission() for permission in permission_classes]


class CacheResponseMixin:
    """
    응답 캐싱을 위한 Mixin
    """
    
    cache_timeout = 300  # 5분
    
    def get_cache_key(self, request) -> str:
        """캐시 키 생성"""
        import hashlib
        
        user_id = request.user.id if request.user.is_authenticated else 'anonymous'
        query_string = request.META.get('QUERY_STRING', '')
        path = request.path
        
        cache_data = f"{self.__class__.__name__}_{path}_{user_id}_{query_string}"
        return hashlib.md5(cache_data.encode()).hexdigest()
    
    def get_cached_response(self, request):
        """캐시된 응답 조회"""
        try:
            from django.core.cache import cache
            
            cache_key = self.get_cache_key(request)
            cached_data = cache.get(cache_key)
            
            if cached_data:
                return Response(cached_data)
                
        except Exception:
            pass  # 캐시 실패 시 무시
        
        return None
    
    def set_cached_response(self, request, response_data):
        """응답 캐싱"""
        try:
            from django.core.cache import cache
            
            cache_key = self.get_cache_key(request)
            cache.set(cache_key, response_data, self.cache_timeout)
            
        except Exception:
            pass  # 캐시 실패 시 무시


class LoggingMixin:
    """
    API 호출 로깅을 위한 Mixin
    """
    
    def initial(self, request, *args, **kwargs):
        """요청 시작 시 로깅"""
        super().initial(request, *args, **kwargs)
        
        import logging
        logger = logging.getLogger(f'smarteye.api.{self.__class__.__name__.lower()}')
        
        user = getattr(request, 'user', None)
        user_info = f"user:{user.id}" if user and user.is_authenticated else "anonymous"
        
        logger.info(
            f"API 호출: {request.method} {request.path} - {user_info} - "
            f"action:{getattr(self, 'action', 'unknown')}"
        )
    
    def finalize_response(self, request, response, *args, **kwargs):
        """응답 완료 시 로깅"""
        response = super().finalize_response(request, response, *args, **kwargs)
        
        import logging
        logger = logging.getLogger(f'smarteye.api.{self.__class__.__name__.lower()}')
        
        # 성능 로깅
        if hasattr(request, '_start_time'):
            duration = (time.time() - request._start_time) * 1000
            logger.debug(f"API 완료: {response.status_code} - {duration:.2f}ms")
        
        return response


class SmartEyeViewSetMixin(
    UserFilteredMixin, 
    TimestampMixin, 
    PermissionMixin, 
    LoggingMixin
):
    """
    SmartEye 프로젝트의 모든 ViewSet에서 사용하는 기본 Mixin
    """
    
    def get_serializer_context(self):
        """시리얼라이저 컨텍스트에 추가 정보 포함"""
        context = super().get_serializer_context()
        context.update({
            'user': self.request.user,
            'action': getattr(self, 'action', None),
            'view_name': self.__class__.__name__
        })
        return context