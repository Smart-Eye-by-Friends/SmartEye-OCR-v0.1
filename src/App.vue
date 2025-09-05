<template>
  <div class="app-container">
    <header>
      <h1>SmartEyeSsen 학습지 분석</h1>
    </header>

    <main class="main-layout">
      <!-- 왼쪽 패널: 이미지 업로드 및 설정 -->
      <div class="left-panel">
        <div class="panel-section">
          <h2>이미지 업로드</h2>
          <div class="img-container">
            <ImageLoader @image-loaded="onImageLoaded" />
          </div>
        </div>

        <div class="panel-section">
          <h2>분석 설정</h2>
          <div class="actions">
            <div class="model-selection">
              <label for="model-select">분석 모델:</label>
              <select id="model-select" v-model="selectedModel">
                <option value="SmartEyeSsen">
                  SmartEyeSsen (학습지 파인튜닝)
                </option>
                <option value="docstructbench">
                  DocStructBench (학습지 최적화)
                </option>
                <option value="doclaynet_docsynth">
                  DocLayNet-Docsynth300K (일반문서)
                </option>
                <option value="docsynth300k">
                  DocSynth300K (사전훈련모델)
                </option>
              </select>
            </div>
            <div class="api-key-input">
              <label for="api-key">OpenAI API Key (선택사항):</label>
              <input
                id="api-key"
                type="password"
                v-model="apiKey"
                placeholder="sk-..."
                title="그림과 표에 대한 AI 설명 생성용"
              />
            </div>
            
            <!-- 🆕 분석 모드 선택 -->
            <div class="analysis-mode">
              <label>분석 모드:</label>
              <div class="radio-group">
                <label>
                  <input type="radio" v-model="analysisMode" value="basic" />
                  일반 분석
                </label>
                <label>
                  <input type="radio" v-model="analysisMode" value="structured" />
                  구조화된 분석 (문제별 정리)
                </label>
              </div>
            </div>
            
            <progress v-if="showProgress" :value="progress" max="100" />
            <div class="status" v-if="showProgress">{{ status }}</div>
            <button
              @click="analyzeWorksheet"
              :disabled="!selectedImage || showProgress"
              class="analyze-btn"
            >
              {{ analysisMode === 'structured' ? '구조화된 분석 시작' : '분석 시작' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 오른쪽 패널: 결과 표시 -->
      <div class="right-panel">
        <div class="panel-section">
          <h2>분석 결과</h2>
          <div class="results-container">
            <div class="tabs">
              <button
                class="tab-button"
                :class="{ active: activeTab === 'layout' }"
                @click="activeTab = 'layout'"
              >
                레이아웃 분석
              </button>
              <button
                class="tab-button"
                :class="{ active: activeTab === 'stats' }"
                @click="activeTab = 'stats'"
              >
                분석 통계
              </button>
              <button
                class="tab-button"
                :class="{ active: activeTab === 'text' }"
                @click="activeTab = 'text'"
              >
                텍스트 편집
              </button>
              <button
                class="tab-button"
                :class="{ active: activeTab === 'ai' }"
                @click="activeTab = 'ai'"
              >
                AI 설명
              </button>
              <!-- 🆕 구조화된 결과 탭 -->
              <button
                v-if="analysisMode === 'structured' && structuredResult"
                class="tab-button"
                :class="{ active: activeTab === 'structured' }"
                @click="activeTab = 'structured'"
              >
                문제별 정리
              </button>
            </div>

            <div class="tab-content">
              <!-- 레이아웃 분석 결과 -->
              <div v-if="activeTab === 'layout'" class="tab-panel">
                <h3>레이아웃 분석 시각화</h3>
                <img
                  v-if="layoutImageUrl"
                  :src="layoutImageUrl"
                  alt="레이아웃 분석 결과"
                  class="result-image"
                />
                <p v-else class="no-result">
                  분석 결과가 없습니다. 이미지를 업로드하고 분석을 시작하세요.
                </p>
              </div>

              <!-- 분석 통계 -->
              <div v-if="activeTab === 'stats'" class="tab-panel">
                <h3>분석 결과 통계</h3>
                <div v-if="analysisStats" class="stats-content">
                  <p>
                    <strong>총 감지된 레이아웃 요소:</strong>
                    {{ analysisStats.total_layout_elements }}개
                  </p>
                  <p>
                    <strong>OCR 처리된 텍스트 블록:</strong>
                    {{ analysisStats.ocr_text_blocks }}개
                  </p>
                  <p>
                    <strong>AI 설명 생성된 이미지/표:</strong>
                    {{ analysisStats.ai_descriptions }}개
                  </p>

                  <h4>감지된 레이아웃 클래스:</h4>
                  <ul>
                    <li
                      v-for="(count, className) in analysisStats.class_counts"
                      :key="className"
                    >
                      {{ className }}: {{ count }}개
                    </li>
                  </ul>

                  <div v-if="jsonUrl" class="json-download">
                    <a :href="jsonUrl" download class="download-button">
                      분석 결과 JSON 다운로드
                    </a>
                  </div>
                </div>
                <p v-else class="no-result">분석 통계가 없습니다.</p>
              </div>

              <!-- 통합된 텍스트 편집 -->
              <div v-if="activeTab === 'text'" class="tab-panel">
                <h3>학습지 텍스트 (편집 가능)</h3>

                <div v-if="formattedText" class="text-content">
                  <div class="formatting-info">
                    <p><strong>자동 적용된 포맷팅:</strong></p>
                    <ul>
                      <li>제목 후에는 두 줄 띄기</li>
                      <li>문제번호 뒤에 점과 공백 추가</li>
                      <li>문제유형과 문제텍스트는 3칸 들여쓰기</li>
                      <li>표/수식 앞뒤로 한 줄씩 띄기</li>
                      <li>그림/표는 AI 설명으로 대체</li>
                      <li>삭제된 텍스트는 [삭제됨] 표시</li>
                    </ul>
                  </div>

                  <div class="editor-container">
                    <textarea
                      id="text-editor"
                      v-model="editableFormattedText"
                      class="tinymce-editor formatted-text"
                    ></textarea>
                  </div>

                  <div class="editor-controls">
                    <button @click="saveText" class="btn btn-primary">
                      편집 내용 저장
                    </button>
                    <button @click="resetText" class="btn btn-secondary">
                      원본으로 되돌리기
                    </button>
                    <button @click="downloadText" class="btn btn-success">
                      텍스트 다운로드
                    </button>
                    <button
                      @click="saveAsWord"
                      class="btn btn-info"
                      :disabled="isWordSaving"
                    >
                      {{
                        isWordSaving ? "워드 저장 중..." : "워드 파일로 저장"
                      }}
                    </button>
                    <button @click="copyText" class="btn btn-secondary">
                      클립보드에 복사
                    </button>
                  </div>
                </div>

                <p v-else class="no-result">
                  이미지를 업로드하고 분석을 시작하세요.
                </p>
              </div>

              <!-- AI 설명 -->
              <div v-if="activeTab === 'ai'" class="tab-panel">
                <h3>AI 생성 설명</h3>
                <div
                  v-if="aiResults && aiResults.length > 0"
                  class="ai-content"
                >
                  <div
                    v-for="(result, index) in aiResults"
                    :key="index"
                    class="description-block"
                  >
                    <h4>{{ index + 1 }}. {{ result.class_name }}</h4>
                    <p>{{ result.description }}</p>
                  </div>
                </div>
                <div v-else-if="!apiKey" class="no-result">
                  AI 설명을 생성하려면 OpenAI API 키를 입력하세요.
                </div>
                <p v-else class="no-result">AI 설명이 생성되지 않았습니다.</p>
              </div>

              <!-- 🆕 구조화된 결과 -->
              <div v-if="activeTab === 'structured'" class="tab-panel">
                <h3>문제별 구조화된 결과</h3>
                <div v-if="structuredResult" class="structured-content">
                  <!-- 문서 정보 -->
                  <div class="document-info">
                    <h4>📋 문서 정보</h4>
                    <div class="info-grid">
                      <div class="info-item">
                        <strong>총 문제 수:</strong> 
                        {{ structuredResult.document_info?.total_questions || 0 }}개
                      </div>
                      <div class="info-item">
                        <strong>레이아웃 유형:</strong> 
                        {{ structuredResult.document_info?.layout_type || '미확인' }}
                      </div>
                    </div>
                  </div>

                  <!-- 문제별 내용 -->
                  <div class="questions-list">
                    <div 
                      v-for="(question, index) in structuredResult.questions" 
                      :key="index"
                      class="question-item"
                    >
                      <div class="question-header">
                        <h4>🔸 {{ question.question_number }}</h4>
                        <span v-if="question.section" class="section-badge">
                          {{ question.section }}
                        </span>
                      </div>

                      <div class="question-content">
                        <!-- 지문 -->
                        <div v-if="question.question_content?.passage" class="content-section">
                          <h5>📖 지문</h5>
                          <p class="passage-text">{{ question.question_content.passage }}</p>
                        </div>

                        <!-- 문제 텍스트 -->
                        <div v-if="question.question_content?.main_question" class="content-section">
                          <h5>❓ 문제</h5>
                          <p class="question-text">{{ question.question_content.main_question }}</p>
                        </div>

                        <!-- 선택지 -->
                        <div v-if="question.question_content?.choices?.length > 0" class="content-section">
                          <h5>📝 선택지</h5>
                          <ul class="choices-list">
                            <li 
                              v-for="(choice, choiceIndex) in question.question_content.choices" 
                              :key="choiceIndex"
                              class="choice-item"
                            >
                              <strong>{{ choice.choice_number }}</strong> {{ choice.choice_text }}
                            </li>
                          </ul>
                        </div>

                        <!-- 이미지 설명 -->
                        <div v-if="question.question_content?.images?.length > 0" class="content-section">
                          <h5>🖼️ 이미지 설명</h5>
                          <div 
                            v-for="(image, imgIndex) in question.question_content.images" 
                            :key="imgIndex"
                            class="description-item"
                          >
                            <p>{{ image.description }}</p>
                          </div>
                        </div>

                        <!-- 표 설명 -->
                        <div v-if="question.question_content?.tables?.length > 0" class="content-section">
                          <h5>📊 표 설명</h5>
                          <div 
                            v-for="(table, tableIndex) in question.question_content.tables" 
                            :key="tableIndex"
                            class="description-item"
                          >
                            <p>{{ table.description }}</p>
                          </div>
                        </div>

                        <!-- 해설 -->
                        <div v-if="question.question_content?.explanations" class="content-section">
                          <h5>💡 해설</h5>
                          <p class="explanation-text">{{ question.question_content.explanations }}</p>
                        </div>

                        <!-- AI 분석 -->
                        <div v-if="hasAiAnalysis(question.ai_analysis)" class="content-section">
                          <h5>🤖 AI 분석</h5>
                          <div class="ai-analysis">
                            <div v-if="question.ai_analysis?.image_descriptions?.length > 0">
                              <strong>이미지 분석:</strong>
                              <ul>
                                <li v-for="(desc, descIndex) in question.ai_analysis.image_descriptions" :key="descIndex">
                                  {{ desc.description }}
                                </li>
                              </ul>
                            </div>
                            <div v-if="question.ai_analysis?.table_analysis?.length > 0">
                              <strong>표 분석:</strong>
                              <ul>
                                <li v-for="(table, tableIndex) in question.ai_analysis.table_analysis" :key="tableIndex">
                                  {{ table.description }}
                                </li>
                              </ul>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- 구조화된 텍스트 편집 -->
                  <div class="structured-text-editor">
                    <h4>📝 구조화된 텍스트 편집</h4>
                    <textarea 
                      v-model="structuredText" 
                      rows="15" 
                      class="structured-textarea"
                      placeholder="구조화된 텍스트가 여기에 표시됩니다..."
                    ></textarea>
                    <button @click="saveStructuredAsWord" class="save-word-btn">
                      📄 워드 문서로 저장
                    </button>
                  </div>
                </div>
                <div v-else class="no-result">
                  구조화된 분석 결과가 없습니다. 구조화된 분석을 먼저 실행하세요.
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, toRefs, onMounted } from "vue";
import ImageLoader from "./components/ImageLoader.vue";
import axios from "axios";

export default defineComponent({
  name: "App",
  components: {
    ImageLoader,
  },
  setup() {
    const state = reactive({
      progress: 0,
      status: "",
      showProgress: false,
      selectedImage: null as File | null,
      selectedModel: "SmartEyeSsen",
      apiKey: "",
      activeTab: "layout",
      analysisMode: "basic", // 🆕 분석 모드 ('basic' 또는 'structured')

      // 분석 결과
      layoutImageUrl: "",
      jsonUrl: "",
      analysisStats: null as any,
      ocrResults: [] as any[],
      aiResults: [] as any[],

      // 🆕 구조화된 분석 결과
      structuredResult: null as any,
      structuredText: "",
      structuredJsonUrl: "",

      // 통합된 텍스트 편집 상태
      formattedText: "",
      editableFormattedText: "",
      originalFormattedText: "",
      tinymceInitialized: false,

      // 워드 저장 상태
      isWordSaving: false,
    });

    const onImageLoaded = (imageFile: File) => {
      state.selectedImage = imageFile;
      // 이전 결과 초기화
      state.layoutImageUrl = "";
      state.jsonUrl = "";
      state.analysisStats = null;
      state.ocrResults = [];
      state.aiResults = [];
      state.formattedText = "";
      state.editableFormattedText = "";
      state.originalFormattedText = "";
    };

    const analyzeWorksheet = async () => {
      if (!state.selectedImage) {
        alert("이미지를 먼저 업로드해주세요.");
        return;
      }

      try {
        state.showProgress = true;
        state.progress = 0;
        
        // 🆕 분석 모드에 따른 상태 메시지
        const isStructured = state.analysisMode === 'structured';
        state.status = isStructured ? "구조화된 분석을 시작합니다..." : "분석을 시작합니다...";

        const formData = new FormData();
        formData.append("image", state.selectedImage);
        formData.append("model_choice", state.selectedModel);
        if (state.apiKey) {
          formData.append("api_key", state.apiKey);
        }

        state.progress = 10;
        state.status = "서버에 업로드 중...";

        // 🆕 분석 모드에 따른 엔드포인트 선택
        const endpoint = isStructured ? "/analyze-structured" : "/analyze";
        
        const response = await axios.post(
          `http://localhost:8000${endpoint}`,
          formData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
            },
            onUploadProgress: (progressEvent) => {
              if (progressEvent.total) {
                const uploadProgress = Math.round(
                  (progressEvent.loaded * 50) / progressEvent.total
                );
                state.progress = Math.min(uploadProgress, 50);
              }
            },
          }
        );

        state.progress = 60;
        state.status = isStructured ? "구조화된 결과 처리 중..." : "분석 결과 처리 중...";

        if (response.data.success) {
          // API 기본 URL
          const baseUrl = "http://localhost:8000";

          state.layoutImageUrl = baseUrl + response.data.layout_image_url;
          state.analysisStats = response.data.stats;
          
          // 🆕 구조화된 분석 결과 처리
          if (isStructured) {
            state.structuredResult = response.data.structured_result;
            state.structuredText = response.data.structured_text || "";
            state.structuredJsonUrl = baseUrl + response.data.structured_json_url;
            
            // 구조화된 분석 완료 시 해당 탭으로 이동
            state.activeTab = "structured";
          } else {
            // 기본 분석 결과 처리
            state.jsonUrl = baseUrl + response.data.json_url;
            state.ocrResults = response.data.ocr_results || [];
            state.aiResults = response.data.ai_results || [];
            state.formattedText = response.data.formatted_text || "";
            state.editableFormattedText = state.formattedText;
            state.originalFormattedText = state.formattedText;
          }
          
          state.progress = 100;
          state.status = isStructured ? "구조화된 분석이 완료되었습니다!" : "분석이 완료되었습니다!";

          // TinyMCE 초기화 (기본 분석 모드에서만)
          if (!isStructured && state.formattedText) {
            setTimeout(() => {
              initTinyMCE();
            }, 100);
          }

          setTimeout(() => {
            state.showProgress = false;
          }, 2000);
        } else {
          throw new Error(response.data.message || "분석에 실패했습니다.");
        }
      } catch (error: any) {
        console.error("분석 오류:", error);
        let errorMessage = "분석 중 오류가 발생했습니다.";

        if (error.response?.data?.detail) {
          errorMessage = error.response.data.detail;
        } else if (error.message) {
          errorMessage = error.message;
        }

        alert(errorMessage);
        state.showProgress = false;
      }
    };

    // TinyMCE 초기화
    const initTinyMCE = () => {
      if (!state.tinymceInitialized && (window as any).tinymce) {
        (window as any).tinymce.init({
          selector: "#text-editor",
          height: 400,
          menubar: false,
          plugins: [
            "advlist",
            "autolink",
            "lists",
            "link",
            "charmap",
            "anchor",
            "searchreplace",
            "visualblocks",
            "code",
            "fullscreen",
            "insertdatetime",
            "table",
            "help",
            "wordcount",
          ],
          toolbar:
            "undo redo | blocks | bold italic forecolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | removeformat | help",
          content_style:
            "body { font-family: -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif; font-size: 14px; -webkit-font-smoothing: antialiased; }",
          setup: function (editor: any) {
            editor.on("change keyup", function () {
              state.editableFormattedText = editor.getContent({
                format: "text",
              });
            });

            editor.on("init", function () {
              editor.setContent(
                state.editableFormattedText.replace(/\n/g, "<br>")
              );
            });
          },
        });
        state.tinymceInitialized = true;
      }
    };

    // 통합된 텍스트 저장
    const saveText = () => {
      state.formattedText = state.editableFormattedText;
      alert("편집 내용이 저장되었습니다!");
    };

    // 원본 텍스트로 되돌리기
    const resetText = () => {
      state.editableFormattedText = state.originalFormattedText;
      state.formattedText = state.originalFormattedText;

      if (
        (window as any).tinymce &&
        (window as any).tinymce.get("text-editor")
      ) {
        (window as any).tinymce
          .get("text-editor")
          .setContent(state.originalFormattedText.replace(/\n/g, "<br>"));
      }
    };

    // 텍스트 다운로드
    const downloadText = () => {
      const blob = new Blob([state.editableFormattedText], {
        type: "text/plain;charset=utf-8",
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `formatted_worksheet_${new Date().getTime()}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    };

    // 텍스트 클립보드 복사
    const copyText = async () => {
      try {
        await navigator.clipboard.writeText(state.editableFormattedText);
        alert("텍스트가 클립보드에 복사되었습니다!");
      } catch (error) {
        console.error("클립보드 복사 실패:", error);
        alert("클립보드 복사에 실패했습니다.");
      }
    };

    // 워드 파일로 저장
    const saveAsWord = async () => {
      if (!state.editableFormattedText.trim()) {
        alert("저장할 텍스트가 없습니다.");
        return;
      }

      state.isWordSaving = true;

      try {
        // 편집된 텍스트 가져오기
        let textContent = state.editableFormattedText;

        // TinyMCE에서 편집된 내용이 있다면 그것을 사용
        if (
          (window as any).tinymce &&
          (window as any).tinymce.get("text-editor")
        ) {
          const editor = (window as any).tinymce.get("text-editor");
          textContent = editor.getContent({ format: "text" }); // HTML 태그 제거
        }

        // FormData 생성
        const formData = new FormData();
        formData.append("text", textContent);
        formData.append("filename", "smarteye_document");

        // API 호출
        const response = await axios.post(
          "http://localhost:8000/save-as-word",
          formData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          }
        );

        if (response.data.success) {
          // 다운로드 링크 생성
          const downloadUrl = `http://localhost:8000${response.data.download_url}`;

          // 파일 다운로드 실행
          const link = document.createElement("a");
          link.href = downloadUrl;
          link.download = response.data.filename;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);

          alert(
            `워드 문서가 성공적으로 생성되어 다운로드됩니다: ${response.data.filename}`
          );
        } else {
          throw new Error(response.data.message || "워드 저장에 실패했습니다.");
        }
      } catch (error) {
        console.error("워드 저장 실패:", error);
        if (axios.isAxiosError(error) && error.response) {
          alert(
            `워드 저장 실패: ${error.response.data.detail || error.message}`
          );
        } else {
          alert("워드 파일 저장 중 오류가 발생했습니다.");
        }
      } finally {
        state.isWordSaving = false;
      }
    };

    // TinyMCE CDN 로드
    const loadTinyMCE = () => {
      if (!(window as any).tinymce) {
        const script = document.createElement("script");
        script.src = "/js/tinymce/tinymce.min.js";
        script.onload = () => {
          console.log("TinyMCE loaded");
        };
        document.head.appendChild(script);
      }
    };

    // 컴포넌트 마운트 시 TinyMCE 로드
    onMounted(() => {
      loadTinyMCE();
    });

    // 🆕 구조화된 분석을 위한 헬퍼 메소드들
    const hasAiAnalysis = (aiAnalysis: any) => {
      if (!aiAnalysis) return false;
      const hasImages = aiAnalysis.image_descriptions && aiAnalysis.image_descriptions.length > 0;
      const hasTables = aiAnalysis.table_analysis && aiAnalysis.table_analysis.length > 0;
      return hasImages || hasTables;
    };

    const saveStructuredAsWord = async () => {
      if (!state.structuredText.trim()) {
        alert("저장할 텍스트가 없습니다.");
        return;
      }

      try {
        state.isWordSaving = true;

        const formData = new FormData();
        formData.append("text", state.structuredText);
        formData.append("filename", `smarteye_structured_document`);

        const response = await axios.post(
          "http://localhost:8000/save-as-word",
          formData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          }
        );

        if (response.data.success) {
          // 다운로드 시작
          const downloadUrl = `http://localhost:8000${response.data.download_url}`;
          window.open(downloadUrl, "_blank");

          alert(`구조화된 워드 문서가 성공적으로 생성되었습니다!\n파일명: ${response.data.filename}`);
        } else {
          throw new Error(response.data.message || "워드 문서 생성 실패");
        }
      } catch (error: any) {
        console.error("워드 저장 오류:", error);
        let errorMessage = "워드 문서 저장 중 오류가 발생했습니다.";

        if (error.response?.data?.detail) {
          errorMessage = error.response.data.detail;
        } else if (error.message) {
          errorMessage = error.message;
        }

        alert(errorMessage);
      } finally {
        state.isWordSaving = false;
      }
    };

    return {
      ...toRefs(state),
      onImageLoaded,
      analyzeWorksheet,
      initTinyMCE,
      saveText,
      resetText,
      downloadText,
      copyText,
      saveAsWord,
      hasAiAnalysis, // 🆕 추가
      saveStructuredAsWord, // 🆕 추가
    };
  },
});
</script>

