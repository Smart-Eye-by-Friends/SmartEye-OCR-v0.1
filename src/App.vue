<template>
  <header>
    <h1>📚 SmartEyeSsen 학습지 분석 시스템</h1>
  </header>
  <main class="container">
    <div class="img-container">
      <ImageLoader @image-loaded="onImageLoaded" />
    </div>
    <div class="actions">
      <div class="model-selection">
        <label for="model-select">분석 모델:</label>
        <select id="model-select" v-model="selectedModel">
          <option value="SmartEyeSsen">SmartEyeSsen (학습지 파인튜닝)</option>
          <option value="docstructbench">DocStructBench (학습지 최적화)</option>
          <option value="doclaynet_docsynth">DocLayNet-Docsynth300K (일반문서)</option>
          <option value="docsynth300k">DocSynth300K (사전훈련모델)</option>
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
      <progress v-if="showProgress" :value="progress" max="100" />
      <div class="status" v-if="showProgress">{{ status }}</div>
      <button @click="analyzeWorksheet" :disabled="!selectedImage || showProgress">
        🚀 분석 시작
      </button>
    </div>
    <div class="results-container">
      <div class="tabs">
        <button 
          class="tab-button" 
          :class="{ active: activeTab === 'layout' }"
          @click="activeTab = 'layout'"
        >
          🎯 레이아웃 분석
        </button>
        <button 
          class="tab-button" 
          :class="{ active: activeTab === 'text' }"
          @click="activeTab = 'text'"
        >
          📄 CIM 결과
        </button>
        <button 
          class="tab-button" 
          :class="{ active: activeTab === 'stats' }"
          @click="activeTab = 'stats'"
        >
          📊 분석 통계
        </button>
        <button 
          class="tab-button" 
          :class="{ active: activeTab === 'ocr' }"
          @click="activeTab = 'ocr'"
        >
          📝 OCR 텍스트
        </button>
        <button 
          class="tab-button" 
          :class="{ active: activeTab === 'ai' }"
          @click="activeTab = 'ai'"
        >
          🤖 AI 설명
        </button>
      </div>

      <div class="tab-content">
        <!-- 레이아웃 분석 결과 -->
        <div v-if="activeTab === 'layout'" class="tab-panel">
          <h3>레이아웃 분석 시각화</h3>
          <img v-if="layoutImageUrl" :src="layoutImageUrl" alt="레이아웃 분석 결과" class="result-image" />
          <p v-else class="no-result">분석 결과가 없습니다. 이미지를 업로드하고 분석을 시작하세요.</p>
        </div>

        <!-- CIM 결과 -->
        <div v-if="activeTab === 'text'" class="tab-panel">
          <h3>CIM 변환 결과 (텍스트/설명)</h3>
          <img v-if="textImageUrl" :src="textImageUrl" alt="CIM 변환 결과" class="result-image" />
          <p v-else class="no-result">분석 결과가 없습니다.</p>
        </div>

        <!-- 분석 통계 -->
        <div v-if="activeTab === 'stats'" class="tab-panel">
          <h3>분석 결과 통계</h3>
          <div v-if="analysisStats" class="stats-content">
            <p><strong>총 감지된 레이아웃 요소:</strong> {{ analysisStats.total_layout_elements }}개</p>
            <p><strong>OCR 처리된 텍스트 블록:</strong> {{ analysisStats.ocr_text_blocks }}개</p>
            <p><strong>AI 설명 생성된 이미지/표:</strong> {{ analysisStats.ai_descriptions }}개</p>
            
            <h4>감지된 레이아웃 클래스:</h4>
            <ul>
              <li v-for="(count, className) in analysisStats.class_counts" :key="className">
                {{ className }}: {{ count }}개
              </li>
            </ul>

            <div v-if="jsonUrl" class="json-download">
              <a :href="jsonUrl" download class="download-button">
                📄 분석 결과 JSON 다운로드
              </a>
            </div>
          </div>
          <p v-else class="no-result">분석 통계가 없습니다.</p>
        </div>

        <!-- OCR 텍스트 -->
        <div v-if="activeTab === 'ocr'" class="tab-panel">
          <h3>추출된 텍스트 (편집 가능)</h3>
          <div v-if="combinedOcrText && combinedOcrText.length > 0" class="ocr-content">
            <div class="editor-container">
              <textarea 
                id="ocr-editor"
                v-model="editableOcrText" 
                class="tinymce-editor"
              ></textarea>
            </div>
            
            <!-- 편집된 텍스트 저장 버튼 -->
            <div class="editor-controls">
              <button @click="saveEditedText" class="btn btn-primary">
                💾 편집 내용 저장
              </button>
              <button @click="resetOcrText" class="btn btn-secondary">
                🔄 원본으로 되돌리기
              </button>
              <button @click="downloadEditedText" class="btn btn-success">
                📄 텍스트 파일로 다운로드
              </button>
            </div>
          </div>
          <p v-else class="no-result">추출된 텍스트가 없습니다.</p>
        </div>

        <!-- AI 설명 -->
        <div v-if="activeTab === 'ai'" class="tab-panel">
          <h3>AI 생성 설명</h3>
          <div v-if="aiResults && aiResults.length > 0" class="ai-content">
            <div v-for="(result, index) in aiResults" :key="index" class="description-block">
              <h4>{{ index + 1 }}. {{ result.class_name }}</h4>
              <p>{{ result.description }}</p>
            </div>
          </div>
          <div v-else-if="!apiKey" class="no-result">
            AI 설명을 생성하려면 OpenAI API 키를 입력하세요.
          </div>
          <p v-else class="no-result">AI 설명이 생성되지 않았습니다.</p>
        </div>
      </div>
    </div>
  </main>
