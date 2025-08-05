"""
FastAPI server for SmartEye OCR backend
"""
import os
import time
import asyncio
import logging
from typing import List, Optional
from contextlib import asynccontextmanager

from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks, Depends
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
import uvicorn

from ..core.engine import SmartEyeEngine
from ..models.api_models import *
from ..utils.file_processor import FileProcessor
from ..utils.memory import MemoryManager
from ..config.settings import settings, MODEL_CONFIGS

# Setup logging
logging.basicConfig(
    level=getattr(logging, settings.log_level),
    format=settings.log_format
)
logger = logging.getLogger(__name__)

# Global engine instance
engine: Optional[SmartEyeEngine] = None
app_start_time = time.time()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan management"""
    global engine
    
    # Startup
    logger.info("Starting SmartEye API server...")
    
    # Initialize engine
    engine = SmartEyeEngine(
        openai_api_key=settings.openai_api_key,
        model_choice=settings.default_model
    )
    
    success = await engine.initialize()
    if not success:
        logger.error("Failed to initialize SmartEye engine")
        raise RuntimeError("Engine initialization failed")
    
    logger.info("SmartEye API server started successfully")
    
    yield
    
    # Shutdown
    logger.info("Shutting down SmartEye API server...")


# Create FastAPI app
app = FastAPI(
    title=settings.app_name,
    description="Production-ready backend API for SmartEye OCR document analysis",
    version=settings.version,
    lifespan=lifespan
)

# Add middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.add_middleware(
    TrustedHostMiddleware,
    allowed_hosts=["*"]  # Configure for production
)


# Dependency functions
def get_engine() -> SmartEyeEngine:
    """Get the global engine instance"""
    if engine is None:
        raise HTTPException(status_code=503, detail="Engine not initialized")
    return engine


# Health check endpoints
@app.get("/health")
async def health_check():
    """Basic health check"""
    return {"status": "healthy", "timestamp": time.time()}


@app.get("/api/v1/system/info", response_model=SystemInfo)
async def get_system_info(engine: SmartEyeEngine = Depends(get_engine)):
    """Get comprehensive system information"""
    try:
        memory_info = MemoryManager.get_memory_info()
        memory_status = MemoryManager.check_memory_status()
        
        # Model information
        models = []
        for model_name, config in MODEL_CONFIGS.items():
            models.append(ModelInfo(
                model_name=model_name,
                description=config["description"],
                loaded=(model_name == engine.lam.model_choice and engine.initialized),
                device=engine.lam.device if engine.initialized else "unknown",
                repo_id=config["repo_id"],
                filename=config["filename"]
            ))
        
        # System health
        health = SystemHealth(
            status="healthy" if engine.initialized else "degraded",
            memory_status=memory_status["message"],
            gpu_available=bool(torch and torch.cuda.is_available()),
            models_loaded=engine.initialized,
            uptime_seconds=time.time() - app_start_time,
            active_tasks=len([t for t in engine.tasks.values() if t.status == "processing"]),
            completed_tasks=len([t for t in engine.tasks.values() if t.status == "completed"]),
            failed_tasks=len([t for t in engine.tasks.values() if t.status == "failed"])
        )
        
        return SystemInfo(
            app_name=settings.app_name,
            version=settings.version,
            models=models,
            memory_info=MemoryInfo(**memory_info),
            health=health
        )
        
    except Exception as e:
        logger.error(f"Failed to get system info: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# Main processing endpoints
@app.post("/api/v1/analyze/single", response_model=TaskStatusResponse)
async def analyze_single_image(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = BackgroundTasks(),
    request: SingleImageRequest = SingleImageRequest(),
    engine: SmartEyeEngine = Depends(get_engine)
):
    """Analyze a single image"""
    try:
        # Validate file
        if not file.filename:
            raise HTTPException(status_code=400, detail="No file provided")
        
        # Save uploaded file
        file_content = await file.read()
        file_path = FileProcessor.save_uploaded_file(file_content, file.filename)
        
        # Validate file
        valid, message = FileProcessor.validate_file(file_path)
        if not valid:
            FileProcessor.cleanup_files([file_path])
            raise HTTPException(status_code=400, detail=message)
        
        # Create task
        task_id = engine.create_task()
        
        # Start processing in background
        background_tasks.add_task(
            _process_single_image_task,
            engine, file_path, task_id, request
        )
        
        return TaskStatusResponse(
            task_info=TaskInfo(
                task_id=task_id,
                status=TaskStatus.PENDING,
                created_at=time.time(),
                progress=0,
                message="Task queued for processing"
            )
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Single image analysis failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/analyze/batch", response_model=TaskStatusResponse)
async def analyze_batch_images(
    files: List[UploadFile] = File(...),
    background_tasks: BackgroundTasks = BackgroundTasks(),
    request: BatchImageRequest = BatchImageRequest(),
    engine: SmartEyeEngine = Depends(get_engine)
):
    """Analyze multiple images in batch"""
    try:
        if not files:
            raise HTTPException(status_code=400, detail="No files provided")
        
        if len(files) > 50:  # Limit batch size
            raise HTTPException(status_code=400, detail="Too many files (max 50)")
        
        # Save uploaded files
        file_paths = []
        for file in files:
            if not file.filename:
                continue
                
            file_content = await file.read()
            file_path = FileProcessor.save_uploaded_file(file_content, file.filename)
            
            # Validate file
            valid, message = FileProcessor.validate_file(file_path)
            if valid:
                file_paths.append(file_path)
            else:
                logger.warning(f"Skipping invalid file {file.filename}: {message}")
                FileProcessor.cleanup_files([file_path])
        
        if not file_paths:
            raise HTTPException(status_code=400, detail="No valid files provided")
        
        # Create task
        task_id = engine.create_task()
        
        # Start processing in background
        background_tasks.add_task(
            _process_batch_images_task,
            engine, file_paths, task_id, request
        )
        
        return TaskStatusResponse(
            task_info=TaskInfo(
                task_id=task_id,
                status=TaskStatus.PENDING,
                created_at=time.time(),
                progress=0,
                message=f"Batch task queued: {len(file_paths)} files"
            )
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Batch analysis failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/analyze/pdf", response_model=TaskStatusResponse)
async def analyze_pdf(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = BackgroundTasks(),
    request: PDFProcessRequest = PDFProcessRequest(),
    engine: SmartEyeEngine = Depends(get_engine)
):
    """Analyze PDF document"""
    try:
        # Validate file
        if not file.filename or not file.filename.lower().endswith('.pdf'):
            raise HTTPException(status_code=400, detail="PDF file required")
        
        # Save uploaded file
        file_content = await file.read()
        file_path = FileProcessor.save_uploaded_file(file_content, file.filename)
        
        # Validate file
        valid, message = FileProcessor.validate_file(file_path)
        if not valid:
            FileProcessor.cleanup_files([file_path])
            raise HTTPException(status_code=400, detail=message)
        
        # Create task
        task_id = engine.create_task()
        
        # Start processing in background
        background_tasks.add_task(
            _process_pdf_task,
            engine, file_path, task_id, request
        )
        
        return TaskStatusResponse(
            task_info=TaskInfo(
                task_id=task_id,
                status=TaskStatus.PENDING,
                created_at=time.time(),
                progress=0,
                message="PDF task queued for processing"
            )
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"PDF analysis failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/v1/status/{task_id}", response_model=TaskStatusResponse)
async def get_task_status(task_id: str, engine: SmartEyeEngine = Depends(get_engine)):
    """Get task processing status"""
    try:
        task_status = engine.get_task_status(task_id)
        if not task_status:
            raise HTTPException(status_code=404, detail="Task not found")
        
        return TaskStatusResponse(
            task_info=TaskInfo(**task_status),
            result=task_status.get('result')
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get task status: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/v1/results/{task_id}", response_model=TaskResultResponse)
async def get_task_results(task_id: str, engine: SmartEyeEngine = Depends(get_engine)):
    """Get task results"""
    try:
        task_status = engine.get_task_status(task_id)
        if not task_status:
            raise HTTPException(status_code=404, detail="Task not found")
        
        if task_status['status'] == 'processing':
            raise HTTPException(status_code=202, detail="Task still processing")
        
        return TaskResultResponse(
            task_id=task_id,
            status=TaskStatus(task_status['status']),
            result=task_status.get('result'),
            error=task_status.get('error')
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get task results: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/v1/visualization/{task_id}")
async def get_visualization(task_id: str, engine: SmartEyeEngine = Depends(get_engine)):
    """Get visualization image for a task"""
    try:
        task_status = engine.get_task_status(task_id)
        if not task_status:
            raise HTTPException(status_code=404, detail="Task not found")
        
        result = task_status.get('result')
        if not result:
            raise HTTPException(status_code=404, detail="No results available")
        
        vis_path = result.get('visualization_path')
        if not vis_path or not os.path.exists(vis_path):
            raise HTTPException(status_code=404, detail="Visualization not available")
        
        return FileResponse(vis_path, media_type="image/png")
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get visualization: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# Background task functions
async def _process_single_image_task(
    engine: SmartEyeEngine,
    file_path: str,
    task_id: str,
    request: SingleImageRequest
):
    """Background task for single image processing"""
    try:
        result = await engine.process_single_image(file_path, task_id)
        logger.info(f"Single image task {task_id} completed successfully")
    except Exception as e:
        logger.error(f"Single image task {task_id} failed: {e}")
    finally:
        # Cleanup uploaded file
        FileProcessor.cleanup_files([file_path])


async def _process_batch_images_task(
    engine: SmartEyeEngine,
    file_paths: List[str],
    task_id: str,
    request: BatchImageRequest
):
    """Background task for batch image processing"""
    try:
        result = await engine.process_batch_images(file_paths, task_id)
        logger.info(f"Batch task {task_id} completed successfully")
    except Exception as e:
        logger.error(f"Batch task {task_id} failed: {e}")
    finally:
        # Cleanup uploaded files
        FileProcessor.cleanup_files(file_paths)


async def _process_pdf_task(
    engine: SmartEyeEngine,
    file_path: str,
    task_id: str,
    request: PDFProcessRequest
):
    """Background task for PDF processing"""
    try:
        result = await engine.process_pdf(file_path, task_id)
        logger.info(f"PDF task {task_id} completed successfully")
    except Exception as e:
        logger.error(f"PDF task {task_id} failed: {e}")
    finally:
        # Cleanup uploaded file
        FileProcessor.cleanup_files([file_path])


# Error handlers
@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    """Handle HTTP exceptions"""
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorResponse(
            error=exc.detail,
            timestamp=time.time()
        ).dict()
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    """Handle general exceptions"""
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            error="Internal server error",
            detail=str(exc),
            timestamp=time.time()
        ).dict()
    )


def main():
    """Run the server"""
    uvicorn.run(
        "backend.api.server:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level=settings.log_level.lower()
    )


if __name__ == "__main__":
    main()