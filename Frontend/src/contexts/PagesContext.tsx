import React, {
  createContext,
  useContext,
  useReducer,
  useEffect,
  useRef,
} from "react";
import type { ReactNode } from "react";
import { projectService } from "@/services/projects";
import { analysisService } from "@/services/analysis";

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
  latestCompletedPageId: string | null;
}

type PagesAction =
  | { type: "ADD_PAGE"; payload: Page }
  | { type: "ADD_PAGES"; payload: Page[] }
  | { type: "SET_PAGES"; payload: Page[] }
  | { type: "SET_PROJECT"; payload: number | null }
  | { type: "SET_CURRENT_PAGE"; payload: string }
  | {
      type: "UPDATE_PAGE_STATUS";
      payload: { id: string; status: Page["analysisStatus"] };
    }
  | { type: "BULK_UPDATE_STATUS"; payload: Record<string, Page["analysisStatus"]> }
  | { type: "SET_LATEST_COMPLETED_PAGE"; payload: string | null };

const initialState: PagesState = {
  pages: [],
  currentPageId: null,
  currentProjectId: null,
  latestCompletedPageId: null,
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
    case "SET_PROJECT":
      return {
        ...state,
        currentProjectId: action.payload,
      };
    case "SET_PAGES":
      return {
        ...state,
        pages: [...action.payload].sort((a, b) => a.pageNumber - b.pageNumber),
      };
    case "SET_CURRENT_PAGE":
      return {
        ...state,
        currentPageId: action.payload,
      };
    case "UPDATE_PAGE_STATUS":
      const updatedPages = state.pages.map((page) =>
        page.id === action.payload.id
          ? { ...page, analysisStatus: action.payload.status }
          : page
      );
      return {
        ...state,
        pages: updatedPages,
      };
    case "SET_LATEST_COMPLETED_PAGE":
      return {
        ...state,
        latestCompletedPageId: action.payload,
      };
    default:
      return state;
  }
}

export const PagesProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(pagesReducer, initialState);
  const pollingRef = useRef<number | null>(null);
  const pollingProjectIdRef = useRef<number | null>(null);
  const isFetchingRef = useRef(false);

  const fetchPageDetail = async (pageId: string) => {
    const numericId = Number(pageId);
    if (!Number.isFinite(numericId)) return;
    try {
      await analysisService.getPageDetail(numericId, {
        includeLayout: true,
        includeText: true,
      });
    } catch (error) {
      console.error("페이지 상세 조회 실패", error);
    }
  };

  const stopPolling = () => {
    if (pollingRef.current !== null) {
      window.clearInterval(pollingRef.current);
      pollingRef.current = null;
      pollingProjectIdRef.current = null;
    }
  };

  const fetchProjectStatus = async (projectId: number) => {
    if (isFetchingRef.current) {
      return;
    }
    isFetchingRef.current = true;
    try {
      const status = await projectService.getProjectStatus(projectId);
      const statusPayload: Record<string, Page["analysisStatus"]> = {};
      const newlyCompletedIds: string[] = [];
      const previousStatusMap = new Map(
        state.pages.map((page) => [page.id, page.analysisStatus])
      );

      status.pages.forEach((pageStatus) => {
        const id = pageStatus.page_id.toString();
        statusPayload[id] =
          pageStatus.analysis_status as Page["analysisStatus"];
        const prev = previousStatusMap.get(id);
        if (
          prev !== "completed" &&
          pageStatus.analysis_status === "completed"
        ) {
          newlyCompletedIds.push(id);
        }
      });

      dispatch({ type: "BULK_UPDATE_STATUS", payload: statusPayload });

      if (newlyCompletedIds.length > 0) {
        newlyCompletedIds.forEach((id) => fetchPageDetail(id));
        dispatch({
          type: "SET_LATEST_COMPLETED_PAGE",
          payload: newlyCompletedIds[newlyCompletedIds.length - 1],
        });
      }
    } catch (error) {
      console.error("프로젝트 상태 갱신 실패", error);
    } finally {
      isFetchingRef.current = false;
    }
  };

  const startPolling = (projectId: number) => {
    if (
      pollingRef.current !== null &&
      pollingProjectIdRef.current === projectId
    ) {
      return;
    }
    stopPolling();
    pollingProjectIdRef.current = projectId;
    const poll = () => fetchProjectStatus(projectId);
    poll();
    pollingRef.current = window.setInterval(poll, 4000);
  };

  useEffect(() => {
    const projectId = state.currentProjectId;
    if (!projectId) {
      stopPolling();
      return;
    }
    const hasActivePages = state.pages.some((page) =>
      ["pending", "processing"].includes(page.analysisStatus)
    );
    if (hasActivePages) {
      startPolling(projectId);
    } else {
      stopPolling();
    }
  }, [state.currentProjectId, state.pages]);

  useEffect(() => {
    return () => stopPolling();
  }, []);

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
    case "BULK_UPDATE_STATUS":
      return {
        ...state,
        pages: state.pages.map((page) =>
          action.payload[page.id]
            ? { ...page, analysisStatus: action.payload[page.id] }
            : page
        ),
      };
