"""
SmartEye Core Processing Engine
Integrates LAM and TSPM for complete document analysis
"""
import os
import asyncio
import time
import uuid
import logging
from typing import List, Dict, Any, Optional, Union
from dataclasses import dataclass, asdict

from .lam import LayoutAnalysisModule
from .tspm import TextFigureProcessingModule
from ..utils.memory import MemoryManager
from ..utils.file_processor import FileProcessor
from ..config.settings import settings

logger = logging.getLogger(__name__)


@dataclass
class ProcessingTask:
    """Processing task information"""
    task_id: str
    status: str  # pending, processing, completed, failed
    created_at: float
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    progress: float = 0.0
    message: str = ""
    error: Optional[str] = None
    result: Optional[Dict[str, Any]] = None


class SmartEyeEngine:
    """Main SmartEye processing engine"""
    
    def __init__(self, openai_api_key: Optional[str] = None, model_choice: str = None):
        self.lam = LayoutAnalysisModule(model_choice=model_choice)
        self.tspm = TextFigureProcessingModule(openai_api_key=openai_api_key)
        self.tasks: Dict[str, ProcessingTask] = {}
        self.initialized = False
        
    async def initialize(self) -> bool:
        """Initialize the engine components"""
        if self.initialized:
            return True
            
        logger.info("Initializing SmartEye engine...")
        
        # Initialize LAM model
        success = await self.lam.initialize_model()
        if not success:
            logger.error("Failed to initialize LAM model")
            return False
            
        self.initialized = True
        logger.info("SmartEye engine initialized successfully")
        return True
    
    def create_task(self) -> str:
        """Create a new processing task"""
        task_id = str(uuid.uuid4())
        task = ProcessingTask(
            task_id=task_id,
            status="pending", 
            created_at=time.time()
        )
        self.tasks[task_id] = task
        return task_id
    
    def get_task_status(self, task_id: str) -> Optional[Dict[str, Any]]:
        """Get task status"""
        task = self.tasks.get(task_id)
        if not task:
            return None
        return asdict(task)
    
    def update_task_progress(self, task_id: str, progress: float, message: str = ""):
        """Update task progress"""
        if task_id in self.tasks:
            self.tasks[task_id].progress = progress
            self.tasks[task_id].message = message
    
    async def process_single_image(self, image_path: str, task_id: str = None) -> Dict[str, Any]:
        """Process a single image"""
        if not self.initialized:
            raise RuntimeError("Engine not initialized")
        
        task_id = task_id or self.create_task()
        task = self.tasks[task_id]
        
        try:
            task.status = "processing"
            task.started_at = time.time()
            
            # Step 1: Validate file
            self.update_task_progress(task_id, 10, "Validating file...")
            valid, message = FileProcessor.validate_file(image_path)
            if not valid:
                raise ValueError(f"File validation failed: {message}")
            
            # Step 2: Preprocess image
            self.update_task_progress(task_id, 20, "Preprocessing image...")
            processed_image_path = FileProcessor.preprocess_image(image_path)
            
            # Step 3: Memory check
            memory_status = MemoryManager.check_memory_status()
            if memory_status['critical']:
                raise RuntimeError("Insufficient memory for processing")
            if memory_status['warning']:
                MemoryManager.cleanup_memory()
            
            # Step 4: Layout analysis (LAM)
            self.update_task_progress(task_id, 40, "Analyzing document layout...")
            layout_result = self.lam.analyze_layout(processed_image_path)
            
            # Step 5: Merge overlapping boxes if requested
            self.update_task_progress(task_id, 50, "Optimizing layout detection...")
            layout_info = self.lam.merge_overlapping_boxes(layout_result['layout_info'])
            
            # Step 6: Content processing (TSPM)
            self.update_task_progress(task_id, 60, "Processing content with OCR and AI...")
            content_result = await self.tspm.process_layout(processed_image_path, layout_info)
            
            # Step 7: Generate visualization
            self.update_task_progress(task_id, 80, "Generating visualization...")
            try:
                visualization = self.lam.visualize_layout(processed_image_path, layout_info)
                # Save visualization
                vis_path = processed_image_path.replace('.', '_analysis.')
                import cv2
                cv2.imwrite(vis_path, visualization)
            except Exception as e:
                logger.warning(f"Visualization failed: {e}")
                vis_path = None
            
            # Step 8: Compile final result
            self.update_task_progress(task_id, 90, "Compiling results...")
            
            file_info = FileProcessor.get_file_info(image_path)
            processing_time = time.time() - task.started_at
            
            result = {
                'task_id': task_id,
                'file_info': file_info,
                'layout_analysis': {
                    'detected_objects_count': len(layout_info),
                    'model_used': layout_result['model_used'],
                    'confidence_threshold': layout_result['confidence_threshold'],
                    'layout_info': layout_info
                },
                'content_analysis': content_result,
                'processing_time': processing_time,
                'visualization_path': vis_path,
                'memory_usage': MemoryManager.get_memory_info()
            }
            
            # Update task
            task.status = "completed"
            task.completed_at = time.time()
            task.progress = 100
            task.message = "Processing completed successfully"
            task.result = result
            
            # Cleanup
            if processed_image_path != image_path:
                FileProcessor.cleanup_files([processed_image_path])
            
            logger.info(f"Single image processing completed in {processing_time:.2f}s")
            return result
            
        except Exception as e:
            # Update task with error
            task.status = "failed"
            task.completed_at = time.time()
            task.error = str(e)
            task.message = f"Processing failed: {e}"
            
            logger.error(f"Single image processing failed: {e}")
            raise
    
    async def process_batch_images(self, image_paths: List[str], task_id: str = None) -> Dict[str, Any]:
        """Process multiple images in batch"""
        if not self.initialized:
            raise RuntimeError("Engine not initialized")
            
        task_id = task_id or self.create_task()
        task = self.tasks[task_id]
        
        try:
            task.status = "processing"
            task.started_at = time.time()
            
            total_images = len(image_paths)
            results = []
            failed_images = []
            
            # Determine batch size based on memory
            batch_size = MemoryManager.get_optimal_batch_size()
            
            for i in range(0, total_images, batch_size):
                batch = image_paths[i:i + batch_size]
                batch_progress = (i / total_images) * 100
                
                self.update_task_progress(
                    task_id, 
                    batch_progress, 
                    f"Processing batch {i//batch_size + 1} ({len(batch)} images)..."
                )
                
                # Process batch concurrently  
                batch_tasks = []
                for img_path in batch:
                    batch_tasks.append(self._process_single_image_for_batch(img_path))
                
                batch_results = await asyncio.gather(*batch_tasks, return_exceptions=True)
                
                for j, result in enumerate(batch_results):
                    if isinstance(result, Exception):
                        failed_images.append({
                            'path': batch[j],
                            'error': str(result)
                        })
                        logger.error(f"Failed to process {batch[j]}: {result}")
                    else:
                        results.append(result)
                
                # Memory cleanup between batches
                MemoryManager.cleanup_memory()
            
            processing_time = time.time() - task.started_at
            
            # Compile batch result
            batch_result = {
                'task_id': task_id,
                'total_images': total_images,
                'successful_images': len(results),
                'failed_images': len(failed_images),
                'batch_size_used': batch_size,
                'processing_time': processing_time,
                'results': results,
                'failures': failed_images,
                'memory_usage': MemoryManager.get_memory_info()
            }
            
            # Update task
            task.status = "completed"
            task.completed_at = time.time()
            task.progress = 100
            task.message = f"Batch processing completed: {len(results)}/{total_images} successful"
            task.result = batch_result
            
            logger.info(f"Batch processing completed: {len(results)}/{total_images} successful in {processing_time:.2f}s")
            return batch_result
            
        except Exception as e:
            task.status = "failed"
            task.completed_at = time.time()
            task.error = str(e)
            task.message = f"Batch processing failed: {e}"
            
            logger.error(f"Batch processing failed: {e}")
            raise
    
    async def process_pdf(self, pdf_path: str, task_id: str = None) -> Dict[str, Any]:
        """Process PDF document"""
        if not self.initialized:
            raise RuntimeError("Engine not initialized")
            
        task_id = task_id or self.create_task()
        task = self.tasks[task_id]
        
        try:
            task.status = "processing"
            task.started_at = time.time()
            
            # Step 1: Convert PDF to images
            self.update_task_progress(task_id, 10, "Converting PDF to images...")
            image_paths = FileProcessor.convert_pdf_to_images(pdf_path)
            
            # Step 2: Process images in batch
            self.update_task_progress(task_id, 20, "Processing PDF pages...")
            batch_result = await self.process_batch_images(image_paths, task_id)
            
            # Step 3: Add PDF-specific metadata
            pdf_info = FileProcessor.get_file_info(pdf_path)
            
            pdf_result = {
                'task_id': task_id,
                'pdf_info': pdf_info,
                'page_count': len(image_paths),
                'batch_result': batch_result,
                'processing_time': batch_result['processing_time']
            }
            
            # Update task result
            task.result = pdf_result
            
            # Cleanup converted images
            FileProcessor.cleanup_files(image_paths)
            
            logger.info(f"PDF processing completed: {len(image_paths)} pages")
            return pdf_result
            
        except Exception as e:
            task.status = "failed"
            task.completed_at = time.time()
            task.error = str(e)
            task.message = f"PDF processing failed: {e}"
            
            logger.error(f"PDF processing failed: {e}")
            raise
    
    async def _process_single_image_for_batch(self, image_path: str) -> Dict[str, Any]:
        """Process single image for batch processing (simplified)"""
        try:
            # Validate and preprocess
            valid, message = FileProcessor.validate_file(image_path)
            if not valid:
                raise ValueError(f"Validation failed: {message}")
            
            processed_path = FileProcessor.preprocess_image(image_path)
            
            # Layout analysis
            layout_result = self.lam.analyze_layout(processed_path)
            layout_info = self.lam.merge_overlapping_boxes(layout_result['layout_info'])
            
            # Content processing
            content_result = await self.tspm.process_layout(processed_path, layout_info)
            
            # Cleanup
            if processed_path != image_path:
                FileProcessor.cleanup_files([processed_path])
            
            return {
                'image_path': image_path,
                'layout_analysis': {
                    'detected_objects_count': len(layout_info),
                    'layout_info': layout_info
                },
                'content_analysis': content_result,
                'file_info': FileProcessor.get_file_info(image_path)
            }
            
        except Exception as e:
            logger.error(f"Failed to process {image_path}: {e}")
            raise