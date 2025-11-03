// src/hooks/useResponsive.ts
import { useState, useEffect } from "react";

export type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl";

const getBreakpoint = (width: number): Breakpoint => {
  if (width < 1366) return "xs"; // 1280px 이하 (최소 화면)
  if (width < 1600) return "sm"; // 1366px ~ 1599px (노트북 최소)
  if (width < 1920) return "md"; // 1600px ~ 1919px (노트북 일반)
  if (width < 2560) return "lg"; // 1920px ~ 2559px (FHD 모니터)
  return "xl"; // 2560px 이상 (QHD+)
};

interface UseResponsiveReturn {
  screenWidth: number;
  screenHeight: number;
  breakpoint: Breakpoint;
}

export const useResponsive = (): UseResponsiveReturn => {
  const [screenWidth, setScreenWidth] = useState(window.innerWidth);
  const [screenHeight, setScreenHeight] = useState(window.innerHeight);
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(
    getBreakpoint(window.innerWidth)
  );

  useEffect(() => {
    const handleResize = () => {
      const width = window.innerWidth;
      const height = window.innerHeight;

      setScreenWidth(width);
      setScreenHeight(height);
      setBreakpoint(getBreakpoint(width));
    };

    // resize 이벤트 리스너 등록
    window.addEventListener("resize", handleResize);

    // cleanup
    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  return {
    screenWidth,
    screenHeight,
    breakpoint,
  };
};