<style lang="less">
@import url(https://smc.org.in/fonts/manjari.css);

:root {
  --primary-color-h: 192;
  --primary-color-s: 100%;
  --primary-color-l: 41%;
  --primary-color: hsl(
    var(--primary-color-h),
    var(--primary-color-s),
    var(--primary-color-l)
  );
  --primary-color--dark: hsl(
    var(--primary-color-h),
    var(--primary-color-s),
    calc(var(--primary-color-l) - 30%)
  );
}

body {
  display: flex;
  height: 100vh;
  flex-direction: column;
  padding: 0;
  margin: 0;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

#app {
  font-family: Helvetica, "Manjari", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--primary-color--dark);
  height: 100vh;
}

header {
  background-color: var(--primary-color);
  color: #ffffff;
  padding: 16px;
  text-align: center;
  flex-shrink: 0;

  h1 {
    margin: 0;
    font-size: 1.5rem;
  }
}

.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.left-panel {
  width: 50%;
  background-color: #f8f9fa;
  border-right: 1px solid #ddd;
  overflow-y: auto;
  padding: 20px;
}

.right-panel {
  width: 50%;
  background-color: white;
  overflow-y: auto;
  padding: 20px;
}

.panel-section {
  margin-bottom: 30px;

  h2 {
    color: var(--primary-color--dark);
    margin-bottom: 15px;
    font-size: 1.2rem;
    border-bottom: 2px solid var(--primary-color);
    padding-bottom: 8px;
  }
}

