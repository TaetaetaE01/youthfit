import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AppLayout from '@/components/layout/AppLayout';
import AdminLayout from '@/components/layout/AdminLayout';
import { RequireAdmin } from '@/components/auth/RequireAdmin';
import LandingPage from '@/pages/LandingPage';
import PolicyListPage from '@/pages/PolicyListPage';
import PolicyDetailPage from '@/pages/PolicyDetailPage';
import LoginPage from '@/pages/LoginPage';
import KakaoCallbackPage from '@/pages/KakaoCallbackPage';
import MyPage from '@/pages/MyPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';

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
          <Route path="/" element={<LandingPage />} />

          <Route element={<AppLayout />}>
            <Route path="/policies" element={<PolicyListPage />} />
            <Route path="/policies/:policyId" element={<PolicyDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
            <Route path="/mypage" element={<MyPage />} />
          </Route>

          {/* 어드민 — RequireAdmin → AdminLayout → 자식 페이지 */}
          <Route element={<RequireAdmin />}>
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<AdminDashboardPage />} />
              {/* 후속 spec에서 자식 라우트 추가 */}
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
