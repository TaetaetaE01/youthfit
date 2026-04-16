import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="border-t border-gray-100 bg-white py-10">
      <div className="mx-auto max-w-[1200px] px-6">
        <div className="flex flex-col items-center gap-6 md:flex-row md:justify-between">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100">
              <div className="h-2.5 w-2.5 rounded-full bg-brand-800" />
            </div>
            <span className="text-[15px] font-bold tracking-tight text-gray-900">YouthFit</span>
          </Link>

          {/* 링크 */}
          <nav className="flex items-center gap-6">
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">이용약관</a>
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">개인정보처리방침</a>
            <a href="#" className="text-sm text-gray-500 transition-colors hover:text-gray-900">문의하기</a>
          </nav>
        </div>

        <p className="mt-6 text-center text-xs leading-relaxed text-gray-400 md:text-left">
          YouthFit은 공식 정책 포털을 대체하지 않습니다. 최종 신청은 공식 채널에서 확인해주세요.
        </p>
      </div>
    </footer>
  );
}
