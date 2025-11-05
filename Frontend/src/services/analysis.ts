import apiClient from "./api";

export interface ProjectAnalysisRequest {
  useAiDescriptions?: boolean;
  apiKey?: string;
  timeoutMs?: number;
}

export interface PageAnalysisRequest {
  useAiDescriptions?: boolean;
  apiKey?: string;
}

export interface PageTextResponse {
  page_id: number;
  version_id: number;
  version_type: string;
  is_current: boolean;
  content: string;
  created_at: string;
}

export interface LayoutElementResponse {
  element_id: number;
  page_id: number;
  class_name: string;
  confidence?: number | null;
  bbox_x: number;
  bbox_y: number;
  bbox_width: number;
  bbox_height: number;
  text_content?: {
    ocr_text: string;
  } | null;
  ai_description?: {
    description: string;
  } | null;
}

export interface PageDetailResponse {
  page_id: number;
  project_id: number;
  page_number: number;
  image_path: string;
  image_width?: number;
  image_height?: number;
  analysis_status: string;
  processing_time?: number | null;
  layout_elements?: LayoutElementResponse[];
  text_content?: string | null;
}

export interface PageStatsResponse {
  page_id: number;
  project_id: number;
  total_elements: number;
  anchor_element_count: number;
  processing_time?: number | null;
  class_distribution: Record<string, number>;
  confidence_scores: Record<string, number>;
}

export const analysisService = {
  async analyzeProject(
    projectId: number,
    options: ProjectAnalysisRequest = {}
  ) {
    return apiClient.post(
      `/projects/${projectId}/analyze`,
      {
        use_ai_descriptions: options.useAiDescriptions ?? true,
        api_key: options.apiKey,
      },
      {
        timeout: options.timeoutMs ?? 3000000, // 기본 50분 대기 (모델 로드 포함)
      }
    );
  },

  async analyzePageAsync(pageId: number, options: PageAnalysisRequest = {}) {
    return apiClient.post(`/pages/${pageId}/analyze/async`, {
      use_ai_descriptions: options.useAiDescriptions ?? true,
      api_key: options.apiKey,
    });
  },

  async getAnalysisJobStatus(jobId: string) {
    return apiClient.get(`/analysis/jobs/${jobId}`);
  },

  async getPageDetail(
    pageId: number,
    options: { includeLayout?: boolean; includeText?: boolean } = {}
  ): Promise<PageDetailResponse> {
    const params = new URLSearchParams();
    if (options.includeLayout) {
      params.append("include_layout", "true");
    }
    if (options.includeText) {
      params.append("include_text", "true");
    }
    const query = params.toString();
    const suffix = query ? `?${query}` : "";
    return apiClient.get(`/pages/${pageId}${suffix}`);
  },

  async getPageText(pageId: number): Promise<PageTextResponse> {
    return apiClient.get(`/pages/${pageId}/text`);
  },

  async savePageText(pageId: number, content: string, userId = 1) {
    return apiClient.post(`/pages/${pageId}/text`, {
      content,
      user_id: userId,
    });
  },

  async getPageStats(pageId: number): Promise<PageStatsResponse> {
    return apiClient.get(`/pages/${pageId}/stats`);
  },
};
