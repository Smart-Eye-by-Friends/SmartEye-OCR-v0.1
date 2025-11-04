import React, { useRef, useState } from "react";
import { usePages } from "@/contexts/PagesContext";
import type { Page } from "@/contexts/PagesContext";
import { projectService } from "@/services/projects";
import {
  uploadService,
  type MultiPageUploadResponse,
  type UploadPageResponse,
} from "@/services/upload";
import styles from "./MultiFileLoader.module.css";

const MultiFileLoader: React.FC = () => {
  const { state, dispatch } = usePages();
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const ensureProjectId = async (): Promise<number> => {
    if (state.currentProjectId) {
      return state.currentProjectId;
    }

    const project = await projectService.createTempProject();
    dispatch({ type: "SET_PROJECT", payload: project.project_id });
    return project.project_id;
  };

  const mapPageResponse = (page: UploadPageResponse): Page => ({
    id: page.page_id.toString(),
    pageNumber: page.page_number,
    imagePath: page.image_path,
    thumbnailPath: page.image_path, // TODO: 썸네일 전용 경로 분리
    analysisStatus: (page.analysis_status as Page["analysisStatus"]) ?? "pending",
    imageWidth: page.image_width,
    imageHeight: page.image_height,
  });

  const isMultiPageResponse = (
    response: UploadPageResponse | MultiPageUploadResponse
  ): response is MultiPageUploadResponse =>
    Array.isArray((response as MultiPageUploadResponse).pages);

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
    const collectedPages: Page[] = [];
    let hasCurrentPage = state.currentPageId !== null;

    try {
      const targetProjectId = await ensureProjectId();

      for (let i = 0; i < files.length; i++) {
        const file = files[i];

        // 서버로 업로드 (백엔드 API 호출)
        const response = await uploadService.uploadPage({
          file,
          projectId: targetProjectId,
        });

        const pagesToAdd = isMultiPageResponse(response)
          ? response.pages.map(mapPageResponse)
          : [mapPageResponse(response)];

        if (pagesToAdd.length > 0) {
          collectedPages.push(...pagesToAdd);
          dispatch({ type: "ADD_PAGES", payload: pagesToAdd });
          if (!hasCurrentPage) {
            dispatch({ type: "SET_CURRENT_PAGE", payload: pagesToAdd[0].id });
            hasCurrentPage = true;
          }
        }
      }

      if (collectedPages.length > 0) {
        alert(`${collectedPages.length}개 페이지 업로드 완료!`);
      }
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

      if (collectedPages.length > 0) {
        errorMessage += `\n\n단, ${collectedPages.length}개 페이지는 업로드되었습니다.`;
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
