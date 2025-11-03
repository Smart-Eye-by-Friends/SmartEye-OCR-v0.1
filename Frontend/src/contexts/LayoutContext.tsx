import React, { createContext, useContext, useReducer } from "react";
import type { ReactNode } from "react";

export interface LayoutElement {
  id: string;
  class: string;
  confidence: number;
  bbox: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  text?: string;
}

interface LayoutState {
  elements: LayoutElement[];
  selectedElementId: string | null;
}

type LayoutAction =
  | { type: "SET_ELEMENTS"; payload: LayoutElement[] }
  | { type: "SELECT_ELEMENT"; payload: string };

const initialState: LayoutState = {
  elements: [],
  selectedElementId: null,
};

const LayoutContext = createContext<
  | {
      state: LayoutState;
      dispatch: React.Dispatch<LayoutAction>;
    }
  | undefined
>(undefined);

function layoutReducer(state: LayoutState, action: LayoutAction): LayoutState {
  switch (action.type) {
    case "SET_ELEMENTS":
      return {
        ...state,
        elements: action.payload,
      };
    case "SELECT_ELEMENT":
      return {
        ...state,
        selectedElementId: action.payload,
      };
    default:
      return state;
  }
}

export const LayoutProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(layoutReducer, initialState);

  return (
    <LayoutContext.Provider value={{ state, dispatch }}>
      {children}
    </LayoutContext.Provider>
  );
};

export const useLayout = () => {
  const context = useContext(LayoutContext);
  if (!context) {
    throw new Error("useLayout must be used within LayoutProvider");
  }
  return context;
};
