# 정책 출처 뱃지 노출 (BOKJIRO / 온통청년 / 청년서울)

> 작성: 2026-04-28
> 상태: spec
> 후속 작업 (별도 spec): ingestion 단계의 다중 출처 정책 dedup — `docs/superpowers/2026-04-28-next-steps.md` 참조

## 1. 배경

- 현재 정책은 복지로(BOKJIRO) API와 청년서울(Youth Seoul) 워크플로우에서 수집된다.
- 가까운 시일 내에 온통청년(YOUTH_CENTER, youthcenter.go.kr) API 수집이 추가될 예정이다.
- 사용자가 정책 카드/상세에서 **이 정책이 어느 출처에서 왔는지**를 시각적으로 구분할 수 있어야 한다 (출처별 신뢰도/주관 부서 가늠).
- DB에는 이미 `PolicySource(policy_id, source_type, external_id, ...)` 가 1:N 으로 저장되어 있다. `SourceType` enum 또한 이미 3개 값을 모두 포함한다.
- 단, `PolicyQueryService` 는 응답에 `sourceUrl` 만 노출하고 `sourceType` 은 버려진다 → 프론트가 출처를 알 방법이 없다.

## 2. 목표 / 비목표

### 목표
- 정책 **상세 응답** 과 **목록(카드) 응답** 에 `sourceType`, `sourceLabel` 두 필드를 추가한다.
- 프론트에 출처별 로고 이미지를 표시하는 `SourceBadge` 컴포넌트를 추가하고, 카드/상세에 부착한다.
- 로고는 mock SVG 자산으로 시작하고, 추후 운영팀이 S3 URL을 제공하면 컴포넌트 인터페이스 변경 없이 교체 가능하게 둔다.

### 비목표 (이번 spec 범위 밖)
- 출처 기반 정렬/필터링.
- 다중 출처 정책의 dedup/머지 로직 (별도 spec, 후속 메모 참조).
- ingestion / n8n 워크플로우 / DB 스키마 / `SourceType` enum 값 변경.
- `PolicyDetail` 외 다른 응답(북마크 목록 등)에 출처 노출.

## 3. 결정 사항 (브레인스토밍 결과)

| 항목 | 결정 | 근거 |
|---|---|---|
| 노출 위치 | 정책 상세 + 정책 목록 | 카드에서도 출처 구분 필요. 정렬/필터는 미사용 |
| 한글 라벨 위치 | 백엔드에서 `sourceLabel` 같이 내림 | 사용자 결정. enum 추가 시 BE 한 곳에서 관리 |
| 로고 이미지 위치 | 프론트 정적 자산 (`src/assets/source-logos/`) | 디자인 자산은 프론트 레포 가까이가 자연스러움 |
| 로고 초기 자산 | mock SVG 3장 | S3 URL 받을 때까지 placeholder. 인터페이스 무변경으로 교체 |
| 다중 출처 처리 | 단일 출처(첫 번째)만 노출 | 현재 `findFirstByPolicyId` 패턴 유지. 다중 출처는 dedup 결정 후 다룸 |

## 4. 아키텍처

### 4.1 손대는 모듈
- `policy` (BE)
- `frontend` (FE)

### 4.2 무변경
- `ingestion` 모듈, n8n 워크플로우
- `SourceType` enum 값 자체 (`YOUTH_SEOUL_CRAWL`, `BOKJIRO_CENTRAL`, `YOUTH_CENTER`)
- `PolicySource` 엔티티 / DB 스키마 / 마이그레이션
- `RegisterPolicyCommand`, `IngestPolicyCommand`

### 4.3 데이터 흐름

```
[기존]
PolicyQueryService.findPolicyById
  → policySourceRepository.findFirstByPolicyId(id) → sourceUrl 만 추출
  → PolicyDetailResult.from(policy, sourceUrl)

[변경 후]
PolicyQueryService.findPolicyById
  → policySourceRepository.findFirstByPolicyId(id) → PolicySource 통째로
  → PolicyDetailResult.from(policy, source)   // sourceUrl + sourceType + sourceLabel 추출

PolicyQueryService.toPageResult (목록)
  → ids = page.content.map(Policy::getId)
  → sourceMap = policySourceRepository.findFirstByPolicyIds(ids)   // N+1 회피
  → page.content.map(p -> PolicySummaryResult.from(p, sourceMap.get(p.getId())))
```

## 5. 백엔드 변경 상세

### 5.1 `SourceType` enum 한글 라벨 추가
파일: `policy/domain/model/SourceType.java`
```java
public enum SourceType {
    YOUTH_SEOUL_CRAWL("청년 서울"),
    BOKJIRO_CENTRAL("복지로"),
    YOUTH_CENTER("온통청년");

    private final String label;
    SourceType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
```
- 도메인 enum이라 Spring/JPA 의존 없음 (백엔드 CLAUDE.md 준수).
- DB에 저장되는 값은 enum name 그대로 (`@Enumerated(EnumType.STRING)`) → 마이그레이션 불필요.

