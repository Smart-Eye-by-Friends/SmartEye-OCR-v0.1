# 🚀 SmartEyeSsen Frontend 개발 로드맵 (React 18)

> **프로젝트**: AI 기반 시각 장애 학생 학습지 분석 시스템  
> **기술 스택**: React 18 + TypeScript + Vite  
> **개발 기간**: 60시간 (8일, 1일 8시간 기준)  
> **작성일**: 2025년 11월 4일

---

## 📋 목차

1. [프로젝트 컨텍스트](#1-프로젝트-컨텍스트)
2. [기술 스택 및 아키텍처](#2-기술-스택-및-아키텍처)
3. [개발 환경 설정](#3-개발-환경-설정)
4. [Phase별 개발 계획](#4-phase별-개발-계획)
5. [일일 작업 스케줄](#5-일일-작업-스케줄)
6. [품질 보증 계획](#6-품질-보증-계획)
7. [리스크 관리](#7-리스크-관리)
8. [완료 기준](#8-완료-기준)

---

## 1. 프로젝트 컨텍스트

### 1.1 프로젝트 개요

**SmartEyeSsen**은 시각 장애 학생을 위한 AI 기반 학습지 자동 분석 및 텍스트 변환 시스템입니다.

**핵심 목적**:

- 학습지 이미지를 업로드하면 AI가 레이아웃을 분석하고 OCR 수행
- 교사가 분석 결과를 확인하고 텍스트를 수정
- 최종 수정본을 TTS 가능한 형식(Word)으로 export
- 점자 변환 대기 시간 2-3주 → 1-2분으로 단축

### 1.2 사용자 워크플로우

```
1. 교사가 학습지 이미지/PDF 업로드 (다중 페이지 지원)
   ↓
2. 문서 타입 선택 (문제지/일반 문서)
   ↓
3. AI 모델 자동 선택 및 분석 실행
   - 문제지 → SmartEyeSsen 모델
   - 일반 문서 → DocLayout-YOLO
   ↓
4. 레이아웃 분석 결과 시각화
   - SVG로 바운딩 박스 표시
   - 클래스별 색상 구분
   ↓
5. 텍스트 편집 및 수정
   - TinyMCE 에디터 사용
   - 자동 저장 (500ms debounce)
   ↓
6. 전체 페이지 통합 다운로드
   - 모든 페이지를 하나의 Word 문서로 병합
```

### 1.3 핵심 UI 구성 (CSS Grid 5열 시스템)

```
┌────────┬──────────┬────────────┬────────────┐
│Sidebar │  Slider  │   Layout   │   Editor   │
│        │          │   Viewer   │   Panel    │
│  문서  │  페이지  │            │            │
│  설정  │  목록    │   이미지   │  텍스트    │
│        │          │   뷰어     │  편집      │
│  200px │  250px   │    50%     │    50%     │
└────────┴──────────┴────────────┴────────────┘

닫힌 상태:
┌────────┬──┬──────────────────┬──────────────────┐
│Sidebar │⏵│     Layout       │     Editor       │
│  200px │40│      50%         │      50%         │
└────────┴──┴──────────────────┴──────────────────┘
```

**반응형 지원 해상도**:

- 1280px (최소), 1366px, 1600px, 1920px, 2560px+

---

## 2. 기술 스택 및 아키텍처

### 2.1 Core 기술 스택

```typescript
{
  "framework": "React 18.2+",         // Hooks, Suspense, Concurrent Features
  "language": "TypeScript 5.0+",      // strict mode
  "build": "Vite 4.4+",               // 빠른 HMR, ESBuild
  "state": "Context API + useReducer", // 전역 상태 관리
  "canvas": "Fabric.js 5.3",          // 바운딩 박스 렌더링 (SVG도 병행)
  "editor": "TinyMCE React 4.3",      // WYSIWYG 에디터
  "http": "Axios 1.4",                // API 통신
  "router": "React Router 6.15",      // 라우팅 (필요시)
  "css": "CSS Grid + CSS Modules",    // 반응형 레이아웃 + 스타일 격리
  "testing": "Vitest + RTL"           // Vitest + React Testing Library
}
```

### 2.2 프로젝트 구조

```
Frontend/
├── src/
│   ├── components/
│   │   ├── layout/              # Grid 레이아웃 컴포넌트
│   │   │   └── MainLayout.tsx
│   │   ├── sidebar/             # Sidebar 관련
│   │   │   ├── Sidebar.tsx
│   │   │   ├── DocumentTypeSelector.tsx
│   │   │   ├── ModelSelector.tsx
│   │   │   ├── AnalysisModeSelector.tsx
│   │   │   ├── AnalyzeButton.tsx
│   │   │   └── IntegratedDownloadButton.tsx
│   │   ├── slider/              # Page Slider
│   │   │   ├── PageSlider.tsx
│   │   │   ├── MultiFileLoader.tsx
│   │   │   ├── PageThumbnailList.tsx
│   │   │   └── SliderRestoreButton.tsx
│   │   ├── viewer/              # Layout Panel
│   │   │   ├── LayoutPanel.tsx
│   │   │   ├── ImageViewer.tsx
│   │   │   ├── ViewerToolbar.tsx
│   │   │   └── BoundingBoxOverlay.tsx
│   │   └── editor/              # Editor Panel
│   │       ├── EditorPanel.tsx
│   │       ├── TextEditorTab.tsx
│   │       ├── AIStatsTab.tsx
│   │       └── ActionButtons.tsx
│   ├── hooks/                   # Custom Hooks
│   │   ├── useGridLayout.ts
│   │   ├── useResponsive.ts
│   │   ├── useBoundingBox.ts
│   │   └── useAutoSave.ts
│   ├── contexts/                # React Context
│   │   ├── ProjectContext.tsx
│   │   ├── PagesContext.tsx
│   │   └── LayoutContext.tsx
│   ├── services/                # API 서비스
│   │   ├── api.ts
│   │   ├── analysis.ts
│   │   └── download.ts
│   ├── types/                   # TypeScript 타입
│   │   ├── index.ts
│   │   ├── layout.ts
│   │   └── api.ts
│   ├── styles/                  # 전역 스타일
│   │   ├── variables.css
│   │   ├── grid.css
│   │   ├── responsive.css
│   │   └── main.css
│   ├── utils/                   # 유틸리티
│   │   ├── coordinateScaler.ts
│   │   └── formatters.ts
│   ├── App.tsx
│   ├── main.tsx
│   └── vite-env.d.ts
├── public/
├── index.html
├── vite.config.ts
├── tsconfig.json
├── package.json
└── README.md
```

### 2.3 Backend API 엔드포인트

```typescript
// FastAPI 백엔드 연동
interface APIEndpoints {
  // 분석
  analyze: "POST /api/analyze"; // 이미지 분석

  // 페이지 데이터
  getVisualizationData: "GET /api/pages/{id}/visualization-data";
  saveText: "POST /api/pages/{id}/text";

  // 통합 다운로드
  exportProject: "POST /api/projects/{id}/export";

  // 포맷팅
  formatText: "POST /api/format";
}
```

---

## 3. 개발 환경 설정

### 3.1 사전 준비 (Day 0 - 1시간)

#### Step 1: Git 브랜치 생성

```bash
# 현재 브랜치 확인
git status
git branch

# 새 기능 브랜치 생성
git checkout -b feature/react-Frontend-implementation

# 백엔드 브랜치 확인 (통합 테스트용)
git branch -a | grep backend
```

#### Step 2: 프로젝트 초기화

```bash
# Vite + React + TypeScript 프로젝트 생성
npm create vite@latest Frontend -- --template react-ts

cd Frontend

# 의존성 설치
npm install

# 추가 라이브러리 설치
npm install fabric @tinymce/tinymce-react
npm install axios
npm install react-router-dom
npm install -D vitest @testing-library/react @testing-library/jest-dom happy-dom
npm install -D @types/fabric
npm install -D @vitejs/plugin-react
```

#### Step 3: 개발 서버 테스트

```bash
npm run dev
# 브라우저에서 http://localhost:5173 확인
```

#### Step 4: 폴더 구조 생성

```bash
# src/ 하위 폴더 생성
mkdir -p src/{components/{layout,sidebar,slider,viewer,editor},hooks,contexts,services,types,styles,utils}

# 컴포넌트 세부 폴더
mkdir -p src/components/sidebar
mkdir -p src/components/slider
mkdir -p src/components/viewer
mkdir -p src/components/editor

# 테스트 폴더
mkdir -p src/__tests__/{unit,integration}
```

### 3.2 TypeScript 설정

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,

    /* Bundler mode */
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",

    /* Linting */
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,

    /* Paths */
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

### 3.3 CSS 변수 파일 (사전 작업)

```css
/* src/styles/variables.css */
:root {
  /* 🎨 색상 변수 */
  --primary-color: #00bcd4;
  --primary-hover: #00acc1;
  --secondary-color: #ff5722;
  --text-color: #333333;
  --bg-color: #f5f5f5;
  --border-color: #e0e0e0;

  /* 📐 반응형 레이아웃 변수 */
  --sidebar-min: 150px;
  --sidebar-ideal: 12vw;
  --sidebar-max: 320px;

  --slider-min: 200px;
  --slider-ideal: 15vw;
  --slider-max: 400px;

  --restore-width: 40px;

  /* 🎭 애니메이션 변수 */
  --transition-speed: 300ms;
  --transition-easing: cubic-bezier(0.4, 0, 0.2, 1);

  /* 📏 간격 변수 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  /* 🔤 폰트 크기 변수 */
  --font-xs: 12px;
  --font-sm: 14px;
  --font-md: 16px;
  --font-lg: 18px;
  --font-xl: 20px;
}
```

---

## 4. Phase별 개발 계획

## Phase 1: CSS Grid 반응형 레이아웃 구축 (Day 1-3, 20시간)

### 목표

- CSS Grid 5열 시스템 구현
- 반응형 minmax() 적용
- 5단계 미디어 쿼리 구현
- Slider 토글 및 Restore Button 기능

### Day 1 (8시간)

#### Task 1.1: CSS Grid 기본 레이아웃 (5시간)

**세부 작업**:

1. **Grid CSS 파일 생성** (30분)

```css
/* src/styles/grid.css */
.main-layout {
  display: grid;
  grid-template-columns:
    minmax(var(--sidebar-min), min(var(--sidebar-ideal), var(--sidebar-max)))
    minmax(var(--slider-min), min(var(--slider-ideal), var(--slider-max)))
    minmax(0, 1fr)
    minmax(0, 1fr);
  grid-template-areas: "sidebar slider layout editor";
  height: calc(100vh - 80px);
  gap: 0;
  transition: grid-template-columns var(--transition-speed) var(
      --transition-easing
    );
}

.main-layout.slider-collapsed {
  grid-template-columns:
    minmax(var(--sidebar-min), min(var(--sidebar-ideal), var(--sidebar-max)))
    0px
    var(--restore-width)
    minmax(0, 1fr)
    minmax(0, 1fr);
  grid-template-areas: "sidebar . restore layout editor";
}

.sidebar {
  grid-area: sidebar;
}
.page-slider {
  grid-area: slider;
}
.slider-restore-btn {
  grid-area: restore;
}
.layout-panel {
  grid-area: layout;
}
.editor-panel {
  grid-area: editor;
}
```

2. **MainLayout 컴포넌트 생성** (2시간)

```typescript
// src/components/layout/MainLayout.tsx
import React, { useState } from "react";
import Sidebar from "@/components/sidebar/Sidebar";
import PageSlider from "@/components/slider/PageSlider";
import SliderRestoreButton from "@/components/slider/SliderRestoreButton";
import LayoutPanel from "@/components/viewer/LayoutPanel";
import EditorPanel from "@/components/editor/EditorPanel";
import "@/styles/grid.css";

const MainLayout: React.FC = () => {
  const [isSliderCollapsed, setIsSliderCollapsed] = useState(false);

  const closeSlider = () => {
    setIsSliderCollapsed(true);
  };

  const openSlider = () => {
    setIsSliderCollapsed(false);
  };

  return (
    <div
      className={`main-layout ${isSliderCollapsed ? "slider-collapsed" : ""}`}
    >
      {/* Sidebar */}
      <div className="sidebar">
        <Sidebar />
      </div>

      {/* Page Slider */}
      {!isSliderCollapsed && (
        <div className="page-slider">
          <PageSlider onClose={closeSlider} />
        </div>
      )}

      {/* Restore Button */}
      {isSliderCollapsed && (
        <SliderRestoreButton onClick={openSlider} pageCount={5} />
      )}

      {/* Layout Panel */}
      <div className="layout-panel">
        <LayoutPanel />
      </div>

      {/* Editor Panel */}
      <div className="editor-panel">
        <EditorPanel />
      </div>
    </div>
  );
};

export default MainLayout;
```

3. **상태 관리 Custom Hook 생성** (1시간)

```typescript
// src/hooks/useGridLayout.ts
import { useState, useCallback } from "react";

export const useGridLayout = () => {
  const [isSliderCollapsed, setIsSliderCollapsed] = useState(false);

  const toggleSlider = useCallback(() => {
    setIsSliderCollapsed((prev) => !prev);
  }, []);

  const openSlider = useCallback(() => {
    setIsSliderCollapsed(false);
  }, []);

  const closeSlider = useCallback(() => {
    setIsSliderCollapsed(true);
  }, []);

  return {
    isSliderCollapsed,
    toggleSlider,
    openSlider,
    closeSlider,
  };
};
```

4. **App.tsx 업데이트** (30min)

```typescript
// src/App.tsx
import React from "react";
import MainLayout from "@/components/layout/MainLayout";
import "@/styles/variables.css";
import "@/styles/main.css";

const App: React.FC = () => {
  return (
    <div className="app">
      <header className="app-header">
        <h1>🔍 SmartEyeSsen 학습지 분석</h1>
        <p>AI 기반 학습지 OCR 및 구조 분석 시스템</p>
      </header>
      <MainLayout />
    </div>
  );
};

export default App;
```

5. **개발 서버 실행 및 확인** (30분)

```bash
npm run dev
# 브라우저에서 4개 영역 Grid 배치 확인
```

6. **Git 커밋** (30분)

```bash
git add .
git commit -m "feat: CSS Grid 기본 레이아웃 구조 구현 (React)

- Grid 5열 시스템 적용
- CSS 변수 기반 반응형 폭 설정
- Slider 토글 상태 관리 Hook 생성
- MainLayout 컴포넌트 기본 구조 완료"
```

**완료 조건**:

- ✅ 4개 영역이 Grid로 정렬됨
- ✅ Slider 열기/닫기 버튼 동작
- ✅ Grid 전환 시 레이아웃 변경 확인

---

#### Task 1.2: 반응형 minmax 설정 (3시간)

**세부 작업**:

1. **반응형 감지 Custom Hook** (1시간)

```typescript
// src/hooks/useResponsive.ts
import { useState, useEffect } from "react";

export type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl";

const getBreakpoint = (width: number): Breakpoint => {
  if (width < 1366) return "xs"; // 1280px 이하
  if (width < 1600) return "sm"; // 1366px ~ 1599px
  if (width < 1920) return "md"; // 1600px ~ 1919px
  if (width < 2560) return "lg"; // 1920px ~ 2559px
  return "xl"; // 2560px 이상
};

export const useResponsive = () => {
  const [screenWidth, setScreenWidth] = useState(window.innerWidth);
  const [screenHeight, setScreenHeight] = useState(window.innerHeight);
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(
    getBreakpoint(window.innerWidth)
  );

  useEffect(() => {
    const handleResize = () => {
      const width = window.innerWidth;
      setScreenWidth(width);
      setScreenHeight(window.innerHeight);
      setBreakpoint(getBreakpoint(width));
    };

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return {
    screenWidth,
    screenHeight,
    breakpoint,
  };
};
```

2. **MainLayout에 반응형 감지 추가** (30min)

```typescript
// src/components/layout/MainLayout.tsx에 추가
import { useResponsive } from "@/hooks/useResponsive";

const MainLayout: React.FC = () => {
  const { screenWidth, breakpoint } = useResponsive();

  useEffect(() => {
    console.log(`Screen: ${screenWidth}px, Breakpoint: ${breakpoint}`);
  }, [screenWidth, breakpoint]);

  // ... 기존 코드
};
```

3. **브라우저 창 크기 조절 테스트** (1시간)

**테스트 해상도**:

- 1280x720
- 1366x768
- 1600x900
- 1920x1080
- 2560x1440

4. **Git 커밋** (30분)

```bash
git add .
git commit -m "feat: 반응형 감지 Hook 및 minmax 동적 계산

- useResponsive Hook으로 5단계 breakpoint 감지
- CSS minmax()로 유동 폭 자동 조정
- 최소/최대값 제한 적용
- 실시간 화면 크기 로깅"
```

**완료 조건**:

- ✅ 화면 크기에 따라 Sidebar/Slider 폭 변경
- ✅ Console에 breakpoint 출력
- ✅ 최소/최대값 제한 작동

---

### Day 2 (8시간)

#### Task 1.3: 미디어 쿼리 구현 (4시간)

**세부 작업**:

1. **responsive.css 파일 생성** (2시간)

```css
/* src/styles/responsive.css */

/* 1. 초대형 화면 (2560px+) */
@media (min-width: 2560px) {
  :root {
    --sidebar-max: 320px;
    --slider-max: 400px;
  }

  .sidebar {
    font-size: clamp(14px, 1vw, 18px);
  }

  .page-slider {
    font-size: clamp(13px, 0.9vw, 16px);
  }
}

/* 2. 대형 화면 (1920px ~ 2560px) */
@media (max-width: 2559px) and (min-width: 1920px) {
  :root {
    --sidebar-max: 280px;
    --slider-max: 350px;
  }
}

/* 3. 중형 화면 (1600px ~ 1920px) */
@media (max-width: 1919px) and (min-width: 1600px) {
  :root {
    --sidebar-ideal: 11.5vw;
    --slider-ideal: 14vw;
  }
}

/* 4. 소형 화면 (1366px ~ 1600px) */
@media (max-width: 1599px) and (min-width: 1366px) {
  :root {
    --sidebar-min: 150px;
    --sidebar-ideal: 11vw;
    --slider-min: 200px;
    --slider-ideal: 13vw;
  }

  .sidebar {
    font-size: 13px;
  }
  .page-slider {
    font-size: 12px;
  }
}

/* 5. 최소 화면 (1366px 이하) */
@media (max-width: 1365px) {
  .main-layout {
    grid-template-columns:
      minmax(140px, 10vw)
      0px
      var(--restore-width)
      minmax(0, 1fr)
      minmax(0, 1fr) !important;
  }

  .page-slider {
    display: none !important;
  }

  .slider-restore-btn {
    display: flex !important;
  }

  .sidebar {
    font-size: 12px;
    padding: 8px;
  }
}
```

2. **App.tsx에 CSS import** (10min)

```typescript
// src/App.tsx
import "@/styles/variables.css";
import "@/styles/grid.css";
import "@/styles/responsive.css";
import "@/styles/main.css";
```

3. **5개 해상도 수동 테스트** (1시간 30분)

```markdown
테스트 체크리스트:

[ ] 1280x720: - Sidebar: 140px - Slider: 자동 닫김 - Restore: 표시됨

[ ] 1366x768: - Sidebar: 150px - Slider: 200px - 폰트: 13px

[ ] 1600x900: - Sidebar: 176px - Slider: 208px

[ ] 1920x1080: - Sidebar: 230px - Slider: 288px

[ ] 2560x1440: - Sidebar: 307px - Slider: 384px
```

4. **Git 커밋** (20min)

```bash
git add .
git commit -m "feat: 5단계 반응형 미디어 쿼리 구현

- 1280px 이하: Slider 강제 숨김
- 1366~1600px: 컴팩트 모드
- 1600~1920px: 약간 축소
- 1920~2560px: 기본 모드
- 2560px+: 최대값 제한

5개 해상도 테스트 완료"
```

**완료 조건**:

- ✅ 5개 중단점 모두 스타일 적용
- ✅ 1366px 이하에서 Slider 자동 숨김
- ✅ 폰트 크기 반응형 조정

---

#### Task 1.4: PageSlider 컴포넌트 분리 (3시간)

**세부 작업**:

1. **PageSlider 컴포넌트 생성** (1시간)

```typescript
// src/components/slider/PageSlider.tsx
import React from "react";
import styles from "./PageSlider.module.css";

interface PageSliderProps {
  pageCount?: number;
  onClose: () => void;
}

const PageSlider: React.FC<PageSliderProps> = ({ pageCount = 0, onClose }) => {
  return (
    <div className={styles.pageSlider}>
      <div className={styles.sliderHeader}>
        <h3>📄 페이지 ({pageCount})</h3>
        <button
          className={styles.closeBtn}
          onClick={onClose}
          aria-label="슬라이더 닫기"
        >
          ⏴
        </button>
      </div>

      <div className={styles.sliderContent}>
        {/* 파일 업로드 존 */}
        <div className={styles.fileUploadZone}>
          <p>파일을 드래그하거나 클릭하세요</p>
        </div>

        {/* 썸네일 리스트 (임시) */}
        <div className={styles.thumbnailList}>
          {Array.from({ length: pageCount }, (_, i) => (
            <div key={i} className={styles.thumbnailItem}>
              페이지 {i + 1}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default PageSlider;
```

```css
/* src/components/slider/PageSlider.module.css */
.pageSlider {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  border-right: 1px solid var(--border-color);
  overflow: hidden;
  transition: all var(--transition-speed) var(--transition-easing);
}

.sliderHeader {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  background: #f9f9f9;
}

.sliderHeader h3 {
  margin: 0;
  font-size: var(--font-md);
}

.closeBtn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: var(--spacing-sm);
  transition: transform 0.2s;
}

.closeBtn:hover {
  transform: scale(1.1);
}

.sliderContent {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.fileUploadZone {
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  padding: var(--spacing-xl);
  text-align: center;
  margin-bottom: var(--spacing-md);
  cursor: pointer;
  transition: all 0.3s;
}

.fileUploadZone:hover {
  border-color: var(--primary-color);
  background: rgba(0, 188, 212, 0.05);
}

.thumbnailList {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.thumbnailItem {
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.thumbnailItem:hover {
  border-color: var(--primary-color);
  background: rgba(0, 188, 212, 0.05);
}
```

2. **MainLayout에서 사용** (30min)

```typescript
// src/components/layout/MainLayout.tsx 업데이트
const MainLayout: React.FC = () => {
  const [pageCount] = useState(5); // 임시 값
  // ...

  return (
    <div
      className={`main-layout ${isSliderCollapsed ? "slider-collapsed" : ""}`}
    >
      {/* ... */}

      {!isSliderCollapsed && (
        <div className="page-slider">
          <PageSlider pageCount={pageCount} onClose={closeSlider} />
        </div>
      )}

      {/* ... */}
    </div>
  );
};
```

3. **동작 테스트** (30min)

```markdown
[ ] Slider 열림/닫힘 전환
[ ] 닫기 버튼 클릭 시 Slider 숨김
[ ] Grid 레이아웃 자동 재조정
[ ] 파일 업로드 존 호버 효과
[ ] 썸네일 리스트 스크롤
```

4. **Git 커밋** (30min)

```bash
git add .
git commit -m "feat: PageSlider 컴포넌트 분리 및 독립 영역 구현

- PageSlider.tsx 생성 (헤더, 파일 업로드, 썸네일)
- CSS Modules로 스타일 격리
- 조건부 렌더링으로 숨김/표시 처리
- Grid 영역으로 독립 배치"
```

**완료 조건**:

- ✅ PageSlider가 독립 컴포넌트로 작동
- ✅ 닫기 버튼 클릭 시 Slider 숨김
- ✅ Grid 전환 애니메이션 부드러움

---

### Day 3 (4시간)

#### Task 1.5: SliderRestoreButton 컴포넌트 (3시간)

**세부 작업**:

1. **SliderRestoreButton 컴포넌트 생성** (1시간 30분)

```typescript
// src/components/slider/SliderRestoreButton.tsx
import React from "react";
import styles from "./SliderRestoreButton.module.css";

interface SliderRestoreButtonProps {
  onClick: () => void;
  pageCount?: number;
}

const SliderRestoreButton: React.FC<SliderRestoreButtonProps> = ({
  onClick,
  pageCount = 0,
}) => {
  return (
    <button
      className={styles.sliderRestoreBtn}
      onClick={onClick}
      aria-label="페이지 슬라이더 열기"
    >
      <div className={styles.restoreIcon}>⏵</div>
      <div className={styles.restoreText}>페이지</div>
      {pageCount > 0 && (
        <div className={styles.pageCountBadge}>{pageCount}</div>
      )}
    </button>
  );
};

export default SliderRestoreButton;
```

```css
/* src/components/slider/SliderRestoreButton.module.css */
.sliderRestoreBtn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  width: var(--restore-width);
  height: 100%;
  background: linear-gradient(
    135deg,
    var(--primary-color),
    var(--primary-hover)
  );
  color: white;
  border: none;
  cursor: pointer;
  position: relative;
  transition: all 0.3s var(--transition-easing);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.sliderRestoreBtn:hover {
  transform: translateX(2px);
  background: linear-gradient(135deg, var(--primary-hover), #0097a7);
  box-shadow: 4px 0 12px rgba(0, 0, 0, 0.15);
}

.restoreIcon {
  font-size: 24px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.restoreText {
  writing-mode: vertical-rl;
  font-size: clamp(12px, 0.8vw, 14px);
  font-weight: 600;
  letter-spacing: 2px;
}

.pageCountBadge {
  position: absolute;
  top: 8px;
  right: 4px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--primary-color);
  font-size: 10px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}
```

2. **MainLayout에 추가** (20min)

```typescript
// src/components/layout/MainLayout.tsx 업데이트
import SliderRestoreButton from "@/components/slider/SliderRestoreButton";

// ...

{
  isSliderCollapsed && (
    <SliderRestoreButton onClick={openSlider} pageCount={pageCount} />
  );
}
```

3. **동작 및 애니메이션 테스트** (40min)

```markdown
[ ] Slider 닫기 → Restore 버튼 출현
[ ] Restore 버튼 클릭 → Slider 열림
[ ] 호버 효과 (translateX, 배경색 변경)
[ ] 페이지 카운트 뱃지 표시
[ ] 아이콘 pulse 애니메이션
[ ] 1366px 이하에서 기본 표시
```

4. **Git 커밋** (30min)

```bash
git add .
git commit -m "feat: SliderRestoreButton 컴포넌트 구현

- 세로 방향 복원 버튼 UI
- 조건부 렌더링 (Slider 닫혔을 때만)
- 페이지 카운트 뱃지 추가
- 호버 효과 및 pulse 애니메이션
- 1366px 이하 자동 표시"
```

**완료 조건**:

- ✅ Restore 버튼이 Slider 위치에 표시
- ✅ 클릭 시 Slider 다시 열림
- ✅ 호버 효과 부드러움
- ✅ 페이지 카운트 뱃지 동적 업데이트

---

#### Task 1.6: 애니메이션 최적화 (1시간)

**세부 작업**:

1. **CSS Transition 세밀 조정** (40min)

```css
/* src/styles/grid.css에 추가 */
.page-slider {
  transition: width 300ms cubic-bezier(0.4, 0, 0.2, 1), opacity 300ms
      cubic-bezier(0.4, 0, 0.2, 1);
}

.page-slider.closing {
  opacity: 0;
  pointer-events: none;
}

.slider-restore-btn {
  opacity: 0;
  animation: fadeIn 300ms cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: 100ms;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
```

2. **Git 커밋** (20min)

```bash
git add .
git commit -m "perf: Grid 전환 애니메이션 최적화

- Cubic-bezier easing 적용
- Opacity + Width 동시 전환
- Restore 버튼 fadeIn 애니메이션
- 부드러운 전환 효과 (60fps 목표)"
```

**완료 조건**:

- ✅ 애니메이션이 60fps로 부드럽게 작동
- ✅ Grid 전환 시 딜레이 없음

---

### Phase 1 완료 검증 (30분)

```markdown
## Phase 1 체크리스트

### 기능 검증

- [ ] Grid 4단 레이아웃 정상 작동
- [ ] Slider 열기/닫기 동작
- [ ] Restore 버튼 조건부 표시
- [ ] 5개 해상도 반응형 적용

### 성능 검증

- [ ] Grid 전환 < 300ms
- [ ] 애니메이션 60fps 유지

### 코드 품질

- [ ] ESLint 오류 없음
- [ ] Console 경고 없음
- [ ] Git 커밋 메시지 명확
```

---

## Phase 2: Sidebar 기능 확장 (Day 4-5, 11시간)

### 목표

- 문서 타입 선택 컴포넌트
- AI 모델 자동 선택 로직
- 분석 시작 버튼
- 통합 다운로드 기능

### Day 4 (8시간)

#### Task 2.1: DocumentTypeSelector 구현 (2시간)

**세부 작업**:

1. **DocumentTypeSelector 컴포넌트 생성** (1시간)

```typescript
// src/components/sidebar/DocumentTypeSelector.tsx
import React, { useState } from "react";
import styles from "./DocumentTypeSelector.module.css";

type DocumentType = "worksheet" | "document";

interface DocumentTypeOption {
  id: DocumentType;
  label: string;
  icon: string;
  description: string;
}

interface DocumentTypeSelectorProps {
  onChange: (type: DocumentType) => void;
}

const DocumentTypeSelector: React.FC<DocumentTypeSelectorProps> = ({
  onChange,
}) => {
  const [selectedType, setSelectedType] = useState<DocumentType>("worksheet");

  const types: DocumentTypeOption[] = [
    {
      id: "worksheet",
      label: "문제지",
      icon: "📝",
      description: "시험지, 문제집",
    },
    {
      id: "document",
      label: "일반 문서",
      icon: "📄",
      description: "보고서, 논문",
    },
  ];

  const handleSelect = (typeId: DocumentType) => {
    setSelectedType(typeId);
    onChange(typeId);
  };

  return (
    <div className={styles.documentTypeSelector}>
      <h3 className={styles.selectorTitle}>문서 타입</h3>
      <div className={styles.typeOptions}>
        {types.map((type) => (
          <label
            key={type.id}
            className={`${styles.typeOption} ${
              selectedType === type.id ? styles.selected : ""
            }`}
          >
            <input
              type="radio"
              value={type.id}
              checked={selectedType === type.id}
              onChange={() => handleSelect(type.id)}
              className={styles.radioInput}
            />
            <div className={styles.optionContent}>
              <span className={styles.optionIcon}>{type.icon}</span>
              <div className={styles.optionText}>
                <strong>{type.label}</strong>
                <small>{type.description}</small>
              </div>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
};

export default DocumentTypeSelector;
```

```css
/* src/components/sidebar/DocumentTypeSelector.module.css */
.documentTypeSelector {
  margin-bottom: var(--spacing-lg);
}

.selectorTitle {
  font-size: var(--font-md);
  margin-bottom: var(--spacing-md);
  color: var(--text-color);
}

.typeOptions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.typeOption {
  display: block;
  cursor: pointer;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  padding: var(--spacing-md);
  transition: all 0.3s;
}

.typeOption:hover {
  border-color: var(--primary-color);
  background: rgba(0, 188, 212, 0.05);
}

.typeOption.selected {
  border-color: var(--primary-color);
  background: rgba(0, 188, 212, 0.1);
  box-shadow: 0 2px 8px rgba(0, 188, 212, 0.2);
}

.radioInput {
  display: none;
}

.optionContent {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.optionIcon {
  font-size: 32px;
}

.optionText {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.optionText strong {
  font-size: var(--font-md);
  color: var(--text-color);
}

.optionText small {
  font-size: var(--font-xs);
  color: #666;
}
```

2. **Sidebar 컴포넌트에 통합** (30min)

```typescript
// src/components/sidebar/Sidebar.tsx
import React, { useState } from "react";
import DocumentTypeSelector from "./DocumentTypeSelector";
import styles from "./Sidebar.module.css";

type DocumentType = "worksheet" | "document";

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");

  const handleDocumentTypeChange = (type: DocumentType) => {
    setDocumentType(type);
    console.log("Document type changed:", type);
  };

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={handleDocumentTypeChange} />
    </div>
  );
};

export default Sidebar;
```

```css
/* src/components/sidebar/Sidebar.module.css */
.sidebar {
  padding: var(--spacing-lg);
  background: #fafafa;
  overflow-y: auto;
  height: 100%;
}
```

3. **Git 커밋** (30min)

```bash
git add .
git commit -m "feat: DocumentTypeSelector 컴포넌트 구현

- 문제지/일반 문서 라디오 선택 UI
- 아이콘 + 설명 조합 레이아웃
- 선택 상태 시각적 피드백
- TypeScript 타입 정의
- CSS Modules로 스타일 격리"
```

**완료 조건**:

- ✅ 두 옵션 중 하나 선택 가능
- ✅ 선택 시 시각적 피드백
- ✅ 타입 안정성 확보

---

#### Task 2.2: AI 모델 자동 선택 로직 (2시간)

**세부 작업**:

1. **useModelSelection Custom Hook** (1시간)

```typescript
// src/hooks/useModelSelection.ts
import { useState, useEffect } from "react";

export type AIModel = "smarteye" | "doclayout";
export type DocumentType = "worksheet" | "document";

export const useModelSelection = (documentType: DocumentType) => {
  const [selectedModel, setSelectedModel] = useState<AIModel>("smarteye");
  const isAutoSelected = true;

  useEffect(() => {
    if (documentType === "worksheet") {
      setSelectedModel("smarteye");
    } else if (documentType === "document") {
      setSelectedModel("doclayout");
    }
  }, [documentType]);

  return {
    selectedModel,
    isAutoSelected,
  };
};
```

2. **ModelSelector 컴포넌트 생성** (40min)

```typescript
// src/components/sidebar/ModelSelector.tsx
import React from "react";
import type { AIModel } from "@/hooks/useModelSelection";
import styles from "./ModelSelector.module.css";

interface ModelSelectorProps {
  selectedModel: AIModel;
  isAutoSelected?: boolean;
}

const ModelSelector: React.FC<ModelSelectorProps> = ({
  selectedModel,
  isAutoSelected = true,
}) => {
  const models = [
    {
      id: "smarteye" as AIModel,
      label: "SmartEye",
      description: "문제지 특화",
    },
    {
      id: "doclayout" as AIModel,
      label: "DocLayout",
      description: "일반 문서",
    },
  ];

  return (
    <div className={styles.modelSelector}>
      <h3 className={styles.selectorTitle}>
        AI 모델
        {isAutoSelected && <span className={styles.autoBadge}>자동 선택</span>}
      </h3>
      <div className={styles.modelOptions}>
        {models.map((model) => (
          <div
            key={model.id}
            className={`${styles.modelOption} ${
              selectedModel === model.id ? styles.selected : ""
            } ${isAutoSelected ? styles.disabled : ""}`}
          >
            <strong>{model.label}</strong>
            <small>{model.description}</small>
          </div>
        ))}
      </div>
      {isAutoSelected && (
        <p className={styles.autoInfo}>
          ℹ️ 문서 타입에 따라 자동으로 선택됩니다
        </p>
      )}
    </div>
  );
};

export default ModelSelector;
```

```css
/* src/components/sidebar/ModelSelector.module.css */
.modelSelector {
  margin-bottom: var(--spacing-lg);
}

.selectorTitle {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-md);
  margin-bottom: var(--spacing-md);
}

.autoBadge {
  font-size: var(--font-xs);
  padding: 2px 8px;
  background: #4caf50;
  color: white;
  border-radius: 12px;
  font-weight: 600;
}

.modelOptions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.modelOption {
  padding: var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.3s;
}

.modelOption.selected {
  border-color: var(--primary-color);
  background: rgba(0, 188, 212, 0.1);
}

.modelOption.disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.autoInfo {
  margin-top: var(--spacing-sm);
  font-size: var(--font-xs);
  color: #666;
}
```

3. **Sidebar에 연동** (10min)

```typescript
// src/components/sidebar/Sidebar.tsx 업데이트
import { useModelSelection } from "@/hooks/useModelSelection";
import ModelSelector from "./ModelSelector";

const Sidebar: React.FC = () => {
  const [documentType, setDocumentType] = useState<DocumentType>("worksheet");
  const { selectedModel, isAutoSelected } = useModelSelection(documentType);

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={setDocumentType} />

      <ModelSelector
        selectedModel={selectedModel}
        isAutoSelected={isAutoSelected}
      />
    </div>
  );
};
```

4. **Git 커밋** (10min)

```bash
git add .
git commit -m "feat: AI 모델 자동 선택 로직 구현

- useModelSelection Hook
- worksheet → smarteye
- document → doclayout
- 비활성화 UI 표시
- 자동 선택 배지 추가"
```

**완료 조건**:

- ✅ 문제지 선택 → SmartEye 자동 선택
- ✅ 일반 문서 → DocLayout 자동 선택
- ✅ 모델 옵션 비활성화 스타일

---

#### Task 2.3: AnalyzeButton 구현 (1시간)

**세부 작업**:

1. **AnalyzeButton 컴포넌트 생성** (40min)

```typescript
// src/components/sidebar/AnalyzeButton.tsx
import React from "react";
import styles from "./AnalyzeButton.module.css";

interface AnalyzeButtonProps {
  isLoading?: boolean;
  disabled?: boolean;
  hasFiles?: boolean;
  onClick: () => void;
}

const AnalyzeButton: React.FC<AnalyzeButtonProps> = ({
  isLoading = false,
  disabled = false,
  hasFiles = false,
  onClick,
}) => {
  const isDisabled = disabled || !hasFiles || isLoading;

  return (
    <button
      className={`${styles.analyzeBtn} ${isLoading ? styles.loading : ""}`}
      disabled={isDisabled}
      onClick={onClick}
    >
      {isLoading ? (
        <>
          <span className={styles.spinner}></span>
          분석 중...
        </>
      ) : (
        <>
          <span className={styles.icon}>🚀</span>
          분석 시작
        </>
      )}
    </button>
  );
};

export default AnalyzeButton;
```

```css
/* src/components/sidebar/AnalyzeButton.module.css */
.analyzeBtn {
  width: 100%;
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(135deg, #4caf50, #45a049);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: var(--font-md);
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.3);
}

.analyzeBtn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(76, 175, 80, 0.4);
}

.analyzeBtn:disabled {
  background: #cccccc;
  cursor: not-allowed;
  box-shadow: none;
}

.analyzeBtn.loading {
  background: #ff9800;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid white;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.icon {
  font-size: 20px;
}
```

2. **Sidebar에 추가** (10min)

```typescript
// src/components/sidebar/Sidebar.tsx 업데이트
import AnalyzeButton from "./AnalyzeButton";

const Sidebar: React.FC = () => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [hasFiles, setHasFiles] = useState(false);

  const handleAnalyze = async () => {
    if (!hasFiles) {
      alert("파일을 먼저 업로드해주세요");
      return;
    }

    setIsAnalyzing(true);

    // TODO: 실제 분석 API 호출

    setTimeout(() => {
      setIsAnalyzing(false);
    }, 3000);
  };

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={setDocumentType} />
      <ModelSelector
        selectedModel={selectedModel}
        isAutoSelected={isAutoSelected}
      />

      <AnalyzeButton
        isLoading={isAnalyzing}
        hasFiles={hasFiles}
        onClick={handleAnalyze}
      />
    </div>
  );
};
```

3. **Git 커밋** (10min)

```bash
git add .
git commit -m "feat: 분석 시작 버튼 UI/UX 구현

- 로딩 상태 표시 (spinner)
- 비활성화 조건 처리 (파일 없음)
- 호버 효과 (translateY, box-shadow)
- 아이콘 추가 (🚀)"
```

**완료 조건**:

- ✅ 파일 없을 때 버튼 비활성화
- ✅ 로딩 중 spinner 표시
- ✅ 호버 시 효과

---

#### Task 2.4: IntegratedDownloadButton 시작 (3시간)

**세부 작업**:

1. **downloadService 생성** (1시간)

```typescript
// src/services/download.ts
import axios from "axios";

export interface DownloadProgress {
  current: number;
  total: number;
  percentage: number;
}

export const downloadService = {
  async downloadAllPages(
    pages: any[],
    onProgress: (progress: DownloadProgress) => void
  ) {
    const total = pages.length;
    const results = [];

    for (let i = 0; i < total; i++) {
      const page = pages[i];

      try {
        const result = await axios.get(`/api/download/${page.id}`, {
          responseType: "blob",
        });

        results.push({
          pageId: page.id,
          success: true,
          blob: result.data,
        });

        onProgress({
          current: i + 1,
          total,
          percentage: Math.round(((i + 1) / total) * 100),
        });
      } catch (error) {
        results.push({
          pageId: page.id,
          success: false,
          error: (error as Error).message,
        });
      }
    }

    return results;
  },
};
```

2. **DownloadProgressModal 컴포넌트 생성** (1시간 30min)

```typescript
// src/components/sidebar/DownloadProgressModal.tsx
import React from "react";
import ReactDOM from "react-dom";
import type { DownloadProgress } from "@/services/download";
import styles from "./DownloadProgressModal.module.css";

interface DownloadProgressModalProps {
  isOpen: boolean;
  progress: DownloadProgress;
  onClose: () => void;
}

const DownloadProgressModal: React.FC<DownloadProgressModalProps> = ({
  isOpen,
  progress,
  onClose,
}) => {
  if (!isOpen) return null;

  const modalContent = (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div
        className={styles.progressModal}
        onClick={(e) => e.stopPropagation()}
      >
        <h3>다운로드 진행 중...</h3>

        <div className={styles.progressBarContainer}>
          <div
            className={styles.progressBarFill}
            style={{ width: `${progress.percentage}%` }}
          />
        </div>

        <p className={styles.progressText}>
          {progress.current} / {progress.total} 페이지 ({progress.percentage}%)
        </p>

        {progress.percentage === 100 && (
          <div className={styles.successMessage}>✅ 다운로드 완료!</div>
        )}

        <button
          className={styles.closeBtn}
          disabled={progress.percentage < 100}
          onClick={onClose}
        >
          닫기
        </button>
      </div>
    </div>
  );

  return ReactDOM.createPortal(modalContent, document.body);
};

export default DownloadProgressModal;
```

```css
/* src/components/sidebar/DownloadProgressModal.module.css */
.modalOverlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.progressModal {
  background: white;
  border-radius: 8px;
  padding: var(--spacing-xl);
  min-width: 400px;
  max-width: 90vw;
}

.progressBarContainer {
  width: 100%;
  height: 20px;
  background: #e0e0e0;
  border-radius: 10px;
  overflow: hidden;
  margin: var(--spacing-lg) 0;
}

.progressBarFill {
  height: 100%;
  background: linear-gradient(90deg, #4caf50, #8bc34a);
  transition: width 0.3s ease;
}

.progressText {
  text-align: center;
  font-size: var(--font-md);
  color: var(--text-color);
  margin: var(--spacing-md) 0;
}

.successMessage {
  text-align: center;
  font-size: var(--font-lg);
  color: #4caf50;
  margin: var(--spacing-md) 0;
  font-weight: 600;
}

.closeBtn {
  width: 100%;
  padding: var(--spacing-md);
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: var(--font-md);
  transition: background 0.3s;
}

.closeBtn:hover:not(:disabled) {
  background: var(--primary-hover);
}

.closeBtn:disabled {
  background: #cccccc;
  cursor: not-allowed;
}
```

3. **Git 커밋** (30min)

```bash
git add .
git commit -m "feat: 통합 다운로드 기능 시작

- downloadService 생성
- DownloadProgressModal 컴포넌트
- 진행률 바 UI
- React Portal 사용"
```

**완료 조건**:

- ✅ 다운로드 서비스 로직 완성
- ✅ 진행률 모달 UI 완성

---

### Day 5 (3시간)

#### Task 2.5: IntegratedDownloadButton 완성 (3시간)

**세부 작업**:

1. **IntegratedDownloadButton 컴포넌트 생성** (2시간)

```typescript
// src/components/sidebar/IntegratedDownloadButton.tsx
import React, { useState } from "react";
import DownloadProgressModal from "./DownloadProgressModal";
import { downloadService, type DownloadProgress } from "@/services/download";
import styles from "./IntegratedDownloadButton.module.css";

interface IntegratedDownloadButtonProps {
  pages: any[];
}

const IntegratedDownloadButton: React.FC<IntegratedDownloadButtonProps> = ({
  pages,
}) => {
  const [isDownloading, setIsDownloading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [progress, setProgress] = useState<DownloadProgress>({
    current: 0,
    total: 0,
    percentage: 0,
  });

  const handleDownload = async () => {
    if (pages.length === 0) {
      alert("다운로드할 페이지가 없습니다.");
      return;
    }

    setIsDownloading(true);
    setShowModal(true);

    try {
      const results = await downloadService.downloadAllPages(pages, (p) =>
        setProgress(p)
      );

      // 성공한 결과만 처리
      const successResults = results.filter((r) => r.success);

      if (successResults.length === 0) {
        throw new Error("다운로드에 실패했습니다");
      }

      // TODO: ZIP 파일 생성 및 다운로드
      console.log("Download completed:", successResults);
    } catch (error) {
      console.error("Download error:", error);
      alert("다운로드 중 오류가 발생했습니다.");
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className={styles.integratedDownload}>
      <button
        className={styles.downloadBtn}
        disabled={isDownloading || pages.length === 0}
        onClick={handleDownload}
      >
        <span className={styles.icon}>📦</span>
        {isDownloading ? "다운로드 중..." : "통합 다운로드"}
      </button>

      <DownloadProgressModal
        isOpen={showModal}
        progress={progress}
        onClose={() => setShowModal(false)}
      />
    </div>
  );
};

export default IntegratedDownloadButton;
```

```css
/* src/components/sidebar/IntegratedDownloadButton.module.css */
.integratedDownload {
  margin-top: var(--spacing-lg);
}

.downloadBtn {
  width: 100%;
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(135deg, #2196f3, #1976d2);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: var(--font-md);
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
}

.downloadBtn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(33, 150, 243, 0.4);
}

.downloadBtn:disabled {
  background: #cccccc;
  cursor: not-allowed;
}

.icon {
  font-size: 20px;
}
```

2. **Sidebar에 추가** (30min)

```typescript
// src/components/sidebar/Sidebar.tsx 업데이트
import IntegratedDownloadButton from "./IntegratedDownloadButton";

const Sidebar: React.FC = () => {
  const [pages] = useState([]); // TODO: 실제 페이지 데이터 연동

  return (
    <div className={styles.sidebar}>
      <DocumentTypeSelector onChange={setDocumentType} />
      <ModelSelector
        selectedModel={selectedModel}
        isAutoSelected={isAutoSelected}
      />
      <AnalyzeButton
        isLoading={isAnalyzing}
        hasFiles={hasFiles}
        onClick={handleAnalyze}
      />

      <IntegratedDownloadButton pages={pages} />
    </div>
  );
};
```

3. **동작 테스트** (20min)

```markdown
[ ] 버튼 클릭 시 모달 표시
[ ] 진행률 바 업데이트
[ ] 다운로드 완료 시 성공 메시지
[ ] 닫기 버튼 동작
```

4. **Git 커밋** (10min)

```bash
git add .
git commit -m "feat: 통합 다운로드 기능 완성

- IntegratedDownloadButton 컴포넌트
- 다운로드 프로세스 구현
- 에러 처리 추가
- React Hooks 활용"
```

**완료 조건**:

- ✅ 여러 페이지 순차 다운로드
- ✅ 진행률 실시간 표시
- ✅ 에러 발생 시 alert

---

### Phase 2 완료 검증 (30분)

```markdown
## Phase 2 체크리스트

### Sidebar 컴포넌트

- [ ] DocumentTypeSelector 작동
- [ ] AI 모델 자동 선택
- [ ] 분석 시작 버튼 (로딩 상태)
- [ ] 통합 다운로드 버튼

### 기능 검증

- [ ] 문서 타입 변경 시 모델 자동 전환
- [ ] 파일 없을 때 버튼 비활성화
- [ ] 다운로드 진행률 표시
```

---

## Phase 3: 바운딩 박스 오버레이 & 에디터 (Day 6-7, 15시간)

### 목표

- SVG/Canvas 기반 바운딩 박스 렌더링
- 클릭 시 에디터 스크롤 연동
- TinyMCE 에디터 통합
- 2개 탭 시스템 (텍스트 편집 / AI 통계)

### Day 6 (8시간)

#### Task 3.1: 바운딩 박스 오버레이 기본 구조 (4시간)

**세부 작업**:

1. **좌표 스케일러 유틸리티** (1시간)

```typescript
// src/utils/coordinateScaler.ts
export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export class CoordinateScaler {
  private scaleX: number;
  private scaleY: number;

  constructor(
    originalWidth: number,
    originalHeight: number,
    displayWidth: number,
    displayHeight: number
  ) {
    this.scaleX = displayWidth / originalWidth;
    this.scaleY = displayHeight / originalHeight;
  }

  scale(bbox: BoundingBox): BoundingBox {
    return {
      x: bbox.x * this.scaleX,
      y: bbox.y * this.scaleY,
      width: bbox.width * this.scaleX,
      height: bbox.height * this.scaleY,
    };
  }

  scaleAll(bboxes: any[]): any[] {
    return bboxes.map((bbox) => ({
      ...bbox,
      coordinates: this.scale(bbox.coordinates),
    }));
  }

  getStrokeWidth(baseWidth: number = 2): number {
    const avgScale = (this.scaleX + this.scaleY) / 2;
    return Math.max(1, Math.min(baseWidth / avgScale, 4));
  }
}
```

2. **BoundingBoxOverlay 컴포넌트 생성 (SVG 방식)** (2시간)

```typescript
// src/components/viewer/BoundingBoxOverlay.tsx
import React, { useMemo } from "react";
import { CoordinateScaler } from "@/utils/coordinateScaler";
import styles from "./BoundingBoxOverlay.module.css";

interface BoundingBoxOverlayProps {
  bboxes: any[];
  imageSize: { width: number; height: number };
  displaySize: { width: number; height: number };
  onBoxClick?: (box: any) => void;
  onBoxHover?: (box: any) => void;
}

const CLASS_COLORS: Record<string, string> = {
  question_number: "#FF5722",
  question_text: "#2196F3",
  choices: "#4CAF50",
  title: "#9C27B0",
  paragraph: "#FF9800",
  table: "#00BCD4",
  figure: "#E91E63",
};

const BoundingBoxOverlay: React.FC<BoundingBoxOverlayProps> = ({
  bboxes,
  imageSize,
  displaySize,
  onBoxClick,
  onBoxHover,
}) => {
  const scaler = useMemo(() => {
    if (!imageSize || !displaySize) return null;
    return new CoordinateScaler(
      imageSize.width,
      imageSize.height,
      displaySize.width,
      displaySize.height
    );
  }, [imageSize, displaySize]);

  const scaledBoxes = useMemo(() => {
    if (!scaler || !bboxes) return [];
    return scaler.scaleAll(bboxes);
  }, [scaler, bboxes]);

  if (!scaler || scaledBoxes.length === 0) {
    return null;
  }

  const strokeWidth = scaler.getStrokeWidth();

  return (
    <svg
      className={styles.boundingBoxOverlay}
      width={displaySize.width}
      height={displaySize.height}
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        pointerEvents: "none",
      }}
    >
      <g className={styles.bboxGroup}>
        {scaledBoxes.map((box, index) => {
          const coords = box.coordinates;
          const color = CLASS_COLORS[box.class] || "#999999";

          return (
            <g
              key={box.id || index}
              style={{ cursor: "pointer", pointerEvents: "all" }}
              onClick={() => onBoxClick?.(box)}
              onMouseEnter={() => onBoxHover?.(box)}
            >
              {/* 반투명 배경 */}
              <rect
                x={coords.x}
                y={coords.y}
                width={coords.width}
                height={coords.height}
                fill={color}
                fillOpacity={0.2}
                stroke={color}
                strokeWidth={strokeWidth}
                strokeOpacity={0.8}
                rx={2}
              />

              {/* 클래스 라벨 (호버 시만 표시하도록 나중에 개선) */}
              <text
                x={coords.x + 5}
                y={coords.y + 15}
                fontSize={12}
                fill={color}
                fontWeight="600"
                style={{ pointerEvents: "none" }}
              >
                {box.class} ({Math.round(box.confidence * 100)}%)
              </text>
            </g>
          );
        })}
      </g>
    </svg>
  );
};

export default BoundingBoxOverlay;
```

```css
/* src/components/viewer/BoundingBoxOverlay.module.css */
.boundingBoxOverlay {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
  pointer-events: none;
}

.bboxGroup {
  pointer-events: all;
}

.bboxGroup g {
  transition: all 0.2s ease;
}

.bboxGroup g:hover rect {
  filter: brightness(1.1);
}
```

3. **LayoutPanel 컴포넌트 생성** (40min)

```typescript
// src/components/viewer/LayoutPanel.tsx
import React, { useState, useRef, useEffect } from "react";
import ImageViewer from "./ImageViewer";
import BoundingBoxOverlay from "./BoundingBoxOverlay";
import styles from "./LayoutPanel.module.css";

const LayoutPanel: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [displaySize, setDisplaySize] = useState({ width: 0, height: 0 });

  // TODO: 실제 데이터 연동
  const currentImage = {
    url: "",
    originalSize: { width: 2000, height: 3000 },
  };

  const analysisResult = {
    bboxes: [],
  };

  const updateSize = () => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setDisplaySize({
      width: rect.width,
      height: rect.height,
    });
  };

  useEffect(() => {
    updateSize();
    window.addEventListener("resize", updateSize);
    return () => window.removeEventListener("resize", updateSize);
  }, []);

  const handleBoxClick = (box: any) => {
    console.log("Box clicked:", box);
    // TODO: 에디터로 스크롤
  };

  const handleBoxHover = (box: any) => {
    console.log("Box hovered:", box);
  };

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      <ImageViewer image={currentImage} />

      {analysisResult.bboxes.length > 0 && (
        <BoundingBoxOverlay
          bboxes={analysisResult.bboxes}
          imageSize={currentImage.originalSize}
          displaySize={displaySize}
          onBoxClick={handleBoxClick}
          onBoxHover={handleBoxHover}
        />
      )}
    </div>
  );
};

export default LayoutPanel;
```

```css
/* src/components/viewer/LayoutPanel.module.css */
.layoutPanel {
  position: relative;
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  overflow: hidden;
}
```

4. **Git 커밋** (20min)

```bash
git add .
git commit -m "feat: 바운딩 박스 오버레이 기본 구조 구현 (SVG)

- CoordinateScaler 유틸리티
- BoundingBoxOverlay 컴포넌트
- 클래스별 색상 구분
- 클릭/호버 이벤트
- React Hooks 활용"
```

**완료 조건**:

- ✅ SVG 오버레이 렌더링
- ✅ 좌표 스케일링 작동
- ✅ 클래스별 색상 구분

---

#### Task 3.2: 바운딩 박스 인터랙션 (2시간)

**세부 작업**:

1. **useBoundingBox Custom Hook** (45min)

```typescript
// src/hooks/useBoundingBox.ts
import { useCallback, RefObject } from "react";

export const useBoundingBox = (editorRef: RefObject<HTMLElement>) => {
  const scrollToEditor = useCallback(
    (boxId: string) => {
      if (!editorRef.current) return;

      const element = editorRef.current.querySelector(
        `[data-bbox-id="${boxId}"]`
      );

      if (element) {
        element.scrollIntoView({
          behavior: "smooth",
          block: "center",
        });

        // 하이라이트 효과
        element.classList.add("highlight");
        setTimeout(() => {
          element.classList.remove("highlight");
        }, 2000);
      }
    },
    [editorRef]
  );

  const getTooltipInfo = useCallback((box: any) => {
    return {
      title: box.class,
      confidence: `${Math.round(box.confidence * 100)}%`,
      text: box.text?.substring(0, 50) + (box.text?.length > 50 ? "..." : ""),
      position: box.coordinates,
    };
  }, []);

  return {
    scrollToEditor,
    getTooltipInfo,
  };
};
```

2. **BoundingBoxTooltip 컴포넌트 생성** (45min)

```typescript
// src/components/viewer/BoundingBoxTooltip.tsx
import React from "react";
import ReactDOM from "react-dom";
import styles from "./BoundingBoxTooltip.module.css";

interface BoundingBoxTooltipProps {
  info: any;
  position: any;
  isVisible: boolean;
}

const BoundingBoxTooltip: React.FC<BoundingBoxTooltipProps> = ({
  info,
  position,
  isVisible,
}) => {
  if (!isVisible || !info) return null;

  const tooltipContent = (
    <div
      className={styles.bboxTooltip}
      style={{
        left: `${position.x + position.width / 2}px`,
        top: `${position.y - 10}px`,
        transform: "translate(-50%, -100%)",
      }}
    >
      <div className={styles.tooltipHeader}>
        <strong>{info.title}</strong>
        <span className={styles.confidenceBadge}>{info.confidence}</span>
      </div>
      {info.text && <div className={styles.tooltipContent}>{info.text}</div>}
      <div className={styles.tooltipArrow} />
    </div>
  );

  return ReactDOM.createPortal(tooltipContent, document.body);
};

export default BoundingBoxTooltip;
```

```css
/* src/components/viewer/BoundingBoxTooltip.module.css */
.bboxTooltip {
  position: fixed;
  background: rgba(0, 0, 0, 0.9);
  color: white;
  padding: var(--spacing-md);
  border-radius: 6px;
  max-width: 300px;
  z-index: 1000;
  pointer-events: none;
}

.tooltipHeader {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.confidenceBadge {
  font-size: var(--font-xs);
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
}

.tooltipContent {
  font-size: var(--font-sm);
  color: rgba(255, 255, 255, 0.9);
}

.tooltipArrow {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 6px solid rgba(0, 0, 0, 0.9);
}
```

3. **BoundingBoxOverlay에 툴팁 통합** (20min)

```typescript
// src/components/viewer/BoundingBoxOverlay.tsx 업데이트
import { useState } from 'react'
import BoundingBoxTooltip from './BoundingBoxTooltip'
import { useBoundingBox } from '@/hooks/useBoundingBox'

const BoundingBoxOverlay: React.FC<BoundingBoxOverlayProps> = ({ ... }) => {
  const [hoveredBox, setHoveredBox] = useState<any>(null)
  const editorRef = useRef(null) // TODO: 실제 에디터 ref 전달

  const { scrollToEditor, getTooltipInfo } = useBoundingBox(editorRef)

  const handleBoxClick = (box: any) => {
    scrollToEditor(box.id)
    onBoxClick?.(box)
  }

  const handleBoxHover = (box: any) => {
    setHoveredBox(box)
    onBoxHover?.(box)
  }

  const tooltipInfo = hoveredBox ? getTooltipInfo(hoveredBox) : null

  return (
    <>
      <svg ...>
        {/* ... SVG 내용 ... */}
      </svg>

      <BoundingBoxTooltip
        info={tooltipInfo}
        position={hoveredBox?.coordinates}
        isVisible={!!hoveredBox}
      />
    </>
  )
}
```

4. **Git 커밋** (10min)

```bash
git add .
git commit -m "feat: 바운딩 박스 인터랙션 구현

- 클릭 시 에디터 스크롤 (준비)
- 호버 시 툴팁 표시
- useBoundingBox Hook
- React Portal 활용
- 하이라이트 애니메이션"
```

**완료 조건**:

- ✅ 바운딩 박스 클릭 → 에디터 스크롤 (준비)
- ✅ 호버 시 툴팁 표시

---

#### Task 3.3: LayoutPanel 완전 통합 (2시간)

**세부 작업**:

1. **ImageViewer 컴포넌트 기본 구조** (1시간)

```typescript
// src/components/viewer/ImageViewer.tsx
import React, { useState, useRef } from "react";
import styles from "./ImageViewer.module.css";

interface ImageViewerProps {
  image: {
    url: string;
    originalSize: { width: number; height: number };
  };
}

const ImageViewer: React.FC<ImageViewerProps> = ({ image }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });

  const zoomIn = () => {
    setZoom((prev) => Math.min(prev + 0.1, 5));
  };

  const zoomOut = () => {
    setZoom((prev) => Math.max(prev - 0.1, 0.1));
  };

  const resetZoom = () => {
    setZoom(1);
    setPosition({ x: 0, y: 0 });
  };

  return (
    <div className={styles.imageViewer} ref={containerRef}>
      <div className={styles.viewerToolbar}>
        <button onClick={zoomOut}>🔍-</button>
        <span>{Math.round(zoom * 100)}%</span>
        <button onClick={zoomIn}>🔍+</button>
        <button onClick={resetZoom}>원본</button>
      </div>

      <div
        className={styles.imageContainer}
        style={{
          transform: `scale(${zoom}) translate(${position.x}px, ${position.y}px)`,
        }}
      >
        {image.url && <img src={image.url} alt="Document" />}
      </div>
    </div>
  );
};

export default ImageViewer;
```

```css
/* src/components/viewer/ImageViewer.module.css */
.imageViewer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.viewerToolbar {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: white;
  border-bottom: 1px solid var(--border-color);
}

.viewerToolbar button {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-color);
  background: white;
  border-radius: 4px;
  cursor: pointer;
}

.viewerToolbar button:hover {
  background: var(--bg-color);
}

.imageContainer {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  transition: transform 0.3s;
}

.imageContainer img {
  max-width: 100%;
  height: auto;
  display: block;
}
```

2. **LayoutPanel 최종 통합** (40min)

```typescript
// src/components/viewer/LayoutPanel.tsx 완전 업데이트
import React, { useState, useRef, useEffect } from "react";
import ImageViewer from "./ImageViewer";
import BoundingBoxOverlay from "./BoundingBoxOverlay";
import styles from "./LayoutPanel.module.css";

const LayoutPanel: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [displaySize, setDisplaySize] = useState({ width: 0, height: 0 });

  // TODO: Context나 Props로 실제 데이터 연동
  const currentImage = {
    url: "",
    originalSize: { width: 2000, height: 3000 },
  };

  const analysisResult = {
    bboxes: [],
  };

  const updateSize = () => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setDisplaySize({
      width: rect.width,
      height: rect.height,
    });
  };

  useEffect(() => {
    updateSize();
    window.addEventListener("resize", updateSize);
    return () => window.removeEventListener("resize", updateSize);
  }, [currentImage]);

  const handleBoxClick = (box: any) => {
    console.log("Box clicked:", box);
    // TODO: 에디터로 스크롤
  };

  const handleBoxHover = (box: any) => {
    console.log("Box hovered:", box);
  };

  return (
    <div className={styles.layoutPanel} ref={containerRef}>
      <ImageViewer image={currentImage} />

      {analysisResult && analysisResult.bboxes.length > 0 && (
        <BoundingBoxOverlay
          bboxes={analysisResult.bboxes}
          imageSize={currentImage.originalSize}
          displaySize={displaySize}
          onBoxClick={handleBoxClick}
          onBoxHover={handleBoxHover}
        />
      )}
    </div>
  );
};

export default LayoutPanel;
```

3. **Git 커밋** (20min)

```bash
git add .
git commit -m "feat: LayoutPanel 완전 통합

- ImageViewer 기본 기능 (줌, 리셋)
- BoundingBoxOverlay 연동
- 반응형 크기 계산
- 이미지 위 오버레이 배치"
```

**완료 조건**:

- ✅ 이미지 뷰어 작동
- ✅ 바운딩 박스 정확히 표시
- ✅ 크기 변경 시 자동 조정

---

### Day 7 (7시간)

#### Task 3.4: EditorPanel 기본 구조 (2시간)

**세부 작업**:

1. **EditorPanel 컴포넌트 생성** (1시간)

```typescript
// src/components/editor/EditorPanel.tsx
import React, { useState } from "react";
import TextEditorTab from "./TextEditorTab";
import AIStatsTab from "./AIStatsTab";
import styles from "./EditorPanel.module.css";

type TabName = "text" | "stats";

const EditorPanel: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabName>("text");
  const [content, setContent] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    // TODO: API 호출
    setTimeout(() => {
      setIsSaving(false);
    }, 1000);
  };

  const handleNext = () => {
    console.log("Next page");
    // TODO: 다음 페이지로 이동
  };

  return (
    <div className={styles.editorPanel}>
      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${
            activeTab === "text" ? styles.active : ""
          }`}
          onClick={() => setActiveTab("text")}
        >
          📝 텍스트 편집
        </button>
        <button
          className={`${styles.tab} ${
            activeTab === "stats" ? styles.active : ""
          }`}
          onClick={() => setActiveTab("stats")}
        >
          🎨 AI 통계
        </button>
      </div>

      <div className={styles.tabContent}>
        {activeTab === "text" ? (
          <TextEditorTab
            content={content}
            onChange={setContent}
            isSaving={isSaving}
            onSave={handleSave}
            onNext={handleNext}
          />
        ) : (
          <AIStatsTab />
        )}
      </div>
    </div>
  );
};

export default EditorPanel;
```

```css
/* src/components/editor/EditorPanel.module.css */
.editorPanel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: white;
}

.tabs {
  display: flex;
  border-bottom: 2px solid var(--border-color);
  background: #f9f9f9;
}

.tab {
  flex: 1;
  padding: var(--spacing-md) var(--spacing-lg);
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: var(--font-md);
  font-weight: 600;
  color: #666;
  transition: all 0.3s;
}

.tab:hover {
  background: rgba(0, 188, 212, 0.05);
}

.tab.active {
  color: var(--primary-color);
  background: white;
  border-bottom: 3px solid var(--primary-color);
}

.tabContent {
  flex: 1;
  overflow: hidden;
}
```

2. **TextEditorTab 기본 구조** (40min)

```typescript
// src/components/editor/TextEditorTab.tsx
import React, { useRef } from "react";
import { Editor } from "@tinymce/tinymce-react";
import ActionButtons from "./ActionButtons";
import styles from "./TextEditorTab.module.css";

interface TextEditorTabProps {
  content: string;
  onChange: (value: string) => void;
  isSaving?: boolean;
  onSave: () => void;
  onNext: () => void;
}

const TextEditorTab: React.FC<TextEditorTabProps> = ({
  content,
  onChange,
  isSaving = false,
  onSave,
  onNext,
}) => {
  const editorRef = useRef<any>(null);

  return (
    <div className={styles.textEditorTab}>
      <div className={styles.editorContainer}>
        <Editor
          apiKey="your-tinymce-api-key" // TODO: 실제 API 키로 교체
          onInit={(evt, editor) => (editorRef.current = editor)}
          value={content}
          onEditorChange={onChange}
          init={{
            height: "100%",
            menubar: false,
            plugins: [
              "advlist",
              "autolink",
              "lists",
              "link",
              "charmap",
              "preview",
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
              "undo redo | formatselect | bold italic | " +
              "alignleft aligncenter alignright | " +
              "bullist numlist | removeformat | help",
          }}
        />
      </div>

      <ActionButtons isSaving={isSaving} onSave={onSave} onNext={onNext} />
    </div>
  );
};

export default TextEditorTab;
```

```css
/* src/components/editor/TextEditorTab.module.css */
.textEditorTab {
  display: grid;
  grid-template-rows: 1fr auto;
  height: 100%;
}

.editorContainer {
  overflow: hidden;
}
```

3. **Git 커밋** (20min)

```bash
git add .
git commit -m "feat: EditorPanel 기본 구조 구현

- 2개 탭 시스템 (텍스트/통계)
- TinyMCE 에디터 통합
- ActionButtons 준비
- TypeScript 타입 정의"
```

**완료 조건**:

- ✅ 탭 전환 작동
- ✅ TinyMCE 렌더링

---

#### Task 3.5: ActionButtons 구현 (2시간)

**세부 작업**:

1. **ActionButtons 컴포넌트 생성** (1시간)

```typescript
// src/components/editor/ActionButtons.tsx
import React from "react";
import styles from "./ActionButtons.module.css";

interface ActionButtonsProps {
  isSaving?: boolean;
  hasNext?: boolean;
  onSave: () => void;
  onNext: () => void;
}

const ActionButtons: React.FC<ActionButtonsProps> = ({
  isSaving = false,
  hasNext = true,
  onSave,
  onNext,
}) => {
  return (
    <div className={styles.actionButtons}>
      <button className={styles.saveBtn} disabled={isSaving} onClick={onSave}>
        {isSaving ? (
          <>
            <span className={styles.spinner}></span>
            저장 중...
          </>
        ) : (
          <>
            <span className={styles.icon}>💾</span>
            저장
          </>
        )}
      </button>

      <button className={styles.nextBtn} disabled={!hasNext} onClick={onNext}>
        <span className={styles.icon}>▶️</span>
        다음 페이지
      </button>
    </div>
  );
};

export default ActionButtons;
```

```css
/* src/components/editor/ActionButtons.module.css */
.actionButtons {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: #f9f9f9;
  border-top: 1px solid var(--border-color);
}

.actionButtons button {
  flex: 1;
  padding: var(--spacing-md) var(--spacing-lg);
  border: none;
  border-radius: 6px;
  font-size: var(--font-md);
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  transition: all 0.3s;
}

.saveBtn {
  background: linear-gradient(135deg, #2196f3, #1976d2);
  color: white;
  box-shadow: 0 2px 8px rgba(33, 150, 243, 0.3);
}

.saveBtn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(33, 150, 243, 0.4);
}

.saveBtn:disabled {
  background: #cccccc;
  cursor: not-allowed;
  box-shadow: none;
}

.nextBtn {
  background: linear-gradient(135deg, #4caf50, #45a049);
  color: white;
  box-shadow: 0 2px 8px rgba(76, 175, 80, 0.3);
}

.nextBtn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.4);
}

.nextBtn:disabled {
  background: #cccccc;
  cursor: not-allowed;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid white;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.icon {
  font-size: 18px;
}
```

2. **자동 저장 Custom Hook** (45min)

```typescript
// src/hooks/useAutoSave.ts
import { useEffect, useRef } from "react";

export const useAutoSave = (
  content: string,
  onSave: (content: string) => void | Promise<void>,
  delay: number = 500
) => {
  const timeoutRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    // 이전 timeout 취소
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    // 새로운 timeout 설정
    timeoutRef.current = setTimeout(() => {
      console.log("Auto-saving...", content);
      onSave(content);
    }, delay);

    // cleanup
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [content, onSave, delay]);
};
```

3. **TextEditorTab에 자동 저장 적용** (10min)

```typescript
// src/components/editor/TextEditorTab.tsx 업데이트
import { useAutoSave } from '@/hooks/useAutoSave'

const TextEditorTab: React.FC<TextEditorTabProps> = ({ ... }) => {
  useAutoSave(content, (value) => {
    console.log('Auto-saved:', value)
    // TODO: API 호출
  })

  // ... 기존 코드
}
```

4. **Git 커밋** (5min)

```bash
git add .
git commit -m "feat: ActionButtons 및 자동 저장 구현

- 저장/다음 버튼 UI
- 로딩 상태 spinner
- useAutoSave Hook (500ms debounce)
- 호버 효과"
```

**완료 조건**:

- ✅ 버튼 클릭 시 이벤트 발생
- ✅ 자동 저장 500ms 후 실행

---

#### Task 3.6: AIStatsTab 구현 (3시간)

**세부 작업**:

1. **AIStatsTab 컴포넌트 생성** (2시간)

```typescript
// src/components/editor/AIStatsTab.tsx
import React, { useMemo } from "react";
import styles from "./AIStatsTab.module.css";

const AIStatsTab: React.FC = () => {
  // TODO: 실제 데이터 연동
  const analysisResult = {
    totalElements: 38,
    questionCount: 5,
    processingTime: 2.5,
    classDistribution: {
      question_number: 5,
      question_text: 5,
      choices: 15,
      figure: 3,
      table: 1,
    },
    confidenceScores: {
      question_number: 0.95,
      question_text: 0.92,
      choices: 0.88,
      figure: 0.85,
      table: 0.9,
    },
  };

  const statCards = useMemo(
    () => [
      {
        icon: "📊",
        label: "총 요소 개수",
        value: analysisResult.totalElements,
        color: "#2196F3",
      },
      {
        icon: "❓",
        label: "문제 개수",
        value: analysisResult.questionCount,
        color: "#4CAF50",
      },
      {
        icon: "⏱️",
        label: "처리 시간",
        value: `${analysisResult.processingTime}초`,
        color: "#FF9800",
      },
    ],
    [analysisResult]
  );

  const distributionData = useMemo(() => {
    const entries = Object.entries(analysisResult.classDistribution);
    const maxCount = Math.max(...entries.map(([, count]) => count as number));

    return entries.map(([className, count]) => ({
      className,
      count,
      percentage: ((count as number) / maxCount) * 100,
    }));
  }, [analysisResult]);

  return (
    <div className={styles.aiStatsTab}>
      {/* 통계 카드 */}
      <div className={styles.statCards}>
        {statCards.map((card) => (
          <div
            key={card.label}
            className={styles.statCard}
            style={{ borderColor: card.color }}
          >
            <div className={styles.cardIcon} style={{ color: card.color }}>
              {card.icon}
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardValue}>{card.value}</div>
              <div className={styles.cardLabel}>{card.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 클래스별 분포 */}
      <div className={styles.classDistribution}>
        <h3>클래스별 분포</h3>
        <div className={styles.distributionBars}>
          {distributionData.map((item) => (
            <div key={item.className} className={styles.distributionItem}>
              <div className={styles.itemLabel}>{item.className}</div>
              <div className={styles.barContainer}>
                <div
                  className={styles.barFill}
                  style={{ width: `${item.percentage}%` }}
                />
                <span className={styles.barValue}>{item.count}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 상세 정보 테이블 */}
      <div className={styles.detailTable}>
        <h3>상세 정보</h3>
        <table>
          <thead>
            <tr>
              <th>클래스</th>
              <th>개수</th>
              <th>평균 신뢰도</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(analysisResult.classDistribution).map(
              ([className, count]) => (
                <tr key={className}>
                  <td>{className}</td>
                  <td>{count}</td>
                  <td>
                    {(
                      analysisResult.confidenceScores[
                        className as keyof typeof analysisResult.confidenceScores
                      ] * 100
                    ).toFixed(1)}
                    %
                  </td>
                </tr>
              )
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AIStatsTab;
```

```css
/* src/components/editor/AIStatsTab.module.css */
.aiStatsTab {
  height: 100%;
  overflow-y: auto;
  padding: var(--spacing-lg);
  background: #f9f9f9;
}

.statCards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.statCard {
  background: white;
  border-left: 4px solid;
  border-radius: 8px;
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.cardIcon {
  font-size: 36px;
}

.cardValue {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-color);
}

.cardLabel {
  font-size: var(--font-sm);
  color: #666;
}

.classDistribution,
.detailTable {
  background: white;
  border-radius: 8px;
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.classDistribution h3,
.detailTable h3 {
  margin-bottom: var(--spacing-md);
}

.distributionBars {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.distributionItem {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.itemLabel {
  width: 150px;
  font-size: var(--font-sm);
  font-weight: 600;
}

.barContainer {
  flex: 1;
  height: 24px;
  background: #e0e0e0;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}

.barFill {
  height: 100%;
  background: linear-gradient(90deg, #2196f3, #00bcd4);
  transition: width 0.5s ease;
}

.barValue {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: var(--font-sm);
  font-weight: 600;
}

.detailTable table {
  width: 100%;
  border-collapse: collapse;
}

.detailTable th,
.detailTable td {
  padding: var(--spacing-md);
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.detailTable th {
  background: #f9f9f9;
  font-weight: 600;
}

/* 반응형 */
@media (max-width: 1599px) {
  .statCards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1365px) {
  .statCards {
    grid-template-columns: 1fr;
  }
}
```

2. **Git 커밋** (1시간)

```bash
git add .
git commit -m "feat: AIStatsTab 완전 구현

- 통계 카드 3개
- 클래스별 분포 막대 그래프
- 상세 정보 테이블
- 반응형 Grid (3→2→1열)
- useMemo로 성능 최적화"
```

**완료 조건**:

- ✅ 통계 카드 표시
- ✅ 막대 그래프 작동
- ✅ 반응형 레이아웃

---

### Phase 3 완료 검증 (30분)

```markdown
## Phase 3 체크리스트

### 바운딩 박스

- [ ] SVG 렌더링
- [ ] 클래스별 색상 구분
- [ ] 호버 툴팁
- [ ] 클릭 이벤트 (준비)

### 에디터

- [ ] 2개 탭 전환
- [ ] TinyMCE 작동
- [ ] ActionButtons 동작
- [ ] 자동 저장 (500ms)

### AI 통계

- [ ] 통계 카드 3개
- [ ] 분포 그래프
- [ ] 상세 테이블
- [ ] 반응형 Grid
```

---

## Phase 4: Context & API 통합 & 테스트 (Day 8, 14시간)

### 목표

- React Context로 전역 상태 관리
- API 연동
- 반응형 E2E 테스트
- 성능 최적화
- 최종 검증

### Day 8 (8시간)

#### Task 4.1: React Context 구현 (3시간)

**세부 작업**:

1. **ProjectContext 생성** (1시간)

```typescript
// src/contexts/ProjectContext.tsx
import React, { createContext, useContext, useReducer, ReactNode } from "react";

export type DocumentType = "worksheet" | "document";
export type AIModel = "smarteye" | "doclayout";

interface ProjectState {
  projectId: string | null;
  documentType: DocumentType;
  selectedModel: AIModel;
  isAnalyzing: boolean;
}

type ProjectAction =
  | { type: "SET_DOCUMENT_TYPE"; payload: DocumentType }
  | { type: "SET_ANALYZING"; payload: boolean }
  | { type: "SET_PROJECT_ID"; payload: string };

const initialState: ProjectState = {
  projectId: null,
  documentType: "worksheet",
  selectedModel: "smarteye",
  isAnalyzing: false,
};

const ProjectContext = createContext<
  | {
      state: ProjectState;
      dispatch: React.Dispatch<ProjectAction>;
    }
  | undefined
>(undefined);

function projectReducer(
  state: ProjectState,
  action: ProjectAction
): ProjectState {
  switch (action.type) {
    case "SET_DOCUMENT_TYPE":
      return {
        ...state,
        documentType: action.payload,
        selectedModel:
          action.payload === "worksheet" ? "smarteye" : "doclayout",
      };
    case "SET_ANALYZING":
      return {
        ...state,
        isAnalyzing: action.payload,
      };
    case "SET_PROJECT_ID":
      return {
        ...state,
        projectId: action.payload,
      };
    default:
      return state;
  }
}

export const ProjectProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(projectReducer, initialState);

  return (
    <ProjectContext.Provider value={{ state, dispatch }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProject = () => {
  const context = useContext(ProjectContext);
  if (!context) {
    throw new Error("useProject must be used within ProjectProvider");
  }
  return context;
};
```

2. **PagesContext 생성** (1시간)

```typescript
// src/contexts/PagesContext.tsx
import React, { createContext, useContext, useReducer, ReactNode } from "react";

export interface Page {
  id: string;
  pageNumber: number;
  imagePath: string;
  thumbnailPath: string;
  analysisStatus: "pending" | "processing" | "completed" | "error";
}

interface PagesState {
  pages: Page[];
  currentPageId: string | null;
}

type PagesAction =
  | { type: "ADD_PAGE"; payload: Page }
  | { type: "SET_CURRENT_PAGE"; payload: string }
  | {
      type: "UPDATE_PAGE_STATUS";
      payload: { id: string; status: Page["analysisStatus"] };
    };

const initialState: PagesState = {
  pages: [],
  currentPageId: null,
};

const PagesContext = createContext<
  | {
      state: PagesState;
      dispatch: React.Dispatch<PagesAction>;
    }
  | undefined
>(undefined);

function pagesReducer(state: PagesState, action: PagesAction): PagesState {
  switch (action.type) {
    case "ADD_PAGE":
      return {
        ...state,
        pages: [...state.pages, action.payload],
      };
    case "SET_CURRENT_PAGE":
      return {
        ...state,
        currentPageId: action.payload,
      };
    case "UPDATE_PAGE_STATUS":
      return {
        ...state,
        pages: state.pages.map((page) =>
          page.id === action.payload.id
            ? { ...page, analysisStatus: action.payload.status }
            : page
        ),
      };
    default:
      return state;
  }
}

export const PagesProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(pagesReducer, initialState);

  return (
    <PagesContext.Provider value={{ state, dispatch }}>
      {children}
    </PagesContext.Provider>
  );
};

export const usePages = () => {
  const context = useContext(PagesContext);
  if (!context) {
    throw new Error("usePages must be used within PagesProvider");
  }
  return context;
};
```

3. **LayoutContext 생성** (40min)

```typescript
// src/contexts/LayoutContext.tsx
import React, { createContext, useContext, useReducer, ReactNode } from "react";

export interface LayoutElement {
  id: string;
  class: string;
  confidence: number;
  bbox: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  text?: string;
}

interface LayoutState {
  elements: LayoutElement[];
  selectedElementId: string | null;
}

type LayoutAction =
  | { type: "SET_ELEMENTS"; payload: LayoutElement[] }
  | { type: "SELECT_ELEMENT"; payload: string };

const initialState: LayoutState = {
  elements: [],
  selectedElementId: null,
};

const LayoutContext = createContext<
  | {
      state: LayoutState;
      dispatch: React.Dispatch<LayoutAction>;
    }
  | undefined
>(undefined);

function layoutReducer(state: LayoutState, action: LayoutAction): LayoutState {
  switch (action.type) {
    case "SET_ELEMENTS":
      return {
        ...state,
        elements: action.payload,
      };
    case "SELECT_ELEMENT":
      return {
        ...state,
        selectedElementId: action.payload,
      };
    default:
      return state;
  }
}

export const LayoutProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [state, dispatch] = useReducer(layoutReducer, initialState);

  return (
    <LayoutContext.Provider value={{ state, dispatch }}>
      {children}
    </LayoutContext.Provider>
  );
};

export const useLayout = () => {
  const context = useContext(LayoutContext);
  if (!context) {
    throw new Error("useLayout must be used within LayoutProvider");
  }
  return context;
};
```

4. **App.tsx에 Context Providers 추가** (20min)

```typescript
// src/App.tsx 업데이트
import { ProjectProvider } from "@/contexts/ProjectContext";
import { PagesProvider } from "@/contexts/PagesContext";
import { LayoutProvider } from "@/contexts/LayoutContext";

const App: React.FC = () => {
  return (
    <ProjectProvider>
      <PagesProvider>
        <LayoutProvider>
          <div className="app">
            <header className="app-header">
              <h1>🔍 SmartEyeSsen 학습지 분석</h1>
              <p>AI 기반 학습지 OCR 및 구조 분석 시스템</p>
            </header>
            <MainLayout />
          </div>
        </LayoutProvider>
      </PagesProvider>
    </ProjectProvider>
  );
};
```

**완료 조건**:

- ✅ 3개 Context 생성
- ✅ 전역 상태 관리 작동

---

#### Task 4.2: API 서비스 레이어 (2시간)

**세부 작업**:

1. **api.ts 기본 설정** (30min)

```typescript
// src/services/api.ts
import axios from "axios";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api",
  timeout: 30000,
  headers: {
    "Content-Type": "application/json",
  },
});

// 요청 인터셉터
apiClient.interceptors.request.use(
  (config) => {
    // TODO: 토큰 추가 등
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터
apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error("API Error:", error);
    return Promise.reject(error);
  }
);

export default apiClient;
```

2. **analysis.ts 서비스** (1시간)

```typescript
// src/services/analysis.ts
import apiClient from "./api";

export interface AnalyzeRequest {
  image: File;
  documentType: "worksheet" | "document";
  analysisMode: "cim" | "basic";
}

export interface AnalyzeResponse {
  page_id: string;
  layout_analysis: any;
  text_content: any[];
  ai_descriptions: any[];
}

export const analysisService = {
  async analyzeImage(data: AnalyzeRequest): Promise<AnalyzeResponse> {
    const formData = new FormData();
    formData.append("image", data.image);
    formData.append("document_type", data.documentType);
    formData.append("analysis_mode", data.analysisMode);

    return apiClient.post("/analyze", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },

  async getVisualizationData(pageId: string) {
    return apiClient.get(`/pages/${pageId}/visualization-data`);
  },

  async saveText(pageId: string, content: string) {
    return apiClient.post(`/pages/${pageId}/text`, { content });
  },

  async formatText(pageId: string) {
    return apiClient.post(`/format`, { page_id: pageId });
  },
};
```

3. **컴포넌트에 API 연동** (30min)

```typescript
// src/components/sidebar/Sidebar.tsx에서 사용 예시
import { analysisService } from "@/services/analysis";
import { useProject } from "@/contexts/ProjectContext";

const Sidebar: React.FC = () => {
  const { state, dispatch } = useProject();

  const handleAnalyze = async () => {
    if (!selectedFile) {
      alert("파일을 먼저 업로드해주세요");
      return;
    }

    dispatch({ type: "SET_ANALYZING", payload: true });

    try {
      const result = await analysisService.analyzeImage({
        image: selectedFile,
        documentType: state.documentType,
        analysisMode: "cim",
      });

      console.log("Analysis result:", result);
      // TODO: Context 업데이트
    } catch (error) {
      console.error("Analysis failed:", error);
      alert("분석 중 오류가 발생했습니다");
    } finally {
      dispatch({ type: "SET_ANALYZING", payload: false });
    }
  };

  // ...
};
```

**완료 조건**:

- ✅ API 클라이언트 설정
- ✅ 분석 서비스 구현
- ✅ 에러 처리

---

#### Task 4.3: 반응형 E2E 테스트 (3시간)

**세부 작업**:

1. **테스트 환경 설정** (30min)

```typescript
// vitest.config.ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "happy-dom",
    globals: true,
    setupFiles: "./src/__tests__/setup.ts",
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
```

```typescript
// src/__tests__/setup.ts
import "@testing-library/jest-dom";
```

2. **Grid 레이아웃 테스트** (1시간)

```typescript
// src/__tests__/integration/GridLayout.test.tsx
import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import MainLayout from "@/components/layout/MainLayout";
import { ProjectProvider } from "@/contexts/ProjectContext";

const renderWithContext = (component: React.ReactElement) => {
  return render(<ProjectProvider>{component}</ProjectProvider>);
};

describe("Grid Layout", () => {
  it("renders 4-column grid layout", () => {
    renderWithContext(<MainLayout />);

    expect(screen.getByTestId("sidebar")).toBeInTheDocument();
    expect(screen.getByTestId("page-slider")).toBeInTheDocument();
    expect(screen.getByTestId("layout-panel")).toBeInTheDocument();
    expect(screen.getByTestId("editor-panel")).toBeInTheDocument();
  });

  it("toggles slider on close button click", async () => {
    renderWithContext(<MainLayout />);

    const closeBtn = screen.getByLabelText("슬라이더 닫기");
    fireEvent.click(closeBtn);

    expect(screen.queryByTestId("page-slider")).not.toBeInTheDocument();
    expect(screen.getByLabelText("페이지 슬라이더 열기")).toBeInTheDocument();
  });

  it("restores slider on restore button click", async () => {
    renderWithContext(<MainLayout />);

    // 먼저 닫기
    const closeBtn = screen.getByLabelText("슬라이더 닫기");
    fireEvent.click(closeBtn);

    // 다시 열기
    const restoreBtn = screen.getByLabelText("페이지 슬라이더 열기");
    fireEvent.click(restoreBtn);

    expect(screen.getByTestId("page-slider")).toBeInTheDocument();
  });
});
```

3. **반응형 테스트** (1시간)

```typescript
// src/__tests__/integration/Responsive.test.ts
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useResponsive } from "@/hooks/useResponsive";

describe("Responsive Behavior", () => {
  let originalInnerWidth: number;

  beforeEach(() => {
    originalInnerWidth = window.innerWidth;
  });

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
  });

  const testBreakpoint = (width: number, expected: string) => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: width,
    });

    const { result } = renderHook(() => useResponsive());
    expect(result.current.breakpoint).toBe(expected);
  };

  it("returns xs breakpoint for 1280px", () => {
    testBreakpoint(1280, "xs");
  });

  it("returns sm breakpoint for 1366px", () => {
    testBreakpoint(1366, "sm");
  });

  it("returns md breakpoint for 1600px", () => {
    testBreakpoint(1600, "md");
  });

  it("returns lg breakpoint for 1920px", () => {
    testBreakpoint(1920, "lg");
  });

  it("returns xl breakpoint for 2560px", () => {
    testBreakpoint(2560, "xl");
  });
});
```

4. **테스트 실행** (30min)

```bash
npm run test
```

**완료 조건**:

- ✅ Grid 레이아웃 테스트 통과
- ✅ 5개 해상도 테스트 통과

---

#### Task 4.4: 성능 최적화 (2시간)

**세부 작업**:

1. **이미지 로딩 최적화** (40min)

```typescript
// src/hooks/useImageOptimization.ts
import { useState, useEffect } from "react";

export const useImageOptimization = (imageUrl: string) => {
  const [optimizedUrl, setOptimizedUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!imageUrl) return;

    const img = new Image();
    img.onload = () => {
      setOptimizedUrl(imageUrl);
      setIsLoading(false);
    };
    img.onerror = () => {
      console.error("Image load error");
      setIsLoading(false);
    };
    img.src = imageUrl;
  }, [imageUrl]);

  return {
    optimizedUrl,
    isLoading,
  };
};
```

2. **React.memo 최적화** (40min)

```typescript
// 주요 컴포넌트에 React.memo 적용
// src/components/sidebar/DocumentTypeSelector.tsx
const DocumentTypeSelector = React.memo<DocumentTypeSelectorProps>(
  ({ onChange }) => {
    // ...
  }
);

// src/components/viewer/BoundingBoxOverlay.tsx
const BoundingBoxOverlay = React.memo<BoundingBoxOverlayProps>(
  ({ bboxes, imageSize, displaySize, onBoxClick, onBoxHover }) => {
    // ...
  },
  (prevProps, nextProps) => {
    return (
      prevProps.bboxes === nextProps.bboxes &&
      prevProps.imageSize === nextProps.imageSize &&
      prevProps.displaySize.width === nextProps.displaySize.width &&
      prevProps.displaySize.height === nextProps.displaySize.height
    );
  }
);
```

3. **Vite 빌드 최적화** (40min)

```typescript
// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ["react", "react-dom"],
          editor: ["@tinymce/tinymce-react", "tinymce"],
          canvas: ["fabric"],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
});
```

**완료 조건**:

- ✅ 이미지 로딩 최적화
- ✅ React.memo 적용
- ✅ 빌드 크기 최적화

---

#### Task 4.5: 최종 통합 및 검증 (4시간)

**세부 작업**:

1. **전체 기능 통합 테스트** (2시간)

```markdown
[ ] 파일 업로드 → 분석 → 결과 표시
[ ] 바운딩 박스 표시 및 인터랙션
[ ] 텍스트 편집 및 자동 저장
[ ] 페이지 네비게이션
[ ] 통합 다운로드
[ ] 반응형 동작 (5개 해상도)
```

2. **문서화 업데이트** (1시간)

```markdown
<!-- README.md -->

# SmartEyeSsen Frontend

## 기술 스택

- React 18 + TypeScript + Vite
- CSS Grid + CSS Modules
- Fabric.js / SVG
- TinyMCE React
- Context API

## 개발 환경 설정

\`\`\`bash
npm install
npm run dev
\`\`\`

## 빌드

\`\`\`bash
npm run build
\`\`\`

## 테스트

\`\`\`bash
npm run test
\`\`\`
```

3. **Git 최종 커밋 및 PR** (1시간)

```bash
# 모든 변경사항 커밋
git add .
git commit -m "feat: SmartEyeSsen Frontend 완성 (React 18)

- CSS Grid 반응형 레이아웃
- Sidebar 전체 기능
- 바운딩 박스 오버레이 (SVG)
- TinyMCE 에디터 통합
- 2개 탭 시스템
- React Context 상태 관리
- API 서비스 레이어
- 반응형 E2E 테스트
- 성능 최적화

총 개발 기간: 60시간 (8일)"

# 브랜치 푸시
git push origin feature/react-Frontend-implementation

# PR 생성
# 제목: feat: React 18 기반 SmartEyeSsen 프론트엔드 구현
# 내용: 개발 로드맵에 따른 전체 기능 구현 완료
```

**완료 조건**:

- ✅ 전체 기능 통합 테스트 통과
- ✅ README 업데이트
- ✅ Git PR 생성

---

## 5. 일일 작업 스케줄

```
Day 0 (1h): 환경 설정 및 프로젝트 초기화
Day 1 (8h): Grid 레이아웃 + minmax 설정
Day 2 (8h): 미디어 쿼리 + PageSlider 분리
Day 3 (4h): RestoreButton + 애니메이션
Day 4 (8h): Sidebar 기능 (문서 타입, 모델, 버튼)
Day 5 (3h): 통합 다운로드 완성
Day 6 (8h): 바운딩 박스 오버레이 (SVG)
Day 7 (7h): EditorPanel + ActionButtons + AIStatsTab
Day 8 (8h): Context + API + 테스트 + 최적화

총: 55시간 + 5시간 버퍼 = 60시간
```

---

## 6. 품질 보증 계획

### 6.1 코드 품질

```bash
# ESLint 설정
npm install -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-react eslint-plugin-react-hooks

# .eslintrc.cjs
module.exports = {
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
    'plugin:@typescript-eslint/recommended'
  ],
  rules: {
    'react/react-in-jsx-scope': 'off'
  }
}

# 실행
npm run lint
```

### 6.2 테스트 커버리지

```bash
# 커버리지 리포트
npm run test -- --coverage

# 목표: > 80%
```

### 6.3 Lighthouse 점수

```markdown
목표 점수:

- Performance: > 90
- Accessibility: > 90
- Best Practices: > 90
- SEO: > 80
```

---

## 7. 리스크 관리

| 리스크                   | 발생 확률 | 영향도 | 대응 방안                          |
| ------------------------ | --------- | ------ | ---------------------------------- |
| **Canvas/SVG 성능 이슈** | 중간      | 높음   | 청킹, RAF 최적화, React.memo       |
| **TinyMCE 라이센스**     | 낮음      | 중간   | 무료 플랜 확인, Quill.js 대안      |
| **API 연동 지연**        | 중간      | 중간   | Mock 데이터로 프론트엔드 먼저 완성 |
| **반응형 버그**          | 낮음      | 중간   | 5개 해상도 철저히 테스트           |
| **일정 지연**            | 중간      | 중간   | 버퍼 5시간 확보                    |

---

## 8. 완료 기준

### 8.1 기능 요구사항

- ✅ CSS Grid 5열 반응형 레이아웃
- ✅ 5개 해상도 지원 (1280~2560px+)
- ✅ Sidebar 전체 기능 (문서 타입, 모델, 분석, 다운로드)
- ✅ PageSlider 토글 + RestoreButton
- ✅ 바운딩 박스 오버레이 (SVG)
- ✅ TinyMCE 에디터 + 자동 저장
- ✅ 2개 탭 시스템 (텍스트/통계)
- ✅ React Context 상태 관리
- ✅ API 연동

### 8.2 성능 요구사항

- ✅ Grid 렌더링 < 16ms (60fps)
- ✅ 애니메이션 부드러움
- ✅ Lighthouse 점수 > 90

### 8.3 코드 품질

- ✅ ESLint 오류 0개
- ✅ TypeScript strict mode
- ✅ 테스트 커버리지 > 80%

---

## 📌 Quick Start

```bash
# 1. 의존성 설치
cd Frontend
npm install

# 2. 개발 서버 실행
npm run dev

# 3. 브라우저에서 확인
# http://localhost:5173

# 4. 빌드
npm run build

# 5. 테스트
npm run test
```

---

## 📞 문의 및 지원

- 기술 문의: 프로젝트 이슈 트래커
- 버그 리포트: GitHub Issues
- 기능 제안: Pull Request

---

**개발 로드맵 최종 수정일**: 2025년 11월 4일  
**예상 완료일**: 시작일 + 8일 (작업일 기준)

---

**🎉 이 로드맵을 따라 React 18 기반 SmartEyeSsen 프론트엔드를 성공적으로 구현하세요!**
