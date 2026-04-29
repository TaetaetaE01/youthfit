# ENTITIES.md

YouthFit 백엔드의 JPA 엔티티 및 테이블 스키마 레퍼런스. 모듈별 도메인 모델과 주요 컬럼·제약·연관관계를 설명한다.

> 의존 방향은 `Presentation → Application → Domain` 이며, 엔티티는 `domain/model` 패키지에만 둔다. 상세 레이어 규칙은 `backend/CLAUDE.md`를, 모듈 경계는 `docs/ARCHITECTURE.md`를 참고한다.

## 공통 규칙
- **기본키**: 모든 엔티티는 `Long id` + `GenerationType.IDENTITY` (단, `LegalDong`은 행정코드 `String code`를 PK로 사용).
- **생성자 가시성**: `@NoArgsConstructor(access = PROTECTED)` + `@Builder` 조합으로 객체 생성을 통제한다.
- **세터 금지**: 상태 변경은 도메인 의미를 담은 메서드(예: `Policy.open()`, `User.updateProfile()`)로만 수행한다.
- **BaseTimeEntity** (`common/domain/BaseTimeEntity`): `created_at`, `updated_at` 을 JPA Auditing 으로 자동 관리. 대부분의 엔티티가 이를 상속한다. 예외는 `NotificationHistory`(자체 `sent_at` 사용), `LegalDong`(불변 참조 데이터).
- **Enum 저장**: 모두 `@Enumerated(EnumType.STRING)`.
- **Lazy 기본**: `@ManyToOne`, `@OneToMany`, `@ElementCollection` 모두 `FetchType.LAZY`.

---

## 1. User 모듈 (`com.youthfit.user.domain.model`)

### 1.1 User — `users`
카카오 OAuth 로그인 기반 사용자 계정.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, IDENTITY | |
| email | VARCHAR | unique | 카카오 계정 이메일(선택) |
| nickname | VARCHAR(50) | NOT NULL | |
| profile_image_url | VARCHAR | nullable | |
| auth_provider | VARCHAR(20) | NOT NULL, enum | `AuthProvider` — 현재 `KAKAO` |
| provider_id | VARCHAR | NOT NULL | OAuth provider 고유 ID |
| role | VARCHAR(10) | NOT NULL, enum | `Role` — `USER` / `ADMIN` |
| refresh_token | VARCHAR | nullable | JWT refresh token |
| created_at / updated_at | TIMESTAMP | Auditing | |

- **Unique**: `uk_users_auth_provider_provider_id (auth_provider, provider_id)` — 동일 OAuth 계정 중복 가입 방지.
- **도메인 메서드**: `updateProfile`, `updateEmail`, `updateRefreshToken`, `clearRefreshToken` (로그아웃 시).

### 1.2 EligibilityProfile — `eligibility_profile`
사용자별 적합도 판정 입력 프로필. 사용자당 1개.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| user_id | BIGINT | **unique**, FK(논리적 → users.id) |
| legal_dong_code | VARCHAR(10) | 거주지 법정동 코드, idx 존재 |
| age | INT | |
| marital_status | VARCHAR(10) enum | `MaritalStatus` |
| income_min / income_max | BIGINT | 연소득 범위(원) |
| education | VARCHAR(30) enum | `Education` |
| employment_kind | VARCHAR(20) enum | `EmploymentKind` |
| major_field | VARCHAR(20) enum | `MajorField` |
| specialization_field | VARCHAR(20) enum | `SpecializationField` |

- **팩토리**: `EligibilityProfile.empty(userId)` — 신규 사용자 가입 시 빈 프로필 선할당.
- **변경 메서드**: 각 필드별 `change...` 메서드로만 수정 가능.

### 1.3 Bookmark — `bookmark`
사용자가 저장한 정책.

- `user_id`, `policy_id` (NOT NULL)
- Unique: `uk_bookmark_user_policy(user_id, policy_id)` — 동일 정책 중복 북마크 방지.

### 1.4 PolicyNotificationSubscription — `policy_notification_subscription`
정책별 마감 알림 구독.

- 구조는 Bookmark 와 유사(`user_id`, `policy_id` + unique).
- 실제 알림 발송 여부는 `NotificationSetting`, 중복 발송 방지는 `NotificationHistory` 가 담당.

