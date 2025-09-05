# SmartEye OCR - React Frontend

SmartEye OCR 프로그램의 React 기반 프론트엔드입니다. AI를 활용한 학습지 OCR 및 구조 분석 시스템의 사용자 인터페이스를 제공합니다.

## 🚀 주요 기능

### 📤 이미지 업로드
- 드래그 앤 드롭 지원
- JPG, PNG, GIF 파일 형식 지원
- 최대 10MB 파일 크기 제한
- 실시간 이미지 미리보기

### 🧠 AI 모델 선택
- **SmartEyeSsen**: 한국어 학습지에 최적화 (권장)
- **DocStructBench**: 일반적인 문서 구조 분석
- **DocLayNet-DocSynth**: 복잡한 레이아웃 분석
- **DocSynth300K**: 대용량 학습 데이터 기반

### 📋 분석 모드
- **일반 분석**: 기본적인 OCR 및 레이아웃 분석
- **구조화된 분석**: 문제별로 정렬된 상세 분석 (권장)

### 🤖 OpenAI 연동
- API 키를 통한 AI 기반 이미지 분석
- 자동 이미지 설명 생성
- 표와 그래프 분석

### 📊 분석 결과 탭
1. **레이아웃 분석**: 감지된 요소들의 위치와 구조
2. **분석 통계**: 분석 결과 요약 정보
3. **텍스트 편집**: OCR 결과 텍스트 편집 (TinyMCE)
4. **AI 설명**: AI 기반 이미지 분석 결과
5. **문제별 정리**: 구조화된 문제별 분석

### 💾 저장 기능
- 텍스트 파일 다운로드
- 워드 문서 저장
- 클립보드 복사
- 로컬 스토리지 자동 저장

## 🛠️ 기술 스택

- **React 18** - 모던 리액트 훅 사용
- **Axios** - HTTP 클라이언트
- **TinyMCE** - 리치 텍스트 에디터
- **React Icons** - 아이콘 컴포넌트
- **CSS Variables** - 모던 CSS 스타일링

## 📁 프로젝트 구조

```
src/
├── components/          # 리액트 컴포넌트
│   ├── ImageLoader.jsx      # 이미지 업로드
│   ├── ModelSelector.jsx    # AI 모델 선택
│   ├── AnalysisModeSelector.jsx  # 분석 모드 선택
│   ├── AnalysisProgress.jsx     # 진행률 표시
│   ├── ResultTabs.jsx       # 결과 탭 컨테이너
│   ├── LayoutTab.jsx        # 레이아웃 분석 탭
│   ├── StatsTab.jsx         # 통계 탭
│   ├── TextEditorTab.jsx    # 텍스트 편집 탭
│   ├── AITab.jsx            # AI 분석 탭
│   └── StructuredTab.jsx    # 구조화된 분석 탭
├── hooks/               # 커스텀 훅
│   ├── useAnalysis.js       # 분석 로직
│   └── useTextEditor.js     # 텍스트 편집 로직
├── services/            # API 서비스
│   └── apiService.js        # 백엔드 API 통신
├── styles/              # 스타일시트
│   ├── index.css            # 전역 스타일
│   └── App.css              # 앱 스타일
├── App.jsx              # 메인 앱 컴포넌트
└── index.js             # 엔트리 포인트
```

## 🔧 설치 및 실행

### 1. 프로젝트 설치

```bash
# 의존성 설치
npm install
```

### 2. 환경 변수 설정

프로젝트 루트에 `.env` 파일을 생성하고 다음을 추가:

```env
REACT_APP_API_URL=http://localhost:8080
```

### 3. 개발 서버 실행

```bash
npm start
```

브라우저에서 http://localhost:3000 접속

### 4. 프로덕션 빌드

```bash
npm run build
```

## 🔌 백엔드 연동

### API 엔드포인트

React 앱은 다음 Spring Boot API 엔드포인트와 통신합니다:

- `POST /analyze` - 일반 분석
- `POST /analyze-structured` - 구조화된 분석  
- `POST /save-as-word` - 워드 문서 저장
- `GET /health` - 헬스 체크

### 요청 형식

```javascript
// 분석 요청
const formData = new FormData();
formData.append('image', imageFile);
formData.append('modelChoice', 'SmartEyeSsen');
formData.append('apiKey', 'sk-...');  // 선택사항
```

### 응답 형식

