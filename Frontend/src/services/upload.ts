import apiClient from "./api";

export interface UploadPageRequest {
  file: File;
}

export interface UploadPageResponse {
  page_id: number;
  project_id: number;
  page_number: number;
  image_path: string;
  image_width: number;
  image_height: number;
  analysis_status: string;
}

export const uploadService = {
  async uploadPage(data: UploadPageRequest): Promise<UploadPageResponse> {
    const formData = new FormData();
    formData.append("file", data.file);

    // 백엔드가 project_id와 page_number를 자동으로 계산하므로
    // file만 전송합니다.
    return apiClient.post("/pages/upload", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },
};
