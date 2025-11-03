import apiClient from "./api";

export interface AnalyzeRequest {
  image: File;
  documentType: "worksheet" | "document";
  analysisMode: "cim" | "basic";
}

export interface AnalyzeResponse {
  page_id: string;
  layout_analysis: any;
  text_content: any[];
  ai_descriptions: any[];
}

export const analysisService = {
  async analyzeImage(data: AnalyzeRequest): Promise<AnalyzeResponse> {
    const formData = new FormData();
    formData.append("image", data.image);
    formData.append("document_type", data.documentType);
    formData.append("analysis_mode", data.analysisMode);

    return apiClient.post("/analyze", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },

  async getVisualizationData(pageId: string) {
    return apiClient.get(`/pages/${pageId}/visualization-data`);
  },

  async saveText(pageId: string, content: string) {
    return apiClient.post(`/pages/${pageId}/text`, { content });
  },

  async formatText(pageId: string) {
    return apiClient.post(`/format`, { page_id: pageId });
  },
};
