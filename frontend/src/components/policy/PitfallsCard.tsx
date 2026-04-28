import type { GuidePitfall } from '@/types/policy';
import { SourceLinkedListCard, type AttachmentRef } from './SourceLinkedListCard';

interface Props {
  pitfalls: GuidePitfall[];
  attachments: AttachmentRef[];
}

export function PitfallsCard({ pitfalls, attachments }: Props) {
  return (
    <SourceLinkedListCard
      title="놓치기 쉬운 점"
      emoji="⚠️"
      tone="amber"
      items={pitfalls}
      attachments={attachments}
    />
  );
}
