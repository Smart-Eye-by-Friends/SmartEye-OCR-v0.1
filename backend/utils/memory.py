"""
Memory management utilities for SmartEye backend
"""
import os
import psutil
import gc
try:
    import torch
    torch_available = True
except ImportError:
    torch = None
    torch_available = False
import logging
from typing import Dict, Any
from ..config.settings import settings

logger = logging.getLogger(__name__)


class MemoryManager:
    """Memory management and monitoring"""
    
    @staticmethod
    def get_memory_info() -> Dict[str, Any]:
        """Get current memory usage information"""
        memory = psutil.virtual_memory()
        
        info = {
            'total_gb': memory.total / (1024**3),
            'available_gb': memory.available / (1024**3),
            'used_gb': memory.used / (1024**3),
            'percent_used': memory.percent / 100,
            'free_gb': memory.free / (1024**3)
        }
        
        # Add GPU memory if available
        if torch_available and torch.cuda.is_available():
            gpu_memory = torch.cuda.get_device_properties(0).total_memory / (1024**3)
            gpu_allocated = torch.cuda.memory_allocated(0) / (1024**3)
            gpu_cached = torch.cuda.memory_reserved(0) / (1024**3)
            
            info.update({
                'gpu_total_gb': gpu_memory,
                'gpu_allocated_gb': gpu_allocated,
                'gpu_cached_gb': gpu_cached,
                'gpu_percent_used': gpu_allocated / gpu_memory
            })
        
        return info
    
    @staticmethod
    def check_memory_status() -> Dict[str, Any]:
        """Check if memory usage is within safe limits"""
        info = MemoryManager.get_memory_info()
        
        status = {
            'memory_info': info,
            'warning': False,
            'critical': False,
            'message': 'Memory usage normal'
        }
        
        if info['percent_used'] >= settings.memory_critical_threshold:
            status.update({
                'critical': True,
                'message': f"Critical memory usage: {info['percent_used']:.1%}"
            })
        elif info['percent_used'] >= settings.memory_warning_threshold:
            status.update({
                'warning': True,
                'message': f"High memory usage: {info['percent_used']:.1%}"
            })
        
        return status
    
    @staticmethod
    def cleanup_memory():
        """Force memory cleanup"""
        logger.info("Performing memory cleanup...")
        
        # Python garbage collection
        gc.collect()
        
        # PyTorch cleanup
        if torch_available and torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.synchronize()
        
        logger.info("Memory cleanup completed")
    
    @staticmethod
    def get_optimal_batch_size() -> int:
        """Determine optimal batch size based on available memory"""
        memory_info = MemoryManager.get_memory_info()
        
        # Use Colab settings if memory is limited
        if memory_info['available_gb'] < 8:
            return settings.batch_size_colab
        else:
            return settings.batch_size_local
    
    @staticmethod
    def monitor_memory_during_processing(func):
        """Decorator to monitor memory usage during processing"""
        def wrapper(*args, **kwargs):
            # Check memory before processing
            before_status = MemoryManager.check_memory_status()
            if before_status['critical']:
                logger.error(f"Cannot proceed: {before_status['message']}")
                raise RuntimeError("Insufficient memory for processing")
            
            if before_status['warning']:
                logger.warning(before_status['message'])
                MemoryManager.cleanup_memory()
            
            try:
                result = func(*args, **kwargs)
                return result
            finally:
                # Cleanup after processing
                MemoryManager.cleanup_memory()
                
                after_status = MemoryManager.check_memory_status()
                logger.info(f"Memory after processing: {after_status['message']}")
        
        return wrapper