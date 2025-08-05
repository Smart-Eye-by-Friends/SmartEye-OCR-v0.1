from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.contrib.auth import get_user_model
from .models import UserPreference
from .serializers import UserSerializer, UserPreferenceSerializer

User = get_user_model()


class UserViewSet(viewsets.ModelViewSet):
    """사용자 관리 ViewSet"""
    
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        """현재 사용자만 조회 가능"""
        return User.objects.filter(id=self.request.user.id)
    
    @action(detail=False, methods=['get'])
    def me(self, request):
        """현재 사용자 정보 조회"""
        serializer = self.get_serializer(request.user)
        return Response(serializer.data)
    
    @action(detail=False, methods=['get'])
    def quota_status(self, request):
        """API 할당량 상태 조회"""
        user = request.user
        return Response({
            'quota_used': user.api_quota_used,
            'quota_limit': user.api_quota_limit,
            'quota_percentage': user.api_quota_percentage,
            'can_use_api': user.can_use_api(),
        })


class UserPreferenceViewSet(viewsets.ModelViewSet):
    """사용자 설정 관리 ViewSet"""
    
    serializer_class = UserPreferenceSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        """현재 사용자의 설정만 조회"""
        return UserPreference.objects.filter(user=self.request.user)
    
    def perform_create(self, serializer):
        """설정 생성 시 현재 사용자 자동 설정"""
        serializer.save(user=self.request.user)
