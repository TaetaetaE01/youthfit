import { useState, useEffect, useRef, type ReactNode } from "react";
import { Link } from "react-router-dom";

/* ──────────────────────────── Hooks ──────────────────────────── */

/** IntersectionObserver 기반 스크롤 감지 훅 */
function useInView(options?: IntersectionObserverInit) {
  const ref = useRef<HTMLDivElement>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) setInView(true); },
      { threshold: 0.15, ...options },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return { ref, inView };
}

/** 숫자 카운트업 훅 */
function useCountUp(target: number, duration = 1600, active = false) {
  const [value, setValue] = useState(0);

  useEffect(() => {
    if (!active) return;
    const start = performance.now();
    let raf: number;
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
      setValue(Math.round(eased * target));
      if (progress < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [active, target, duration]);

  return value;
}

/* ──────────────────────────── Animation wrappers ──────────────────────────── */

/** 스크롤 페이드인 래퍼 */
function FadeInOnScroll({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const { ref, inView } = useInView();

  return (
    <div
      ref={ref}
      className={`transition-all duration-700 ease-out ${className}`}
      style={{
        opacity: inView ? 1 : 0,
        transform: inView ? "translateY(0)" : "translateY(24px)",
        transitionDelay: `${delay}ms`,
      }}
    >
      {children}
    </div>
  );
}

/** 순차 등장 아이템 */
function StaggerItem({ children, index, inView, className = "" }: { children: ReactNode; index: number; inView: boolean; className?: string }) {
  return (
    <div
      className={`transition-all duration-500 ease-out ${className}`}
      style={{
        opacity: inView ? 1 : 0,
        transform: inView ? "translateY(0)" : "translateY(16px)",
        transitionDelay: `${index * 120}ms`,
      }}
    >
      {children}
    </div>
  );
}

/* ──────────────────────────── Icons (inline SVG) ──────────────────────────── */

function SparklesIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z" />
    </svg>
  );
}

function ChatIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM2.25 12.76c0 1.6 1.123 2.994 2.707 3.227 1.087.16 2.185.283 3.293.369V21l4.076-4.076a1.526 1.526 0 011.037-.443 48.282 48.282 0 005.68-.494c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0012 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018z" />
    </svg>
  );
}

function CheckCircleIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function ExclamationIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
    </svg>
  );
}

function XCircleIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function GlobeIcon({ className = "w-6 h-6" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" />
    </svg>
  );
}

function BookOpenIcon({ className = "w-6 h-6" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" />
    </svg>
  );
}

function UserGroupIcon({ className = "w-6 h-6" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
    </svg>
  );
}

function ArrowRightIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
    </svg>
  );
}

/* ──────────────────────────── Sub-components ──────────────────────────── */

