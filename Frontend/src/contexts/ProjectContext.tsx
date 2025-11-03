import React, { createContext, useContext, useReducer } from "react";
import type { ReactNode } from "react";

export type DocumentType = "worksheet" | "document";
export type AIModel = "smarteye" | "doclayout";

interface ProjectState {
  projectId: string | null;
  documentType: DocumentType;
  selectedModel: AIModel;
  isAnalyzing: boolean;
}

type ProjectAction =
  | { type: "SET_DOCUMENT_TYPE"; payload: DocumentType }
  | { type: "SET_ANALYZING"; payload: boolean }
  | { type: "SET_PROJECT_ID"; payload: string };

const initialState: ProjectState = {
  projectId: null,
  documentType: "worksheet",
  selectedModel: "smarteye",
  isAnalyzing: false,
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