### 5.2 `PolicySourceRepository` 일괄 조회 메서드 추가
파일: `policy/domain/repository/PolicySourceRepository.java`
```java
Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds);
```

구현(`PolicySourceRepositoryImpl`):
- JPA Spring Data: `findAllByPolicyIdIn(List<Long> ids)` 으로 한 번에 조회
- 자바에서 `Collectors.groupingBy(s -> s.getPolicy().getId())` 후 각 그룹의 첫 번째만 추출 → `Map<Long, PolicySource>` 반환
- 빈 입력 리스트 → 빈 Map 즉시 반환 (DB 호출 회피)
- 한 정책에 source가 여러 개 → 도메인 메모: 첫 번째(`id` 오름차순) 만. `findFirstByPolicyId` 와 동일 정렬 기준.

### 5.3 `PolicyQueryService` 변경
파일: `policy/application/service/PolicyQueryService.java`

**`findPolicyById`**:
- 기존: `sourceUrl` 만 추출하여 `PolicyDetailResult.from(policy, sourceUrl)` 호출
- 변경: `PolicySource` 객체 자체를 넘김 → `PolicyDetailResult.from(policy, source)` (`source` 가 `null` 이어도 안전 처리)

**`toPageResult`**:
- 시그니처 변경: `Page<Policy>` 받아서 내부에서 `policySourceRepository.findFirstByPolicyIds(ids)` 호출
- 각 정책에 대해 `PolicySummaryResult.from(p, sourceMap.get(p.getId()))` (`null` 가능)

### 5.4 Result DTO
**`PolicyDetailResult`** 에 필드 추가:
```java
SourceType sourceType,    // null 가능
String sourceLabel        // null 가능 (sourceType 이 null 이면 같이 null)
```
정적 팩토리 시그니처: `from(Policy policy, PolicySource source)` — `source` 에서 `sourceType`, `sourceLabel`, `sourceUrl` 모두 추출 (null safe).

**`PolicySummaryResult`** 에 동일 필드 추가:
정적 팩토리: `from(Policy policy, PolicySource source)` (sourceUrl 은 summary 에 없으므로 type/label 만).

### 5.5 Response DTO
**`PolicyDetailResponse`**, **`PolicySummaryResponse`** 양쪽에 동일 필드 추가:
```java
SourceType sourceType,
String sourceLabel
```
`from(Result)` 정적 팩토리에서 그대로 전달.

## 6. 프론트엔드 변경 상세

### 6.1 타입 추가
파일: `frontend/src/types/policy.ts`
```ts
export type SourceType = 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER';

export interface Policy {
  // ... 기존 필드
  sourceType: SourceType | null;
  sourceLabel: string | null;
}

// PolicyDetail 은 Policy 를 extends 하므로 자동 상속
```

### 6.2 Mock 로고 자산
신규 폴더: `frontend/src/assets/source-logos/`
- `bokjiro.svg` — 텍스트 "복지로" + 단순 배경. viewBox 0 0 100 32, height 32px 기준
- `youth-center.svg` — "온통청년"
- `youth-seoul.svg` — "청년 서울"

요건: 단색 배경 + 텍스트만. S3 URL 받으면 SVG 파일 자체를 교체하거나 `LOGO_MAP` 의 import 경로를 외부 URL 로 바꿈. 컴포넌트 props/시그니처 변경 없음.

### 6.3 신규 컴포넌트 `SourceBadge`
파일: `frontend/src/components/policy/SourceBadge.tsx`
```tsx
import bokjiroLogo from '@/assets/source-logos/bokjiro.svg';
import youthCenterLogo from '@/assets/source-logos/youth-center.svg';
import youthSeoulLogo from '@/assets/source-logos/youth-seoul.svg';
import { cn } from '@/lib/cn';
import type { SourceType } from '@/types/policy';

const LOGO_MAP: Record<SourceType, string> = {
  BOKJIRO_CENTRAL: bokjiroLogo,
  YOUTH_CENTER: youthCenterLogo,
  YOUTH_SEOUL_CRAWL: youthSeoulLogo,
};

interface Props {
  sourceType: SourceType | null;
  sourceLabel: string | null;
  size?: 'sm' | 'md';
}

export default function SourceBadge({ sourceType, sourceLabel, size = 'sm' }: Props) {
  if (!sourceType || !sourceLabel) return null;
  const heightCls = size === 'sm' ? 'h-5' : 'h-6';
  return (
    <span
      className="inline-flex items-center rounded-full border border-gray-200 bg-white px-2 py-0.5"
      title={`출처: ${sourceLabel}`}
    >
      <img src={LOGO_MAP[sourceType]} alt={sourceLabel} className={cn(heightCls, 'w-auto')} />
    </span>
  );
}
```
- a11y: `alt` 텍스트(스크린리더/이미지 로딩 실패 대비) + `title` (마우스 호버 시 한글 라벨)
- props `null` → 미렌더 (조건부 호출 없이 안전하게 사용 가능)

