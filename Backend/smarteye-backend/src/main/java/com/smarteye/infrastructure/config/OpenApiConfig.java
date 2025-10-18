package com.smarteye.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 설정
 * <p>
 * Spring Boot 3.5.5 및 Springdoc OpenAPI 2.7.0 호환 설정
 * Swagger UI: http://localhost:8080/swagger-ui/index.html
 * API Docs: http://localhost:8080/v3/api-docs
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:smarteye-backend}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        developmentServer(),
                        productionServer()
                ));
    }

    /**
     * API 정보 설정
     */
    private Info apiInfo() {
        return new Info()
                .title("SmartEye API")
                .version("v0.4")
                .description("""
                        ## SmartEye AI 기반 학습지 분석 시스템 API

                        ### 주요 기능
                        - **레이아웃 분석**: DocLayout-YOLO 기반 33가지 레이아웃 요소 감지
                        - **OCR 처리**: Tesseract 기반 한국어+영어 텍스트 인식
                        - **AI 설명**: OpenAI GPT-4 Vision 기반 이미지/차트 설명
                        - **CIM 통합 분석**: Circuit Integration Management 기반 구조화된 분석
                        - **다단 레이아웃 지원**: CBHLS 전략 기반 2D 공간 정렬
                        - **XSS 방지**: Apache Commons Text 기반 안전한 HTML 출력

                        ### 아키텍처
                        - **백엔드**: Java 21 + Spring Boot 3.5.5
                        - **LAM Service**: Python FastAPI + DocLayout-YOLO
                        - **데이터베이스**: PostgreSQL 15 + JPA/Hibernate
                        - **인프라**: Docker Compose + Nginx

                        ### 분석 파이프라인
                        ```
                        1. 이미지 업로드
                        2. LAM Service: 레이아웃 분석 (SmartEye, SmartEyeSsen 모델)
                        3. TSPM Engine: 문제별 정렬 및 구조화
                        4. OCR 처리 (Tesseract)
                        5. AI 설명 (OpenAI GPT-4 Vision)
                        6. CIM Processor: 최종 구조화
                        7. FormattedText 생성 (다단 레이아웃 지원)
                        ```

                        ### 개발 환경 특징 (v0.4)
                        - 🔧 **사용자 인증 불필요**: dev_user 자동 할당으로 즉시 테스트 가능
                        - 🔧 **자동 데이터 초기화**: 애플리케이션 시작 시 기본 테스트 사용자 생성
                        - 🔧 **유연한 사용자 관리**: userId 파라미터 선택적 (프로덕션에서는 필수)

                        ### 주요 개선사항 (v0.4)
                        - ✅ CBHLS 전략 85% 구현 (Phase 1-2 완료)
                        - ✅ 다단 레이아웃 처리 (Gap Detection 알고리즘)
                        - ✅ XSS 방지 (HTML 이스케이프 처리)
                        - ✅ 2D 공간 분석 (90% 정확도)
                        - ✅ 개발 환경 친화적 설정 (DevDataInitializer)

                        ### 기술 지원
                        - GitHub: https://github.com/Smart-Eye-by-Friends/SmartEye
                        - 문의: smart-eye-team@example.com
                        """)
                .contact(new Contact()
                        .name("SmartEye Development Team")
                        .email("smart-eye-team@example.com")
                        .url("https://github.com/Smart-Eye-by-Friends"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    /**
     * 개발 서버 설정
     */
    private Server developmentServer() {
        return new Server()
                .url("http://localhost:" + serverPort)
                .description("Development Server (로컬 개발 환경)");
    }

    /**
     * 프로덕션 서버 설정
     */
    private Server productionServer() {
        return new Server()
                .url("https://api.smarteye.example.com")
                .description("Production Server (프로덕션 환경)");
    }
}
