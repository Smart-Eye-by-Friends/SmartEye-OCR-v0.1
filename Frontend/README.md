# SmartEyeSsen Frontend

> React 19.1.1 기반 AI 학습지 분석 시스템 프론트엔드

## 🎯 프로젝트 개요

SmartEyeSsen은 AI 기반 학습지 OCR 및 구조 분석 시스템입니다. 이 프론트엔드는 React 19와 TypeScript를 기반으로 구축되었으며, CSS Grid를 활용한 반응형 레이아웃을 제공합니다.

## 🚀 기술 스택

### Core
- **React 19.1.1** - 최신 React 버전
- **TypeScript 5.9.3** - strict mode
- **Vite 7.1.12** - 빠른 개발 서버 및 빌드

### State Management
- **React Context API** - 전역 상태 관리
- **useReducer** - 복잡한 상태 로직

### Styling
- **CSS Grid** - 5열 반응형 레이아웃
- **CSS Modules** - 스타일 격리
- **CSS Variables** - 테마 및 디자인 토큰

### API & Network
- **axios 1.x** - HTTP 클라이언트
- **API 인터셉터** - 요청/응답 처리

### Testing
- **vitest 4.x** - 단위 테스트
- **@testing-library/react** - 컴포넌트 테스트
- **happy-dom** - 테스트 환경

### Graphics
- **SVG** - 바운딩 박스 오버레이
- **CoordinateScaler** - 좌표 변환 유틸리티

## 📁 프로젝트 구조

```
Frontend/
├── src/
│   ├── components/          # React 컴포넌트
│   │   ├── layout/          # MainLayout
│   │   ├── sidebar/         # 문서 타입, 모델 선택, 버튼
│   │   ├── slider/          # 페이지 슬라이더
│   │   ├── viewer/          # 이미지 뷰어, 바운딩 박스
│   │   └── editor/          # 텍스트 에디터, AI 통계
│   ├── contexts/            # React Context (Project, Pages, Layout)
│   ├── hooks/               # Custom Hooks
│   ├── services/            # API 서비스 레이어
│   ├── styles/              # 전역 CSS (Grid, 반응형)
│   ├── utils/               # 유틸리티 (CoordinateScaler)
│   ├── __tests__/           # 테스트 파일
│   ├── App.tsx              # 루트 컴포넌트
│   └── main.tsx             # 진입점
├── public/                  # 정적 파일
├── vitest.config.ts         # 테스트 설정
├── vite.config.ts           # Vite 설정
├── tsconfig.json            # TypeScript 설정
└── package.json             # 의존성 관리
```

## 🛠️ 개발 환경 설정

### 필수 요구사항
- Node.js 20.15.1 이상
- npm 10.7.0 이상

### 설치

```bash
# 의존성 설치
npm install
```

### 개발 서버 실행

```bash
# 개발 서버 시작 (http://localhost:5173)
npm run dev
```

### 빌드

```bash
# 프로덕션 빌드
npm run build

# 빌드 결과 미리보기
npm run preview
```

### 테스트

```bash
# 테스트 실행
npm run test

# watch 모드로 테스트
npm run test -- --watch

# 커버리지 리포트
npm run test -- --coverage
```

### 린트

```bash
# ESLint 실행
npm run lint
```

## 🎨 주요 기능

### 1. CSS Grid 반응형 레이아웃
- 5열 시스템 (sidebar, slider, layout, editor)
- 5개 breakpoint 지원 (1280px ~ 2560px+)
- minmax 기반 유연한 열 크기 조정

### 2. 문서 분석 워크플로우
- 문서 타입 선택 (문제지/일반문서)
- AI 모델 선택 (SmartEye/DocLayout)
- 파일 업로드 및 분석
- 결과 시각화

### 3. 바운딩 박스 오버레이
- SVG 기반 렌더링
- 클래스별 색상 구분
- 호버 툴팁 (클래스, 신뢰도)
- 클릭 시 에디터 스크롤

### 4. 텍스트 편집
- 2개 탭 시스템 (텍스트 편집 / AI 통계)
- 자동 저장 기능
- 포맷팅 지원

### 5. AI 통계 대시보드
- 통계 카드 (총 요소, 문제 개수, 처리 시간)
- 클래스별 분포 막대 그래프
- 상세 정보 테이블

### 6. 페이지 네비게이션
- 썸네일 기반 슬라이더
- 토글/복원 애니메이션
- 현재 페이지 하이라이트

### 7. 통합 다운로드
- 진행률 모달
- 다양한 포맷 지원 (JSON, PDF 등)

## 🧪 테스트

### Grid Layout 테스트
- 4개 패널 렌더링 확인
- 슬라이더 토글 동작
- 복원 버튼 동작

### 반응형 테스트
- 5개 breakpoint 검증
- 화면 크기 계산 확인

## ⚡ 성능 최적화

### React.memo 적용
- `DocumentTypeSelector` - onChange 콜백 최적화
- `BoundingBoxOverlay` - Custom comparison으로 리렌더링 방지

### 이미지 로딩 최적화
- `useImageOptimization` hook - 사전 로딩 및 에러 처리

### Vite 빌드 최적화
- Manual chunks (vendor, utils 분리)
- Chunk size warning limit: 1000KB

## 🔧 환경 변수

```env
# API Base URL (기본값: http://localhost:8000/api)
VITE_API_BASE_URL=http://localhost:8000/api
```

## 📦 주요 의존성

```json
{
  "dependencies": {
    "react": "^19.1.1",
    "react-dom": "^19.1.1",
    "axios": "^1.13.1"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^5.0.4",
    "typescript": "~5.9.3",
    "vite": "^7.1.7",
    "vitest": "^4.0.6",
    "@testing-library/react": "^16.3.0",
    "@testing-library/jest-dom": "^6.9.1",
    "happy-dom": "^20.0.10"
  }
}
```

## 🎯 개발 로드맵

### Phase 1 (완료) - CSS Grid 반응형 레이아웃
- ✅ 5열 Grid 시스템
- ✅ 5개 breakpoint 설정
- ✅ PageSlider 분리
- ✅ RestoreButton 구현

### Phase 2 (완료) - Sidebar 기능 확장
- ✅ DocumentTypeSelector
- ✅ ModelSelector
- ✅ AnalyzeButton
- ✅ IntegratedDownloadButton

### Phase 3 (완료) - 바운딩 박스 & 에디터
- ✅ BoundingBoxOverlay (SVG)
- ✅ 바운딩 박스 인터랙션
- ✅ EditorPanel
- ✅ TextEditorTab
- ✅ AIStatsTab

### Phase 4 (완료) - Context & API & 테스트
- ✅ React Context 구현
- ✅ API 서비스 레이어
- ✅ 반응형 E2E 테스트
- ✅ 성능 최적화

## 🤝 기여 가이드

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

## 📞 문의 및 지원

- **기술 문의**: GitHub Issues
- **버그 리포트**: GitHub Issues
- **기능 제안**: Pull Request

---

**개발 기간**: 8일 (60시간)  
**최종 수정일**: 2025년 11월 4일
