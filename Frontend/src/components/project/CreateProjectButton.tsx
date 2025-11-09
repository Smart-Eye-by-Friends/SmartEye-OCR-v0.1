import React, { useState } from "react";
import CreateProjectModal from "@/components/project/CreateProjectModal";
import { useProject, type DocumentType } from "@/contexts/ProjectContext";
import { projectService } from "@/services/projects";
import "./CreateProjectButton.css";

const CreateProjectButton: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const { dispatch } = useProject();

  const handleCreateProject = async (
    projectName: string,
    documentType: DocumentType
  ) => {
    try {
      setIsCreating(true);

      // doc_type_id: 1 = worksheet, 2 = document
      const docTypeId = documentType === "worksheet" ? 1 : 2;

      // 프로젝트 생성 API 호출
      const project = await projectService.createProject(
        projectName,
        docTypeId,
        "auto",
        1
      );

      // Context 업데이트
      dispatch({
        type: "SET_PROJECT_ID",
        payload: project.project_id.toString(),
      });
      dispatch({ type: "SET_DOCUMENT_TYPE", payload: documentType });

      // 모달 닫기
      setIsModalOpen(false);

      // 성공 메시지 (optional)
      console.log("✅ 프로젝트 생성 완료:", project);
    } catch (error) {
      console.error("❌ 프로젝트 생성 실패:", error);
      alert("프로젝트 생성에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <>
      <button
        className="create-project-btn"
        onClick={() => setIsModalOpen(true)}
        disabled={isCreating}
      >
        <span className="btn-icon">➕</span>
        <span className="btn-text">새 프로젝트</span>
      </button>

      <CreateProjectModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreateProject={handleCreateProject}
      />
    </>
  );
};

export default CreateProjectButton;
