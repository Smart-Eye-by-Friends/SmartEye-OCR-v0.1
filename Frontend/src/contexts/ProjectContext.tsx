import React, { createContext, useContext, useReducer } from "react";
import type { ReactNode } from "react";

export type DocumentType = "worksheet" | "document";
export type AIModel = "smarteye" | "doclayout";

export interface ProjectSummary {
  projectId: string;
  projectName: string;
  documentType: DocumentType;
  createdAt?: string;
}

interface ProjectState {
  projectId: string | null;
  projectName: string | null;
  documentType: DocumentType;
  selectedModel: AIModel;
  isAnalyzing: boolean;
  recentProjects: ProjectSummary[];
}

type ProjectAction =
  | { type: "SET_DOCUMENT_TYPE"; payload: DocumentType }
  | { type: "SET_ANALYZING"; payload: boolean }
  | { type: "SET_PROJECT_ID"; payload: string | null }
  | {
      type: "SET_PROJECT_INFO";
      payload: { projectId: string; projectName: string; documentType: DocumentType };
    }
  | { type: "SET_PROJECT_NAME"; payload: string | null }
  | { type: "SET_RECENT_PROJECTS"; payload: ProjectSummary[] };

const initialState: ProjectState = {
  projectId: null,
  projectName: null,
  documentType: "worksheet",
  selectedModel: "smarteye",
  isAnalyzing: false,
  recentProjects: [],
};

const ProjectContext = createContext<
  | {
      state: ProjectState;
      dispatch: React.Dispatch<ProjectAction>;
    }
  | undefined
>(undefined);

function projectReducer(
  state: ProjectState,
  action: ProjectAction
): ProjectState {
  switch (action.type) {
    case "SET_DOCUMENT_TYPE":
      return {
        ...state,
        documentType: action.payload,
        selectedModel:
          action.payload === "worksheet" ? "smarteye" : "doclayout",
      };
    case "SET_ANALYZING":
      return {
        ...state,
        isAnalyzing: action.payload,
      };
    case "SET_PROJECT_ID":
      return {
        ...state,
        projectId: action.payload,
        projectName: action.payload ? state.projectName : null,
      };
    case "SET_PROJECT_INFO":
      return {
        ...state,
        projectId: action.payload.projectId,
        projectName: action.payload.projectName,
        documentType: action.payload.documentType,
        selectedModel:
          action.payload.documentType === "worksheet" ? "smarteye" : "doclayout",
      };
    case "SET_PROJECT_NAME":
      return {
        ...state,
        projectName: action.payload,
      };
    case "SET_RECENT_PROJECTS":
      return {
        ...state,
        recentProjects: action.payload,
      };
    default:
      return state;
  }
}

export const ProjectProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(projectReducer, initialState);

  return (
    <ProjectContext.Provider value={{ state, dispatch }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProject = () => {
  const context = useContext(ProjectContext);
  if (!context) {
    throw new Error("useProject must be used within ProjectProvider");
  }
  return context;
};
