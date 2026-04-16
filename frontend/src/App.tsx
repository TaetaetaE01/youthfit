import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AppLayout from '@/components/layout/AppLayout';
import LandingPage from '@/pages/LandingPage';
import PolicyListPage from '@/pages/PolicyListPage';
import PolicyDetailPage from '@/pages/PolicyDetailPage';
import LoginPage from '@/pages/LoginPage';
import KakaoCallbackPage from '@/pages/KakaoCallbackPage';
import MyPage from '@/pages/MyPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          {/* 랜딩은 자체 레이아웃 사용 */}
          <Route path="/" element={<LandingPage />} />

          {/* 공통 레이아웃 적용 */}
          <Route element={<AppLayout />}>
            <Route path="/policies" element={<PolicyListPage />} />
            <Route path="/policies/:policyId" element={<PolicyDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
            <Route path="/mypage" element={<MyPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
