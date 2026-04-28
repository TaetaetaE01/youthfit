import type { GuideHighlight } from '@/types/policy';
import { SourceLinkedListCard, type AttachmentRef } from './SourceLinkedListCard';

interface Props {
  highlights: GuideHighlight[];
  attachments: AttachmentRef[];
}

export function HighlightsCard({ highlights, attachments }: Props) {
  return (
    <SourceLinkedListCard
      title="이 정책의 특징"
      emoji="🌟"
      tone="indigo"
      items={highlights}
      attachments={attachments}
    />
  );
}