### 1.5 NotificationSetting — `notification_setting`
사용자별 알림 환경설정. 사용자당 1개.

| 컬럼 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| user_id | BIGINT unique | | |
| email_enabled | BOOLEAN | true | 이메일 알림 on/off |
| days_before_deadline | INT | 7 | 마감 며칠 전 발송 |

### 1.6 NotificationHistory — `notification_history`
이미 발송된 알림 기록(중복 발송 방지용).

- `BaseTimeEntity` 를 쓰지 않고 `@CreatedDate sent_at` 컬럼만 둔다.
- Unique: `uk_notification_user_policy_type(user_id, policy_id, notification_type)` — 동일 사용자·정책·알림타입 조합은 한 번만.

---

## 2. Policy 모듈 (`com.youthfit.policy.domain.model`)

### 2.1 Policy — `policy`
정책의 정규화된 핵심 엔티티. 수집원(`PolicySource`)과 분리되어 있다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| title | VARCHAR | 정책명 |
| summary | TEXT | 한 줄 요약 |
| body | TEXT | 본문(정제된 원문) |
| support_target | TEXT | 지원 대상 |
| selection_criteria | TEXT | 선정 기준 |
| support_content | TEXT | 지원 내용 |
| organization | VARCHAR(200) | 주관 기관 |
| contact | VARCHAR(300) | 문의처 |
| category | VARCHAR(30) enum | `Category` |
| region_code | VARCHAR(20) | 적용 지역(법정동 코드) |
| apply_start / apply_end | DATE | 신청 기간 |
| status | VARCHAR(20) enum | `PolicyStatus` — `UPCOMING`/`OPEN`/`CLOSED` |
| detail_level | VARCHAR(10) enum | `DetailLevel` — 수집 상세도 (`LITE`→`FULL` 단방향 업그레이드) |

- **도메인 메서드**
  - `open()`: `UPCOMING → OPEN` 상태 전이 (다른 상태에서 호출 시 예외).
  - `close()`: `OPEN → CLOSED`.
  - `upgradeDetailLevel(newLevel)`: 기존 레벨 이상으로만 올림 (다운그레이드 방지).
  - `isOpen()`, `isExpired()`: 상태 조회.
  - `updateInfo(...)`: 수집 파이프라인에서 정규화된 데이터로 전체 갱신.
  - `replaceTags(life, theme, target)`: 세 종류의 태그를 한 번에 교체.
  - `replaceAttachments(list)`: 첨부 전체 교체 (orphanRemoval).

- **연관관계**
  - `@OneToMany(mappedBy="policy", cascade=ALL, orphanRemoval=true)` → `PolicyAttachment`
  - 태그 3종은 `@ElementCollection` 으로 별도 테이블에 저장:
    - `policy_life_tag(policy_id, tag)` — 생애 주기 태그(청년·신혼부부 등)
    - `policy_theme_tag(policy_id, tag)` — 주제 태그(주거·취업 등)
    - `policy_target_tag(policy_id, tag)` — 대상 태그

### 2.2 PolicyAttachment — `policy_attachment`
정책 첨부파일(공고문 PDF 등).

- `policy_id` (FK, `@ManyToOne`)
- `name`(300), `url`(1000), `media_type`(100)
- `assignTo(Policy)` 는 **패키지 프라이빗** — 외부 모듈이 직접 연결 못 하게 한다. 반드시 `Policy.replaceAttachments()` 경로로 부착.

### 2.3 PolicySource — `policy_source`
정책의 외부 수집원(청년정책 API, 복지로 등) 원천 데이터 보관.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT FK | |
| source_type | VARCHAR(30) enum | `SourceType` — 수집 채널 |
| external_id | VARCHAR | 원천 시스템의 고유 ID |
| source_url | VARCHAR | 원문 URL |
| raw_json | TEXT | 원본 JSON 스냅샷 |
| source_hash | VARCHAR(64) | 원본 해시 (변경 감지용) |

- **변경 감지**: `hasChanged(newHash)` 으로 재수집 시 해시 비교, 바뀐 경우만 `updateSource()` 호출 → LLM/임베딩 비용 방어 (`CLAUDE.md` 규칙).

---

## 3. Guide 모듈 (`com.youthfit.guide.domain.model`)

