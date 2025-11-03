import React, { createContext, useContext, useReducer } from "react";
import type { ReactNode } from "react";

export interface Page {
  id: string;
  pageNumber: number;
  imagePath: string;
  thumbnailPath: string;
  analysisStatus: "pending" | "processing" | "completed" | "error";
}

interface PagesState {
  pages: Page[];
  currentPageId: string | null;
}

type PagesAction =
  | { type: "ADD_PAGE"; payload: Page }
  | { type: "SET_CURRENT_PAGE"; payload: string }
  | {
      type: "UPDATE_PAGE_STATUS";
      payload: { id: string; status: Page["analysisStatus"] };
    };

const initialState: PagesState = {
  pages: [],
  currentPageId: null,
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
      return {
        ...state,
        pages: [...state.pages, action.payload],
      };
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
