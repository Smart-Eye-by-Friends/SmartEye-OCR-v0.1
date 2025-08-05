from rest_framework import viewsets
from rest_framework.permissions import IsAuthenticated
from .models import SourceFile, FileUploadSession
from .serializers import SourceFileSerializer, FileUploadSessionSerializer


class SourceFileViewSet(viewsets.ModelViewSet):
    """원본 파일 관리 ViewSet"""
    serializer_class = SourceFileSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        return SourceFile.objects.filter(user=self.request.user)


class FileUploadSessionViewSet(viewsets.ModelViewSet):
    """파일 업로드 세션 관리 ViewSet"""
    serializer_class = FileUploadSessionSerializer
    permission_classes = [IsAuthenticated]
    
    def get_queryset(self):
        return FileUploadSession.objects.filter(user=self.request.user)