function Badge({ children, variant = "primary" }: { children: ReactNode; variant?: "primary" | "dark" }) {
  const styles = {
    primary: "bg-brand-100 text-indigo-600",
    dark: "bg-white/15 text-white",
  };
  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-widest ${styles[variant]}`}>
      {children}
    </span>
  );
}

/* ──────────────────────────── Navbar ──────────────────────────── */

function Navbar() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-6">
        {/* Logo */}
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100">
            <div className="h-3 w-3 rounded-full bg-brand-800" />
          </div>
          <span className="text-[17px] font-bold text-gray-900 tracking-tight">YouthFit</span>
        </div>

        {/* Nav links (desktop) */}
        <nav className="hidden items-center gap-8 md:flex">
          <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">정책 목록</Link>
          <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">적합도 판정</Link>
          <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">Q&A</Link>
        </nav>

        {/* CTA + Mobile toggle */}
        <div className="flex items-center gap-3">
          <Link to="/login" className="rounded-xl bg-brand-800 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-900">
            시작하기
          </Link>
          <button
            className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 md:hidden"
            onClick={() => setMobileOpen(!mobileOpen)}
          >
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              {mobileOpen
                ? <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                : <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
              }
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile nav */}
      {mobileOpen && (
        <nav className="border-t border-gray-100 bg-white px-6 py-4 md:hidden">
          <div className="flex flex-col gap-3">
            <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">정책 목록</Link>
            <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">적합도 판정</Link>
            <Link to="/policies" className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800">Q&A</Link>
          </div>
        </nav>
      )}
    </header>
  );
}

/* ──────────────────────────── Hero ──────────────────────────── */

function HeroSection() {
  const { ref, inView } = useInView();
  const policyCount = useCountUp(500, 1600, inView);
  const categoryCount = useCountUp(7, 1200, inView);
  const regionCount = useCountUp(17, 1400, inView);

  return (
    <section className="relative overflow-hidden bg-gradient-to-b from-brand-50 via-white to-white pb-16 pt-20 md:pb-24 md:pt-28">
      {/* 배경 장식 — 느린 플로팅 */}
      <div className="pointer-events-none absolute -right-40 -top-40 h-[500px] w-[500px] animate-float-slow rounded-full bg-brand-100/50 blur-3xl" />
      <div className="pointer-events-none absolute -left-32 top-20 h-[300px] w-[300px] animate-float-slow-reverse rounded-full bg-indigo-500/5 blur-3xl" />

      <div className="relative mx-auto max-w-[1200px] px-6 text-center">
        {/* 배지 */}
        <FadeInOnScroll>
          <Badge>청년 정책 탐색 서비스</Badge>
        </FadeInOnScroll>

        {/* 헤딩 */}
        <FadeInOnScroll delay={100}>
          <h1 className="mx-auto mt-6 max-w-2xl text-[2.5rem] font-extrabold leading-[1.2] tracking-tight text-gray-900 md:text-5xl md:leading-[1.15]">
            흩어진 청년 정책,{" "}
            <br className="hidden sm:block" />
            <span className="text-brand-800">한눈에 쉽게</span>
          </h1>
        </FadeInOnScroll>

        {/* 서브텍스트 */}
        <FadeInOnScroll delay={200}>
          <p className="mx-auto mt-5 max-w-lg text-base leading-relaxed text-gray-500 md:text-lg">
            복잡한 정책 용어를 쉬운 말로 풀어드리고,
            <br className="hidden sm:block" />
            내 상황에 맞는 정책을 빠르게 찾아드려요.
          </p>
        </FadeInOnScroll>

        {/* CTA 버튼 */}
        <FadeInOnScroll delay={300}>
          <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row sm:gap-4">
            <Link
              to="/policies"
              className="inline-flex items-center gap-2 rounded-xl bg-brand-800 px-7 py-3 text-sm font-semibold text-white transition-all hover:bg-brand-900 hover:gap-3"
            >
              정책 둘러보기
              <ArrowRightIcon className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
            <a
              href="#how-it-works"
              className="inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-7 py-3 text-sm font-semibold text-gray-700 transition-colors hover:border-brand-800 hover:text-brand-800"
            >
              어떻게 도움이 되나요?
            </a>
          </div>
        </FadeInOnScroll>

        {/* 통계 — 카운트업 */}
        <div ref={ref} className="mx-auto mt-12 flex max-w-md items-center justify-center gap-8 sm:gap-12">
          <FadeInOnScroll delay={400} className="text-center">
            <p className="text-2xl font-bold text-gray-900 md:text-3xl">{policyCount}+</p>
            <p className="mt-1 text-xs text-gray-500 sm:text-sm">수집된 정책</p>
          </FadeInOnScroll>
          <div className="h-8 w-px bg-gray-200" />
          <FadeInOnScroll delay={500} className="text-center">
            <p className="text-2xl font-bold text-gray-900 md:text-3xl">{categoryCount}개</p>
            <p className="mt-1 text-xs text-gray-500 sm:text-sm">카테고리</p>
          </FadeInOnScroll>
          <div className="h-8 w-px bg-gray-200" />
          <FadeInOnScroll delay={600} className="text-center">
            <p className="text-2xl font-bold text-gray-900 md:text-3xl">{regionCount}개</p>
            <p className="mt-1 text-xs text-gray-500 sm:text-sm">지역</p>
          </FadeInOnScroll>
        </div>
      </div>
    </section>
  );
}

/* ──────────────────────────── Pain Point ──────────────────────────── */

const painPoints = [
  {
    icon: GlobeIcon,
    title: "정보가 흩어져 있어요",
    description: "온라인청년센터, 고용24, 지자체 홈페이지...\n매번 여러 사이트를 돌아다녀야 해요.",
  },
  {
    icon: BookOpenIcon,
    title: "용어가 너무 어려워요",
    description: '"중위소득 150% 이하", "무주택 세대주"...\n나한테 해당되는 건지 알 수가 없어요.',
  },
  {
    icon: UserGroupIcon,
    title: "결국 포기하게 돼요",
    description: "자격이 되는지 확인하기 어렵고,\n신청 경로를 찾다 지쳐서 포기해요.",
  },
];

function PainPointSection() {
  const { ref, inView } = useInView();

  return (
    <section className="bg-gray-50 py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-6">
        {/* 라벨 + 타이틀 */}
        <FadeInOnScroll className="text-center">
          <Badge>Problem</Badge>
          <h2 className="mx-auto mt-4 max-w-md text-2xl font-bold leading-snug text-gray-900 md:text-[2rem]">
            청년 정책,
            <br />
            왜 이렇게 어려울까요?
          </h2>
        </FadeInOnScroll>

        {/* 카드 — 순차 등장 */}
        <div ref={ref} className="mt-12 grid gap-5 sm:grid-cols-2 md:grid-cols-3">
          {painPoints.map((item, i) => (
            <StaggerItem key={item.title} index={i} inView={inView}>
              <div className="group h-full rounded-2xl border border-gray-100 bg-white p-7 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:shadow-card-hover">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-100 transition-transform duration-300 group-hover:scale-110">
                  <item.icon className="h-6 w-6 text-brand-800" />
                </div>
                <h3 className="mt-5 text-lg font-bold text-gray-900">{item.title}</h3>
                <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-gray-500">
                  {item.description}
                </p>
              </div>
            </StaggerItem>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ──────────────────────────── Feature Section ──────────────────────────── */

function FeatureExplore() {
  const { ref, inView } = useInView();
  const categories = [
    { name: "취업", count: 87 },
    { name: "주거", count: 64 },
    { name: "교육", count: 53 },
    { name: "복지", count: 71 },
    { name: "금융", count: 42 },
  ];

  return (
    <FadeInOnScroll>
      <div className="grid items-center gap-10 md:grid-cols-2">
        {/* 텍스트 */}
        <div>
          <Badge>탐색</Badge>
          <h3 className="mt-4 text-2xl font-bold leading-snug text-gray-900 md:text-[1.75rem]">
            정책을 한곳에서
            <br />
            쉽게 찾아보세요
          </h3>
          <p className="mt-4 text-sm leading-relaxed text-gray-500 md:text-base">
            카테고리, 지역, 모집 상태로 필터링하고 키워드로 검색하세요.
            <br />
            500개 이상의 청년 정책이 정리되어 있어요.
          </p>
        </div>
        {/* 비주얼 — 순차 등장 */}
        <div ref={ref} className="rounded-2xl border border-gray-100 bg-white p-5 shadow-card">
          <div className="flex flex-col gap-3">
            {categories.map((cat, i) => (
              <StaggerItem key={cat.name} index={i} inView={inView}>
                <div className="flex items-center justify-between rounded-xl bg-gray-50 px-4 py-3 transition-colors duration-200 hover:bg-brand-50">
                  <span className="text-sm font-semibold text-gray-700">{cat.name}</span>
                  <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-800">{cat.count}개</span>
                </div>
              </StaggerItem>
            ))}
          </div>
        </div>
      </div>
    </FadeInOnScroll>
  );
}

function FeatureInterpret() {
  return (
    <FadeInOnScroll>
      <div className="grid items-center gap-10 md:grid-cols-2">
        {/* 비주얼 (왼쪽) */}
        <div className="order-2 md:order-1">
          <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-card">
            {/* 원문 */}
            <div className="rounded-lg bg-gray-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-widest text-gray-400">원문</p>
              <p className="mt-2 text-sm leading-relaxed text-gray-600">
                기준 중위소득 150% 이하인 가구의 무주택 세대주
              </p>
            </div>
            {/* 화살표 — 바운스 */}
            <div className="flex justify-center py-3">
              <svg className="h-5 w-5 animate-bounce-gentle text-brand-800" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 13.5L12 21m0 0l-7.5-7.5M12 21V3" />
              </svg>
            </div>
            {/* 쉬운 설명 */}
            <div className="rounded-lg bg-brand-50 p-4">
              <div className="flex items-center gap-2">
                <SparklesIcon className="h-4 w-4 text-brand-800" />
                <p className="text-xs font-semibold uppercase tracking-widest text-brand-800">쉬운 설명</p>
              </div>
              <p className="mt-2 text-sm font-medium leading-relaxed text-gray-900">
                1인 가구 기준 월 소득 약 330만 원 이하이고, 본인 명의 집이 없으면 지원 가능해요
              </p>
            </div>
          </div>
        </div>
        {/* 텍스트 (오른쪽) */}
        <div className="order-1 md:order-2">
          <Badge>해석</Badge>
          <h3 className="mt-4 text-2xl font-bold leading-snug text-gray-900 md:text-[1.75rem]">
            어려운 조건도
            <br />
            쉬운 말로 풀어드려요
          </h3>
          <p className="mt-4 text-sm leading-relaxed text-gray-500 md:text-base">
            AI가 복잡한 행정 용어를 일상 언어로 바꿔드려요.
            <br />
            공고 원문을 처음부터 읽을 필요 없어요.
          </p>
        </div>
      </div>
    </FadeInOnScroll>
  );
}

function FeatureEligibility() {
  const { ref, inView } = useInView();
  const criteria = [
    { label: "연령 조건", status: "높음", icon: CheckCircleIcon, color: "text-success-500" },
    { label: "거주지 조건", status: "높음", icon: CheckCircleIcon, color: "text-success-500" },
    { label: "소득 기준", status: "추가 확인 필요", icon: ExclamationIcon, color: "text-warning-500" },
    { label: "고용 상태", status: "낮음", icon: XCircleIcon, color: "text-error-500" },
  ];

  return (
    <FadeInOnScroll>
      <div className="grid items-center gap-10 md:grid-cols-2">
        {/* 텍스트 */}
        <div>
          <Badge>적합도</Badge>
          <h3 className="mt-4 text-2xl font-bold leading-snug text-gray-900 md:text-[1.75rem]">
            내 상황에 맞는지
            <br />
            바로 확인하세요
          </h3>
          <p className="mt-4 text-sm leading-relaxed text-gray-500 md:text-base">
            나이, 소득, 거주지 등 내 정보를 입력하면 해당 가능성을 알려드려요.
            <br />
            법적 판정이 아닌 가벼운 가이드예요.
          </p>
        </div>
        {/* 비주얼 — 순차 등장 */}
        <div ref={ref} className="rounded-2xl border border-gray-100 bg-white p-5 shadow-card">
          <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-gray-400">적합도 판정 결과</p>
          <div className="flex flex-col gap-3">
            {criteria.map((item, i) => (
              <StaggerItem key={item.label} index={i} inView={inView}>
                <div className="flex items-center justify-between rounded-xl bg-gray-50 px-4 py-3">
                  <span className="text-sm font-medium text-gray-700">{item.label}</span>
                  <span className={`flex items-center gap-1.5 text-sm font-semibold ${item.color}`}>
                    <item.icon className="h-4 w-4" />
                    {item.status === "높음" ? "해당 가능성 높음" : item.status === "낮음" ? "해당 가능성 낮음" : item.status}
                  </span>
                </div>
              </StaggerItem>
            ))}
          </div>
        </div>
      </div>
    </FadeInOnScroll>
  );
}

function FeatureQnA() {
  const { ref, inView } = useInView();

  return (
    <FadeInOnScroll>
      <div className="grid items-center gap-10 md:grid-cols-2">
        {/* 비주얼 (왼쪽) — 채팅 순차 등장 */}
        <div className="order-2 md:order-1" ref={ref}>
          <div className="overflow-hidden rounded-2xl border border-gray-100 bg-brand-800/80 p-5 shadow-card">
            {/* 사용자 메시지 */}
            <div
              className="flex justify-end transition-all duration-500 ease-out"
              style={{
                opacity: inView ? 1 : 0,
                transform: inView ? "translateX(0)" : "translateX(24px)",
              }}
            >
              <div className="rounded-xl rounded-br-sm bg-indigo-500/40 px-4 py-2.5">
                <p className="text-sm text-white">재학생도 신청할 수 있나요?</p>
              </div>
            </div>
            {/* AI 응답 */}
            <div
              className="mt-3 flex gap-2 transition-all duration-500 ease-out"
              style={{
                opacity: inView ? 1 : 0,
                transform: inView ? "translateX(0)" : "translateX(-24px)",
                transitionDelay: "400ms",
              }}
            >
              <ChatIcon className="mt-0.5 h-5 w-5 shrink-0 text-white/50" />
              <div className="rounded-xl rounded-bl-sm bg-white/20 px-4 py-2.5">
                <p className="text-sm leading-relaxed text-white">
                  네, 이 정책은 만 19~34세 청년이면 재학 여부와 관계없이 신청 가능합니다.
                </p>
              </div>
            </div>
            {/* 출처 */}
            <div
              className="mt-3 ml-7 flex items-center gap-1.5 transition-all duration-500 ease-out"
              style={{
                opacity: inView ? 1 : 0,
                transitionDelay: "700ms",
              }}
            >
              <div className="h-1 w-1 rounded-full bg-white/30" />
              <p className="text-xs text-white/40">출처: 원문 자격 요건 섹션 기반</p>
            </div>
          </div>
        </div>
        {/* 텍스트 (오른쪽) */}
        <div className="order-1 md:order-2">
          <Badge>Q&A</Badge>
          <h3 className="mt-4 text-2xl font-bold leading-snug text-gray-900 md:text-[1.75rem]">
            궁금한 건
            <br />
            바로 물어보세요
          </h3>
          <p className="mt-4 text-sm leading-relaxed text-gray-500 md:text-base">
            정책 원문을 기반으로 AI가 답변해드려요.
            <br />
            출처도 함께 보여주니까 신뢰할 수 있어요.
          </p>
        </div>
      </div>
    </FadeInOnScroll>
  );
}

function FeatureSection() {
  return (
    <section id="features" className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-6">
        {/* 라벨 + 타이틀 */}
        <FadeInOnScroll className="text-center">
          <Badge>Features</Badge>
          <h2 className="mx-auto mt-4 max-w-md text-2xl font-bold leading-snug text-gray-900 md:text-[2rem]">
            YouthFit이
            <br />
            도와드릴게요
          </h2>
        </FadeInOnScroll>

        {/* 기능 카드들 */}
        <div className="mt-16 flex flex-col gap-20 md:gap-28">
          <FeatureExplore />
          <FeatureInterpret />
          <FeatureEligibility />
          <FeatureQnA />
        </div>
      </div>
    </section>
  );
}

/* ──────────────────────────── How it Works ──────────────────────────── */

const steps = [
  {
    number: "01",
    title: "정책 검색",
    description: "키워드나 카테고리로 관심 있는 청년 정책을 찾아보세요.",
  },
  {
    number: "02",
    title: "적합도 확인",
    description: "내 프로필을 기반으로 해당 가능성을 확인하세요.",
  },
  {
    number: "03",
    title: "공식 채널에서 신청",
    description: "준비 사항을 확인하고, 공식 신청 채널로 바로 이동하세요.",
  },
];

function HowItWorksSection() {
  const { ref, inView } = useInView();

  return (
    <section id="how-it-works" className="bg-gray-50 py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-6">
        {/* 라벨 + 타이틀 */}
        <FadeInOnScroll className="text-center">
          <Badge>How it Works</Badge>
          <h2 className="mt-4 text-2xl font-bold leading-snug text-gray-900 md:text-[2rem]">
            3단계로 간단하게
          </h2>
        </FadeInOnScroll>

        {/* 스텝 카드 — 순차 등장 */}
        <div ref={ref} className="mt-12 grid gap-6 md:grid-cols-3">
          {steps.map((step, i) => (
            <StaggerItem key={step.number} index={i} inView={inView}>
              <div className="relative h-full rounded-2xl border border-gray-100 bg-white p-7 shadow-card">
                {/* 커넥터 라인 (데스크톱만) */}
                {i < steps.length - 1 && (
                  <div className="pointer-events-none absolute right-0 top-1/2 hidden h-px w-6 -translate-y-1/2 translate-x-full bg-gray-200 md:block" />
                )}
                <span className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-brand-800 text-sm font-bold text-white">
                  {step.number}
                </span>
                <h3 className="mt-4 text-lg font-bold text-gray-900">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-500">{step.description}</p>
              </div>
            </StaggerItem>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ──────────────────────────── CTA Section ──────────────────────────── */

function CTASection() {
  return (
    <section className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-6">
        <FadeInOnScroll>
          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-800 to-brand-900 px-6 py-16 text-center md:px-16 md:py-20">
            {/* 배경 장식 */}
            <div className="pointer-events-none absolute -right-20 -top-20 h-[300px] w-[300px] animate-float-slow rounded-full bg-indigo-500/10 blur-3xl" />

            <h2 className="relative mx-auto max-w-md text-2xl font-bold leading-snug text-white md:text-[2rem]">
              지금 바로
              <br />
              나에게 맞는 정책을 찾아보세요
            </h2>
            <p className="relative mx-auto mt-4 max-w-sm text-sm leading-relaxed text-white/70 md:text-base">
              회원가입 없이도 정책을 둘러볼 수 있어요.
            </p>
            <div className="relative mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row sm:gap-4">
              <Link
                to="/policies"
                className="inline-flex items-center gap-2 rounded-xl bg-white px-7 py-3 text-sm font-semibold text-brand-800 shadow-lg transition-all hover:bg-gray-100 hover:shadow-xl animate-cta-glow"
              >
                정책 둘러보기
                <ArrowRightIcon className="h-4 w-4" />
              </Link>
              <Link
                to="/login"
                className="inline-flex items-center gap-2 rounded-xl border border-white/30 px-7 py-3 text-sm font-semibold text-white transition-colors hover:bg-white/10"
              >
                카카오로 시작하기
              </Link>
            </div>
          </div>
        </FadeInOnScroll>
      </div>
    </section>
  );
}

/* ──────────────────────────── Footer ──────────────────────────── */

function Footer() {
  return (
    <footer className="border-t border-gray-100 bg-white py-10">
      <div className="mx-auto max-w-[1200px] px-6">
        <div className="flex flex-col items-center gap-6 md:flex-row md:justify-between">
          {/* Logo */}
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100">
              <div className="h-2.5 w-2.5 rounded-full bg-brand-800" />
            </div>
            <span className="text-[15px] font-bold text-gray-900 tracking-tight">YouthFit</span>
          </div>

          {/* 링크 */}
          <nav className="flex items-center gap-6">
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">이용약관</a>
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">개인정보처리방침</a>
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">문의하기</a>
          </nav>
        </div>

        {/* 면책 고지 */}
        <p className="mt-6 text-center text-xs leading-relaxed text-gray-400 md:text-left">
          YouthFit은 공식 정책 포털을 대체하지 않습니다. 최종 신청은 공식 채널에서 확인해주세요.
        </p>
      </div>
    </footer>
  );
}

/* ──────────────────────────── Page ──────────────────────────── */

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white font-sans text-gray-900 antialiased">
      <Navbar />
      <HeroSection />
      <PainPointSection />
      <FeatureSection />
      <HowItWorksSection />
      <CTASection />
      <Footer />
    </div>
  );
}
