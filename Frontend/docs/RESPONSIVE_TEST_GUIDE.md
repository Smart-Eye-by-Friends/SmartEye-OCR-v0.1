# 반응형 테스트 가이드

## Task 1.2: 반응형 minmax 설정 테스트

### 테스트 목적
- CSS Grid의 minmax() 함수가 올바르게 작동하는지 확인
- 5단계 breakpoint 감지가 정확한지 검증
- 화면 크기에 따라 Sidebar/Slider 폭이 적절히 변경되는지 확인

---

## 테스트 해상도 체크리스트

### 1️⃣ 1280x720 (xs - 최소 화면)
```
예상 결과:
- Breakpoint: xs
- Sidebar: ~140px (10vw, 최소값 제한)
- Slider: 자동 숨김 (0px)
- Restore Button: 표시됨 (40px)
- Layout + Editor: 동일한 폭으로 나뉨
```

**확인 사항:**
- [ ] Console에 "Screen: 1280x720px, Breakpoint: xs" 출력
- [ ] Slider가 자동으로 숨겨짐
- [ ] Restore 버튼이 표시됨
- [ ] Sidebar 폭이 약 140px

**Chrome DevTools 설정:**
1. F12 → DevTools 열기
2. Ctrl+Shift+M → Responsive Design Mode
3. Dimensions → Edit → Add custom device
4. Width: 1280, Height: 720
5. Console 탭에서 로그 확인

---

### 2️⃣ 1366x768 (sm - 노트북 최소)
```
예상 결과:
- Breakpoint: sm
- Sidebar: ~150px (11vw, 최소값 적용)
- Slider: ~200px (13vw, 최소값 적용)
- Layout + Editor: 동일한 폭으로 나뉨
- 폰트: 13px (컴팩트 모드)
```

**확인 사항:**
- [ ] Console에 "Screen: 1366x768px, Breakpoint: sm" 출력
- [ ] Sidebar 폭이 약 150px
- [ ] Slider 폭이 약 200px
- [ ] 닫기 버튼이 작동함
- [ ] 폰트 크기가 작아짐

**계산 검증:**
- Sidebar: min(1366 * 0.11, 320) = min(150.26, 320) = 150px ✓
- Slider: min(1366 * 0.13, 400) = min(177.58, 400) ≈ 200px (최소값) ✓

---

### 3️⃣ 1600x900 (md - 노트북 일반)
```
예상 결과:
- Breakpoint: md
- Sidebar: ~184px (11.5vw)
- Slider: ~224px (14vw)
- Layout + Editor: 동일한 폭으로 나뉨
```

**확인 사항:**
- [ ] Console에 "Screen: 1600x900px, Breakpoint: md" 출력
- [ ] Sidebar 폭이 약 176-184px
- [ ] Slider 폭이 약 208-224px
- [ ] 모든 영역이 잘 보임

**계산 검증:**
- Sidebar: min(1600 * 0.115, 280) = min(184, 280) = 184px ✓
- Slider: min(1600 * 0.14, 350) = min(224, 350) = 224px ✓

---

### 4️⃣ 1920x1080 (lg - FHD 모니터)
```
예상 결과:
- Breakpoint: lg
- Sidebar: ~230px (12vw)
- Slider: ~288px (15vw)
- Layout + Editor: 동일한 폭으로 나뉨
```

**확인 사항:**
- [ ] Console에 "Screen: 1920x1080px, Breakpoint: lg" 출력
- [ ] Sidebar 폭이 약 230px
- [ ] Slider 폭이 약 288px
- [ ] 레이아웃이 균형있게 배치됨

**계산 검증:**
- Sidebar: min(1920 * 0.12, 280) = min(230.4, 280) = 230px ✓
- Slider: min(1920 * 0.15, 350) = min(288, 350) = 288px ✓

---

### 5️⃣ 2560x1440 (xl - QHD 모니터)
```
예상 결과:
- Breakpoint: xl
- Sidebar: ~307px (12vw)
- Slider: ~384px (15vw)
- Layout + Editor: 동일한 폭으로 나뉨
- 최대값 제한 적용
```

**확인 사항:**
- [ ] Console에 "Screen: 2560x1440px, Breakpoint: xl" 출력
- [ ] Sidebar 폭이 약 307px
- [ ] Slider 폭이 약 384px
- [ ] 폰트 크기가 적절함 (clamp 적용)

**계산 검증:**
- Sidebar: min(2560 * 0.12, 320) = min(307.2, 320) = 307px ✓
- Slider: min(2560 * 0.15, 400) = min(384, 400) = 384px ✓

---

### 6️⃣ 3440x1440 (xl - 울트라와이드) - 추가 테스트
```
예상 결과:
- Breakpoint: xl
- Sidebar: 320px (최대값 제한)
- Slider: 400px (최대값 제한)
- Layout + Editor: 매우 넓은 작업 공간
```

