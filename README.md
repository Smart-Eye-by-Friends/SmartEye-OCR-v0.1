# SmartEye OCR Backend API Server

Production-ready backend API server for SmartEye OCR document analysis system, built on the existing Jupyter notebook functionality.

## 🎯 Overview

This backend server transforms the SmartEye Jupyter notebook into a scalable, production-ready API service that provides:

- **LAM (Layout Analysis Module)**: DocLayout-YOLO based document layout analysis
- **TSPM (Text/Figure Processing Module)**: OCR and OpenAI Vision API integration  
- **Memory Management**: Efficient handling of large files and batch processing
- **Async Processing**: Non-blocking API with task queuing
- **Multi-format Support**: Images (JPG, PNG) and PDF documents

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   FastAPI       │    │   SmartEye      │    │   DocLayout     │
│   Server        │◄──►│   Engine        │◄──►│   YOLO          │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       ▼                       │
         │              ┌─────────────────┐              │
         │              │   TSPM Module   │              │
         │              │                 │              │
         │              └─────────────────┘              │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   File          │    │   Tesseract     │    │   OpenAI        │
│   Processor     │    │   OCR           │    │   Vision API    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Python 3.11+
- Tesseract OCR
- CUDA (optional, for GPU acceleration)
- OpenAI API key (for figure/table analysis)

### Installation

1. **Clone the repository**
```bash
git clone <repository-url>
cd SmartEye-OCR-v0.1
```

2. **Install dependencies**
```bash
pip install -r requirements.txt
```

3. **Configure environment**
```bash
cp .env.example .env
# Edit .env with your settings, especially SMARTEYE_OPENAI_API_KEY
```

4. **Run the server**
```bash
python run_server.py
```

The API will be available at `http://localhost:8000`

### Docker Deployment

1. **Using Docker Compose (Recommended)**
```bash
docker-compose up -d
```

2. **Using Docker only**
```bash
docker build -t smarteye-api .
docker run -p 8000:8000 -e SMARTEYE_OPENAI_API_KEY="your-key" smarteye-api
```

## 📚 API Documentation

### Base URL
```
http://localhost:8000/api/v1
```

### Authentication
Currently no authentication required. Add API keys in production.

### Endpoints

#### 1. Single Image Analysis
```http
POST /api/v1/analyze/single
Content-Type: multipart/form-data
```
**Parameters:**
- `file`: Image file (JPG, PNG)
- `confidence_threshold`: Detection confidence (0.1-0.9, default: 0.25)
- `merge_boxes`: Merge overlapping boxes (default: true)
- `processing_mode`: fast|accurate|complete (default: complete)

**Response:**
```json
{
  "task_info": {
    "task_id": "uuid",
    "status": "pending|processing|completed|failed",
    "progress": 0-100,
    "message": "Status message"
  }
}
```

#### 2. Batch Image Analysis
```http
POST /api/v1/analyze/batch
Content-Type: multipart/form-data
```
**Parameters:**
- `files`: Multiple image files (max 50)
- `confidence_threshold`: Detection confidence (default: 0.25)
- `processing_mode`: fast|accurate|complete (default: fast)

#### 3. PDF Document Analysis
```http
POST /api/v1/analyze/pdf
Content-Type: multipart/form-data
```
**Parameters:**
- `file`: PDF file
- `confidence_threshold`: Detection confidence (default: 0.25)
- `max_pages`: Maximum pages to process (default: all)

#### 4. Task Status
```http
GET /api/v1/status/{task_id}
```
Get current processing status and progress.

#### 5. Task Results
```http
GET /api/v1/results/{task_id}
```
Get final processing results (only when completed).

#### 6. Visualization
```http
GET /api/v1/visualization/{task_id}
```
Get analysis visualization image.

#### 7. System Information
```http
GET /api/v1/system/info
```
Get system health, memory usage, and model information.

## 📊 Supported Analysis Classes

### Layout Classes (DocLayout-YOLO)
- `title` - Document titles
- `plain text` - Regular text content
- `abandon text` - Text to ignore
- `figure` - Images, charts, diagrams
- `figure caption` - Figure descriptions
- `table` - Data tables
- `table caption` - Table descriptions
- `table footnote` - Table footnotes
- `isolated formula` - Mathematical formulas
- `formula caption` - Formula descriptions

