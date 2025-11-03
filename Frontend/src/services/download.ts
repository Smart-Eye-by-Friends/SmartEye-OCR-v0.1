// src/services/download.ts
import axios from "axios";

export interface DownloadProgress {
  current: number;
  total: number;
  percentage: number;
}

export const downloadService = {
  async downloadAllPages(
    pages: any[],
    onProgress: (progress: DownloadProgress) => void
  ) {
    const total = pages.length;
    const results = [];

    for (let i = 0; i < total; i++) {
      const page = pages[i];

      try {
        const result = await axios.get(`/api/download/${page.id}`, {
          responseType: "blob",
        });

        results.push({
          pageId: page.id,
          success: true,
          blob: result.data,
        });

        onProgress({
          current: i + 1,
          total,
          percentage: Math.round(((i + 1) / total) * 100),
        });
      } catch (error) {
        results.push({
          pageId: page.id,
          success: false,
          error: (error as Error).message,
        });
      }
    }

    return results;
  },
};
