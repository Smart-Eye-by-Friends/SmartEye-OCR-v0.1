from rest_framework import viewsets
from rest_framework.permissions import IsAuthenticated
from .models import SourceFile, FileUploadSession
from .serializers import SourceFileSerializer, FileUploadSessionSerializer
from utils.mixins import SmartEyeViewSetMixin, SearchMixin


class SourceFileViewSet(SmartEyeViewSetMixin, SearchMixin, viewsets.ModelViewSet):
    """원본 파일 관리 ViewSet"""
    queryset = SourceFile.objects.all()
    serializer_class = SourceFileSerializer
    search_fields = ['original_filename', 'file_type']


class FileUploadSessionViewSet(SmartEyeViewSetMixin, viewsets.ModelViewSet):
    """파일 업로드 세션 관리 ViewSet"""
    queryset = FileUploadSession.objects.all()
    serializer_class = FileUploadSessionSerializer