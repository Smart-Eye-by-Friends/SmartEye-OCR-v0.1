from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import AnalysisJobViewSet, ProcessedImageViewSet

router = DefaultRouter()
router.register(r'jobs', AnalysisJobViewSet, basename='analysis-job')
router.register(r'images', ProcessedImageViewSet, basename='processed-image')

urlpatterns = [
    path('', include(router.urls)),
]
