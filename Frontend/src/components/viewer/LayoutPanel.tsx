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
  const [transform, setTransform] = useState({ zoom: 1, position: { x: 0, y: 0 } });
  const [overlayVisible, setOverlayVisible] = useState(true);
  const [visibleClasses, setVisibleClasses] = useState<Set<string>>(new Set());
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

  const availableClasses = useMemo(() => {
    return Array.from(new Set(layoutBoxes.map((box) => box.class)));
  }, [layoutBoxes]);

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

  const handleBoxClick = (box: BoundingBox) => {
    console.log("Box clicked:", box);
    // TODO: 에디터와 연동
  };

  const handleBoxHover = (box: BoundingBox) => {
    console.log("Box hovered:", box);
  };

  const toggleAllClasses = () => {
    if (visibleClasses.size === 0) {
      setVisibleClasses(new Set(availableClasses));
    } else {
      setVisibleClasses(new Set());
    }
  };

  const hasDisplaySize =
    imageDisplaySize.width > 0 && imageDisplaySize.height > 0
      ? imageDisplaySize
      : undefined;

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      {/* 오버레이 컨트롤 UI */}
      {layoutBoxes.length > 0 && (
        <div className={styles.overlayControls}>
          <button
            className={styles.toggleBtn}
            onClick={() => setOverlayVisible(!overlayVisible)}
          >
            {overlayVisible ? "🔲 오버레이 숨기기" : "🔳 오버레이 보기"}
          </button>

          {overlayVisible && availableClasses.length > 0 && (
            <div className={styles.classFilters}>
              <div className={styles.filterHeader}>
                <strong>클래스 필터</strong>
                <button onClick={toggleAllClasses}>
                  {visibleClasses.size === 0 ? "전체 선택" : "전체 해제"}
                </button>
              </div>

              {availableClasses.map((cls) => (
                <label key={cls} className={styles.filterItem}>
                  <input
                    type="checkbox"
                    checked={visibleClasses.size === 0 || visibleClasses.has(cls)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        const newSet = new Set(visibleClasses);
                        newSet.add(cls);
                        setVisibleClasses(newSet);
                      } else {
                        // "모두 보이기" 상태에서 하나를 제외
                        if (visibleClasses.size === 0) {
                          const newSet = new Set(availableClasses);
                          newSet.delete(cls);
                          setVisibleClasses(newSet);
                        } else {
                          const newSet = new Set(visibleClasses);
                          newSet.delete(cls);
                          setVisibleClasses(newSet);
                        }
                      }
                    }}
                  />
                  <span className={styles.className}>{cls}</span>
                </label>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 이미지 뷰어 */}
      <ImageViewer
        image={currentImage}
        displaySize={hasDisplaySize}
        onTransformChange={setTransform}
        overlay={
          currentImage && currentPage?.analysisStatus === "completed" && layoutBoxes.length > 0 ? (
            <BoundingBoxOverlay
              bboxes={layoutBoxes}
              imageSize={currentImage.originalSize}
              displaySize={imageDisplaySize}
              transform={transform}
              isVisible={overlayVisible}
              visibleClasses={visibleClasses}
              onBoxClick={handleBoxClick}
              onBoxHover={handleBoxHover}
            />
          ) : null
        }
      />

      {/* 상태 메시지 */}
      {!currentImage && (
        <div className={styles.statusOverlay}>
          <span>이미지를 선택해주세요.</span>
        </div>
      )}

      {currentImage && currentPage?.analysisStatus !== "completed" && (
        <div className={styles.statusOverlay}>
          <span>분석이 완료되면 레이아웃 결과가 표시됩니다.</span>
        </div>
      )}

      {isLayoutLoading && (
        <div className={styles.statusOverlay}>
          <span>레이아웃을 불러오는 중...</span>
        </div>
      )}

      {layoutError && (
        <div className={styles.statusOverlay}>
          <span>{layoutError}</span>
        </div>
      )}

      {currentImage && currentPage?.analysisStatus === "completed" && !isLayoutLoading && !layoutError && layoutBoxes.length === 0 && (
        <div className={styles.statusOverlay}>
          <span>표시할 레이아웃 요소가 없습니다.</span>
        </div>
      )}
    </div>
  );
};

export default LayoutPanel;
