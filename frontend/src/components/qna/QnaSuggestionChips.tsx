const SUGGESTIONS = [
  '신청 자격이 어떻게 되나요?',
  '어떤 서류가 필요한가요?',
  '신청은 언제까지인가요?',
  '지원 금액은 얼마인가요?',
] as const;

interface Props {
  onPick: (question: string) => void;
}

export function QnaSuggestionChips({ onPick }: Props) {
  return (
    <div className="flex flex-wrap justify-center gap-2">
      {SUGGESTIONS.map((q) => (
        <button
          key={q}
          type="button"
          onClick={() => onPick(q)}
          className="rounded-full border border-[--color-chat-accent]/30 bg-[--color-chat-accent]/10 min-h-11 px-[14px] py-2 text-[13px] text-[--color-chat-accent] transition hover:-translate-y-px hover:border-[--color-chat-accent]/50 hover:bg-[--color-chat-accent]/25 focus-visible:outline-2 focus-visible:outline-[--color-chat-accent] focus-visible:outline-offset-2"
        >
          {q}
        </button>
      ))}
    </div>
  );
}
