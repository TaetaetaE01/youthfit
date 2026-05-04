import { ExternalLink } from 'lucide-react';

interface Props {
  disclaimer: string;
  sourceUrl: string | null;
}

export default function EligibilityFooter({ disclaimer, sourceUrl }: Props) {
  return (
    <div className="border-t border-neutral-100 pt-4">
      <p className="text-xs leading-relaxed text-neutral-500">{disclaimer}</p>
      {sourceUrl && (
        <a
          href={sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-3 flex items-center justify-center gap-1.5 rounded-xl border border-brand-800 px-4 py-2.5 text-sm font-semibold text-brand-800 transition-colors hover:bg-brand-100"
        >
          공식 신청 채널에서 확인
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      )}
    </div>
  );
}