.img-container {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 15px;

  .model-selection,
  .api-key-input,
  .analysis-mode {
    display: flex;
    flex-direction: column;
    gap: 5px;

    label {
      font-weight: bold;
      color: var(--primary-color--dark);
    }

    select,
    input {
      padding: 8px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
    }

    .radio-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-top: 5px;

      label {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: normal;
        cursor: pointer;

        input[type="radio"] {
          margin: 0;
        }
      }
    }
  }

  /* 🆕 분석 모드 선택 스타일 */
  .analysis-mode {
    display: flex;
    flex-direction: column;
    gap: 10px;

    label {
      font-weight: bold;
      color: var(--primary-color--dark);
    }

    .radio-group {
      display: flex;
      flex-direction: column;
      gap: 8px;

      label {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: normal;
        cursor: pointer;

        input[type="radio"] {
          margin: 0;
        }
      }
    }
  }

  progress {
    width: 100%;
    height: 8px;
  }

  .status {
    text-align: center;
    font-weight: bold;
    color: var(--primary-color);
  }

  .analyze-btn {
    padding: 12px 24px;
    font-size: 1.1rem;
    background-color: var(--primary-color);
    color: white;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    transition: background-color 0.3s;

    &:hover:not(:disabled) {
      background-color: var(--primary-color--dark);
    }

    &:disabled {
      background-color: #ccc;
      cursor: not-allowed;
    }
  }
}

