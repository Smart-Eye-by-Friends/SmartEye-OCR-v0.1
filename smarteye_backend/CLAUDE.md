# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SmartEye Backend is a Django-based document analysis service that converts documents (images and PDFs) into accessible formats. The system uses a three-stage processing pipeline:

1. **LAM (Layout Analysis Module)**: DocLayout-YOLO-based layout detection
2. **TSPM (Text & Scene Processing Module)**: OCR and image description generation  
3. **CIM (Content Integration Module)**: Result integration and output generation

## Development Commands

### Local Development Setup
```bash
# Initial setup
./setup.sh

# Manual setup if needed
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python manage.py makemigrations
python manage.py migrate
python manage.py createsuperuser
```

### Development Server
```bash
# Start Django development server
python manage.py runserver

# Start Celery worker
celery -A smarteye worker --loglevel=info

# Start Celery beat (scheduled tasks)
celery -A smarteye beat --loglevel=info
```

### Docker Development
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Database Management
```bash
# Create migrations
python manage.py makemigrations

# Apply migrations
python manage.py migrate

# Create superuser
python manage.py createsuperuser

# Django shell
python manage.py shell
```

### Testing
```bash
# Run tests (check for pytest configuration in project)
python manage.py test

# If pytest is configured
pytest

# Run specific test file
python manage.py test apps.analysis.tests
```

## Core Architecture

### Three-Stage Processing Pipeline

The system follows a sequential processing model where each stage depends on the previous:

1. **LAM**: Analyzes document layout using YOLO models, creates bounding boxes for detected elements
2. **TSPM**: Processes LAM results to extract text (OCR) and generate descriptions for non-text elements
3. **CIM**: Integrates all results into final accessible formats (text, braille, JSON, PDF)

### Key Models Structure

- **AnalysisJob**: Main job entity with status tracking
- **ProcessedImage**: Individual image/page processing records
- **LAMLayoutDetection**: Layout detection results with bounding boxes
- **TSPMOCRResult**: OCR extraction results
- **TSPMImageDescription**: AI-generated image descriptions
- **CIMIntegratedResult**: Final integrated output

### Asynchronous Processing

The system uses Celery for background processing:
- Jobs are queued and processed asynchronously
- Real-time progress updates via WebSocket (Django Channels)
- Automatic retry mechanisms for failed tasks
- Memory-aware batch processing

### Core Services Location

- LAM Service: `core/lam/service.py`
- TSPM Service: `core/tspm/service.py` 
- CIM Service: `core/cim/service.py`
- Celery Tasks: `apps/analysis/tasks.py`

### API Structure

The API follows RESTful conventions:
- `/api/v1/auth/` - Authentication endpoints
- `/api/v1/analysis/` - Job management and processing
- `/api/v1/files/` - File upload and management
- `/api/v1/users/` - User management

### Configuration Management

Settings are split by environment:
- `smarteye/settings/base.py` - Common settings
- `smarteye/settings/development.py` - Development overrides
- `smarteye/settings/production.py` - Production configuration

Environment variables are managed via `.env` file with django-environ. See `.env.example` for all required environment variables including:
- Database connection (PostgreSQL)
- Redis configuration (Celery + Channels)
- OpenAI API key (for AI image descriptions)
- Security settings (SECRET_KEY, SSL, CORS)
- Optional AWS S3 storage configuration

### Memory Management

The LAM module includes adaptive memory management:
- Dynamic batch size calculation based on available memory
- Automatic cleanup of temporary files
- Memory usage monitoring during processing

### File Processing

Supports multiple input formats:
- Images: JPG, PNG 
- Documents: PDF (converted to images per page)
- Output formats: Text, JSON, XML, Braille, PDF reports

### Dependencies

Key packages (current versions):
- Django==4.2.7 with DRF==3.14.0 for API
- Celery==5.3.4 + Redis==5.0.1 for async processing
- Channels==4.0.0 for WebSocket support
- PyTorch==2.2.0 + Ultralytics==8.0.200 for YOLO models
- OpenCV==4.8.1.78 for image processing
- PyTesseract==0.3.10 for OCR
- OpenAI==1.3.5 API for image descriptions
- psycopg2-binary==2.9.7 for PostgreSQL
- PyMuPDF==1.23.8 for PDF processing
- Gunicorn==21.2.0 + gevent==23.9.1 for production

## Common Patterns

### Error Handling
All services implement comprehensive error handling with detailed logging. Check `logs/django.log` for debugging.

### Database Queries
Models use proper indexing for performance. Complex queries should use select_related/prefetch_related.

### File Storage
Uses Django's default storage backend. In production, configure for cloud storage via django-storages.

### API Responses
APIs return consistent JSON responses with status, data, and error fields.

### Configuration Access
SmartEye-specific configs are in `settings.SMARTEYE_CONFIG` dictionary.