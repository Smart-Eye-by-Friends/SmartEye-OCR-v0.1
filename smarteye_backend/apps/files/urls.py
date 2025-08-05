from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import SourceFileViewSet, FileUploadSessionViewSet

router = DefaultRouter()
router.register(r'', SourceFileViewSet, basename='source-file')
router.register(r'sessions', FileUploadSessionViewSet, basename='upload-session')

urlpatterns = [
    path('', include(router.urls)),
]
