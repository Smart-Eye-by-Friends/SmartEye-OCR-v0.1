from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import UserViewSet, UserPreferenceViewSet

router = DefaultRouter()
router.register(r'', UserViewSet, basename='user')
router.register(r'preferences', UserPreferenceViewSet, basename='user-preference')

urlpatterns = [
    path('', include(router.urls)),
]
