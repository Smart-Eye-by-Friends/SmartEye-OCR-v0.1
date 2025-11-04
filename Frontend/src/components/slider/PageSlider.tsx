// src/components/slider/PageSlider.tsx
import React from "react";
import MultiFileLoader from "./MultiFileLoader";
import { usePages } from "@/contexts/PagesContext";
import styles from "./PageSlider.module.css";

interface PageSliderProps {
  onClose: () => void;
}

const PageSlider: React.FC<PageSliderProps> = ({ onClose }) => {
  const { state, dispatch } = usePages();

  const handleSelectPage = (pageId: string) => {
    dispatch({ type: "SET_CURRENT_PAGE", payload: pageId });
  };

  return (
    <div className={styles.pageSlider}>
      <div className={styles.sliderHeader}>
        <h3>📄 페이지 미리보기 ({state.pages.length})</h3>
        <button
          className={styles.closeBtn}
          onClick={onClose}
          aria-label="슬라이더 닫기"
        >
          ⏴
        </button>
      </div>

      <div className={styles.sliderContent}>
        {/* 파일 업로드 */}
        <MultiFileLoader />

        {/* 업로드된 페이지 목록 */}
        {state.pages.length > 0 && (
          <div className={styles.thumbnailList}>
            {state.pages.map((page) => (
              <div
                key={page.id}
                className={`${styles.thumbnailItem} ${
                  state.currentPageId === page.id ? styles.active : ""
                }`}
                onClick={() => handleSelectPage(page.id)}
              >
                {/* 썸네일 이미지 */}
                <div className={styles.thumbnailImage}>
                  <img
                    src={`http://localhost:8000/${page.imagePath}`}
                    alt={`페이지 ${page.pageNumber}`}
                    loading="lazy"
                    onError={(e) => {
                      // 이미지 로드 실패 시 대체 아이콘
                      e.currentTarget.src =
                        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E%3Crect width='100' height='100' fill='%23f0f0f0'/%3E%3Ctext x='50' y='50' text-anchor='middle' dy='.3em' fill='%23999' font-size='40'%3E📄%3C/text%3E%3C/svg%3E";
                    }}
                  />
                </div>

                {/* 페이지 정보 */}
                <div className={styles.thumbnailInfo}>
                  <span className={styles.pageNumber}>
                    페이지 {page.pageNumber}
                  </span>
                  <span className={styles.pageStatus}>
                    {page.analysisStatus === "pending" && "⏳ 대기"}
                    {page.analysisStatus === "processing" && "⚙️ 분석 중"}
                    {page.analysisStatus === "completed" && "✅ 완료"}
                    {page.analysisStatus === "error" && "❌ 에러"}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 페이지 없을 때 안내 */}
        {state.pages.length === 0 && (
          <div className={styles.emptyState}>
            <p>📂 업로드된 페이지가 없습니다</p>
            <small>위의 영역을 클릭하거나 파일을 드래그하세요</small>
          </div>
        )}
      </div>
    </div>
  );
};

export default PageSlider;
