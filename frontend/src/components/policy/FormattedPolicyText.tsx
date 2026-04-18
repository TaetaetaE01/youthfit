import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { formatPolicyText, type ListItem } from '@/lib/formatPolicyText';

interface Props {
  text: string | null | undefined;
  className?: string;
}

function renderItems(items: ListItem[], level: number): ReactNode {
  const markerClass = level === 0 ? 'list-[square]' : 'list-[circle]';
  const markerColor = level === 0 ? 'marker:text-neutral-700' : 'marker:text-neutral-400';
  return (
    <ul className={cn(markerClass, markerColor, 'space-y-2 pl-5')}>
      {items.map((item, i) => (
        <li key={i}>
          <span className="whitespace-pre-wrap">{item.text}</span>
          {item.note && item.note.length > 0 && (
            <div className="mt-1 space-y-0.5 pl-1 text-xs text-neutral-500">
              {item.note.map((n, k) => (
                <p key={k} className="whitespace-pre-wrap">
                  {n}
                </p>
              ))}
            </div>
          )}
          {item.sub && item.sub.length > 0 && (
            <div className="mt-2">{renderItems(item.sub, level + 1)}</div>
          )}
        </li>
      ))}
    </ul>
  );
}

export default function FormattedPolicyText({ text, className }: Props) {
  if (!text?.trim()) return null;

  const blocks = formatPolicyText(text);

  if (blocks.length === 0) {
    return (
      <p
        className={cn(
          'whitespace-pre-wrap text-sm leading-relaxed text-neutral-700',
          className,
        )}
      >
        {text}
      </p>
    );
  }

  return (
    <div className={cn('text-sm leading-relaxed text-neutral-700', className)}>
      {blocks.map((block, idx) => {
        if (block.type === 'heading') {
          return (
            <h3
              key={idx}
              className="mb-2 mt-5 flex items-center gap-2 text-base font-semibold text-neutral-900 first:mt-0 before:inline-block before:h-4 before:w-1 before:rounded-sm before:bg-brand-800"
            >
              {block.text}
            </h3>
          );
        }
        if (block.type === 'list') {
          return (
            <div key={idx} className="mb-3 last:mb-0">
              {renderItems(block.items, 0)}
            </div>
          );
        }
        return (
          <p key={idx} className="mb-3 whitespace-pre-wrap last:mb-0">
            {block.text}
          </p>
        );
      })}
    </div>
  );
}
