import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import { cn } from '@/lib/cn';

interface Props {
  disabled: boolean;
  placeholder: string;
  onSubmit: (question: string) => void;
  readOnly?: boolean;
  onFocus?: () => void;
}

export function QnaComposer({ disabled, placeholder, onSubmit, readOnly, onFocus }: Props) {
  const [value, setValue] = useState('');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSubmit(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && e.nativeEvent.isComposing) {
      e.preventDefault();
    }
  };

  return (
    <form onSubmit={handleSubmit} className="relative">
      <input
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onFocus={onFocus}
        readOnly={readOnly}
        placeholder={placeholder}
        className={cn(
          'h-11 w-full rounded-[14px] border border-chat-accent/20 bg-chat-accent/10 pl-4 pr-12 text-[15px] text-white outline-none transition-colors placeholder:text-chat-accent/55 focus:bg-chat-accent/18 focus-visible:outline-2 focus-visible:outline-chat-accent',
          readOnly && 'cursor-pointer',
        )}
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        aria-label="질문 전송"
        className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-chat-accent transition hover:bg-chat-accent/15 disabled:opacity-40"
      >
        <Send className="h-4 w-4" />
      </button>
    </form>
  );
}
