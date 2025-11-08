import apiClient from "./api";

export interface ProjectResponse {
  project_id: number;
  project_name: string;
  doc_type_id: number;
  analysis_mode: string;
  status: string;
  total_pages: number;
  user_id: number;
  created_at: string;
  updated_at: string;
}

export interface ProjectPageResponse {
  page_id: number;
  page_number: number;
  image_path: string;
  thumbnail_path?: string | null;
  analysis_status: "pending" | "processing" | "completed" | "error";
  image_width?: number | null;
  image_height?: number | null;
}

export interface ProjectWithPagesResponse extends ProjectResponse {
  pages: ProjectPageResponse[];
}

const DEFAULT_PROJECT_PAYLOAD = {
  project_name: "temp",
  doc_type_id: 1,
  analysis_mode: "auto",
  user_id: 1,
};

export const projectService = {
  async createTempProject(): Promise<ProjectResponse> {
    return apiClient.post("/projects", DEFAULT_PROJECT_PAYLOAD);
  },

  async getProjectDetail(projectId: number): Promise<ProjectWithPagesResponse> {
    return apiClient.get(`/projects/${projectId}`);
  },
};
