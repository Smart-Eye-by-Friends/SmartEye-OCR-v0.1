#!/usr/bin/env python3
"""
SmartEye Backend Server Entry Point
"""
import os
import sys
import argparse
import logging

# Add the project root to Python path
project_root = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, project_root)

from backend.api.server import main
from backend.config.settings import settings

def setup_logging():
    """Setup logging configuration"""
    logging.basicConfig(
        level=getattr(logging, settings.log_level),
        format=settings.log_format,
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler('smarteye.log') if not settings.debug else logging.StreamHandler()
        ]
    )

def parse_args():
    """Parse command line arguments"""
    parser = argparse.ArgumentParser(description="SmartEye OCR API Server")
    parser.add_argument("--host", default=settings.host, help="Host to bind to")
    parser.add_argument("--port", type=int, default=settings.port, help="Port to bind to") 
    parser.add_argument("--debug", action="store_true", help="Enable debug mode")
    parser.add_argument("--log-level", default=settings.log_level, 
                       choices=["DEBUG", "INFO", "WARNING", "ERROR"],
                       help="Logging level")
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_args()
    
    # Update settings with CLI args
    settings.host = args.host
    settings.port = args.port
    settings.debug = args.debug
    settings.log_level = args.log_level
    
    # Setup logging
    setup_logging()
    
    logger = logging.getLogger(__name__)
    logger.info(f"Starting SmartEye API Server on {args.host}:{args.port}")
    
    # Run the server
    main()