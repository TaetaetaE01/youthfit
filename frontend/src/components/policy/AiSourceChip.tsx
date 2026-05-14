import { Bot } from 'lucide-react';

interface Props {
  sourceUrl?: string | null;
  className?: string;
}

export function AiSourceChip({ sourceUrl, className }: Props) {
  const title = sourceUrl
    ? `정책 안내 페이지(${sourceUrl})에서 AI가 자동으로 정리한 정보입니다`
    : 'AI가 외부 페이지에서 자동으로 정리한 정보입니다';
  return (
    <span
      title={title}
      className={
        'inline-flex items-center gap-1 rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-medium text-violet-700 ring-1 ring-violet-200 ' +
        (className ?? '')
      }
    >
      <Bot className="h-3 w-3" />
      AI 자동 수집
    </span>
  );
}
