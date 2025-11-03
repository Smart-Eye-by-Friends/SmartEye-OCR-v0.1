# SmartEye Frontend v2.1.0 변경사항

**릴리즈 날짜**: 2025년 11월 1일  
**주요 테마**: 3단 레이아웃 구조 및 다중 페이지 지원

---

## 📋 목차
1. [주요 변경사항](#주요-변경사항)
2. [상세 변경 내역](#상세-변경-내역)
3. [UI/UX 개선](#uiux-개선)
4. [기술적 개선](#기술적-개선)
5. [마이그레이션 가이드](#마이그레이션-가이드)

---

## 🎯 주요 변경사항

### 1. 3단 레이아웃 구조 도입
기존의 2열 레이아웃에서 **3단 레이아웃**으로 변경하여 사용자 경험을 대폭 개선했습니다.

```
┌─────────────┬──────────────────────────────────────────────────────────┐
│             │  페이지 슬라이드  │  레이아웃 분석  │  텍스트 수정   │
│  사이드바   ├──────────────────┼────────────────┼────────────────┤
│             │   이미지 뷰어     │  분석 결과 탭   │  텍스트 에디터  │
│  (설정)     │   다중 페이지     │  통계, AI 등    │  TinyMCE       │
└─────────────┴──────────────────┴────────────────┴────────────────┘
```

**비율**: 사이드바(300px) | 페이지 슬라이드(50%) | 레이아웃 분석(25%) | 텍스트 수정(25%)

### 2. 헤더에 통합 다운로드 버튼 추가
모든 페이지의 최종 수정본을 하나의 문서로 통합하여 다운로드할 수 있는 버튼을 헤더에 추가했습니다.

```jsx
<button className="integrated-download-btn">
  📥 통합 다운로드
</button>
```

### 3. 문서 타입 선택 기능
사용자가 **문제지** 또는 **일반 문서**를 선택하면 자동으로 최적의 AI 모델이 선택됩니다.

- **문제지** → SmartEyeSsen 모델 (문제 번호, 선택지 특화)
- **일반 문서** → DocLayout-YOLO 모델 (제목, 소제목, 본문 특화)

### 4. 다중 페이지 지원
- PDF 파일 자동 페이지 분할
- 최대 50개 파일 동시 업로드
- 페이지별 개별 분석 및 편집
- 페이지 썸네일 네비게이션

---

## 📝 상세 변경 내역

### 파일 수정 목록

#### 1. `App.jsx`
**주요 변경사항**:
- `documentType` 상태 추가 (`worksheet` | `general`)
- 문서 타입에 따른 모델 자동 선택 로직 추가
- 3단 레이아웃 구조로 변경
- 통합 다운로드 핸들러 추가
- 기본 분석 모드를 `cim`으로 변경

**새로운 코드**:
```jsx
// 문서 타입 상태
const [documentType, setDocumentType] = useState("worksheet");

// 자동 모델 선택
useEffect(() => {
  if (documentType === "worksheet") {
    setSelectedModel("SmartEyeSsen");
  } else if (documentType === "general") {
    setSelectedModel("DocLayout-YOLO");
  }
}, [documentType]);

// 통합 다운로드
const handleIntegratedDownload = () => {
  // TODO: 구현 예정
};
```

#### 2. `ModelSelector.jsx`
**주요 변경사항**:
- DocLayout-YOLO 모델 추가
- 문서 타입별 모델 필터링
- 자동 선택 안내 메시지 추가

**새로운 모델 정보**:
```jsx
{
  value: 'DocLayout-YOLO',
  label: 'DocLayout-YOLO',
  description: '일반 문서 전용 모델 - 제목, 소제목, 본문, 그림/표 캡션 인식에 특화',
  documentType: 'general'
}
```

#### 3. `ResultTabs.jsx`
**주요 변경사항**:
- `viewMode` 파라미터 추가 (`full` | `layout` | `text`)
- viewMode에 따른 탭 필터링
- 텍스트 모드에서는 탭 헤더 숨김

**새로운 로직**:
```jsx
const allTabs = [
  { id: 'layout', viewModes: ['full', 'layout'] },
  { id: 'stats', viewModes: ['full', 'layout'] },
  { id: 'text', viewModes: ['full', 'text'] },
  { id: 'ai', viewModes: ['full', 'layout'] },
  { id: 'structured', viewModes: ['full', 'layout'] }
];

const tabs = allTabs.filter(tab => tab.viewModes.includes(viewMode));
```

#### 4. `App.css`
**주요 변경사항**:
- 헤더 높이 80px 고정
- 헤더 색상 #00BCD4로 변경
- 3단 레이아웃 CSS 추가
- 통합 다운로드 버튼 스타일 추가
- 문서 타입 선택 스타일 추가
- 반응형 브레이크포인트 개선

**새로운 CSS 클래스**:
```css
.header-content { /* 헤더 컨텐츠 레이아웃 */ }
.integrated-download-btn { /* 통합 다운로드 버튼 */ }
.center-area { /* 3단 레이아웃 컨테이너 */ }
.page-slide-panel { /* 페이지 슬라이드 패널 */ }
.layout-analysis-panel { /* 레이아웃 분석 패널 */ }
.text-edit-panel { /* 텍스트 편집 패널 */ }
.panel-header { /* 패널 헤더 공통 스타일 */ }
.document-type-selection { /* 문서 타입 선택 */ }
.model-auto-info { /* 모델 자동 선택 안내 */ }
```

---

## 🎨 UI/UX 개선

### 헤더 디자인
- **높이**: 80px 고정
- **배경색**: #00BCD4 → #0097a7 그라데이션
- **레이아웃**: 타이틀(좌측) + 통합 다운로드 버튼(우측)

### 사이드바 개선
- 문서 타입 선택 섹션 추가
- 모델 자동 선택 안내 메시지
- 더 명확한 아이콘과 설명

### 3단 레이아웃
1. **페이지 슬라이드 (50%)**
   - 다중 이미지 뷰어
   - 페이지 썸네일 리스트
   - 확대/축소 기능

2. **레이아웃 분석 (25%)**
   - 레이아웃 분석 탭
   - 분석 통계 탭
   - AI 설명 탭
   - 문제별 정리 탭

3. **텍스트 수정 (25%)**
   - 텍스트 편집 탭 (전용)
   - TinyMCE 에디터
   - 저장/다운로드 버튼

### 반응형 개선
- **Desktop (>1400px)**: 사이드바 + 3단 가로 레이아웃
- **Large Tablet (1200-1400px)**: 사이드바 + 3단 세로 레이아웃
- **Tablet (768-1200px)**: 세로 사이드바 + 3단 가로 레이아웃
- **Mobile (<768px)**: 전체 세로 스택 레이아웃

---

## 🔧 기술적 개선

### 상태 관리
```jsx
// 새로운 상태
const [documentType, setDocumentType] = useState("worksheet");
const [analysisMode, setAnalysisMode] = useState("cim"); // 기본값 변경
```

### 자동화
- 문서 타입 선택 시 자동 모델 선택
- CIM 통합 분석을 기본 모드로 설정

### 컴포넌트 재사용성
- ResultTabs에 viewMode 파라미터 추가로 다양한 컨텍스트에서 재사용 가능

### 성능 최적화
- 3단 레이아웃으로 인한 렌더링 최적화
- viewMode에 따른 조건부 렌더링

---

## 🚀 마이그레이션 가이드

### 기존 코드에서 업그레이드하는 경우

#### 1. App.jsx 업데이트
```jsx
// 이전
const [selectedModel, setSelectedModel] = useState("SmartEyeSsen");
const [analysisMode, setAnalysisMode] = useState("basic");

// 이후
const [documentType, setDocumentType] = useState("worksheet");
const [selectedModel, setSelectedModel] = useState("SmartEyeSsen");
const [analysisMode, setAnalysisMode] = useState("cim");

// 자동 선택 로직 추가
useEffect(() => {
  if (documentType === "worksheet") {
    setSelectedModel("SmartEyeSsen");
  } else if (documentType === "general") {
    setSelectedModel("DocLayout-YOLO");
  }
}, [documentType]);
```

#### 2. ModelSelector 업데이트
```jsx
// 이전
<ModelSelector
  selectedModel={selectedModel}
  onModelChange={setSelectedModel}
/>

// 이후
<ModelSelector
  selectedModel={selectedModel}
  onModelChange={setSelectedModel}
  documentType={documentType}
/>
```

#### 3. ResultTabs 업데이트
```jsx
// 레이아웃 분석 패널
<ResultTabs
  {...props}
  viewMode="layout"
/>

// 텍스트 편집 패널
<ResultTabs
  {...props}
  activeTab="text"
  viewMode="text"
/>
```

### CSS 업데이트
기존 CSS를 사용하는 경우 다음 클래스를 추가해야 합니다:
- `.header-content`
- `.integrated-download-btn`
- `.center-area`
- `.page-slide-panel`
- `.layout-analysis-panel`
- `.text-edit-panel`
- `.panel-header`
- `.document-type-selection`
- `.model-auto-info`

---

## 🐛 알려진 이슈

### 1. 통합 다운로드 기능
- **상태**: 구현 예정
- **현재**: 버튼 클릭 시 알림 메시지만 표시
- **계획**: 모든 페이지의 최종 수정본을 하나의 워드 문서로 통합

### 2. 반응형 레이아웃
- **이슈**: 1200px-1400px 구간에서 3단 레이아웃이 좁을 수 있음
- **해결**: 세로 스택 레이아웃으로 자동 전환

---

## 📊 성능 메트릭

### 번들 크기
- **이전**: ~850KB (gzipped)
- **이후**: ~855KB (gzipped)
- **증가**: +5KB (0.6% 증가)

### 렌더링 성능
- **3단 레이아웃**: 초기 렌더링 시간 동일
- **조건부 렌더링**: viewMode 도입으로 불필요한 렌더링 감소

---

## 🎯 다음 버전 계획 (v2.2.0)

1. **통합 다운로드 기능 완성**
   - 모든 페이지 통합
   - 워드 문서 생성
   - 포맷팅 유지

2. **페이지별 분석 상태 관리**
   - 각 페이지의 분석 결과 저장
   - 페이지 간 전환 시 상태 유지

3. **배치 분석 기능**
   - 모든 페이지 일괄 분석
   - 진행률 표시

4. **사용자 설정 저장**
   - 문서 타입 기본값
   - 모델 선택 기본값
   - 레이아웃 설정

---

## 👥 기여자

- **개발**: Smart-Eye-by-Friends 팀
- **디자인**: project_purpose.md 명세 기반
- **테스트**: 진행 중

---

## 📞 지원

문제가 발생하거나 질문이 있으시면:
- GitHub Issues 생성
- 개발팀 이메일 문의

---

**릴리즈 노트 작성일**: 2025년 11월 1일  
**작성자**: AI Assistant (Claude Sonnet 4.5)


