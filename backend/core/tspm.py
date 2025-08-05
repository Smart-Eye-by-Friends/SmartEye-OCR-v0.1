"""
Text/Figure Processing Module (TSPM)
OCR and OpenAI Vision API integration for content extraction
"""
import cv2
import base64
import io
import asyncio
import logging
from typing import List, Dict, Any, Optional
from PIL import Image
import pytesseract
import openai
from openai import AsyncOpenAI

from ..config.settings import settings, OCR_TARGET_CLASSES, API_TARGET_CLASSES

logger = logging.getLogger(__name__)


class TextFigureProcessingModule:
    """Text and Figure Processing Module for content extraction"""
    
    def __init__(self, openai_api_key: Optional[str] = None):
        self.openai_api_key = openai_api_key or settings.openai_api_key
        self.openai_client = None
        
        if self.openai_api_key:
            self.openai_client = AsyncOpenAI(api_key=self.openai_api_key)
        
        # OCR configuration
        self.tesseract_config = settings.tesseract_config
        self.ocr_languages = "+".join(settings.ocr_languages)
        
    async def process_text_regions(self, image_path: str, layout_info: List[Dict]) -> List[Dict]:
        """Process text regions using OCR"""
        logger.info(f"Processing {len(layout_info)} text regions with OCR")
        
        # Load image
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Could not load image: {image_path}")
        
        ocr_results = []
        
        for layout in layout_info:
            if layout['class_name'] not in OCR_TARGET_CLASSES:
                continue
                
            try:
                # Extract region
                x1, y1, x2, y2 = layout['box']
                region = img[y1:y2, x1:x2]
                
                if region.size == 0:
                    continue
                
                # Convert to PIL Image for tesseract
                region_pil = Image.fromarray(cv2.cvtColor(region, cv2.COLOR_BGR2RGB))
                
                # Perform OCR
                extracted_text = pytesseract.image_to_string(
                    region_pil, 
                    lang=self.ocr_languages,
                    config=self.tesseract_config
                ).strip()
                
                if extracted_text:
                    ocr_results.append({
                        'id': layout['id'],
                        'class_name': layout['class_name'],
                        'coordinates': layout['coordinates'],
                        'confidence': layout['confidence'],
                        'text': extracted_text,
                        'method': 'OCR'
                    })
                    
                    logger.debug(f"OCR extracted from {layout['class_name']}: {extracted_text[:50]}...")
                    
            except Exception as e:
                logger.error(f"OCR failed for region {layout['id']}: {e}")
                continue
        
        logger.info(f"OCR completed: {len(ocr_results)} text regions processed")
        return ocr_results
    
    async def process_figure_regions(self, image_path: str, layout_info: List[Dict]) -> List[Dict]:
        """Process figure/table regions using OpenAI Vision API"""
        if not self.openai_client:
            logger.warning("OpenAI API key not provided, skipping figure analysis")
            return []
            
        logger.info(f"Processing {len(layout_info)} figure regions with OpenAI Vision API")
        
        # Load image
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Could not load image: {image_path}")
        
        api_results = []
        
        # Define prompts for different object types
        prompts = {
            'figure': "이 그림이나 도표의 내용을 상세히 설명해 주세요. 주요 요소와 시각적 정보를 포함해서 설명해주세요.",
            'table': "이 표의 내용을 분석하고 구조와 데이터를 설명해 주세요. 행과 열의 정보를 포함해서 설명해주세요.",
            'figure caption': "이 그림 설명(캡션)의 내용을 분석해 주세요.",
            'table caption': "이 표 설명(캡션)의 내용을 분석해 주세요.",
            'table footnote': "이 표 각주의 내용을 분석해 주세요."
        }
        
        # Process regions that should use API
        api_regions = [layout for layout in layout_info 
                      if layout['class_name'] in API_TARGET_CLASSES]
        
        # Process in batches to avoid rate limits
        batch_size = 3
        for i in range(0, len(api_regions), batch_size):
            batch = api_regions[i:i+batch_size]
            tasks = []
            
            for layout in batch:
                task = self._process_single_region_with_api(img, layout, prompts)
                tasks.append(task)
            
            batch_results = await asyncio.gather(*tasks, return_exceptions=True)
            
            for result in batch_results:
                if isinstance(result, Exception):
                    logger.error(f"API processing failed: {result}")
                elif result:
                    api_results.append(result)
            
            # Rate limiting delay
            if i + batch_size < len(api_regions):
                await asyncio.sleep(1)
        
        logger.info(f"OpenAI Vision API completed: {len(api_results)} regions processed")
        return api_results
    
    async def _process_single_region_with_api(self, img: Any, layout: Dict, prompts: Dict) -> Optional[Dict]:
        """Process a single region with OpenAI Vision API"""
        try:
            # Extract region
            x1, y1, x2, y2 = layout['box']
            region = img[y1:y2, x1:x2]
            
            if region.size == 0:
                return None
            
            # Convert to PIL and encode as base64
            region_pil = Image.fromarray(cv2.cvtColor(region, cv2.COLOR_BGR2RGB))
            buffered = io.BytesIO()
            region_pil.save(buffered, format="PNG")
            img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
            
            # Get appropriate prompt
            class_name = layout['class_name'].lower()
            prompt = prompts.get(class_name, f"이 {layout['class_name']}의 내용을 간단히 설명해 주세요.")
            
            # Make API call
            response = await self.openai_client.chat.completions.create(
                model="gpt-4-turbo",
                messages=[{
                    "role": "user", 
                    "content": [
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{img_base64}"}}
                    ]
                }],
                max_tokens=300
            )
            
            description = response.choices[0].message.content.strip()
            
            return {
                'id': layout['id'],
                'class_name': layout['class_name'],
                'coordinates': layout['coordinates'],
                'confidence': layout['confidence'],
                'description': description,
                'method': 'OpenAI_Vision'
            }
            
        except Exception as e:
            logger.error(f"API processing failed for region {layout['id']}: {e}")
            return None
    
    def integrate_results(self, ocr_results: List[Dict], api_results: List[Dict]) -> Dict[str, Any]:
        """Integrate OCR and API results"""
        all_results = []
        
        # Add OCR results
        for result in ocr_results:
            all_results.append({
                'id': result['id'],
                'class_name': result['class_name'],
                'coordinates': result['coordinates'],
                'confidence': result['confidence'],
                'content': result['text'],
                'content_type': 'text',
                'method': result['method']
            })
        
        # Add API results  
        for result in api_results:
            all_results.append({
                'id': result['id'],
                'class_name': result['class_name'],
                'coordinates': result['coordinates'],
                'confidence': result['confidence'],
                'content': result['description'],
                'content_type': 'description',
                'method': result['method']
            })
        
        # Sort by ID for consistent ordering
        all_results.sort(key=lambda x: x['id'])
        
        return {
            'total_objects': len(all_results),
            'ocr_objects': len(ocr_results),
            'api_objects': len(api_results),
            'results': all_results
        }
    
    async def process_layout(self, image_path: str, layout_info: List[Dict]) -> Dict[str, Any]:
        """Process all layout regions with appropriate methods"""
        logger.info(f"Starting TSPM processing for {len(layout_info)} regions")
        
        # Separate regions by processing method
        text_regions = [layout for layout in layout_info if layout['class_name'] in OCR_TARGET_CLASSES]
        figure_regions = [layout for layout in layout_info if layout['class_name'] in API_TARGET_CLASSES]
        
        logger.info(f"Text regions (OCR): {len(text_regions)}")
        logger.info(f"Figure regions (API): {len(figure_regions)}")
        
        # Process concurrently
        ocr_task = self.process_text_regions(image_path, text_regions)
        api_task = self.process_figure_regions(image_path, figure_regions)
        
        ocr_results, api_results = await asyncio.gather(ocr_task, api_task)
        
        # Integrate results
        integrated_results = self.integrate_results(ocr_results, api_results)
        
        logger.info(f"TSPM processing completed: {integrated_results['total_objects']} objects processed")
        
        return integrated_results