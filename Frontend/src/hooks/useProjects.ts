import { useCallback, useState, useEffect } from "react";
import { projectService, type ProjectResponse } from "@/services/projects";
import type { DocumentType, ProjectSummary } from "@/contexts/ProjectContext";

const resolveDocumentType = (docTypeId?: number): DocumentType =>
  docTypeId === 2 ? "document" : "worksheet";

const mapProjectResponse = (project: ProjectResponse): ProjectSummary => ({
  projectId: project.project_id.toString(),
  projectName: project.project_name,
  documentType: resolveDocumentType(project.doc_type_id),
  createdAt: project.created_at,
});

export const useProjects = () => {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchProjects = useCallback(
    async (limit = 25, userId?: number) => {
      setIsLoading(true);
      setError(null);
      try {
        const list = await projectService.listProjects({ limit, userId });
        const mapped = list.map(mapProjectResponse);
        setProjects(mapped);
        return mapped;
      } catch (err) {
        setError(err as Error);
        throw err;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    fetchProjects().catch(() => undefined);
  }, [fetchProjects]);

  return {
    projects,
    isLoading,
    error,
    refresh: fetchProjects,
  };
};

export default useProjects;
