import type { GuidePitfall, PolicyAttachment } from '@/types/policy';
import { SourceLinkedListCard, type AttachmentSummary } from './SourceLinkedListCard';

interface Props {
  pitfalls: GuidePitfall[];
  attachments: AttachmentSummary[];
  policyAttachments: PolicyAttachment[];
}

export function PitfallsCard({ pitfalls, attachments, policyAttachments }: Props) {
  return (
    <SourceLinkedListCard
      title="놓치기 쉬운 점"
      emoji="⚠️"
      tone="amber"
      items={pitfalls}
      attachments={attachments}
      policyAttachments={policyAttachments}
    />
  );
}
