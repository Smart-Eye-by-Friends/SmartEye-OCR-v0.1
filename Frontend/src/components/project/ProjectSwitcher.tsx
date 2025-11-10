import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  useProject,
  type DocumentType,
  type ProjectSummary,
} from "@/contexts/ProjectContext";
import { usePages } from "@/contexts/PagesContext";
import { projectService, type ProjectResponse } from "@/services/projects";
import styles from "./ProjectSwitcher.module.css";

const RECENT_VISIBLE_COUNT = 3;
const DEFAULT_FETCH_LIMIT = 25;

const resolveDocumentType = (docTypeId?: number): DocumentType =>
  docTypeId === 2 ? "document" : "worksheet";

const mapProjectResponse = (project: ProjectResponse): ProjectSummary => ({
  projectId: project.project_id.toString(),
  projectName: project.project_name,
  documentType: resolveDocumentType(project.doc_type_id),
  createdAt: project.created_at,
});

const ProjectSwitcher: React.FC = () => {
  const { state: projectState, dispatch: projectDispatch } = useProject();
  const { dispatch: pagesDispatch } = usePages();
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const currentProjectName =
    projectState.projectName ||
    (projectState.projectId ? `프로젝트 #${projectState.projectId}` : "프로젝트 없음");

  useEffect(() => {
    const fetchProjects = async () => {
      setIsLoading(true);
      try {
        const list = await projectService.listProjects({
          limit: DEFAULT_FETCH_LIMIT,
        });
        const mapped = list.map(mapProjectResponse);
        setProjects(mapped);
        projectDispatch({
          type: "SET_RECENT_PROJECTS",
          payload: mapped,
        });
      } catch (error) {
        console.error("프로젝트 목록 로드 실패", error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchProjects();
  }, [projectDispatch, projectState.projectId]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsDropdownOpen(false);
      }
    };
    if (isDropdownOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isDropdownOpen]);

  const visibleProjects = useMemo(
    () =>
      showAll || projects.length <= RECENT_VISIBLE_COUNT
        ? projects
        : projects.slice(0, RECENT_VISIBLE_COUNT),
    [projects, showAll]
  );

  const handleSelectProject = (project: ProjectSummary) => {
    projectDispatch({
      type: "SET_PROJECT_INFO",
      payload: {
        projectId: project.projectId,
        projectName: project.projectName,
        documentType: project.documentType,
      },
    });
    pagesDispatch({ type: "SET_PROJECT", payload: Number(project.projectId) });
    setIsDropdownOpen(false);
  };

  const handleToggleDropdown = () => {
    setIsDropdownOpen((prev) => !prev);
  };

  const hasMoreProjects = projects.length > RECENT_VISIBLE_COUNT;

  return (
    <div className={styles.wrapper} ref={dropdownRef}>
      <button
        type="button"
        className={styles.currentButton}
        onClick={handleToggleDropdown}
      >
        <span className={styles.projectName}>{currentProjectName}</span>
        <span className={styles.caret} aria-hidden>
          {isDropdownOpen ? "▲" : "▼"}
        </span>
      </button>

      {isDropdownOpen && (
        <div className={styles.dropdown}>
          {isLoading ? (
            <div className={styles.emptyState}>불러오는 중...</div>
          ) : visibleProjects.length === 0 ? (
            <div className={styles.emptyState}>생성된 프로젝트가 없습니다.</div>
          ) : (
            <>
              <ul className={styles.projectList}>
                {visibleProjects.map((project) => (
                  <li key={project.projectId}>
                    <button
                      type="button"
                      className={styles.projectItem}
                      onClick={() => handleSelectProject(project)}
                    >
                      <span className={styles.projectTitle}>
                        {project.projectName}
                      </span>
                      <span className={styles.projectMeta}>
                        {project.documentType === "worksheet"
                          ? "문제지"
                          : "일반 문서"}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
              {hasMoreProjects && (
                <button
                  type="button"
                  className={styles.moreButton}
                  onClick={() => setShowAll((prev) => !prev)}
                >
                  {showAll ? "접기" : "+ 더보기"}
                </button>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default ProjectSwitcher;
