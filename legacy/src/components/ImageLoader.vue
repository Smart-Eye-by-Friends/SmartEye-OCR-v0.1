<template>
  <div
    class="drop"
    :class="classes"
    @dragover.prevent="dragOver"
    @dragleave.prevent="dragLeave"
    @drop.prevent="drop($event)"
  >
    <img :src="imageSource" v-if="imageSource" id="ocr-img" />
    <h1 v-if="wrongFile">잘못된 파일 형식입니다</h1>
    <h1 v-if="!imageSource && !isDragging && !wrongFile">
      이미지를 드래그하거나 붙여넣기 또는 업로드하세요
    </h1>
    <input
      type="file"
      id="uploadimage"
      :accept="'image/*'"
      @change="requestUploadFile"
    />
    <label for="uploadimage" v-if="!imageSource" class="upload-button">
      📁 파일 선택
    </label>
  </div>
</template>

<script lang="ts">
import { defineComponent, computed, reactive, toRefs } from "vue";

export default defineComponent({
  name: "ImageLoader",
  emits: ['image-loaded'],
  setup(props, { emit }) {
    const state = reactive({
      isDragging: false,
      wrongFile: false,
      imageSource: null,
      currentFile: null as File | null,
    });
    const classes = computed(() => state.isDragging);
    
    const requestUploadFile = (event: any) => {
      const files = event.target.files;
      if (files && files.length > 0) {
        processFile(files[0]);
      }
    };
    
    const processFile = (file: File) => {
      state.wrongFile = false;
      
      // 이미지 파일인지 확인
      if (file.type.indexOf("image/") >= 0) {
        const reader = new FileReader();
        reader.onload = (f) => {
          state.imageSource = f.target?.result;
          state.isDragging = false;
          state.currentFile = file;
          
          // 부모 컴포넌트에 이미지 로드 이벤트 전달
          emit('image-loaded', file);
        };
        reader.readAsDataURL(file);
      } else {
        state.wrongFile = true;
        state.imageSource = null;
        state.isDragging = false;
        state.currentFile = null;
      }
    };
    
    const dragOver = () => {
      state.isDragging = true;
    };
    
    const dragLeave = () => {
      state.isDragging = false;
    };
    
    const drop = (e: any) => {
      const files: FileList | null | undefined = e.dataTransfer?.files;
      
      // 파일 업로드 input에서 온 경우
      if (!files && e.target?.files) {
        const inputFiles = e.target.files;
        if (inputFiles.length === 1) {
          processFile(inputFiles[0]);
        }
        return;
      }
      
      // 드래그 앤 드롭에서 온 경우
      if (files?.length === 1) {
        processFile(files[0]);
      }
    };

    // 클립보드 붙여넣기 지원
    document.onpaste = (event) => {
      const items = event.clipboardData?.items;
      for (let index in items) {
        const item = items[index];
        if (item.kind === 'file') {
          const blob = item.getAsFile();
          if (blob) {
            processFile(blob);
          }
        }
      }
    };

    return {
      classes,
      ...toRefs(state),
      dragOver,
      dragLeave,
      drop,
      requestUploadFile
    };
  },
});
</script>

<style>
.drop {
  width: 100%;
  height: 400px;
  background-color: #f8f9fa;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s ease-in-out;
  font-family: sans-serif;
  padding: 20px;
  flex-direction: column;
  overflow-y: auto;
  border: 2px dashed #ddd;
  border-radius: 8px;
  position: relative;
}

.isDragging {
  background-color: #e3f2fd;
  border-color: #2196f3;
}

.drop h1 {
  color: #666;
  font-size: 1.2rem;
  text-align: center;
  margin: 0;
}

.drop img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

#uploadimage {
  display: none;
}

.upload-button {
  display: inline-block;
  margin-top: 15px;
  padding: 10px 20px;
  background-color: #2196f3;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
  font-size: 1rem;
}

.upload-button:hover {
  background-color: #1976d2;
}

.drop:hover {
  border-color: #2196f3;
}
</style>
