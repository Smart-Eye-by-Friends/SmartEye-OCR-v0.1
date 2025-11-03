import apiClient from "./api";

export interface UploadPageRequest {
  projectId: number;
  pageNumber: number;
  file: File;
}

export interface UploadPageResponse {
  page_id: number;
  image_path: string;
  image_width: number;
  image_height: number;
  status: string;
}

export const uploadService = {
  async uploadPage(data: UploadPageRequest): Promise<UploadPageResponse> {
    const formData = new FormData();
    formData.append("project_id", data.projectId.toString());
    formData.append("page_number", data.pageNumber.toString());
    formData.append("file", data.file);

    return apiClient.post("/upload/page", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },
};
