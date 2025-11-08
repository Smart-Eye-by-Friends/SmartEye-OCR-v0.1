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
  const [selectedClasses, setSelectedClasses] = useState<Set<string> | null>(null);
  const overlayControlsRef = useRef<HTMLDivElement>(null);
  const controlsInitializedRef = useRef(false);
  const dragOffsetRef = useRef({ x: 0, y: 0 });
  const [controlsPosition, setControlsPosition] = useState({ x: 20, y: 80 });
  const [controlsCollapsed, setControlsCollapsed] = useState(false);
  const [isDraggingControls, setIsDraggingControls] = useState(false);
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
  }, [currentPage, state.latestCompletedPageId]);

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
    if (selectedClasses === null) {
      setSelectedClasses(new Set());
    } else {
      setSelectedClasses(null);
    }
  };

  const clampControlsPosition = (x: number, y: number) => {
    const padding = 12;
    const panelWidth = panelSize.width || 0;
    const panelHeight = panelSize.height || 0;
    const controlsWidth = overlayControlsRef.current?.offsetWidth || 260;
    const controlsHeight = overlayControlsRef.current?.offsetHeight || 200;

    const maxX =
      panelWidth > 0
        ? Math.max(padding, panelWidth - controlsWidth - padding)
        : x;
    const maxY =
      panelHeight > 0
        ? Math.max(padding, panelHeight - controlsHeight - padding)
        : y;

    return {
      x: Math.min(Math.max(x, padding), maxX),
      y: Math.min(Math.max(y, padding), maxY),
    };
  };

  useEffect(() => {
    if (panelSize.width === 0) {
      return;
    }
    if (!controlsInitializedRef.current) {
      const defaultWidth = overlayControlsRef.current?.offsetWidth || 260;
      const initialX = Math.max(panelSize.width - defaultWidth - 20, 20);
      setControlsPosition((prev) => ({ x: initialX, y: prev.y }));
      controlsInitializedRef.current = true;
    } else {
      setControlsPosition((prev) => {
        const next = clampControlsPosition(prev.x, prev.y);
        if (next.x === prev.x && next.y === prev.y) {
          return prev;
        }
        return next;
      });
    }
  }, [panelSize.width, panelSize.height]);

  const handleControlsPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    dragOffsetRef.current = {
      x: event.clientX - controlsPosition.x,
      y: event.clientY - controlsPosition.y,
    };
    setIsDraggingControls(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleControlsPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingControls) return;
    event.preventDefault();
    const nextPosition = clampControlsPosition(
      event.clientX - dragOffsetRef.current.x,
      event.clientY - dragOffsetRef.current.y
    );
    setControlsPosition(nextPosition);
  };

  const stopControlsDrag = (event?: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingControls) return;
    event?.preventDefault();
    setIsDraggingControls(false);
    if (event) {
      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch {
        // ignore capture errors
      }
    }
  };

  const hasDisplaySize =
    imageDisplaySize.width > 0 && imageDisplaySize.height > 0
      ? imageDisplaySize
      : undefined;

  const statusMessage = useMemo(() => {
    if (!currentImage) {
      return "이미지를 선택해주세요.";
    }
    if (isLayoutLoading) {
      return "레이아웃을 불러오는 중...";
    }
    if (layoutError) {
      return layoutError;
    }
    if (currentPage?.analysisStatus !== "completed") {
      return "분석이 완료되면 레이아웃 결과가 표시됩니다.";
    }
    if (layoutBoxes.length === 0) {
      return "표시할 레이아웃 요소가 없습니다.";
    }
    return null;
  }, [
    currentImage,
    currentPage?.analysisStatus,
    isLayoutLoading,
    layoutError,
    layoutBoxes.length,
  ]);

  const overlayControlsClassName = [
    styles.overlayControls,
    controlsCollapsed ? styles.collapsed : "",
    isDraggingControls ? styles.dragging : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      {/* 오버레이 컨트롤 UI */}
      {layoutBoxes.length > 0 && (
        <div
          ref={overlayControlsRef}
          className={overlayControlsClassName}
          style={{ top: controlsPosition.y, left: controlsPosition.x }}
        >
          <div
            className={styles.controlsHeader}
            onPointerDown={handleControlsPointerDown}
            onPointerMove={handleControlsPointerMove}
            onPointerUp={stopControlsDrag}
            onPointerLeave={stopControlsDrag}
          >
            <span className={styles.headerTitle}>레이아웃 오버레이</span>
            <div className={styles.headerButtons}>
              <button
                type="button"
                className={styles.iconButton}
                onClick={(event) => {
                  event.stopPropagation();
                  setOverlayVisible((prev) => !prev);
                }}
                onPointerDown={(event) => event.stopPropagation()}
                aria-label={overlayVisible ? "오버레이 숨기기" : "오버레이 보이기"}
              >
                {overlayVisible ? "👁‍🗙" : "👁"}
              </button>
              <button
                type="button"
                className={styles.iconButton}
                onClick={(event) => {
                  event.stopPropagation();
                  setControlsCollapsed((prev) => !prev);
                }}
                onPointerDown={(event) => event.stopPropagation()}
                aria-label={controlsCollapsed ? "필터 패널 펼치기" : "필터 패널 접기"}
              >
                {controlsCollapsed ? "➕" : "➖"}
              </button>
            </div>
          </div>

          {!controlsCollapsed && (
            <div className={styles.controlsBody}>
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
                      {selectedClasses === null ? "전체 해제" : "전체 선택"}
                    </button>
                  </div>

                  {availableClasses.map((cls) => (
                    <label key={cls} className={styles.filterItem}>
                      <input
                        type="checkbox"
                        checked={
                          selectedClasses === null || selectedClasses.has(cls)
                        }
                        onChange={(e) => {
                          if (selectedClasses === null) {
                            const initial = new Set(availableClasses);
                            if (e.target.checked) {
                              return;
                            }
                            initial.delete(cls);
                            setSelectedClasses(initial);
                            return;
                          }

                          const newSet = new Set(selectedClasses);
                          if (e.target.checked) {
                            newSet.add(cls);
                            if (newSet.size === availableClasses.length) {
                              setSelectedClasses(null);
                            } else {
                              setSelectedClasses(newSet);
                            }
                          } else {
                            newSet.delete(cls);
                            setSelectedClasses(newSet);
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
              visibleClasses={selectedClasses}
              onBoxClick={handleBoxClick}
              onBoxHover={handleBoxHover}
            />
          ) : null
        }
      />
      {currentPage?.analysisStatus === "completed" && isLayoutLoading && (
        <div className={styles.loadingOverlay}>
          <span>레이아웃 데이터를 불러오는 중...</span>
        </div>
      )}

      {statusMessage && (
        <div className={styles.statusToast}>
          <span>{statusMessage}</span>
        </div>
      )}
    </div>
  );
};

export default LayoutPanel;