### 3.1 Guide — `guide`
LLM으로 생성된 정책 해설 콘텐츠. 정책당 1개. 페어드 레이아웃(원문 ↔ 쉬운 해석)을 위해 구조화된 JSON으로 저장.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT | **unique** |
| content | JSONB NOT NULL | `oneLineSummary`, `target/criteria/content` 페어드 섹션, `pitfalls` 배열 |
| source_hash | VARCHAR(64) | Policy 구조화 필드 + referenceYear + 청크 결합 해시 |

- `hasChanged(newHash)` + `regenerate(content, newHash)` 로 원본이 바뀐 경우에만 재생성.
- `content` JSON 스키마: `docs/superpowers/specs/DONE_2026-04-28-easy-policy-interpretation-design.md` 4.1 참조.

---

## 4. RAG 모듈 (`com.youthfit.rag.domain.model`)

### 4.1 PolicyDocument — `policy_document`
정책 본문 + 첨부 추출 텍스트의 청크 + 임베딩. **pgvector 확장** 사용.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT | |
| chunk_index | INT | 청크 순서 |
| content | TEXT | 청크 본문 |
| source_hash | VARCHAR(64) | 원본 해시(변경 시 재임베딩) |
| embedding | vector(1536) | OpenAI `text-embedding-3-small` 차원 |
| attachment_id | BIGINT NULL | 첨부 청크인 경우 `PolicyAttachment.id`, 정책 본문 청크는 NULL |
| page_start | INT NULL | 청크가 걸친 PDF 시작 페이지 (1-based). HWP/본문/페이지 메타 없는 경우 NULL |
| page_end | INT NULL | 청크가 걸친 PDF 끝 페이지. 단일 페이지 청크면 `page_start == page_end` |

- Hibernate 6 `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = 1536)` 로 pgvector 매핑.
- `updateEmbedding(float[])`, `hasEmbedding()` 제공.
- 인덱스: `idx_policy_document_attachment` on `attachment_id` (가이드 검증 시 정책별 청크의 첨부 매핑 빠른 조회).
- `DocumentChunker` 가 mergedContent 의 본문/첨부 boundary (`=== 정책 본문 ===` / `=== 첨부 attachment-id=N ===`) 에서 청크 강제 분할 → 한 청크 = 단일 출처 보장. 첨부 segment 안의 페이지 마커 (`--- page=N ---`) 로 청크별 페이지 range 추적.

---

## 5. Eligibility 모듈 (`com.youthfit.eligibility.domain.model`)

### 5.1 EligibilityRule — `eligibility_rule`
정책 단위로 저장되는 규칙 기반 적합도 조건.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT | |
| field | VARCHAR(30) | 사용자 프로필 필드명(`age`, `income`, `legalDongCode` 등) |
| operator | VARCHAR(20) enum | `RuleOperator` — `GTE`, `LTE`, `EQ`, `IN`, ... |
| value | VARCHAR | 비교 대상 값(단일/리스트 직렬화) |
| label | VARCHAR(50) | UI 표시용 라벨 (예: "만 19~34세") |
| source_reference | TEXT | 규칙 출처 문구 |

- 한 정책은 여러 개의 규칙을 가질 수 있고, 규칙들의 AND 결과로 적합도를 판정한다.

---

## 6. Region 모듈 (`com.youthfit.region.domain.model`)

### 6.1 LegalDong — `legal_dong`
행정표준코드 기반 법정동 참조 테이블. **`BaseTimeEntity` 미상속 (불변 마스터 데이터)**.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| code | VARCHAR(10) | **PK**, 행정표준코드 |
| sido_name | VARCHAR(30) NOT NULL | 시/도명 |
| sigungu_name | VARCHAR(40) | 시/군/구명 (시도는 null) |
| level | VARCHAR(10) enum | `RegionLevel` — `SIDO`/`SIGUNGU` |
| parent_code | VARCHAR(10) | 상위 법정동 코드, idx 존재 |
| active | BOOLEAN NOT NULL | 폐지/통합 여부 |

- `displayName()`: 레벨에 따라 시도명 또는 시군구명 반환.
- 사용처: `EligibilityProfile.legalDongCode`, `Policy.regionCode` 의 참조 값.

---

## 7. QnA 모듈 (`com.youthfit.qna.domain.model`)

