import type { FailureReason } from '@/apis/admin.ingestion.api';
import { FailureReasonBadge } from './FailureReasonBadge';

interface ReasonInfo {
  reason: FailureReason;
  summary: string;
  hint: string;
}

const REASONS: ReasonInfo[] = [
  {
    reason: 'VALIDATION',
    summary: '필수 필드 누락이나 형식 위반으로 도메인 검증을 통과하지 못한 경우',
    hint: 'IllegalArgumentException, Bean Validation 제약 위반 등을 분류',
  },
  {
    reason: 'PARSING',
    summary: '원천 JSON 또는 첨부 본문을 읽어들이지 못한 경우',
    hint: '인코딩 깨짐, 응답이 잘림, 스키마 변경이 흔한 원인',
  },
  {
    reason: 'MAPPING',
    summary: '정규화 단계에서 원천 필드를 도메인 모델로 변환하지 못한 경우',
    hint: '새 카테고리/지역 코드 등장, enum 변환 실패가 흔한 원인',
  },
  {
    reason: 'DEDUPLICATION_CONFLICT',
    summary: '같은 외부 ID 또는 해시의 정책이 이미 있어 충돌한 경우',
    hint: '동일 정책이 두 원천에서 들어오거나 멱등성이 깨졌을 때',
  },
  {
    reason: 'OTHER',
    summary: '위 분류에 잡히지 않은 모든 예외',
    hint: '네트워크/외부 API 오류, 예상 못한 NullPointerException 등',
  },
];

interface Props {
  currentReason?: FailureReason;
}

export function FailureReasonGuide({ currentReason }: Props) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <header className="mb-3 flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-slate-800">실패 사유 가이드</h2>
        <span className="text-[11px] text-slate-400">FailureReason</span>
      </header>
      <ul className="space-y-3">
        {REASONS.map((r) => {
          const active = r.reason === currentReason;
          return (
            <li
              key={r.reason}
              className={
                active
                  ? 'rounded-md bg-slate-50 p-2 ring-1 ring-brand-700/30'
                  : 'p-2'
              }
            >
              <div className="mb-1 flex items-center gap-2">
                <FailureReasonBadge reason={r.reason} />
                <code className="text-[11px] text-slate-400">{r.reason}</code>
              </div>
              <p className="text-xs text-slate-700">{r.summary}</p>
              <p className="mt-0.5 text-[11px] text-slate-500">{r.hint}</p>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