.results-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tabs {
  display: flex;
  border-bottom: 2px solid #ddd;
  margin-bottom: 20px;

  .tab-button {
    padding: 12px 20px;
    background: none;
    border: none;
    cursor: pointer;
    font-size: 1rem;
    color: #666;
    border-bottom: 3px solid transparent;
    transition: all 0.3s;

    &:hover {
      color: var(--primary-color);
    }

    &.active {
      color: var(--primary-color);
      border-bottom-color: var(--primary-color);
      font-weight: bold;
    }
  }
}

.tab-content {
  flex: 1;
}

.tab-panel {
  h3 {
    color: var(--primary-color--dark);
    margin-bottom: 15px;
  }

  .result-image {
    max-width: 100%;
    height: auto;
    border: 1px solid #ddd;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  .no-result {
    text-align: center;
    color: #666;
    font-style: italic;
    padding: 40px;
    background-color: #f8f9fa;
    border-radius: 4px;
  }

  /* 🆕 구조화된 분석 결과 스타일 */
  .structured-content {
    .document-info {
      background: linear-gradient(135deg, #f8f9fa, #e9ecef);
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 25px;
      border-left: 4px solid var(--primary-color);

      h4 {
        color: var(--primary-color--dark);
        margin-bottom: 15px;
        font-size: 1.2rem;
      }

      .info-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 15px;

        .info-item {
          background: white;
          padding: 12px;
          border-radius: 6px;
          box-shadow: 0 2px 4px rgba(0,0,0,0.1);

          strong {
            color: var(--primary-color--dark);
          }
        }
      }
    }

    .questions-list {
      .question-item {
        background: white;
        border: 1px solid #e9ecef;
        border-radius: 10px;
        margin-bottom: 25px;
        overflow: hidden;
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        transition: box-shadow 0.3s ease;

        &:hover {
          box-shadow: 0 4px 12px rgba(0,0,0,0.12);
        }

        .question-header {
          background: linear-gradient(135deg, var(--primary-color), var(--primary-color--dark));
          color: white;
          padding: 15px 20px;
          display: flex;
          justify-content: space-between;
          align-items: center;

          h4 {
            margin: 0;
            font-size: 1.3rem;
          }

          .section-badge {
            background: rgba(255,255,255,0.2);
            padding: 5px 12px;
            border-radius: 15px;
            font-size: 0.9rem;
          }
        }

        .question-content {
          padding: 20px;

          .content-section {
            margin-bottom: 20px;
            
            &:last-child {
              margin-bottom: 0;
            }

            h5 {
              color: var(--primary-color--dark);
              margin-bottom: 10px;
              font-size: 1.1rem;
              display: flex;
              align-items: center;
              gap: 8px;
            }

            .passage-text, .question-text, .explanation-text {
              background: #f8f9fa;
              padding: 15px;
              border-radius: 6px;
              border-left: 3px solid var(--primary-color);
              line-height: 1.6;
            }

            .choices-list {
              background: #f8f9fa;
              padding: 15px;
              border-radius: 6px;
              margin: 0;

              .choice-item {
                padding: 8px 0;
                border-bottom: 1px solid #e9ecef;

                &:last-child {
                  border-bottom: none;
                }

                strong {
                  color: var(--primary-color);
                  margin-right: 8px;
                }
              }
            }

            .description-item {
              background: #e8f4f8;
              padding: 12px;
              border-radius: 6px;
              margin-bottom: 10px;
              border-left: 3px solid #007bff;

              &:last-child {
                margin-bottom: 0;
              }

              p {
                margin: 0;
                line-height: 1.5;
              }
            }

            .ai-analysis {
              background: #fff3cd;
              padding: 15px;
              border-radius: 6px;
              border-left: 3px solid #ffc107;

              strong {
                color: #856404;
                display: block;
                margin-bottom: 8px;
              }

              ul {
                margin: 0;
                padding-left: 20px;

                li {
                  margin-bottom: 5px;
                  line-height: 1.5;
                }
              }
            }
          }
        }
      }
    }

    .structured-text-editor {
      background: white;
      border: 1px solid #e9ecef;
      border-radius: 10px;
      padding: 20px;
      margin-top: 30px;

      h4 {
        color: var(--primary-color--dark);
        margin-bottom: 15px;
        font-size: 1.2rem;
      }

      .structured-textarea {
        width: 100%;
        border: 1px solid #ddd;
        border-radius: 6px;
        padding: 15px;
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        font-size: 14px;
        line-height: 1.6;
        resize: vertical;
        margin-bottom: 15px;

        &:focus {
          outline: none;
          border-color: var(--primary-color);
          box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.2);
        }
      }

      .save-word-btn {
        background: linear-gradient(135deg, #28a745, #20c997);
        color: white;
        border: none;
        padding: 12px 24px;
        border-radius: 6px;
        font-size: 1rem;
        cursor: pointer;
        transition: all 0.3s ease;

        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(40, 167, 69, 0.3);
        }

        &:active {
          transform: translateY(0);
        }
      }
    }
  }
}

