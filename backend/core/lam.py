"""
Layout Analysis Module (LAM)
DocLayout-YOLO based document layout analysis
"""
import os
import cv2
import torch
import numpy as np
from typing import List, Dict, Any, Tuple, Optional
from PIL import Image
import logging
from huggingface_hub import hf_hub_download

from ..config.settings import settings, MODEL_CONFIGS

logger = logging.getLogger(__name__)


class LayoutAnalysisModule:
    """Layout Analysis Module using DocLayout-YOLO"""
    
    def __init__(self, model_choice: str = None, device: str = None):
        self.model_choice = model_choice or settings.default_model
        self.device = self._setup_device(device)
        self.model = None
        self.class_names = []
        
    def _setup_device(self, device: Optional[str] = None) -> str:
        """Setup computing device"""
        if device:
            return device
        elif settings.device == "auto":
            return 'cuda:0' if torch.cuda.is_available() else 'cpu'
        else:
            return settings.device
    
    async def initialize_model(self) -> bool:
        """Download and initialize the DocLayout-YOLO model"""
        try:
            if self.model_choice not in MODEL_CONFIGS:
                raise ValueError(f"Unsupported model: {self.model_choice}")
                
            model_config = MODEL_CONFIGS[self.model_choice]
            
            logger.info(f"Downloading model: {model_config['filename']}")
            
            # Download model from HuggingFace
            model_path = hf_hub_download(
                repo_id=model_config["repo_id"],
                filename=model_config["filename"]
            )
            
            # Import and load DocLayout-YOLO
            try:
                from doclayout_yolo import YOLOv10
                self.model = YOLOv10(model_path, task='predict')
                self.model.to(self.device)
                
                logger.info(f"Model loaded successfully on {self.device}")
                return True
                
            except ImportError:
                logger.error("DocLayout-YOLO not installed. Please install doclayout-yolo package.")
                return False
                
        except Exception as e:
            logger.error(f"Failed to initialize model: {e}")
            return False
    
    def analyze_layout(self, image_path: str, confidence_threshold: float = None) -> Dict[str, Any]:
        """Analyze document layout from image"""
        if self.model is None:
            raise RuntimeError("Model not initialized. Call initialize_model() first.")
            
        confidence_threshold = confidence_threshold or settings.confidence_threshold
        
        try:
            # Load and validate image
            img = cv2.imread(image_path)
            if img is None:
                raise ValueError(f"Could not load image: {image_path}")
                
            height, width = img.shape[:2]
            
            # Run inference
            logger.info(f"Running layout analysis on {os.path.basename(image_path)}")
            results = self.model.predict(
                source=image_path,
                conf=confidence_threshold,
                device=self.device,
                verbose=False
            )
            
            # Process results
            layout_info = []
            detected_objects = 0
            
            for result in results:
                boxes = result.boxes
                if boxes is not None:
                    for i in range(len(boxes)):
                        # Extract box coordinates
                        xyxy = boxes.xyxy[i].cpu().numpy().astype(int)
                        x1, y1, x2, y2 = xyxy
                        
                        # Get confidence and class
                        conf = float(boxes.conf[i].cpu().numpy())
                        cls_id = int(boxes.cls[i].cpu().numpy())
                        class_name = result.names[cls_id]
                        
                        # Validate coordinates
                        x1, y1 = max(0, x1), max(0, y1)
                        x2, y2 = min(width, x2), min(height, y2)
                        
                        if x2 > x1 and y2 > y1:  # Valid box
                            layout_info.append({
                                'id': len(layout_info) + 1,
                                'class_name': class_name,
                                'confidence': conf,
                                'box': [x1, y1, x2, y2],
                                'coordinates': [x1, y1, x2, y2],
                                'area': (x2 - x1) * (y2 - y1)
                            })
                            detected_objects += 1
            
            # Sort by area (largest first) for better processing order
            layout_info.sort(key=lambda x: x['area'], reverse=True)
            
            return {
                'layout_info': layout_info,
                'image_shape': [height, width],
                'detected_objects_count': detected_objects,
                'model_used': self.model_choice,
                'confidence_threshold': confidence_threshold
            }
            
        except Exception as e:
            logger.error(f"Layout analysis failed: {e}")
            raise
    
    def merge_overlapping_boxes(self, layout_info: List[Dict], iou_threshold: float = 0.3) -> List[Dict]:
        """Merge overlapping bounding boxes"""
        if not layout_info:
            return layout_info
            
        logger.info("Merging overlapping boxes...")
        
        def calculate_iou(box1: List[int], box2: List[int]) -> float:
            """Calculate Intersection over Union"""
            x1_1, y1_1, x2_1, y2_1 = box1
            x1_2, y1_2, x2_2, y2_2 = box2
            
            # Calculate intersection
            x1_i = max(x1_1, x1_2)
            y1_i = max(y1_1, y1_2)
            x2_i = min(x2_1, x2_2)
            y2_i = min(y2_1, y2_2)
            
            if x2_i <= x1_i or y2_i <= y1_i:
                return 0.0
                
            intersection = (x2_i - x1_i) * (y2_i - y1_i)
            area1 = (x2_1 - x1_1) * (y2_1 - y1_1)
            area2 = (x2_2 - x1_2) * (y2_2 - y1_2)
            union = area1 + area2 - intersection
            
            return intersection / union if union > 0 else 0.0
        
        merged_layout = []
        used_indices = set()
        
        for i, layout1 in enumerate(layout_info):
            if i in used_indices:
                continue
                
            current_box = layout1.copy()
            merged_with = [i]
            
            for j, layout2 in enumerate(layout_info):
                if j <= i or j in used_indices:
                    continue
                    
                iou = calculate_iou(layout1['box'], layout2['box'])
                
                if iou > iou_threshold and layout1['class_name'] == layout2['class_name']:
                    # Merge boxes
                    x1 = min(current_box['box'][0], layout2['box'][0])
                    y1 = min(current_box['box'][1], layout2['box'][1])
                    x2 = max(current_box['box'][2], layout2['box'][2])
                    y2 = max(current_box['box'][3], layout2['box'][3])
                    
                    current_box['box'] = [x1, y1, x2, y2]
                    current_box['coordinates'] = [x1, y1, x2, y2]
                    current_box['area'] = (x2 - x1) * (y2 - y1)
                    current_box['confidence'] = max(current_box['confidence'], layout2['confidence'])
                    
                    merged_with.append(j)
                    used_indices.add(j)
            
            used_indices.update(merged_with)
            merged_layout.append(current_box)
        
        logger.info(f"Merged {len(layout_info)} boxes into {len(merged_layout)} boxes")
        return merged_layout
    
    def visualize_layout(self, image_path: str, layout_info: List[Dict], alpha: float = 0.3) -> np.ndarray:
        """Visualize layout analysis results"""
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Could not load image: {image_path}")
            
        overlay = img.copy()
        
        # Generate colors for each class
        unique_classes = list(set(layout['class_name'] for layout in layout_info))
        import colorsys
        colors = {}
        for i, cls_name in enumerate(unique_classes):
            h = i / max(1, len(unique_classes))
            r, g, b = colorsys.hsv_to_rgb(h, 0.8, 0.9)
            colors[cls_name] = (int(b * 255), int(g * 255), int(r * 255))
        
        # Draw boxes
        for layout in layout_info:
            x1, y1, x2, y2 = layout['box']
            color = colors[layout['class_name']]
            
            # Fill box
            cv2.rectangle(overlay, (x1, y1), (x2, y2), color, -1)
            
            # Draw border
            cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
            
            # Add label
            label = f"{layout['class_name']} ({layout['confidence']:.2f})"
            label_size = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)[0]
            
            cv2.rectangle(img, (x1, y1 - label_size[1] - 10), 
                         (x1 + label_size[0], y1), color, -1)
            cv2.putText(img, label, (x1, y1 - 5), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        
        # Apply overlay
        result = cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0)
        return result