import type { GuideHighlight, PolicyAttachment } from '@/types/policy';
import { SourceLinkedListCard, type AttachmentSummary } from './SourceLinkedListCard';

interface Props {
  highlights: GuideHighlight[];
  attachments: AttachmentSummary[];
  policyAttachments: PolicyAttachment[];
}

export function HighlightsCard({ highlights, attachments, policyAttachments }: Props) {
  return (
    <SourceLinkedListCard
      title="이 정책의 특징"
      emoji="🌟"
      tone="indigo"
      items={highlights}
      attachments={attachments}
      policyAttachments={policyAttachments}
    />
  );
}
