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

export interface CreateProjectRequest {
  project_name: string;
  doc_type_id: number;
  analysis_mode?: string;
  user_id?: number;
}

export const projectService = {
  async createProject(
    projectName: string,
    docTypeId: number,
    analysisMode: string = "auto",
    userId: number = 1
  ): Promise<ProjectResponse> {
    const payload: CreateProjectRequest = {
      project_name: projectName,
      doc_type_id: docTypeId,
      analysis_mode: analysisMode,
      user_id: userId,
    };
    return apiClient.post("/projects", payload);
  },

  async listProjects(params: {
    userId?: number;
    skip?: number;
    limit?: number;
  } = {}): Promise<ProjectResponse[]> {
    return apiClient.get("/projects", { params });
  },

  async getProjectDetail(projectId: number): Promise<ProjectWithPagesResponse> {
    return apiClient.get(`/projects/${projectId}`);
  },
};
