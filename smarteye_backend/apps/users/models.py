from django.db import models
from django.contrib.auth.models import AbstractUser


class User(AbstractUser):
    """커스텀 사용자 모델"""
    
    USER_TYPES = [
        ('student', '학생'),
        ('teacher', '교사'),
        ('admin', '관리자'),
    ]
    
    SUBSCRIPTION_TIERS = [
        ('free', '무료'),
        ('basic', '기본'),
        ('premium', '프리미엄'),
    ]
    
    user_type = models.CharField(
        max_length=20,
        choices=USER_TYPES,
        default='student',
        verbose_name='사용자 유형'
    )
    
    subscription_tier = models.CharField(
        max_length=20,
        choices=SUBSCRIPTION_TIERS,
        default='free',
        verbose_name='구독 등급'
    )
    
    api_quota_used = models.IntegerField(
        default=0,
        verbose_name='사용된 API 할당량'
    )
    
    api_quota_limit = models.IntegerField(
        default=100,
        verbose_name='API 할당량 제한'
    )
    
    phone_number = models.CharField(
        max_length=20,
        blank=True,
        verbose_name='전화번호'
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
        verbose_name = '사용자'
        verbose_name_plural = '사용자들'
        db_table = 'users'
    
    def __str__(self):
        return f"{self.username} ({self.get_user_type_display()})"
    
    @property
    def api_quota_percentage(self):
        """API 할당량 사용률 반환"""
        if self.api_quota_limit == 0:
            return 0
        return (self.api_quota_used / self.api_quota_limit) * 100
    
    def can_use_api(self):
        """API 사용 가능 여부 확인"""
        return self.api_quota_used < self.api_quota_limit
    
    def increment_api_usage(self, count=1):
        """API 사용량 증가"""
        self.api_quota_used += count
        self.save(update_fields=['api_quota_used'])


class UserSession(models.Model):
    """사용자 세션 관리"""
    
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='sessions',
        verbose_name='사용자'
    )
    
    session_token = models.CharField(
        max_length=255,
        unique=True,
        verbose_name='세션 토큰'
    )
    
    expires_at = models.DateTimeField(
        verbose_name='만료일시'
    )
    
    ip_address = models.GenericIPAddressField(
        null=True,
        blank=True,
        verbose_name='IP 주소'
    )
    
    user_agent = models.TextField(
        blank=True,
        verbose_name='사용자 에이전트'
    )
    
    created_at = models.DateTimeField(
        auto_now_add=True,
        verbose_name='생성일시'
    )
    
    class Meta:
        verbose_name = '사용자 세션'
        verbose_name_plural = '사용자 세션들'
        db_table = 'user_sessions'
        indexes = [
            models.Index(fields=['user', 'expires_at']),
            models.Index(fields=['session_token']),
        ]
    
    def __str__(self):
        return f"{self.user.username} - {self.session_token[:10]}..."


class UserPreference(models.Model):
    """사용자별 설정"""
    
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='preferences',
        verbose_name='사용자'
    )
    
    preference_key = models.CharField(
        max_length=100,
        verbose_name='설정 키'
    )
    
    preference_value = models.TextField(
        verbose_name='설정 값'
    )
    
    updated_at = models.DateTimeField(
        auto_now=True,
        verbose_name='수정일시'
    )
    
    class Meta:
        verbose_name = '사용자 설정'
        verbose_name_plural = '사용자 설정들'
        db_table = 'user_preferences'
        unique_together = ['user', 'preference_key']
        indexes = [
            models.Index(fields=['user', 'preference_key']),
        ]
    
    def __str__(self):
        return f"{self.user.username} - {self.preference_key}"