</template>

<script lang="ts">
import { defineComponent, reactive, toRefs } from "vue";
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
      
      // 분석 결과
      layoutImageUrl: "",
      textImageUrl: "",
      jsonUrl: "",
      analysisStats: null as any,
      ocrResults: [] as any[],
      aiResults: [] as any[],
      
      // TinyMCE 에디터용
      combinedOcrText: "",
      editableOcrText: "",
      originalOcrText: "",
      tinymceInitialized: false,
    });

    const onImageLoaded = (imageFile: File) => {
      state.selectedImage = imageFile;
      // 이전 결과 초기화
      state.layoutImageUrl = "";
      state.textImageUrl = "";
      state.jsonUrl = "";
      state.analysisStats = null;
      state.ocrResults = [];
      state.aiResults = [];
      state.combinedOcrText = "";
      state.editableOcrText = "";
      state.originalOcrText = "";
    };

    const analyzeWorksheet = async () => {
      if (!state.selectedImage) {
        alert("이미지를 먼저 업로드해주세요.");
        return;
      }

      try {
        state.showProgress = true;
        state.progress = 0;
        state.status = "분석을 시작합니다...";

        const formData = new FormData();
        formData.append('image', state.selectedImage);
        formData.append('model_choice', state.selectedModel);
        if (state.apiKey) {
          formData.append('api_key', state.apiKey);
        }

        state.progress = 10;
        state.status = "서버에 업로드 중...";

        const response = await axios.post('http://localhost:8000/analyze', formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
          onUploadProgress: (progressEvent) => {
            if (progressEvent.total) {
              const uploadProgress = Math.round((progressEvent.loaded * 50) / progressEvent.total);
              state.progress = Math.min(uploadProgress, 50);
            }
          },
        });

        state.progress = 60;
        state.status = "분석 결과 처리 중...";

        if (response.data.success) {
          // API 기본 URL
          const baseUrl = 'http://localhost:8000';
          
          state.layoutImageUrl = baseUrl + response.data.layout_image_url;
          state.textImageUrl = baseUrl + response.data.text_image_url;
          state.jsonUrl = baseUrl + response.data.json_url;
          state.analysisStats = response.data.stats;
          state.ocrResults = response.data.ocr_results;
          state.aiResults = response.data.ai_results;
          
          // OCR 텍스트 통합 (TinyMCE용)
          state.combinedOcrText = response.data.ocr_text || "";
          state.originalOcrText = state.combinedOcrText;
          state.editableOcrText = state.combinedOcrText;

          state.progress = 100;
          state.status = "분석 완료!";
          
          // 자동으로 레이아웃 분석 탭으로 이동
          state.activeTab = "layout";
          
          // TinyMCE 초기화 (OCR 텍스트가 있는 경우)
          if (state.combinedOcrText) {
            setTimeout(() => {
              initTinyMCE();
            }, 100);
          }
          
          setTimeout(() => {
            state.showProgress = false;
          }, 2000);
        } else {
          throw new Error("분석 실패");
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
          selector: '#ocr-editor',
          height: 400,
          menubar: false,
          plugins: [
            'advlist', 'autolink', 'lists', 'link', 'charmap',
            'anchor', 'searchreplace', 'visualblocks', 'code', 'fullscreen',
            'insertdatetime', 'table', 'help', 'wordcount'
          ],
          toolbar: 'undo redo | blocks | bold italic forecolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | removeformat | help',
          content_style: 'body { font-family: -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif; font-size: 14px; -webkit-font-smoothing: antialiased; }',
          setup: function (editor: any) {
            editor.on('change keyup', function () {
              state.editableOcrText = editor.getContent({ format: 'text' });
            });
            
            editor.on('init', function () {
              editor.setContent(state.editableOcrText.replace(/\n/g, '<br>'));
            });
          }
        });
        state.tinymceInitialized = true;
      }
    };

    // 편집된 텍스트 저장
    const saveEditedText = () => {
      state.combinedOcrText = state.editableOcrText;
      alert('편집 내용이 저장되었습니다!');
    };

    // 원본 텍스트로 되돌리기
    const resetOcrText = () => {
      state.editableOcrText = state.originalOcrText;
      state.combinedOcrText = state.originalOcrText;
      
      if ((window as any).tinymce && (window as any).tinymce.get('ocr-editor')) {
        (window as any).tinymce.get('ocr-editor').setContent(state.originalOcrText.replace(/\n/g, '<br>'));
      }
    };

    // 편집된 텍스트 다운로드
    const downloadEditedText = () => {
      const blob = new Blob([state.editableOcrText], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `edited_ocr_text_${new Date().getTime()}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    };

    // TinyMCE CDN 로드
    const loadTinyMCE = () => {
      if (!(window as any).tinymce) {
        const script = document.createElement('script');
        script.src = '/js/tinymce/tinymce.min.js';
        script.onload = () => {
          console.log('TinyMCE loaded');
        };
        document.head.appendChild(script);
      }
    };

    // 컴포넌트 마운트 시 TinyMCE 로드
    const { onMounted } = require('vue');
    onMounted(() => {
      loadTinyMCE();
    });

    return { 
      ...toRefs(state), 
      onImageLoaded,
      analyzeWorksheet,
      initTinyMCE,
      saveEditedText,
      resetOcrText,
      downloadEditedText
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
  height: 100%;
  flex-direction: column;
  padding: 0;
  margin: 0;
}

#app {
  font-family: Helvetica, "Manjari", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--primary-color--dark);
}

header {
  background-color: var(--primary-color);
  color: #ffffff;
  padding: 16px;
  text-align: center;
  
  h1 {
    margin: 0;
    font-size: 1.5rem;
  }
}

main.container {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 20px;
  gap: 20px;
}

.img-container {
  flex: 0 0 auto;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  
  .model-selection, .api-key-input {
    display: flex;
    flex-direction: column;
    gap: 5px;
    
    label {
      font-weight: bold;
      color: var(--primary-color--dark);
    }
    
    select, input {
      padding: 8px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
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
  
  button {
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
  flex: 1;
  display: flex;
  flex-direction: column;
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
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }
  
  .no-result {
    text-align: center;
    color: #666;
    font-style: italic;
    padding: 40px;
    background-color: #f8f9fa;
    border-radius: 4px;
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

.ocr-content, .ai-content {
  .text-block, .description-block {
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
  main.container {
    padding: 10px;
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
    .model-selection, .api-key-input {
      select, input {
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
  font-family: 'Courier New', monospace;
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

.btn:hover {
  opacity: 0.8;
}
</style>
