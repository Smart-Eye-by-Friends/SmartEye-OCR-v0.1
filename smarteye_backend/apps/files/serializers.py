from rest_framework import serializers
from .models import SourceFile, FileUploadSession


class SourceFileSerializer(serializers.ModelSerializer):
    class Meta:
        model = SourceFile
        fields = ['id', 'original_filename', 'file_type', 'file_size_mb', 
                 'upload_status', 'created_at']
        read_only_fields = ['id', 'user', 'stored_filename', 'file_hash', 
                          'storage_path', 'created_at']


class FileUploadSessionSerializer(serializers.ModelSerializer):
    progress_percentage = serializers.ReadOnlyField()
    
    class Meta:
        model = FileUploadSession
        fields = ['id', 'session_id', 'filename', 'total_size', 'uploaded_size', 
                 'chunk_size', 'is_completed', 'progress_percentage', 'created_at', 'expires_at']
        read_only_fields = ['id', 'user', 'created_at']