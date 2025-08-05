"""
File processing utilities for SmartEye backend
"""
import os
import shutil
import hashlib
import magic
from typing import List, Optional, Tuple
from pathlib import Path
import logging
from PIL import Image
import PyMuPDF as fitz

from ..config.settings import settings

logger = logging.getLogger(__name__)


class FileProcessor:
    """File processing and validation utilities"""
    
    @staticmethod
    def validate_file(file_path: str) -> Tuple[bool, str]:
        """Validate uploaded file"""
        try:
            # Check if file exists
            if not os.path.exists(file_path):
                return False, "File does not exist"
            
            # Check file size
            file_size = os.path.getsize(file_path)
            if file_size > settings.max_file_size:
                return False, f"File too large: {file_size / (1024*1024):.1f}MB > {settings.max_file_size / (1024*1024):.1f}MB"
            
            # Check file extension
            file_ext = Path(file_path).suffix.lower()
            if file_ext not in settings.allowed_extensions:
                return False, f"Unsupported file type: {file_ext}"
            
            # Check MIME type
            mime_type = magic.from_file(file_path, mime=True)
            allowed_mimes = {
                '.jpg': 'image/jpeg',
                '.jpeg': 'image/jpeg', 
                '.png': 'image/png',
                '.pdf': 'application/pdf'
            }
            
            expected_mime = allowed_mimes.get(file_ext)
            if expected_mime and not mime_type.startswith(expected_mime.split('/')[0]):
                return False, f"File content doesn't match extension: {mime_type}"
            
            return True, "File validation passed"
            
        except Exception as e:
            return False, f"Validation error: {e}"
    
    @staticmethod
    def generate_file_hash(file_path: str) -> str:
        """Generate MD5 hash for file"""
        hash_md5 = hashlib.md5()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    
    @staticmethod
    def ensure_upload_directory() -> str:
        """Ensure upload directory exists"""
        os.makedirs(settings.upload_dir, exist_ok=True)
        return settings.upload_dir
    
    @staticmethod
    def save_uploaded_file(file_content: bytes, filename: str) -> str:
        """Save uploaded file to upload directory"""
        upload_dir = FileProcessor.ensure_upload_directory()
        
        # Generate unique filename
        file_hash = hashlib.md5(file_content).hexdigest()[:8]
        file_ext = Path(filename).suffix.lower()
        unique_filename = f"{file_hash}_{filename}"
        
        file_path = os.path.join(upload_dir, unique_filename)
        
        with open(file_path, 'wb') as f:
            f.write(file_content)
        
        return file_path
    
    @staticmethod
    def convert_pdf_to_images(pdf_path: str) -> List[str]:
        """Convert PDF pages to images"""
        logger.info(f"Converting PDF to images: {pdf_path}")
        
        try:
            # Open PDF
            pdf_document = fitz.open(pdf_path)
            image_paths = []
            
            upload_dir = FileProcessor.ensure_upload_directory()
            base_name = Path(pdf_path).stem
            
            for page_num in range(pdf_document.page_count):
                page = pdf_document[page_num]
                
                # Convert page to image
                mat = fitz.Matrix(2.0, 2.0)  # 2x scale for better quality
                pix = page.get_pixmap(matrix=mat)
                
                # Save as PNG
                img_filename = f"{base_name}_page_{page_num + 1}.png"
                img_path = os.path.join(upload_dir, img_filename)
                pix.save(img_path)
                
                image_paths.append(img_path)
                logger.debug(f"Converted page {page_num + 1} to {img_path}")
            
            pdf_document.close()
            logger.info(f"PDF conversion completed: {len(image_paths)} pages")
            
            return image_paths
            
        except Exception as e:
            logger.error(f"PDF conversion failed: {e}")
            raise
    
    @staticmethod
    def preprocess_image(image_path: str, max_size: Tuple[int, int] = (2048, 2048)) -> str:
        """Preprocess image for optimal analysis"""
        try:
            with Image.open(image_path) as img:
                # Convert to RGB if necessary
                if img.mode != 'RGB':
                    img = img.convert('RGB')
                
                # Resize if too large
                if img.size[0] > max_size[0] or img.size[1] > max_size[1]:
                    img.thumbnail(max_size, Image.Resampling.LANCZOS)
                    
                    # Save preprocessed image
                    preprocessed_path = image_path.replace('.', '_preprocessed.')
                    img.save(preprocessed_path, 'JPEG', quality=90)
                    
                    logger.info(f"Image resized from {Image.open(image_path).size} to {img.size}")
                    return preprocessed_path
                
                return image_path
                
        except Exception as e:
            logger.error(f"Image preprocessing failed: {e}")
            return image_path
    
    @staticmethod
    def cleanup_files(file_paths: List[str]):
        """Clean up temporary files"""
        for file_path in file_paths:
            try:
                if os.path.exists(file_path):
                    os.remove(file_path)
                    logger.debug(f"Cleaned up file: {file_path}")
            except Exception as e:
                logger.warning(f"Failed to cleanup file {file_path}: {e}")
    
    @staticmethod
    def get_file_info(file_path: str) -> dict:
        """Get comprehensive file information"""
        try:
            stat = os.stat(file_path)
            
            info = {
                'path': file_path,
                'filename': os.path.basename(file_path),
                'size_bytes': stat.st_size,
                'size_mb': stat.st_size / (1024 * 1024),
                'mime_type': magic.from_file(file_path, mime=True),
                'extension': Path(file_path).suffix.lower()
            }
            
            # Add image-specific info
            if info['extension'] in ['.jpg', '.jpeg', '.png']:
                try:
                    with Image.open(file_path) as img:
                        info.update({
                            'width': img.width,
                            'height': img.height,
                            'mode': img.mode,
                            'format': img.format
                        })
                except Exception:
                    pass
            
            # Add PDF-specific info
            elif info['extension'] == '.pdf':
                try:
                    pdf_doc = fitz.open(file_path)
                    info.update({
                        'page_count': pdf_doc.page_count,
                        'title': pdf_doc.metadata.get('title', ''),
                        'author': pdf_doc.metadata.get('author', '')
                    })
                    pdf_doc.close()
                except Exception:
                    pass
            
            return info
            
        except Exception as e:
            logger.error(f"Failed to get file info for {file_path}: {e}")
            return {'path': file_path, 'error': str(e)}