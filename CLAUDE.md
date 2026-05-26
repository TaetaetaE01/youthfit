# CLAUDE.md

## 프로젝트
- **이름**: YouthFit
- **목표**: 흩어진 청년 정책 정보를 한곳에 모으고, 쉬운 설명·가벼운 적합도 판정·출처 기반 Q&A 를 통해 사용자가 자격 요건, 준비사항, 다음 행동을 이해할 수 있도록 돕는다.
- **포지셔닝**: YouthFit 은 공식 정책 포털을 대체하지 않는다. 정책을 더 쉽게 찾고 이해하도록 돕고, 최종 신청은 공식 신청 채널로 연결하는 보완형 서비스다.

## MVP (v0) 범위
**포함**: 정책 목록·상세·검색, 카카오 로그인, 프로필, 적합도 판정, RAG Q&A, 북마크, 이메일 알림
**제외**: 커뮤니티·평점, 모바일 앱·푸시, 관리자 대시보드, 하이브리드 검색, 이벤트 드리븐 아키텍처, 외부 공개 API 연동 (초기 크롤링 제외)

## 프로젝트 구조
```
youthfit/
├── CLAUDE.md              # 공통 지침 (이 파일)
├── .claude/rules/         # 컨벤션 (공통 + backend/ + frontend/)
├── backend/               # Spring Boot — backend/CLAUDE.md, backend/docs/
├── frontend/              # React — frontend/CLAUDE.md, frontend/docs/
├── docs/                  # 공통 제품·아키텍처 문서
└── n8n/                   # 워크플로우 설정
```

## 모듈 경계

### 백엔드 모듈
- `admin` 어드민 도구 (정책 enrichment 리뷰, RAG 미리보기, 이메일 로그, Q&A 캐시, LLM 비용, ingestion 헬스, 대시보드)
- `auth` 카카오 OAuth + JWT
- `common` 공통 유틸·횡단 관심사
- `eligibility` 규칙 기반 적합도 판정
- `guide` 구조화된 AI 가이드 콘텐츠 생성
- `ingestion` n8n·외부 수집 파이프라인 수신
- `policy` 정책 도메인·정규화·중복 제거
- `qna` 정책 Q&A·스트리밍 응답
- `rag` 임베딩·청크 분할·벡터 조회
- `user` 프로필·북마크·알림

### 프론트엔드 주요 영역
- 정책 탐색 (목록·상세·검색) / 인증 (카카오·토큰) / 사용자 (프로필·북마크·알림) / 적합도 판정 UI / Q&A 스트리밍 UI

## 컨벤션 (반드시 따른다)
@.claude/rules/common.md

- 백엔드 코드 수정 전 → `backend/CLAUDE.md` (모듈 진입 시 자동 로드, `.claude/rules/backend/` 5 개 파일을 `@` 참조함)
- 프론트엔드 코드 수정 전 → `frontend/CLAUDE.md` (모듈 진입 시 자동 로드, `.claude/rules/frontend/` 3 개 파일을 `@` 참조함)

## 문서 맵
| 위치 | 내용 |
|------|------|
| `docs/PRODUCT.md` | 제품 목표·타겟·정책 해석 원칙 |
| `docs/ARCHITECTURE.md` | 모듈 경계·레이어·데이터 흐름·인프라 |
| `docs/PRD.md` | 상세 기능 요구사항 (통합본) |
| `docs/prd/` | 도메인별 PRD (분할판, 활성화 시점에 사용) |
| `docs/OPS.md` | 환경변수·배포·시크릿·운영 안전장치 |
| `docs/ops/cost-snapshot.md` | AWS prod 인프라 비용 스냅샷 |
| `docs/runbooks/` | 배포 전 절차·운영 활성 상태 메모 |
| `docs/troubleshooting/` | 백/프/인프라 디버깅 회고 |
| `docs/superpowers/` | brainstorming → spec → plan 사이클 산출물 |
| `backend/docs/ENTITIES.md` | JPA 엔티티·스키마 레퍼런스 |
| `backend/docs/INGESTION_PIPELINE.md` | n8n → DB 적재 파이프라인 |
| `backend/docs/CONTENT_GENERATION_FLOW.md` | 가이드·룰·RAG·Q&A 이벤트 흐름 |
| `frontend/docs/DESIGN.md` | 디자인 토큰 (컬러·타이포·간격) |

## 수정 전에 읽기
- 모듈 경계·의존 방향 변경 → `docs/ARCHITECTURE.md`
- 새 기능 구현 → `docs/PRD.md` (또는 활성화된 경우 `docs/prd/0X-<domain>.md`)
- 사용자 해석 방식 변경 → `docs/PRODUCT.md`
- 크롤링·배포·시크릿 → `docs/OPS.md`
- 백엔드 코드 → `backend/CLAUDE.md` + 관련 `backend/docs/*`
- 프론트엔드 코드 → `frontend/CLAUDE.md` + `frontend/docs/DESIGN.md`

## Claude Code 기능 관련 메모
- **Plan Mode** 는 큰 리팩토링이나 위험한 변경 전에 읽기 중심으로 범위를 파악하고 계획을 세울 때 유용하다.
- **Custom Subagents** 는 반복적으로 역할 분리가 필요할 때만 선택적으로 도입한다.
- 장기 유지보수 가치가 충분하지 않다면 기능별 에이전트 규칙을 여기에 추가하지 않는다.