.stats-content {
  p {
    margin-bottom: 10px;
  }

  h4 {
    color: var(--primary-color--dark);
    margin-top: 20px;
    margin-bottom: 10px;
  }

  ul {
    list-style-type: disc;
    padding-left: 20px;
  }

  .json-download {
    margin-top: 20px;

    .download-button {
      display: inline-block;
      padding: 10px 20px;
      background-color: var(--primary-color);
      color: white;
      text-decoration: none;
      border-radius: 4px;
      transition: background-color 0.3s;

      &:hover {
        background-color: var(--primary-color--dark);
      }
    }
  }
}

.ocr-content,
.ai-content {
  .text-block,
  .description-block {
    margin-bottom: 20px;
    padding: 15px;
    background-color: #f8f9fa;
    border-radius: 4px;
    border-left: 4px solid var(--primary-color);

    h4 {
      color: var(--primary-color--dark);
      margin: 0 0 10px 0;
    }

    p {
      margin: 0;
      line-height: 1.6;
      white-space: pre-wrap;
    }
  }
}

@media (max-width: 768px) {
  .main-layout {
    flex-direction: column;
  }

  .left-panel {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #ddd;
  }

  .right-panel {
    width: 100%;
  }

  .panel-section {
    margin-bottom: 20px;
  }

  .tabs {
    flex-wrap: wrap;

    .tab-button {
      flex: 1;
      min-width: 120px;
      font-size: 0.9rem;
    }
  }

  .actions {
    .model-selection,
    .api-key-input {
      select,
      input {
        font-size: 0.9rem;
      }
    }
  }
}

