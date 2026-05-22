export default function AllClearBanner() {
  return (
    <section className="flex items-center gap-2 rounded-xl border border-success-100 bg-success-50 px-4 py-3 text-sm text-success-700">
      <span aria-hidden>✅</span>
      <span>현재 이상 없음 — 운영 정상</span>
    </section>
  );
}
