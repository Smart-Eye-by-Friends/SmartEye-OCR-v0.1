import React, { useRef, useState } from "react";
import { usePages } from "@/contexts/PagesContext";
import { uploadService } from "@/services/upload";
import styles from "./MultiFileLoader.module.css";

const MultiFileLoader: React.FC = () => {
  const { dispatch } = usePages();
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length > 0) {
      await uploadFiles(files);
    }
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);

    const files = Array.from(e.dataTransfer.files).filter(
      (file) =>
        file.type.startsWith("image/") || file.type === "application/pdf"
    );

    if (files.length > 0) {
      await uploadFiles(files);
    }
  };

  const uploadFiles = async (files: File[]) => {
    setIsUploading(true);

    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i];

        // 서버로 업로드 (백엔드 API 호출)
        const response = await uploadService.uploadPage({
          file,
        });

        // Context에 페이지 추가 (DB에 저장된 데이터 사용)
        dispatch({
          type: "ADD_PAGE",
          payload: {
            id: response.page_id.toString(),
            pageNumber: response.page_number,
            imagePath: response.image_path,
            thumbnailPath: response.image_path, // TODO: 썸네일 생성
            analysisStatus: response.analysis_status as
              | "pending"
              | "processing"
              | "completed"
              | "error",
          },
        });
      }

      alert(`${files.length}개 파일 업로드 완료!`);
    } catch (error: unknown) {
      console.error("Upload failed:", error);

      // 에러 메시지 개선
      let errorMessage = "업로드 중 오류가 발생했습니다.";

      if (error && typeof error === "object") {
        const err = error as {
          code?: string;
          message?: string;
          response?: { status: number; data?: { message?: string } };
        };

        if (
          err.code === "ERR_NETWORK" ||
          err.message?.includes("Network Error")
        ) {
          errorMessage =
            "⚠️ 백엔드 서버에 연결할 수 없습니다.\n\n백엔드 서버가 실행 중인지 확인해주세요.\n(http://localhost:8000)";
        } else if (err.response) {
          errorMessage = `서버 오류: ${err.response.status} - ${
            err.response.data?.message || err.message
          }`;
        }
      }

      alert(errorMessage);
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div
      className={`${styles.fileUploadZone} ${
        isDragging ? styles.dragging : ""
      }`}
      onClick={handleClick}
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
    >
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,application/pdf"
        onChange={handleFileChange}
        style={{ display: "none" }}
      />

      {isUploading ? (
        <>
          <div className={styles.spinner} />
          <p>업로드 중...</p>
        </>
      ) : (
        <>
          <div className={styles.uploadIcon}>📁</div>
          <p>파일을 드래그하거나 클릭하세요</p>
          <small>이미지 (JPG, PNG) 또는 PDF</small>
        </>
      )}
    </div>
  );
};

export default MultiFileLoader;
