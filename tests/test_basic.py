"""
Basic tests for SmartEye backend API
"""
import pytest
import asyncio
import os
from fastapi.testclient import TestClient
from backend.api.server import app
from backend.core.engine import SmartEyeEngine

client = TestClient(app)

def test_health_check():
    """Test basic health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"

def test_system_info():
    """Test system information endpoint"""
    response = client.get("/api/v1/system/info")
    # May return 503 if engine not initialized in test environment
    assert response.status_code in [200, 503]

@pytest.mark.asyncio
async def test_engine_initialization():
    """Test SmartEye engine initialization"""
    engine = SmartEyeEngine()
    # Should not fail even if models aren't downloaded
    assert engine is not None
    assert not engine.initialized

def test_invalid_task_status():
    """Test invalid task ID handling"""
    response = client.get("/api/v1/status/invalid-task-id")
    assert response.status_code in [404, 503]  # 503 if engine not initialized

def test_invalid_task_results():
    """Test invalid task results handling"""
    response = client.get("/api/v1/results/invalid-task-id")
    assert response.status_code in [404, 503]  # 503 if engine not initialized

def test_api_model_validation():
    """Test API model imports"""
    from backend.models.api_models import (
        TaskStatus, ProcessingMode, SingleImageRequest,
        TaskStatusResponse, SystemHealth
    )
    
    # Test enum values
    assert TaskStatus.PENDING == "pending"
    assert ProcessingMode.FAST == "fast"
    
    # Test model creation
    request = SingleImageRequest()
    assert request.confidence_threshold == 0.25
    assert request.merge_boxes == True