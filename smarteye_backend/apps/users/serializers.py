from rest_framework import serializers
from django.contrib.auth import get_user_model
from .models import UserPreference

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    """사용자 시리얼라이저"""
    
    quota_percentage = serializers.ReadOnlyField()
    can_use_api = serializers.ReadOnlyField()
    
    class Meta:
        model = User
        fields = [
            'id', 'username', 'email', 'first_name', 'last_name',
            'user_type', 'subscription_tier', 'api_quota_used',
            'api_quota_limit', 'quota_percentage', 'can_use_api',
            'phone_number', 'date_joined', 'created_at', 'updated_at'
        ]
        read_only_fields = [
            'id', 'date_joined', 'created_at', 'updated_at',
            'api_quota_used', 'quota_percentage', 'can_use_api'
        ]


class UserPreferenceSerializer(serializers.ModelSerializer):
    """사용자 설정 시리얼라이저"""
    
    class Meta:
        model = UserPreference
        fields = ['id', 'preference_key', 'preference_value', 'updated_at']
        read_only_fields = ['id', 'updated_at']
