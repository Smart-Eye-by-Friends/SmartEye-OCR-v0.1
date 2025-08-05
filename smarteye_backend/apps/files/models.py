from django.db import models
from django.contrib.auth import get_user_model
import hashlib

User = get_user_model()


class SourceFile(models.Model):
    """업로드된 원본 파일"""
    
    FILE_TYPES = [
        ('image', '이미지'),
        ('pdf', 'PDF'),
    ]
    
    UPLOAD_STATUS = [
        ('uploading', '업로드 중'),
        ('completed', '완료'),
        ('failed', '실패'),
    ]
    
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='uploaded_files',
        verbose_name='사용자'
    )
    
    original_filename = models.CharField(
        max_length=255,
        verbose_name='원본 파일명'
    )
    
    stored_filename = models.CharField(
        max_length=255,
        unique=True,
        verbose_name='저장된 파일명'
    )
    
    file_type = models.CharField(
        max_length=10,
        choices=FILE_TYPES,
        verbose_name='파일 유형'
    )
    
    file_size_mb = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        verbose_name='파일 크기 (MB)'
    )
    
    file_hash = models.CharField(
        max_length=64,
        unique=True,
        blank=True,
        verbose_name='파일 해시'
    )
    
    storage_path = models.CharField(
        max_length=500,
        verbose_name='저장 경로'
    )
    
    upload_status = models.CharField(
        max_length=20,
        choices=UPLOAD_STATUS,
        default='uploading',
        verbose_name='업로드 상태'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = '원본 파일'
        verbose_name_plural = '원본 파일들'
        db_table = 'source_files'
        indexes = [
            models.Index(fields=['user', 'created_at']),
            models.Index(fields=['file_hash']),
        ]
    
    def __str__(self):
        return f"{self.original_filename} ({self.file_size_mb}MB)"
    
    def save(self, *args, **kwargs):
        # 파일 해시 생성 (중복 방지용)
        if not self.file_hash and self.storage_path:
            self.file_hash = self._generate_file_hash()
        super().save(*args, **kwargs)
    
    def _generate_file_hash(self):
        """파일 해시 생성"""
        hasher = hashlib.sha256()
        hasher.update(f"{self.original_filename}{self.file_size_mb}".encode())
        return hasher.hexdigest()


class FileUploadSession(models.Model):
    """파일 업로드 세션 (대용량 파일 청크 업로드용)"""
    
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='upload_sessions',
        verbose_name='사용자'
    )
    
    session_id = models.CharField(
        max_length=255,
        unique=True,
        verbose_name='세션 ID'
    )
    
    filename = models.CharField(
        max_length=255,
        verbose_name='파일명'
    )
    
    total_size = models.BigIntegerField(
        verbose_name='총 크기 (bytes)'
    )
    
    uploaded_size = models.BigIntegerField(
        default=0,
        verbose_name='업로드된 크기 (bytes)'
    )
    
    chunk_size = models.IntegerField(
        default=1048576,  # 1MB
        verbose_name='청크 크기 (bytes)'
    )
    
    is_completed = models.BooleanField(
        default=False,
        verbose_name='완료 여부'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    expires_at = models.DateTimeField(
        verbose_name='만료일시'
    )
    
    class Meta:
        verbose_name = '파일 업로드 세션'
        verbose_name_plural = '파일 업로드 세션들'
        db_table = 'file_upload_sessions'
        indexes = [
            models.Index(fields=['user', 'created_at']),
            models.Index(fields=['session_id']),
            models.Index(fields=['expires_at']),
        ]
    
    def __str__(self):
        return f"{self.filename} - {self.session_id[:10]}..."
    
    @property
    def progress_percentage(self):
        """업로드 진행률 계산"""
        if self.total_size == 0:
            return 0
        return (self.uploaded_size / self.total_size) * 100
