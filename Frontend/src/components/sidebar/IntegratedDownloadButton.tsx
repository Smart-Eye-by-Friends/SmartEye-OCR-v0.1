// src/components/sidebar/IntegratedDownloadButton.tsx
import React, { useState } from "react";
import DownloadProgressModal from "./DownloadProgressModal";
import { downloadService, type DownloadProgress } from "@/services/download";
import styles from "./IntegratedDownloadButton.module.css";

interface IntegratedDownloadButtonProps {
  pages: any[];
  projectId: number | null;
}

const IntegratedDownloadButton: React.FC<IntegratedDownloadButtonProps> = ({
  pages,
  projectId,
}) => {
  const [isDownloading, setIsDownloading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [progress, setProgress] = useState<DownloadProgress>({
    current: 0,
    total: 2,
    percentage: 0,
    status: "대기 중",
  });

  const handleDownload = async () => {
    if (pages.length === 0) {
      alert("다운로드할 페이지가 없습니다.");
      return;
    }

    if (!projectId) {
      alert("프로젝트 정보가 없습니다. 페이지를 다시 업로드하거나 선택해주세요.");
      return;
    }

    setIsDownloading(true);
    setShowModal(true);
    setProgress({ current: 0, total: 2, percentage: 0, status: "통합 텍스트 생성 준비" });

    try {
      await downloadService.generateCombinedText(projectId);
      setProgress({
        current: 1,
        total: 2,
        percentage: 50,
        status: "통합 텍스트 생성 완료",
      });

      const { blob, filename } = await downloadService.downloadProjectDocx(
        projectId
      );

      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = downloadUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(downloadUrl);

      setProgress({
        current: 2,
        total: 2,
        percentage: 100,
        status: "DOCX 다운로드 완료",
      });
    } catch (error) {
      console.error("Download error:", error);
      alert("다운로드 중 오류가 발생했습니다.");
      setProgress({
        current: 0,
        total: 2,
        percentage: 0,
        status: "오류 발생",
      });
      setShowModal(false);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className={styles.integratedDownload}>
      <button
        className={styles.downloadBtn}
        disabled={isDownloading || pages.length === 0 || !projectId}
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
