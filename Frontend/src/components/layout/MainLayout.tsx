// src/components/layout/MainLayout.tsx
import React, { useEffect } from "react";
import Sidebar from "../sidebar/Sidebar";
import PageSlider from "../slider/PageSlider";
import SliderRestoreButton from "../slider/SliderRestoreButton";
import LayoutPanel from "../viewer/LayoutPanel";
import EditorPanel from "../editor/EditorPanel";
import { useGridLayout } from "../../hooks/useGridLayout";
import { useResponsive } from "../../hooks/useResponsive";
import { usePages } from "@/contexts/PagesContext";
import "../../styles/grid.css";

const MainLayout: React.FC = () => {
  const { isSliderCollapsed, closeSlider, openSlider } = useGridLayout();
  const { screenWidth, screenHeight, breakpoint } = useResponsive();
  const { state } = usePages();

  // 반응형 감지 로그
  useEffect(() => {
    console.log(
      `📐 Screen: ${screenWidth}x${screenHeight}px, Breakpoint: ${breakpoint}`
    );
  }, [screenWidth, screenHeight, breakpoint]);

  return (
    <div
      className={`main-layout ${isSliderCollapsed ? "slider-collapsed" : ""}`}
    >
      {/* Sidebar */}
      <div className="sidebar">
        <Sidebar />
      </div>

      {/* Page Slider */}
      {!isSliderCollapsed && (
        <div className="page-slider">
          <PageSlider onClose={closeSlider} />
        </div>
      )}

      {/* Restore Button */}
      {isSliderCollapsed && (
        <div className="slider-restore-btn">
          <SliderRestoreButton
            onClick={openSlider}
            pageCount={state.pages.length}
          />
        </div>
      )}

      {/* Layout Panel */}
      <div className="layout-panel">
        <LayoutPanel />
      </div>

      {/* Editor Panel */}
      <div className="editor-panel">
        <EditorPanel />
      </div>
    </div>
  );
};

export default MainLayout;