.editor-container {
  margin: 1rem 0;
}

.tinymce-editor {
  width: 100%;
  min-height: 300px;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.5rem;
  font-family: "Courier New", monospace;
  line-height: 1.5;
}

.editor-controls {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
  flex-wrap: wrap;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.3s;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-secondary {
  background-color: #6c757d;
  color: white;
}

.btn-success {
  background-color: #28a745;
  color: white;
}

.btn-info {
  background-color: #17a2b8;
  color: white;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn:hover:not(:disabled) {
  opacity: 0.8;
}

/* 텍스트 편집 관련 스타일 */
.text-content {
  margin-top: 1rem;
}

.formatting-info {
  background-color: #e7f3ff;
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1.5rem;
  border-left: 4px solid var(--primary-color);
}

.formatting-info p {
  margin: 0 0 0.5rem 0;
  font-weight: bold;
}

.formatting-info ul {
  margin: 0.5rem 0 0 1rem;
  font-size: 0.9rem;
}

.formatting-info li {
  margin-bottom: 0.3rem;
  color: #495057;
}

.formatted-text {
  background-color: #fffef7;
  border: 2px solid #ffc107;
  font-family: "Courier New", monospace;
  line-height: 1.8;
  font-size: 0.95rem;
}

@media (max-width: 768px) {
  .formatting-info {
    padding: 0.8rem;
    font-size: 0.85rem;
  }
}
</style>
