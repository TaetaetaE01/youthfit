# DESIGN.md — YouthFit 디자인 시스템

> 이 파일은 스크린샷 기반으로 추출한 디자인 토큰이다.
> 이후 모든 UI 작업은 이 파일을 기준으로 한다.
> `[추정]` 표시가 있는 항목은 스크린샷에서 정확히 추출하기 어려워 근사값을 사용한 것이다.

---

## 1. 컬러 팔레트

### Primary (Navy / Indigo)

| 토큰 | Hex | 용도 |
|------|-----|------|
| `primary-900` | `#1B2559` | CTA 버튼 hover, Smart Q&A 카드 배경 |
| `primary-800` | `#1E2A78` | "시작하기" · "검색" 버튼 배경 |
| `primary-700` | `#2B3990` | 버튼 hover 상태 `[추정]` |
| `primary-100` | `#EEF0FF` | 태그 배지 배경 ("AI TRANSLATION" 등) |
| `primary-50`  | `#F5F6FF` | Hero 섹션 배경 그라데이션 시작 `[추정]` |

### Secondary (Indigo / Violet)

| 토큰 | Hex | 용도 |
|------|-----|------|
| `secondary-600` | `#4F46E5` | 태그 배지 텍스트, 로고 아이콘 accent |
| `secondary-500` | `#6366F1` | 링크 hover, 포커스 링 `[추정]` |

### Semantic

| 토큰 | Hex | 용도 |
|------|-----|------|
| `success-500` | `#22C55E` | "지원가능" 뱃지, 적합도 높음 |
| `success-100` | `#DCFCE7` | 성공 배경 `[추정]` |
| `warning-500` | `#F59E0B` | 적합도 보통 `[추정]` |
| `error-500`   | `#EF4444` | 적합도 낮음, 에러 `[추정]` |

### Neutral

| 토큰 | Hex | 용도 |
|------|-----|------|
| `neutral-900` | `#111827` | 카드 제목, 본문 강조 텍스트 |
| `neutral-700` | `#374151` | 본문 텍스트 |
| `neutral-500` | `#6B7280` | 보조 텍스트, placeholder |
| `neutral-300` | `#D1D5DB` | 카드 border, 구분선 |
| `neutral-200` | `#E5E7EB` | 입력 필드 border |
| `neutral-100` | `#F3F4F6` | 페이지 배경, 인용 블록 배경 |
| `neutral-50`  | `#F9FAFB` | 카드 hover 배경 `[추정]` |
| `white`       | `#FFFFFF` | 카드 배경, 내비게이션 배경 |

### Tailwind CSS 매핑

```
// tailwind.config 에서 extend.colors
colors: {
  brand: {
    50:  '#F5F6FF',
    100: '#EEF0FF',
    800: '#1E2A78',
    900: '#1B2559',
  },
  indigo: {
    500: '#6366F1',
    600: '#4F46E5',
  },
}
```

---

## 2. 타이포그래피

**Font Family**: `'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif`

| 토큰 | Size | Weight | Line-Height | Letter-Spacing | 용도 |
|------|------|--------|-------------|----------------|------|
| `display-lg` | 40px (2.5rem) | 800 (ExtraBold) | 1.2 | -0.02em | Hero 헤딩 |
| `display-sm` | 32px (2rem) | 700 (Bold) | 1.3 | -0.01em | 섹션 제목 `[추정]` |
| `heading-lg` | 24px (1.5rem) | 700 (Bold) | 1.35 | -0.01em | 카드 제목 |
| `heading-sm` | 20px (1.25rem) | 600 (SemiBold) | 1.4 | 0 | 서브 헤딩 |
| `body-lg` | 16px (1rem) | 400 (Regular) | 1.6 | 0 | 본문 |
| `body-md` | 14px (0.875rem) | 400 (Regular) | 1.5 | 0 | 보조 텍스트, 카드 설명 |
| `label-lg` | 14px (0.875rem) | 600 (SemiBold) | 1.4 | 0.02em | 버튼, 내비 링크 |
| `label-sm` | 12px (0.75rem) | 600 (SemiBold) | 1.3 | 0.05em | 태그 배지 (영문 uppercase) |
| `caption` | 12px (0.75rem) | 400 (Regular) | 1.4 | 0 | 부가 설명 |

