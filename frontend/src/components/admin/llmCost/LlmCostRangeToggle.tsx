import { SegmentedControl } from '@/components/admin/AdminControls';

type Range = '24h' | '7d' | '30d';

interface Props {
  value: Range;
  onChange: (r: Range) => void;
}

const OPTIONS = [
  { value: '24h' as const, label: '24시간' },
  { value: '7d' as const, label: '7일' },
  { value: '30d' as const, label: '30일' },
];

export function LlmCostRangeToggle({ value, onChange }: Props) {
  return (
    <SegmentedControl
      ariaLabel="기간"
      value={value}
      onChange={onChange}
      options={OPTIONS}
    />
  );
}
