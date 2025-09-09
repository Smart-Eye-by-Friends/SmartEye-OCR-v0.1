# 프로젝트 개요

SmartEye v0.4는 광학 문자 인식(OCR)을 사용하는 문서 분석 시스템입니다. Java/Spring Boot 백엔드와 AI/ML 작업을 위한 Python/FastAPI 서비스로 구성된 마이크로서비스 아키텍처로 구축되었습니다. 전체 시스템은 Docker Compose를 사용하여 컨테이너화됩니다.

프론트엔드는 React 애플리케이션이지만 아직 백엔드와 완전히 통합되지 않았습니다.

## 주요 기술

*   **백엔드:** Java 21, Spring Boot 3.5.5, Spring Data JPA, Spring Web, Spring WebFlux, Resilience4j, Apache PDFBox, Tess4J, OpenCV
*   **LAM 서비스 (AI/ML):** Python 3.9+, FastAPI, PyTorch, HuggingFace Transformers, DocLayout-YOLO, OpenCV, PIL, NumPy
*   **데이터베이스:** PostgreSQL 15
*   **프론트엔드:** React, Material UI
*   **인프라:** Docker, Docker Compose, Nginx

# 빌드 및 실행

이 프로젝트는 통합된 `manage.sh` 스크립트로 관리됩니다.

*   **사용 가능한 모든 명령어를 보려면:**
    ```bash
    ./manage.sh help
    ```
*   **시스템을 시작하려면:**
    ```bash
    ./manage.sh start
    ```
*   **시스템을 중지하려면:**
    ```bash
    ./manage.sh stop
    ```
*   **시스템 상태를 확인하려면:**
    ```bash
    ./manage.sh status
    ```

메인 Docker Compose 파일은 `Backend/docker-compose.yml`에 있습니다.

## 개발 컨벤션

*   백엔드 코드는 `Backend/smarteye-backend`에 있으며 표준 Spring Boot 프로젝트 구조를 따릅니다.
*   LAM 서비스 코드는 `Backend/smarteye-lam-service`에 있습니다.
*   프론트엔드 코드는 `frontend`에 있습니다.
*   API 문서는 시스템 시작 후 `http://localhost:8080/swagger-ui/index.html`의 Swagger UI에서 확인할 수 있습니다.
*   레거시 Python 서버 및 관련 파일은 `legacy` 디렉터리에 있습니다.