```javascript
{
  "success": true,
  "layout_image_url": "/static/layout_viz_xxx.png",
  "json_url": "/static/analysis_result_xxx.json",
  "stats": { /* 분석 통계 */ },
  "ocr_results": [ /* OCR 결과 */ ],
  "ai_results": [ /* AI 분석 결과 */ ],
  "formatted_text": "...",
  "structured_result": { /* 구조화된 결과 (해당 모드일 때만) */ }
}
```

## 🎨 UI/UX 특징

### 반응형 디자인
- 데스크톱, 태블릿, 모바일 지원
- CSS Grid 및 Flexbox 활용
- 모던 CSS Variables 사용

### 사용자 경험
- 직관적인 드래그 앤 드롭 인터페이스
- 실시간 진행률 표시
- 탭 기반 결과 표시
- 자동 저장 기능

### 접근성
- 키보드 네비게이션 지원
- 시맨틱 HTML 구조
- 명확한 레이블링
- 고대비 색상 사용

## 🔍 주요 컴포넌트 설명

### `useAnalysis` 훅
- 분석 상태 관리
- API 호출 및 오류 처리
- 진행률 시뮬레이션

### `useTextEditor` 훅
- 텍스트 편집 상태 관리
- 로컬 스토리지 자동 저장
- 다양한 내보내기 기능

### `apiService`
- Axios 기반 HTTP 클라이언트
- 요청/응답 인터셉터
- 파일 업로드 유효성 검사

## 🚨 오류 처리

### 클라이언트 측 검증
- 파일 크기 제한 (10MB)
- 파일 형식 검증 (JPG, PNG, GIF)
- 네트워크 연결 상태 확인

### 서버 오류 대응
- HTTP 상태 코드별 메시지
- 타임아웃 처리 (5분)
- 재시도 메커니즘

## 🔄 Vue.js에서 React로의 주요 변환점

### 상태 관리
- `reactive()` → `useState()`, `useCallback()`
- `computed()` → `useMemo()`
- `watch()` → `useEffect()`

### 템플릿 문법
- `v-if` → `{condition && <Component />}`
- `v-for` → `{array.map()}`
- `@click` → `onClick`
- `v-model` → `value` + `onChange`

### 컴포넌트 구조
- Single File Components → JSX 파일
- `<template>` → JSX 반환
- `<script setup>` → 함수형 컴포넌트
- `<style scoped>` → CSS 모듈 or styled-components

## 📱 반응형 브레이크포인트

- **Desktop**: > 1200px (2열 레이아웃)
- **Tablet**: 768px - 1200px (1열 레이아웃)
- **Mobile**: < 768px (축약된 탭, 스택 레이아웃)
- **Small Mobile**: < 480px (최소 패딩, 간소화된 UI)

## 🎯 성능 최적화

### React 최적화
- `useCallback`으로 함수 메모이제이션
- `useMemo`로 계산 결과 캐싱
- 조건부 렌더링으로 불필요한 컴포넌트 방지

### 네트워크 최적화
- 이미지 압축 옵션
- API 호출 디바운싱
- 파일 업로드 진행률 표시

### 사용자 경험 개선
- 스켈레톤 로딩
- 에러 바운더리
- 오프라인 감지

## 🔧 커스터마이징

### 색상 테마 변경
`src/styles/App.css`의 CSS 변수를 수정:

```css
:root {
  --primary-color: #00bcd4;      /* 메인 색상 */
  --primary-color-dark: #0097a7;  /* 어두운 메인 색상 */
  --success-color: #4caf50;      /* 성공 색상 */
  --error-color: #f44336;        /* 오류 색상 */
}
```

### 새 탭 추가
1. `components/` 폴더에 새 탭 컴포넌트 생성
2. `ResultTabs.jsx`에 탭 정보 추가
3. `renderTabContent()` 함수에 케이스 추가

## 🧪 테스트

```bash
# 테스트 실행
npm test

# 테스트 커버리지
npm run test:coverage
```

## 📦 배포

### 빌드
```bash
npm run build
```

### 서버 설정
빌드된 파일을 웹 서버에 배포하고, React Router를 위해 모든 경로를 `index.html`로 리다이렉트 설정

## 🤝 기여 방법

1. 포크 생성
2. 피처 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 커밋 (`git commit -m 'Add amazing feature'`)
4. 푸시 (`git push origin feature/amazing-feature`)
5. Pull Request 생성

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 있습니다.

## 🆘 지원

문제가 발생하면 이슈를 생성해주세요:
- 버그 리포트
- 기능 요청
- 사용 방법 질문

---

**개발팀**: Smart-Eye-by-Friends
**버전**: 1.0.0
**최종 업데이트**: 2024년 9월 4일
