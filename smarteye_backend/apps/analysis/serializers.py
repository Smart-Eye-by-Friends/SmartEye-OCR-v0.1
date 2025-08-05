from rest_framework import serializers
from .models import (
    AnalysisJob, ProcessedImage, LAMLayoutDetection,
    TSPMOCRResult, TSPMImageDescription, CIMOutput, DocumentStructure
)


class AnalysisJobSerializer(serializers.ModelSerializer):
    """분석 작업 시리얼라이저"""
    
    progress_percentage = serializers.ReadOnlyField()
    
    class Meta:
        model = AnalysisJob
        fields = [
            'id', 'job_name', 'description', 'status', 'model_type',
            'processing_mode', 'total_images', 'processed_images', 'failed_images',
            'batch_size', 'enable_resume', 'enable_ocr', 'enable_api',
            'priority', 'started_at', 'completed_at', 'estimated_completion',
            'error_message', 'created_at', 'updated_at', 'progress_percentage'
        ]
        read_only_fields = [
            'id', 'status', 'processed_images', 'failed_images',
            'started_at', 'completed_at', 'estimated_completion',
            'error_message', 'created_at', 'updated_at', 'progress_percentage'
        ]


class ProcessedImageSerializer(serializers.ModelSerializer):
    """처리된 이미지 시리얼라이저"""
    
    class Meta:
        model = ProcessedImage
        fields = [
            'id', 'processed_filename', 'page_number', 'image_width',
            'image_height', 'processing_status', 'processing_time_ms',
            'memory_usage_mb', 'error_message', 'created_at', 'updated_at'
        ]
        read_only_fields = ['id', 'created_at', 'updated_at']


class LAMLayoutDetectionSerializer(serializers.ModelSerializer):
    """LAM 레이아웃 감지 시리얼라이저"""
    
    class Meta:
        model = LAMLayoutDetection
        fields = [
            'id', 'detection_order', 'class_name', 'confidence',
            'bbox_x1', 'bbox_y1', 'bbox_x2', 'bbox_y2',
            'area_pixels', 'width_ratio', 'height_ratio',
            'center_x', 'center_y', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']


class TSPMOCRResultSerializer(serializers.ModelSerializer):
    """TSPM OCR 결과 시리얼라이저"""
    
    class Meta:
        model = TSPMOCRResult
        fields = [
            'id', 'extracted_text', 'processed_text', 'confidence',
            'language', 'processing_method', 'text_length',
            'processing_time_ms', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']


class TSPMImageDescriptionSerializer(serializers.ModelSerializer):
    """TSPM 이미지 설명 시리얼라이저"""
    
    class Meta:
        model = TSPMImageDescription
        fields = [
            'id', 'description_text', 'subject_category', 'description_type',
            'api_model', 'api_cost', 'processing_time_ms', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']


class CIMOutputSerializer(serializers.ModelSerializer):
    """CIM 출력 시리얼라이저"""
    
    class Meta:
        model = CIMOutput
        fields = [
            'id', 'final_text', 'braille_notation', 'reading_order',
            'export_format', 'accessibility_score', 'word_count',
            'estimated_reading_time', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']


class DocumentStructureSerializer(serializers.ModelSerializer):
    """문서 구조 시리얼라이저"""
    
    class Meta:
        model = DocumentStructure
        fields = [
            'id', 'structure_type', 'page_layout', 'content_hierarchy',
            'reading_flow', 'difficulty_level', 'subject_area', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']
