// src/components/slider/PageSlider.tsx
import React from "react";
import MultiFileLoader from "./MultiFileLoader";
import { usePages } from "@/contexts/PagesContext";
import styles from "./PageSlider.module.css";

interface PageSliderProps {
  onClose: () => void;
}

const PageSlider: React.FC<PageSliderProps> = ({ onClose }) => {
  const { state } = usePages();

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
                onClick={() => {
                  // TODO: 페이지 선택 기능
                  console.log("페이지 선택:", page.id);
                }}
              >
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
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default PageSlider;
