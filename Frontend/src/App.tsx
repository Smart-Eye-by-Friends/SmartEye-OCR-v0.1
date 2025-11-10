// src/App.tsx
import React from "react";
import MainLayout from "./components/layout/MainLayout";
import CreateProjectButton from "./components/project/CreateProjectButton";
import ProjectSwitcher from "./components/project/ProjectSwitcher";
import { ProjectProvider } from "@/contexts/ProjectContext";
import { PagesProvider } from "@/contexts/PagesContext";
import { LayoutProvider } from "@/contexts/LayoutContext";
import "./styles/variables.css";
import "./styles/main.css";
import "./styles/responsive.css";

const App: React.FC = () => {
  return (
    <ProjectProvider>
      <PagesProvider>
        <LayoutProvider>
          <div className="app">
            <header className="app-header">
              <div className="header-content">
                <div className="header-text">
                  <h1>🔍 Smart Eye 학습지 분석</h1>
                  <p>AI 기반 학습지 OCR 및 구조 분석 시스템</p>
                </div>
                <div className="header-actions">
                  <ProjectSwitcher />
                  <CreateProjectButton />
                </div>
              </div>
            </header>
            <MainLayout />
          </div>
        </LayoutProvider>
      </PagesProvider>
    </ProjectProvider>
  );
};

export default App;