### 규칙
- 영문 태그 라벨은 **uppercase** + letter-spacing `0.05em` (예: "AI TRANSLATION", "ELIGIBILITY")
- 한글 본문의 최소 크기: 14px
- 적합도 퍼센트 숫자: 32px, Bold

---

## 3. 간격 시스템

**Base unit**: 4px

| 토큰 | 값 | 주요 사용처 |
|------|----|-------------|
| `space-1`  | 4px  | 아이콘-텍스트 갭, 인라인 여백 |
| `space-2`  | 8px  | 태그 내부 패딩, 작은 갭 |
| `space-3`  | 12px | 카드 내부 요소 간격 |
| `space-4`  | 16px | 카드 내부 패딩, 섹션 내 요소 간격 |
| `space-5`  | 20px | 카드 패딩 |
| `space-6`  | 24px | 카드 패딩 (큰 카드), 그룹 간 간격 |
| `space-8`  | 32px | 섹션 간 간격 |
| `space-10` | 40px | 히어로-콘텐츠 갭 `[추정]` |
| `space-12` | 48px | 페이지 상하 여백 |
| `space-16` | 64px | 대형 섹션 간격 |
| `space-20` | 80px | Hero 섹션 상하 패딩 `[추정]` |

---

## 4. 보더 Radius

| 토큰 | 값 | 용도 |
|------|----|------|
| `radius-sm`   | 8px  | 인용 블록, 작은 요소 |
| `radius-md`   | 12px | 버튼, 입력 필드 |
| `radius-lg`   | 16px | 카드 |
| `radius-xl`   | 20px | 큰 카드, 검색바 컨테이너 `[추정]` |
| `radius-full` | 9999px | 태그 배지 (pill), 원형 프로그레스 |

---

## 5. 그림자 (Box Shadow)

| 토큰 | 값 | 용도 |
|------|----|------|
| `shadow-sm`   | `0 1px 2px rgba(0, 0, 0, 0.05)` | 내비게이션 바 |
| `shadow-card` | `0 1px 3px rgba(0, 0, 0, 0.06), 0 4px 12px rgba(0, 0, 0, 0.04)` | 기본 카드 |
| `shadow-lg`   | `0 4px 16px rgba(0, 0, 0, 0.08)` | 카드 hover, 검색바 `[추정]` |
| `shadow-xl`   | `0 8px 24px rgba(0, 0, 0, 0.12)` | 모달, 드롭다운 `[추정]` |

---

## 6. 컴포넌트 규칙

### 6.1 버튼

| 변형 | 배경 | 텍스트 | Radius | 패딩 | 높이 |
|------|------|--------|--------|------|------|
| **Primary** (CTA) | `primary-800` | `white` | `radius-md` (12px) | 16px 24px | 44px |
| **Primary hover** | `primary-900` | `white` | — | — | — |
| **Secondary** | `white` | `primary-800` | `radius-md` | 16px 24px | 44px `[추정]` |
| **Ghost** | `transparent` | `neutral-700` | `radius-md` | 12px 16px | 40px `[추정]` |

- 최소 터치 타겟: 44 x 44px
- 폰트: `label-lg` (14px, SemiBold)
- transition: `all 150ms ease`

### 6.2 카드

| 속성 | 값 |
|------|----|
| 배경 | `white` |
| Border | `1px solid neutral-200` 또는 그림자만 사용 |
| Radius | `radius-lg` (16px) |
| 패딩 | 24px (데스크탑) / 20px (모바일) |
| Shadow | `shadow-card` |
| Hover | `shadow-lg`, 미세한 translateY(-2px) `[추정]` |

