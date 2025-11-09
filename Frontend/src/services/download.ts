import axios from "axios";
import apiClient from "./api";

export interface CombinedTextStats {
  total_pages: number;
  total_words: number;
  total_characters: number;
  [key: string]: number;
}

export interface CombinedTextResponse {
  project_id: number;
  project_name: string;
  combined_text: string;
  stats: CombinedTextStats;
  generated_at: string;
}

export interface DownloadProgress {
  current: number;
  total: number;
  percentage: number;
  status?: string;
}

const resolveApiBaseUrl = (): string => {
  const base = apiClient.defaults.baseURL || "";
  if (base) {
    return base.replace(/\/$/, "");
  }
  const envBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api";
  return (envBase as string).replace(/\/$/, "");
};

const parseFilename = (disposition?: string, fallback?: string): string => {
  if (!disposition) {
    return fallback || "download.docx";
  }

  const filenameMatch = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition);
  if (filenameMatch) {
    const encoded = filenameMatch[1] || filenameMatch[2];
    try {
      return decodeURIComponent(encoded);
    } catch (_error) {
      return encoded;
    }
  }

  return fallback || "download.docx";
};

export const downloadService = {
  async generateCombinedText(projectId: number): Promise<CombinedTextResponse> {
    return apiClient.get(`/projects/${projectId}/combined-text`);
  },

  async downloadProjectDocx(projectId: number): Promise<{ blob: Blob; filename: string }>
  {
    const baseURL = resolveApiBaseUrl();
    const requestUrl = `${baseURL}/projects/${projectId}/download`;

    const response = await axios.post(requestUrl, undefined, {
      responseType: "blob",
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      },
    });

    const filename = parseFilename(
      response.headers["content-disposition"],
      `project-${projectId}.docx`
    );

    return { blob: response.data as Blob, filename };
  },
};
