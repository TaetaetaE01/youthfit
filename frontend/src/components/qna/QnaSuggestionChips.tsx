import { cn } from '@/lib/cn';

const DEFAULT_SUGGESTIONS = [
  '신청 자격이 어떻게 되나요?',
  '어떤 서류가 필요한가요?',
  '신청은 언제까지인가요?',
  '지원 금액은 얼마인가요?',
] as const;

type Variant = 'dark' | 'light';

interface Props {
  questions?: readonly string[];
  onPick: (question: string) => void;
  variant?: Variant;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  dark: 'border-chat-accent/40 bg-chat-accent/15 text-chat-accent hover:border-chat-accent/60 hover:bg-chat-accent/25 focus-visible:outline-chat-accent',
  light:
    'border-chat-surface/25 bg-chat-source-bg text-chat-surface hover:border-chat-surface/50 hover:bg-chat-surface/10 focus-visible:outline-chat-surface',
};

export function QnaSuggestionChips({ questions, onPick, variant = 'dark' }: Props) {
  const items = questions ?? DEFAULT_SUGGESTIONS;
  if (items.length === 0) return null;

  return (
    <div className="flex flex-wrap justify-center gap-2">
      {items.map((q) => (
        <button
          key={q}
          type="button"
          onClick={() => onPick(q)}
          className={cn(
            'rounded-full border min-h-11 px-[14px] py-2 text-[13px] font-medium transition hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-offset-2',
            VARIANT_CLASSES[variant],
          )}
        >
          {q}
        </button>
      ))}
    </div>
  );
}
