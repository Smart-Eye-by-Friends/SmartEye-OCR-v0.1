import { useState, useCallback } from "react";
import { apiService } from "../services/apiService";

export const useAnalysis = () => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState("");
  const [analysisResults, setAnalysisResults] = useState(null);
  const [structuredResult, setStructuredResult] = useState(null);

  const analyzeWorksheet = useCallback(
    async ({ image, model, apiKey, mode }) => {
      setIsAnalyzing(true);
      setProgress(0);
      setStatus(
        mode === "structured"
          ? "구조화된 분석을 시작합니다..."
          : "분석을 시작합니다..."
      );

      try {
        // 프로그레스 시뮬레이션
        const progressSteps = [
          { progress: 10, status: "서버에 업로드 중..." },
          { progress: 25, status: "OCR 분석 중..." },
          { progress: 50, status: "레이아웃 분석 중..." },
          {
            progress: 75,
            status:
              mode === "structured"
                ? "구조화된 결과 생성 중..."
                : "결과 처리 중...",
          },
          { progress: 90, status: "AI 분석 중..." },
        ];

        // 점진적 프로그레스 업데이트
        let currentStep = 0;
        const progressInterval = setInterval(() => {
          if (currentStep < progressSteps.length) {
            setProgress(progressSteps[currentStep].progress);
            setStatus(progressSteps[currentStep].status);
            currentStep++;
          } else {
            clearInterval(progressInterval);
          }
        }, 500);

        // CIM 통합 분석 엔드포인트 사용
        const endpoint = "/api/document/analyze-cim";
        const response = await apiService.analyzeWorksheet({
          image,
          modelChoice: model,
          apiKey,
          endpoint,
        });

        clearInterval(progressInterval);

        console.log("=== useAnalysis 응답 처리 ===");
        console.log("원본 응답:", response);
        console.log("응답 타입:", typeof response);
        console.log("response.success:", response.success);
        console.log("===========================");

        if (response.success) {
          // CIM 통합 분석 결과 처리
          const analysisData = {
            layoutImageUrl:
              response.layoutImageUrl || response.layout_image_url,
            jsonUrl: response.jsonUrl || response.json_url,
            stats: response.stats,
            ocrResults: response.ocrResults || response.ocr_results || [],
            aiResults: response.aiResults || response.ai_results || [],
            cimData: response.cimData || response.cim_data || null, // CIM 데이터 추가
            formattedText:
              response.formattedText ||
              response.formatted_text ||
              response.structuredText ||
              response.structured_text ||
              "",
          };

          console.log("=== CIM 분석 결과 데이터 ===");
          console.log("analysisData:", analysisData);
          console.log("CIM 데이터:", analysisData.cimData);
          console.log("================================");

          setAnalysisResults(analysisData);

          // 구조화된 결과는 더 이상 별도로 관리하지 않음 (CIM으로 통합)
          setStructuredResult(null);

          setProgress(100);
          setStatus("분석 완료! 결과를 확인해보세요.");

          // 2초 후 프로그레스 숨김
          setTimeout(() => {
            setIsAnalyzing(false);
            setProgress(0);
            setStatus("");
          }, 2000);
        } else {
          throw new Error(response.error || "분석에 실패했습니다.");
        }
      } catch (error) {
        console.error("분석 오류:", error);

        let errorMessage = "분석 중 오류가 발생했습니다.";
        if (error.response?.status === 413) {
          errorMessage =
            "이미지 파일이 너무 큽니다. 10MB 이하의 파일을 사용해주세요.";
        } else if (error.response?.status === 422) {
          errorMessage =
            "지원하지 않는 이미지 형식입니다. JPG, PNG, GIF 파일을 사용해주세요.";
        } else if (error.response?.data?.detail) {
          errorMessage = error.response.data.detail;
        } else if (error.message) {
          errorMessage = error.message;
        }

        alert(errorMessage);
        setIsAnalyzing(false);
        setProgress(0);
        setStatus("");
      }
    },
    []
  );

  const reset = useCallback(() => {
    setAnalysisResults(null);
    setStructuredResult(null);
    setProgress(0);
    setStatus("");
    setIsAnalyzing(false);
  }, []);

  return {
    isAnalyzing,
    progress,
    status,
    analysisResults,
    structuredResult,
    analyzeWorksheet,
    reset,
  };
};
