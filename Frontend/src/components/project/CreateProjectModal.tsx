import React, { useState } from "react";
import type { DocumentType } from "@/contexts/ProjectContext";
import "./CreateProjectModal.css";

interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreateProject: (projectName: string, documentType: DocumentType) => void;
}

const CreateProjectModal: React.FC<CreateProjectModalProps> = ({
  isOpen,
  onClose,
  onCreateProject,
}) => {
  const [projectName, setProjectName] = useState("");
  const [selectedDocType, setSelectedDocType] =
    useState<DocumentType>("worksheet");

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (projectName.trim()) {
      onCreateProject(projectName.trim(), selectedDocType);
      // Reset form
      setProjectName("");
      setSelectedDocType("worksheet");
    }
  };

  const handleCancel = () => {
    setProjectName("");
    setSelectedDocType("worksheet");
    onClose();
  };

  return (
    <div className="modal-overlay" onClick={handleCancel}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-title">새 프로젝트 만들기</h2>

        <form onSubmit={handleSubmit} className="project-form">
          {/* 프로젝트 이름 입력 */}
          <div className="form-group">
            <label htmlFor="project-name" className="form-label">
              프로젝트 이름:
            </label>
            <input
              type="text"
              id="project-name"
              className="form-input"
              placeholder="수학 중간고사 문제지"
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
              required
              autoFocus
            />
          </div>

          {/* 문서 타입 선택 */}
          <div className="form-group">
            <label className="form-label">문서 타입 선택:</label>

            <div className="doc-type-options">
              {/* Worksheet 옵션 */}
              <label
                className={`doc-type-card ${
                  selectedDocType === "worksheet" ? "selected" : ""
                }`}
              >
                <input
                  type="radio"
                  name="doc-type"
                  value="worksheet"
                  checked={selectedDocType === "worksheet"}
                  onChange={(e) =>
                    setSelectedDocType(e.target.value as DocumentType)
                  }
                  className="doc-type-radio"
                />
                <div className="doc-type-content">
                  <div className="doc-type-header">
                    <span className="doc-type-icon">📝</span>
                    <span className="doc-type-title">문제지 (Worksheet)</span>
                  </div>
                  <ul className="doc-type-features">
                    <li>문제 번호 기반 자동 정렬</li>
                    <li>문제별 그룹핑</li>
                    <li>21개 포맷팅 규칙 적용</li>
                  </ul>
                </div>
              </label>

              {/* Document 옵션 */}
              <label
                className={`doc-type-card ${
                  selectedDocType === "document" ? "selected" : ""
                }`}
              >
                <input
                  type="radio"
                  name="doc-type"
                  value="document"
                  checked={selectedDocType === "document"}
                  onChange={(e) =>
                    setSelectedDocType(e.target.value as DocumentType)
                  }
                  className="doc-type-radio"
                />
                <div className="doc-type-content">
                  <div className="doc-type-header">
                    <span className="doc-type-icon">📄</span>
                    <span className="doc-type-title">일반 문서 (Document)</span>
                  </div>
                  <ul className="doc-type-features">
                    <li>좌표 기반 순차 정렬</li>
                    <li>10개 포맷팅 규칙 적용</li>
                  </ul>
                </div>
              </label>
            </div>
          </div>

          {/* 버튼 그룹 */}
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-cancel"
              onClick={handleCancel}
            >
              취소
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={!projectName.trim()}
            >
              프로젝트 생성
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateProjectModal;
