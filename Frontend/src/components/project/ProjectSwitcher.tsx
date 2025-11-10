import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  useProject,
  type DocumentType,
  type ProjectSummary,
} from "@/contexts/ProjectContext";
import { usePages } from "@/contexts/PagesContext";
import {
  projectService,
  type ProjectResponse,
  type ProjectWithPagesResponse,
} from "@/services/projects";
import { useProjectDetails } from "@/hooks/useProjectDetails";
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
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [deletingProjectId, setDeletingProjectId] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { fetchProjectDetail } = useProjectDetails();

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

  const handleSelectProject = async (project: ProjectSummary) => {
    projectDispatch({
      type: "SET_PROJECT_INFO",
      payload: {
        projectId: project.projectId,
        projectName: project.projectName,
        documentType: project.documentType,
      },
    });
    setIsDetailLoading(true);
    setDetailError(null);
    try {
      const detail: ProjectWithPagesResponse = await fetchProjectDetail(
        Number(project.projectId)
      );
      pagesDispatch({
        type: "SET_PROJECT",
        payload: detail.project_id,
      });
      pagesDispatch({
        type: "SET_PAGES",
        payload: detail.pages.map((page) => ({
          id: page.page_id.toString(),
          pageNumber: page.page_number,
          imagePath: page.image_path,
          thumbnailPath: page.thumbnail_path || page.image_path,
          analysisStatus: page.analysis_status,
          imageWidth: page.image_width ?? undefined,
          imageHeight: page.image_height ?? undefined,
        })),
      });
      if (detail.pages.length > 0) {
        pagesDispatch({
          type: "SET_CURRENT_PAGE",
          payload: detail.pages[0].page_id.toString(),
        });
      }
      setIsDropdownOpen(false);
    } catch (error) {
      console.error("프로젝트 상세 로드 실패", error);
      setDetailError("프로젝트를 불러오지 못했습니다. 다시 시도해주세요.");
    } finally {
      setIsDetailLoading(false);
    }
  };

  const handleDeleteProject = async (
    event: React.MouseEvent,
    project: ProjectSummary
  ) => {
    event.stopPropagation();
    if (deletingProjectId || isDetailLoading) {
      return;
    }

    const confirmed = window.confirm(
      `프로젝트 "${project.projectName}"을(를) 삭제하시겠습니까?\n삭제한 프로젝트는 복구할 수 없습니다.`
    );
    if (!confirmed) {
      return;
    }

    setDeletingProjectId(project.projectId);
    try {
      await projectService.deleteProject(Number(project.projectId));
      setProjects((prev) => {
        const updated = prev.filter(
          (item) => item.projectId !== project.projectId
        );
        projectDispatch({
          type: "SET_RECENT_PROJECTS",
          payload: updated,
        });
        return updated;
      });

      if (projectState.projectId === project.projectId) {
        projectDispatch({ type: "SET_PROJECT_ID", payload: null });
        projectDispatch({ type: "SET_PROJECT_NAME", payload: null });
        pagesDispatch({ type: "SET_PROJECT", payload: null });
      }
    } catch (error) {
      console.error("프로젝트 삭제 실패", error);
      alert("프로젝트 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setDeletingProjectId(null);
    }
  };

  const hasMoreProjects = projects.length > RECENT_VISIBLE_COUNT;

  return (
    <div className={styles.wrapper} ref={dropdownRef}>
      <button
        type="button"
        className={styles.currentButton}
        onClick={() => setIsDropdownOpen((prev) => !prev)}
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
          ) : detailError ? (
            <div className={styles.emptyState}>{detailError}</div>
          ) : isDetailLoading ? (
            <div className={styles.emptyState}>프로젝트를 불러오는 중...</div>
          ) : visibleProjects.length === 0 ? (
            <div className={styles.emptyState}>생성된 프로젝트가 없습니다.</div>
          ) : (
            <>
              <ul className={styles.projectList}>
                {visibleProjects.map((project) => (
                  <li key={project.projectId}>
                    <div className={styles.projectItemWrapper}>
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
                      <button
                        type="button"
                        className={styles.deleteButton}
                        onClick={(event) => handleDeleteProject(event, project)}
                        disabled={
                          deletingProjectId === project.projectId || isDetailLoading
                        }
                        aria-label={`${project.projectName} 삭제`}
                      >
                        ×
                      </button>
                    </div>
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