**확인 사항:**
- [ ] Console에 "Screen: 3440x1440px, Breakpoint: xl" 출력
- [ ] Sidebar가 320px에서 더 커지지 않음
- [ ] Slider가 400px에서 더 커지지 않음
- [ ] 작업 영역 비율이 증가함 (~79%)

**계산 검증:**
- Sidebar: min(3440 * 0.12, 320) = min(412.8, 320) = 320px (최대값) ✓
- Slider: min(3440 * 0.15, 400) = min(516, 400) = 400px (최대값) ✓

---

## 동작 테스트

### Slider 토글 테스트
1. **열린 상태에서 닫기**
   - [ ] 닫기 버튼(⏴) 클릭
   - [ ] Slider가 부드럽게 사라짐 (300ms)
   - [ ] Restore 버튼이 나타남 (fadeIn)
   - [ ] Layout/Editor 영역이 자동 확장
   - [ ] Grid 전환이 부드러움

2. **닫힌 상태에서 열기**
   - [ ] Restore 버튼 클릭
   - [ ] Slider가 부드럽게 나타남
   - [ ] Restore 버튼이 사라짐
   - [ ] Layout/Editor 영역이 자동 축소

### 브라우저 창 크기 조절 (실시간)
1. **점진적 축소 테스트**
   - [ ] 1920px → 1366px 창 축소
   - [ ] Console 로그가 실시간 업데이트
   - [ ] Sidebar/Slider 폭이 부드럽게 변경
   - [ ] Breakpoint가 정확히 변경 (lg → sm)

2. **1366px 임계점 테스트**
   - [ ] 1366px → 1280px 창 축소
   - [ ] Slider가 자동으로 숨겨짐
   - [ ] Restore 버튼이 자동 표시
   - [ ] sm → xs breakpoint 전환

---

## 예상 결과 요약표

| 해상도 | Breakpoint | Sidebar | Slider | Restore | Layout | Editor |
|--------|-----------|---------|--------|---------|--------|--------|
| 1280x720 | xs | 140px (10.9%) | 0px (숨김) | 40px | ~550px | ~550px |
| 1366x768 | sm | 150px (11%) | 200px (14.6%) | 숨김 | ~508px | ~508px |
| 1600x900 | md | 184px (11.5%) | 224px (14%) | 숨김 | ~596px | ~596px |
| 1920x1080 | lg | 230px (12%) | 288px (15%) | 숨김 | ~701px | ~701px |
| 2560x1440 | xl | 307px (12%) | 384px (15%) | 숨김 | ~934px | ~934px |
| 3440x1440 | xl | 320px (9.3%) | 400px (11.6%) | 숨김 | ~1360px | ~1360px |

**작업 영역 비율:**
- 1280px (Slider 닫힘): 86%
- 1366px~2560px (Slider 열림): 73-76%
- 3440px (최대값 제한): 79%

---

## 문제 발생 시 체크리스트

### Console에 로그가 안 나타나는 경우
- [ ] 개발 서버가 실행 중인지 확인 (`npm run start`)
- [ ] 브라우저 새로고침 (Ctrl+R)
- [ ] Console 필터가 "All levels"인지 확인

### Grid 폭이 이상한 경우
- [ ] `grid.css`가 import 되었는지 확인
- [ ] `variables.css`가 먼저 import 되었는지 확인
- [ ] 브라우저 DevTools → Elements → Computed → grid-template-columns 확인

### Slider가 자동으로 안 닫히는 경우 (1366px 이하)
- [ ] `grid.css`의 미디어 쿼리 확인
- [ ] `@media (max-width: 1365px)` 규칙 확인
- [ ] 브라우저 캐시 삭제 후 새로고침

### Breakpoint가 틀리게 나오는 경우
- [ ] `useResponsive.ts`의 `getBreakpoint` 함수 로직 확인
- [ ] `window.innerWidth` 값 확인

---

## 완료 조건

### ✅ 기능 검증
- [ ] 5개 해상도에서 Console 로그가 정확히 출력됨
- [ ] Sidebar/Slider 폭이 계산값과 일치함
- [ ] 1366px 이하에서 Slider 자동 숨김
- [ ] Slider 토글이 부드럽게 작동함

### ✅ 성능 검증
- [ ] 창 크기 조절 시 끊김 없음
- [ ] 애니메이션이 60fps 유지
- [ ] Console 로그가 너무 많이 출력되지 않음 (throttle 필요 없음)

### ✅ 코드 품질
- [ ] TypeScript 타입 에러 없음
- [ ] ESLint 경고 없음
- [ ] Console에 에러 없음

---

## 다음 단계 (Task 1.3)

Task 1.2가 완료되면 다음 작업:
1. `responsive.css` 파일 생성 (5단계 미디어 쿼리)
2. 폰트 크기 반응형 조정 (clamp 함수)
3. 작은 화면 컴팩트 모드 구현

**예상 소요 시간**: 4시간