**다크 카드 변형** (Smart Q&A):
- 배경: `primary-900`
- 텍스트: `white`
- 태그 배지: 반투명 white 배경

### 6.3 태그 배지

> 4가지 변형을 용도별로 엄격히 구분한다. 한 화면에서 같은 위계의 정보에 같은 변형을 섞어 쓰지 않는다.

#### (A) Status Pill — 상태/카테고리

| 속성 | 값 |
|------|----|
| 배경 | `primary-100` (#EEF0FF) |
| 텍스트 | `secondary-600` (#4F46E5) |
| 폰트 | `label-sm` (12px, SemiBold) |
| 패딩 | 2px 10px |
| Radius | `radius-full` |
| 용도 | 정책 카테고리("복지"), 예정/진행 상태, AI Summary 라벨 |

#### (B) Success Pill — 긍정 상태

| 속성 | 값 |
|------|----|
| 배경 | `success-100` (#DCFCE7) |
| 텍스트 | `success-500` (#22C55E) |
| 폰트 | `label-sm` (12px, SemiBold) |
| 패딩 | 2px 10px |
| Radius | `radius-full` |
| 용도 | "진행중", "지원가능", 적합도 높음 |
| 옵션 | 왼쪽에 6px 도트 인디케이터 추가 가능 |

#### (C) Subtle Badge — 부가 메타

| 속성 | 값 |
|------|----|
| 배경 | `neutral-100` (#F3F4F6) |
| 텍스트 | `neutral-700` (#374151) |
| 폰트 | `caption` (12px, Regular) |
| 패딩 | 2px 8px |
| Radius | `radius-md` (12px) |
| 용도 | 버전 번호, 카운트, 중요도 낮은 인라인 메타 |

#### (D) Pill Chip — 필터/태그

| 속성 | 값 |
|------|----|
| 배경 | `white` |
| Border | `1px solid neutral-300` (#D1D5DB) |
| 텍스트 | `neutral-800` (#1F2937) |
| 폰트 | `caption` (12px, Medium) |
| 패딩 | 4px 12px |
| Radius | `radius-full` |
| 용도 | 정책 상세의 관련 태그(`#청년`, `#주거`), 필터 칩, 보조 분류 |
| 규칙 | Status/Success Pill과 같은 줄에 두지 않는다. 태그 텍스트는 `#` prefix로 시각적 구분 |

#### 선택 가이드

| 상황 | 사용 변형 |
|------|----------|
| "이게 어떤 종류의 정책인가?" | (A) Status Pill |
| "지금 신청 가능한가?" | (B) Success Pill / (A) 상태 pill |
| "v2.4 / 3건 남음" | (C) Subtle Badge |
| "어떤 주제·대상에 해당하는가?" | (D) Pill Chip |

### 6.4 검색바

| 속성 | 값 |
|------|----|
| 컨테이너 높이 | 56px `[추정]` |
| 배경 | `white` |
| Border | `1px solid neutral-200` |
| Radius | `radius-xl` (20px) |
| Shadow | `shadow-card` (포커스 시 `shadow-lg`) |
| 아이콘 | 좌측 검색 아이콘, `neutral-500` |
| 버튼 | 우측 내장 "검색" 버튼 (Primary 스타일, 별도 radius) |

### 6.5 입력 필드

| 속성 | 값 |
|------|----|
| 높이 | 44px |
| 배경 | `white` |
| Border | `1px solid neutral-200` |
| Radius | `radius-md` (12px) |
| 포커스 border | `secondary-500` |
| 포커스 ring | `0 0 0 3px rgba(99, 102, 241, 0.15)` `[추정]` |
| placeholder 색상 | `neutral-500` |

### 6.6 내비게이션 바

| 속성 | 값 |
|------|----|
| 높이 | 64px `[추정]` |
| 배경 | `white` |
| Shadow | `shadow-sm` |
| 로고 | 아이콘 + "YouthFit" 텍스트 (16px, Bold) |
| 링크 | `label-lg` (14px, SemiBold), `neutral-700` |
| 링크 hover | `primary-800` |
| CTA 버튼 | Primary 버튼 스타일 |

### 6.7 적합도 원형 프로그레스

| 속성 | 값 |
|------|----|
| 크기 | 96px (외부), 80px (내부) `[추정]` |
| 트랙 색상 | `neutral-200` |
| 진행 색상 | `primary-800` ~ `secondary-500` (그라데이션) `[추정]` |
| 중앙 텍스트 | 32px, Bold, 퍼센트 값 |
| 하단 라벨 | `caption`, `neutral-500` |

### 6.8 인용 블록 (정책 원문)

| 속성 | 값 |
|------|----|
| 배경 | `neutral-100` |
| Border | 없음 (배경으로 구분) |
| Radius | `radius-sm` (8px) |
| 패딩 | 16px |
| 폰트 | `body-md` (14px, Regular), `neutral-700` |

### 6.9 번호 배지 (01, 02, 03 ...)

| 속성 | 값 |
|------|----|
| 크기 | 32px circle |
| 배경 | `neutral-100` |
| 텍스트 | `neutral-500`, 14px, SemiBold |
| Radius | `radius-full` |

---

## 7. 레이아웃 그리드 & 브레이크포인트

### 브레이크포인트

| 토큰 | 값 | 설명 |
|------|----|------|
| `mobile` | < 640px | 모바일 (기본, mobile-first) |
| `sm` | 640px | 소형 태블릿 |
| `md` | 768px | 태블릿 |
| `lg` | 1024px | 소형 데스크탑 |
| `xl` | 1280px | 데스크탑 |

### 그리드

| 속성 | 모바일 (< 768px) | 데스크탑 (>= 768px) |
|------|-------------------|---------------------|
| 컨테이너 max-width | 100% | 1200px `[추정]` |
| 컨테이너 패딩 | 16px | 24px |
| 그리드 컬럼 | 1 | 12 |
| 거터 | 16px | 24px |

### Feature 카드 그리드 (Hero 아래 섹션)

스크린샷 기준 레이아웃:
```
Desktop (≥ 1024px):
┌─────────────┬─────────────┐
│  01 카드     │  02 카드     │
│  (span 6)   │  (span 6)   │
├────────┬────┴───┬─────────┤
│ 03카드  │ Q&A   │ 04 카드  │
│(span 5)│(span 4)│(span 3) │
└────────┴────────┴─────────┘

Mobile (< 768px):
┌─────────────┐
│  01 카드     │
├─────────────┤
│  02 카드     │
├─────────────┤
│  03 카드     │
├─────────────┤
│  04 카드     │
└─────────────┘
```

---

## 8. 애니메이션 & 트랜지션

| 토큰 | 값 | 용도 |
|------|----|------|
| `transition-fast` | `150ms ease` | 버튼 hover, 색상 변경 |
| `transition-base` | `200ms ease` | 카드 hover, 확장/축소 |
| `transition-slow` | `300ms ease-out` | 모달 진입, 페이지 전환 `[추정]` |

---

## 9. 전체 디자인 톤

- **깔끔하고 신뢰감 있는** 톤: 정부 정책 정보를 다루므로 과하지 않은 시각적 표현
- **White-dominant**: 흰 배경 카드 중심, 네이비 포인트
- **대비 강한 CTA**: 네이비 버튼이 밝은 배경에서 눈에 띄게
- **정보 계층 명확**: 태그 → 제목 → 설명 → 부가정보 순서로 시각 위계 구성
- **둥근 모서리 중심**: 전반적으로 friendly한 느낌 (radius 12-16px)
