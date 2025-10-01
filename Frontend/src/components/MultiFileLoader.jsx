import React, { useState, useRef } from "react";

const MultiFileLoader = ({ onFilesLoad, maxFiles = 50 }) => {
  const [isDragging, setIsDragging] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [processStatus, setProcessStatus] = useState("");
  const fileInputRef = useRef(null);

  const processFiles = async (files) => {
    setIsProcessing(true);
    const processedImages = [];

    try {
      for (
        let i = 0;
        i < files.length && processedImages.length < maxFiles;
        i++
      ) {
        const file = files[i];
        setProcessStatus(`처리 중: ${file.name} (${i + 1}/${files.length})`);

        if (file.type.startsWith("image/")) {
          // 이미지 파일 처리
          const preview = URL.createObjectURL(file);
          processedImages.push({
            id: `${file.name}-${Date.now()}-${i}`,
            name: file.name,
            type: "image",
            file,
            preview,
            pageNumber: processedImages.length + 1,
          });
        } else if (file.type === "application/pdf") {
          // PDF 파일 처리
          try {
            const pdfImages = await processPdfFile(file);
            processedImages.push(...pdfImages);
          } catch (error) {
            console.error(`PDF 파일 처리 실패 (${file.name}):`, error);
            alert(
              `PDF 파일 "${file.name}" 처리 중 오류가 발생했습니다. 이 파일을 건너뜁니다.`
            );
          }
        }
      }

      onFilesLoad(processedImages);
    } catch (error) {
      console.error("파일 처리 중 오류:", error);
      alert("파일 처리 중 오류가 발생했습니다: " + error.message);
    } finally {
      setIsProcessing(false);
      setProcessStatus("");
    }
  };

  // PDF.js를 동적으로 로드하는 함수
  const loadPdfJs = async () => {
    try {
      const pdfjsLib = await import("pdfjs-dist/webpack");
      // Worker 설정
      if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`;
      }
      return pdfjsLib;
    } catch (error) {
      console.error("PDF.js 로드 실패:", error);
      throw new Error("PDF 처리 라이브러리를 로드할 수 없습니다.");
    }
  };

  const processPdfFile = async (file) => {
    const pdfjsLib = await loadPdfJs();
    const arrayBuffer = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
    const pdfImages = [];

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum);
      const viewport = page.getViewport({ scale: 1.5 });

      const canvas = document.createElement("canvas");
      const context = canvas.getContext("2d");
      canvas.height = viewport.height;
      canvas.width = viewport.width;

      await page.render({
        canvasContext: context,
        viewport: viewport,
      }).promise;

      const imageBlob = await new Promise((resolve) => {
        canvas.toBlob(resolve, "image/png");
      });

      const preview = URL.createObjectURL(imageBlob);

      pdfImages.push({
        id: `${file.name}-page-${pageNum}-${Date.now()}`,
        name: `${file.name} (페이지 ${pageNum})`,
        type: "pdf-page",
        file: imageBlob,
        preview,
        pageNumber: pageNum,
        originalPdf: file.name,
      });
    }

    return pdfImages;
  };

  const handleFileSelect = async (files) => {
    const fileArray = Array.from(files);
    const validFiles = fileArray.filter(
      (file) =>
        file.type.startsWith("image/") || file.type === "application/pdf"
    );

    if (validFiles.length === 0) {
      alert("이미지 파일 또는 PDF 파일만 업로드 가능합니다.");
      return;
    }

    if (validFiles.length > maxFiles) {
      alert(`최대 ${maxFiles}개의 파일만 업로드할 수 있습니다.`);
      return;
    }

    await processFiles(validFiles);
  };

  const handleFileChange = (e) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      handleFileSelect(files);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    if (!e.currentTarget.contains(e.relatedTarget)) {
      setIsDragging(false);
    }
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    setIsDragging(false);

    const files = e.dataTransfer.files;
    if (files.length > 0) {
      await handleFileSelect(files);
    }
  };

  const handleClick = () => {
    if (!isProcessing) {
      fileInputRef.current?.click();
    }
  };

  return (
    <div className="multi-file-loader">
      <div
        className={`upload-area ${isDragging ? "dragging" : ""} ${
          isProcessing ? "processing" : ""
        }`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={handleClick}
      >
        {isProcessing ? (
          <div className="processing-state">
            <div className="loading-spinner"></div>
            <h3>파일 처리 중...</h3>
            <p>{processStatus}</p>
          </div>
        ) : (
          <div className="upload-placeholder">
            <div className="upload-icon">📁</div>
            <h3>파일 업로드</h3>
            <p>이미지 또는 PDF 파일을 여기에 드래그하거나 클릭하여 업로드</p>
            <div className="upload-hints">
              <span className="hint">✓ 이미지: JPG, PNG, GIF, WebP</span>
              <span className="hint">✓ 문서: PDF</span>
              <span className="hint">✓ 다중 파일 지원</span>
              <span className="hint">✓ 최대 {maxFiles}개 파일</span>
            </div>
          </div>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,.pdf"
        onChange={handleFileChange}
        style={{ display: "none" }}
        disabled={isProcessing}
      />
    </div>
  );
};

export default MultiFileLoader;
