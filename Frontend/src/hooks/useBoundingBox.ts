// src/hooks/useBoundingBox.ts
import { useCallback } from "react";
import type { RefObject } from "react";

export const useBoundingBox = (editorRef: RefObject<HTMLElement>) => {
  const scrollToEditor = useCallback(
    (boxId: string) => {
      if (!editorRef.current) return;

      const element = editorRef.current.querySelector(
        `[data-bbox-id="${boxId}"]`
      );

      if (element) {
        element.scrollIntoView({
          behavior: "smooth",
          block: "center",
        });

        // 하이라이트 효과
        element.classList.add("highlight");
        setTimeout(() => {
          element.classList.remove("highlight");
        }, 2000);
      }
    },
    [editorRef]
  );

  const getTooltipInfo = useCallback((box: any) => {
    return {
      title: box.class,
      confidence: `${Math.round(box.confidence * 100)}%`,
      text: box.text?.substring(0, 50) + (box.text?.length > 50 ? "..." : ""),
      position: box.coordinates,
    };
  }, []);

  return {
    scrollToEditor,
    getTooltipInfo,
  };
};
