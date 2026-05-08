import type { InputHTMLAttributes, ReactNode } from 'react';
import { ChevronDown, Search } from 'lucide-react';
import { cn } from '@/lib/cn';

/* -------- SegmentedControl -------- */

type SegmentedOption<T extends string> = {
  value: T;
  label: ReactNode;
};

type SegmentedProps<T extends string> = {
  value: T;
  onChange: (v: T) => void;
  options: readonly SegmentedOption<T>[];
  size?: 'sm' | 'md';
  ariaLabel?: string;
  className?: string;
};

export function SegmentedControl<T extends string>({
  value,
  onChange,
  options,
  size = 'md',
  ariaLabel,
  className,
}: SegmentedProps<T>) {
  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className={cn(
        'inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5 text-xs',
        className,
      )}
    >
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(o.value)}
            className={cn(
              'inline-flex items-center justify-center rounded-md font-semibold transition-all',
              size === 'md' ? 'h-7 px-3' : 'h-6 px-2.5',
              active
                ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200/60'
                : 'text-slate-500 hover:text-slate-700',
            )}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

/* -------- AdminSelect (native, chevron) -------- */

type SelectOption<T extends string> = {
  value: T;
  label: string;
};

type AdminSelectProps<T extends string> = {
  value: T | '';
  onChange: (v: T | '') => void;
  options: readonly SelectOption<T>[];
  placeholder?: string;
  size?: 'sm' | 'md';
  className?: string;
  ariaLabel?: string;
};

export function AdminSelect<T extends string>({
  value,
  onChange,
  options,
  placeholder,
  size = 'md',
  className,
  ariaLabel,
}: AdminSelectProps<T>) {
  return (
    <div className={cn('relative inline-block', className)}>
      <select
        value={value}
        onChange={(e) => onChange((e.target.value || '') as T | '')}
        aria-label={ariaLabel}
        className={cn(
          'appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-8 text-sm text-slate-700 transition-colors hover:border-slate-300 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/10',
          size === 'md' ? 'h-9' : 'h-8',
        )}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400"
        aria-hidden
      />
    </div>
  );
}

/* -------- ToggleChip (multi-select pill) -------- */

type ToggleChipTone =
  | 'default'
  | 'indigo'
  | 'emerald'
  | 'amber'
  | 'rose'
  | 'slate';

type ToggleChipProps = {
  active: boolean;
  onToggle: () => void;
  tone?: ToggleChipTone;
  children: ReactNode;
  className?: string;
};

const TONE_ACTIVE: Record<ToggleChipTone, string> = {
  default: 'bg-brand-50 text-brand-800 border-brand-200',
  indigo: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  amber: 'bg-amber-50 text-amber-700 border-amber-200',
  rose: 'bg-rose-50 text-rose-700 border-rose-200',
  slate: 'bg-slate-100 text-slate-700 border-slate-300',
};

const TONE_DOT: Record<ToggleChipTone, string> = {
  default: 'bg-brand-500',
  indigo: 'bg-indigo-500',
  emerald: 'bg-emerald-500',
  amber: 'bg-amber-500',
  rose: 'bg-rose-500',
  slate: 'bg-slate-400',
};

export function ToggleChip({
  active,
  onToggle,
  tone = 'default',
  children,
  className,
}: ToggleChipProps) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={active}
      onClick={onToggle}
      className={cn(
        'inline-flex h-7 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium transition-all',
        active
          ? `${TONE_ACTIVE[tone]} shadow-sm`
          : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-700',
        className,
      )}
    >
      <span
        className={cn(
          'h-1.5 w-1.5 rounded-full transition-opacity',
          active ? TONE_DOT[tone] : 'bg-slate-300',
        )}
        aria-hidden
      />
      {children}
    </button>
  );
}

/* -------- AdminSearchInput -------- */

type SearchProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> & {
  size?: 'sm' | 'md';
  containerClassName?: string;
};

export function AdminSearchInput({
  size = 'md',
  containerClassName,
  className,
  ...rest
}: SearchProps) {
  return (
    <div className={cn('relative', containerClassName)}>
      <Search
        className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400"
        aria-hidden
      />
      <input
        {...rest}
        className={cn(
          'rounded-lg border border-slate-200 bg-white pl-8 pr-3 text-sm text-slate-700 placeholder:text-slate-400 transition-colors hover:border-slate-300 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/10',
          size === 'md' ? 'h-9' : 'h-8',
          className,
        )}
      />
    </div>
  );
}