### Processing Methods
- **OCR Target Classes**: `title`, `plain text`, `isolated formula`
- **API Target Classes**: `figure`, `table`, `figure caption`, `table caption`

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SMARTEYE_OPENAI_API_KEY` | OpenAI API key | Required |
| `SMARTEYE_HOST` | Server host | 0.0.0.0 |
| `SMARTEYE_PORT` | Server port | 8000 |
| `SMARTEYE_DEBUG` | Debug mode | false |
| `SMARTEYE_DEFAULT_MODEL` | Default YOLO model | docstructbench |
| `SMARTEYE_CONFIDENCE_THRESHOLD` | Detection threshold | 0.25 |
| `SMARTEYE_MAX_FILE_SIZE` | Max upload size (bytes) | 52428800 |
| `SMARTEYE_MEMORY_WARNING_THRESHOLD` | Memory warning % | 0.8 |
| `SMARTEYE_MEMORY_CRITICAL_THRESHOLD` | Memory critical % | 0.9 |

### Model Options

| Model | Description | Use Case |
|-------|-------------|----------|
| `docstructbench` | Educational worksheets | Recommended for Korean study materials |
| `doclaynet` | General documents | Academic papers, reports |
| `docsynth` | Custom synthetic training | Specialized document types |

## 📈 Performance & Scaling

### Memory Management
- Automatic memory monitoring and cleanup
- Adaptive batch sizing based on available memory
- Support for both CPU and GPU processing

### Batch Processing
- **Colab Environment**: Batch size 2 (memory limited)
- **Local Environment**: Batch size 4 (higher performance)
- **Production**: Configurable based on resources

### Processing Times (Approximate)
- Single image: 5-15 seconds
- Batch (10 images): 30-60 seconds  
- PDF (10 pages): 45-90 seconds

*Times vary based on image complexity, hardware, and processing mode.*

## 🛠️ Development

### Project Structure
```
backend/
├── api/           # FastAPI server and endpoints
├── core/          # Core processing modules (LAM, TSPM, Engine)
├── models/        # Pydantic data models
├── utils/         # Utility functions (memory, file processing)
└── config/        # Configuration management
```

### Running Tests
```bash
pytest tests/
```

### Adding New Features
1. Create feature branch
2. Implement changes in appropriate module
3. Add tests
4. Update API models if needed
5. Update documentation

## 📋 API Response Examples

### Single Image Analysis Result
```json
{
  "task_id": "uuid-12345",
  "status": "completed",
  "result": {
    "file_info": {
      "filename": "worksheet.jpg",
      "size_mb": 2.1,
      "width": 1024,
      "height": 768
    },
    "layout_analysis": {
      "detected_objects_count": 12,
      "model_used": "docstructbench",
      "layout_info": [
        {
          "id": 1,
          "class_name": "title",
          "confidence": 0.95,
          "coordinates": [100, 50, 500, 120]
        }
      ]
    },
    "content_analysis": {
      "total_objects": 8,
      "ocr_objects": 5,
      "api_objects": 3,
      "results": [
        {
          "id": 1,
          "class_name": "title",
          "content": "Math Worksheet Chapter 5",
          "content_type": "text",
          "method": "OCR"
        }
      ]
    },
    "processing_time": 12.5
  }
}
```

## 🐛 Troubleshooting

### Common Issues

**1. Model Download Fails**
```bash
# Check internet connection and HuggingFace access
# Clear cache: rm -rf ~/.cache/huggingface/
```

**2. OCR Not Working**
```bash
# Install Tesseract
sudo apt-get install tesseract-ocr tesseract-ocr-kor
```

**3. Memory Issues**
```bash
# Reduce batch size or use CPU mode
export SMARTEYE_DEVICE=cpu
export SMARTEYE_BATCH_SIZE_LOCAL=2
```

**4. OpenAI API Errors**
```bash
# Verify API key and quota
# Check network connectivity to OpenAI
```

### Logs
```bash
# View logs
tail -f smarteye.log

# Debug mode
python run_server.py --debug --log-level DEBUG
```

## 🔒 Security Considerations

### Production Deployment
- Add authentication/authorization
- Configure CORS properly
- Use HTTPS with SSL certificates
- Implement rate limiting
- Validate file uploads thoroughly
- Set up monitoring and alerting

### Environment Security
- Store API keys securely
- Use environment variables, not hardcoded values
- Implement proper error handling
- Regular security updates

## 📄 License

This project follows the same license as the original SmartEye repository.

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📞 Support

For issues and questions:
- Check the [troubleshooting section](#-troubleshooting)
- Create an issue on GitHub
- Check logs for detailed error information

---

**Built with ❤️ for the SmartEye community**