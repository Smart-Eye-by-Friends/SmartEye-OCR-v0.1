// src/components/sidebar/IntegratedDownloadButton.tsx
import React, { useState } from "react";
import DownloadProgressModal from "./DownloadProgressModal";
import { downloadService, type DownloadProgress } from "@/services/download";
import styles from "./IntegratedDownloadButton.module.css";

interface IntegratedDownloadButtonProps {
  pages: any[];
}

const IntegratedDownloadButton: React.FC<IntegratedDownloadButtonProps> = ({
  pages,
}) => {
  const [isDownloading, setIsDownloading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [progress, setProgress] = useState<DownloadProgress>({
    current: 0,
    total: 0,
    percentage: 0,
  });

  const handleDownload = async () => {
    if (pages.length === 0) {
      alert("다운로드할 페이지가 없습니다.");
      return;
    }

    setIsDownloading(true);
    setShowModal(true);

    try {
      const results = await downloadService.downloadAllPages(pages, (p) =>
        setProgress(p)
      );

      // 성공한 결과만 처리
      const successResults = results.filter((r) => r.success);

      if (successResults.length === 0) {
        throw new Error("다운로드에 실패했습니다");
      }

      // TODO: ZIP 파일 생성 및 다운로드
      console.log("Download completed:", successResults);
    } catch (error) {
      console.error("Download error:", error);
      alert("다운로드 중 오류가 발생했습니다.");
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className={styles.integratedDownload}>
      <button
        className={styles.downloadBtn}
        disabled={isDownloading || pages.length === 0}
        onClick={handleDownload}
      >
        <span className={styles.icon}>📦</span>
        {isDownloading ? "다운로드 중..." : "통합 다운로드"}
      </button>

      <DownloadProgressModal
        isOpen={showModal}
        progress={progress}
        onClose={() => setShowModal(false)}
      />
    </div>
  );
};

export default IntegratedDownloadButton;
