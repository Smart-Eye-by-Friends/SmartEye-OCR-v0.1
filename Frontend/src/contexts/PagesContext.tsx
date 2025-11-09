import React, { createContext, useContext, useReducer } from "react";
import type { ReactNode } from "react";

export interface Page {
  id: string;
  pageNumber: number;
  imagePath: string;
  thumbnailPath: string;
  analysisStatus: "pending" | "processing" | "completed" | "error";
  imageWidth?: number;
  imageHeight?: number;
}

interface PagesState {
  pages: Page[];
  currentPageId: string | null;
  currentProjectId: number | null;
}

type PagesAction =
  | { type: "ADD_PAGE"; payload: Page }
  | { type: "ADD_PAGES"; payload: Page[] }
  | { type: "SET_PROJECT"; payload: number | null }
  | { type: "SET_CURRENT_PAGE"; payload: string }
  | {
      type: "UPDATE_PAGE_STATUS";
      payload: { id: string; status: Page["analysisStatus"] };
    };

const initialState: PagesState = {
  pages: [],
  currentPageId: null,
  currentProjectId: null,
};

const PagesContext = createContext<
  | {
      state: PagesState;
      dispatch: React.Dispatch<PagesAction>;
    }
  | undefined
>(undefined);

function pagesReducer(state: PagesState, action: PagesAction): PagesState {
  switch (action.type) {
    case "ADD_PAGE":
      if (state.pages.some((page) => page.id === action.payload.id)) {
        return state;
      }
      return {
        ...state,
        pages: [...state.pages, action.payload].sort(
          (a, b) => a.pageNumber - b.pageNumber
        ),
      };
    case "ADD_PAGES":
      const existingIds = new Set(state.pages.map((page) => page.id));
      const mergedPages = [
        ...state.pages,
        ...action.payload.filter((page) => !existingIds.has(page.id)),
      ];
      mergedPages.sort((a, b) => a.pageNumber - b.pageNumber);
      return {
        ...state,
        pages: mergedPages,
      };
    case "SET_PROJECT": {
      const hasChanged = state.currentProjectId !== action.payload;
      return {
        ...state,
        currentProjectId: action.payload,
        pages: hasChanged ? [] : state.pages,
        currentPageId: hasChanged ? null : state.currentPageId,
      };
    }
    case "SET_CURRENT_PAGE":
      return {
        ...state,
        currentPageId: action.payload,
      };
    case "UPDATE_PAGE_STATUS":
      return {
        ...state,
        pages: state.pages.map((page) =>
          page.id === action.payload.id
            ? { ...page, analysisStatus: action.payload.status }
            : page
        ),
      };
    default:
      return state;
  }
}

export const PagesProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(pagesReducer, initialState);

  return (
    <PagesContext.Provider value={{ state, dispatch }}>
      {children}
    </PagesContext.Provider>
  );
};

export const usePages = () => {
  const context = useContext(PagesContext);
  if (!context) {
    throw new Error("usePages must be used within PagesProvider");
  }
  return context;
};
