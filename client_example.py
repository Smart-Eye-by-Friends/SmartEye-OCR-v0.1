#!/usr/bin/env python3
"""
SmartEye API Client Example
Simple client to test the SmartEye backend API
"""
import requests
import time
import os
from pathlib import Path

class SmartEyeClient:
    """Simple client for SmartEye API"""
    
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
        self.api_base = f"{base_url}/api/v1"
    
    def health_check(self):
        """Check if the API is healthy"""
        try:
            response = requests.get(f"{self.base_url}/health", timeout=10)
            return response.status_code == 200
        except requests.exceptions.RequestException:
            return False
    
    def get_system_info(self):
        """Get system information"""
        response = requests.get(f"{self.api_base}/system/info")
        response.raise_for_status()
        return response.json()
    
    def analyze_single_image(self, image_path, confidence_threshold=0.25):
        """Analyze a single image"""
        with open(image_path, 'rb') as f:
            files = {'file': f}
            data = {'confidence_threshold': confidence_threshold}
            
            response = requests.post(f"{self.api_base}/analyze/single", 
                                   files=files, data=data)
            response.raise_for_status()
            return response.json()
    
    def analyze_batch_images(self, image_paths, confidence_threshold=0.25):
        """Analyze multiple images"""
        files = []
        try:
            for i, path in enumerate(image_paths):
                files.append(('files', open(path, 'rb')))
            
            data = {'confidence_threshold': confidence_threshold}
            response = requests.post(f"{self.api_base}/analyze/batch",
                                   files=files, data=data)
            response.raise_for_status()
            return response.json()
        finally:
            # Close all file handles
            for _, file_handle in files:
                file_handle.close()
    
    def analyze_pdf(self, pdf_path, confidence_threshold=0.25):
        """Analyze a PDF document"""
        with open(pdf_path, 'rb') as f:
            files = {'file': f}
            data = {'confidence_threshold': confidence_threshold}
            
            response = requests.post(f"{self.api_base}/analyze/pdf",
                                   files=files, data=data)
            response.raise_for_status()
            return response.json()
    
    def get_task_status(self, task_id):
        """Get task status"""
        response = requests.get(f"{self.api_base}/status/{task_id}")
        response.raise_for_status()
        return response.json()
    
    def get_task_results(self, task_id):
        """Get task results"""
        response = requests.get(f"{self.api_base}/results/{task_id}")
        response.raise_for_status()
        return response.json()
    
    def wait_for_completion(self, task_id, timeout=300, poll_interval=2):
        """Wait for task completion"""
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            status = self.get_task_status(task_id)
            task_info = status['task_info']
            
            print(f"Task {task_id}: {task_info['status']} ({task_info['progress']:.1f}%) - {task_info['message']}")
            
            if task_info['status'] in ['completed', 'failed']:
                return task_info['status']
            
            time.sleep(poll_interval)
        
        raise TimeoutError(f"Task {task_id} did not complete within {timeout} seconds")
    
    def download_visualization(self, task_id, output_path=None):
        """Download visualization image"""
        response = requests.get(f"{self.api_base}/visualization/{task_id}")
        response.raise_for_status()
        
        if not output_path:
            output_path = f"visualization_{task_id}.png"
        
        with open(output_path, 'wb') as f:
            f.write(response.content)
        
        return output_path


def main():
    """Example usage"""
    client = SmartEyeClient()
    
    # Check if API is running
    if not client.health_check():
        print("❌ API is not running or not accessible")
        print("Start the server with: python run_server.py")
        return
    
    print("✅ API is running")
    
    # Get system info
    try:
        system_info = client.get_system_info()
        print(f"📊 System: {system_info['app_name']} v{system_info['version']}")
        print(f"🧠 Memory: {system_info['memory_info']['used_gb']:.1f}GB used")
        print(f"🔧 Models loaded: {system_info['health']['models_loaded']}")
    except Exception as e:
        print(f"⚠️ Could not get system info: {e}")
    
    # Example: Analyze image if provided
    import sys
    if len(sys.argv) > 1:
        file_path = sys.argv[1]
        
        if not os.path.exists(file_path):
            print(f"❌ File not found: {file_path}")
            return
        
        print(f"🔍 Analyzing: {file_path}")
        
        try:
            if file_path.lower().endswith('.pdf'):
                result = client.analyze_pdf(file_path)
            else:
                result = client.analyze_single_image(file_path)
            
            task_id = result['task_info']['task_id']
            print(f"📋 Task ID: {task_id}")
            
            # Wait for completion
            status = client.wait_for_completion(task_id)
            
            if status == 'completed':
                results = client.get_task_results(task_id)
                print("✅ Analysis completed!")
                
                # Print summary
                result_data = results['result']
                if 'layout_analysis' in result_data:
                    layout = result_data['layout_analysis']
                    print(f"📄 Detected objects: {layout['detected_objects_count']}")
                
                if 'content_analysis' in result_data:
                    content = result_data['content_analysis']
                    print(f"📝 Content objects: {content['total_objects']}")
                    print(f"   OCR: {content['ocr_objects']}, API: {content['api_objects']}")
                
                print(f"⏱️ Processing time: {result_data['processing_time']:.1f}s")
                
                # Download visualization if available
                try:
                    vis_path = client.download_visualization(task_id)
                    print(f"🎨 Visualization saved: {vis_path}")
                except:
                    print("🎨 No visualization available")
                    
            else:
                print(f"❌ Analysis failed: {status}")
                
        except Exception as e:
            print(f"❌ Error: {e}")
    
    else:
        print("\n💡 Usage: python client_example.py <image_or_pdf_path>")
        print("Example: python client_example.py test_image.jpg")


if __name__ == "__main__":
    main()