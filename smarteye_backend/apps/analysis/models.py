from django.db import models
from django.contrib.auth import get_user_model

User = get_user_model()


class AnalysisJob(models.Model):
    """분석 작업 모델"""
    
    STATUS_CHOICES = [
        ('pending', '대기중'),
        ('processing', '처리중'),
        ('completed', '완료'),
        ('failed', '실패'),
        ('cancelled', '취소됨'),
    ]
    
    MODEL_CHOICES = [
        ('docstructbench', 'DocStructBench'),
        ('doclaynet_docsynth', 'DocLayNet DocSynth'),
        ('docsynth300k', 'DocSynth300K'),
    ]
    
    PROCESSING_MODES = [
        ('basic', '기본'),
        ('advanced', '고급'),
    ]
    
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='analysis_jobs',
        verbose_name='사용자'
    )
    
    job_name = models.CharField(
        max_length=100,
        verbose_name='작업명'
    )
    
    description = models.TextField(
        blank=True,
        verbose_name='설명'
    )
    
    status = models.CharField(
        max_length=20,
        choices=STATUS_CHOICES,
        default='pending',
        verbose_name='상태'
    )
    
    model_type = models.CharField(
        max_length=50,
        choices=MODEL_CHOICES,
        verbose_name='모델 유형'
    )
    
    processing_mode = models.CharField(
        max_length=20,
        choices=PROCESSING_MODES,
        default='basic',
        verbose_name='처리 모드'
    )
    
    total_images = models.IntegerField(
        verbose_name='총 이미지 수'
    )
    
    processed_images = models.IntegerField(
        default=0,
        verbose_name='처리된 이미지 수'
    )
    
    failed_images = models.IntegerField(
        default=0,
        verbose_name='실패한 이미지 수'
    )
    
    batch_size = models.IntegerField(
        default=2,
        verbose_name='배치 크기'
    )
    
    enable_resume = models.BooleanField(
        default=True,
        verbose_name='재시작 활성화'
    )
    
    enable_ocr = models.BooleanField(
        default=True,
        verbose_name='OCR 활성화'
    )
    
    enable_api = models.BooleanField(
        default=True,
        verbose_name='API 활성화'
    )
    
    priority = models.IntegerField(
        default=0,
        verbose_name='우선순위'
    )
    
    started_at = models.DateTimeField(
        null=True,
        blank=True,
        verbose_name='시작일시'
    )
    
    completed_at = models.DateTimeField(
        null=True,
        blank=True,
        verbose_name='완료일시'
    )
    
    estimated_completion = models.DateTimeField(
        null=True,
        blank=True,
        verbose_name='예상 완료일시'
    )
    
    error_message = models.TextField(
        blank=True,
        verbose_name='오류 메시지'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    updated_at = models.DateTimeField(
        auto_now=True,
        verbose_name='수정일시'
    )
    
    class Meta:
        verbose_name = '분석 작업'
        verbose_name_plural = '분석 작업들'
        db_table = 'analysis_jobs'
        indexes = [
            models.Index(fields=['user', 'status', 'created_at']),
            models.Index(fields=['status', 'priority', 'created_at']),
        ]
    
    def __str__(self):
        return f"{self.job_name} ({self.get_status_display()})"
    
    @property
    def progress_percentage(self):
        """진행률 계산"""
        if self.total_images == 0:
            return 0
        return (self.processed_images / self.total_images) * 100


class ProcessedImage(models.Model):
    """처리된 이미지 모델"""
    
    STATUS_CHOICES = [
        ('pending', '대기중'),
        ('processing', '처리중'),
        ('completed', '완료'),
        ('failed', '실패'),
    ]
    
    source_file = models.ForeignKey(
        'files.SourceFile',
        on_delete=models.CASCADE,
        related_name='processed_images',
        verbose_name='원본 파일'
    )
    
    job = models.ForeignKey(
        AnalysisJob,
        on_delete=models.CASCADE,
        related_name='processed_image_set',
        verbose_name='분석 작업'
    )
    
    processed_filename = models.CharField(
        max_length=255,
        verbose_name='처리된 파일명'
    )
    
    page_number = models.IntegerField(
        default=1,
        verbose_name='페이지 번호'
    )
    
    image_width = models.IntegerField(
        verbose_name='이미지 너비'
    )
    
    image_height = models.IntegerField(
        verbose_name='이미지 높이'
    )
    
    processing_status = models.CharField(
        max_length=20,
        choices=STATUS_CHOICES,
        default='pending',
        verbose_name='처리 상태'
    )
    
    processing_time_ms = models.IntegerField(
        null=True,
        blank=True,
        verbose_name='처리 시간 (ms)'
    )
    
    memory_usage_mb = models.DecimalField(
        max_digits=8,
        decimal_places=2,
        null=True,
        blank=True,
        verbose_name='메모리 사용량 (MB)'
    )
    
    error_message = models.TextField(
        blank=True,
        verbose_name='오류 메시지'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    updated_at = models.DateTimeField(
        auto_now=True,
        verbose_name='수정일시'
    )
    
    class Meta:
        verbose_name = '처리된 이미지'
        verbose_name_plural = '처리된 이미지들'
        db_table = 'processed_images'
        indexes = [
            models.Index(fields=['job', 'processing_status']),
            models.Index(fields=['source_file', 'page_number']),
        ]
    
    def __str__(self):
        return f"{self.processed_filename} (Page {self.page_number})"


class LAMLayoutDetection(models.Model):
    """LAM 레이아웃 감지 결과"""
    
    image = models.ForeignKey(
        ProcessedImage,
        on_delete=models.CASCADE,
        related_name='layout_detections',
        verbose_name='이미지'
    )
    
    detection_order = models.IntegerField(
        verbose_name='감지 순서'
    )
    
    class_name = models.CharField(
        max_length=50,
        verbose_name='클래스명'
    )
    
    confidence = models.DecimalField(
        max_digits=5,
        decimal_places=4,
        verbose_name='신뢰도'
    )
    
    bbox_x1 = models.IntegerField(verbose_name='바운딩박스 X1')
    bbox_y1 = models.IntegerField(verbose_name='바운딩박스 Y1')
    bbox_x2 = models.IntegerField(verbose_name='바운딩박스 X2')
    bbox_y2 = models.IntegerField(verbose_name='바운딩박스 Y2')
    
    area_pixels = models.IntegerField(
        verbose_name='영역 픽셀 수'
    )
    
    width_ratio = models.DecimalField(
        max_digits=6,
        decimal_places=4,
        verbose_name='너비 비율'
    )
    
    height_ratio = models.DecimalField(
        max_digits=6,
        decimal_places=4,
        verbose_name='높이 비율'
    )
    
    center_x = models.IntegerField(verbose_name='중심 X')
    center_y = models.IntegerField(verbose_name='중심 Y')
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = 'LAM 레이아웃 감지'
        verbose_name_plural = 'LAM 레이아웃 감지들'
        db_table = 'lam_layout_detections'
        indexes = [
            models.Index(fields=['image', 'detection_order']),
            models.Index(fields=['class_name', 'confidence']),
        ]
    
    def __str__(self):
        return f"{self.class_name} ({self.confidence:.2f})"


class TSPMOCRResult(models.Model):
    """TSPM OCR 결과"""
    
    detection = models.ForeignKey(
        LAMLayoutDetection,
        on_delete=models.CASCADE,
        related_name='ocr_results',
        verbose_name='감지 결과'
    )
    
    extracted_text = models.TextField(
        verbose_name='추출된 텍스트'
    )
    
    processed_text = models.TextField(
        blank=True,
        verbose_name='처리된 텍스트'
    )
    
    confidence = models.DecimalField(
        max_digits=5,
        decimal_places=4,
        null=True,
        blank=True,
        verbose_name='신뢰도'
    )
    
    language = models.CharField(
        max_length=10,
        default='kor',
        verbose_name='언어'
    )
    
    processing_method = models.CharField(
        max_length=50,
        default='tesseract',
        verbose_name='처리 방법'
    )
    
    text_length = models.IntegerField(
        verbose_name='텍스트 길이'
    )
    
    processing_time_ms = models.IntegerField(
        null=True,
        blank=True,
        verbose_name='처리 시간 (ms)'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = 'TSPM OCR 결과'
        verbose_name_plural = 'TSPM OCR 결과들'
        db_table = 'tspm_ocr_results'
        indexes = [
            models.Index(fields=['detection']),
        ]
    
    def __str__(self):
        return f"OCR: {self.extracted_text[:50]}..."


class TSPMImageDescription(models.Model):
    """TSPM 이미지 설명"""
    
    detection = models.ForeignKey(
        LAMLayoutDetection,
        on_delete=models.CASCADE,
        related_name='image_descriptions',
        verbose_name='감지 결과'
    )
    
    description_text = models.TextField(
        verbose_name='설명 텍스트'
    )
    
    subject_category = models.CharField(
        max_length=50,
        blank=True,
        verbose_name='주제 카테고리'
    )
    
    description_type = models.CharField(
        max_length=50,
        blank=True,
        verbose_name='설명 유형'
    )
    
    api_model = models.CharField(
        max_length=50,
        default='gpt-4-turbo',
        verbose_name='API 모델'
    )
    
    api_cost = models.DecimalField(
        max_digits=8,
        decimal_places=4,
        null=True,
        blank=True,
        verbose_name='API 비용'
    )
    
    processing_time_ms = models.IntegerField(
        null=True,
        blank=True,
        verbose_name='처리 시간 (ms)'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = 'TSPM 이미지 설명'
        verbose_name_plural = 'TSPM 이미지 설명들'
        db_table = 'tspm_image_descriptions'
        indexes = [
            models.Index(fields=['detection']),
            models.Index(fields=['subject_category', 'description_type']),
        ]
    
    def __str__(self):
        return f"설명: {self.description_text[:50]}..."


class CIMOutput(models.Model):
    """CIM 최종 출력"""
    
    EXPORT_FORMATS = [
        ('text', '텍스트'),
        ('json', 'JSON'),
        ('xml', 'XML'),
        ('braille', '점자'),
    ]
    
    job = models.ForeignKey(
        AnalysisJob,
        on_delete=models.CASCADE,
        related_name='cim_outputs',
        verbose_name='분석 작업'
    )
    
    final_text = models.TextField(
        verbose_name='최종 텍스트'
    )
    
    braille_notation = models.TextField(
        blank=True,
        verbose_name='점자 표기'
    )
    
    reading_order = models.JSONField(
        default=dict,
        verbose_name='읽기 순서'
    )
    
    export_format = models.CharField(
        max_length=20,
        choices=EXPORT_FORMATS,
        default='text',
        verbose_name='출력 형식'
    )
    
    accessibility_score = models.DecimalField(
        max_digits=3,
        decimal_places=1,
        null=True,
        blank=True,
        verbose_name='접근성 점수'
    )
    
    word_count = models.IntegerField(
        null=True,
        blank=True,
        verbose_name='단어 수'
    )
    
    estimated_reading_time = models.IntegerField(
        null=True,
        blank=True,
        verbose_name='예상 읽기 시간 (분)'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = 'CIM 출력'
        verbose_name_plural = 'CIM 출력들'
        db_table = 'cim_outputs'
        indexes = [
            models.Index(fields=['job']),
        ]
    
    def __str__(self):
        return f"출력: {self.job.job_name}"


class DocumentStructure(models.Model):
    """문서 구조 분석 결과"""
    
    DIFFICULTY_LEVELS = [
        ('elementary', '초등'),
        ('middle', '중등'),
        ('high', '고등'),
    ]
    
    job = models.ForeignKey(
        AnalysisJob,
        on_delete=models.CASCADE,
        related_name='document_structures',
        verbose_name='분석 작업'
    )
    
    structure_type = models.CharField(
        max_length=50,
        verbose_name='구조 유형'
    )
    
    page_layout = models.JSONField(
        default=dict,
        verbose_name='페이지 레이아웃'
    )
    
    content_hierarchy = models.JSONField(
        default=dict,
        verbose_name='내용 계층 구조'
    )
    
    reading_flow = models.JSONField(
        default=dict,
        verbose_name='읽기 흐름'
    )
    
    difficulty_level = models.CharField(
        max_length=20,
        choices=DIFFICULTY_LEVELS,
        null=True,
        blank=True,
        verbose_name='난이도'
    )
    
    subject_area = models.CharField(
        max_length=50,
        blank=True,
        verbose_name='과목 영역'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = '문서 구조'
        verbose_name_plural = '문서 구조들'
        db_table = 'document_structure'
        indexes = [
            models.Index(fields=['job']),
            models.Index(fields=['subject_area', 'difficulty_level']),
        ]
    
    def __str__(self):
        return f"구조: {self.structure_type}"


class AnalysisResult(models.Model):
    """분석 결과 통합 모델"""
    
    RESULT_TYPES = [
        ('complete', '완전 분석'),
        ('lam_only', 'LAM만'),
        ('tspm_only', 'TSPM만'),
        ('cim_only', 'CIM만'),
    ]
    
    job = models.OneToOneField(
        AnalysisJob,
        on_delete=models.CASCADE,
        related_name='result',
        verbose_name='분석 작업'
    )
    
    result_type = models.CharField(
        max_length=20,
        choices=RESULT_TYPES,
        default='complete',
        verbose_name='결과 유형'
    )
    
    result_data = models.JSONField(
        default=dict,
        verbose_name='결과 데이터'
    )
    
    processing_time = models.FloatField(
        default=0.0,
        verbose_name='처리 시간 (초)'
    )
    
    detection_count = models.IntegerField(
        default=0,
        verbose_name='탐지된 요소 수'
    )
    
    confidence_score = models.FloatField(
        default=0.0,
        verbose_name='평균 신뢰도 점수'
    )
    
    file_paths = models.JSONField(
        default=dict,
        verbose_name='생성된 파일 경로들'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    updated_at = models.DateTimeField(
        auto_now=True,
        verbose_name='수정일시'
    )
    
    class Meta:
        verbose_name = '분석 결과'
        verbose_name_plural = '분석 결과들'
        db_table = 'analysis_results'
        indexes = [
            models.Index(fields=['job']),
            models.Index(fields=['result_type']),
            models.Index(fields=['created_at']),
        ]
    
    def __str__(self):
        return f"결과: {self.job.job_name} ({self.result_type})"


class CIMIntegratedResult(models.Model):
    """CIM 통합 결과 모델"""
    
    job = models.ForeignKey(
        AnalysisJob,
        on_delete=models.CASCADE,
        related_name='cim_integrated_results',
        verbose_name='분석 작업'
    )
    
    integrated_data = models.JSONField(
        default=dict,
        verbose_name='통합 데이터'
    )
    
    visualization_paths = models.JSONField(
        default=dict,
        verbose_name='시각화 파일 경로들'
    )
    
    summary_stats = models.JSONField(
        default=dict,
        verbose_name='요약 통계'
    )
    
    export_formats = models.JSONField(
        default=dict,
        verbose_name='내보내기 형식들'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    updated_at = models.DateTimeField(
        auto_now=True,
        verbose_name='수정일시'
    )
    
    class Meta:
        verbose_name = 'CIM 통합 결과'
        verbose_name_plural = 'CIM 통합 결과들'
        db_table = 'cim_integrated_results'
        indexes = [
            models.Index(fields=['job']),
            models.Index(fields=['created_at']),
        ]
    
    def __str__(self):
        return f"CIM 결과: {self.job.job_name}"