### 6.4 사용 위치
**`PolicyCard.tsx`**: 상단 뱃지 줄 (`<CategoryBadge> <StatusBadge>` 옆)에 `<SourceBadge size="sm" sourceType={policy.sourceType} sourceLabel={policy.sourceLabel} />` 삽입.

**`PolicyDetailPage.tsx`**: 헤더/제목 영역 메타 정보 근처에 `<SourceBadge size="md" ... />` 삽입. (정확한 배치는 구현 단계에서 기존 레이아웃과 맞춰 결정)

### 6.5 무변경
- API 함수 / TanStack Query 훅: 응답 필드만 늘어나므로 무변경
- 다른 페이지 / 컴포넌트
- 라우팅 / 상태관리

## 7. 테스트 계획

### 7.1 백엔드 (JUnit 5, 백엔드 CLAUDE.md `spring-test` 컨벤션)

| 테스트 | 종류 | 케이스 |
|---|---|---|
| `SourceTypeTest` | 단위 | 3개 enum 값의 `getLabel()` 반환 (parameterized) |
| `PolicySourceRepositoryImplTest.findFirstByPolicyIds` | 슬라이스 (`@DataJpaTest`) | (1) 정책 N개 각각 source 1개 → Map 사이즈 N (2) 한 정책에 source 2개 → 첫 번째만 매핑 (3) source 없는 정책 ID 포함 → Map에 누락 (4) 빈 입력 → 빈 Map |
| `PolicyQueryServiceTest.findPolicyById` | 단위 (Mockito) | (1) source 있을 때 result 의 `sourceType`/`sourceLabel`/`sourceUrl` 채워짐 (2) source 없을 때 모두 null |
| `PolicyQueryServiceTest.findPoliciesByFilters` | 단위 (Mockito) | page 의 각 summary 가 sourceMap 에 따라 매핑됨, source 없는 항목은 type/label null |

기존 테스트 파일 존재 여부는 plan 단계에서 확인 후 확장 / 신규 결정.

### 7.2 프론트엔드 (Vitest + Testing Library)

| 테스트 | 케이스 |
|---|---|
| `SourceBadge.test.tsx` | (1) 정상 props → `<img>` 렌더, `alt`/`title` 정확 (2) `sourceType: null` → 미렌더 (`container.firstChild === null`) (3) 3개 enum 각각 올바른 mock 이미지 경로 |

`PolicyCard` / `PolicyDetailPage` 통합 테스트는 사이즈 대비 회귀 가치가 낮아 이번 범위 제외. 카드/상세에 SourceBadge 가 실제로 보이는지는 **`npm run dev` 수동 시각 확인** (frontend CLAUDE.md 의 UI 변경 검증 표준).

### 7.3 빌드/타입체크
- BE: `./gradlew build` 통과
- FE: `npm run build` (타입체크 포함) 통과

## 8. 영향 분석

| 측면 | 영향 |
|---|---|
| DB 마이그레이션 | 없음 |
| API 호환성 | 응답 필드 추가만 → 기존 클라이언트 무영향 (파괴적 변경 없음) |
| 캐시 | 정책 응답 캐시가 있다면 키 무변경으로 신규 필드 자연 반영 (기존 캐시 키 그대로) |
| 성능 | 목록 조회에 `policy_source` 1회 IN 쿼리 추가 (페이지 사이즈만큼의 ID 세트) — 인덱스 `policy_id` 가정 하에 무시 가능 |
| 보안 | 출처 enum 값/한글 라벨은 공개 메타데이터 — 민감 정보 아님 |

## 9. 구현 순서 (plan 단계 입력)

1. 백엔드: `SourceType` 라벨 → `PolicySourceRepository` 메서드 → `PolicyQueryService` → Result/Response DTO → 백엔드 테스트 → 빌드
2. 프론트엔드: 타입 → mock 로고 자산 → `SourceBadge` → `SourceBadge` 테스트 → 카드/상세 부착 → 빌드 → 수동 시각 확인
3. 커밋: BE/FE 분리 또는 한 번에 (plan 단계에서 결정)

## 10. 후속 (이번 spec 범위 밖)

- **ingestion 다중 출처 정책 dedup**: 복지로와 온통청년에서 같은 정책이 들어왔을 때 정책 row 를 어떻게 식별/머지할지. 별도 brainstorming 1사이클 필요. `docs/superpowers/2026-04-28-next-steps.md` 에 메모.
- **출처별 정렬/필터**: 사용자 요청 시 도입.
- **로고 S3 교체**: 운영팀이 URL 제공 후 `LOGO_MAP` 또는 SVG 파일만 교체.
- **북마크 목록 등 다른 응답에 출처 노출**: 필요 시 동일 패턴으로 확장.
