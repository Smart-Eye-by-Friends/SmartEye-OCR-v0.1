import axios from "axios";

// 환경 변수에서 API URL 가져오기, 없으면 기본값 사용
const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

class ApiService {
  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 300000, // 5분 타임아웃 (대용량 이미지 분석용)
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });

    // 요청 인터셉터
    this.client.interceptors.request.use(
      (config) => {
        console.log(`API 요청: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        console.error("API 요청 오류:", error);
        return Promise.reject(error);
      }
    );

    // 응답 인터셉터
    this.client.interceptors.response.use(
      (response) => {
        console.log(`API 응답: ${response.status} ${response.config.url}`);
        return response;
      },
      (error) => {
        console.error(
          "API 응답 오류:",
          error.response?.status,
          error.response?.data
        );
        return Promise.reject(error);
      }
    );
  }

  async analyzeWorksheet({ image, modelChoice, apiKey, endpoint }) {
    const formData = new FormData();
    formData.append("image", image);
    formData.append("modelChoice", modelChoice);

    if (apiKey) {
      formData.append("apiKey", apiKey);
    }

    try {
      // Backend API 명세에 맞는 올바른 엔드포인트 사용
      let correctedEndpoint = endpoint;

      // Frontend에서 전달되는 엔드포인트를 Backend API 형식으로 변환
      if (endpoint === "/analyze-structured") {
        correctedEndpoint = "/api/document/analyze-structured";
      } else if (endpoint === "/analyze") {
        correctedEndpoint = "/api/document/analyze";
      } else if (!endpoint.startsWith("/api/")) {
        correctedEndpoint = `/api/document${endpoint}`;
      }

      console.log(`수정된 엔드포인트: ${correctedEndpoint}`);
      const response = await this.client.post(correctedEndpoint, formData);

      // 응답 데이터 상세 로깅
      console.log("=== API 응답 데이터 상세 ===");
      console.log("응답 상태:", response.status);
      console.log("응답 헤더:", response.headers);
      console.log("응답 데이터 타입:", typeof response.data);
      console.log("응답 데이터:", response.data);
      console.log("응답 데이터 JSON:", JSON.stringify(response.data, null, 2));
      console.log("=== API 응답 데이터 끝 ===");

      return response.data;
    } catch (error) {
      console.error("분석 API 호출 오류:", error);
      throw error;
    }
  }

  async saveAsWord(text, filename = "smarteye_document") {
    const formData = new FormData();

    // HTML 태그 제거 후 전송
    const plainText = text.replace(/<[^>]*>/g, "");
    formData.append("text", plainText);
    formData.append("filename", filename);

    try {
      const response = await this.client.post("/save-as-word", formData, {
        responseType: "blob",
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });

      // 파일 다운로드 처리
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `${filename}.docx`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      return { success: true };
    } catch (error) {
      console.error("워드 저장 API 호출 오류:", error);
      throw error;
    }
  }

  async healthCheck() {
    try {
      const response = await this.client.get("/health");
      return response.data;
    } catch (error) {
      console.error("헬스 체크 실패:", error);
      throw error;
    }
  }

  // 백엔드 서버 상태 확인
  async checkServerStatus() {
    try {
      const response = await this.client.get("/status", { timeout: 5000 });
      return {
        status: "online",
        data: response.data,
      };
    } catch (error) {
      return {
        status: "offline",
        error: error.message,
      };
    }
  }

  // 파일 업로드 유효성 검사
  validateImageFile(file) {
    const maxSize = 10 * 1024 * 1024; // 10MB
    const allowedTypes = ["image/jpeg", "image/jpg", "image/png", "image/gif"];

    if (!file) {
      return { valid: false, error: "파일이 선택되지 않았습니다." };
    }

    if (file.size > maxSize) {
      return { valid: false, error: "파일 크기가 10MB를 초과합니다." };
    }

    if (!allowedTypes.includes(file.type)) {
      return {
        valid: false,
        error:
          "지원하지 않는 파일 형식입니다. JPG, PNG, GIF 파일만 지원됩니다.",
      };
    }

    return { valid: true };
  }

  // 이미지 압축 (옵션)
  async compressImage(file, maxWidth = 1920, quality = 0.8) {
    return new Promise((resolve) => {
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d");
      const img = new Image();

      img.onload = () => {
        // 비율 유지하면서 크기 조정
        const ratio = Math.min(maxWidth / img.width, maxWidth / img.height);
        canvas.width = img.width * ratio;
        canvas.height = img.height * ratio;

        // 이미지 그리기
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

        // Blob으로 변환
        canvas.toBlob(resolve, "image/jpeg", quality);
      };

      img.src = URL.createObjectURL(file);
    });
  }
}

export const apiService = new ApiService();
