import { MapPin } from 'lucide-react';

interface RegionPickerBannerProps {
  regionLabel: string;
  onDismiss: () => void;
}

export default function RegionPickerBanner({ regionLabel, onDismiss }: RegionPickerBannerProps) {
  return (
    <div
      role="status"
      className="mb-4 flex items-center gap-2 rounded-xl border border-brand-100 bg-gradient-to-r from-brand-50 to-brand-100/50 px-4 py-3 text-sm"
    >
      <MapPin className="h-4 w-4 shrink-0 text-brand-800" aria-hidden="true" />
      <span className="flex-1 text-brand-900">
        <strong className="font-semibold">내 지역({regionLabel})</strong>으로 보고 있어요
      </span>
      <button
        type="button"
        onClick={onDismiss}
        className="rounded-md px-2 py-1 text-xs font-semibold text-brand-800 transition-colors hover:bg-brand-100"
      >
        해제
      </button>
    </div>
  );
}
