// src/components/viewer/LayoutPanel.tsx
import React, { useState, useRef, useEffect, useMemo } from "react";
import ImageViewer from "./ImageViewer";
import BoundingBoxOverlay from "./BoundingBoxOverlay";
import styles from "./LayoutPanel.module.css";
import { usePages } from "@/contexts/PagesContext";
import {
  analysisService,
  type LayoutElementResponse,
} from "@/services/analysis";

type BoundingBox = {
  id: string;
  class: string;
  confidence: number;
  text?: string | null;
  coordinates: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
};

const LayoutPanel: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [panelSize, setPanelSize] = useState({ width: 0, height: 0 });
  const [layoutBoxes, setLayoutBoxes] = useState<BoundingBox[]>([]);
  const [isLayoutLoading, setIsLayoutLoading] = useState(false);
  const [layoutError, setLayoutError] = useState<string | null>(null);
  const { state } = usePages();

  const apiBase =
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api";
  const uploadBaseCandidate = apiBase.replace(/\/api\/?$/, "");
  const uploadBase =
    uploadBaseCandidate !== ""
      ? uploadBaseCandidate.replace(/\/$/, "")
      : typeof window !== "undefined"
      ? window.location.origin
      : "";

  const currentPage = useMemo(() => {
    if (!state.pages.length) {
      return null;
    }
    return (
      state.pages.find((page) => page.id === state.currentPageId) ||
      state.pages[0]
    );
  }, [state.pages, state.currentPageId]);

  const currentImage = useMemo(() => {
    if (!currentPage || !currentPage.imagePath) {
      return null;
    }

    const sanitizedPath = currentPage.imagePath.replace(/^\/+/, "");

    return {
      url: `${uploadBase}/${sanitizedPath}`,
      originalSize: {
        width: currentPage.imageWidth ?? 0,
        height: currentPage.imageHeight ?? 0,
      },
    };
  }, [currentPage, uploadBase]);

  const updatePanelSize = () => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setPanelSize({ width: rect.width, height: rect.height });
  };

  useEffect(() => {
    updatePanelSize();
    window.addEventListener("resize", updatePanelSize);
    return () => window.removeEventListener("resize", updatePanelSize);
  }, []);

  useEffect(() => {
    updatePanelSize();
  }, [currentImage]);

  useEffect(() => {
    if (!currentPage) {
      setLayoutBoxes([]);
      setLayoutError(null);
      setIsLayoutLoading(false);
      return;
    }

    if (currentPage.analysisStatus !== "completed") {
      setLayoutBoxes([]);
      setLayoutError(null);
      setIsLayoutLoading(false);
      return;
    }

    const pageIdNumber = Number(currentPage.id);
    if (!Number.isFinite(pageIdNumber)) {
      setLayoutError("잘못된 페이지 ID입니다.");
      setLayoutBoxes([]);
      return;
    }

    let isActive = true;
    setIsLayoutLoading(true);
    setLayoutError(null);

    analysisService
      .getPageDetail(pageIdNumber, { includeLayout: true })
      .then((detail) => {
        if (!isActive) return;
        const mapped: BoundingBox[] = (detail.layout_elements ?? []).map(
          (element: LayoutElementResponse) => ({
            id: element.element_id.toString(),
            class: element.class_name,
            confidence: element.confidence ?? 0,
            text:
              element.text_content?.ocr_text ||
              element.ai_description?.description ||
              null,
            coordinates: {
              x: element.bbox_x,
              y: element.bbox_y,
              width: element.bbox_width,
              height: element.bbox_height,
            },
          })
        );
        setLayoutBoxes(mapped);
      })
      .catch((error) => {
        if (!isActive) return;
        console.error("레이아웃 데이터 조회 실패", error);
        let message = "레이아웃 데이터를 불러오는 중 오류가 발생했습니다.";
        if (error && typeof error === "object") {
          const err = error as {
            response?: { status: number; data?: { detail?: string; error?: string } };
            message?: string;
          };
          if (err.response) {
            message = `서버 오류 (${err.response.status}): ${
              err.response.data?.detail || err.response.data?.error ||
              err.message || "알 수 없는 오류"
            }`;
          }
        }
        setLayoutError(message);
        setLayoutBoxes([]);
      })
      .finally(() => {
        if (isActive) {
          setIsLayoutLoading(false);
        }
      });

    return () => {
      isActive = false;
    };
  }, [currentPage]);

  const imageDisplaySize = useMemo(() => {
    if (!currentImage || !currentImage.originalSize.width || !currentImage.originalSize.height) {
      return { width: 0, height: 0 };
    }

    if (panelSize.width === 0 || panelSize.height === 0) {
      return { width: 0, height: 0 };
    }

    const { width: originalWidth, height: originalHeight } = currentImage.originalSize;
    if (originalWidth === 0 || originalHeight === 0) {
      return { width: 0, height: 0 };
    }

    const scale = Math.min(
      panelSize.width / originalWidth,
      panelSize.height / originalHeight
    );

    return {
      width: originalWidth * scale,
      height: originalHeight * scale,
    };
  }, [currentImage, panelSize]);

  const overlayOffset = useMemo(() => {
    if (!imageDisplaySize.width || !imageDisplaySize.height) {
      return { x: 0, y: 0 };
    }
    return {
      x: (panelSize.width - imageDisplaySize.width) / 2,
      y: (panelSize.height - imageDisplaySize.height) / 2,
    };
  }, [panelSize, imageDisplaySize]);

  const handleBoxClick = (box: BoundingBox) => {
    console.log("Box clicked:", box);
    // TODO: 에디터와 연동
  };

  const handleBoxHover = (box: BoundingBox) => {
    console.log("Box hovered:", box);
  };

  const renderOverlay = () => {
    if (!currentImage) {
      return (
        <div className={styles.statusOverlay}>
          <span>이미지를 선택해주세요.</span>
        </div>
      );
    }

    if (currentPage?.analysisStatus !== "completed") {
      return (
        <div className={styles.statusOverlay}>
          <span>분석이 완료되면 레이아웃 결과가 표시됩니다.</span>
        </div>
      );
    }

    if (isLayoutLoading) {
      return (
        <div className={styles.statusOverlay}>
          <span>레이아웃을 불러오는 중...</span>
        </div>
      );
    }

    if (layoutError) {
      return (
        <div className={styles.statusOverlay}>
          <span>{layoutError}</span>
        </div>
      );
    }

    if (layoutBoxes.length === 0) {
      return (
        <div className={styles.statusOverlay}>
          <span>표시할 레이아웃 요소가 없습니다.</span>
        </div>
      );
    }

    if (!imageDisplaySize.width || !imageDisplaySize.height) {
      return (
        <div className={styles.statusOverlay}>
          <span>이미지 크기를 계산할 수 없습니다.</span>
        </div>
      );
    }

    return (
      <BoundingBoxOverlay
        bboxes={layoutBoxes}
        imageSize={currentImage.originalSize}
        displaySize={imageDisplaySize}
        offset={overlayOffset}
        onBoxClick={handleBoxClick}
        onBoxHover={handleBoxHover}
      />
    );
  };

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      <ImageViewer image={currentImage} />
      {renderOverlay()}
    </div>
  );
};

export default LayoutPanel;
