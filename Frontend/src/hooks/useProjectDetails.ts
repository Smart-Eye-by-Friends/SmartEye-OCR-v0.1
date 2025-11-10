import { useCallback, useState } from "react";
import { projectService } from "@/services/projects";
import type { ProjectWithPagesResponse } from "@/services/projects";

export const useProjectDetails = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchProjectDetail = useCallback(async (projectId: number) => {
    setIsLoading(true);
    setError(null);
    try {
      const detail: ProjectWithPagesResponse =
        await projectService.getProjectDetail(projectId);
      return detail;
    } catch (err) {
      setError(err as Error);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  return {
    isLoading,
    error,
    fetchProjectDetail,
  };
};

export default useProjectDetails;