### 7.1 QnaHistory — `qna_history`
정책 기반 RAG Q&A 로그.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| user_id / policy_id | BIGINT | |
| question | TEXT NOT NULL | |
| answer | TEXT | 스트리밍 완료 시 채워짐 |
| sources | TEXT | 인용된 청크 근거(JSON 직렬화) |

- `@Builder` 는 질문 시점에 호출되고, 답변 스트리밍 종료 후 `completeAnswer(answer, sources)` 로 마무리.

---

## 8. 엔티티 관계 개요 (논리적 FK)

```
User (1) ──┬── (1) EligibilityProfile
           ├── (N) Bookmark            ── (N) Policy
           ├── (N) PolicyNotificationSubscription ── (N) Policy
           ├── (1) NotificationSetting
           ├── (N) NotificationHistory
           └── (N) QnaHistory          ── (N) Policy

Policy (1) ──┬── (N) PolicyAttachment  [JPA FK, cascade ALL]
             ├── (N) PolicySource
             ├── (N) PolicyDocument   (RAG 청크/임베딩)
             ├── (N) EligibilityRule
             ├── (1) Guide
             └── (N) policy_*_tag     [@ElementCollection]

LegalDong  ◄── EligibilityProfile.legalDongCode
LegalDong  ◄── Policy.regionCode
```

> `Policy` ↔ `PolicyAttachment` 만 실제 JPA 연관관계(`@ManyToOne`/`@OneToMany`)로 매핑되어 있고, 나머지는 **논리적 FK(Long id 보관)** 로 모듈 간 의존을 낮춘다. 이는 모듈 독립성(모듈 경계 유지 원칙, 루트 `CLAUDE.md`)과 일관된 설계다.

---

## 9. 엔티티 사용 가이드

### 9.1 생성·수정 흐름
1. Application 서비스가 `@Transactional` 경계 내에서 리포지토리로 엔티티를 조회/저장한다.
2. 상태 변경은 **엔티티의 도메인 메서드**로만 수행 (`policy.open()` 등). 서비스 레이어에서 필드를 직접 set하지 않는다.
3. 변경 후 더티체킹으로 플러시되거나, 새 엔티티는 `repository.save()` 로 영속화한다.

### 9.2 Controller 응답 시 주의
- Controller에는 Entity를 그대로 내보내지 않는다. Application의 `Result` record → Presentation `Response` record 변환 경로를 사용한다(`backend/CLAUDE.md`).

### 9.3 변경 감지 패턴
- `PolicySource.sourceHash`, `Guide.sourceHash`, `PolicyDocument.sourceHash` 는 모두 **원본 변경 감지** 용도다. 재수집·재임베딩·재요약 전 반드시 `hasChanged(newHash)` 로 확인해 LLM/임베딩 비용을 방어한다.

### 9.4 Enum 추가/수정
- Enum은 DB에 String으로 저장되므로 **이름 변경은 파괴적 마이그레이션**이 된다. 리네이밍 대신 새 값을 추가하고 기존 값을 deprecated 처리하는 방향을 권장.

### 9.5 pgvector 주의
- `PolicyDocument.embedding` 의 차원(1536)은 사용하는 임베딩 모델과 반드시 일치해야 한다. 모델을 변경하면 전량 재임베딩이 필요하다.
- 유사도 검색은 pgvector 연산자(`<=>`, `<->`)를 사용하는 네이티브 쿼리/Repository 에서 수행한다.

### 9.6 중복 방지 제약
- `Bookmark`, `PolicyNotificationSubscription`, `NotificationHistory`, `User(provider)`, `EligibilityProfile(user)` 는 모두 unique 제약으로 **의미 중복**을 DB 레벨에서 막는다. Application 레이어에서 사전 체크 후 insert 하더라도 race condition 대비로 제약을 그대로 두고, `DataIntegrityViolationException` 을 도메인 예외로 매핑한다.

---

## 10. 참고
- 모듈 경계·의존 방향: `docs/ARCHITECTURE.md`
- DTO/Controller/Service 컨벤션: `docs/CONVENTIONS.md`, `backend/CLAUDE.md`
- 제품 관점의 필드 의미(특히 적합도 프로필 필드): `docs/PRODUCT.md`, `docs/PRD.md`
